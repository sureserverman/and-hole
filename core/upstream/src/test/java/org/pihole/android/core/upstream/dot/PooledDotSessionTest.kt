package org.pihole.android.core.upstream.dot

import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class PooledDotSessionTest {

    @Test
    fun exchange_reusesExistingConnection() {
        var openCount = 0
        var closeCount = 0
        val writes = mutableListOf<ByteArray>()
        val replies = ArrayDeque(listOf(byteArrayOf(0x11), byteArrayOf(0x22)))
        val connection =
            object : DotConnection {
                override fun writeLengthPrefixed(message: ByteArray) {
                    writes += message.copyOf()
                }

                override fun readLengthPrefixed(): ByteArray = replies.removeFirst()

                override fun close() {
                    closeCount += 1
                }
            }
        val session = PooledDotSession { openCount += 1; connection }

        val first = session.exchange(byteArrayOf(0x01))
        val second = session.exchange(byteArrayOf(0x02))

        assertArrayEquals(byteArrayOf(0x11), first)
        assertArrayEquals(byteArrayOf(0x22), second)
        assertEquals(1, openCount)
        assertEquals(0, closeCount)
        assertEquals(listOf(listOf(0x01.toByte()), listOf(0x02.toByte())), writes.map { it.toList() })
    }

    @Test
    fun exchange_staleConnectionRetriesOnceAndSucceeds() {
        var openCount = 0
        var closeCount = 0
        val session =
            PooledDotSession {
                openCount += 1
                when (openCount) {
                    1 ->
                        object : DotConnection {
                            override fun writeLengthPrefixed(message: ByteArray) = Unit

                            override fun readLengthPrefixed(): ByteArray {
                                throw IOException("boom")
                            }

                            override fun close() {
                                closeCount += 1
                            }
                        }

                    else ->
                        object : DotConnection {
                            override fun writeLengthPrefixed(message: ByteArray) = Unit

                            override fun readLengthPrefixed(): ByteArray = byteArrayOf(0x33)

                            override fun close() {
                                closeCount += 1
                            }
                        }
                }
            }

        session.ensureConnected()
        val response = session.exchange(byteArrayOf(0x01))

        assertArrayEquals(byteArrayOf(0x33), response)
        assertEquals(2, openCount)
        assertEquals(1, closeCount)
    }

    @Test
    fun exchange_newConnectionFailureStillThrows() {
        var openCount = 0
        var closeCount = 0
        val session =
            PooledDotSession {
                openCount += 1
                object : DotConnection {
                    override fun writeLengthPrefixed(message: ByteArray) = Unit

                    override fun readLengthPrefixed(): ByteArray {
                        throw IOException("boom")
                    }

                    override fun close() {
                        closeCount += 1
                    }
                }
            }

        try {
            session.exchange(byteArrayOf(0x01))
            fail("Expected IOException from the first pooled connection")
        } catch (expected: IOException) {
            assertEquals("boom", expected.message)
        }

        assertEquals(1, openCount)
        assertEquals(1, closeCount)
    }

    @Test
    fun ensureConnected_primesSessionWithoutReopening() {
        var openCount = 0
        var closeCount = 0
        val session =
            PooledDotSession {
                openCount += 1
                object : DotConnection {
                    override fun writeLengthPrefixed(message: ByteArray) = Unit

                    override fun readLengthPrefixed(): ByteArray = byteArrayOf(0x44)

                    override fun close() {
                        closeCount += 1
                    }
                }
            }

        session.ensureConnected()
        session.ensureConnected()
        val reply = session.exchange(byteArrayOf(0x03))

        assertArrayEquals(byteArrayOf(0x44), reply)
        assertEquals(1, openCount)
        assertEquals(0, closeCount)
    }
}
