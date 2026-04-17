package org.pihole.android.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.pihole.android.data.runtime.DnsControlRepository

class HomeViewModel(
    private val repository: DnsControlRepository,
) : ViewModel() {

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    val uiState: StateFlow<HomeUiState> =
        combine(repository.snapshot, _refreshing) { snapshot, refreshing ->
            HomeUiState(
                listenerState = snapshot.listenerState,
                listenerPort = snapshot.listenerPort,
                bindAllInterfaces = snapshot.bindAllInterfaces,
                autoStart = snapshot.autoStart,
                torRuntimeMode = snapshot.torRuntimeMode,
                torBootstrapProgress = snapshot.torBootstrapProgress,
                torBootstrapSummary = snapshot.torBootstrapSummary,
                torLastError = snapshot.torLastError,
                torLine = snapshot.torLine,
                socksLine = snapshot.socksLine,
                dnsServiceDetail = snapshot.dnsServiceDetail,
                adlistCount = snapshot.adlistCount,
                customRuleCount = snapshot.customRuleCount,
                localDnsCount = snapshot.localDnsCount,
                recentBlockedDomains = snapshot.recentBlockedDomains,
                refreshing = refreshing,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            HomeUiState(),
        )

    fun startListener() {
        viewModelScope.launch { repository.startListener() }
    }

    fun stopListener() {
        viewModelScope.launch { repository.stopListener() }
    }

    fun refreshLists() {
        viewModelScope.launch {
            _refreshing.value = true
            try {
                repository.refreshAdlists()
            } finally {
                _refreshing.value = false
            }
        }
    }
}
