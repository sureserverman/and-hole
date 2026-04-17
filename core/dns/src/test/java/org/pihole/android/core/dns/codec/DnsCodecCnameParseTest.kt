package org.pihole.android.core.dns.codec

import org.junit.Assert.assertEquals
import org.junit.Test

class DnsCodecCnameParseTest {

    @Test
    fun forEachCnameTargetInAnswers_findsCnameTarget() {
        val q = DnsQuestion("alias.example.com.", DnsConstants.QTYPE_A, DnsConstants.QCLASS_IN)
        val cname = DnsCodec.buildCnameRecordAnswer("alias.example.com.", 60, "target.example.com.")
        val resp = DnsCodec.buildResponseQuery(0x77AA, q, cname)
        val targets = mutableListOf<String>()
        DnsCodec.forEachCnameTargetInAnswers(resp) { targets.add(it) }
        assertEquals(1, targets.size)
        assertEquals("target.example.com.", targets[0])
    }
}
