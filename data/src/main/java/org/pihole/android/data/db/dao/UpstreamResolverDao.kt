package org.pihole.android.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.pihole.android.data.db.entity.UpstreamResolverEntity

@Dao
interface UpstreamResolverDao {
    @Query("SELECT * FROM upstream_resolvers ORDER BY sortOrder ASC, id ASC")
    fun observeAllOrdered(): Flow<List<UpstreamResolverEntity>>

    @Query("SELECT * FROM upstream_resolvers ORDER BY sortOrder ASC, id ASC")
    suspend fun getAllOrdered(): List<UpstreamResolverEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: UpstreamResolverEntity): Long

    @Update
    suspend fun update(entity: UpstreamResolverEntity)

    @Query("SELECT * FROM upstream_resolvers WHERE id = :id")
    suspend fun getById(id: Long): UpstreamResolverEntity?

    @Query("DELETE FROM upstream_resolvers WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}
