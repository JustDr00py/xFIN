package com.xfin.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xfin.core.database.entity.ChannelMatchKeys
import com.xfin.core.database.entity.ChannelRow
import com.xfin.core.database.entity.FavoriteEntity
import com.xfin.core.database.entity.GroupRow
import com.xfin.core.database.entity.HiddenGroupEntity
import com.xfin.core.database.entity.HistoryEntity
import com.xfin.core.database.entity.StagedChannelEntity
import kotlinx.coroutines.flow.Flow

// Channel queries deliberately do not join guide tables: "on now" is loaded lazily for visible
// rows, so guide imports never invalidate (and re-run) the channel lists.

private const val CHANNEL_WITH_FAV = """
    SELECT c.*, (f.channelKey IS NOT NULL) AS isFavorite FROM channels c
    LEFT JOIN favorites f ON f.sourceId = c.sourceId AND f.channelKey = c.channelKey
"""
private const val VISIBLE = "c.groupName NOT IN (SELECT name FROM hidden_groups)"
private const val PLAYLIST_ORDER = "ORDER BY c.sourceId, c.sortIndex"
private const val GROUP_ORDER = "ORDER BY c.groupName, c.sourceId, c.sortIndex"
private const val FAVORITES = """
    FROM channels c JOIN favorites f ON f.sourceId = c.sourceId AND f.channelKey = c.channelKey
"""
private const val RECENT = """
    FROM channels c JOIN history h ON h.sourceId = c.sourceId AND h.channelKey = c.channelKey
    LEFT JOIN favorites f ON f.sourceId = c.sourceId AND f.channelKey = c.channelKey
"""

@Dao
interface ChannelDao {
    // --- Browsing (paged) ---

    @Query("$CHANNEL_WITH_FAV WHERE $VISIBLE $PLAYLIST_ORDER")
    fun pageAll(): PagingSource<Int, ChannelRow>

    @Query("$CHANNEL_WITH_FAV WHERE c.groupName = :group $GROUP_ORDER")
    fun pageGroup(group: String): PagingSource<Int, ChannelRow>

    @Query("SELECT c.*, 1 AS isFavorite $FAVORITES ORDER BY f.addedAt")
    fun pageFavorites(): PagingSource<Int, ChannelRow>

    @Query("SELECT c.*, (f.channelKey IS NOT NULL) AS isFavorite $RECENT ORDER BY h.watchedAt DESC LIMIT 100")
    fun pageRecent(): PagingSource<Int, ChannelRow>

    /** [match] is an FTS4 MATCH expression, e.g. `espn* sp*`. */
    @Query(
        """$CHANNEL_WITH_FAV WHERE c.id IN (SELECT rowid FROM channels_fts WHERE channels_fts MATCH :match)
        AND $VISIBLE $PLAYLIST_ORDER""",
    )
    fun pageSearch(match: String): PagingSource<Int, ChannelRow>

    // --- Zapping lineups: ids only, same order as browsing ---

    @Query("SELECT c.id FROM channels c WHERE $VISIBLE $PLAYLIST_ORDER")
    suspend fun idsAll(): List<Long>

    @Query("SELECT c.id FROM channels c WHERE c.groupName = :group $GROUP_ORDER")
    suspend fun idsGroup(group: String): List<Long>

    @Query("SELECT c.id $FAVORITES ORDER BY f.addedAt")
    suspend fun idsFavorites(): List<Long>

    @Query("SELECT c.id $RECENT ORDER BY h.watchedAt DESC LIMIT 100")
    suspend fun idsRecent(): List<Long>

    @Query("SELECT id FROM channels WHERE number = :number")
    suspend fun idsWithNumber(number: Int): List<Long>

    @Query("$CHANNEL_WITH_FAV WHERE c.id = :id")
    suspend fun get(id: Long): ChannelRow?

    @Query("$CHANNEL_WITH_FAV WHERE c.id IN (:ids)")
    suspend fun getAll(ids: List<Long>): List<ChannelRow>

    @Query("$CHANNEL_WITH_FAV WHERE c.id = :id")
    fun observe(id: Long): Flow<ChannelRow?>

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

    // --- Import (staging, then one atomic swap) ---

    @Query("DELETE FROM channels_staging WHERE sourceId = :sourceId")
    suspend fun clearStaging(sourceId: Long)

    @Insert
    suspend fun stage(channels: List<StagedChannelEntity>)

    @Query("DELETE FROM channels WHERE sourceId = :sourceId")
    suspend fun deleteForSource(sourceId: Long)

    /** Deletes a slice of a source's channels; loop until 0 to remove a source without long locks. */
    @Query("DELETE FROM channels WHERE id IN (SELECT id FROM channels WHERE sourceId = :sourceId LIMIT :limit)")
    suspend fun deleteSlice(sourceId: Long, limit: Int): Int

