package org.pihole.android.core.upstream.transport

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket

class SocketBidirectionalStream(
    val socket: Socket,
) : BidirectionalStream {
    private val input = DataInputStream(socket.getInputStream())
    private val output = DataOutputStream(socket.getOutputStream())

    override fun writeFully(bytes: ByteArray) {
        output.write(bytes)
        output.flush()
    }

    override fun readFully(length: Int): ByteArray {
        val buf = ByteArray(length)
        input.readFully(buf)
        return buf
    }

    override fun close() {
        socket.close()
    }
}
