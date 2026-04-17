package org.pihole.android.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.pihole.android.data.db.entity.CompiledSnapshotEntity

@Dao
interface CompiledSnapshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CompiledSnapshotEntity): Long

    @Query("SELECT * FROM compiled_snapshots ORDER BY id DESC LIMIT 1")
    suspend fun getLatest(): CompiledSnapshotEntity?

    @Query("SELECT * FROM compiled_snapshots ORDER BY id DESC LIMIT 1")
    fun observeLatest(): Flow<CompiledSnapshotEntity?>
}
