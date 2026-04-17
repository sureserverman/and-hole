package org.pihole.android.core.dns.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pihole.android.core.dns.codec.DnsCodec
import org.pihole.android.core.dns.codec.DnsConstants
import org.pihole.android.core.dns.codec.DnsQuestion
import org.pihole.android.core.dns.upstream.DnsUpstream
import org.pihole.android.core.filter.rules.CompiledMatcher
import org.pihole.android.core.filter.trie.SuffixTrie
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DnsQueryLoggingTest {

    @Test
    fun upstreamResponse_emitsQueryLogEvent_withRcodeAndDecision() = runBlocking {
        val q = DnsQuestion("example.com.", DnsConstants.QTYPE_A, DnsConstants.QCLASS_IN)
        val query = buildQueryPacket(0xAB01, listOf(q))
        val answer = DnsCodec.buildARecordAnswer(q.qname, 30, DnsCodec.ipv4Bytes(1, 2, 3, 4))
        val upstreamBytes = DnsCodec.buildResponseQuery(0xAB01, q, answer)
        val upstream = DnsUpstream { upstreamBytes }

        var logged: DnsQueryLogEvent? = null
        val port = ServerSocket(0).use { it.localPort }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val controller =
            DnsServerController(
                scope,
                matcher = CompiledMatcher.empty(),
                upstream = upstream,
                onQueryLog = { e -> logged = e },
            )
        controller.start(port)

        val socket = DatagramSocket()
        socket.soTimeout = 5_000
        socket.send(
            DatagramPacket(
                query,
                query.size,
                InetAddress.getByName("127.0.0.1"),
                port,
            ),
        )
        val buf = ByteArray(2048)
        val pkt = DatagramPacket(buf, buf.size)
        socket.receive(pkt)
        socket.close()

        val e = logged
        assertNotNull(e)
        assertEquals("example.com.", e!!.qname)
        assertEquals(DnsConstants.QTYPE_A, e.qtype)
        assertEquals("pass", e.decision)
        assertEquals(null, e.matchedRuleId)
        assertEquals(null, e.matchedSourceId)
        assertEquals(0, e.responseCode)
        assertTrue(e.latencyMs >= 0)
        controller.stop()
    }

    @Test
    fun subscribedBlock_emitsBlockedDecision_withSubscribedAttribution() = runBlocking {
        val q = DnsQuestion("ads.blocked.test.", DnsConstants.QTYPE_A, DnsConstants.QCLASS_IN)
        val query = buildQueryPacket(0xAB02, listOf(q))

        val trie = SuffixTrie().apply { insertReversedLabels(listOf("test", "blocked")) }
        val matcher = CompiledMatcher(emptySet(), emptySet(), trie, emptyList(), emptyList())

        var logged: DnsQueryLogEvent? = null
        val port = ServerSocket(0).use { it.localPort }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val controller =
            DnsServerController(
                scope,
                matcher = matcher,
                upstream = DnsUpstream { null },
                onQueryLog = { e -> logged = e },
            )
        controller.start(port)

        val socket = DatagramSocket()
        socket.soTimeout = 5_000
        socket.send(
            DatagramPacket(
                query,
                query.size,
                InetAddress.getByName("127.0.0.1"),
                port,
            ),
        )
        val buf = ByteArray(2048)
        val pkt = DatagramPacket(buf, buf.size)
        socket.receive(pkt)
        socket.close()

        val e = logged
        assertNotNull(e)
        assertEquals("ads.blocked.test.", e!!.qname)
        assertEquals("blocked", e.decision)
        assertEquals(DnsServerController.MATCHED_SUBSCRIBED_LIST_SENTINEL, e.matchedSourceId)
        assertEquals(null, e.matchedRuleId)
        assertEquals(0, e.responseCode)
        controller.stop()
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

