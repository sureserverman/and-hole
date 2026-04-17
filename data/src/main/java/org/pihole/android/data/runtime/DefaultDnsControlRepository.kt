package org.pihole.android.data.runtime

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import org.pihole.android.data.db.AppDatabase
import org.pihole.android.data.db.DatabaseProvider
import org.pihole.android.data.lists.AdlistRefreshEngine
import org.pihole.android.data.prefs.AppPreferences

class DefaultDnsControlRepository(
    private val context: Context,
    private val db: AppDatabase = DatabaseProvider.get(context.applicationContext),
    private val prefs: AppPreferences = AppPreferences(context.applicationContext),
    private val runtimeSnapshotFlow: Flow<DebugRuntimeSnapshot> = DebugRuntimeStatus.snapshot,
    private val startCommand: (Context) -> Unit,
    private val stopCommand: (Context) -> Unit,
    private val refreshAction: suspend (Context, AppDatabase) -> Unit = { appContext, appDb ->
        AdlistRefreshEngine.refreshAll(appContext, appDb)
    },
) : DnsControlRepository {

    override val snapshot: Flow<DnsControlSnapshot> =
        combine(
            runtimeSnapshotFlow,
            prefs.dnsListenPort,
            prefs.dnsBindAllInterfaces,
            prefs.autoStartEnabled,
            db.adlistSourceDao().observeCount(),
            db.customRuleDao().observeCount(),
            db.localDnsRecordDao().observeCount(),
            db.queryLogDao().observeRecentBlockedDomains(5),
        ) { values: Array<Any?> ->
            val runtime = values[0] as DebugRuntimeSnapshot
            val listenerPort = values[1] as Int
            val bindAllInterfaces = values[2] as Boolean
            val autoStart = values[3] as Boolean
            val adlistCount = values[4] as Int
            val customRuleCount = values[5] as Int
            val localDnsCount = values[6] as Int
            @Suppress("UNCHECKED_CAST")
            val recentBlockedDomains = values[7] as List<String>
            DnsControlSnapshot(
                listenerState = runtime.dnsForegroundServiceState,
                listenerPort = listenerPort,
                bindAllInterfaces = bindAllInterfaces,
                autoStart = autoStart,
                torRuntimeMode = runtime.torRuntimeMode,
                torBootstrapProgress = runtime.torBootstrapProgress,
                torBootstrapSummary = runtime.torBootstrapSummary,
                torLastError = runtime.torLastError,
                torLine = runtime.torLine,
                socksLine = runtime.socksPortLine,
                dnsServiceDetail = runtime.dnsServiceDetail,
                adlistCount = adlistCount,
                customRuleCount = customRuleCount,
                localDnsCount = localDnsCount,
                recentBlockedDomains = recentBlockedDomains,
            )
        }

    override suspend fun startListener() {
        withContext(Dispatchers.Main.immediate) {
            startCommand(context.applicationContext)
        }
    }

    override suspend fun stopListener() {
        withContext(Dispatchers.Main.immediate) {
            stopCommand(context.applicationContext)
        }
    }

    override suspend fun refreshAdlists() {
        withContext(Dispatchers.IO) {
            refreshAction(context.applicationContext, db)
        }
    }
}
