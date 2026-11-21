package com.travelogue.app.ui.hotels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.travelogue.app.core.util.Intents
import com.travelogue.app.data.db.entity.HotelEntity
import com.travelogue.app.data.db.entity.PhotoOwner
import com.travelogue.app.data.db.entity.TripPhotoEntity
import com.travelogue.app.data.repository.JournalRepository
import com.travelogue.app.data.repository.PhotoRepository
import com.travelogue.app.ui.common.appViewModel
import com.travelogue.app.ui.navigation.Nav
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HotelDetailState(val loading: Boolean = true, val hotel: HotelEntity? = null, val photos: List<TripPhotoEntity> = emptyList())

class HotelDetailViewModel(private val journal: JournalRepository, photos: PhotoRepository, private val hotelId: Long) : ViewModel() {
    val state: StateFlow<HotelDetailState> = combine(journal.observeHotel(hotelId), photos.observeFor(PhotoOwner.HOTEL, hotelId)) { h, p ->
        HotelDetailState(false, h, p)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HotelDetailState())

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            journal.deleteHotel(hotelId)
            onDone()
        }
    }
}

@Composable
fun HotelDetailScreen(nav: Nav, hotelId: Long) {
    val vm = appViewModel(key = "hotel-$hotelId") { HotelDetailViewModel(journal, photos, hotelId) }
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val hotel = state.hotel

    if (hotel == null) {
        ScreenScaffold("Stay", onBack = nav::back) { p ->
            if (state.loading) LoadingState(Modifier.padding(p))
            else EmptyState(Icons.Rounded.Hotel, "Stay not found", "It may have been deleted.", Modifier.padding(p))
        }
        return
    }

    val c = Travel.colors
    Box(Modifier.fillMaxSize().background(c.background)) {
        val heroListState = androidx.compose.foundation.lazy.rememberLazyListState()
        LazyColumn(
            Modifier.fillMaxSize(),
            state = heroListState,
            contentPadding = PaddingValues(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + Space.huge),
        ) {
            item { HeroGallery(hotel, state.photos) { nav.photo(hotel.tripId, it.id) } }
            item { HotelBody(hotel) }
        }
        // Floating top actions over the photo
        ScrollStatusScrim(heroListState)
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Space.lg, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val glass = c.card.copy(alpha = 0.88f)
            CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, nav::back, contentDescription = "Back", background = glass)
            Spacer(Modifier.weight(1f))
            CircleIconButton(Icons.Rounded.Edit, { nav.hotelEditor(hotel.tripId, hotel.id) }, contentDescription = "Edit", background = glass)
            Spacer(Modifier.width(Space.sm))
            CircleIconButton(Icons.Rounded.DeleteOutline, { confirmDelete = true }, contentDescription = "Delete", background = glass, tint = c.danger)
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this stay?",
            message = "\"${hotel.name}\", its review and photos will be removed.",
            confirmLabel = "Delete",
            onConfirm = { vm.delete(nav::back) },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun HeroGallery(hotel: HotelEntity, photos: List<TripPhotoEntity>, onOpen: (TripPhotoEntity) -> Unit) {
    Box(Modifier.fillMaxWidth().height(380.dp)) {
        if (photos.isEmpty()) {
            GradientArt(hotel.name, Modifier.fillMaxSize())
        } else {
            val pager = rememberPagerState { photos.size }
            HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                val p = photos[page]
                PhotoImage(p.relativePath, Modifier.fillMaxSize().clickable { onOpen(p) }, contentDescription = p.caption.ifBlank { hotel.name })
            }
            BottomScrim(0.45f, 0.55f)
            if (photos.size > 1) {
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 46.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    repeat(photos.size.coerceAtMost(10)) { i ->
                        val active = i == pager.currentPage.coerceAtMost(9)
                        Box(
                            Modifier.height(6.dp).width(if (active) 18.dp else 6.dp).clip(Shapes.pill)
                                .background(if (active) Color.White else Color.White.copy(alpha = 0.5f)),
                        )
                    }
                }
            }
        }
        // Paper edge that the body slides under
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(28.dp)
                .clip(Shapes.xl.copy(bottomStart = androidx.compose.foundation.shape.CornerSize(0.dp), bottomEnd = androidx.compose.foundation.shape.CornerSize(0.dp)))
                .background(Travel.colors.background),
        )
    }
}

