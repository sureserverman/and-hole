package org.pihole.android.core.upstream.dot

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.pihole.android.core.upstream.tls.TlsChannel
import org.pihole.android.core.upstream.transport.BidirectionalStream
import org.pihole.android.core.upstream.transport.StreamDialer

class DotClientDialerTest {

    @Test
    fun exchange_usesDialerHostAndPort() {
        val dialRequests = mutableListOf<Pair<String, Int>>()
        val fakeTransport =
            object : BidirectionalStream {
                override fun writeFully(bytes: ByteArray) = Unit

                override fun readFully(length: Int): ByteArray = ByteArray(length)

                override fun close() = Unit
            }
        val fakeTls =
            RecordingTlsChannel(
                reads = ArrayDeque(listOf(byteArrayOf(0x00, 0x01), byteArrayOf(0x2A))),
            )
        val client =
            DotClient(
                upstreamHost = "resolver.example",
                upstreamPort = 853,
                dialerFactory = {
                    StreamDialer { host, port ->
                        dialRequests += host to port
                        fakeTransport
                    }
                },
                tlsFactory = { _, _, _ -> fakeTls },
            )

        val reply = client.exchangeOverSocks(9050, byteArrayOf(0x01))

        assertThat(dialRequests).containsExactly("resolver.example" to 853)
        assertThat(fakeTls.writes).hasSize(1)
        assertThat(fakeTls.writes.single().toList()).isEqualTo(listOf<Byte>(0x00, 0x01, 0x01))
        assertThat(reply.toList()).isEqualTo(listOf<Byte>(0x2A))
    }

    private class RecordingTlsChannel(
        val reads: ArrayDeque<ByteArray>,
    ) : TlsChannel {
        val writes = mutableListOf<ByteArray>()

        override fun writeFully(bytes: ByteArray) {
            writes += bytes.copyOf()
        }

        override fun readFully(length: Int): ByteArray {
            val next = reads.removeFirst()
            require(next.size == length)
            return next
        }

        override fun close() = Unit
    }
}
