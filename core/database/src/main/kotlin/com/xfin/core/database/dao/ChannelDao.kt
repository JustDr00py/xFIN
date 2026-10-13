package com.xfin.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xfin.core.database.entity.ChannelEntity
import com.xfin.core.database.entity.ChannelLinkRow
import com.xfin.core.database.entity.ChannelRow
import com.xfin.core.database.entity.FavoriteEntity
import com.xfin.core.database.entity.GroupRow
import com.xfin.core.database.entity.HiddenGroupEntity
import com.xfin.core.database.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

/** Programme airing at :now on the channel's linked guide (current generation only). */
private const val NOW_PROGRAM = """
    LEFT JOIN programs p ON p.id = (
        SELECT p2.id FROM programs p2 JOIN sources s ON s.id = p2.sourceId
        WHERE p2.sourceId = c.sourceId AND p2.generation = s.epgGeneration
        AND p2.channelId = c.epgId AND p2.startAt <= :now AND p2.endAt > :now
        LIMIT 1
    )
"""
private const val NOW_COLUMNS = "p.title AS nowTitle, p.startAt AS nowStart, p.endAt AS nowEnd"

private const val CHANNEL_WITH_FAV = """
    SELECT c.*, (f.channelKey IS NOT NULL) AS isFavorite, $NOW_COLUMNS FROM channels c
    LEFT JOIN favorites f ON f.sourceId = c.sourceId AND f.channelKey = c.channelKey
    $NOW_PROGRAM
"""
private const val VISIBLE = "c.groupName NOT IN (SELECT name FROM hidden_groups)"
private const val MATCHES = "(:query = '' OR c.name LIKE '%' || :query || '%')"
private const val PLAYLIST_ORDER = "ORDER BY c.sourceId, c.sortIndex"

@Dao
interface ChannelDao {
    // --- Browsing (paged) ---

    @Query("$CHANNEL_WITH_FAV WHERE $VISIBLE AND $MATCHES $PLAYLIST_ORDER")
    fun pageAll(query: String, now: Long): PagingSource<Int, ChannelRow>

    @Query("$CHANNEL_WITH_FAV WHERE c.groupName = :group AND $MATCHES $PLAYLIST_ORDER")
    fun pageGroup(group: String, query: String, now: Long): PagingSource<Int, ChannelRow>

    @Query(
        """SELECT c.*, 1 AS isFavorite, $NOW_COLUMNS FROM channels c
        JOIN favorites f ON f.sourceId = c.sourceId AND f.channelKey = c.channelKey
        $NOW_PROGRAM
        WHERE $MATCHES ORDER BY f.addedAt""",
    )
    fun pageFavorites(query: String, now: Long): PagingSource<Int, ChannelRow>

    @Query(
        """SELECT c.*, (f.channelKey IS NOT NULL) AS isFavorite, $NOW_COLUMNS FROM channels c
        JOIN history h ON h.sourceId = c.sourceId AND h.channelKey = c.channelKey
        LEFT JOIN favorites f ON f.sourceId = c.sourceId AND f.channelKey = c.channelKey
        $NOW_PROGRAM
        WHERE $MATCHES ORDER BY h.watchedAt DESC LIMIT 100""",
    )
    fun pageRecent(query: String, now: Long): PagingSource<Int, ChannelRow>

    // --- Zapping lists (full, same order as browsing) ---

    @Query("$CHANNEL_WITH_FAV WHERE $VISIBLE $PLAYLIST_ORDER")
    suspend fun listAll(now: Long): List<ChannelRow>

    @Query("$CHANNEL_WITH_FAV WHERE c.groupName = :group $PLAYLIST_ORDER")
    suspend fun listGroup(group: String, now: Long): List<ChannelRow>

    @Query(
        """SELECT c.*, 1 AS isFavorite, $NOW_COLUMNS FROM channels c
        JOIN favorites f ON f.sourceId = c.sourceId AND f.channelKey = c.channelKey
        $NOW_PROGRAM
        ORDER BY f.addedAt""",
    )
    suspend fun listFavorites(now: Long): List<ChannelRow>

    @Query(
        """SELECT c.*, (f.channelKey IS NOT NULL) AS isFavorite, $NOW_COLUMNS FROM channels c
        JOIN history h ON h.sourceId = c.sourceId AND h.channelKey = c.channelKey
        LEFT JOIN favorites f ON f.sourceId = c.sourceId AND f.channelKey = c.channelKey
        $NOW_PROGRAM
        ORDER BY h.watchedAt DESC LIMIT 100""",
    )
    suspend fun listRecent(now: Long): List<ChannelRow>

    @Query("$CHANNEL_WITH_FAV WHERE c.id = :id")
    suspend fun get(id: Long, now: Long): ChannelRow?

    @Query("$CHANNEL_WITH_FAV WHERE c.id = :id")
    fun observe(id: Long, now: Long): Flow<ChannelRow?>

    // --- Groups ---

    @Query(
        """SELECT groupName AS name, COUNT(*) AS channelCount,
        (groupName IN (SELECT name FROM hidden_groups)) AS hidden
        FROM channels GROUP BY groupName ORDER BY MIN(sourceId), MIN(sortIndex)""",
    )
    fun observeGroups(): Flow<List<GroupRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun hideGroup(group: HiddenGroupEntity)

    @Query("DELETE FROM hidden_groups WHERE name = :name")
    suspend fun unhideGroup(name: String)

    // --- Favorites & history ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE sourceId = :sourceId AND channelKey = :channelKey")
    suspend fun removeFavorite(sourceId: Long, channelKey: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordWatched(history: HistoryEntity)

    @Query("SELECT COUNT(*) FROM favorites")
    fun observeFavoriteCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM history")
    fun observeHistoryCount(): Flow<Int>

    // --- Import ---

    @Query("SELECT id, tvgId, name, epgId FROM channels WHERE sourceId = :sourceId")
    suspend fun linkRows(sourceId: Long): List<ChannelLinkRow>

    @Query("UPDATE channels SET epgId = :epgId WHERE id = :id")
    suspend fun setEpgId(id: Long, epgId: String?)

    @Query("DELETE FROM channels WHERE sourceId = :sourceId")
    suspend fun deleteForSource(sourceId: Long)

    @Insert
    suspend fun insertAll(channels: List<ChannelEntity>)
}
