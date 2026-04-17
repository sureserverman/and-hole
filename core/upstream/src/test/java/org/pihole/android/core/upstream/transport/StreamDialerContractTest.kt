package org.pihole.android.core.upstream.transport

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StreamDialerContractTest {

    @Test
    fun bidirectionalStream_roundTripsBytes() {
        val stream =
            FakeBidirectionalStream(
                reads = ArrayDeque(listOf(byteArrayOf(0x12, 0x34))),
            )

        stream.writeFully(byteArrayOf(0x01, 0x02))

        assertThat(stream.writes).hasSize(1)
        assertThat(stream.writes.single().toList()).isEqualTo(listOf<Byte>(0x01, 0x02))
        assertThat(stream.readFully(2).toList()).isEqualTo(listOf<Byte>(0x12, 0x34))
    }

    private class FakeBidirectionalStream(
        val reads: ArrayDeque<ByteArray>,
    ) : BidirectionalStream {
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
