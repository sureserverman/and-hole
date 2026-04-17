package org.pihole.android.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.pihole.android.data.db.entity.AdlistSourceEntity

@Dao
interface AdlistSourceDao {
    @Query("SELECT * FROM adlist_sources ORDER BY id ASC")
    fun observeAll(): Flow<List<AdlistSourceEntity>>

    @Query("SELECT * FROM adlist_sources ORDER BY id ASC")
    suspend fun getAll(): List<AdlistSourceEntity>

    @Query("SELECT COUNT(*) FROM adlist_sources")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM adlist_sources WHERE enabled = 1")
    suspend fun getEnabled(): List<AdlistSourceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AdlistSourceEntity): Long

    @Insert
    suspend fun insertAll(entities: List<AdlistSourceEntity>)

    @Query("SELECT COUNT(*) FROM adlist_sources")
    suspend fun countSources(): Int

    @Query("SELECT url FROM adlist_sources")
    suspend fun listAllUrls(): List<String>

    @Update
    suspend fun update(entity: AdlistSourceEntity)

    @Query("SELECT * FROM adlist_sources WHERE id = :id")
    suspend fun getById(id: Long): AdlistSourceEntity?

    @Delete
    suspend fun delete(entity: AdlistSourceEntity)
}
