package org.pihole.android.core.tor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.pihole.android.core.tor.runtime.TorBootstrapState
import org.pihole.android.core.tor.runtime.TorRuntime

/**
 * Thin adapter from [TorRuntime.bootstrap] to the app-facing [TorState].
 */
class TorController(
    private val runtime: TorRuntime,
) {
    private val _state = MutableStateFlow<TorState>(TorState.Stopped)
    val state: StateFlow<TorState> = _state.asStateFlow()

    private var bootstrapJob: Job? = null

    fun beginStartAndMonitor(scope: CoroutineScope) {
        bootstrapJob?.cancel()
        runtime.start(scope)
        _state.value = TorState.Starting
        bootstrapJob =
            scope.launch {
                runtime.bootstrap.collect { bootstrap ->
                    _state.value =
                        when (bootstrap) {
                            is TorBootstrapState.Stopped -> TorState.Stopped
                            is TorBootstrapState.Starting -> TorState.Starting
                            is TorBootstrapState.Ready -> TorState.Ready
                            is TorBootstrapState.Failed -> TorState.Failed(bootstrap.message)
                        }
                }
            }
    }

    fun stopMonitoring() {
        bootstrapJob?.cancel()
        bootstrapJob = null
        runtime.close()
        _state.value = TorState.Stopped
    }
}
