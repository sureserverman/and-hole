package org.pihole.android.core.upstream.dot

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream

class DotRequestEncodingTest {

    @Test
    fun lengthPrefix_roundTrip() {
        val msg = byteArrayOf(0xAB.toByte(), 0xCD.toByte())
        val wire = ByteArray(2 + msg.size)
        wire[0] = ((msg.size shr 8) and 0xFF).toByte()
        wire[1] = (msg.size and 0xFF).toByte()
        System.arraycopy(msg, 0, wire, 2, msg.size)
        val inp = DataInputStream(ByteArrayInputStream(wire))
        val len = inp.readUnsignedShort()
        assertEquals(msg.size, len)
        val out = ByteArray(len)
        inp.readFully(out)
        assertEquals(msg.toList(), out.toList())
    }
}
