package org.pihole.android.core.dns.codec

data class DnsQuestion(
    val qname: String,
    val qtype: Int,
    val qclass: Int,
)
