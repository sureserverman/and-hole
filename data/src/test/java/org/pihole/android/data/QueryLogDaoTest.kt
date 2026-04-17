package org.pihole.android.data

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.pihole.android.data.db.AppDatabase
import org.pihole.android.data.db.entity.QueryLogEntity
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class QueryLogDaoTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: AppDatabase
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun deleteAllExceptNewest_keepsMostRecentByTimestamp() = runBlocking {
        val now = 1_000_000L
        for (i in 0 until 5) {
            db.queryLogDao().insert(
                QueryLogEntity(
                    timestamp = now + i,
                    qname = "q$i.example.",
                    qtype = 1,
                    decision = "pass",
                    matchedRuleId = null,
                    matchedSourceId = null,
                    responseCode = 0,
                    latencyMs = 1,
                    answeredFromCache = false,
                ),
            )
        }

        val deleted = db.queryLogDao().deleteAllExceptNewest(keep = 2)
        // Should delete 3 of 5
        assertEquals(3, deleted)
        val remaining = db.queryLogDao().listRecent(10)
        assertEquals(2, remaining.size)
        assertEquals("q4.example.", remaining[0].qname)
        assertEquals("q3.example.", remaining[1].qname)
    }

    @Test
    fun deleteAll_removesEverything() = runBlocking {
        db.queryLogDao().insert(
            QueryLogEntity(
                timestamp = 1,
                qname = "a.example.",
                qtype = 1,
                decision = "pass",
                matchedRuleId = null,
                matchedSourceId = null,
                responseCode = 0,
                latencyMs = 1,
                answeredFromCache = false,
            ),
        )
        assertEquals(1, db.queryLogDao().listRecent(10).size)
        val deleted = db.queryLogDao().deleteAll()
        assertEquals(1, deleted)
        assertEquals(0, db.queryLogDao().listRecent(10).size)
    }

    @Test
    fun aggregateQueries_returnExpectedTopDomainsAndDecisionCounts() = runBlocking {
        val base = 10_000L
        val rows =
            listOf(
                QueryLogEntity(timestamp = base + 1, qname = "ads.example.", qtype = 1, decision = "blocked", matchedRuleId = null, matchedSourceId = -1, responseCode = 0, latencyMs = 2, answeredFromCache = false),
                QueryLogEntity(timestamp = base + 2, qname = "ads.example.", qtype = 1, decision = "blocked", matchedRuleId = null, matchedSourceId = -1, responseCode = 0, latencyMs = 3, answeredFromCache = false),
                QueryLogEntity(timestamp = base + 3, qname = "ok.example.", qtype = 1, decision = "allowed", matchedRuleId = null, matchedSourceId = null, responseCode = 0, latencyMs = 4, answeredFromCache = false),
                QueryLogEntity(timestamp = base + 4, qname = "ok.example.", qtype = 1, decision = "allowed", matchedRuleId = null, matchedSourceId = null, responseCode = 0, latencyMs = 5, answeredFromCache = false),
                QueryLogEntity(timestamp = base + 5, qname = "upstream.example.", qtype = 1, decision = "pass", matchedRuleId = null, matchedSourceId = null, responseCode = 0, latencyMs = 6, answeredFromCache = true),
            )
        rows.forEach { db.queryLogDao().insert(it) }

        val topBlocked = db.queryLogDao().topBlockedDomains(base, 5)
        assertEquals(1, topBlocked.size)
        assertEquals("ads.example.", topBlocked.first().qname)
        assertEquals(2, topBlocked.first().hits)

        val topAllowed = db.queryLogDao().topAllowedDomains(base, 5)
        assertEquals(1, topAllowed.size)
        assertEquals("ok.example.", topAllowed.first().qname)
        assertEquals(2, topAllowed.first().hits)

        val counts = db.queryLogDao().decisionCounts(base).associate { it.decision to it.hits }
        assertEquals(2, counts["blocked"])
        assertEquals(2, counts["allowed"])
        assertEquals(1, counts["pass"])
    }

    @Test
    fun latestBlockingAttributionRows_onlyReturnsBlockedRowsWithAttribution() = runBlocking {
        val base = 20_000L
        db.queryLogDao().insert(
            QueryLogEntity(
                timestamp = base + 1,
                qname = "noattr.example.",
                qtype = 1,
                decision = "blocked",
                matchedRuleId = null,
                matchedSourceId = null,
                responseCode = 0,
                latencyMs = 1,
                answeredFromCache = false,
            ),
        )
        db.queryLogDao().insert(
            QueryLogEntity(
                timestamp = base + 2,
                qname = "attr.example.",
                qtype = 1,
                decision = "blocked",
                matchedRuleId = -2,
                matchedSourceId = null,
                responseCode = 0,
                latencyMs = 1,
                answeredFromCache = false,
            ),
        )
        db.queryLogDao().insert(
            QueryLogEntity(
                timestamp = base + 3,
                qname = "allowed.example.",
                qtype = 1,
                decision = "allowed",
                matchedRuleId = -2,
                matchedSourceId = null,
                responseCode = 0,
                latencyMs = 1,
                answeredFromCache = false,
            ),
        )

        val rows = db.queryLogDao().latestBlockingAttributionRows(10)
        assertEquals(1, rows.size)
        assertEquals("attr.example.", rows.first().qname)
        assertTrue(rows.first().decision == "blocked")
    }
}

