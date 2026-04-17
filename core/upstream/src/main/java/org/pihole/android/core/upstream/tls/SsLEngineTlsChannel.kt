package org.pihole.android.core.upstream.tls

import java.nio.ByteBuffer
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLEngineResult
import javax.net.ssl.SSLParameters
import org.pihole.android.core.upstream.transport.BidirectionalStream

class SsLEngineTlsChannel(
    private val host: String,
    private val port: Int,
    private val transport: BidirectionalStream,
    private val engineFactory: (String, Int) -> SSLEngine = { h, p ->
        SSLContext.getDefault().createSSLEngine(h, p)
    },
) : TlsChannel {
    private val engine = engineFactory(host, port).apply(configureClientEngine(host))
    private val netIn = ByteBuffer.allocate(engine.session.packetBufferSize.coerceAtLeast(16 * 1024))
    private val netOut = ByteBuffer.allocate(engine.session.packetBufferSize.coerceAtLeast(16 * 1024))
    private val appIn = ByteBuffer.allocate(engine.session.applicationBufferSize.coerceAtLeast(16 * 1024))

    init {
        appIn.limit(0)
        engine.beginHandshake()
        driveHandshake()
    }

    override fun writeFully(bytes: ByteArray) {
        driveHandshakeIfNeeded()
        val src = ByteBuffer.wrap(bytes)
        while (src.hasRemaining()) {
            netOut.clear()
            val result = engine.wrap(src, netOut)
            when (result.status) {
                SSLEngineResult.Status.OK -> {
                    flushNetOut()
                    when (result.handshakeStatus) {
                        SSLEngineResult.HandshakeStatus.NEED_TASK -> runDelegatedTasks()
                        SSLEngineResult.HandshakeStatus.NEED_WRAP,
                        SSLEngineResult.HandshakeStatus.NEED_UNWRAP,
                        -> driveHandshake()
                        SSLEngineResult.HandshakeStatus.FINISHED,
                        SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING,
                        -> Unit
                    }
                }
                SSLEngineResult.Status.BUFFER_OVERFLOW -> flushNetOut()
                SSLEngineResult.Status.CLOSED -> {
                    flushNetOut()
                    return
                }
                SSLEngineResult.Status.BUFFER_UNDERFLOW ->
                    error("SSLEngine.wrap() returned BUFFER_UNDERFLOW unexpectedly")
            }
        }
    }

    override fun readFully(length: Int): ByteArray {
        driveHandshakeIfNeeded()
        val out = ByteArray(length)
        var copied = 0
        while (copied < length) {
            copied += drainAppIn(out, copied, length - copied)
            if (copied >= length) break
            unwrapUntilApplicationData()
        }
        return out
    }

    override fun close() {
        runCatching {
            engine.closeOutbound()
            driveHandshake()
        }
        runCatching { engine.closeInbound() }
        runCatching { transport.close() }
    }

    private fun driveHandshakeIfNeeded() {
        if (engine.handshakeStatus != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
            driveHandshake()
        }
    }

    private fun driveHandshake() {
        while (true) {
            when (val status = engine.handshakeStatus) {
                SSLEngineResult.HandshakeStatus.NEED_WRAP -> {
                    netOut.clear()
                    val result = engine.wrap(EMPTY_BUFFER, netOut)
                    flushNetOut()
                    if (result.status == SSLEngineResult.Status.CLOSED) return
                }
                SSLEngineResult.HandshakeStatus.NEED_UNWRAP -> {
                    when (unwrapOnce().status) {
                        SSLEngineResult.Status.BUFFER_UNDERFLOW -> readOneNetworkByte()
                        SSLEngineResult.Status.CLOSED -> return
                        else -> Unit
                    }
                }
                SSLEngineResult.HandshakeStatus.NEED_TASK -> runDelegatedTasks()
                SSLEngineResult.HandshakeStatus.FINISHED,
                SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING,
                -> return
            }
        }
    }

    private fun unwrapUntilApplicationData() {
        while (appIn.remaining() == 0) {
            when (unwrapOnce().status) {
                SSLEngineResult.Status.OK -> {
                    if (appIn.remaining() > 0) return
                    if (engine.handshakeStatus != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
                        driveHandshake()
                    }
                }
                SSLEngineResult.Status.BUFFER_UNDERFLOW -> readOneNetworkByte()
                SSLEngineResult.Status.BUFFER_OVERFLOW -> error("Application buffer overflow")
                SSLEngineResult.Status.CLOSED -> error("TLS channel closed before reading expected bytes")
            }
        }
    }

    private fun unwrapOnce(): SSLEngineResult {
        appIn.compact()
        val dst = arrayOf(appIn)
        netIn.flip()
        val result = engine.unwrap(netIn, dst, 0, 1)
        netIn.compact()
        appIn.flip()
        return result
    }

    private fun runDelegatedTasks() {
        while (true) {
            val task = engine.delegatedTask ?: return
            task.run()
        }
    }

    private fun flushNetOut() {
        netOut.flip()
        if (netOut.hasRemaining()) {
            val bytes = ByteArray(netOut.remaining())
            netOut.get(bytes)
            transport.writeFully(bytes)
        }
        netOut.clear()
    }

    private fun readOneNetworkByte() {
        val next = transport.readFully(1)
        ensureCapacityForIncoming(next.size)
        netIn.put(next)
    }

    private fun ensureCapacityForIncoming(extra: Int) {
        if (netIn.remaining() >= extra) return
        error("Network input buffer too small for incoming TLS data")
    }

    private fun drainAppIn(target: ByteArray, offset: Int, maxCount: Int): Int {
        val count = minOf(maxCount, appIn.remaining())
        if (count > 0) {
            appIn.get(target, offset, count)
        }
        return count
    }

    private fun configureClientEngine(host: String): SSLEngine.() -> Unit = {
        useClientMode = true
        sslParameters =
            SSLParameters().also { params ->
                params.serverNames = listOf(SNIHostName(host))
            }
    }

    companion object {
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocate(0)
    }
}
