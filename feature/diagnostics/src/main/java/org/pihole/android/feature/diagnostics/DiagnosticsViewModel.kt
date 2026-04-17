package org.pihole.android.feature.diagnostics

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.pihole.android.data.db.AppDatabase
import org.pihole.android.data.db.entity.CompiledSnapshotEntity
import org.pihole.android.data.lists.AdlistSnapshotManifest
import org.pihole.android.data.lists.storage.CompiledSnapshotManifestStore
import org.pihole.android.data.prefs.AppPreferences
import org.pihole.android.data.runtime.DebugRuntimeStatus
import org.pihole.android.data.runtime.DnsUpstreamStatus
import org.pihole.android.data.runtime.TorRuntimeGlance

class DiagnosticsViewModel(
    app: Application,
    private val db: AppDatabase,
    private val prefs: AppPreferences,
) : AndroidViewModel(app) {

    private data class DataPart(
        val snapshotLine: String,
        val adlistCount: Int,
        val customRuleCount: Int,
        val localDnsCount: Int,
        val queryLogCount: Int,
        val dnsPort: Int,
    )

    private data class RoomCounts(
        val snapshot: CompiledSnapshotEntity?,
        val adlistCount: Int,
        val customRuleCount: Int,
        val localDnsCount: Int,
        val queryLogCount: Int,
    )

    val uiState =
        combine(
            combine(
                db.compiledSnapshotDao().observeLatest(),
                db.adlistSourceDao().observeCount(),
                db.customRuleDao().observeCount(),
                db.localDnsRecordDao().observeCount(),
                db.queryLogDao().observeCount(),
            ) { snap, adlists, rules, localDns, qlog ->
                RoomCounts(snap, adlists, rules, localDns, qlog)
            },
            prefs.dnsListenPort,
            prefs.dnsBindAllInterfaces,
            prefs.autoStartEnabled,
            DebugRuntimeStatus.snapshot,
        ) { counts, port, bindAllIfaces, autoStart, runtime ->
            val data =
                DataPart(
                    snapshotLine =
                        counts.snapshot?.let { "id=${it.id} checksum=${it.checksum}" }
                            ?: "no compiled snapshot yet",
                    adlistCount = counts.adlistCount,
                    customRuleCount = counts.customRuleCount,
                    localDnsCount = counts.localDnsCount,
                    queryLogCount = counts.queryLogCount,
                    dnsPort = port,
                )
            val ctx = getApplication<Application>()
            val debuggable = (ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            val manifestText = CompiledSnapshotManifestStore.readTextOrNull(ctx)
            val suffixCount =
                manifestText?.let { AdlistSnapshotManifest.parseSuffixDenyDomains(it).size } ?: 0
            val manifestSection =
                buildString {
                    append("File present: ${manifestText != null}\n")
                    if (manifestText != null) {
                        append("Size (bytes): ${manifestText.length}\n")
                        append("suffixDeny count: $suffixCount")
                    } else {
                        append("(Run Lists → Refresh after adding sources.)")
                    }
                }
            val dataSection =
                """
                Compiled snapshot: ${data.snapshotLine}
                Adlist sources (rows): ${data.adlistCount}
                Custom rules (rows): ${data.customRuleCount}
                Local DNS records (rows): ${data.localDnsCount}
                Query log (rows): ${data.queryLogCount}
                """.trimIndent()
            val prefsSection =
                """
                DNS listen port: ${data.dnsPort}
                DNS bind all interfaces (0.0.0.0): $bindAllIfaces
                Auto-start DNS after boot: $autoStart
                """.trimIndent()
            val lastRestart =
                runtime.dnsLastListenerRestartEpochMs?.let { java.time.Instant.ofEpochMilli(it).toString() }
                    ?: "—"
            val upstreamStatus = DnsUpstreamStatus.status
            val upstreamFail = upstreamStatus.lastFailureMessage ?: "—"
            val transportLine = runtime.socksPortLine
            val bootstrapProgress = runtime.torBootstrapProgress?.let { "$it%" } ?: "—"
            val bootstrapSummary = runtime.torBootstrapSummary.ifEmpty { "—" }
            val torLastError = runtime.torLastError.ifEmpty { "—" }
            val runtimeSection =
                """
                DNS foreground service (in-process): ${runtime.dnsForegroundServiceState}
                DNS service detail: ${runtime.dnsServiceDetail.ifEmpty { "—" }}
                Bridge listener port: ${runtime.dnsListenPort ?: "—"}
                Listener (re)start cycles: ${runtime.dnsListenerCycles}
                Last listener (re)start: $lastRestart
                Listener protocols: UDP on 127.0.0.1
                Tor runtime mode: ${runtime.torRuntimeMode}
                Tor runtime: ${runtime.torLine}
                Tor bootstrap progress: $bootstrapProgress
                Tor bootstrap summary: $bootstrapSummary
                Tor last error: $torLastError
                Tor transport detail: $transportLine
                DoT upstream: Ordered resolver chain from Settings (with failover)
                Last upstream forward failure: $upstreamFail
                Active resolver: ${upstreamStatus.activeResolver ?: "—"}
                Last failed resolver: ${upstreamStatus.lastFailedResolver ?: "—"}
                Upstream failover count: ${upstreamStatus.failoverCount}
                Last failover event: ${upstreamStatus.lastFailoverEvent ?: "—"}
                Logcat (upstream path): adb logcat -s PiholeDns:D
                ${failureTriageChecklist()}
                """.trimIndent()
            val cheatSheet =
                if (debuggable) {
                    """
                    Drawer label: And-hole DNS (debug) — search that if the icon is easy to miss.
                    ./scripts/verify-harness.sh [--dns-probe | --dns-probe-suite]
                    adb shell am start -n org.pihole.android/.MainActivity
                    Prefer: adb shell am start -n org.pihole.android/.debug.DebugStartDnsActivity
                    (avoid: adb shell am start-foreground-service … from shell — FGS often DENIED on API 35)
                    adb shell am broadcast -a org.pihole.android.debug.START_DNS -n org.pihole.android/.debug.DebugDnsReceiver
                    adb shell am broadcast -a org.pihole.android.debug.OPEN_MAIN -n org.pihole.android/.debug.DebugDnsReceiver
                    adb shell am broadcast -a org.pihole.android.debug.STOP_DNS -n org.pihole.android/.debug.DebugDnsReceiver
                    adb shell dumpsys activity services org.pihole.android
                    ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.pihole.android.service.DnsUdpLoopbackInstrumentedTest
                    """.trimIndent()
                } else {
                    null
                }
            val fullReport =
                listOfNotNull(
                    "=== Build ===",
                    buildSection(ctx),
                    "",
                    "=== Preferences ===",
                    prefsSection,
                    "",
                    "=== Data (Room) ===",
                    dataSection,
                    "",
                    "=== On-disk manifest ===",
                    manifestSection,
                    "",
                    "=== Runtime (foreground service bridge) ===",
                    runtimeSection,
                    cheatSheet?.let { section ->
                        listOf("", "=== Debug cheat sheet (host / adb) ===", section).joinToString("\n")
                    },
                ).joinToString("\n")
            val glanceSecondary =
                TorRuntimeGlance.secondaryLine(
                    runtime.torBootstrapProgress,
                    runtime.torBootstrapSummary,
                    runtime.torLastError,
                )
            DiagnosticsUiState(
                buildSection = buildSection(ctx),
                prefsSection = prefsSection,
                dataSection = dataSection,
                manifestSection = manifestSection,
                runtimeGlancePrimary = runtime.torRuntimeMode,
                runtimeGlanceSecondary = glanceSecondary,
                runtimeSection = runtimeSection,
                cheatSheetSection = cheatSheet,
                fullReportText = fullReport,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            placeholderUiState(getApplication()),
        )

    fun copyReportToClipboard() {
        val cm =
            ContextCompat.getSystemService(getApplication(), ClipboardManager::class.java)
                ?: return
        cm.setPrimaryClip(ClipData.newPlainText("And-hole diagnostics", uiState.value.fullReportText))
    }

    private fun buildSection(app: Application): String {
        val pkg = app.packageName
        val pi =
            try {
                app.packageManager.getPackageInfo(pkg, PackageManager.GET_META_DATA)
            } catch (_: PackageManager.NameNotFoundException) {
                return "Package: $pkg (package info unavailable)"
            }
        val debuggable = (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val vc =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pi.longVersionCode.toString()
            } else {
                @Suppress("DEPRECATION")
                pi.versionCode.toString()
            }
        return """
            Package: $pkg
            Version: ${pi.versionName} ($vc)
            Debuggable: $debuggable
        """.trimIndent()
    }

    private companion object {
        /**
         * Mirrors [docs/DNS-TRIAGE.md](docs/DNS-TRIAGE.md) — keep in sync when editing the triage doc.
         */
        fun failureTriageChecklist(): String =
            """
            Failure triage (symptom → check):
            (1) Port refused / nothing listening → FGS started? Port matches client? OEM battery? VPN+127.0.0.1?
            (2) Port OK, bad answers / slow → Tor runtime Ready? Bootstrap summary? Transport detail? Last upstream forward failure?
            (3) Resolve OK, no blocks → Lists→Refresh? manifest suffixDeny count & snapshot in Diagnostics?
            (4) Only with sing-box/VPN → Private DNS Off; use the UDP profile; if 127.0.0.1 still refuses, try bind-all-interfaces.
            Full checklist: docs/DNS-TRIAGE.md (repo).
            """.trimIndent()
    }

    private fun placeholderUiState(app: Application): DiagnosticsUiState {
        val build = buildSection(app)
        return DiagnosticsUiState(
            buildSection = build,
            prefsSection = "…",
            dataSection = "…",
            manifestSection = "…",
            runtimeGlancePrimary = "…",
            runtimeGlanceSecondary = null,
            runtimeSection = "…",
            cheatSheetSection = null,
            fullReportText = build,
        )
    }
}
