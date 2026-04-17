package org.pihole.android.core.dns.server

/**
 * Static on-device record (from Room `local_dns_records`) applied before filter + upstream.
 */
data class StaticDnsRr(
    val ownerFqdn: String,
    val qtype: Int,
    val rdataAscii: String,
    val ttl: Int,
)
