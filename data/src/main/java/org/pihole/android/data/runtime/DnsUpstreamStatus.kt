package org.pihole.android.data.runtime

data class DnsUpstreamDebugStatus(
    val activeResolver: String? = null,
    val lastFailedResolver: String? = null,
    val lastFailureMessage: String? = null,
    val failoverCount: Long = 0L,
    val lastFailoverEvent: String? = null,
)

/**
 * Last upstream routing status for diagnostics (best-effort; in-memory only).
 */
object DnsUpstreamStatus {
    @Volatile
    var status: DnsUpstreamDebugStatus = DnsUpstreamDebugStatus()
        @Synchronized get
        @Synchronized set

    @Synchronized
    fun clear() {
        status = DnsUpstreamDebugStatus()
    }
}
