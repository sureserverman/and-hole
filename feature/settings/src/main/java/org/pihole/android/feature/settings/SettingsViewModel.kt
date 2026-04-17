package org.pihole.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.pihole.android.data.prefs.AppPreferences

class SettingsViewModel(
    private val prefs: AppPreferences,
) : ViewModel() {
    val torRuntimeMode: StateFlow<String> =
        prefs.torRuntimeMode.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            AppPreferences.TOR_RUNTIME_MODE_AUTO,
        )
    val bindAllIfaces: StateFlow<Boolean> =
        prefs.dnsBindAllInterfaces.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val autoStart: StateFlow<Boolean> =
        prefs.autoStartEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val retentionDays: StateFlow<Int> =
        prefs.logRetentionDaysFlow.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            AppPreferences.DEFAULT_LOG_RETENTION_DAYS,
        )
    val maxRows: StateFlow<Int> =
        prefs.logMaxRowsFlow.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            AppPreferences.DEFAULT_LOG_MAX_ROWS,
        )

    fun setBindAllInterfaces(enabled: Boolean) {
        viewModelScope.launch { prefs.setDnsBindAllInterfaces(enabled) }
    }

    fun setAutoStart(enabled: Boolean) {
        viewModelScope.launch { prefs.setAutoStartEnabled(enabled) }
    }

    fun setTorRuntimeMode(mode: String) {
        viewModelScope.launch { prefs.setTorRuntimeMode(mode) }
    }

    fun setRetentionDays(days: Int) {
        viewModelScope.launch { prefs.setLogRetentionDays(days.coerceIn(0, 365)) }
    }

    fun setMaxRows(rows: Int) {
        viewModelScope.launch { prefs.setLogMaxRows(rows.coerceIn(0, 200_000)) }
    }
}

class SettingsViewModelFactory(
    private val prefs: AppPreferences,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(prefs) as T
        }
        error("Unknown ViewModel class")
    }
}
