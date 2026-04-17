package org.pihole.android.data.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide snapshot of DNS foreground service + Tor + listener restarts for Diagnostics and debugging.
 * Updated from [org.pihole.android.service.DnsForegroundService]; reset when that service is destroyed.
 */
enum class DnsForegroundRuntimeState {
    Idle,
    Running,
}

data class DebugRuntimeSnapshot(
    val dnsForegroundServiceState: DnsForegroundRuntimeState = DnsForegroundRuntimeState.Idle,
    val dnsServiceDetail: String = "",
    val dnsListenPort: Int? = null,
    val dnsListenerCycles: Long = 0L,
    val dnsLastListenerRestartEpochMs: Long? = null,
    val torRuntimeMode: String = "unknown",
    val torLine: String = "unknown",
    val torBootstrapProgress: Int? = null,
    val torBootstrapSummary: String = "",
    val torLastError: String = "",
    /** Best-effort detail about the currently active Tor transport/runtime layer. */
    val socksPortLine: String = "—",
)

object DebugRuntimeStatus {

    private val _snapshot = MutableStateFlow(DebugRuntimeSnapshot())
    val snapshot: StateFlow<DebugRuntimeSnapshot> = _snapshot.asStateFlow()

    fun reset() {
        _snapshot.value = DebugRuntimeSnapshot()
    }

    fun updateDnsForegroundServiceState(state: DnsForegroundRuntimeState) {
        _snapshot.value = _snapshot.value.copy(dnsForegroundServiceState = state)
    }

    fun setDnsServiceDetail(detail: String) {
        _snapshot.value = _snapshot.value.copy(dnsServiceDetail = detail)
    }

    fun recordDnsListenerRestart(port: Int) {
        val cur = _snapshot.value
        _snapshot.value = cur.copy(
            dnsListenPort = port,
            dnsListenerCycles = cur.dnsListenerCycles + 1,
            dnsLastListenerRestartEpochMs = System.currentTimeMillis(),
        )
    }

    fun setTorLine(line: String) {
        _snapshot.value = _snapshot.value.copy(torLine = line)
    }

    fun setTorRuntimeMode(mode: String) {
        _snapshot.value = _snapshot.value.copy(torRuntimeMode = mode)
    }

    fun setTorBootstrapProgress(progress: Int?) {
        _snapshot.value = _snapshot.value.copy(torBootstrapProgress = progress)
    }

    fun setTorBootstrapSummary(summary: String) {
        _snapshot.value = _snapshot.value.copy(torBootstrapSummary = summary)
    }

    fun setTorLastError(message: String) {
        _snapshot.value = _snapshot.value.copy(torLastError = message)
    }

    fun setSocksPortLine(line: String) {
        _snapshot.value = _snapshot.value.copy(socksPortLine = line)
    }
}
