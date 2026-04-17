package org.pihole.android.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "refresh_runs")
data class RefreshRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val finishedAt: Long?,
    val sourcesChecked: Int,
    val sourcesUpdated: Int,
    val sourcesFailed: Int,
    val snapshotId: Long?,
    val status: String,
)
