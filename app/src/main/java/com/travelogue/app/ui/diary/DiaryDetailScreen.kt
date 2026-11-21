package com.travelogue.app.ui.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelogue.app.core.designsystem.components.*
import com.travelogue.app.core.designsystem.theme.Shapes
import com.travelogue.app.core.designsystem.theme.Space
import com.travelogue.app.core.designsystem.theme.Travel
import com.travelogue.app.core.util.Fmt
import com.travelogue.app.ui.common.appViewModel
import com.travelogue.app.ui.memories.storyDayNumber
import com.travelogue.app.ui.navigation.Nav

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiaryDetailScreen(nav: Nav, entryId: Long) {
    val vm = appViewModel(key = "diary-detail-$entryId") { DiaryDetailViewModel(trips, diary, photos, entryId) }
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val c = Travel.colors

    val entry = state.entry
    if (entry == null) {
        ScreenScaffold("Diary entry", onBack = nav::back) { p ->
            if (!state.loaded) LoadingState(Modifier.padding(p))
            else EmptyState(Icons.Rounded.AutoStories, "Entry not found", "It may have been deleted.", Modifier.padding(p), "Go back", nav::back)
        }
        return
    }
    val hero = state.photos.firstOrNull()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize().background(c.background)) {
        val heroListState = androidx.compose.foundation.lazy.rememberLazyListState()
        LazyColumn(Modifier.fillMaxSize(), state = heroListState, contentPadding = PaddingValues(bottom = navBottom + Space.huge)) {
            item(key = "hero") {
                Box(Modifier.fillMaxWidth().height(if (hero != null) 400.dp else 240.dp)) {
                    if (hero != null) {
                        PhotoImage(
                            hero.relativePath,
                            Modifier.fillMaxSize().clickable { nav.photo(entry.tripId, hero.id) },
                            contentDescription = hero.caption.ifBlank { entry.title },
                        )
                    } else {
                        GradientArt(entry.title + entry.id, Modifier.fillMaxSize())
                    }
                    BottomScrim(0.55f, 0.55f)
                    if (hero != null && hero.caption.isNotBlank()) {
                        Text(
                            hero.caption, style = Travel.type.bodySmall, color = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.align(Alignment.BottomStart).padding(Space.gutter),
                        )
                    }
                }
            }
            item(key = "head") {
                Column(Modifier.padding(horizontal = Space.gutter).padding(top = Space.xxl)) {
                    val date = entry.dateTime.toLocalDate()
                    val day = storyDayNumber(date, state.trip)
                    Overline(
                        listOfNotNull(day?.let { "Day $it" }, Fmt.weekday(date), Fmt.time(entry.dateTime.toLocalTime())).joinToString(" · "),
                        color = c.accent,
                    )
                    Spacer(Modifier.height(Space.sm))
                    Text(entry.title, style = Travel.type.display, color = c.ink)
                    Spacer(Modifier.height(Space.md))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                        entry.mood?.let { Tag("${it.emoji}  ${it.label}", color = c.gold) }
                        if (entry.location.isNotBlank()) Tag(entry.location, color = c.secondary, icon = Icons.Rounded.Place)
                        state.trip?.let { Tag(it.name, color = c.inkSoft, background = c.backgroundDeep) }
                    }
                    if (state.tags.isNotEmpty()) {
                        Spacer(Modifier.height(Space.md))
                        Text(state.tags.joinToString("   ") { "#$it" }, style = Travel.type.label, color = c.secondary)
                    }
                    Spacer(Modifier.height(Space.xl))
                    Hairline()
                    Spacer(Modifier.height(Space.xl))
                }
            }
            item(key = "body") {
                val body = entry.body.trim()
                Column(Modifier.padding(horizontal = Space.gutter + 4.dp)) {
                    if (body.isEmpty()) {
                        Text("No words yet — just the pictures.", style = Travel.type.journal, color = c.inkFaint)
                    } else {
                        // Drop-cap style first letter.
                        val text = buildAnnotatedString {
                            withStyle(SpanStyle(fontSize = 44.sp, fontWeight = FontWeight.Medium, color = c.accent)) { append(body.first()) }
                            append(body.drop(1))
                        }
                        Text(text, style = Travel.type.journal, color = c.ink)
                    }
                }
            }
            val rest = state.photos.drop(1)
            if (rest.isNotEmpty()) {
                item(key = "photos-h") {
                    Spacer(Modifier.height(Space.xxl))
                    Overline("Photographs", Modifier.padding(horizontal = Space.gutter))
                    Spacer(Modifier.height(Space.md))
                }
                rest.forEachIndexed { i, p ->
                    item(key = "p-${p.id}") {
                        // Alternate full-bleed and inset frames for a scrapbook rhythm.
                        val inset = i % 2 == 1
                        Column(Modifier.padding(horizontal = if (inset) Space.huge else Space.gutter).padding(bottom = Space.xl)) {
                            PhotoImage(
                                p.relativePath,
                                Modifier.fillMaxWidth().aspectRatio(if (p.width > 0 && p.height > 0) (p.width.toFloat() / p.height).coerceIn(0.7f, 1.8f) else 1.33f)
                                    .clip(Shapes.md).clickable { nav.photo(entry.tripId, p.id) },
                                contentDescription = p.caption.ifBlank { null },
                            )
                            if (p.caption.isNotBlank()) {
                                Spacer(Modifier.height(Space.sm))
                                Text(p.caption, style = Travel.type.bodySmall.copy(fontFamily = Travel.type.journal.fontFamily), color = c.inkSoft)
                            }
                        }
                    }
                }
            }
            item(key = "foot") {
                Spacer(Modifier.height(Space.xxl))
                Text(
                    "Written ${Fmt.date(java.time.Instant.ofEpochMilli(entry.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate())}",
                    style = Travel.type.bodySmall, color = c.inkFaint, modifier = Modifier.padding(horizontal = Space.gutter),
                )
            }
        }

        // Floating chrome over the hero.
        ScrollStatusScrim(heroListState)
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Space.lg, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, nav::back, contentDescription = "Back", background = c.card.copy(alpha = 0.92f))
            Spacer(Modifier.weight(1f))
            CircleIconButton(Icons.Rounded.Edit, { nav.diaryEditor(entry.tripId, entry.id) }, contentDescription = "Edit", background = c.card.copy(alpha = 0.92f))
            Spacer(Modifier.width(Space.sm))
            CircleIconButton(Icons.Rounded.DeleteOutline, { confirmDelete = true }, contentDescription = "Delete", tint = c.danger, background = c.card.copy(alpha = 0.92f))
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this entry?",
            message = "\"${entry.title}\" and its photos will be permanently removed from this device.",
            confirmLabel = "Delete",
            onConfirm = { vm.delete { nav.back() } },
            onDismiss = { confirmDelete = false },
        )
    }
}
