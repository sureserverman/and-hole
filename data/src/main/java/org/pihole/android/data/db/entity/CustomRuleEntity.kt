package org.pihole.android.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_rules")
data class CustomRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val value: String,
    val enabled: Boolean,
    val comment: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
