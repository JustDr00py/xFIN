package com.xfin.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xfin.core.database.entity.VodFavoriteEntity
import com.xfin.core.database.entity.VodItemEntity
import com.xfin.core.database.entity.VodProgressEntity
import kotlinx.coroutines.flow.Flow

/** Current generation only, so a running import is invisible until it is swapped in. */
private const val CURRENT = "JOIN sources s ON s.id = v.sourceId AND s.vodGeneration = v.generation"
private const val ITEMS = "SELECT v.* FROM vod_items v $CURRENT WHERE v.sourceId = :sourceId AND v.kind = :kind"

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