    // Diff-based swap: unchanged channels are not touched, so a routine refresh writes almost
    // nothing and channel ids (and anything holding them, like an open player) stay valid.

    @Query(
        """DELETE FROM channels WHERE sourceId = :sourceId AND NOT EXISTS (
        SELECT 1 FROM channels_staging s WHERE s.sourceId = channels.sourceId AND s.channelKey = channels.channelKey)""",
    )
    suspend fun deleteMissing(sourceId: Long): Int

    @Query(
        """UPDATE channels SET
        name = ($STAGED name $STAGED_ROW), number = ($STAGED number $STAGED_ROW),
        logoUrl = ($STAGED logoUrl $STAGED_ROW), groupName = ($STAGED groupName $STAGED_ROW),
        tvgId = ($STAGED tvgId $STAGED_ROW), stream = ($STAGED stream $STAGED_ROW),
        catchupMode = ($STAGED catchupMode $STAGED_ROW), catchupSource = ($STAGED catchupSource $STAGED_ROW),
        catchupDays = ($STAGED catchupDays $STAGED_ROW), userAgent = ($STAGED userAgent $STAGED_ROW),
        referrer = ($STAGED referrer $STAGED_ROW), sortIndex = ($STAGED sortIndex $STAGED_ROW),
        nameKey = ($STAGED nameKey $STAGED_ROW), tvgKey = ($STAGED tvgKey $STAGED_ROW)
        WHERE sourceId = :sourceId AND EXISTS (
            SELECT 1 FROM channels_staging s WHERE s.sourceId = channels.sourceId AND s.channelKey = channels.channelKey
            AND (s.name IS NOT channels.name OR s.number IS NOT channels.number OR s.logoUrl IS NOT channels.logoUrl
            OR s.groupName IS NOT channels.groupName OR s.tvgId IS NOT channels.tvgId OR s.stream IS NOT channels.stream
            OR s.catchupMode IS NOT channels.catchupMode OR s.catchupSource IS NOT channels.catchupSource
            OR s.catchupDays IS NOT channels.catchupDays OR s.userAgent IS NOT channels.userAgent
            OR s.referrer IS NOT channels.referrer OR s.sortIndex IS NOT channels.sortIndex
            OR s.nameKey IS NOT channels.nameKey OR s.tvgKey IS NOT channels.tvgKey))""",
    )
    suspend fun updateChanged(sourceId: Long): Int

    /** Duplicate keys (e.g. a provider listing a stream twice) keep the first occurrence. */
    @Query(
        """INSERT OR IGNORE INTO channels (sourceId, channelKey, name, number, logoUrl, groupName, tvgId, stream,
        catchupMode, catchupSource, catchupDays, userAgent, referrer, sortIndex, nameKey, tvgKey)
        SELECT sourceId, channelKey, name, number, logoUrl, groupName, tvgId, stream,
        catchupMode, catchupSource, catchupDays, userAgent, referrer, sortIndex, nameKey, tvgKey
        FROM channels_staging s WHERE s.sourceId = :sourceId AND NOT EXISTS (
            SELECT 1 FROM channels c WHERE c.sourceId = s.sourceId AND c.channelKey = s.channelKey)
        ORDER BY sortIndex""",
    )
    suspend fun insertNew(sourceId: Long): Long

    @Query("SELECT COUNT(*) FROM channels WHERE sourceId = :sourceId")
    suspend fun count(sourceId: Long): Int

    // --- Guide linking ---

    @Query("SELECT tvgKey, nameKey FROM channels WHERE sourceId = :sourceId")
    suspend fun matchKeys(sourceId: Long): List<ChannelMatchKeys>

    /** Links channels to guide channels in one statement: exact tvg-id first, then normalized name. */
    @Query(
        """UPDATE channels SET epgId = $LINK_EXPR
        WHERE sourceId = :sourceId AND epgId IS NOT $LINK_EXPR""",
    )
    suspend fun linkGuide(sourceId: Long): Int
}

private const val STAGED = "SELECT s."
private const val STAGED_ROW =
    "FROM channels_staging s WHERE s.sourceId = channels.sourceId AND s.channelKey = channels.channelKey ORDER BY s.sortIndex LIMIT 1"

private const val LINK_EXPR = """COALESCE(
    (SELECT e.epgId FROM epg_channels e WHERE e.sourceId = channels.sourceId AND e.epgId = channels.tvgKey LIMIT 1),
    (SELECT e.epgId FROM epg_channels e WHERE e.sourceId = channels.sourceId AND channels.nameKey != ''
        AND e.nameKey = channels.nameKey LIMIT 1)
)"""
