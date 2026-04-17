package org.pihole.android.feature.settings

import androidx.compose.runtime.staticCompositionLocalOf

/** Start/stop loopback DNS listener (app `DnsForegroundService`). */
data class DnsListenerActions(
    val startListener: () -> Unit,
    val stopListener: () -> Unit,
)

val LocalDnsListenerActions = staticCompositionLocalOf<DnsListenerActions?> { null }
