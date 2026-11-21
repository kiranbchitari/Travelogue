package com.travelogue.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.travelogue.app.core.designsystem.components.*
import com.travelogue.app.core.designsystem.theme.Shapes
import com.travelogue.app.core.designsystem.theme.Space
import com.travelogue.app.core.designsystem.theme.Travel
import com.travelogue.app.data.db.entity.TravellerEntity
import com.travelogue.app.data.repository.TripRepository
import com.travelogue.app.ui.common.appViewModel
import com.travelogue.app.ui.navigation.Nav
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class TravellerRow(val traveller: TravellerEntity, val tripCount: Int)

class TravellersViewModel(private val trips: TripRepository) : ViewModel() {
    val rows: StateFlow<List<TravellerRow>> = combine(trips.observeAllTravellers(), trips.observeAllTripTravellers()) { all, links ->
        val counts = links.groupingBy { it.travellerId }.eachCount()
        all.map { TravellerRow(it, counts[it.id] ?: 0) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun add(name: String, onDone: (String?) -> Unit) = viewModelScope.launch {
        if (name.isBlank()) { onDone("Enter a name"); return@launch }
        if (rows.value.any { it.traveller.name.equals(name.trim(), true) }) { onDone("Already in your list"); return@launch }
        trips.addTraveller(name); onDone(null)
    }

    fun rename(t: TravellerEntity, name: String, onDone: (String?) -> Unit) = viewModelScope.launch {
        if (name.isBlank()) { onDone("Enter a name"); return@launch }
        if (rows.value.any { it.traveller.id != t.id && it.traveller.name.equals(name.trim(), true) }) { onDone("Another traveller has this name"); return@launch }
        trips.renameTraveller(t, name); onDone(null)
    }

    fun delete(t: TravellerEntity, onDone: (Boolean) -> Unit) = viewModelScope.launch { onDone(trips.deleteTraveller(t.id)) }
}

@Composable
fun ManageTravellersScreen(nav: Nav) {
    val vm = appViewModel { TravellersViewModel(trips) }
    val rows by vm.rows.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var newName by remember { mutableStateOf("") }
    var addError by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<TravellerEntity?>(null) }
    var deleting by remember { mutableStateOf<TravellerRow?>(null) }

    ScreenScaffold("Travellers", onBack = nav::back) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, top = padding.calculateTopPadding() + Space.sm, bottom = padding.calculateBottomPadding() + Space.huge),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            item {
                Text(
                    "People you travel with. Add them here or straight from a trip; they're shared across trips so expense splits stay consistent.",
                    style = Travel.type.body, color = Travel.colors.inkSoft,
                )
                Spacer(Modifier.height(Space.lg))
                FormField(
                    "Add a traveller", newName, { newName = it; addError = null }, placeholder = "Name",
                    capitalization = KeyboardCapitalization.Words, error = addError, leadingIcon = Icons.Rounded.PersonAdd,
                    trailing = {
                        TextButton(onClick = { vm.add(newName) { e -> if (e == null) newName = "" else addError = e } }, enabled = newName.isNotBlank()) {
                            Text("Add", color = Travel.colors.accent)
                        }
                    },
                )
                Spacer(Modifier.height(Space.md))
            }
            if (rows.isEmpty()) {
                item { EmptyState(Icons.Rounded.Group, "No travellers yet", "Add the people you travel with to split expenses and see who owes whom.") }
            }
            items(rows, key = { it.traveller.id }) { r ->
                SurfaceCard(Modifier.fillMaxWidth(), onClick = { editing = r.traveller }, contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        InitialAvatar(r.traveller.name, size = 42.dp)
                        Spacer(Modifier.width(Space.md))
                        Column(Modifier.weight(1f)) {
                            Text(r.traveller.name, style = Travel.type.titleSmall, color = Travel.colors.ink)
                            Text(
                                when (r.tripCount) { 0 -> "Not on any trip"; 1 -> "On 1 trip"; else -> "On ${r.tripCount} trips" },
                                style = Travel.type.bodySmall, color = Travel.colors.inkFaint,
                            )
                        }
                        Text("Delete", style = Travel.type.label, color = Travel.colors.danger, modifier = Modifier.clip(Shapes.pill).clickable { deleting = r }.padding(8.dp))
                    }
                }
            }
        }
    }

    editing?.let { t ->
        var name by remember(t.id) { mutableStateOf(t.name) }
        var err by remember(t.id) { mutableStateOf<String?>(null) }
        TravelSheet({ editing = null }, title = "Rename traveller") {
            FormField("Name", name, { name = it; err = null }, error = err, capitalization = KeyboardCapitalization.Words)
            Spacer(Modifier.height(Space.xl))
            PrimaryButton("Save", { vm.rename(t, name) { e -> if (e == null) editing = null else err = e } }, Modifier.fillMaxWidth())
        }
    }
    deleting?.let { r ->
        ConfirmDialog(
            "Delete ${r.traveller.name}?",
            if (r.tripCount > 0) "${r.traveller.name} is part of ${r.tripCount} trip(s). Remove them from those trips first." else "They will be removed from your traveller list.",
            "Delete",
            { vm.delete(r.traveller) { ok -> if (!ok) Toast.makeText(context, "Remove ${r.traveller.name} from their trips first", Toast.LENGTH_LONG).show() } },
            { deleting = null },
        )
    }
}
