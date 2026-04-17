package org.pihole.android.core.tor.runtime

import java.io.Closeable
import kotlinx.coroutines.CoroutineScope

interface EmbeddedTorEngineBridge : Closeable {
    fun start(scope: CoroutineScope, listener: (TorBootstrapState) -> Unit)

    fun openStream(host: String, port: Int): EmbeddedTorStream
}
