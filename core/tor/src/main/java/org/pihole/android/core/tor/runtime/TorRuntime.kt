package org.pihole.android.core.tor.runtime

import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import org.pihole.android.core.upstream.transport.StreamDialer

interface TorRuntime : Closeable {
    val bootstrap: StateFlow<TorBootstrapState>

    fun start(scope: CoroutineScope)

    fun dialer(): StreamDialer
}
