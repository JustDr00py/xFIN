package com.xfin.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
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
        Index(value = ["groupName"]),
        Index(value = ["name"]),
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
    val sortIndex: Int,
    /** Guide channel id this channel is linked to (tvg-id or name match). */
    val epgId: String? = null,
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

/** Channel row joined with per-user state and the programme airing at query time. */
data class ChannelRow(
    @Embedded val channel: ChannelEntity,
    @ColumnInfo(name = "isFavorite") val isFavorite: Boolean,
    val nowTitle: String? = null,
    val nowStart: Long? = null,
    val nowEnd: Long? = null,
)

/** Minimal channel projection used when linking channels to guide data. */
data class ChannelLinkRow(
    val id: Long,
    val tvgId: String?,
    val name: String,
    val epgId: String?,
)

data class GroupRow(
    val name: String,
    val channelCount: Int,
    val hidden: Boolean,
)
