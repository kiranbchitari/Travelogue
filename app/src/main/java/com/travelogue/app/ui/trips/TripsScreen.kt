package com.travelogue.app.ui.trips

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Luggage
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelogue.app.core.designsystem.components.*
import com.travelogue.app.core.designsystem.theme.Shapes
import com.travelogue.app.core.designsystem.theme.Space
import com.travelogue.app.core.designsystem.theme.Travel
import com.travelogue.app.ui.backup.rememberBackupImporter
import com.travelogue.app.ui.common.appViewModel
import com.travelogue.app.ui.common.tabContentPadding
import com.travelogue.app.ui.navigation.Nav

@Composable
fun TripsScreen(nav: Nav) {
    val vm = appViewModel(key = "trips") { TripsViewModel(this) }
    val s by vm.state.collectAsStateWithLifecycle()
    val importBackup = rememberBackupImporter()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = tabContentPadding()) {
            item {
                LargeHeader(
                    overline = if (s.totalCount == 1) "1 journey" else "${s.totalCount} journeys",
                    title = "My Trips",
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            CircleIconButton(Icons.Rounded.FileOpen, importBackup, contentDescription = "Import trip")
                            CircleIconButton(
                                Icons.Rounded.Add, nav::newTrip, contentDescription = "New trip",
                                background = Travel.colors.ink, tint = Travel.colors.background,
                            )
                        }
                    },
                )
            }

            if (!s.loaded) {
                item { LoadingState() }
                return@LazyColumn
            }

            if (s.totalCount == 0) {
                item {
                    EmptyState(
                        Icons.Rounded.Luggage,
                        "No trips yet",
                        "Create your first trip, or import one you exported earlier from Travelogue.",
                        actionLabel = "Create a trip",
                        onAction = nav::newTrip,
                    )
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        SecondaryButton("Import a trip", importBackup, icon = Icons.Rounded.FileOpen)
                    }
                }
                return@LazyColumn
            }

            item {
                SearchField(s.query, vm::setQuery, Modifier.padding(horizontal = Space.gutter).padding(top = Space.sm))
                Spacer(Modifier.height(Space.md))
                ChoiceRow(TripFilter.entries, s.filter, { it.label }, vm::setFilter)
                Spacer(Modifier.height(Space.xl))
            }

            if (s.trips.isEmpty()) {
                item {
                    EmptyState(
                        if (s.query.isNotBlank()) Icons.Rounded.SearchOff else Icons.Rounded.Luggage,
                        if (s.query.isNotBlank()) "Nothing matches “${s.query}”" else "No ${s.filter.label.lowercase()} trips",
                        if (s.query.isNotBlank()) "Try a different name, destination or country." else "Trips with this status will appear here.",
                    )
                }
            } else {
                items(s.trips, key = { it.trip.id }) { t ->
                    TripHeroCard(
                        t,
                        onClick = { nav.trip(t.trip.id) },
                        modifier = Modifier.padding(horizontal = Space.gutter).padding(bottom = Space.lg).animateItem(),
                        height = 280.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    TextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth().border(1.dp, Travel.colors.line, Shapes.md),
        placeholder = { Text("Search trips, places, countries", style = Travel.type.body) },
        textStyle = Travel.type.bodyLarge,
        singleLine = true,
        shape = Shapes.md,
        colors = fieldColors(),
        leadingIcon = { Icon(Icons.Rounded.Search, null, modifier = Modifier.size(20.dp)) },
        trailingIcon = if (value.isNotEmpty()) {
            { IconButton(onClick = { onChange("") }) { Icon(Icons.Rounded.Close, "Clear search", modifier = Modifier.size(18.dp)) } }
        } else null,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    )
}
