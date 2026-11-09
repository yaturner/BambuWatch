package com.das.bambuwatch.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.das.bambuwatch.BambuWatchApp
import com.das.bambuwatch.data.ConfigFileStatus
import com.das.bambuwatch.shared.PrinterConfig
import com.das.bambuwatch.monitor.PrinterMonitorService
import com.das.bambuwatch.shared.PrinterState
import com.das.bambuwatch.watch.WatchLinkStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ConfigUiState {
    data object Loading : ConfigUiState
    data class Loaded(val config: PrinterConfig?) : ConfigUiState
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as BambuWatchApp
    private val repository = app.printerRepository
    private val store = app.configStore

    val config: StateFlow<ConfigUiState> = store.config
        .map<PrinterConfig?, ConfigUiState> { ConfigUiState.Loaded(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ConfigUiState.Loading)

    val monitoringEnabled: StateFlow<Boolean> = store.monitoringEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val printerState: StateFlow<PrinterState> = repository.state

    val fileStatus: StateFlow<ConfigFileStatus> = store.fileStatus

    val watchLink: StateFlow<WatchLinkStatus> = app.watchSync.link

    /** Pre-fill for the setup form when nothing is saved (debug builds with printer.properties). */
    val setupDefaults: PrinterConfig? = store.setupDefaults

    private val _editing = MutableStateFlow(false)
    val editing: StateFlow<Boolean> = _editing.asStateFlow()

    init {
        // The service owns the connection. Starting it here (app in foreground) is always allowed;
        // if it's already running this is a no-op.
        viewModelScope.launch {
            store.restore()
            val saved = store.config.first()
            if (saved?.isComplete == true && store.monitoringEnabled.first()) {
                PrinterMonitorService.start(app)
            }
        }
    }

    fun save(config: PrinterConfig) {
        viewModelScope.launch {
            store.save(config)
            store.setMonitoringEnabled(true)
            _editing.value = false
            // Restart the connection with the new settings, then make sure the service is up.
            repository.start(store.config.first() ?: config)
            PrinterMonitorService.start(app)
        }
    }

    fun setMonitoring(enabled: Boolean) {
        viewModelScope.launch {
            store.setMonitoringEnabled(enabled)
            if (enabled) PrinterMonitorService.start(app) else PrinterMonitorService.stop(app)
        }
    }

    /** Called on every resume: picks up file access granted in Settings and edits to printer.json. */
    fun onResume() {
        viewModelScope.launch { app.watchSync.refreshLinkStatus() }
        viewModelScope.launch {
            val before = store.config.first()
            store.restore()
            val after = store.config.first()
            if (after != null && after != before && after.isComplete) {
                repository.start(after)
                if (store.monitoringEnabled.first()) PrinterMonitorService.start(app)
            }
        }
    }

    fun edit() { _editing.value = true }
    fun cancelEdit() { _editing.value = false }

    fun reconnect() {
        val cfg = (config.value as? ConfigUiState.Loaded)?.config ?: return
        repository.start(cfg)
        if (monitoringEnabled.value) PrinterMonitorService.start(app)
    }
}
