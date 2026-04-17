package org.pihole.android.core.dns.server

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.net.SocketException

class UdpDnsServer(
    private val scope: CoroutineScope,
    private val listenAddress: InetAddress,
    private val port: Int,
    private val handler: suspend (ByteArray) -> ByteArray?,
) {
    private var socket: DatagramSocket? = null
    private var job: Job? = null

    /**
     * Binds the socket on [Dispatchers.IO] then receives until stopped.
     * The returned deferred completes when the UDP socket is bound and ready for packets
     * (callers should [kotlinx.coroutines.CompletableDeferred.await] before assuming the port is open).
     */
    fun start(): CompletableDeferred<Unit> {
        val bound = CompletableDeferred<Unit>()
        job = scope.launch(Dispatchers.IO) {
            try {
                val ds =
                    try {
                        DatagramSocket(null as SocketAddress?).apply {
                            reuseAddress = true
                            bind(InetSocketAddress(listenAddress, port))
                        }
                    } catch (_: Exception) {
                        DatagramSocket(port, listenAddress)
                    }
                socket = ds
                bound.complete(Unit)
                val buf = ByteArray(8192)
                while (isActive) {
                    try {
                        val pkt = DatagramPacket(buf, buf.size)
                        ds.receive(pkt)
                        val data = buf.copyOf(pkt.length)
                        val response = handler(data) ?: continue
                        val out = DatagramPacket(response, response.size, pkt.socketAddress)
                        ds.send(out)
                    } catch (_: SocketException) {
                        break
                    }
                }
            } catch (e: Throwable) {
                if (!bound.isCompleted) {
                    bound.completeExceptionally(e)
                }
                throw e
            }
        }
        return bound
    }

    fun stop() {
        val s = socket
        socket = null
        try {
            s?.close()
        } catch (_: Exception) {
        }
        job?.cancel()
        job = null
    }
}
