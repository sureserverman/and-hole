package org.pihole.android.core.tor.runtime

import android.content.Context

object TorRuntimeFactory {
    const val MODE_AUTO: String = "auto"
    const val MODE_EMBEDDED: String = "embedded"
    const val MODE_COMPATIBILITY: String = "compatibility"

    data class Creation(
        val runtime: TorRuntime,
        val runtimeModeLabel: String,
    )

    fun create(context: Context, requestedMode: String): Creation {
        val appContext = context.applicationContext
        return when (requestedMode) {
            MODE_EMBEDDED ->
                Creation(
                    runtime = EmbeddedTorRuntime(ArtiEmbeddedTorEngineBridge(appContext)),
                    runtimeModeLabel = "Embedded Arti runtime (forced)",
                )
            MODE_COMPATIBILITY ->
                Creation(
                    runtime = TorServiceSocksRuntime(appContext),
                    runtimeModeLabel = "TorService compatibility runtime (forced)",
                )
            else ->
                runCatching {
                    Creation(
                        runtime = EmbeddedTorRuntime(ArtiEmbeddedTorEngineBridge(appContext)),
                        runtimeModeLabel = "Embedded Arti runtime (auto)",
                    )
                }.getOrElse {
                    Creation(
                        runtime = TorServiceSocksRuntime(appContext),
                        runtimeModeLabel = "TorService compatibility runtime (auto fallback)",
                    )
                }
        }
    }
}
