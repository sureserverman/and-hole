package org.pihole.android.core.tor.runtime

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.pihole.android.core.upstream.dot.SocksStreamDialer
import org.pihole.android.core.upstream.transport.StreamDialer
import org.torproject.jni.TorService

class TorServiceSocksRuntime(
    private val context: Context,
) : TorRuntime {
    private val streamDialer: StreamDialer = SocksStreamDialer("127.0.0.1", DEFAULT_SOCKS_PORT)
    private val _bootstrap = MutableStateFlow<TorBootstrapState>(TorBootstrapState.Stopped)
    override val bootstrap: StateFlow<TorBootstrapState> = _bootstrap.asStateFlow()

    private var readinessJob: Job? = null
    private var bootstrapJob: Job? = null
    private var receiverRegistered = false
    private var boundTorService: TorService? = null
    private var bound = false
    private var started = false

    private val statusReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    TorService.ACTION_STATUS -> {
                        val status = intent.getStringExtra(TorService.EXTRA_STATUS).orEmpty()
                        when (status) {
                            TorService.STATUS_STARTING -> _bootstrap.value = TorBootstrapState.Starting()
                            TorService.STATUS_ON -> {
                                if (_bootstrap.value !is TorBootstrapState.Ready) {
                                    _bootstrap.value = TorBootstrapState.Starting(summary = "Tor reported on")
                                }
                            }
                            TorService.STATUS_STOPPING,
                            TorService.STATUS_OFF,
                            -> _bootstrap.value = TorBootstrapState.Stopped
                        }
                    }

                    TorService.ACTION_ERROR -> {
                        val msg = intent.getStringExtra(Intent.EXTRA_TEXT) ?: "Tor error"
                        transitionToFailed(msg)
                    }
                }
            }
        }

    private val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                boundTorService = (service as? TorService.LocalBinder)?.getService()
                bound = boundTorService != null
            }

            override fun onServiceDisconnected(name: ComponentName) {
                boundTorService = null
                bound = false
            }
        }

    override fun start(scope: CoroutineScope) {
        if (started) return
        started = true
        ensureReceiverRegistered()

        val intent = Intent(context, TorService::class.java)
        @Suppress("DEPRECATION")
        context.startService(intent)
        runCatching {
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }

        _bootstrap.value = TorBootstrapState.Starting()
        readinessJob?.cancel()
        bootstrapJob?.cancel()

        readinessJob =
            scope.launch(Dispatchers.IO) {
                repeat(MAX_SOCKS_ATTEMPTS) {
                    if (!isActive) return@launch
                    if (tryConnectSocks()) {
                        promoteToReadyIfStarting()
                        return@launch
                    }
                    delay(POLL_INTERVAL_MS)
                }
            }

        bootstrapJob =
            scope.launch(Dispatchers.IO) {
                for (attempt in 0 until MAX_BIND_ATTEMPTS) {
                    if (!isActive) return@launch
                    if (bound) break
                    delay(200)
                    if (attempt == MAX_BIND_ATTEMPTS - 1 && !bound) {
                        if (tryConnectSocks()) {
                            _bootstrap.value = TorBootstrapState.Ready
                        } else {
                            transitionToFailed(
                                "TorService bind failed; SOCKS 127.0.0.1:$DEFAULT_SOCKS_PORT not reachable within ${MAX_SOCKS_ATTEMPTS * POLL_INTERVAL_MS / 1000}s",
                            )
                        }
                    }
                }

                val svc = boundTorService ?: return@launch
                repeat(MAX_BOOTSTRAP_ATTEMPTS) {
                    if (!isActive) return@launch
                    if (bootstrap.value is TorBootstrapState.Ready) return@launch
                    val phase = svc.getInfo("status/bootstrap-phase").orEmpty()
                    val progress = phase.substringAfter("PROGRESS=", "").takeWhile { it.isDigit() }.toIntOrNull()
                    val summary = phase.substringAfter("SUMMARY=", "").ifBlank { null }
                    _bootstrap.value = TorBootstrapState.Starting(progress = progress, summary = summary)
                    if (phase.contains("PROGRESS=100") || phase.contains("TAG=done")) {
                        _bootstrap.value = TorBootstrapState.Ready
                        return@launch
                    }
                    delay(POLL_INTERVAL_MS)
                }

                if (tryConnectSocks()) {
                    _bootstrap.value = TorBootstrapState.Ready
                } else {
                    transitionToFailed(
                        "Tor bootstrap did not reach PROGRESS=100 within ${MAX_BOOTSTRAP_ATTEMPTS * POLL_INTERVAL_MS / 1000}s and SOCKS is not reachable",
                    )
                }
            }
    }

    override fun dialer(): StreamDialer = streamDialer

    override fun close() {
        started = false
        readinessJob?.cancel()
        readinessJob = null
        bootstrapJob?.cancel()
        bootstrapJob = null
        if (bound) {
            runCatching { context.unbindService(serviceConnection) }
        }
        boundTorService = null
        bound = false
        unregisterReceiverIfNeeded()
        _bootstrap.value = TorBootstrapState.Stopped
    }

    private fun ensureReceiverRegistered() {
        if (receiverRegistered) return
        val filter =
            IntentFilter().apply {
                addAction(TorService.ACTION_STATUS)
                addAction(TorService.ACTION_ERROR)
            }
        ContextCompat.registerReceiver(
            context,
            statusReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true
    }

    private fun unregisterReceiverIfNeeded() {
        if (!receiverRegistered) return
        runCatching { context.unregisterReceiver(statusReceiver) }
        receiverRegistered = false
    }

    private fun transitionToFailed(message: String) {
        if (_bootstrap.value is TorBootstrapState.Ready) return
        _bootstrap.value = TorBootstrapState.Failed(message)
    }

    private fun promoteToReadyIfStarting() {
        if (_bootstrap.value is TorBootstrapState.Starting) {
            _bootstrap.value = TorBootstrapState.Ready
        }
    }

    companion object {
        const val DEFAULT_SOCKS_PORT: Int = 9050
        private const val POLL_INTERVAL_MS: Long = 1_000L
        private const val MAX_SOCKS_ATTEMPTS: Int = 120
        private const val MAX_BIND_ATTEMPTS: Int = 50
        private const val MAX_BOOTSTRAP_ATTEMPTS: Int = 180

        private fun tryConnectSocks(): Boolean =
            try {
                java.net.Socket().use { socket ->
                    socket.connect(java.net.InetSocketAddress("127.0.0.1", DEFAULT_SOCKS_PORT), 2_000)
                    true
                }
            } catch (_: Exception) {
                false
            }
    }
}
