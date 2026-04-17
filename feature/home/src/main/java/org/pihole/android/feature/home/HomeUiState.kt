package org.pihole.android.feature.home

import org.pihole.android.data.runtime.DnsForegroundRuntimeState

data class HomeUiState(
    val listenerState: DnsForegroundRuntimeState = DnsForegroundRuntimeState.Idle,
    val listenerPort: Int = 53_535,
    val bindAllInterfaces: Boolean = false,
    val autoStart: Boolean = false,
    val torRuntimeMode: String = "unknown",
    val torBootstrapProgress: Int? = null,
    val torBootstrapSummary: String = "",
    val torLastError: String = "",
    val torLine: String = "unknown",
    val socksLine: String = "—",
    val dnsServiceDetail: String = "",
    val adlistCount: Int = 0,
    val customRuleCount: Int = 0,
    val localDnsCount: Int = 0,
    val recentBlockedDomains: List<String> = emptyList(),
    val refreshing: Boolean = false,
)
