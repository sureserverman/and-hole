package org.pihole.android.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "compiled_snapshots")
data class CompiledSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val ruleCountExactAllow: Int,
    val ruleCountExactDeny: Int,
    val ruleCountSuffixDeny: Int,
    val ruleCountRegexAllow: Int,
    val ruleCountRegexDeny: Int,
    val checksum: String,
)
