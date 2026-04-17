package org.pihole.android.core.dns.server

/**
 * Minimal DNS query log event emitted by [DnsServerController].
 *
 * This is kept in core:dns so the DNS stack does not depend on Room or Android. The app layer can
 * persist it to `query_log` and/or expose it in the UI.
 */
data class DnsQueryLogEvent(
    val timestampEpochMs: Long,
    val qname: String,
    val qtype: Int,
    val decision: String,
    val responseCode: Int,
    val latencyMs: Long,
    val answeredFromCache: Boolean,
    val matchedRuleId: Long? = null,
    val matchedSourceId: Long? = null,
)

