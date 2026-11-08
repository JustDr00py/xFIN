package com.xfin.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.xfin.core.database.entity.SourceEntity
import com.xfin.core.database.entity.VodCategoryEntity
import com.xfin.core.database.entity.VodFavoriteEntity
import com.xfin.core.database.entity.VodItemEntity
import com.xfin.core.database.entity.VodProgressEntity
import kotlinx.coroutines.flow.Flow

/** Current generation only, so a running import is invisible until it is swapped in. */
private const val CURRENT = "JOIN sources s ON s.id = v.sourceId AND s.vodGeneration = v.generation"
private const val ITEMS = "SELECT v.* FROM vod_items v $CURRENT WHERE v.sourceId = :sourceId AND v.kind = :kind"

/** ORDER BY clauses for category queries, matching the fixed sorts above. */
const val VOD_ORDER_NAME = "v.name COLLATE NOCASE"
const val VOD_ORDER_ADDED = "v.added DESC, v.name COLLATE NOCASE"
const val VOD_ORDER_YEAR = "v.year IS NULL, v.year DESC, v.name COLLATE NOCASE"
const val VOD_ORDER_RATING = "v.rating IS NULL, v.rating DESC, v.name COLLATE NOCASE"
private val ORDERS = setOf(VOD_ORDER_NAME, VOD_ORDER_ADDED, VOD_ORDER_YEAR, VOD_ORDER_RATING)

@Dao
interface VodDao {
    @Query("$ITEMS ORDER BY v.name COLLATE NOCASE")
    fun pageByName(sourceId: Long, kind: String): PagingSource<Int, VodItemEntity>

    @Query("$ITEMS ORDER BY v.added DESC, v.name COLLATE NOCASE")
    fun pageByAdded(sourceId: Long, kind: String): PagingSource<Int, VodItemEntity>

    @Query("$ITEMS ORDER BY v.year IS NULL, v.year DESC, v.name COLLATE NOCASE")
    fun pageByYear(sourceId: Long, kind: String): PagingSource<Int, VodItemEntity>

    @Query("$ITEMS ORDER BY v.rating IS NULL, v.rating DESC, v.name COLLATE NOCASE")
    fun pageByRating(sourceId: Long, kind: String): PagingSource<Int, VodItemEntity>

    @Query("$ITEMS ORDER BY v.added DESC LIMIT :limit")
    suspend fun latest(sourceId: Long, kind: String, limit: Int): List<VodItemEntity>

    @Query("$ITEMS AND v.providerId = :providerId LIMIT 1")
    suspend fun get(sourceId: Long, kind: String, providerId: Long): VodItemEntity?

    /** Simple substring search; catalogues are tens of thousands of rows, which LIKE handles fine. */
    @Query(
        """SELECT v.* FROM vod_items v $CURRENT WHERE v.sourceId = :sourceId AND v.name LIKE '%' || :term || '%'
        ORDER BY (v.name LIKE :term || '%') DESC, v.added DESC LIMIT :limit""",
    )
    suspend fun search(sourceId: Long, term: String, limit: Int): List<VodItemEntity>

    // --- Categories ---

    /**
     * Categories that have titles, in the provider's order (unknown ones by name, then titles
     * without a category, whose [VodCategoryCount.name] is null).
     */
    @Query(
        """SELECT v.category AS name, COUNT(*) AS count FROM vod_items v $CURRENT
        LEFT JOIN vod_categories c ON c.sourceId = v.sourceId AND c.kind = v.kind AND c.name = v.category
        WHERE v.sourceId = :sourceId AND v.kind = :kind
        GROUP BY v.category
        ORDER BY v.category IS NULL, c.position IS NULL, c.position, v.category COLLATE NOCASE""",
    )
    suspend fun categories(sourceId: Long, kind: String): List<VodCategoryCount>

