package org.pihole.android.core.dns.codec

object DnsConstants {
    const val QTYPE_A: Int = 1
    const val QTYPE_CNAME: Int = 5
    const val QTYPE_AAAA: Int = 28
    const val QCLASS_IN: Int = 1

    const val RCODE_NOERROR: Int = 0
    const val RCODE_SERVFAIL: Int = 2
    const val RCODE_NXDOMAIN: Int = 3

    const val OPCODE_QUERY: Int = 0
}
