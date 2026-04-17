package org.pihole.android.data.runtime

import kotlinx.coroutines.flow.Flow

interface UpstreamPolicyRepository {
    val resolvers: Flow<List<UpstreamResolver>>

    suspend fun listNow(): List<UpstreamResolver>

    suspend fun addResolver(
        label: String,
        host: String,
        port: Int,
        tlsServerName: String?,
        enabled: Boolean,
    ): Long

    suspend fun updateResolver(
        id: Long,
        label: String,
        host: String,
        port: Int,
        tlsServerName: String?,
        enabled: Boolean,
    )

    suspend fun deleteResolver(id: Long)
    suspend fun setResolverEnabled(id: Long, enabled: Boolean)
    suspend fun moveResolverUp(id: Long)
    suspend fun moveResolverDown(id: Long)
}
