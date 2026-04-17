package org.pihole.android.core.upstream.dot

import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import org.pihole.android.core.upstream.transport.BidirectionalStream
import org.pihole.android.core.upstream.transport.SocketBidirectionalStream
import org.pihole.android.core.upstream.transport.StreamDialer

class SocksStreamDialer(
    private val socksHost: String,
    private val socksPort: Int,
) : StreamDialer {
    override fun connect(host: String, port: Int): BidirectionalStream {
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(socksHost, socksPort))
        val socket = Socket(proxy)
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
