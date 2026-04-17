package org.pihole.android.core.tor.runtime

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class EmbeddedTorRuntimeTest {

    @Test
    fun dialer_returnsStreamWithoutLocalSocksDependency() =
        runTest {
            val engine = FakeEmbeddedEngine()
            val runtime = EmbeddedTorRuntime(engine)

            runtime.start(this)
            advanceUntilIdle()
            val stream = runtime.dialer().connect("resolver.example", 853)

            assertThat(engine.startCalls).isEqualTo(1)
            assertThat(runtime.bootstrap.value).isEqualTo(TorBootstrapState.Ready)
            assertThat(engine.connectRequests).containsExactly("resolver.example" to 853)
            assertThat(stream).isNotNull()
            stream.close()
            runtime.close()
        }

    @Test
    fun transientOpenStreamFailureAfterReady_doesNotDemoteBootstrapState() =
        runTest {
            val engine = FakeEmbeddedEngine(failNextOpen = true)
            val runtime = EmbeddedTorRuntime(engine)

            runtime.start(this)
            advanceUntilIdle()

            runCatching { runtime.dialer().connect("resolver.example", 853) }

            assertThat(runtime.bootstrap.value).isEqualTo(TorBootstrapState.Ready)

            val recovered = runtime.dialer().connect("resolver.example", 853)
            assertThat(runtime.bootstrap.value).isEqualTo(TorBootstrapState.Ready)

            recovered.close()
            runtime.close()
        }

    private class FakeEmbeddedEngine(
        private var failNextOpen: Boolean = false,
    ) : EmbeddedTorEngineBridge {
        var startCalls = 0
        val connectRequests = mutableListOf<Pair<String, Int>>()

        override fun start(scope: CoroutineScope, listener: (TorBootstrapState) -> Unit) {
            startCalls += 1
            listener(TorBootstrapState.Starting(progress = 25, summary = "bootstrapping"))
            listener(TorBootstrapState.Ready)
        }

        override fun openStream(host: String, port: Int): EmbeddedTorStream {
            connectRequests += host to port
            if (failNextOpen) {
                failNextOpen = false
                throw IOException("transient connect failure")
            }
            return object : EmbeddedTorStream {
                override fun writeFully(bytes: ByteArray) = Unit
                override fun readFully(length: Int): ByteArray = ByteArray(length)
                override fun close() = Unit
            }
        }

        override fun close() = Unit
    }
}
