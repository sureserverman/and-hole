package org.pihole.android.core.dns.server

import org.pihole.android.core.dns.codec.DnsCodec
import org.pihole.android.core.dns.codec.DnsConstants
import org.pihole.android.core.filter.normalize.DomainNormalizer
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

object StaticDnsResponder {

    fun buildAnswerPayloadIfHit(qname: String, qtype: Int, records: List<StaticDnsRr>): ByteArray? {
        val fqdn = DomainNormalizer.normalizeFqdn(qname)
        val hit = records.firstOrNull { DomainNormalizer.normalizeFqdn(it.ownerFqdn) == fqdn && it.qtype == qtype }
            ?: return null
        val ttl = hit.ttl.coerceIn(30, 86_400)
        return try {
            when (qtype) {
                DnsConstants.QTYPE_A -> {
                    val addr = InetAddress.getByName(hit.rdataAscii.trim())
                    if (addr !is Inet4Address) return null
                    DnsCodec.buildARecordAnswer(qname, ttl, addr.address)
                }
                DnsConstants.QTYPE_AAAA -> {
                    val addr = InetAddress.getByName(hit.rdataAscii.trim())
                    if (addr !is Inet6Address) return null
                    DnsCodec.buildAaaaRecordAnswer(qname, ttl, addr.address)
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }
}