@Composable
private fun HotelBody(hotel: HotelEntity) {
    val c = Travel.colors
    val context = LocalContext.current
    val r = hotel.review
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.gutter)) {
        Overline(listOf(hotel.bookingPlatform, hotel.roomType).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Stay" })
        Spacer(Modifier.height(6.dp))
        Text(hotel.name, style = Travel.type.display, color = c.ink)
        if (hotel.address.isNotBlank()) {
            Spacer(Modifier.height(Space.sm))
            Row(
                Modifier.clip(Shapes.sm).clickable { Intents.searchAddress(context, hotel.address) }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Place, null, tint = c.accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(hotel.address, style = Travel.type.body, color = c.inkSoft)
            }
        }

        Spacer(Modifier.height(Space.xl))
        // Stay strip
        SurfaceCard(contentPadding = PaddingValues(vertical = Space.lg, horizontal = Space.sm)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                StayCell("Check-in", Fmt.weekdayShort(hotel.checkIn), Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(36.dp).background(c.line))
                StayCell("Check-out", Fmt.weekdayShort(hotel.checkOut), Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(36.dp).background(c.line))
                StayCell("Nights", hotel.nights.toString(), Modifier.weight(1f))
            }
        }

        // Quick actions
        if (hotel.phone.isNotBlank() || hotel.address.isNotBlank()) {
            Spacer(Modifier.height(Space.lg))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                if (hotel.phone.isNotBlank()) {
                    QuickAction(Icons.Rounded.Call, "Call", { Intents.dial(context, hotel.phone) })
                    QuickAction(Icons.Rounded.ContentCopy, "Copy number", { Intents.copy(context, "Phone", hotel.phone) }, tint = c.secondary)
                }
                if (hotel.address.isNotBlank()) {
                    QuickAction(Icons.Rounded.Map, "Map", { Intents.searchAddress(context, hotel.address) }, tint = c.sage)
                }
                if (hotel.bookingReference.isNotBlank()) {
                    QuickAction(Icons.Rounded.ConfirmationNumber, "Copy ref", { Intents.copy(context, "Booking reference", hotel.bookingReference) }, tint = c.gold)
                }
            }
        }

        // Ratings
        val subRatings = listOf("Cleanliness" to r.cleanlinessRating, "Location" to r.locationRating, "Service" to r.serviceRating, "Value for money" to r.valueRating)
        if (r.overallRating > 0 || subRatings.any { it.second > 0 }) {
            Spacer(Modifier.height(Space.xxxl))
            SectionHeader("My rating")
            Spacer(Modifier.height(Space.lg))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(112.dp)) {
                    Text(if (r.overallRating > 0) "${r.overallRating}.0" else "–", style = Travel.type.display, color = c.ink)
                    RatingStars(r.overallRating, size = 14.dp)
                    Spacer(Modifier.height(4.dp))
                    Text("Overall", style = Travel.type.bodySmall, color = c.inkFaint)
                }
                Spacer(Modifier.width(Space.lg))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    subRatings.forEach { (label, v) ->
                        Column {
                            Row {
                                Text(label, style = Travel.type.bodySmall, color = c.inkSoft, modifier = Modifier.weight(1f))
                                Text(if (v > 0) "$v" else "–", style = Travel.type.label, color = c.ink)
                            }
                            Spacer(Modifier.height(4.dp))
                            ProgressBar(v / 5f, color = c.gold, height = 6.dp)
                        }
                    }
                }
            }
        }

        // Review
        if (r.review.isNotBlank()) {
            Spacer(Modifier.height(Space.xxxl))
            Text("“", style = Travel.type.display.copy(fontSize = Travel.type.display.fontSize * 1.6f), color = c.accent.copy(alpha = 0.5f), modifier = Modifier.height(44.dp))
            Text(r.review, style = Travel.type.journal, color = c.ink)
        }

        val pros = r.pros.lines().map { it.trim().trimStart('-', '•', '*').trim() }.filter { it.isNotEmpty() }
        val cons = r.cons.lines().map { it.trim().trimStart('-', '•', '*').trim() }.filter { it.isNotEmpty() }
        if (pros.isNotEmpty() || cons.isNotEmpty()) {
            Spacer(Modifier.height(Space.xxl))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
                if (pros.isNotEmpty()) ProsCons("Loved", pros, Icons.Rounded.Check, c.success, Modifier.weight(1f))
                if (cons.isNotEmpty()) ProsCons("Not so much", cons, Icons.Rounded.Close, c.danger, Modifier.weight(1f))
            }
        }

        r.wouldStayAgain?.let { again ->
            Spacer(Modifier.height(Space.xxl))
            Row(
                Modifier.fillMaxWidth().clip(Shapes.lg).background((if (again) c.success else c.danger).copy(alpha = 0.1f)).padding(Space.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (again) Icons.Rounded.Favorite else Icons.Rounded.HeartBroken, null, tint = if (again) c.success else c.danger)
                Spacer(Modifier.width(Space.md))
                Text(
                    if (again) "I'd happily stay here again." else "Not one I'd return to.",
                    style = Travel.type.titleSmall,
                    color = c.ink,
                )
            }
        }

        if (hotel.amenityList.isNotEmpty()) {
            Spacer(Modifier.height(Space.xxxl))
            SectionHeader("Amenities")
            Spacer(Modifier.height(Space.md))
            AmenityFlow(hotel.amenityList)
        }

        // Booking information
        Spacer(Modifier.height(Space.xxxl))
        SectionHeader("Booking")
        Spacer(Modifier.height(Space.sm))
        SurfaceCard(contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.sm)) {
            if (hotel.bookingReference.isNotBlank()) {
                InfoRow("Reference", hotel.bookingReference, icon = Icons.Rounded.ContentCopy,
                    onClick = { Intents.copy(context, "Booking reference", hotel.bookingReference) })
                Hairline()
            }
            if (hotel.bookingPlatform.isNotBlank()) {
                InfoRow("Booked via", hotel.bookingPlatform, icon = Icons.Rounded.Language)
                Hairline()
            }
            if (hotel.roomType.isNotBlank()) {
                InfoRow("Room", hotel.roomType, icon = Icons.Rounded.Bed)
                Hairline()
            }
            InfoRow("Dates", Fmt.dateRange(hotel.checkIn, hotel.checkOut), icon = Icons.Rounded.CalendarMonth)
            Hairline()
            if (hotel.pricePerNight > 0) {
                InfoRow("Per night", Fmt.money(hotel.pricePerNight, hotel.currency), icon = Icons.Rounded.NightsStay)
                Hairline()
            }
            InfoRow("Total", if (hotel.totalPrice > 0) Fmt.money(hotel.totalPrice, hotel.currency) else "—", icon = Icons.Rounded.Payments)
            Hairline()
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Verified, null, tint = c.inkFaint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Space.md))
                Text("Payment", style = Travel.type.body, color = c.inkSoft, modifier = Modifier.weight(1f))
                Tag(hotel.paymentStatus.label, color = hotel.paymentStatus.color())
            }
            if (hotel.phone.isNotBlank()) {
                Hairline()
                InfoRow("Phone", hotel.phone, icon = Icons.Rounded.Call, onClick = { Intents.dial(context, hotel.phone) })
            }
        }
    }
}

@Composable
private fun StayCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = Travel.type.bodySmall, color = Travel.colors.inkFaint)
        Spacer(Modifier.height(2.dp))
        Text(value, style = Travel.type.titleSmall, color = Travel.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ProsCons(title: String, items: List<String>, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Overline(title, color = tint)
        Spacer(Modifier.height(Space.sm))
        items.forEach { item ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 2.dp).size(18.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = tint, modifier = Modifier.size(12.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(item, style = Travel.type.body, color = Travel.colors.ink)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AmenityFlow(items: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        items.forEach { Tag(it, color = Travel.colors.secondary) }
    }
}