    @Query("DELETE FROM vod_categories WHERE sourceId = :sourceId")
    suspend fun deleteCategories(sourceId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategories(categories: List<VodCategoryEntity>)

    /** Current-generation titles; built by the repository so a category filter can use its index. */
    @RawQuery(observedEntities = [VodItemEntity::class, SourceEntity::class])
    fun pageRaw(query: SupportSQLiteQuery): PagingSource<Int, VodItemEntity>

    @RawQuery
    suspend fun listRaw(query: SupportSQLiteQuery): List<VodItemEntity>

    /** One category's titles, paged; [category] null means titles without a category. */
    fun pageCategory(sourceId: Long, kind: String, category: String?, orderBy: String): PagingSource<Int, VodItemEntity> =
        pageRaw(categoryQuery(sourceId, kind, category, orderBy, limit = null))

    /** The first [limit] titles of a category, for its row. */
    suspend fun categoryItems(sourceId: Long, kind: String, category: String?, orderBy: String, limit: Int): List<VodItemEntity> =
        listRaw(categoryQuery(sourceId, kind, category, orderBy, limit))

    private fun categoryQuery(sourceId: Long, kind: String, category: String?, orderBy: String, limit: Int?): SupportSQLiteQuery {
        require(orderBy in ORDERS) { "Unknown order: $orderBy" }
        val filter = if (category == null) "v.category IS NULL" else "v.category = ?"
        val args = listOfNotNull<Any>(sourceId, kind, category)
        val sql = "SELECT v.* FROM vod_items v $CURRENT WHERE v.sourceId = ? AND v.kind = ? AND $filter ORDER BY $orderBy" +
            (limit?.let { " LIMIT $it" } ?: "")
        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }

    // --- Import ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<VodItemEntity>)

    @Query("DELETE FROM vod_items WHERE sourceId = :sourceId AND generation = :generation")
    suspend fun deleteGeneration(sourceId: Long, generation: Int)

    @Query(
        """DELETE FROM vod_items WHERE rowid IN (
        SELECT rowid FROM vod_items WHERE sourceId = :sourceId AND generation != :keep LIMIT :limit)""",
    )
    suspend fun deleteOtherGenerations(sourceId: Long, keep: Int, limit: Int): Int

    @Query(
        """UPDATE sources SET vodGeneration = :generation, vodMovieCount = :movies, vodSeriesCount = :series,
        vodUpdatedAt = :at WHERE id = :sourceId""",
    )
    suspend fun swap(sourceId: Long, generation: Int, movies: Int, series: Int, at: Long)

    // --- Favorites ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: VodFavoriteEntity)

    @Query("DELETE FROM vod_favorites WHERE sourceId = :sourceId AND kind = :kind AND providerId = :providerId")
    suspend fun removeFavorite(sourceId: Long, kind: String, providerId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM vod_favorites WHERE sourceId = :sourceId AND kind = :kind AND providerId = :providerId)")
    suspend fun isFavorite(sourceId: Long, kind: String, providerId: Long): Boolean

    /** Favorites still in the catalogue, most recently added first; [kind] null means movies and shows. */
    @Query(
        "SELECT v.* FROM vod_items v $CURRENT JOIN vod_favorites f ON f.sourceId = v.sourceId AND f.kind = v.kind " +
            "AND f.providerId = v.providerId WHERE v.sourceId = :sourceId AND (:kind IS NULL OR v.kind = :kind) " +
            "ORDER BY f.addedAt DESC LIMIT :limit",
    )
    suspend fun favorites(sourceId: Long, kind: String?, limit: Int): List<VodItemEntity>

    @Query(
        "SELECT v.* FROM vod_items v $CURRENT JOIN vod_favorites f ON f.sourceId = v.sourceId AND f.kind = v.kind " +
            "AND f.providerId = v.providerId WHERE v.sourceId = :sourceId AND v.kind = :kind ORDER BY f.addedAt DESC",
    )
    fun pageFavorites(sourceId: Long, kind: String): PagingSource<Int, VodItemEntity>

    // --- Progress ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: VodProgressEntity)

    @Query("SELECT * FROM vod_progress WHERE sourceId = :sourceId AND itemId = :itemId")
    suspend fun progress(sourceId: Long, itemId: String): VodProgressEntity?

    @Query("SELECT * FROM vod_progress WHERE sourceId = :sourceId AND itemId IN (:itemIds)")
    suspend fun progressFor(sourceId: Long, itemIds: List<String>): List<VodProgressEntity>

    @Query("SELECT * FROM vod_progress WHERE sourceId = :sourceId AND played = 0 AND positionMs > 0 ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun inProgress(sourceId: Long, limit: Int): List<VodProgressEntity>

    @Query("SELECT COALESCE(MAX(updatedAt), 0) FROM vod_progress")
    fun observeProgressVersion(): Flow<Long>
}

data class VodCategoryCount(val name: String?, val count: Int)
