package org.pihole.android.core.tor

sealed class TorState {
    data object Stopped : TorState()
    data object Starting : TorState()
    data object Ready : TorState()
    data class Failed(val message: String) : TorState()
}
