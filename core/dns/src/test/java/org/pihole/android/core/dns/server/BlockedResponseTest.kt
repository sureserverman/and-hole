package org.pihole.android.core.dns.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.pihole.android.core.dns.codec.DnsCodec
import org.pihole.android.core.dns.codec.DnsConstants
import org.pihole.android.core.dns.codec.DnsQuestion
import org.pihole.android.core.filter.rules.CompiledMatcher
import org.pihole.android.core.filter.trie.SuffixTrie
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BlockedResponseTest {

    @Test
    fun blockedA_returnsNullIpv4() = runBlocking {
        val trie = SuffixTrie()
        trie.insertReversedLabels(listOf("com", "blocked", "evil"))
        val matcher = CompiledMatcher(
            exactAllow = emptySet(),
            exactDeny = emptySet(),
            subscribedTrie = trie,
            regexAllow = emptyList(),
            regexDeny = emptyList(),
        )
        val port = ServerSocket(0).use { it.localPort }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val controller = DnsServerController(scope, matcher = matcher)
        controller.start(port)

        val q = DnsQuestion("evil.blocked.com.", DnsConstants.QTYPE_A, DnsConstants.QCLASS_IN)
        val query = buildQueryPacket(0x1111, listOf(q))
        val socket = DatagramSocket()
        socket.soTimeout = 5000
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
        val response = buf.copyOf(pkt.length)
        val bb = ByteBuffer.wrap(response).order(ByteOrder.BIG_ENDIAN)
        bb.position(response.size - 4)
        val last4 = ByteArray(4)
        bb.get(last4)
        assertEquals(0, last4[0].toInt() and 0xFF)
        assertEquals(0, last4[1].toInt() and 0xFF)
        assertEquals(0, last4[2].toInt() and 0xFF)
        assertEquals(0, last4[3].toInt() and 0xFF)
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
