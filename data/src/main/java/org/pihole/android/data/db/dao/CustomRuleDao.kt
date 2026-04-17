package org.pihole.android.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.pihole.android.data.db.entity.CustomRuleEntity

@Dao
interface CustomRuleDao {
    @Query("SELECT * FROM custom_rules ORDER BY id ASC")
    fun observeAll(): Flow<List<CustomRuleEntity>>

    @Query("SELECT COUNT(*) FROM custom_rules")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM custom_rules")
    suspend fun getAll(): List<CustomRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CustomRuleEntity): Long

    @Update
    suspend fun update(entity: CustomRuleEntity): Int

    @Query("SELECT * FROM custom_rules WHERE kind = :kind AND value = :value LIMIT 1")
    suspend fun findByKindAndValue(kind: String, value: String): CustomRuleEntity?

    @Query("SELECT * FROM custom_rules WHERE id = :id")
    suspend fun getById(id: Long): CustomRuleEntity?

    @Query("DELETE FROM custom_rules WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}
