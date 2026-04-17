package org.pihole.android.feature.logs

import org.junit.Assert.assertEquals
import org.junit.Test
import org.pihole.android.data.db.entity.QueryLogEntity

class LogsInsightsViewModelTest {
    @Test
    fun computeInsights_mapsDecisionAndDomainAggregates() {
        val rows =
            listOf(
                QueryLogEntity(timestamp = 1, qname = "a.test.", qtype = 1, decision = "blocked", matchedRuleId = null, matchedSourceId = -1, responseCode = 0, latencyMs = 1, answeredFromCache = false),
                QueryLogEntity(timestamp = 2, qname = "a.test.", qtype = 1, decision = "blocked", matchedRuleId = null, matchedSourceId = -1, responseCode = 0, latencyMs = 1, answeredFromCache = false),
                QueryLogEntity(timestamp = 3, qname = "b.test.", qtype = 1, decision = "allowed", matchedRuleId = null, matchedSourceId = null, responseCode = 0, latencyMs = 1, answeredFromCache = true),
                QueryLogEntity(timestamp = 4, qname = "c.test.", qtype = 1, decision = "pass", matchedRuleId = null, matchedSourceId = null, responseCode = 0, latencyMs = 1, answeredFromCache = false),
            )

        val insights = LogsViewModel.computeInsights(rows)

        assertEquals(2, insights.decisionCounts.first { it.decision == "blocked" }.hits)
        assertEquals(1, insights.decisionCounts.first { it.decision == "allowed" }.hits)
        assertEquals("a.test.", insights.topBlockedDomains.first().qname)
        assertEquals(2, insights.topBlockedDomains.first().hits)
        assertEquals("b.test.", insights.topAllowedDomains.first().qname)
        assertEquals(1, insights.cacheHits)
        assertEquals(1, insights.upstreamPasses)
    }
}
