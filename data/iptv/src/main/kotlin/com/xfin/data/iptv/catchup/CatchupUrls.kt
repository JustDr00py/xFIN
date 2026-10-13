package com.xfin.data.iptv.catchup

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Builds archive ("catch-up") URLs for a past or airing program.
 *
 * Xtream: `/timeshift/{user}/{pass}/{minutes}/{yyyy-MM-dd:HH-mm}/{id}.ts`, with the start in the
 * provider's own timezone. M3U playlists declare a `catchup` mode:
 * - `default`: `catchup-source` is the whole URL, with placeholders
 * - `append`: `catchup-source` is appended to the stream URL
 * - `shift`: `?utc=…&lutc=…` is appended
 * - `flussonic` / `fs`: the stream path is rewritten to a Flussonic archive path
 * - `xc`: an Xtream-style stream URL rewritten to `/timeshift/…`
 */
object CatchupUrls {

    private val XTREAM_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd:HH-mm")

    fun xtream(
        baseUrl: String,
        username: String,
        password: String,
        streamId: String,
        startMs: Long,
        endMs: Long,
        zone: ZoneId = ZoneOffset.UTC,
    ): String {
        val start = XTREAM_TIME.format(Instant.ofEpochMilli(startMs).atZone(zone))
        return "${baseUrl.trimEnd('/')}/timeshift/${username.encodePath()}/${password.encodePath()}/" +
            "${minutes(startMs, endMs)}/$start/$streamId.ts"
    }

    /** Null when the mode is unknown or the playlist lacks what the mode needs. */
    fun m3u(
        mode: String,
        source: String?,
        streamUrl: String,
        startMs: Long,
        endMs: Long,
        nowMs: Long,
    ): String? = when (mode.lowercase()) {
        "default" -> source?.takeIf { it.isNotBlank() }?.let { fill(it, startMs, endMs, nowMs) }
        "append" -> source?.takeIf { it.isNotBlank() }?.let { streamUrl + fill(it, startMs, endMs, nowMs) }
        "shift", "timeshift" -> streamUrl + (if ('?' in streamUrl) "&" else "?") + "utc=${startMs / 1000}&lutc=${nowMs / 1000}"
        "flussonic", "flussonic-hls", "flussonic-ts", "fs" -> flussonic(streamUrl, startMs, endMs)
        "xc" -> xtreamFromStreamUrl(streamUrl, startMs, endMs)
        else -> source?.takeIf { it.startsWith("http") }?.let { fill(it, startMs, endMs, nowMs) }
    }

    /**
     * Replaces the placeholders IPTV players agree on: `{utc}`/`${start}`, `{utcend}`/`${end}`,
     * `{lutc}`/`${now}`/`{timestamp}`, `{duration}` (seconds), `{offset}`, `{Y}{m}{d}{H}{M}{S}`
     * (start, UTC) and formatted forms like `{utc:Y-m-d-H-M}` or `${start:YmdHMS}`.
     */
    fun fill(template: String, startMs: Long, endMs: Long, nowMs: Long): String {
        val start = startMs / 1000
        val end = endMs / 1000
        val now = nowMs / 1000
        val formatted = Regex("""\$?\{(utc|start|utcend|end|lutc|now|timestamp):([^}]+)}""").replace(template) { m ->
            val at = when (m.groupValues[1]) {
                "utc", "start" -> start
                "utcend", "end" -> end
                else -> now
            }
            formatLetters(m.groupValues[2], at)
        }
        var out = formatted
        val values = linkedMapOf(
            "utcend" to end, "end" to end,
            "utc" to start, "start" to start,
            "lutc" to now, "now" to now, "timestamp" to now,
            "duration" to (end - start), "offset" to (now - start),
        )
        values.forEach { (key, value) ->
            out = out.replace("\${$key}", value.toString()).replace("{$key}", value.toString())
        }
        "YmdHMS".forEach { letter -> out = out.replace("{$letter}", formatLetters(letter.toString(), start)) }
        return out
    }

    private fun flussonic(streamUrl: String, startMs: Long, endMs: Long): String? {
        val match = Regex("""^(https?://[^/]+)/(.+)/([^/?]+?)(\.m3u8|\.ts)?(\?.*)?$""").find(streamUrl) ?: return null
        val (host, channel, file, ext, query) = match.destructured
        val start = startMs / 1000
        val duration = (endMs - startMs) / 1000
        val path = when {
            file == "mpegts" || ext == ".ts" -> "$channel/timeshift_abs-$start.ts"
            file.startsWith("index") || file.startsWith("video") -> "$channel/$file-$start-$duration.m3u8"
            else -> "$channel/$file/timeshift_abs-$start.m3u8"
        }
        return "$host/$path$query"
    }

    private fun xtreamFromStreamUrl(streamUrl: String, startMs: Long, endMs: Long): String? {
        val match = Regex("""^(https?://[^/]+)/(?:live/)?([^/]+)/([^/]+)/(\d+)(?:\.\w+)?(\?.*)?$""").find(streamUrl) ?: return null
        val (host, user, pass, id) = match.destructured
        return "$host/timeshift/$user/$pass/${minutes(startMs, endMs)}/" +
            "${XTREAM_TIME.format(Instant.ofEpochMilli(startMs).atZone(ZoneOffset.UTC))}/$id.ts"
    }

    private fun minutes(startMs: Long, endMs: Long) = ((endMs - startMs + 59_999) / 60_000).coerceAtLeast(1)

    /** PHP-style date letters (Y m d H M/i S/s) for the start time in UTC. */
    private fun formatLetters(pattern: String, epochSeconds: Long): String {
        val t = Instant.ofEpochSecond(epochSeconds).atZone(ZoneOffset.UTC)
        return buildString {
            pattern.forEach { c ->
                append(
                    when (c) {
                        'Y' -> "%04d".format(t.year)
                        'm' -> "%02d".format(t.monthValue)
                        'd' -> "%02d".format(t.dayOfMonth)
                        'H' -> "%02d".format(t.hour)
                        'M', 'i' -> "%02d".format(t.minute)
                        'S', 's' -> "%02d".format(t.second)
                        else -> c.toString()
                    },
                )
            }
        }
    }

    private fun String.encodePath() = java.net.URLEncoder.encode(this, "UTF-8").replace("+", "%20")
}
