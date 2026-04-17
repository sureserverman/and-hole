package org.pihole.android.core.dns.upstream

/**
 * Forwards a raw DNS query message (UDP-style wire format, no TCP length prefix) to an upstream
 * resolver and returns the raw response message, or null on failure.
 */
fun interface DnsUpstream {
    suspend fun forward(query: ByteArray): ByteArray?
}
