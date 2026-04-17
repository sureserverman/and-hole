package org.pihole.android.core.dns.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pihole.android.core.dns.codec.DnsCodec
import org.pihole.android.core.dns.codec.DnsConstants
import org.pihole.android.core.dns.codec.DnsQuestion
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket

class LoopbackServerTest {

    @Test
    fun udpLoopback_respondsToSyntheticA() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val controller = DnsServerController(scope)
        controller.start(port)

        val q = DnsQuestion("test.pi-hole.local.", DnsConstants.QTYPE_A, DnsConstants.QCLASS_IN)
        val query = buildQueryPacket(0x7777, listOf(q))

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
        assertTrue(response.size > 12)
        val id = ByteBuffer.wrap(response).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
        assertTrue(id == 0x7777)

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
