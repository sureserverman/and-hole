package org.pihole.android.data.runtime

data class DnsControlSnapshot(
    val listenerState: DnsForegroundRuntimeState,
    val listenerPort: Int,
    val bindAllInterfaces: Boolean,
    val autoStart: Boolean,
    /** Label from [DebugRuntimeSnapshot.torRuntimeMode] (includes auto vs forced vs auto fallback). */
    val torRuntimeMode: String = "unknown",
    val torBootstrapProgress: Int? = null,
    val torBootstrapSummary: String = "",
    val torLastError: String = "",
    val torLine: String,
    val socksLine: String,
    val dnsServiceDetail: String,
    val adlistCount: Int,
    val customRuleCount: Int,
    val localDnsCount: Int,
    val recentBlockedDomains: List<String>,
)
