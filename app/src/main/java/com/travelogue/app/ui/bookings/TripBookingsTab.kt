package com.travelogue.app.ui.bookings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelogue.app.core.designsystem.components.*
import com.travelogue.app.core.designsystem.theme.Shapes
import com.travelogue.app.core.designsystem.theme.Space
import com.travelogue.app.core.designsystem.theme.Travel
import com.travelogue.app.core.util.Fmt
import com.travelogue.app.core.util.Intents
import com.travelogue.app.data.db.entity.BookingEntity
import com.travelogue.app.data.db.entity.BookingType
import com.travelogue.app.ui.common.appViewModel
import com.travelogue.app.ui.navigation.Nav
import java.time.LocalDateTime
import java.time.LocalTime

@Composable
fun TripBookingsTab(nav: Nav, tripId: Long, modifier: Modifier = Modifier) {
    val vm = appViewModel(key = "bookings-$tripId") { TripBookingsViewModel(journal, trips, tripId) }
    val items by vm.bookings.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf<BookingType?>(null) }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier.fillMaxSize()) {
        val all = items
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = Space.lg, bottom = navBottom + 96.dp),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            when {
                all == null -> item { LoadingState() }
                all.isEmpty() -> item {
                    EmptyState(
                        icon = Icons.Rounded.ConfirmationNumber,
                        title = "No bookings yet",
                        message = "Flights, trains, ferries and tickets — keep every PNR and confirmation in one place, offline.",
                        actionLabel = "Add a booking",
                        onAction = { nav.bookingEditor(tripId) },
                    )
                }
                else -> {
                    val types = all.map { it.booking.type }.distinct().sortedBy { it.ordinal }
                    if (types.size > 1) {
                        item {
                            ChoiceRow(
                                options = listOf<BookingType?>(null) + types,
                                selected = filter?.takeIf { it in types },
                                label = { it?.label ?: "All · ${all.size}" },
                                onSelect = { filter = it },
                            )
                        }
                    }
                    // A filter whose chip has disappeared (its last booking was deleted) no longer applies.
                    val active = filter?.takeIf { types.size > 1 && it in types }
                    val shown = all.filter { active == null || it.booking.type == active }
                    items(shown, key = { it.booking.id }) { item ->
                        TicketCard(item, { nav.bookingEditor(tripId, item.booking.id) }, Modifier.padding(horizontal = Space.gutter))
                    }
                }
            }
        }
        AccentFab(
            Icons.Rounded.Add,
            { nav.bookingEditor(tripId) },
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(Space.gutter),
            contentDescription = "Add booking",
            label = "Booking",
        )
    }
}

private fun when_(dt: LocalDateTime?): String? = dt?.let {
    if (it.toLocalTime() == LocalTime.MIDNIGHT) Fmt.weekdayShort(it.toLocalDate())
    else "${Fmt.weekdayShort(it.toLocalDate())} · ${Fmt.time(it.toLocalTime())}"
}

private fun timeOnly(dt: LocalDateTime?): String? = dt?.takeIf { it.toLocalTime() != LocalTime.MIDNIGHT }?.let { Fmt.time(it.toLocalTime()) }

/** A boarding-pass style card: header, route, perforation, details stub. */
@Composable
private fun TicketCard(item: BookingItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val b: BookingEntity = item.booking
    val c = Travel.colors
    val context = LocalContext.current
    SurfaceCard(modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(0.dp)) {
        // Header
        Row(Modifier.padding(start = Space.lg, end = Space.lg, top = Space.lg), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(b.type.icon, c.secondary, size = 36.dp, iconSize = 18.dp)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(b.title, style = Travel.type.titleSmall, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = listOf(b.type.label, b.provider).filter { it.isNotBlank() }.joinToString(" · ")
                Text(sub, style = Travel.type.bodySmall, color = c.inkFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Tag(b.paymentStatus.label, color = b.paymentStatus.color())
        }

        // Route
        if (b.fromPlace.isNotBlank() || b.toPlace.isNotBlank()) {
            Row(Modifier.padding(horizontal = Space.lg).padding(top = Space.lg), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(b.fromPlace.ifBlank { "—" }, style = Travel.type.title, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    timeOnly(b.departure)?.let { Text(it, style = Travel.type.bodySmall, color = c.inkSoft) }
                }
                Box(Modifier.width(96.dp).padding(horizontal = Space.sm), contentAlignment = Alignment.Center) {
                    val lineColor = c.inkFaint
                    Canvas(Modifier.fillMaxWidth().height(2.dp)) {
                        drawLine(
                            lineColor, Offset(0f, size.height / 2), Offset(size.width, size.height / 2),
                            strokeWidth = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
                        )
                    }
                    Box(Modifier.size(30.dp).clip(CircleShape).background(c.card), contentAlignment = Alignment.Center) {
                        Icon(b.type.icon, null, tint = c.accent, modifier = Modifier.size(18.dp))
                    }
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(b.toPlace.ifBlank { "—" }, style = Travel.type.title, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
                    timeOnly(b.arrival)?.let { Text(it, style = Travel.type.bodySmall, color = c.inkSoft) }
                }
            }
        }
        val dates = listOfNotNull(when_(b.departure), when_(b.arrival)?.takeIf { b.arrival?.toLocalDate() != b.departure?.toLocalDate() })
        if (dates.isNotEmpty()) {
            Text(
                dates.joinToString("  →  "),
                style = Travel.type.bodySmall,
                color = c.inkFaint,
                modifier = Modifier.padding(horizontal = Space.lg).padding(top = Space.sm),
            )
        }

        // Perforation with notches
        val bg = c.background
        val dash = c.line
        Canvas(Modifier.fillMaxWidth().padding(top = Space.lg).height(20.dp)) {
            val r = size.height / 2
            drawLine(dash, Offset(r * 1.6f, r), Offset(size.width - r * 1.6f, r), strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
            drawCircle(bg, r, Offset(0f, r))
            drawCircle(bg, r, Offset(size.width, r))
        }

        // Stub
        Row(Modifier.padding(start = Space.lg, end = Space.md, top = Space.xs, bottom = Space.md), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Overline(if (b.type == BookingType.FLIGHT || b.type == BookingType.TRAIN) "PNR / Reference" else "Reference")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(b.reference.ifBlank { "—" }, style = Travel.type.label.copy(fontFamily = Travel.type.numberSmall.fontFamily), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (b.reference.isNotBlank()) {
                        Spacer(Modifier.width(4.dp))
                        CircleIconButton(
                            Icons.Rounded.ContentCopy, { Intents.copy(context, "Booking reference", b.reference) },
                            contentDescription = "Copy reference", size = 30.dp, iconSize = 14.dp,
                            background = c.backgroundDeep, tint = c.inkSoft,
                        )
                    }
                }
            }
            if (item.attachments > 0) {
                Row(
                    Modifier.clip(Shapes.pill).background(c.backgroundDeep).padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.AttachFile, null, tint = c.inkSoft, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("${item.attachments}", style = Travel.type.label, color = c.inkSoft)
                }
                Spacer(Modifier.width(Space.md))
            }
            if (b.amount > 0) {
                Text(Fmt.money(b.amount, b.currency), style = Travel.type.numberSmall, color = c.ink)
                Spacer(Modifier.width(Space.xs))
            }
        }
    }
}
