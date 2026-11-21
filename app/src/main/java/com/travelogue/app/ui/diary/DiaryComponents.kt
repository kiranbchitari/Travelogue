package com.travelogue.app.ui.diary

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.travelogue.app.core.designsystem.components.Overline
import com.travelogue.app.core.designsystem.components.SurfaceCard
import com.travelogue.app.core.designsystem.components.Tag
import com.travelogue.app.core.designsystem.theme.Space
import com.travelogue.app.core.designsystem.theme.Travel
import com.travelogue.app.core.util.Fmt
import com.travelogue.app.data.db.entity.TripPhotoEntity
import com.travelogue.app.ui.memories.PhotoCollage

/**
 * Editorial diary card: photo collage on top, then time overline, serif title, mood, location,
 * excerpt and tags.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiaryEntryCard(
    model: DiaryCardModel,
    onClick: () -> Unit,
    onPhotoClick: (TripPhotoEntity) -> Unit,
    modifier: Modifier = Modifier,
    mirrored: Boolean = false,
    showDate: Boolean = false,
) {
    val e = model.entry
    SurfaceCard(modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(0.dp)) {
        if (model.photos.isNotEmpty()) {
            PhotoCollage(model.photos, height = if (model.photos.size == 1) 210.dp else 230.dp, mirrored = mirrored, onPhotoClick = onPhotoClick)
        }
        Column(Modifier.padding(horizontal = Space.lg, vertical = Space.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val when_ = if (showDate) "${Fmt.date(e.dateTime.toLocalDate())} · ${Fmt.time(e.dateTime.toLocalTime())}" else Fmt.time(e.dateTime.toLocalTime())
                Overline(listOfNotNull(model.tripName, when_).joinToString(" · "), Modifier.weight(1f))
                e.mood?.let {
                    Text(it.emoji, style = Travel.type.titleSmall)
                    Spacer(Modifier.width(4.dp))
                    Text(it.label, style = Travel.type.bodySmall, color = Travel.colors.inkFaint)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(e.title, style = Travel.type.title, color = Travel.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (e.location.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Place, null, tint = Travel.colors.accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(e.location, style = Travel.type.bodySmall, color = Travel.colors.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (e.body.isNotBlank()) {
                Spacer(Modifier.height(Space.sm))
                Text(
                    e.body.trim(),
                    style = Travel.type.body.copy(fontFamily = Travel.type.journal.fontFamily, fontSize = Travel.type.bodyLarge.fontSize),
                    color = Travel.colors.inkSoft,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (model.tags.isNotEmpty()) {
                Spacer(Modifier.height(Space.md))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    model.tags.forEach { Tag("#$it", color = Travel.colors.secondary) }
                }
            }
        }
    }
}
