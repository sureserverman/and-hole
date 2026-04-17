package org.pihole.android.feature.logs

data class InsightDomainStat(
    val qname: String,
    val hits: Int,
)

data class InsightDecisionStat(
    val decision: String,
    val hits: Int,
)
