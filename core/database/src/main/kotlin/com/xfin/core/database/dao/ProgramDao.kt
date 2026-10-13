package com.xfin.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.xfin.core.database.entity.EpgChannelEntity
import com.xfin.core.database.entity.ProgramEntity
import kotlinx.coroutines.flow.Flow

private const val CURRENT = "JOIN sources s ON s.id = p.sourceId AND s.epgGeneration = p.generation"

@Dao
interface ProgramDao {
    @Query(
        """SELECT p.* FROM programs p $CURRENT
        WHERE p.sourceId = :sourceId AND p.channelId = :channelId AND p.endAt > :from AND p.startAt < :to
        ORDER BY p.startAt""",
    )
    suspend fun programs(sourceId: Long, channelId: String, from: Long, to: Long): List<ProgramEntity>

    @Query(
        """SELECT p.* FROM programs p $CURRENT
        WHERE p.sourceId = :sourceId AND p.channelId = :channelId AND p.endAt > :now
        ORDER BY p.startAt LIMIT 2""",
    )
    suspend fun nowNext(sourceId: Long, channelId: String, now: Long): List<ProgramEntity>

    @Query("SELECT * FROM programs WHERE id = :id")
    suspend fun get(id: Long): ProgramEntity?

    /** Changes whenever any source swaps in a new guide; used to invalidate caches. */
    @Query("SELECT COALESCE(SUM(epgGeneration), 0) FROM sources")
    fun observeGuideVersion(): Flow<Int>

    @Query("SELECT COUNT(*) FROM channels WHERE epgId IS NOT NULL")
    fun observeLinkedChannelCount(): Flow<Int>

    // --- Import ---

    @Insert
    suspend fun insertAll(programs: List<ProgramEntity>)

    @Query("DELETE FROM programs WHERE sourceId = :sourceId AND generation = :generation")
    suspend fun deleteGeneration(sourceId: Long, generation: Int)

    /** Deletes stale generations in chunks so no single statement holds the database for long. */
    @Query(
        """DELETE FROM programs WHERE id IN (
        SELECT id FROM programs WHERE sourceId = :sourceId AND generation != :keep LIMIT :limit)""",
    )
    suspend fun deleteOtherGenerations(sourceId: Long, keep: Int, limit: Int): Int

    @Query("SELECT * FROM epg_channels WHERE sourceId = :sourceId")
    suspend fun epgChannels(sourceId: Long): List<EpgChannelEntity>

    @Query("DELETE FROM epg_channels WHERE sourceId = :sourceId")
    suspend fun deleteEpgChannels(sourceId: Long)

    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertEpgChannels(channels: List<EpgChannelEntity>)
}
