package org.pihole.android.core.dns.codec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DnsCodecTest {

    @Test
    fun parseQuestions_singleAQuestion() {
        val q = DnsQuestion("test.pi-hole.local.", DnsConstants.QTYPE_A, DnsConstants.QCLASS_IN)
        val query = buildQueryPacket(0x1234, listOf(q))
        val (id, questions) = DnsCodec.parseQuestions(query)
        assertEquals(0x1234, id)
        assertEquals(1, questions.size)
        assertEquals("test.pi-hole.local.", questions.first().qname)
        assertEquals(DnsConstants.QTYPE_A, questions.first().qtype)
    }

    @Test
    fun buildServFailFromQuery_preservesIdAndQuestion_rcodeServFail() {
        val q = DnsQuestion("example.com.", DnsConstants.QTYPE_A, DnsConstants.QCLASS_IN)
        val query = buildQueryPacket(0xABE1, listOf(q))
        val fail = DnsCodec.buildServFailFromQuery(query)
        val (id, parsed) = DnsCodec.parseQuestions(query)
        assertEquals(0xABE1, id)
        assertEquals(1, parsed.size)
        val flags =
            ((fail[2].toInt() and 0xFF) shl 8) or (fail[3].toInt() and 0xFF)
        assertEquals(DnsConstants.RCODE_SERVFAIL, flags and 0xF)
        assertTrue((flags shr 15) and 1 == 1)
        assertEquals(0xABE1, ((fail[0].toInt() and 0xFF) shl 8) or (fail[1].toInt() and 0xFF))
        assertTrue(DnsCodec.isValidResponseForQuery(0xABE1, fail))
    }

    @Test
    fun isValidResponseForQuery_acceptsMatchingReply() {
        val q = DnsQuestion("x.test.", DnsConstants.QTYPE_A, DnsConstants.QCLASS_IN)
        val query = buildQueryPacket(0x5001, listOf(q))
        val ans = DnsCodec.buildARecordAnswer(q.qname, 10, DnsCodec.ipv4Bytes(8, 8, 8, 8))
        val reply = DnsCodec.buildResponseQuery(0x5001, q, ans)
        assertTrue(DnsCodec.isValidResponseForQuery(0x5001, reply))
    }

    private fun buildQueryPacket(id: Int, questions: List<DnsQuestion>): ByteArray {
        val bb = ByteBuffer.allocate(512).order(ByteOrder.BIG_ENDIAN)
        bb.putShort(id.toShort())
        bb.putShort(0)
        bb.putShort(questions.size.toShort())
        bb.putShort(0)
        bb.putShort(0)
        bb.putShort(0)
        for (q in questions) {
            bb.put(DnsCodec.encodeName(q.qname))
            bb.putShort(q.qtype.toShort())
            bb.putShort(q.qclass.toShort())
        }
        return bb.array().copyOf(bb.position())
    }
}
