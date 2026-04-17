package org.pihole.android.core.dns.server

import org.pihole.android.core.dns.codec.DnsCodec
import org.pihole.android.core.dns.codec.DnsConstants
import org.pihole.android.core.filter.normalize.DomainNormalizer

/**
 * Small in-memory LRU of successful upstream replies (NOERROR only).
 */
class SimpleDnsCache(
    private val maxEntries: Int = 256,
    private val ttlMs: Long = 120_000L,
) {
    private data class Entry(val data: ByteArray, val expiresAtEpochMs: Long)

    private val map = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean = size > maxEntries
    }

    @Synchronized
    fun get(qname: String, qtype: Int, nowEpochMs: Long): ByteArray? {
        val key = cacheKey(qname, qtype)
        val e = map.remove(key) ?: return null
        if (e.expiresAtEpochMs < nowEpochMs) return null
        map[key] = e
        return e.data.copyOf()
    }

    @Synchronized
    fun putIfNoError(qname: String, qtype: Int, response: ByteArray, nowEpochMs: Long) {
        if (response.size < 4) return
        val flags = DnsCodec.readUInt16(response, 2)
        val rcode = flags and 0xF
        if (rcode != DnsConstants.RCODE_NOERROR) return
        val anCount = DnsCodec.readUInt16(response, 6)
        if (anCount < 1) return
        val key = cacheKey(qname, qtype)
        map[key] = Entry(response.copyOf(), nowEpochMs + ttlMs)
    }

    private fun cacheKey(qname: String, qtype: Int): String =
        "${DomainNormalizer.normalizeFqdn(qname)}|$qtype"
}
