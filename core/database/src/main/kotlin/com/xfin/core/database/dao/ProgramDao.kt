package com.xfin.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.xfin.core.database.entity.EpgChannelEntity
import com.xfin.core.database.entity.ProgramEntity
import com.xfin.core.database.entity.ProgramSearchRow
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

    /** Programs of several guide channels of one source at once (the guide loads rows in batches). */
    @Query(
        """SELECT p.* FROM programs p $CURRENT
        WHERE p.sourceId = :sourceId AND p.channelId IN (:channelIds) AND p.endAt > :from AND p.startAt < :to
        ORDER BY p.channelId, p.startAt""",
    )
    suspend fun programsFor(sourceId: Long, channelIds: List<String>, from: Long, to: Long): List<ProgramEntity>

    @Query(
        """SELECT p.* FROM programs p $CURRENT
        WHERE p.sourceId = :sourceId AND p.channelId = :channelId AND p.endAt > :now
        ORDER BY p.startAt LIMIT 2""",
    )
    suspend fun nowNext(sourceId: Long, channelId: String, now: Long): List<ProgramEntity>

    // --- Search ---

    @Query("DELETE FROM programs_fts")
    suspend fun clearSearchIndex()

    /** Indexes the titles of every source's current guide. */
    @Query("INSERT INTO programs_fts(rowid, title) SELECT p.id, p.title FROM programs p $CURRENT")
    suspend fun fillSearchIndex()

    @Query("SELECT EXISTS(SELECT 1 FROM programs_fts LIMIT 1)")
    suspend fun hasSearchIndex(): Boolean

    /**
     * Programs whose title matches [match] (an FTS4 expression) and that air between [from] and
     * [to], each with a channel showing it; channels in hidden groups are left out.
     */
    @Query(
        """SELECT c.id AS chId, p.* FROM programs_fts f
        JOIN programs p ON p.id = f.rowid $CURRENT
        JOIN channels c ON c.sourceId = p.sourceId AND c.epgId = p.channelId
        WHERE programs_fts MATCH :match AND p.endAt > :from AND p.startAt < :to
            AND c.groupName NOT IN (SELECT name FROM hidden_groups)
        ORDER BY p.startAt, c.sortIndex LIMIT :limit""",
    )
    suspend fun search(match: String, from: Long, to: Long, limit: Int): List<ProgramSearchRow>

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
