package com.travelogue.app.ui.memories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.travelogue.app.core.designsystem.components.PhotoImage
import com.travelogue.app.core.designsystem.theme.Shapes
import com.travelogue.app.core.designsystem.theme.Space
import com.travelogue.app.core.designsystem.theme.Travel
import com.travelogue.app.data.db.entity.TripPhotoEntity

/** One photo with optional "+N" overlay and favourite heart. */
@Composable
fun PhotoTile(
    photo: TripPhotoEntity,
    modifier: Modifier = Modifier,
    shape: Shape = Shapes.xs,
    overlayText: String? = null,
    showFavourite: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Box(modifier.clip(shape).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) {
        PhotoImage(photo.relativePath, Modifier.fillMaxSize(), contentDescription = photo.caption.ifBlank { null })
        if (overlayText != null) {
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.42f)), contentAlignment = Alignment.Center) {
                Text(overlayText, style = Travel.type.title, color = Color.White)
            }
        }
        if (showFavourite && photo.isFavourite) {
            Icon(
                Icons.Rounded.Favorite, "Favourite", tint = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(16.dp),
            )
        }
    }
}

/**
 * Collage of up to three photos: one full, two side-by-side, or one large + two stacked.
 * [mirrored] flips the layout so consecutive collages alternate.
 */
@Composable
fun PhotoCollage(
    photos: List<TripPhotoEntity>,
    modifier: Modifier = Modifier,
    height: Dp = 220.dp,
    gap: Dp = 3.dp,
    mirrored: Boolean = false,
    tileShape: Shape = androidx.compose.ui.graphics.RectangleShape,
    onPhotoClick: ((TripPhotoEntity) -> Unit)? = null,
) {
    if (photos.isEmpty()) return
    val shown = photos.take(3)
    val extra = photos.size - shown.size
    fun click(p: TripPhotoEntity): (() -> Unit)? = onPhotoClick?.let { { it(p) } }
    Row(modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(gap)) {
        when (shown.size) {
            1 -> PhotoTile(shown[0], Modifier.fillMaxSize(), tileShape, onClick = click(shown[0]))
            2 -> {
                PhotoTile(shown[0], Modifier.weight(if (mirrored) 1f else 1.4f).fillMaxHeight(), tileShape, onClick = click(shown[0]))
                PhotoTile(shown[1], Modifier.weight(if (mirrored) 1.4f else 1f).fillMaxHeight(), tileShape, onClick = click(shown[1]))
            }
            else -> {
                if (!mirrored) PhotoTile(shown[0], Modifier.weight(1.7f).fillMaxHeight(), tileShape, onClick = click(shown[0]))
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    PhotoTile(shown[1], Modifier.weight(1f).fillMaxWidth(), tileShape, onClick = click(shown[1]))
                    PhotoTile(
                        shown[2], Modifier.weight(1f).fillMaxWidth(), tileShape,
                        overlayText = if (extra > 0) "+$extra" else null, onClick = click(shown[2]),
                    )
                }
                if (mirrored) PhotoTile(shown[0], Modifier.weight(1.7f).fillMaxHeight(), tileShape, onClick = click(shown[0]))
            }
        }
    }
}

private enum class MosaicPattern(val count: Int) { BIG_LEFT(3), TRIO(3), PAIR(2), BIG_RIGHT(3), SINGLE(1) }

private class MosaicRowData(val pattern: MosaicPattern, val photos: List<TripPhotoEntity>)

private fun mosaicRows(photos: List<TripPhotoEntity>): List<MosaicRowData> {
    val cycle = listOf(MosaicPattern.BIG_LEFT, MosaicPattern.TRIO, MosaicPattern.PAIR, MosaicPattern.BIG_RIGHT, MosaicPattern.TRIO)
    val out = mutableListOf<MosaicRowData>()
    var i = 0
    var c = 0
    while (i < photos.size) {
        val remaining = photos.size - i
        var p = cycle[c % cycle.size]
        if (p.count > remaining) p = if (remaining == 2) MosaicPattern.PAIR else MosaicPattern.SINGLE
        out += MosaicRowData(p, photos.subList(i, i + p.count))
        i += p.count
        c++
    }
    return out
}

/**
 * Editorial mixed-size photo grid emitted as LazyColumn rows (no nested grids):
 * big + stacked, three squares, two wide, mirrored big + stacked…
 */
fun LazyListScope.photoMosaic(
    photos: List<TripPhotoEntity>,
    keyPrefix: String,
    onClick: (TripPhotoEntity) -> Unit,
) {
    val rows = mosaicRows(photos)
    items(rows.size, key = { "$keyPrefix-${rows[it].photos.first().id}" }) { i -> MosaicRow(rows[i], onClick) }
}

@Composable
private fun MosaicRow(row: MosaicRowData, onClick: (TripPhotoEntity) -> Unit) {
    val gap = 4.dp
    val base = Modifier.fillMaxWidth().padding(horizontal = Space.gutter).padding(bottom = gap)
    val p = row.photos
    when (row.pattern) {
        MosaicPattern.BIG_LEFT, MosaicPattern.BIG_RIGHT -> Row(base.height(250.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
            val left = row.pattern == MosaicPattern.BIG_LEFT
            if (left) PhotoTile(p[0], Modifier.weight(2f).fillMaxHeight()) { onClick(p[0]) }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                PhotoTile(p[1], Modifier.weight(1f).fillMaxWidth()) { onClick(p[1]) }
                PhotoTile(p[2], Modifier.weight(1f).fillMaxWidth()) { onClick(p[2]) }
            }
            if (!left) PhotoTile(p[0], Modifier.weight(2f).fillMaxHeight()) { onClick(p[0]) }
        }
        MosaicPattern.TRIO -> Row(base.height(124.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
            p.forEach { ph -> PhotoTile(ph, Modifier.weight(1f).fillMaxHeight()) { onClick(ph) } }
        }
        MosaicPattern.PAIR -> Row(base.height(180.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
            PhotoTile(p[0], Modifier.weight(1.25f).fillMaxHeight()) { onClick(p[0]) }
            PhotoTile(p[1], Modifier.weight(1f).fillMaxHeight()) { onClick(p[1]) }
        }
        MosaicPattern.SINGLE -> PhotoTile(p[0], base.height(230.dp)) { onClick(p[0]) }
    }
}

/** Square-tile grid rows (3 per row) for galleries, emitted as LazyColumn items. */
fun LazyListScope.photoGridRows(
    photos: List<TripPhotoEntity>,
    keyPrefix: String,
    columns: Int = 3,
    onClick: (TripPhotoEntity) -> Unit,
) {
    val rows = photos.chunked(columns)
    items(rows.size, key = { "$keyPrefix-${rows[it].first().id}" }) { i ->
        val gap = 4.dp
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.gutter).padding(bottom = gap),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            rows[i].forEach { ph ->
                PhotoTile(ph, Modifier.weight(1f).aspectRatio(1f)) { onClick(ph) }
            }
            repeat(columns - rows[i].size) { Spacer(Modifier.weight(1f)) }
        }
    }
}
