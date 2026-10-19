package com.xfin.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xfin.core.database.entity.FavoriteEntity
import com.xfin.core.database.entity.HiddenGroupEntity
import com.xfin.core.database.entity.HistoryEntity
import com.xfin.core.database.entity.VodFavoriteEntity
import com.xfin.core.database.entity.VodProgressEntity

/** The user's own state (everything that can't be re-downloaded from a source), for backups. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM favorites")
    suspend fun favorites(): List<FavoriteEntity>

    @Query("SELECT * FROM history")
    suspend fun history(): List<HistoryEntity>

    @Query("SELECT * FROM hidden_groups")
    suspend fun hiddenGroups(): List<HiddenGroupEntity>

    @Query("SELECT * FROM vod_progress")
    suspend fun vodProgress(): List<VodProgressEntity>

    @Query("SELECT * FROM vod_favorites")
    suspend fun vodFavorites(): List<VodFavoriteEntity>

    // Restores merge: what's already on the device wins.

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFavorites(rows: List<FavoriteEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHistory(rows: List<HistoryEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHiddenGroups(rows: List<HiddenGroupEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertVodProgress(rows: List<VodProgressEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertVodFavorites(rows: List<VodFavoriteEntity>)
}
