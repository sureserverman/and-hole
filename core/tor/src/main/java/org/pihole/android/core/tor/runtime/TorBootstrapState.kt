package org.pihole.android.core.tor.runtime

sealed class TorBootstrapState {
    data object Stopped : TorBootstrapState()

    data class Starting(
        val progress: Int? = null,
        val summary: String? = null,
    ) : TorBootstrapState()

    data object Ready : TorBootstrapState()

    data class Failed(
        val message: String,
    ) : TorBootstrapState()
}
