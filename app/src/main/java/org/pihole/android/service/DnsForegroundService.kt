package org.pihole.android.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.IBinder
import androidx.core.app.ServiceCompat
import java.net.InetAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import org.pihole.android.core.dns.server.DnsQueryLogEvent
import org.pihole.android.core.dns.server.DnsServerController
import org.pihole.android.core.dns.server.StaticDnsRr
import org.pihole.android.core.filter.normalize.DomainNormalizer
import org.pihole.android.core.tor.TorController
import org.pihole.android.core.tor.TorState
import org.pihole.android.core.tor.runtime.TorBootstrapState
import org.pihole.android.core.tor.runtime.TorRuntime
import org.pihole.android.core.tor.runtime.TorRuntimeFactory
import org.pihole.android.core.upstream.transport.DirectTcpStreamDialer
import kotlinx.coroutines.runBlocking
import org.pihole.android.data.db.DatabaseProvider
import org.pihole.android.data.db.entity.CompiledSnapshotEntity
import org.pihole.android.data.db.entity.CustomRuleEntity
import org.pihole.android.data.db.entity.LocalDnsRecordEntity
import org.pihole.android.data.db.entity.QueryLogEntity
import org.pihole.android.data.lists.AdlistSnapshotManifest
import org.pihole.android.data.lists.storage.CompiledSnapshotManifestStore
import org.pihole.android.data.lists.MatcherAssembler
import org.pihole.android.data.prefs.AppPreferences
import org.pihole.android.data.runtime.DebugRuntimeStatus
import org.pihole.android.data.runtime.DnsForegroundRuntimeState
import org.pihole.android.data.runtime.DnsUpstreamStatus
import org.pihole.android.data.runtime.UpstreamResolver
import org.pihole.android.data.runtime.UpstreamResolverDefaults
import org.pihole.android.resolver.TorDotDnsUpstream

class DnsForegroundService : Service() {

    sealed interface ServiceState {
        data object Idle : ServiceState
        data object Running : ServiceState
    }

