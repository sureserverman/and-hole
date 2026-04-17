package org.pihole.android.core.upstream.tls

import com.google.common.truth.Truth.assertThat
import java.nio.ByteBuffer
import java.security.Principal
import java.security.cert.Certificate
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLEngineResult
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSessionContext
import org.junit.Test
import org.pihole.android.core.upstream.transport.BidirectionalStream

class SsLEngineTlsChannelTest {

    @Test
    fun channel_writesAndReadsApplicationBytes() {
        val transport =
            FakeBidirectionalStream(
                reads = ArrayDeque(listOf(0x22.toByte(), 0x00, 0x02, 0xCC.toByte(), 0xDD.toByte())),
            )
        val channel =
            SsLEngineTlsChannel(
                host = "resolver.example",
                port = 853,
                transport = transport,
                engineFactory = { _: String, _: Int -> FakeTlsEngine() },
            )

        channel.writeFully(byteArrayOf(0x00, 0x02, 0xAA.toByte(), 0xBB.toByte()))
        val reply = channel.readFully(4)

        assertThat(transport.writes.map { it.toList() }).containsExactly(
            listOf<Byte>(0x11),
            listOf<Byte>(0x00, 0x02, 0xAA.toByte(), 0xBB.toByte()),
        ).inOrder()
        assertThat(reply.toList()).isEqualTo(listOf<Byte>(0x00, 0x02, 0xCC.toByte(), 0xDD.toByte()))
    }

    private class FakeBidirectionalStream(
        reads: ArrayDeque<Byte>,
    ) : BidirectionalStream {
        private val readQueue = reads
        val writes = mutableListOf<ByteArray>()

        override fun writeFully(bytes: ByteArray) {
            writes += bytes.copyOf()
        }

        override fun readFully(length: Int): ByteArray {
            require(readQueue.size >= length)
            return ByteArray(length) { readQueue.removeFirst() }
        }

        override fun close() = Unit
    }

    private class FakeTlsEngine : SSLEngine() {
        private val session = FakeSslSession()
        private var handshakeStatus = SSLEngineResult.HandshakeStatus.NEED_WRAP
        private var outboundClosed = false
        private var inboundClosed = false
        private var params: SSLParameters = SSLParameters()
        private var clientMode = false

        override fun beginHandshake() {
            handshakeStatus = SSLEngineResult.HandshakeStatus.NEED_WRAP
        }

        override fun getHandshakeStatus(): SSLEngineResult.HandshakeStatus = handshakeStatus

        override fun wrap(
            srcs: Array<out ByteBuffer>,
            offset: Int,
            length: Int,
            dst: ByteBuffer,
        ): SSLEngineResult {
            if (handshakeStatus == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
                dst.put(0x11)
                handshakeStatus = SSLEngineResult.HandshakeStatus.NEED_UNWRAP
                return SSLEngineResult(
                    SSLEngineResult.Status.OK,
                    handshakeStatus,
                    0,
                    1,
                )
            }

            var consumed = 0
            for (i in offset until offset + length) {
                val src = srcs[i]
                while (src.hasRemaining()) {
                    dst.put(src.get())
                    consumed++
                }
            }
            handshakeStatus = SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING
            return SSLEngineResult(
                SSLEngineResult.Status.OK,
                handshakeStatus,
                consumed,
                consumed,
            )
        }

        override fun unwrap(
            src: ByteBuffer,
            dsts: Array<out ByteBuffer>,
            offset: Int,
            length: Int,
        ): SSLEngineResult {
            if (handshakeStatus == SSLEngineResult.HandshakeStatus.NEED_UNWRAP) {
                if (!src.hasRemaining()) {
                    return SSLEngineResult(
                        SSLEngineResult.Status.BUFFER_UNDERFLOW,
                        handshakeStatus,
                        0,
                        0,
                    )
                }
                src.get()
                handshakeStatus = SSLEngineResult.HandshakeStatus.FINISHED
                return SSLEngineResult(
                    SSLEngineResult.Status.OK,
                    SSLEngineResult.HandshakeStatus.FINISHED,
                    1,
                    0,
                )
            }

            val dst = dsts[offset]
            var consumed = 0
            var produced = 0
            while (src.hasRemaining() && dst.hasRemaining()) {
                dst.put(src.get())
                consumed++
                produced++
            }
            handshakeStatus = SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING
            return SSLEngineResult(
                if (consumed == 0) SSLEngineResult.Status.BUFFER_UNDERFLOW else SSLEngineResult.Status.OK,
                handshakeStatus,
                consumed,
                produced,
            )
        }

        override fun closeInbound() {
            inboundClosed = true
        }

        override fun isInboundDone(): Boolean = inboundClosed

        override fun closeOutbound() {
            outboundClosed = true
        }

        override fun isOutboundDone(): Boolean = outboundClosed

        override fun getDelegatedTask(): Runnable? = null

        override fun getEnabledCipherSuites(): Array<String> = emptyArray()

        override fun setEnabledCipherSuites(suites: Array<out String>?) = Unit

        override fun getEnabledProtocols(): Array<String> = emptyArray()

        override fun setEnabledProtocols(protocols: Array<out String>?) = Unit

        override fun getSession(): SSLSession = session

        override fun getSupportedCipherSuites(): Array<String> = emptyArray()

        override fun getSupportedProtocols(): Array<String> = emptyArray()

        override fun setUseClientMode(mode: Boolean) {
            clientMode = mode
        }

        override fun getUseClientMode(): Boolean = clientMode

        override fun setNeedClientAuth(need: Boolean) = Unit

        override fun getNeedClientAuth(): Boolean = false

        override fun setWantClientAuth(want: Boolean) = Unit

        override fun getWantClientAuth(): Boolean = false

        override fun setEnableSessionCreation(flag: Boolean) = Unit

        override fun getEnableSessionCreation(): Boolean = true

        override fun setSSLParameters(params: SSLParameters?) {
            this.params = params ?: SSLParameters()
        }

        override fun getSSLParameters(): SSLParameters = params
    }

    private class FakeSslSession : SSLSession {
        override fun getApplicationBufferSize(): Int = 16 * 1024
        override fun getPacketBufferSize(): Int = 16 * 1024
        override fun getCipherSuite(): String = "fake"
        override fun getCreationTime(): Long = 0L
        override fun getId(): ByteArray = ByteArray(0)
        override fun getLastAccessedTime(): Long = 0L
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getLocalPrincipal(): Principal? = null
        override fun getPeerCertificateChain(): Array<javax.security.cert.X509Certificate> = emptyArray()
        override fun getPeerCertificates(): Array<Certificate> = emptyArray()
        override fun getPeerHost(): String = "resolver.example"
        override fun getPeerPort(): Int = 853
        override fun getPeerPrincipal(): Principal? = null
        override fun getProtocol(): String = "TLSv1.3"
        override fun getSessionContext(): SSLSessionContext? = null
        override fun getValue(name: String?): Any? = null
        override fun getValueNames(): Array<String> = emptyArray()
        override fun invalidate() = Unit
        override fun isValid(): Boolean = true
        override fun putValue(name: String?, value: Any?) = Unit
        override fun removeValue(name: String?) = Unit
    }
}
