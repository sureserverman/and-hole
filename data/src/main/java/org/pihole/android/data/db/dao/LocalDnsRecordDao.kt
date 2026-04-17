package org.pihole.android.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.pihole.android.data.db.entity.LocalDnsRecordEntity

@Dao
interface LocalDnsRecordDao {
    @Query("SELECT * FROM local_dns_records ORDER BY id ASC")
    fun observeAll(): Flow<List<LocalDnsRecordEntity>>

    @Query("SELECT COUNT(*) FROM local_dns_records")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM local_dns_records")
    suspend fun getAll(): List<LocalDnsRecordEntity>

    @Insert
    suspend fun insert(entity: LocalDnsRecordEntity): Long

    @Update
    suspend fun update(entity: LocalDnsRecordEntity): Int

    @Query("DELETE FROM local_dns_records WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}
