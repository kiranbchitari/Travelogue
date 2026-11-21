package com.travelogue.app.ui.memories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelogue.app.data.db.entity.*
import com.travelogue.app.data.repository.DiaryRepository
import com.travelogue.app.data.repository.ExpenseRepository
import com.travelogue.app.data.repository.JournalRepository
import com.travelogue.app.data.repository.PhotoRepository
import com.travelogue.app.data.repository.TripRepository
import com.travelogue.app.domain.CurrencyConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.LocalTime

/** One item in the trip story. [time] orders items within a day. */
sealed interface StoryEvent {
    val key: String
    val time: LocalTime

    data class Diary(val entry: DiaryEntryEntity, val photos: List<TripPhotoEntity>, val tags: List<String>) : StoryEvent {
        override val key = "diary-${entry.id}"
        override val time: LocalTime = entry.dateTime.toLocalTime()
    }
    data class Place(val place: PlaceEntity, val photos: List<TripPhotoEntity>) : StoryEvent {
        override val key = "place-${place.id}"
        override val time: LocalTime = LocalTime.of(12, 0)
    }
    data class CheckIn(val hotel: HotelEntity, val photos: List<TripPhotoEntity>) : StoryEvent {
        override val key = "in-${hotel.id}"
        override val time: LocalTime = LocalTime.of(14, 0)
    }
    data class CheckOut(val hotel: HotelEntity) : StoryEvent {
        override val key = "out-${hotel.id}"
        override val time: LocalTime = LocalTime.of(11, 0)
    }
    data class Activity(val activity: ItineraryActivityEntity) : StoryEvent {
        override val key = "act-${activity.id}"
        override val time: LocalTime = activity.startTime ?: LocalTime.of(12, 30)
    }
    data class Booking(val booking: BookingEntity) : StoryEvent {
        override val key = "book-${booking.id}"
        override val time: LocalTime = booking.departure?.toLocalTime() ?: LocalTime.of(8, 0)
    }
    data class Photos(val date: LocalDate?, val photos: List<TripPhotoEntity>) : StoryEvent {
        override val key = "photos-$date"
        override val time: LocalTime = photos.minOfOrNull { it.takenAt.toLocalTime() } ?: LocalTime.NOON
    }
    data class Expenses(
        val date: LocalDate,
        val expenses: List<ExpenseEntity>,
        val total: Double,
        val currency: String,
        val unconvertedCount: Int,
        val categories: Map<Long, ExpenseCategoryEntity>,
    ) : StoryEvent {
        override val key = "exp-$date"
        override val time: LocalTime = LocalTime.MAX
    }
}

data class StoryDay(val date: LocalDate?, val dayNumber: Int?, val events: List<StoryEvent>)

data class TimelineState(
    val trip: TripEntity? = null,
    val days: List<StoryDay> = emptyList(),
    val photoCount: Int = 0,
    val entryCount: Int = 0,
    val placeCount: Int = 0,
    val loaded: Boolean = false,
)

private data class PartA(
    val trip: TripEntity?,
    val entries: List<DiaryEntryEntity>,
    val places: List<PlaceEntity>,
    val hotels: List<HotelEntity>,
    val activities: List<ItineraryActivityEntity>,
)

private data class PartB(
    val days: List<ItineraryDayEntity>,
    val expenses: List<ExpenseEntity>,
    val categories: List<ExpenseCategoryEntity>,
    val bookings: List<BookingEntity>,
    val photos: List<TripPhotoEntity>,
)

class TripTimelineViewModel(
    trips: TripRepository,
    diary: DiaryRepository,
    journal: JournalRepository,
    expenses: ExpenseRepository,
    photos: PhotoRepository,
    tripId: Long,
) : ViewModel() {
    private val a = combine(
        trips.observeTrip(tripId), diary.observeForTrip(tripId), journal.observePlaces(tripId),
        journal.observeHotels(tripId), journal.observeActivities(tripId), ::PartA,
    )
    private val b = combine(
        journal.observeDays(tripId), expenses.observeForTrip(tripId), expenses.observeCategories(),
        journal.observeBookings(tripId), photos.observeForTrip(tripId), ::PartB,
    )

    val state: StateFlow<TimelineState> = combine(a, b, trips.observeRates(tripId), diary.observeEntryTags()) { pa, pb, rates, tags ->
        build(pa, pb, rates.associate { it.currency to it.rate }, tags.groupBy({ it.entryId }, { it.name }))
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TimelineState())

    private fun build(a: PartA, b: PartB, rates: Map<String, Double>, tags: Map<Long, List<String>>): TimelineState {
        val trip = a.trip ?: return TimelineState(loaded = true)
        val byDate = HashMap<LocalDate?, MutableList<StoryEvent>>()
        fun add(d: LocalDate?, e: StoryEvent) { byDate.getOrPut(d) { mutableListOf() } += e }

        val photosByOwner = b.photos.groupBy { it.ownerType to it.ownerId }
        fun ownerPhotos(o: PhotoOwner, id: Long) = photosByOwner[o to id].orEmpty()

        a.entries.forEach { add(it.dateTime.toLocalDate(), StoryEvent.Diary(it, ownerPhotos(PhotoOwner.DIARY, it.id), tags[it.id].orEmpty())) }
        a.places.forEach { add(it.visitDate, StoryEvent.Place(it, ownerPhotos(PhotoOwner.PLACE, it.id))) }
        a.hotels.forEach { h ->
            add(h.checkIn, StoryEvent.CheckIn(h, ownerPhotos(PhotoOwner.HOTEL, h.id)))
            if (h.checkOut.isAfter(h.checkIn)) add(h.checkOut, StoryEvent.CheckOut(h))
        }
        val dayDates = b.days.associate { it.id to it.date }
        a.activities.forEach { act -> add(dayDates[act.dayId], StoryEvent.Activity(act)) }
        b.bookings.forEach { add(it.departure?.toLocalDate(), StoryEvent.Booking(it)) }

        // Loose photos (gallery / itinerary) become one collage per day.
        b.photos.filter { it.ownerType == PhotoOwner.GENERAL || it.ownerType == PhotoOwner.ACTIVITY }
            .groupBy { it.takenAt.toLocalDate() }
            .forEach { (d, list) -> add(d, StoryEvent.Photos(d, list.sortedBy { it.takenAt })) }

        val converter = CurrencyConverter(trip.currency, rates)
        val cats = b.categories.associateBy { it.id }
        b.expenses.groupBy { it.date }.forEach { (d, list) ->
            var total = 0.0
            var unconverted = 0
            list.forEach { e -> converter.toBase(e.amount, e.currency)?.let { total += it } ?: unconverted++ }
            add(d, StoryEvent.Expenses(d, list.sortedWith(compareBy({ it.time }, { it.id })), total, trip.currency, unconverted, cats))
        }

        val days = byDate.entries
            .sortedWith(compareBy(nullsLast<LocalDate>()) { it.key })
            .map { (d, events) -> StoryDay(d, d?.let { storyDayNumber(it, trip) }, events.sortedBy { it.time }) }

        return TimelineState(
            trip = trip,
            days = days,
            photoCount = b.photos.size,
            entryCount = a.entries.size,
            placeCount = a.places.size,
            loaded = true,
        )
    }
}
