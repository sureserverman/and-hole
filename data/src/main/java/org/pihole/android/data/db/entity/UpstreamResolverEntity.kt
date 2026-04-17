package org.pihole.android.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "upstream_resolvers")
data class UpstreamResolverEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val host: String,
    val port: Int,
    val tlsServerName: String?,
    val enabled: Boolean,
    val sortOrder: Int,
)
