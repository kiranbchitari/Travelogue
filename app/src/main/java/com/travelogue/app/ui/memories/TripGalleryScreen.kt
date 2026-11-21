package com.travelogue.app.ui.memories

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.travelogue.app.core.designsystem.components.*
import com.travelogue.app.core.designsystem.theme.Space
import com.travelogue.app.core.designsystem.theme.Travel
import com.travelogue.app.core.storage.StorageFullException
import com.travelogue.app.data.db.entity.PhotoOwner
import com.travelogue.app.data.db.entity.TripEntity
import com.travelogue.app.data.db.entity.TripPhotoEntity
import com.travelogue.app.data.repository.PhotoRepository
import com.travelogue.app.data.repository.TripRepository
import com.travelogue.app.ui.common.appViewModel
import com.travelogue.app.ui.navigation.Nav
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

private enum class GalleryFilter(val label: String) {
    ALL("All"), FAVOURITES("Favourites"), DIARY("Diary"), HOTELS("Hotels"), PLACES("Places"), GALLERY("Gallery");

    fun matches(p: TripPhotoEntity): Boolean = when (this) {
        ALL -> true
        FAVOURITES -> p.isFavourite
        DIARY -> p.ownerType == PhotoOwner.DIARY
        HOTELS -> p.ownerType == PhotoOwner.HOTEL
        PLACES -> p.ownerType == PhotoOwner.PLACE
        GALLERY -> p.ownerType == PhotoOwner.GENERAL || p.ownerType == PhotoOwner.ACTIVITY
    }
}

data class TripGalleryState(val trip: TripEntity? = null, val photos: List<TripPhotoEntity> = emptyList(), val loaded: Boolean = false)

class TripGalleryViewModel(trips: TripRepository, private val photos: PhotoRepository, private val tripId: Long) : ViewModel() {
    val state: StateFlow<TripGalleryState> = combine(trips.observeTrip(tripId), photos.observeForTrip(tripId)) { t, p ->
        TripGalleryState(t, p, true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TripGalleryState())

    private val _adding = MutableStateFlow(0)
    /** Number of photos currently being copied in. */
    val adding: StateFlow<Int> = _adding
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    fun add(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _adding.value += uris.size
        viewModelScope.launch {
            try {
                photos.addPhotos(tripId, uris, PhotoOwner.GENERAL, null, defaultPhotoTime(state.value.trip))
                messages.tryEmit(if (uris.size == 1) "Photo added" else "${uris.size} photos added")
            } catch (e: StorageFullException) {
                messages.tryEmit(e.message ?: "Storage is full")
            } catch (e: Exception) {
                messages.tryEmit("Couldn't add photos: ${e.message ?: "unknown error"}")
            } finally {
                _adding.value -= uris.size
            }
        }
    }
}

@Composable
fun TripGalleryScreen(nav: Nav, tripId: Long) {
    val vm = appViewModel(key = "gallery-$tripId") { TripGalleryViewModel(trips, photos, tripId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val adding by vm.adding.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(GalleryFilter.ALL) }
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } }

    val pick = rememberImagePicker(true) { vm.add(it) }
    val camera = rememberCameraCapture { vm.add(listOf(it)) }
    val trip = state.trip
    val shown = state.photos.filter(filter::matches)
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    ScreenScaffold(
        title = "Gallery",
        subtitle = trip?.let { "${it.name} · ${state.photos.size} photo${if (state.photos.size == 1) "" else "s"}" },
        onBack = nav::back,
        actions = { CircleIconButton(Icons.Rounded.PhotoCamera, camera, contentDescription = "Take a photo") },
        floatingAction = {
            AccentFab(Icons.Rounded.AddPhotoAlternate, pick, Modifier.navigationBarsPadding(), "Add photos", label = if (adding > 0) "Adding $adding…" else "Add photos")
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(bottom = navBottom + 100.dp),
        ) {
            item(key = "filters") {
                ChoiceRow(GalleryFilter.entries, filter, { it.label }, { filter = it })
                Spacer(Modifier.height(Space.lg))
            }
            if (adding > 0) item(key = "adding") { LoadingState() }
            if (!state.loaded) {
                item(key = "loading") { LoadingState() }
            } else if (shown.isEmpty()) {
                item(key = "empty") {
                    if (filter == GalleryFilter.ALL) {
                        EmptyState(
                            Icons.Rounded.PhotoLibrary, "An empty album",
                            "Add the photographs that tell this trip's story. They're copied safely into the app, nothing is uploaded.",
                            actionLabel = "Add photos", onAction = pick,
                        )
                    } else {
                        EmptyState(Icons.Rounded.PhotoLibrary, "Nothing here", "No ${filter.label.lowercase()} photos for this trip yet.")
                    }
                }
            } else {
                val byDay = shown.groupBy { it.takenAt.toLocalDate() }.toSortedMap()
                byDay.forEach { (date, list) ->
                    item(key = "d-$date") {
                        StoryDayHeader(
                            date, storyDayNumber(date, trip),
                            Modifier.padding(top = Space.sm, bottom = Space.md),
                            trailing = if (list.size == 1) "1 photo" else "${list.size} photos",
                        )
                    }
                    photoGridRows(list, "g-$date") { nav.photo(tripId, it.id) }
                    item(key = "ds-$date") { Spacer(Modifier.height(Space.lg)) }
                }
                item(key = "foot") {
                    Text(
                        "Photos are stored privately on this device.",
                        style = Travel.type.bodySmall, color = Travel.colors.inkFaint,
                        modifier = Modifier.fillMaxWidth().padding(Space.gutter),
                    )
                }
            }
        }
    }
}
