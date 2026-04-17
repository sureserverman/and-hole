package org.pihole.android.data.runtime

/**
 * Short user-facing lines for Home / Diagnostics so Tor mode and bootstrap/error are visible without opening the full report.
 */
object TorRuntimeGlance {
    fun secondaryLine(
        torBootstrapProgress: Int?,
        torBootstrapSummary: String,
        torLastError: String,
    ): String? {
        if (torLastError.isNotBlank()) {
            return "Last error: $torLastError"
        }
        val p = torBootstrapProgress
        if (p != null && p < 100) {
            val s = torBootstrapSummary.ifBlank { "…" }
            return "Bootstrap: $p% — $s"
        }
        return null
    }
}
