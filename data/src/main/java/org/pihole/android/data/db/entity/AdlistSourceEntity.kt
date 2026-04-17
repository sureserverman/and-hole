package org.pihole.android.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "adlist_sources")
data class AdlistSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val enabled: Boolean,
    val etag: String?,
    val lastModified: String?,
    val lastRefreshStartedAt: Long?,
    val lastSuccessAt: Long?,
    val lastResult: String?,
    val lastError: String?,
)
