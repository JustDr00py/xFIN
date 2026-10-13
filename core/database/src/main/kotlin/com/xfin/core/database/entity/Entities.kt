package com.xfin.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val url: String,
    val username: String? = null,
    /** Encrypted with an Android Keystore key, see SecretCipher. */
    val passwordEnc: String? = null,
    val epgUrl: String? = null,
    val userAgent: String? = null,
    val xtreamOutput: String = "TS",
    val channelCount: Int = 0,
    val lastRefreshedAt: Long? = null,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** Programmes are imported under a new generation, then swapped in by bumping this. */
    @ColumnInfo(defaultValue = "0") val epgGeneration: Int = 0,
    val epgUpdatedAt: Long? = null,
    @ColumnInfo(defaultValue = "0") val epgProgramCount: Int = 0,
    val epgError: String? = null,
    /** Jellyfin: the signed-in user and the server's id (the access token lives in passwordEnc). */
    val userId: String? = null,
    val serverId: String? = null,
)

@Entity(
    tableName = "channels",
    foreignKeys = [
        ForeignKey(
            entity = SourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["sourceId", "channelKey"], unique = true),
        // Playlist order, for "All channels" and per-group browsing without sorting the table.
        Index(value = ["sourceId", "sortIndex"]),
        Index(value = ["groupName", "sourceId", "sortIndex"]),
        Index(value = ["sourceId", "epgId"]),
    ],
)
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val channelKey: String,
    val name: String,
    val number: Int? = null,
    val logoUrl: String? = null,
    val groupName: String,
    val tvgId: String? = null,
    /** Absolute URL for M3U; stream id for Xtream (URL is built at play time so credentials are not duplicated). */
    val stream: String,
    val catchupMode: String? = null,
    val catchupSource: String? = null,
    val catchupDays: Int = 0,
    val userAgent: String? = null,
    val referrer: String? = null,
    val sortIndex: Long,
    /** Guide channel id this channel is linked to (tvg-id or name match). */
    val epgId: String? = null,
    /** Normalized name used to match guide channels (see channelNameKey). */
    @ColumnInfo(defaultValue = "") val nameKey: String = "",
    /** Normalized tvg-id used to match guide channels. */
    val tvgKey: String? = null,
)

/** Full-text index over channel names; kept in sync with `channels` by Room's triggers. */
@Fts4(contentEntity = ChannelEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "channels_fts")
data class ChannelFtsEntity(
    val name: String,
)

/**
 * Imports are written here first (in many short transactions, so readers are never blocked),
 * then copied into `channels` with a single INSERT ... SELECT.
 */
@Entity(tableName = "channels_staging", indices = [Index(value = ["sourceId", "channelKey"])])
data class StagedChannelEntity(
    @PrimaryKey(autoGenerate = true) val stagingId: Long = 0,
    val sourceId: Long,
    val channelKey: String,
    val name: String,
    val number: Int? = null,
    val logoUrl: String? = null,
    val groupName: String,
    val tvgId: String? = null,
    val stream: String,
    val catchupMode: String? = null,
    val catchupSource: String? = null,
    val catchupDays: Int = 0,
    val userAgent: String? = null,
    val referrer: String? = null,
    val sortIndex: Long,
    val nameKey: String,
    val tvgKey: String? = null,
)

@Entity(
    tableName = "favorites",
    primaryKeys = ["sourceId", "channelKey"],
    foreignKeys = [
        ForeignKey(
            entity = SourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class FavoriteEntity(
    val sourceId: Long,
    val channelKey: String,
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "history",
    primaryKeys = ["sourceId", "channelKey"],
    foreignKeys = [
        ForeignKey(
            entity = SourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["watchedAt"])],
)
data class HistoryEntity(
    val sourceId: Long,
    val channelKey: String,
    val watchedAt: Long,
)

@Entity(tableName = "hidden_groups")
data class HiddenGroupEntity(
    @PrimaryKey val name: String,
)

@Entity(
    tableName = "programs",
    foreignKeys = [
        ForeignKey(
            entity = SourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["sourceId", "generation", "channelId", "startAt"]),
    ],
)
data class ProgramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val generation: Int,
    val channelId: String,
    val startAt: Long,
    val endAt: Long,
    val title: String,
    val subtitle: String? = null,
    val description: String? = null,
    val category: String? = null,
    val iconUrl: String? = null,
    val episode: String? = null,
)

/** Guide channels of a source with normalized display names, used to link playlist channels. */
@Entity(
    tableName = "epg_channels",
    primaryKeys = ["sourceId", "epgId", "nameKey"],
    // Covering (includes epgId) so the name lookup in guide linking uses it; without it SQLite
    // scanned the primary key per channel (100k channels x 20k guide keys took minutes).
    indices = [Index(value = ["sourceId", "nameKey", "epgId"])],
    foreignKeys = [
        ForeignKey(
            entity = SourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class EpgChannelEntity(
    val sourceId: Long,
    val epgId: String,
    val nameKey: String,
)

/** Channel row joined with per-user state. */
data class ChannelRow(
    @Embedded val channel: ChannelEntity,
    @ColumnInfo(name = "isFavorite") val isFavorite: Boolean,
)

/** Match keys of a source's channels, used to pick which guide programmes to keep. */
data class ChannelMatchKeys(
    val tvgKey: String?,
    val nameKey: String,
)

data class GroupRow(
    val name: String,
    val channelCount: Int,
    val hidden: Boolean,
)
