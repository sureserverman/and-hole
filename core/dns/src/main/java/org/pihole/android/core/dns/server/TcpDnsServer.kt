package org.pihole.android.core.dns.server

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel

class TcpDnsServer(
    private val scope: CoroutineScope,
    private val listenAddress: InetAddress,
    private val port: Int,
    private val handler: suspend (ByteArray) -> ByteArray?,
) {
    private var server: ServerSocketChannel? = null
    private var job: Job? = null

    /**
     * Binds the TCP DNS length-prefixed server on [Dispatchers.IO].
     * The returned deferred completes right after [ServerSocketChannel.bind].
     */
    fun start(): CompletableDeferred<Unit> {
        val bound = CompletableDeferred<Unit>()
        job = scope.launch(Dispatchers.IO) {
            try {
                val ch = ServerSocketChannel.open()
                server = ch
                ch.setOption(StandardSocketOptions.SO_REUSEADDR, true)
                ch.bind(InetSocketAddress(listenAddress, port))
                ch.configureBlocking(true)
                bound.complete(Unit)
                while (isActive) {
                    try {
                        val client: SocketChannel = ch.accept()
                        scope.launch(Dispatchers.IO) {
                            try {
                                handleClient(client)
                            } finally {
                                client.close()
                            }
                        }
                    } catch (_: java.nio.channels.AsynchronousCloseException) {
                        break
                    } catch (_: java.nio.channels.ClosedChannelException) {
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

    private suspend fun handleClient(client: SocketChannel) {
        val lenBuf = ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN)
        if (!readFully(client, lenBuf)) return
        lenBuf.flip()
        val msgLen = lenBuf.short.toInt() and 0xFFFF
        val msgBuf = ByteBuffer.allocate(msgLen)
        if (!readFully(client, msgBuf)) return
        val query = msgBuf.array()
        val response = handler(query) ?: return
        val outLen = ByteBuffer.allocate(2 + response.size).order(ByteOrder.BIG_ENDIAN)
        outLen.putShort(response.size.toShort())
        outLen.put(response)
        outLen.flip()
        while (outLen.hasRemaining()) {
            client.write(outLen)
        }
    }

    private fun readFully(ch: SocketChannel, buf: ByteBuffer): Boolean {
        while (buf.hasRemaining()) {
            val n = ch.read(buf)
            if (n < 0) return false
        }
        return true
    }

    fun stop() {
        val ch = server
        server = null
        try {
            ch?.close()
        } catch (_: Exception) {
        }
        job?.cancel()
        job = null
    }
}
