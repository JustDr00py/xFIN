package com.xfin.data.iptv.epg

import org.xmlpull.v1.XmlPullParser
import java.time.LocalDateTime
import java.time.ZoneOffset

data class XmltvChannel(val id: String, val displayNames: List<String>, val iconUrl: String?)

data class XmltvProgramme(
    val channelId: String,
    val start: Long,
    val stop: Long,
    val title: String,
    val subtitle: String?,
    val description: String?,
    val category: String?,
    val iconUrl: String?,
    val episode: String?,
)

/**
 * Streaming XMLTV reader on top of XmlPullParser: constant memory regardless of guide size.
 * [accept] is asked once per `<programme>` before its body is read, so programmes for channels
 * the user does not have are skipped cheaply.
 */
object XmltvParser {

    fun parse(
        parser: XmlPullParser,
        onChannel: (XmltvChannel) -> Unit,
        accept: (channelId: String, start: Long, stop: Long) -> Boolean,
        onProgramme: (XmltvProgramme) -> Unit,
    ) {
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "channel" -> readChannel(parser)?.let(onChannel)
                    "programme" -> {
                        val channel = parser.getAttributeValue(null, "channel")?.trim()
                        val start = parser.getAttributeValue(null, "start")?.let(::parseTime)
                        val stop = parser.getAttributeValue(null, "stop")?.let(::parseTime)
                        if (channel.isNullOrEmpty() || start == null || !accept(channel, start, stop ?: start)) {
                            skip(parser)
                        } else {
                            readProgramme(parser, channel, start, stop)?.let(onProgramme)
                        }
                    }
                }
            }
            event = parser.next()
        }
    }

    private fun readChannel(parser: XmlPullParser): XmltvChannel? {
        val id = parser.getAttributeValue(null, "id")?.trim()
        val names = mutableListOf<String>()
        var icon: String? = null
        forEachChild(parser) { name ->
            when (name) {
                "display-name" -> readText(parser).takeIf { it.isNotBlank() }?.let(names::add)
                "icon" -> {
                    icon = parser.getAttributeValue(null, "src")
                    skip(parser)
                }
                else -> skip(parser)
            }
        }
        return if (id.isNullOrEmpty()) null else XmltvChannel(id, names, icon)
    }

    private fun readProgramme(parser: XmlPullParser, channel: String, start: Long, stop: Long?): XmltvProgramme? {
        var title: String? = null
        var subtitle: String? = null
        var desc: String? = null
        var category: String? = null
        var icon: String? = null
        var episode: String? = null
        forEachChild(parser) { name ->
            when (name) {
                "title" -> readText(parser).let { if (title == null) title = it }
                "sub-title" -> readText(parser).let { if (subtitle == null) subtitle = it }
                "desc" -> readText(parser).let { if (desc == null) desc = it }
                "category" -> readText(parser).let { if (category == null) category = it }
                "episode-num" -> {
                    val system = parser.getAttributeValue(null, "system")
                    val text = readText(parser)
                    if (system == "onscreen" || episode == null) {
                        episode = if (system == "xmltv_ns") formatXmltvNs(text) ?: episode else text
                    }
                }
                "icon" -> {
                    icon = parser.getAttributeValue(null, "src")
                    skip(parser)
                }
                else -> skip(parser)
            }
        }
        val t = title?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        // Some feeds omit `stop`; assume 30 minutes rather than dropping the programme.
        val end = stop?.takeIf { it > start } ?: (start + 30 * 60_000L)
        return XmltvProgramme(
            channelId = channel,
            start = start,
            stop = end,
            title = t,
            subtitle = subtitle?.trim()?.ifEmpty { null },
            description = desc?.trim()?.ifEmpty { null },
            category = category?.trim()?.ifEmpty { null },
            iconUrl = icon?.trim()?.ifEmpty { null },
            episode = episode?.trim()?.ifEmpty { null },
        )
    }

    /** Calls [block] for each direct child start tag; [block] must consume the child (read or skip). */
    private inline fun forEachChild(parser: XmlPullParser, block: (String) -> Unit) {
        val depth = parser.depth
        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.END_DOCUMENT) return
            if (event == XmlPullParser.END_TAG && parser.depth == depth) return
            if (event == XmlPullParser.START_TAG) block(parser.name)
        }
    }

    private fun readText(parser: XmlPullParser): String {
        val sb = StringBuilder()
        val depth = parser.depth
        while (true) {
            when (parser.next()) {
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> sb.append(parser.text)
                XmlPullParser.END_TAG -> if (parser.depth == depth) return sb.toString()
                XmlPullParser.END_DOCUMENT -> return sb.toString()
            }
        }
    }

    private fun skip(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) return
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    /** `0.4.` (zero-based season.episode.part) -> `S1 E5`. */
    internal fun formatXmltvNs(text: String): String? {
        val parts = text.split('.')
        if (parts.size < 2) return null
        val season = parts[0].substringBefore('/').trim().toIntOrNull()?.plus(1)
        val ep = parts[1].substringBefore('/').trim().toIntOrNull()?.plus(1)
        return when {
            season != null && ep != null -> "S$season E$ep"
            ep != null -> "E$ep"
            else -> null
        }
    }

    /** XMLTV time: `YYYYMMDDhhmmss ±hhmm` (seconds and offset optional; no offset means UTC). */
    internal fun parseTime(value: String): Long? {
        val text = value.trim()
        val digits = text.takeWhile { it.isDigit() }
        if (digits.length < 12) return null
        return runCatching {
            val local = LocalDateTime.of(
                digits.substring(0, 4).toInt(),
                digits.substring(4, 6).toInt(),
                digits.substring(6, 8).toInt(),
                digits.substring(8, 10).toInt(),
                digits.substring(10, 12).toInt(),
                if (digits.length >= 14) digits.substring(12, 14).toInt() else 0,
            )
            val rest = text.substring(digits.length).trim()
            val offset = Regex("""([+-])(\d{2}):?(\d{2})""").find(rest)?.let { m ->
                val sign = if (m.groupValues[1] == "-") -1 else 1
                ZoneOffset.ofHoursMinutes(sign * m.groupValues[2].toInt(), sign * m.groupValues[3].toInt())
            } ?: ZoneOffset.UTC
            local.toInstant(offset).toEpochMilli()
        }.getOrNull()
    }
}
