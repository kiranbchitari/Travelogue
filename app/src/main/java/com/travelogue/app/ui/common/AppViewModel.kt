package com.travelogue.app.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.travelogue.app.TravelogueApp
import com.travelogue.app.core.designsystem.theme.Space
import com.travelogue.app.di.AppContainer

/** The app's dependency container, for composables that need it directly (rare). */
@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as TravelogueApp).container

/**
 * Creates a ViewModel scoped to the current navigation destination, built from the [AppContainer].
 * Usage: `val vm = appViewModel(key = "trip-$id") { TripDetailViewModel(trips, expenses, id) }`
 */
@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: AppContainer.() -> VM): VM {
    val container = appContainer()
    return viewModel(
        key = key,
        factory = viewModelFactory {
            initializer {
                // Editors keep their unsaved form across process death; see DraftHolder.
                container.create().also { vm -> if (vm is DraftHolder) keepDraft(vm, createSavedStateHandle()) }
            }
        },
    )
}

/** Content padding for top-level tab screens so the last item clears the floating bottom bar. */
@Composable
fun tabContentPadding(top: Dp = 0.dp): PaddingValues {
    val status = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return PaddingValues(top = status + top, bottom = nav + Space.bottomBarClearance)
}
