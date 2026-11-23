package com.travelogue.app.ui.navigation

import kotlinx.serialization.Serializable

// Top-level tabs
@Serializable data object HomeRoute
@Serializable data object TripsRoute
@Serializable data object ExpensesRoute
@Serializable data object MemoriesRoute
@Serializable data object SettingsRoute

// Trips
/** [startDay]/[endDay] are epoch days that prefill a new trip's dates (0 = not set). */
@Serializable data class TripEditorRoute(val tripId: Long = 0, val startDay: Long = 0, val endDay: Long = 0)
@Serializable data class TripDetailRoute(val tripId: Long, val tab: Int = 0)
@Serializable data class TripFinanceRoute(val tripId: Long)
@Serializable data class TripTimelineRoute(val tripId: Long)
@Serializable data class TripGalleryRoute(val tripId: Long)

// Records inside a trip (id = 0 means "new")
@Serializable data class ExpenseEditorRoute(val tripId: Long, val expenseId: Long = 0)
@Serializable data class ActivityEditorRoute(val tripId: Long, val activityId: Long = 0, val dayId: Long = 0)
@Serializable data class HotelEditorRoute(val tripId: Long, val hotelId: Long = 0)
@Serializable data class HotelDetailRoute(val hotelId: Long)
@Serializable data class PlaceEditorRoute(val tripId: Long, val placeId: Long = 0)
@Serializable data class PlaceDetailRoute(val placeId: Long)
@Serializable data class BookingEditorRoute(val tripId: Long, val bookingId: Long = 0)
@Serializable data class ContactEditorRoute(val tripId: Long, val contactId: Long = 0)
@Serializable data class DiaryEditorRoute(val tripId: Long, val entryId: Long = 0)
@Serializable data class DiaryDetailRoute(val entryId: Long)

/** tripId = 0 shows photos from all trips. */
@Serializable data class PhotoViewerRoute(val tripId: Long, val photoId: Long, val favouritesOnly: Boolean = false)

/** Calendar of the user's and their partner's days off. */
@Serializable data object HolidayPlannerRoute

// Settings
@Serializable data object ManageCategoriesRoute
@Serializable data object ManageTravellersRoute
@Serializable data object ExchangeRatesRoute
@Serializable data object BackupRoute

/** Tab indices of the trip detail screen. */
object TripTab {
    const val OVERVIEW = 0
    const val ITINERARY = 1
    const val EXPENSES = 2
    const val PLACES = 3
    const val HOTELS = 4
    const val BOOKINGS = 5
    const val CONTACTS = 6
    const val DIARY = 7
    val titles = listOf("Overview", "Itinerary", "Expenses", "Places", "Hotels", "Bookings", "Contacts", "Diary")
}
