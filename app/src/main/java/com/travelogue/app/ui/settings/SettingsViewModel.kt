package com.travelogue.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelogue.app.core.designsystem.theme.ThemeMode
import com.travelogue.app.core.storage.StorageUsage
import com.travelogue.app.data.settings.AppSettings
import com.travelogue.app.di.AppContainer
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings> = c.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    private val _usage = MutableStateFlow<StorageUsage?>(null)
    val usage: StateFlow<StorageUsage?> = _usage

    val counts: StateFlow<Pair<Int, Int>> = combine(c.trips.observeTrips(), c.photos.observeAll()) { t, p -> t.size to p.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0 to 0)

    val hasDemo: StateFlow<Boolean> = c.trips.observeTrips().map { list -> list.any { it.uuid.startsWith(DEMO_PREFIX) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy

    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val message: SharedFlow<String> = _message

    init {
        refreshUsage()
        // Keep the storage figure current as photos are added/removed.
        viewModelScope.launch { c.photos.observeAll().drop(1).collect { refreshUsage() } }
    }

    fun refreshUsage() = viewModelScope.launch { _usage.value = runCatching { c.media.usage() }.getOrNull() }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { c.settings.setTheme(mode) }
    fun setCurrency(code: String) = viewModelScope.launch { c.settings.setDefaultCurrency(code) }

    fun loadDemo() = viewModelScope.launch {
        _busy.value = "Creating sample trips…"
        runCatching { c.demo.seed() }
            .onSuccess { _message.tryEmit("Sample trips added. Delete them any time from here.") }
            .onFailure { _message.tryEmit("Couldn't create sample data: ${it.message}") }
        _busy.value = null
        refreshUsage()
    }

    fun removeDemo() = viewModelScope.launch {
        _busy.value = "Removing sample trips…"
        runCatching { c.demo.remove() }.onSuccess { _message.tryEmit("Sample trips removed.") }
        _busy.value = null
        refreshUsage()
    }

    fun clearAll() = viewModelScope.launch {
        _busy.value = "Clearing all data…"
        runCatching { c.backup.clearAllData() }
            .onSuccess { _message.tryEmit("All data cleared.") }
            .onFailure { _message.tryEmit("Couldn't clear data: ${it.message}") }
        _busy.value = null
        refreshUsage()
    }

    companion object {
        const val DEMO_PREFIX = "demo-"
    }
}
