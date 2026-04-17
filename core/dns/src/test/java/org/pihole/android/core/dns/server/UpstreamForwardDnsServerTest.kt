package org.pihole.android.core.dns.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pihole.android.core.dns.codec.DnsCodec
import org.pihole.android.core.dns.codec.DnsConstants
import org.pihole.android.core.dns.codec.DnsQuestion
import org.pihole.android.core.dns.upstream.DnsUpstream
import org.pihole.android.core.filter.rules.CompiledMatcher
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder

class UpstreamForwardDnsServerTest {

    @Test
    fun passQuery_upstreamReturnsValidResponse_forwardsToClient() = runBlocking {
        val q = DnsQuestion("example.com.", DnsConstants.QTYPE_A, DnsConstants.QCLASS_IN)
        val query = buildQueryPacket(0xCD01, listOf(q))
        val answer = DnsCodec.buildARecordAnswer(q.qname, 30, DnsCodec.ipv4Bytes(1, 2, 3, 4))
        val upstreamBytes = DnsCodec.buildResponseQuery(0xCD01, q, answer)
        val upstream =
            DnsUpstream { qry ->
                assertEquals(query.size, qry.size)
                upstreamBytes
            }

        val port = ServerSocket(0).use { it.localPort }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val controller =
            DnsServerController(
                scope,
                matcher = CompiledMatcher.empty(),
                upstream = upstream,
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
        val response = buf.copyOf(pkt.length)

        assertTrue(response.contentEquals(upstreamBytes))
        controller.stop()
    }

    @Test
    fun passQuery_upstreamNull_returnsServFail() = runBlocking {
        val q = DnsQuestion("example.com.", DnsConstants.QTYPE_A, DnsConstants.QCLASS_IN)
        val query = buildQueryPacket(0xCD02, listOf(q))
        val upstream = DnsUpstream { null }

        val port = ServerSocket(0).use { it.localPort }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val controller =
            DnsServerController(
                scope,
                matcher = CompiledMatcher.empty(),
                upstream = upstream,
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
        val response = buf.copyOf(pkt.length)

        val flags = readUInt16(response, 2)
        val rcode = flags and 0xF
        assertEquals(DnsConstants.RCODE_SERVFAIL, rcode)
        assertEquals(0xCD02, readUInt16(response, 0))
        controller.stop()
    }

    @Test
    fun passQuery_upstreamWrongId_returnsServFail() = runBlocking {
        val q = DnsQuestion("example.com.", DnsConstants.QTYPE_A, DnsConstants.QCLASS_IN)
        val query = buildQueryPacket(0xCD03, listOf(q))
        val answer = DnsCodec.buildARecordAnswer(q.qname, 30, DnsCodec.ipv4Bytes(1, 2, 3, 4))
        val bad = DnsCodec.buildResponseQuery(0x9999, q, answer)
        val upstream = DnsUpstream { bad }

        val port = ServerSocket(0).use { it.localPort }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val controller =
            DnsServerController(
                scope,
                matcher = CompiledMatcher.empty(),
                upstream = upstream,
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
        val response = buf.copyOf(pkt.length)

        val flags = readUInt16(response, 2)
        assertEquals(DnsConstants.RCODE_SERVFAIL, flags and 0xF)
        assertEquals(0xCD03, readUInt16(response, 0))
        controller.stop()
    }

    private fun readUInt16(packet: ByteArray, offset: Int): Int =
        ((packet[offset].toInt() and 0xFF) shl 8) or (packet[offset + 1].toInt() and 0xFF)

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
