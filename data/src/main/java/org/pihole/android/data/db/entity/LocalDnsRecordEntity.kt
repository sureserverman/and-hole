package org.pihole.android.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_dns_records")
data class LocalDnsRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: Int,
    val value: String,
    val ttl: Int,
    val enabled: Boolean,
)
