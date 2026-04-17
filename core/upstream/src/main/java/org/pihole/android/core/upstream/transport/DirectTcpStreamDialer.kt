package org.pihole.android.core.upstream.transport

import java.net.InetSocketAddress
import java.net.Socket

class DirectTcpStreamDialer : StreamDialer {
    override fun connect(host: String, port: Int): BidirectionalStream {
        val socket = Socket()
        try {
            socket.tcpNoDelay = true
            socket.soTimeout = 30_000
            socket.connect(InetSocketAddress.createUnresolved(host, port), 30_000)
            return SocketBidirectionalStream(socket)
        } catch (e: Exception) {
            runCatching { socket.close() }
            throw e
        }
    }
}

