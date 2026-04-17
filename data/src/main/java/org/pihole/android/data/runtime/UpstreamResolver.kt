package org.pihole.android.data.runtime

data class UpstreamResolver(
    val id: Long,
    val label: String,
    val host: String,
    val port: Int,
    val tlsServerName: String?,
    val enabled: Boolean,
    val sortOrder: Int,
) {
    val tlsNameOrHost: String
        get() = tlsServerName?.takeIf { it.isNotBlank() } ?: host
}

