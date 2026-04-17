package org.pihole.android.core.tor.runtime

import android.content.Context
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ArtiEmbeddedTorEngineBridge(
    context: Context,
) : EmbeddedTorEngineBridge {
    private val appContext = context.applicationContext
    private val dataDir: File = File(appContext.filesDir, "arti")
    private var pollJob: Job? = null

    init {
        ArtiJni.initOnce(dataDir)
    }

    override fun start(scope: CoroutineScope, listener: (TorBootstrapState) -> Unit) {
        pollJob?.cancel()
        pollJob =
            scope.launch(Dispatchers.Default) {
                listener(TorBootstrapState.Starting())

                val startOk =
                    runCatching { withContext(Dispatchers.IO) { ArtiJni.nativeStart() } }.isSuccess
                if (!startOk) {
                    val msg = runCatching { ArtiJni.nativeLastError() }.getOrNull().orEmpty()
                    listener(
                        TorBootstrapState.Failed(
                            if (msg.isNotBlank()) msg else "Arti bootstrap failed",
                        ),
                    )
                    return@launch
                }

                // `nativeStart` performs the full bootstrap; poll briefly for progress updates while
                // it is still marked as starting (should usually be a no-op after return).
                while (isActive && ArtiJni.nativeBootstrapState() == 1) {
                    val p = ArtiJni.nativeBootstrapProgress()
                    val s = ArtiJni.nativeBootstrapSummary()
                    listener(
                        TorBootstrapState.Starting(
                            progress = p,
                            summary = s.ifBlank { null },
                        ),
                    )
                    delay(100)
                }

                when (ArtiJni.nativeBootstrapState()) {
                    0 -> listener(TorBootstrapState.Stopped)
                    1 -> listener(TorBootstrapState.Starting()) // should be rare
                    2 -> listener(TorBootstrapState.Ready)
                    3 -> {
                        val msg = runCatching { ArtiJni.nativeLastError() }.getOrNull().orEmpty()
                        listener(
                            TorBootstrapState.Failed(
                                if (msg.isNotBlank()) msg else "Arti bootstrap failed",
                            ),
                        )
                    }
                }
            }
    }

    override fun openStream(host: String, port: Int): EmbeddedTorStream {
        val id = ArtiJni.nativeOpenStream(host, port)
        require(id != 0L) { "nativeOpenStream returned 0" }
        return ArtiStream(id)
    }

    override fun close() {
        pollJob?.cancel()
        pollJob = null
        runCatching { ArtiJni.nativeShutdown() }
    }

    private class ArtiStream(
        private val id: Long,
    ) : EmbeddedTorStream {
        override fun writeFully(bytes: ByteArray) {
            ArtiJni.nativeWrite(id, bytes)
        }

        override fun readFully(length: Int): ByteArray = ArtiJni.nativeRead(id, length)

        override fun close() {
            ArtiJni.nativeCloseStream(id)
        }
    }
}

