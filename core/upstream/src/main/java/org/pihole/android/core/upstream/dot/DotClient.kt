package org.pihole.android.core.upstream.dot

import android.util.Log
import java.io.Closeable
import org.pihole.android.core.upstream.tls.PlatformTlsChannelFactory
import org.pihole.android.core.upstream.tls.TlsChannelFactory
import org.pihole.android.core.upstream.transport.StreamDialer

class DotClient(
    private val upstreamHost: String = "tor.cloudflare-dns.com",
    private val upstreamPort: Int = 853,
    private val tlsServerNameOverride: String? = null,
    private val dialerFactory: (Int) -> StreamDialer = { port -> SocksStreamDialer("127.0.0.1", port) },
    private val tlsFactory: TlsChannelFactory = PlatformTlsChannelFactory(),
) : Closeable {
    private val sessionLock = Any()
    private var pooledSession: PooledDotSession? = null
    private var pooledSocksPort: Int? = null
    private var pooledDialer: StreamDialer? = null

    val host: String get() = upstreamHost
    val port: Int get() = upstreamPort

    fun ensureConnectedOverSocks(socksPort: Int) {
        synchronized(sessionLock) {
            sessionForLocked(socksPort).ensureConnected()
        }
    }

    fun ensureConnected(dialer: StreamDialer) {
        synchronized(sessionLock) {
            sessionForLocked(dialer).ensureConnected()
        }
    }

    fun exchangeOverSocks(socksPort: Int, message: ByteArray): ByteArray =
        synchronized(sessionLock) {
            sessionForLocked(socksPort).exchange(message)
        }

    fun exchange(dialer: StreamDialer, message: ByteArray): ByteArray =
        synchronized(sessionLock) {
            sessionForLocked(dialer).exchange(message)
        }

    fun openOverSocks(socksPort: Int): DotConnection {
        logDebug("DoT begin host=$upstreamHost:$upstreamPort socks=127.0.0.1:$socksPort")
        return open(dialerFactory(socksPort))
    }

    fun open(dialer: StreamDialer): DotConnection {
        val transport = dialer.connect(upstreamHost, upstreamPort)
        try {
            val tlsServerName = tlsServerNameOverride?.takeIf { it.isNotBlank() } ?: upstreamHost
            logDebug("DoT transport connected, starting TLS")
            val tls = tlsFactory.openClientChannel(tlsServerName, upstreamPort, transport)
            logDebug("DoT TLS handshake…")
            logDebug("DoT TLS handshake OK")
            return object : DotConnection {
                override fun writeLengthPrefixed(message: ByteArray) {
                    val frame = ByteArray(2 + message.size)
                    frame[0] = ((message.size shr 8) and 0xFF).toByte()
                    frame[1] = (message.size and 0xFF).toByte()
                    System.arraycopy(message, 0, frame, 2, message.size)
                    tls.writeFully(frame)
                }

                override fun readLengthPrefixed(): ByteArray {
                    val header = tls.readFully(2)
                    val len = ((header[0].toInt() and 0xFF) shl 8) or (header[1].toInt() and 0xFF)
                    return tls.readFully(len)
                }

                override fun close() {
                    runCatching { tls.close() }
                }
            }
        } catch (e: Exception) {
            logWarn("DoT failed host=$upstreamHost:$upstreamPort ${e.javaClass.simpleName}: ${e.message}")
            runCatching { transport.close() }
            throw e
        }
    }

    override fun close() {
        synchronized(sessionLock) {
            closeSessionLocked()
        }
    }

    private fun sessionForLocked(socksPort: Int): PooledDotSession {
        if (pooledSession == null || pooledSocksPort != socksPort) {
            closeSessionLocked()
            pooledSocksPort = socksPort
            pooledDialer = null
            pooledSession = PooledDotSession { openOverSocks(socksPort) }
        }
        return checkNotNull(pooledSession)
    }

    private fun sessionForLocked(dialer: StreamDialer): PooledDotSession {
        if (pooledSession == null || pooledDialer !== dialer) {
            closeSessionLocked()
            pooledSocksPort = null
            pooledDialer = dialer
            pooledSession = PooledDotSession { open(dialer) }
        }
        return checkNotNull(pooledSession)
    }

    private fun closeSessionLocked() {
        runCatching { pooledSession?.close() }
        pooledSession = null
        pooledSocksPort = null
        pooledDialer = null
    }

    companion object {
        private const val TAG = "PiholeDns"
    }

    private fun logDebug(message: String) {
        runCatching { Log.d(TAG, message) }
    }

    private fun logWarn(message: String) {
        runCatching { Log.w(TAG, message) }
    }
}
