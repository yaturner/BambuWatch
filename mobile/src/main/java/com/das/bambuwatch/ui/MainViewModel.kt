package com.das.bambuwatch.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.das.bambuwatch.BambuWatchApp
import com.das.bambuwatch.data.PrinterConfig
import com.das.bambuwatch.shared.PrinterState
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

    val config: StateFlow<ConfigUiState> = app.configStore.config
        .map<PrinterConfig?, ConfigUiState> { ConfigUiState.Loaded(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ConfigUiState.Loading)

    val printerState: StateFlow<PrinterState> = repository.state

    private val _editing = MutableStateFlow(false)
    val editing: StateFlow<Boolean> = _editing.asStateFlow()

    init {
        // Auto-connect with the saved config on launch, unless already connected (e.g. after rotation).
        viewModelScope.launch {
            val saved = app.configStore.config.first()
            if (saved != null && saved.isComplete && repository.state.value.updatedAt == 0L) {
                repository.start(saved)
            }
        }
    }

    fun save(config: PrinterConfig) {
        viewModelScope.launch {
            app.configStore.save(config)
            _editing.value = false
            repository.start(app.configStore.config.first() ?: config)
        }
    }

    fun edit() { _editing.value = true }
    fun cancelEdit() { _editing.value = false }
    fun reconnect() {
        (config.value as? ConfigUiState.Loaded)?.config?.let(repository::start)
    }
}
