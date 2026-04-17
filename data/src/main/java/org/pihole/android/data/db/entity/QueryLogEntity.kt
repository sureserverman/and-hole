package org.pihole.android.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "query_log",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["decision"]),
        Index(value = ["qname", "timestamp"]),
    ],
)
data class QueryLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val qname: String,
    val qtype: Int,
    val decision: String,
    val matchedRuleId: Long?,
    val matchedSourceId: Long?,
    val responseCode: Int,
    val latencyMs: Long,
    val answeredFromCache: Boolean,
)
