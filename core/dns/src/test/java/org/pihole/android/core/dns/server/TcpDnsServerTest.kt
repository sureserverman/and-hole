package org.pihole.android.core.dns.server

import kotlinx.coroutines.CoroutineExceptionHandler
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
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CopyOnWriteArrayList

class TcpDnsServerTest {

    @Test
    fun clientDisconnectBeforeFullFrame_doesNotEscapeAsUnhandledException() = runBlocking {
        val failures = CopyOnWriteArrayList<Throwable>()
        val scope =
            CoroutineScope(
                SupervisorJob() +
                    Dispatchers.IO +
                    CoroutineExceptionHandler { _, throwable -> failures += throwable },
            )
        val port = ServerSocket(0).use { it.localPort }
        val server =
            TcpDnsServer(
                scope = scope,
                listenAddress = InetAddress.getByName("127.0.0.1"),
                port = port,
            ) { packet ->
                val (queryId, questions) = DnsCodec.parseQuestions(packet)
                val q = questions.single()
                val answer = DnsCodec.buildARecordAnswer(q.qname, 60, DnsCodec.ipv4Bytes(192, 0, 2, 1))
                DnsCodec.buildResponseQuery(queryId, q, answer)
            }
        server.start().await()

        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", port), 5_000)
            }
            Thread.sleep(200)

            val q = DnsQuestion("test.pi-hole.local.", DnsConstants.QTYPE_A, DnsConstants.QCLASS_IN)
            val response = tcpDnsExchange(buildQueryPacket(0x3456, listOf(q)), port)

            assertTrue("Expected a DNS response after a prior client disconnect", response.size > 12)
            assertEquals(0x3456, readUInt16(response, 0))
            assertTrue("Unexpected unhandled coroutine failures: $failures", failures.isEmpty())
        } finally {
            server.stop()
        }
    }

    private fun tcpDnsExchange(query: ByteArray, port: Int): ByteArray =
        Socket().use { socket ->
            socket.tcpNoDelay = true
            socket.soTimeout = 5_000
            socket.connect(InetSocketAddress("127.0.0.1", port), 5_000)

            val out = socket.getOutputStream()
            out.write(byteArrayOf((query.size ushr 8).toByte(), query.size.toByte()))
            out.write(query)
            out.flush()

            val input = socket.getInputStream()
            val lenBuf = ByteArray(2)
            readFully(input, lenBuf)
            val responseLength = readUInt16(lenBuf, 0)
            val response = ByteArray(responseLength)
            readFully(input, response)
            response
        }

    private fun readFully(input: java.io.InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val n = input.read(buffer, offset, buffer.size - offset)
            if (n < 0) error("unexpected EOF")
            offset += n
        }
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