    private val binder = LocalBinder()

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Default)

    private var dns: DnsServerController? = null
    private var upstreamWarmupLaunched = false

    private var torRuntime: TorRuntime? = null
    private var torRuntimeMode: String = "disabled"
    private var torController: TorController? = null
    private lateinit var dnsUpstream: TorDotDnsUpstream

    private val _state = MutableStateFlow<ServiceState>(ServiceState.Idle)
    val state: StateFlow<ServiceState> = _state.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): DnsForegroundService = this@DnsForegroundService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        // Call startForeground before any other work so the system never hits
        // ForegroundServiceDidNotStartInTimeException if Tor JNI, Room, or channels are slow.
        ServiceNotification.ensureChannel(this)
        val notification = ServiceNotification.buildForegroundNotification(this)
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        DebugRuntimeStatus.reset()
        val prefs = AppPreferences(this@DnsForegroundService)
        val useTor = runBlocking { prefs.upstreamUseTor.first() }
        val requestedRuntimeMode = runBlocking { prefs.torRuntimeMode.first() }
        val initialResolvers =
            runBlocking {
                val db = DatabaseProvider.get(applicationContext)
                UpstreamResolverDefaults.seedIfEmpty(db)
                db.upstreamResolverDao().getAllOrdered().map {
                    UpstreamResolver(
                        id = it.id,
                        label = it.label,
                        host = it.host,
                        port = it.port,
                        tlsServerName = it.tlsServerName,
                        enabled = it.enabled,
                        sortOrder = it.sortOrder,
                    )
                }
            }
        rebuildUpstreamPolicy(useTor = useTor, requestedRuntimeMode = requestedRuntimeMode, resolvers = initialResolvers)
        serviceScope.launch {
            state.collect {
                DebugRuntimeStatus.updateDnsForegroundServiceState(
                    when (it) {
                        ServiceState.Idle -> DnsForegroundRuntimeState.Idle
                        ServiceState.Running -> DnsForegroundRuntimeState.Running
                    },
                )
            }
        }
        serviceScope.launch(Dispatchers.IO) {
            runCatching { pruneQueryLogOnStart() }
        }
        startReactiveDnsLoop()
    }

    /**
     * Rebuilds the loopback DNS listener whenever snapshot, custom rules, local static records, or port change.
     * Rapid Room emissions (e.g. during Lists → Refresh) are [debounce]d so we do not stop/bind in a tight loop.
     */
    private fun startReactiveDnsLoop() {
        val db = DatabaseProvider.get(applicationContext)
        val prefs = AppPreferences(this)
        serviceScope.launch {
            combine(
                db.compiledSnapshotDao().observeLatest(),
                db.customRuleDao().observeAll(),
                db.localDnsRecordDao().observeAll(),
                prefs.dnsListenPort,
                prefs.dnsBindAllInterfaces,
                prefs.upstreamUseTor,
                prefs.torRuntimeMode,
                db.upstreamResolverDao().observeAllOrdered(),
            ) { values: Array<Any?> ->
                val latest = values[0] as CompiledSnapshotEntity?
                @Suppress("UNCHECKED_CAST")
                val rules = values[1] as List<CustomRuleEntity>
                @Suppress("UNCHECKED_CAST")
                val localRows = values[2] as List<LocalDnsRecordEntity>
                val port = values[3] as Int
                val bindAll = values[4] as Boolean
                val useTor = values[5] as Boolean
                val requestedRuntimeMode = values[6] as String
                @Suppress("UNCHECKED_CAST")
                val upstreamRows = values[7] as List<org.pihole.android.data.db.entity.UpstreamResolverEntity>
                DnsInputs(
                    snapshot = latest,
                    rules = rules,
                    localRecords = localRows,
                    port = port,
                    bindAllInterfaces = bindAll,
                    useTor = useTor,
                    requestedTorRuntimeMode = requestedRuntimeMode,
                    upstreamResolvers =
                        upstreamRows.map {
                            UpstreamResolver(
                                id = it.id,
                                label = it.label,
                                host = it.host,
                                port = it.port,
                                tlsServerName = it.tlsServerName,
                                enabled = it.enabled,
                                sortOrder = it.sortOrder,
                            )
                        },
                )
            }
                .map { inputs ->
                    inputs to
                        matcherReloadKey(
                            inputs.snapshot,
                            inputs.rules,
                            inputs.localRecords,
                            inputs.port,
                            inputs.bindAllInterfaces,
                            inputs.useTor,
                            inputs.requestedTorRuntimeMode,
                            inputs.upstreamResolvers,
                        )
                }
                .distinctUntilChanged { a, b -> a.second == b.second }
                .map { (inputs, _) -> inputs }
                .debounce(REACTIVE_DNS_DEBOUNCE_MS)
                .collect { inputs ->
                    rebuildUpstreamPolicy(
                        useTor = inputs.useTor,
                        requestedRuntimeMode = inputs.requestedTorRuntimeMode,
                        resolvers = inputs.upstreamResolvers,
                    )
                    val subscribed =
                        if (inputs.snapshot != null) {
                            CompiledSnapshotManifestStore.readTextOrNull(applicationContext)
                                ?.let { AdlistSnapshotManifest.parseSuffixDenyDomains(it) }
                                ?: emptySet()
                        } else {
                            emptySet()
                        }
                    val matcher = MatcherAssembler.assemble(subscribed, inputs.rules)
                    val staticLocal =
                        inputs.localRecords
                            .filter { it.enabled }
                            .map { rec ->
                                StaticDnsRr(
                                    ownerFqdn = DomainNormalizer.normalizeFqdn(rec.name),
                                    qtype = rec.type,
                                    rdataAscii = rec.value,
                                    ttl = rec.ttl,
                                )
                            }
                    dns?.stop()
                    val listen =
                        if (inputs.bindAllInterfaces) {
                            InetAddress.getByName("0.0.0.0")
                        } else {
                            InetAddress.getByName("127.0.0.1")
                        }
                    val controller =
                        DnsServerController(
                            serviceScope,
                            listenAddress = listen,
                            matcher = matcher,
                            upstream = dnsUpstream,
                            localRecords = staticLocal,
                            onQueryLog = { e -> persistQueryLog(db, e) },
                        )
                    dns = controller
                    try {
                        controller.start(inputs.port)
                        DebugRuntimeStatus.setDnsServiceDetail("")
                        DebugRuntimeStatus.recordDnsListenerRestart(inputs.port)
                        _state.value = ServiceState.Running
                    } catch (e: Exception) {
                        controller.stop()
                        dns = null
                        DebugRuntimeStatus.setDnsServiceDetail("DNS listener failed: ${e.message}")
                        _state.value = ServiceState.Idle
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Belt-and-suspenders: satisfy FGS contract even if the system counts from onStartCommand
        // or onCreate was delayed behind a prior service teardown on the main queue.
        ServiceNotification.ensureChannel(this)
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            ServiceNotification.buildForegroundNotification(this),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        return START_STICKY
    }

    override fun onDestroy() {
        dns?.stop()
        dns = null
        dnsUpstream.close()
        torController?.stopMonitoring()
        serviceJob.cancel()
        _state.value = ServiceState.Idle
        DebugRuntimeStatus.reset()
        super.onDestroy()
    }

    private fun maybePrewarmUpstream() {
        if (upstreamWarmupLaunched) return
        upstreamWarmupLaunched = true
        serviceScope.launch(Dispatchers.IO) {
            dnsUpstream.prewarm()
        }
    }

    private fun rebuildUpstreamPolicy(
        useTor: Boolean,
        requestedRuntimeMode: String,
        resolvers: List<UpstreamResolver>,
    ) {
        if (::dnsUpstream.isInitialized) {
            dnsUpstream.close()
        }
        torController?.stopMonitoring()
        torController = null
        torRuntime = null
        upstreamWarmupLaunched = false
        DnsUpstreamStatus.clear()
        if (!useTor) {
            torRuntimeMode = "disabled (direct DoT)"
            DebugRuntimeStatus.setTorRuntimeMode(torRuntimeMode)
            DebugRuntimeStatus.setTorLine("Disabled by policy")
            DebugRuntimeStatus.setTorBootstrapProgress(null)
            DebugRuntimeStatus.setTorBootstrapSummary("")
            DebugRuntimeStatus.setTorLastError("")
            DebugRuntimeStatus.setSocksPortLine("Direct TCP dialer (no Tor)")
            dnsUpstream =
                TorDotDnsUpstream(
                    useTor = false,
                    resolvers = resolvers,
                    directDialer = { DirectTcpStreamDialer() },
                )
            return
        }

        val runtimeCreation = TorRuntimeFactory.create(this, requestedRuntimeMode)
        val runtime = runtimeCreation.runtime
        torRuntime = runtime
        torRuntimeMode = runtimeCreation.runtimeModeLabel
        DebugRuntimeStatus.setTorRuntimeMode(torRuntimeMode)
        val controller = TorController(runtime)
        torController = controller
        dnsUpstream =
            TorDotDnsUpstream(
                useTor = true,
                resolvers = resolvers,
                torController = controller,
                torDialer = { runtime.dialer() },
                directDialer = { DirectTcpStreamDialer() },
            )
        controller.beginStartAndMonitor(serviceScope)
        serviceScope.launch {
            runtime.bootstrap.collect { bootstrap ->
                when (bootstrap) {
                    TorBootstrapState.Stopped -> {
                        DebugRuntimeStatus.setTorBootstrapProgress(null)
                        DebugRuntimeStatus.setTorBootstrapSummary("")
                        DebugRuntimeStatus.setTorLastError("")
                    }
                    is TorBootstrapState.Starting -> {
                        DebugRuntimeStatus.setTorBootstrapProgress(bootstrap.progress)
                        DebugRuntimeStatus.setTorBootstrapSummary(bootstrap.summary.orEmpty())
                        DebugRuntimeStatus.setTorLastError("")
                    }
                    TorBootstrapState.Ready -> {
                        DebugRuntimeStatus.setTorBootstrapProgress(100)
                        DebugRuntimeStatus.setTorBootstrapSummary("Bootstrap complete")
                        DebugRuntimeStatus.setTorLastError("")
                    }
                    is TorBootstrapState.Failed -> {
                        DebugRuntimeStatus.setTorBootstrapProgress(null)
                        DebugRuntimeStatus.setTorBootstrapSummary("")
                        DebugRuntimeStatus.setTorLastError(bootstrap.message)
                    }
                }
            }
        }
        serviceScope.launch {
            controller.state.collect { st ->
                DebugRuntimeStatus.setTorLine(formatTorStateForDiagnostics(st))
                when (st) {
                    is TorState.Ready -> {
                        DebugRuntimeStatus.setSocksPortLine(
                            when (torRuntimeMode) {
                                "Embedded Arti runtime" ->
                                    "Embedded Arti stream dialer active (no local SOCKS hop)"
                                else ->
                                    "Compatibility runtime active (TorService-backed stream dialer via local SOCKS endpoint)"
                            },
                        )
                        maybePrewarmUpstream()
                    }
                    is TorState.Failed ->
                        DebugRuntimeStatus.setSocksPortLine("— (runtime failed before transport became usable)")
                    TorState.Starting ->
                        DebugRuntimeStatus.setSocksPortLine(
                            when (torRuntimeMode) {
                                "Embedded Arti runtime" -> "Embedded bootstrap in progress"
                                else -> "Bootstrap in progress"
                            },
                        )
                    TorState.Stopped ->
                        DebugRuntimeStatus.setSocksPortLine("—")
                    else -> Unit
                }
            }
        }
    }

    companion object {
        const val NOTIFICATION_ID: Int = 1001

        /** Coalesce rapid DB-driven matcher changes before tearing down the UDP listener. */
        private const val REACTIVE_DNS_DEBOUNCE_MS: Long = 400L
    }
}

private suspend fun DnsForegroundService.pruneQueryLogOnStart() {
    val db = DatabaseProvider.get(applicationContext)
    val prefs = AppPreferences(applicationContext)
    val days = prefs.logRetentionDaysFlow.first()
    if (days > 0) {
        val cutoff = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
        db.queryLogDao().deleteOlderThan(cutoff)
    }
    val maxRows = prefs.logMaxRowsFlow.first()
    if (maxRows > 0) {
        db.queryLogDao().deleteAllExceptNewest(maxRows)
    }
}

private suspend fun persistQueryLog(db: org.pihole.android.data.db.AppDatabase, e: DnsQueryLogEvent) {
    db.queryLogDao().insert(
        QueryLogEntity(
            timestamp = e.timestampEpochMs,
            qname = e.qname,
            qtype = e.qtype,
            decision = e.decision,
            matchedRuleId = e.matchedRuleId,
            matchedSourceId = e.matchedSourceId,
            responseCode = e.responseCode,
            latencyMs = e.latencyMs,
            answeredFromCache = e.answeredFromCache,
        ),
    )
}

private data class DnsInputs(
    val snapshot: CompiledSnapshotEntity?,
    val rules: List<CustomRuleEntity>,
    val localRecords: List<LocalDnsRecordEntity>,
    val port: Int,
    val bindAllInterfaces: Boolean,
    val useTor: Boolean,
    val requestedTorRuntimeMode: String,
    val upstreamResolvers: List<UpstreamResolver>,
)

private data class MatcherReloadKey(
    val snapshotId: Long,
    val checksum: String,
    val rulesSig: String,
    val localSig: String,
    val port: Int,
    val bindAllInterfaces: Boolean,
    val useTor: Boolean,
    val requestedTorRuntimeMode: String,
    val upstreamSig: String,
)

private fun formatTorStateForDiagnostics(state: TorState): String =
    when (state) {
        TorState.Stopped -> "Stopped"
        TorState.Starting -> "Starting (bootstrap in progress)"
        TorState.Ready -> "Ready (runtime bootstrap complete)"
        is TorState.Failed -> "Failed: ${state.message}"
    }

private fun matcherReloadKey(
    latest: CompiledSnapshotEntity?,
    rules: List<CustomRuleEntity>,
    localRecords: List<LocalDnsRecordEntity>,
    port: Int,
    bindAllInterfaces: Boolean,
    useTor: Boolean,
    requestedTorRuntimeMode: String,
    upstreamResolvers: List<UpstreamResolver>,
): MatcherReloadKey =
    MatcherReloadKey(
        snapshotId = latest?.id ?: -1L,
        checksum = latest?.checksum.orEmpty(),
        rulesSig = rules.joinToString("\u0000") { "${it.id}|${it.enabled}|${it.kind}|${it.value}" },
        localSig =
            localRecords.joinToString("\u0000") {
                "${it.id}|${it.enabled}|${it.name}|${it.type}|${it.value}|${it.ttl}"
            },
        port = port,
        bindAllInterfaces = bindAllInterfaces,
        useTor = useTor,
        requestedTorRuntimeMode = requestedTorRuntimeMode,
        upstreamSig =
            upstreamResolvers.joinToString("\u0000") {
                "${it.id}|${it.enabled}|${it.sortOrder}|${it.host}|${it.port}|${it.tlsServerName.orEmpty()}"
            },
    )
