package org.pihole.android.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.pihole.android.data.db.entity.QueryLogEntity

data class DomainHitStat(
    val qname: String,
    val hits: Int,
)

data class DecisionCountStat(
    val decision: String,
    val hits: Int,
)

@Dao
interface QueryLogDao {
    @Query("SELECT COUNT(*) FROM query_log")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM query_log ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<QueryLogEntity>>

    @Query(
        """
        SELECT qname
        FROM query_log
        WHERE decision = 'blocked'
        ORDER BY timestamp DESC
        LIMIT :limit
        """,
    )
    fun observeRecentBlockedDomains(limit: Int): Flow<List<String>>

    @Query("SELECT * FROM query_log ORDER BY timestamp DESC LIMIT :limit")
    suspend fun listRecent(limit: Int): List<QueryLogEntity>

    @Query(
        """
        SELECT qname, COUNT(*) AS hits
        FROM query_log
        WHERE decision = 'blocked' AND timestamp >= :sinceEpochMs
        GROUP BY qname
        ORDER BY hits DESC
        LIMIT :limit
        """,
    )
    suspend fun topBlockedDomains(sinceEpochMs: Long, limit: Int): List<DomainHitStat>

    @Query(
        """
        SELECT qname, COUNT(*) AS hits
        FROM query_log
        WHERE decision = 'allowed' AND timestamp >= :sinceEpochMs
        GROUP BY qname
        ORDER BY hits DESC
        LIMIT :limit
        """,
    )
    suspend fun topAllowedDomains(sinceEpochMs: Long, limit: Int): List<DomainHitStat>

    @Query(
        """
        SELECT decision, COUNT(*) AS hits
        FROM query_log
        WHERE timestamp >= :sinceEpochMs
        GROUP BY decision
        """,
    )
    suspend fun decisionCounts(sinceEpochMs: Long): List<DecisionCountStat>

    @Query(
        """
        SELECT *
        FROM query_log
        WHERE decision = 'blocked'
          AND (matchedRuleId IS NOT NULL OR matchedSourceId IS NOT NULL)
        ORDER BY timestamp DESC
        LIMIT :limit
        """,
    )
    suspend fun latestBlockingAttributionRows(limit: Int): List<QueryLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: QueryLogEntity): Long

    @Query("DELETE FROM query_log")
    suspend fun deleteAll(): Int

    @Query("DELETE FROM query_log WHERE timestamp < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long): Int

    @Query(
        """
        DELETE FROM query_log
        WHERE id NOT IN (
            SELECT id FROM query_log
            ORDER BY timestamp DESC
            LIMIT :keep
        )
        """,
    )
    suspend fun deleteAllExceptNewest(keep: Int): Int
}
