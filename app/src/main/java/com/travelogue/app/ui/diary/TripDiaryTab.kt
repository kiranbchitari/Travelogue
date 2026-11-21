package com.travelogue.app.ui.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelogue.app.core.designsystem.components.*
import com.travelogue.app.core.designsystem.theme.Shapes
import com.travelogue.app.core.designsystem.theme.Space
import com.travelogue.app.core.designsystem.theme.Travel
import com.travelogue.app.ui.common.appViewModel
import com.travelogue.app.ui.memories.StoryDayHeader
import com.travelogue.app.ui.memories.storyDayNumber
import com.travelogue.app.ui.navigation.Nav

@Composable
fun TripDiaryTab(nav: Nav, tripId: Long, modifier: Modifier = Modifier) {
    val vm = appViewModel(key = "diary-tab-$tripId") { TripDiaryViewModel(trips, diary, photos, tripId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = Space.lg, bottom = navBottom + 96.dp),
        ) {
            item(key = "links") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Space.gutter),
                    horizontalArrangement = Arrangement.spacedBy(Space.md),
                ) {
                    StoryLink(
                        Icons.Rounded.Timeline, "Trip story", "Your journey, day by day", Travel.colors.accent,
                        Modifier.weight(1f),
                    ) { nav.tripTimeline(tripId) }
                    StoryLink(
                        Icons.Rounded.PhotoLibrary, "Gallery",
                        if (state.photoCount == 1) "1 photo" else "${state.photoCount} photos",
                        Travel.colors.secondary, Modifier.weight(1f),
                    ) { nav.tripGallery(tripId) }
                }
                Spacer(Modifier.height(Space.xxl))
            }

            if (!state.loaded) {
                item(key = "loading") { LoadingState() }
            } else if (state.entries.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.AutoStories,
                        title = "Your journal awaits",
                        message = "Write down the sunsets, the conversations and the little surprises before they fade.",
                        actionLabel = "Write the first entry",
                        onAction = { nav.diaryEditor(tripId) },
                    )
                }
            } else {
                val byDay = state.entries.groupBy { it.entry.dateTime.toLocalDate() }
                var index = 0
                byDay.forEach { (date, cards) ->
                    item(key = "h-$date") {
                        StoryDayHeader(
                            date, storyDayNumber(date, state.trip),
                            Modifier.padding(bottom = Space.md),
                            trailing = if (cards.size == 1) "1 entry" else "${cards.size} entries",
                        )
                    }
                    cards.forEach { card ->
                        val mirrored = index % 2 == 1
                        index++
                        item(key = card.entry.id) {
                            DiaryEntryCard(
                                model = card,
                                onClick = { nav.diaryEntry(card.entry.id) },
                                onPhotoClick = { nav.photo(tripId, it.id) },
                                mirrored = mirrored,
                                modifier = Modifier.padding(horizontal = Space.gutter).padding(bottom = Space.lg),
                            )
                        }
                    }
                    item(key = "s-$date") { Spacer(Modifier.height(Space.md)) }
                }
            }
        }
        AccentFab(
            Icons.Rounded.EditNote,
            { nav.diaryEditor(tripId) },
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(Space.gutter),
            contentDescription = "New diary entry",
            label = "Write",
        )
    }
}

@Composable
private fun StoryLink(icon: ImageVector, title: String, subtitle: String, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    SurfaceCard(modifier, onClick = onClick, contentPadding = PaddingValues(Space.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, tint, size = 36.dp, iconSize = 18.dp)
            Spacer(Modifier.weight(1f))
            Box(Modifier.clip(Shapes.pill).background(Travel.colors.backgroundDeep).padding(6.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Travel.colors.inkSoft, modifier = Modifier.size(14.dp))
            }
        }
        Spacer(Modifier.height(Space.md))
        Text(title, style = Travel.type.titleSmall, color = Travel.colors.ink)
        Text(subtitle, style = Travel.type.bodySmall, color = Travel.colors.inkFaint, maxLines = 1)
    }
}
