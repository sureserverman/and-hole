package org.pihole.android.resolver

import android.util.Log
import java.io.Closeable
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.pihole.android.core.dns.upstream.DnsUpstream
import org.pihole.android.core.tor.TorController
import org.pihole.android.core.tor.TorState
import org.pihole.android.core.upstream.dot.DotClient
import org.pihole.android.core.upstream.transport.StreamDialer
import org.pihole.android.data.runtime.DnsUpstreamDebugStatus
import org.pihole.android.data.runtime.DnsUpstreamStatus
import org.pihole.android.data.runtime.UpstreamResolver
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException

/**
 * DNS-over-TLS over either Tor or direct transport depending on the current policy.
 */
class TorDotDnsUpstream(
    private val useTor: Boolean,
    private val resolvers: List<UpstreamResolver>,
    private val torController: TorController? = null,
    private val torDialer: (() -> StreamDialer)? = null,
    private val directDialer: () -> StreamDialer,
) : DnsUpstream, Closeable {
    private val dotClients: List<DotClient> =
        resolvers
            .filter { it.enabled }
            .sortedBy { it.sortOrder }
            .map { resolver ->
                DotClient(
                    upstreamHost = resolver.host,
                    upstreamPort = resolver.port,
                    tlsServerNameOverride = resolver.tlsServerName,
                )
            }
    private val resolverNames: List<String> =
        resolvers
            .filter { it.enabled }
            .sortedBy { it.sortOrder }
            .map { "${it.label} (${it.host}:${it.port})" }

    override suspend fun forward(query: ByteArray): ByteArray? =
        withContext(Dispatchers.IO) {
            if (useTor) {
                val tc = torController
                if (tc == null || torDialer == null) {
                    Log.e(TAG, "Tor-enabled policy requested but runtime is unavailable")
                    DnsUpstreamStatus.status =
                        DnsUpstreamDebugStatus(
                            lastFailureMessage = "Tor enabled but runtime unavailable",
                        )
                    return@withContext null
                }
                val reached =
                    withTimeoutOrNull(TOR_WAIT_FOR_READY_MS) {
                        tc.state.first { st ->
                            st is TorState.Ready || st is TorState.Failed
                        }
                    }
                if (reached == null) {
                    Log.w(TAG, "Tor state did not reach Ready/Failed within ${TOR_WAIT_FOR_READY_MS}ms; attempting DoT anyway")
                } else {
                    Log.d(TAG, "Tor gate: ${reached::class.simpleName}")
                }
            }

            if (dotClients.isEmpty()) {
                DnsUpstreamStatus.status =
                    DnsUpstreamDebugStatus(
                        lastFailureMessage = "No enabled upstream resolvers configured",
                    )
                return@withContext null
            }

            var failoverCount = 0L
            var lastFailedResolver: String? = null
            var lastFailureMessage: String? = null
            for ((index, client) in dotClients.withIndex()) {
                val resolverName = resolverNames[index]
                repeat(PER_ENDPOINT_ATTEMPTS) { attempt ->
                    try {
                        Log.d(TAG, "upstream ${client.host}:${client.port} attempt ${attempt + 1}/$PER_ENDPOINT_ATTEMPTS")
                        val dialer = if (useTor) torDialer!!.invoke() else directDialer()
                        val reply = client.exchange(dialer, query)
                        Log.i(TAG, "upstream OK ${client.host}:${client.port} replyBytes=${reply.size}")
                        DnsUpstreamStatus.status =
                            DnsUpstreamDebugStatus(
                                activeResolver = resolverName,
                                lastFailedResolver = lastFailedResolver,
                                lastFailureMessage = lastFailureMessage,
                                failoverCount = failoverCount,
                                lastFailoverEvent = if (failoverCount > 0) "$lastFailedResolver -> $resolverName" else null,
                            )
                        return@withContext reply
                    } catch (e: Exception) {
                        lastFailedResolver = resolverName
                        lastFailureMessage = "${client.host}:${client.port} ${e.javaClass.simpleName}: ${e.message}"
                        Log.w(TAG, "upstream fail ${client.host}:${client.port} ${e.javaClass.simpleName}: ${e.message}")
                        if (attempt < PER_ENDPOINT_ATTEMPTS - 1 && isTransientUpstreamFailure(e)) {
                            val backoff = RETRY_BACKOFF_MS * (attempt + 1)
                            Log.d(TAG, "upstream transient, backoff ${backoff}ms")
                            delay(backoff)
                        } else if (attempt == PER_ENDPOINT_ATTEMPTS - 1 && index < dotClients.lastIndex) {
                            failoverCount += 1
                        }
                    }
                }
            }
            Log.e(TAG, "upstream exhausted all DoT endpoints")
            DnsUpstreamStatus.status =
                DnsUpstreamDebugStatus(
                    lastFailedResolver = lastFailedResolver,
                    lastFailureMessage = lastFailureMessage ?: "upstream exhausted all DoT endpoints",
                    failoverCount = failoverCount,
                )
            null
        }

    suspend fun prewarm() {
        withContext(Dispatchers.IO) {
            for ((index, client) in dotClients.withIndex()) {
                repeat(PER_ENDPOINT_ATTEMPTS) { attempt ->
                    try {
                        Log.d(TAG, "upstream prewarm ${client.host}:${client.port} attempt ${attempt + 1}/$PER_ENDPOINT_ATTEMPTS")
                        val dialer = if (useTor) torDialer?.invoke() ?: return@withContext else directDialer()
                        client.ensureConnected(dialer)
                        Log.i(TAG, "upstream prewarm OK ${client.host}:${client.port}")
                        DnsUpstreamStatus.status =
                            DnsUpstreamDebugStatus(
                                activeResolver = resolverNames.getOrNull(index),
                            )
                        return@withContext
                    } catch (e: Exception) {
                        DnsUpstreamStatus.status =
                            DnsUpstreamDebugStatus(
                                lastFailedResolver = resolverNames.getOrNull(index),
                                lastFailureMessage = "${client.host}:${client.port} ${e.javaClass.simpleName}: ${e.message}",
                            )
                        Log.w(TAG, "upstream prewarm fail ${client.host}:${client.port} ${e.javaClass.simpleName}: ${e.message}")
                        if (attempt < PER_ENDPOINT_ATTEMPTS - 1 && isTransientUpstreamFailure(e)) {
                            val backoff = RETRY_BACKOFF_MS * (attempt + 1)
                            Log.d(TAG, "upstream prewarm transient, backoff ${backoff}ms")
                            delay(backoff)
                        }
                    }
                }
            }
            Log.w(TAG, "upstream prewarm exhausted all DoT endpoints")
        }
    }

    override fun close() {
        dotClients.forEach { client ->
            runCatching { client.close() }
        }
    }

    companion object {
        private const val TAG = "PiholeDns"

        /** Match slow Tor bootstrap on real hardware (see TorController bootstrap / SOCKS timing). */
        private const val TOR_WAIT_FOR_READY_MS: Long = 120_000L
        private const val PER_ENDPOINT_ATTEMPTS: Int = 3
        private const val RETRY_BACKOFF_MS: Long = 450L

        private fun isTransientUpstreamFailure(e: Throwable): Boolean =
            when (e) {
                is SocketTimeoutException,
                is SSLException,
                is IOException,
                -> true
                else -> e.cause?.let { isTransientUpstreamFailure(it) } == true
            }
    }
}
