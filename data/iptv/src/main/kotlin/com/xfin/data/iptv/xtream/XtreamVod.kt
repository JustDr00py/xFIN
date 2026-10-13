package com.xfin.data.iptv.xtream

import com.xfin.data.iptv.xtream.XtreamParsing.int
import com.xfin.data.iptv.xtream.XtreamParsing.long
import com.xfin.data.iptv.xtream.XtreamParsing.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** A movie from get_vod_streams. */
data class XtreamMovie(
    val streamId: Long,
    val name: String,
    val icon: String?,
    val categoryId: String?,
    val rating: Float?,
    val added: Long,
    val extension: String?,
    val year: Int?,
)

/** A show from get_series. */
data class XtreamSeries(
    val seriesId: Long,
    val name: String,
    val cover: String?,
    val categoryId: String?,
    val rating: Float?,
    val added: Long,
    val plot: String?,
    val genre: String?,
    val backdrop: String?,
    val year: Int?,
)

/** Extra movie details from get_vod_info. */
data class XtreamMovieInfo(
    val plot: String?,
    val genre: String?,
    val backdrop: String?,
    val durationMs: Long?,
    val year: Int?,
    val rating: Float?,
    val extension: String?,
)

data class XtreamEpisode(
    val id: Long,
    val season: Int,
    val number: Int?,
    val title: String,
    val extension: String,
    val plot: String?,
    val durationMs: Long?,
    val image: String?,
)

data class XtreamSeason(val number: Int, val name: String, val cover: String?)

/** get_series_info: show details with seasons and episodes. */
data class XtreamSeriesInfo(
    val plot: String?,
    val genre: String?,
    val backdrop: String?,
    val cover: String?,
    val rating: Float?,
    val year: Int?,
    val seasons: List<XtreamSeason>,
    val episodes: List<XtreamEpisode>,
)

internal object XtreamVodParsing {

    fun movie(element: JsonElement): XtreamMovie? {
        val o = element as? JsonObject ?: return null
        val id = o.long("stream_id") ?: return null
        val name = o.string("name")?.trim().orEmpty().ifEmpty { return null }
        return XtreamMovie(
            streamId = id,
            name = name,
            icon = o.string("stream_icon")?.takeIf { it.isNotBlank() },
            categoryId = o.string("category_id"),
            rating = o.rating(),
            added = (o.long("added") ?: 0) * 1000,
            extension = o.string("container_extension")?.takeIf { it.isNotBlank() },
            year = yearOf(o.string("year") ?: o.string("releaseDate") ?: o.string("release_date")) ?: yearInName(name),
        )
    }

    fun series(element: JsonElement): XtreamSeries? {
        val o = element as? JsonObject ?: return null
        val id = o.long("series_id") ?: return null
        val name = o.string("name")?.trim().orEmpty().ifEmpty { return null }
        return XtreamSeries(
            seriesId = id,
            name = name,
            cover = o.string("cover")?.takeIf { it.isNotBlank() },
            categoryId = o.string("category_id"),
            rating = o.rating(),
            added = (o.long("last_modified") ?: 0) * 1000,
            plot = o.string("plot")?.takeIf { it.isNotBlank() },
            genre = o.string("genre")?.takeIf { it.isNotBlank() },
            backdrop = o.firstBackdrop(),
            year = yearOf(o.string("releaseDate") ?: o.string("release_date") ?: o.string("year")) ?: yearInName(name),
        )
    }

    fun movieInfo(root: JsonElement): XtreamMovieInfo? {
        val o = root as? JsonObject ?: return null
        val info = o["info"] as? JsonObject
        val data = o["movie_data"] as? JsonObject
        return XtreamMovieInfo(
            plot = info?.string("plot")?.takeIf { it.isNotBlank() } ?: info?.string("description")?.takeIf { it.isNotBlank() },
            genre = info?.string("genre")?.takeIf { it.isNotBlank() },
            backdrop = info?.firstBackdrop(),
            durationMs = info?.long("duration_secs")?.times(1000),
            year = yearOf(info?.string("releasedate") ?: info?.string("release_date")),
            rating = info?.rating(),
            extension = data?.string("container_extension")?.takeIf { it.isNotBlank() },
        )
    }

    fun seriesInfo(root: JsonElement): XtreamSeriesInfo? {
        val o = root as? JsonObject ?: return null
        val info = o["info"] as? JsonObject
        val seasons = (o["seasons"] as? JsonArray).orEmpty().mapNotNull { s ->
            val so = s as? JsonObject ?: return@mapNotNull null
            val number = so.int("season_number") ?: return@mapNotNull null
            XtreamSeason(number, so.string("name")?.takeIf { it.isNotBlank() } ?: "Season $number", so.string("cover")?.takeIf { it.isNotBlank() })
        }
        // "episodes" is an object keyed by season number (sometimes an array of arrays).
        val episodeGroups: List<JsonElement> = when (val e = o["episodes"]) {
            is JsonObject -> e.values.toList()
            is JsonArray -> e.toList()
            else -> emptyList()
        }
        val episodes = episodeGroups.flatMap { group -> (group as? JsonArray).orEmpty() }.mapNotNull { ep ->
            val eo = ep as? JsonObject ?: return@mapNotNull null
            val id = eo.long("id") ?: return@mapNotNull null
            val epInfo = eo["info"] as? JsonObject
            XtreamEpisode(
                id = id,
                season = eo.int("season") ?: 1,
                number = eo.int("episode_num"),
                title = eo.string("title")?.trim().orEmpty().ifEmpty { "Episode ${eo.int("episode_num") ?: ""}".trim() },
                extension = eo.string("container_extension")?.takeIf { it.isNotBlank() } ?: "mp4",
                plot = epInfo?.string("plot")?.takeIf { it.isNotBlank() },
                durationMs = epInfo?.long("duration_secs")?.times(1000),
                image = epInfo?.string("movie_image")?.takeIf { it.isNotBlank() },
            )
        }.sortedWith(compareBy({ it.season }, { it.number ?: Int.MAX_VALUE }))
        val seasonList = seasons.ifEmpty {
            episodes.map { it.season }.distinct().map { XtreamSeason(it, "Season $it", null) }
        }.filter { s -> episodes.any { it.season == s.number } }.sortedBy { it.number }
        return XtreamSeriesInfo(
            plot = info?.string("plot")?.takeIf { it.isNotBlank() },
            genre = info?.string("genre")?.takeIf { it.isNotBlank() },
            backdrop = info?.firstBackdrop(),
            cover = info?.string("cover")?.takeIf { it.isNotBlank() },
            rating = info?.rating(),
            year = yearOf(info?.string("releaseDate") ?: info?.string("release_date")),
            seasons = seasonList,
            episodes = episodes,
        )
    }

    private fun JsonObject.rating(): Float? =
        (string("rating_5based")?.toFloatOrNull()?.takeIf { it > 0 }?.times(2) ?: string("rating")?.toFloatOrNull())
            ?.takeIf { it > 0 }

    private fun JsonObject.firstBackdrop(): String? = when (val b = get("backdrop_path")) {
        is JsonArray -> (b.firstOrNull() as? JsonPrimitive)?.contentOrNull
        is JsonPrimitive -> b.contentOrNull
        else -> null
    }?.takeIf { it.isNotBlank() }

    private fun yearOf(value: String?): Int? = value?.let { Regex("""(19|20)\d{2}""").find(it)?.value?.toInt() }

    private fun yearInName(name: String): Int? = Regex("""\((19|20)\d{2}\)""").find(name)?.value?.trim('(', ')')?.toInt()
}
