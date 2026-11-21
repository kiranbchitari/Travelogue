package com.travelogue.app.ui.hotels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.travelogue.app.core.designsystem.components.*
import com.travelogue.app.core.designsystem.theme.Shapes
import com.travelogue.app.core.designsystem.theme.Space
import com.travelogue.app.core.designsystem.theme.Travel
import com.travelogue.app.core.util.Fmt
import com.travelogue.app.data.db.entity.HotelEntity
import com.travelogue.app.data.db.entity.PhotoOwner
import com.travelogue.app.data.repository.JournalRepository
import com.travelogue.app.data.repository.PhotoRepository
import com.travelogue.app.ui.common.appViewModel
import com.travelogue.app.ui.navigation.Nav
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HotelCardModel(val hotel: HotelEntity, val coverPath: String?, val photoCount: Int)
data class HotelsState(val loading: Boolean = true, val hotels: List<HotelCardModel> = emptyList())

class HotelsViewModel(journal: JournalRepository, photos: PhotoRepository, tripId: Long) : ViewModel() {
    val state: StateFlow<HotelsState> = combine(journal.observeHotels(tripId), photos.observeForTrip(tripId)) { hotels, allPhotos ->
        val byHotel = allPhotos.filter { it.ownerType == PhotoOwner.HOTEL }.groupBy { it.ownerId }
        HotelsState(
            loading = false,
            hotels = hotels.map { h ->
                val ps = byHotel[h.id].orEmpty().sortedWith(compareBy({ it.sortOrder }, { it.id }))
                HotelCardModel(h, ps.firstOrNull()?.relativePath, ps.size)
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HotelsState())
}

@Composable
fun TripHotelsTab(nav: Nav, tripId: Long, modifier: Modifier = Modifier) {
    val vm = appViewModel(key = "hotels-$tripId") { HotelsViewModel(journal, photos, tripId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val bottomPad = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 96.dp

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, top = Space.lg, bottom = bottomPad),
            verticalArrangement = Arrangement.spacedBy(Space.xl),
        ) {
            when {
                state.loading -> item { LoadingState() }
                state.hotels.isEmpty() -> item {
                    EmptyState(
                        Icons.Rounded.Hotel,
                        "Where are you staying?",
                        "Keep every stay in one place: bookings, photos and your honest review.",
                        actionLabel = "Add a stay",
                        onAction = { nav.hotelEditor(tripId) },
                    )
                }
                else -> {
                    item { StaysSummary(state.hotels.map { it.hotel }) }
                    items(state.hotels, key = { it.hotel.id }) { m -> HotelCard(m) { nav.hotel(m.hotel.id) } }
                }
            }
        }
        AccentFab(
            Icons.Rounded.Add,
            { nav.hotelEditor(tripId) },
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(Space.gutter),
            contentDescription = "Add stay",
            label = "Stay",
        )
    }
}

@Composable
private fun StaysSummary(hotels: List<HotelEntity>) {
    val nights = hotels.sumOf { it.nights }
    val currencies = hotels.map { it.currency }.distinct()
    val total = if (currencies.size == 1) Fmt.money(hotels.sumOf { it.totalPrice }, currencies.first()) else null
    val rated = hotels.filter { it.review.overallRating > 0 }
    Column {
        Overline("Accommodation")
        Spacer(Modifier.height(4.dp))
        Text(
            buildString {
                append(if (hotels.size == 1) "1 stay" else "${hotels.size} stays")
                append(" · ").append(Fmt.nights(nights))
                if (total != null && hotels.any { it.totalPrice > 0 }) append(" · ").append(total)
            },
            style = Travel.type.title,
            color = Travel.colors.ink,
        )
        if (rated.isNotEmpty()) {
            val avg = rated.sumOf { it.review.overallRating }.toDouble() / rated.size
            Text("Average rating ${String.format(java.util.Locale.getDefault(), "%.1f", avg)} / 5", style = Travel.type.bodySmall, color = Travel.colors.inkFaint)
        }
    }
}

@Composable
private fun HotelCard(model: HotelCardModel, onClick: () -> Unit) {
    val h = model.hotel
    val c = Travel.colors
    SurfaceCard(onClick = onClick, contentPadding = PaddingValues(0.dp)) {
        Box(Modifier.fillMaxWidth().height(210.dp)) {
            PhotoImage(model.coverPath, Modifier.fillMaxSize(), contentDescription = h.name, fallbackSeed = h.name)
            BottomScrim(0.7f, 0.7f)
            Row(Modifier.align(Alignment.TopStart).padding(Space.md), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Tag(h.paymentStatus.label, color = h.paymentStatus.color(), background = c.cardHigh.copy(alpha = 0.92f))
            }
            if (h.review.wouldStayAgain == true) {
                Row(
                    Modifier.align(Alignment.TopEnd).padding(Space.md).clip(Shapes.pill).background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Favorite, null, tint = Color.White, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Would stay again", style = Travel.type.bodySmall, color = Color.White)
                }
            }
            Column(Modifier.align(Alignment.BottomStart).padding(Space.lg)) {
                Text(h.name, style = Travel.type.title, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(
                    "${Fmt.dateRange(h.checkIn, h.checkOut)} · ${Fmt.nights(h.nights)}",
                    style = Travel.type.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(Space.lg), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (h.review.overallRating > 0) {
                    RatingStars(h.review.overallRating, size = 15.dp)
                } else {
                    Text("Not reviewed yet", style = Travel.type.bodySmall, color = c.inkFaint)
                }
                val sub = listOf(h.roomType, h.address).firstOrNull { it.isNotBlank() }
                if (sub != null) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (sub == h.address) {
                            Icon(Icons.Rounded.Place, null, tint = c.inkFaint, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(3.dp))
                        }
                        Text(sub, style = Travel.type.bodySmall, color = c.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (h.totalPrice > 0) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(Fmt.money(h.totalPrice, h.currency), style = Travel.type.numberSmall, color = c.ink)
                    if (h.pricePerNight > 0) Text("${Fmt.money(h.pricePerNight, h.currency)} / night", style = Travel.type.bodySmall, color = c.inkFaint)
                }
            }
        }
    }
}
