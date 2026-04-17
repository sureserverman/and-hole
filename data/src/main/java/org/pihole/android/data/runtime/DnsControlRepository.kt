package org.pihole.android.data.runtime

import kotlinx.coroutines.flow.Flow

interface DnsControlRepository {
    val snapshot: Flow<DnsControlSnapshot>

    suspend fun startListener()

    suspend fun stopListener()

    suspend fun refreshAdlists()
}
