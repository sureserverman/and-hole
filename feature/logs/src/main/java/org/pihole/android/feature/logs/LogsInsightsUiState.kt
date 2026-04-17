package org.pihole.android.feature.logs

data class LogsInsightsUiState(
    val decisionCounts: List<InsightDecisionStat> = emptyList(),
    val topBlockedDomains: List<InsightDomainStat> = emptyList(),
    val topAllowedDomains: List<InsightDomainStat> = emptyList(),
    val cacheHits: Int = 0,
    val upstreamPasses: Int = 0,
)
