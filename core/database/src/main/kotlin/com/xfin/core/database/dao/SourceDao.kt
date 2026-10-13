package com.xfin.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.xfin.core.database.entity.SourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceDao {
    @Query("SELECT * FROM sources ORDER BY createdAt")
    fun observeAll(): Flow<List<SourceEntity>>

    @Query("SELECT * FROM sources ORDER BY createdAt")
    suspend fun getAll(): List<SourceEntity>

    @Query("SELECT * FROM sources WHERE id = :id")
    suspend fun get(id: Long): SourceEntity?

    @Query("SELECT COUNT(*) FROM sources")
    fun observeCount(): Flow<Int>

    @Insert
    suspend fun insert(source: SourceEntity): Long

    @Update
    suspend fun update(source: SourceEntity)

    @Query("UPDATE sources SET channelCount = :count, lastRefreshedAt = :at, lastError = NULL WHERE id = :id")
    suspend fun markRefreshed(id: Long, count: Int, at: Long)

    @Query("UPDATE sources SET lastError = :error WHERE id = :id")
    suspend fun markFailed(id: Long, error: String)

    @Query(
        """UPDATE sources SET epgGeneration = :generation, epgUpdatedAt = :at,
        epgProgramCount = :count, epgError = NULL WHERE id = :id""",
    )
    suspend fun markEpgSwapped(id: Long, generation: Int, count: Int, at: Long)

    @Query("UPDATE sources SET epgError = :error WHERE id = :id")
    suspend fun markEpgFailed(id: Long, error: String)

    @Query("UPDATE sources SET epgUrl = :url WHERE id = :id")
    suspend fun setEpgUrl(id: Long, url: String?)

    @Query("DELETE FROM sources WHERE id = :id")
    suspend fun delete(id: Long)
}
