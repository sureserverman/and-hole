package org.pihole.android.core.tor.runtime

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.pihole.android.core.tor.TorController
import org.pihole.android.core.tor.TorState
import org.pihole.android.core.upstream.transport.BidirectionalStream
import org.pihole.android.core.upstream.transport.StreamDialer

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TorRuntimeContractTest {
    @Test
    fun controller_readsBootstrapFromRuntimeInsteadOfSocksPort() =
        runTest {
            val runtime = FakeTorRuntime()
            val controller = TorController(runtime)

            controller.beginStartAndMonitor(this)
            runtime.emit(TorBootstrapState.Starting(progress = 10, summary = "bootstrapping"))
            runtime.emit(TorBootstrapState.Ready)
            advanceUntilIdle()

            assertThat(runtime.startedScopes).hasSize(1)
            assertThat(controller.state.value).isEqualTo(TorState.Ready)
            controller.stopMonitoring()
        }

    private class FakeTorRuntime : TorRuntime {
        private val bootstrapFlow = MutableStateFlow<TorBootstrapState>(TorBootstrapState.Stopped)
        val startedScopes = mutableListOf<CoroutineScope>()

        override val bootstrap: StateFlow<TorBootstrapState> = bootstrapFlow.asStateFlow()

        override fun start(scope: CoroutineScope) {
            startedScopes += scope
        }

        override fun dialer(): StreamDialer =
            StreamDialer { _, _ ->
                object : BidirectionalStream {
                    override fun writeFully(bytes: ByteArray) = Unit
                    override fun readFully(length: Int): ByteArray = ByteArray(length)
                    override fun close() = Unit
                }
            }

        override fun close() = Unit

        fun emit(state: TorBootstrapState) {
            bootstrapFlow.value = state
        }
    }
}
