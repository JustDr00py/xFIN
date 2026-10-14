package com.xfin.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.xfin.core.database.dao.BackupDao
import com.xfin.core.database.dao.ChannelDao
import com.xfin.core.database.dao.ProgramDao
import com.xfin.core.database.dao.VodDao
import com.xfin.core.database.entity.VodItemEntity
import com.xfin.core.database.entity.VodProgressEntity
import com.xfin.core.database.dao.SourceDao
import com.xfin.core.database.entity.ChannelEntity
import com.xfin.core.database.entity.ChannelFtsEntity
import com.xfin.core.database.entity.StagedChannelEntity
import com.xfin.core.database.entity.EpgChannelEntity
import com.xfin.core.database.entity.FavoriteEntity
import com.xfin.core.database.entity.HiddenGroupEntity
import com.xfin.core.database.entity.HistoryEntity
import com.xfin.core.database.entity.ProgramEntity
import com.xfin.core.database.entity.SourceEntity

@Database(
    entities = [
        SourceEntity::class,
        ChannelEntity::class,
        FavoriteEntity::class,
        HistoryEntity::class,
        HiddenGroupEntity::class,
        ProgramEntity::class,
        EpgChannelEntity::class,
        ChannelFtsEntity::class,
        StagedChannelEntity::class,
        VodItemEntity::class,
        VodProgressEntity::class,
    ],
    version = 7,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class XfinDatabase : RoomDatabase() {
    abstract fun sourceDao(): SourceDao
    abstract fun channelDao(): ChannelDao
    abstract fun programDao(): ProgramDao
    abstract fun vodDao(): VodDao
    abstract fun backupDao(): BackupDao
}
