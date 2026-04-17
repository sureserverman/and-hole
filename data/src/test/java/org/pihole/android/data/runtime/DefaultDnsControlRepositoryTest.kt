package org.pihole.android.data.runtime

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.pihole.android.data.db.AppDatabase
import org.pihole.android.data.db.entity.AdlistSourceEntity
import org.pihole.android.data.db.entity.CustomRuleEntity
import org.pihole.android.data.db.entity.LocalDnsRecordEntity
import org.pihole.android.data.db.entity.QueryLogEntity
import org.pihole.android.data.prefs.AppPreferences
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DefaultDnsControlRepositoryTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var prefs: AppPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefs = AppPreferences(context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun snapshot_combinesRuntimePrefsCountsAndRecentBlocked() = runBlocking {
        db.adlistSourceDao().insert(
            AdlistSourceEntity(
                url = "https://example.com/ads.txt",
                enabled = true,
                etag = null,
                lastModified = null,
                lastRefreshStartedAt = null,
                lastSuccessAt = null,
                lastResult = null,
                lastError = null,
            ),
        )
        val now = System.currentTimeMillis()
        db.customRuleDao().insert(
            CustomRuleEntity(
                kind = "exact_deny",
                value = "ads.example",
                enabled = true,
                comment = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
        db.localDnsRecordDao().insert(
            LocalDnsRecordEntity(
                name = "lan.test",
                type = 1,
                value = "192.168.1.10",
                ttl = 60,
                enabled = true,
            ),
        )
        db.queryLogDao().insert(
            QueryLogEntity(
                timestamp = now,
                qname = "blocked.test.",
                qtype = 1,
                decision = "blocked",
                matchedRuleId = null,
                matchedSourceId = -1,
                responseCode = 0,
                latencyMs = 12,
                answeredFromCache = false,
            ),
        )

        prefs.setDnsListenPort(5454)
        prefs.setDnsBindAllInterfaces(true)
        prefs.setAutoStartEnabled(true)

        val runtime =
            MutableStateFlow(
                DebugRuntimeSnapshot(
                    dnsForegroundServiceState = DnsForegroundRuntimeState.Running,
                    dnsServiceDetail = "healthy",
                    torLine = "ready",
                    socksPortLine = "127.0.0.1:9050 accepts TCP",
                ),
            )

        var startCalled = false
        var stopCalled = false
        var refreshCalled = false
        val repository =
            DefaultDnsControlRepository(
                context = context,
                db = db,
                prefs = prefs,
                runtimeSnapshotFlow = runtime,
                startCommand = { startCalled = true },
                stopCommand = { stopCalled = true },
                refreshAction = { _, _ -> refreshCalled = true },
            )

        val snapshot = repository.snapshot.first()
        assertEquals(DnsForegroundRuntimeState.Running, snapshot.listenerState)
        assertEquals(5454, snapshot.listenerPort)
        assertTrue(snapshot.bindAllInterfaces)
        assertTrue(snapshot.autoStart)
        assertEquals(1, snapshot.adlistCount)
        assertEquals(1, snapshot.customRuleCount)
        assertEquals(1, snapshot.localDnsCount)
        assertEquals(listOf("blocked.test."), snapshot.recentBlockedDomains)

        repository.startListener()
        repository.stopListener()
        repository.refreshAdlists()
        assertTrue(startCalled)
        assertTrue(stopCalled)
        assertTrue(refreshCalled)
        assertFalse(snapshot.recentBlockedDomains.isEmpty().not() && snapshot.recentBlockedDomains[0].isBlank())
    }
}
