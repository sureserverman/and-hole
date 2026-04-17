package org.pihole.android.core.tor.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.pihole.android.core.upstream.transport.BidirectionalStream
import org.pihole.android.core.upstream.transport.StreamDialer

class EmbeddedTorRuntime(
    private val engine: EmbeddedTorEngineBridge,
) : TorRuntime {
    private val _bootstrap = MutableStateFlow<TorBootstrapState>(TorBootstrapState.Stopped)
    override val bootstrap: StateFlow<TorBootstrapState> = _bootstrap.asStateFlow()

    private var started = false
    private val openStreams = mutableSetOf<EmbeddedTorStream>()

    private val streamDialer =
        StreamDialer { host, port ->
            val stream =
                try {
                    engine.openStream(host, port)
                } catch (e: Exception) {
                    // A transient stream-open failure after bootstrap should not poison the runtime state:
                    // later connects can still succeed against the same embedded Tor client.
                    if (_bootstrap.value !is TorBootstrapState.Ready) {
                        _bootstrap.value = TorBootstrapState.Failed(e.message ?: e.javaClass.simpleName)
                    }
                    throw e
                }
            openStreams += stream
            object : BidirectionalStream {
                override fun writeFully(bytes: ByteArray) {
                    stream.writeFully(bytes)
                }

                override fun readFully(length: Int): ByteArray = stream.readFully(length)

                override fun close() {
                    runCatching { stream.close() }
                    openStreams -= stream
                }
            }
        }

    override fun start(scope: CoroutineScope) {
        if (started) return
        started = true
        _bootstrap.value = TorBootstrapState.Starting()
        try {
            engine.start(scope) { state ->
                _bootstrap.value = state
            }
        } catch (e: Exception) {
            _bootstrap.value = TorBootstrapState.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    override fun dialer(): StreamDialer = streamDialer

    override fun close() {
        openStreams.toList().forEach { stream ->
            runCatching { stream.close() }
            openStreams -= stream
        }
        runCatching { engine.close() }
        started = false
        _bootstrap.value = TorBootstrapState.Stopped
    }
}
