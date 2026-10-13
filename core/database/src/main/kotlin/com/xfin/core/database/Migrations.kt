package com.xfin.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v3: full-text channel search, staged imports, guide match keys and playlist-order indices.
 * Statements mirror schemas/3.json. Match keys are filled by the next import, so sources are
 * marked stale to be re-imported on the next refresh.
 */
internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `channels` ADD COLUMN `nameKey` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `channels` ADD COLUMN `tvgKey` TEXT")
        db.execSQL("DROP INDEX IF EXISTS `index_channels_groupName`")
        db.execSQL("DROP INDEX IF EXISTS `index_channels_name`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_channels_sourceId_sortIndex` ON `channels` (`sourceId`, `sortIndex`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_channels_groupName_sourceId_sortIndex` ON `channels` (`groupName`, `sourceId`, `sortIndex`)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_epg_channels_sourceId_nameKey` ON `epg_channels` (`sourceId`, `nameKey`)")

        db.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS `channels_fts` USING FTS4(`name` TEXT NOT NULL, tokenize=unicode61, content=`channels`)",
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_channels_fts_BEFORE_UPDATE BEFORE UPDATE ON `channels` " +
                "BEGIN DELETE FROM `channels_fts` WHERE `docid`=OLD.`rowid`; END",
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_channels_fts_BEFORE_DELETE BEFORE DELETE ON `channels` " +
                "BEGIN DELETE FROM `channels_fts` WHERE `docid`=OLD.`rowid`; END",
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_channels_fts_AFTER_UPDATE AFTER UPDATE ON `channels` " +
                "BEGIN INSERT INTO `channels_fts`(`docid`, `name`) VALUES (NEW.`rowid`, NEW.`name`); END",
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_channels_fts_AFTER_INSERT AFTER INSERT ON `channels` " +
                "BEGIN INSERT INTO `channels_fts`(`docid`, `name`) VALUES (NEW.`rowid`, NEW.`name`); END",
        )
        db.execSQL("INSERT INTO `channels_fts`(`channels_fts`) VALUES('rebuild')")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `channels_staging` (`stagingId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`sourceId` INTEGER NOT NULL, `channelKey` TEXT NOT NULL, `name` TEXT NOT NULL, `number` INTEGER, " +
                "`logoUrl` TEXT, `groupName` TEXT NOT NULL, `tvgId` TEXT, `stream` TEXT NOT NULL, `catchupMode` TEXT, " +
                "`catchupSource` TEXT, `catchupDays` INTEGER NOT NULL, `userAgent` TEXT, `referrer` TEXT, " +
                "`sortIndex` INTEGER NOT NULL, `nameKey` TEXT NOT NULL, `tvgKey` TEXT)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_channels_staging_sourceId` ON `channels_staging` (`sourceId`)")

        db.execSQL("UPDATE `sources` SET `lastRefreshedAt` = NULL")
    }
}

/** v4: staging is matched by (sourceId, channelKey) for diff-based imports. */
internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP INDEX IF EXISTS `index_channels_staging_sourceId`")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_channels_staging_sourceId_channelKey` ON `channels_staging` (`sourceId`, `channelKey`)",
        )
    }
}

/**
 * Room's FTS content-sync triggers fire on any UPDATE of `channels`. Imports and guide linking
 * update sort order, logos and guide ids on many rows, so re-create the UPDATE triggers to fire
 * only when the indexed `name` column changes. Idempotent; run on every open.
 */
internal fun narrowFtsUpdateTriggers(db: SupportSQLiteDatabase) {
    db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_channels_fts_BEFORE_UPDATE")
    db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_channels_fts_AFTER_UPDATE")
    db.execSQL(
        "CREATE TRIGGER IF NOT EXISTS xfin_channels_fts_before_name_update BEFORE UPDATE OF `name` ON `channels` " +
            "BEGIN DELETE FROM `channels_fts` WHERE `docid`=OLD.`rowid`; END",
    )
    db.execSQL(
        "CREATE TRIGGER IF NOT EXISTS xfin_channels_fts_after_name_update AFTER UPDATE OF `name` ON `channels` " +
            "BEGIN INSERT INTO `channels_fts`(`docid`, `name`) VALUES (NEW.`rowid`, NEW.`name`); END",
    )
}

/** v5: covering name index for guide linking. */
internal val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP INDEX IF EXISTS `index_epg_channels_sourceId_nameKey`")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_epg_channels_sourceId_nameKey_epgId` ON `epg_channels` (`sourceId`, `nameKey`, `epgId`)",
        )
    }
}

/** v6: Jellyfin sources store the signed-in user and server id. */
internal val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `sources` ADD COLUMN `userId` TEXT")
        db.execSQL("ALTER TABLE `sources` ADD COLUMN `serverId` TEXT")
    }
}

/** v7: Xtream movies/series catalogue and local watch progress. */
internal val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `sources` ADD COLUMN `vodGeneration` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `sources` ADD COLUMN `vodMovieCount` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `sources` ADD COLUMN `vodSeriesCount` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `sources` ADD COLUMN `vodUpdatedAt` INTEGER")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `vod_items` (`sourceId` INTEGER NOT NULL, `generation` INTEGER NOT NULL, " +
                "`kind` TEXT NOT NULL, `providerId` INTEGER NOT NULL, `name` TEXT NOT NULL, `poster` TEXT, `category` TEXT, " +
                "`rating` REAL, `year` INTEGER, `added` INTEGER NOT NULL, `extension` TEXT, `plot` TEXT, `genre` TEXT, " +
                "`backdrop` TEXT, PRIMARY KEY(`sourceId`, `generation`, `kind`, `providerId`), FOREIGN KEY(`sourceId`) " +
                "REFERENCES `sources`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_vod_items_sourceId_generation_kind_added` ON `vod_items` (`sourceId`, `generation`, `kind`, `added`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_vod_items_sourceId_generation_kind_name` ON `vod_items` (`sourceId`, `generation`, `kind`, `name`)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `vod_progress` (`sourceId` INTEGER NOT NULL, `itemId` TEXT NOT NULL, " +
                "`positionMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `played` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "`kind` TEXT NOT NULL, `name` TEXT NOT NULL, `seriesId` TEXT, `seriesName` TEXT, `posterUrl` TEXT, `thumbUrl` TEXT, " +
                "`seasonNumber` INTEGER, `episodeNumber` INTEGER, PRIMARY KEY(`sourceId`, `itemId`), FOREIGN KEY(`sourceId`) " +
                "REFERENCES `sources`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_vod_progress_sourceId_updatedAt` ON `vod_progress` (`sourceId`, `updatedAt`)")
    }
}
