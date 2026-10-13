package com.xfin.data.iptv.m3u

import java.io.BufferedReader

/**
 * Streaming extended-M3U parser. Never holds the whole playlist in memory, so 100k+ entry
 * playlists parse in constant memory. Tolerates the many dialects seen in the wild:
 * missing #EXTM3U, unquoted attributes, commas inside quoted values, #EXTGRP, #EXTVLCOPT,
 * and catch-up attributes on either the header or the entry.
 */
object M3uParser {

    fun parse(reader: BufferedReader, onHeader: (M3uHeader) -> Unit = {}, onEntry: (M3uEntry) -> Unit) {
        var header = M3uHeader()
        var info: ExtInf? = null
        var extGroup: String? = null
        var userAgent: String? = null
        var referrer: String? = null

        reader.lineSequence().forEach { raw ->
            val line = raw.trim().removePrefix("﻿")
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTM3U", ignoreCase = true) -> {
                    val attrs = parseAttributes(line.substring(7))
                    header = M3uHeader(
                        epgUrls = (attrs["url-tvg"] ?: attrs["x-tvg-url"])
                            ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
                            .orEmpty(),
                        catchupMode = attrs["catchup"],
                        catchupSource = attrs["catchup-source"],
                        catchupDays = attrs["catchup-days"]?.toIntOrNull() ?: 0,
                    )
                    onHeader(header)
                }
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    info = parseExtInf(line)
                    extGroup = null
                    userAgent = null
                    referrer = null
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) -> extGroup = line.substring(8).trim()
                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val option = line.substring(11)
                    when {
                        option.startsWith("http-user-agent=") -> userAgent = option.substringAfter('=')
                        option.startsWith("http-referrer=") -> referrer = option.substringAfter('=')
                    }
                }
                line.startsWith("#") -> Unit
                else -> {
                    val current = info
                    val (url, pipeHeaders) = splitPipeHeaders(line)
                    val attrs = current?.attributes.orEmpty()
                    val name = current?.title?.takeIf { it.isNotBlank() }
                        ?: attrs["tvg-name"]
                        ?: url.substringAfterLast('/')
                    onEntry(
                        M3uEntry(
                            name = name.trim(),
                            url = url,
                            tvgId = attrs["tvg-id"]?.takeIf { it.isNotBlank() },
                            tvgName = attrs["tvg-name"]?.takeIf { it.isNotBlank() },
                            logoUrl = (attrs["tvg-logo"] ?: attrs["logo"])?.takeIf { it.isNotBlank() },
                            group = (attrs["group-title"] ?: extGroup)?.takeIf { it.isNotBlank() },
                            number = (attrs["tvg-chno"] ?: attrs["channel-number"])?.toIntOrNull(),
                            catchupMode = attrs["catchup"] ?: attrs["catchup-type"] ?: header.catchupMode,
                            catchupSource = attrs["catchup-source"] ?: header.catchupSource,
                            catchupDays = attrs["catchup-days"]?.toIntOrNull()
                                ?: attrs["timeshift"]?.toIntOrNull()
                                ?: header.catchupDays,
                            userAgent = pipeHeaders["user-agent"] ?: userAgent,
                            referrer = pipeHeaders["referer"] ?: pipeHeaders["referrer"] ?: referrer,
                        ),
                    )
                    info = null
                    extGroup = null
                    userAgent = null
                    referrer = null
                }
            }
        }
    }

    internal data class ExtInf(val attributes: Map<String, String>, val title: String)

    /** `#EXTINF:-1 tvg-id="x" group-title="A, B",Channel Name` */
    internal fun parseExtInf(line: String): ExtInf {
        val body = line.substringAfter(':', "")
        // The title starts after the first comma that is not inside quotes.
        var inQuotes = false
        var titleStart = -1
        for (i in body.indices) {
            when (body[i]) {
                '"' -> inQuotes = !inQuotes
                ',' -> if (!inQuotes) {
                    titleStart = i + 1
                    break
                }
            }
        }
        val attrPart = if (titleStart >= 0) body.substring(0, titleStart - 1) else body
        val title = if (titleStart >= 0) body.substring(titleStart) else ""
        return ExtInf(parseAttributes(attrPart), title.trim())
    }

    private val ATTRIBUTE = Regex("""([A-Za-z0-9_-]+)=(?:"([^"]*)"|([^\s"]+))""")

    internal fun parseAttributes(text: String): Map<String, String> =
        ATTRIBUTE.findAll(text).associate { match ->
            match.groupValues[1].lowercase() to (match.groups[2]?.value ?: match.groupValues[3]).trim()
        }

    /** Kodi-style `http://url|User-Agent=x&Referer=y`. */
    private fun splitPipeHeaders(line: String): Pair<String, Map<String, String>> {
        val pipe = line.indexOf('|')
        if (pipe < 0) return line to emptyMap()
        val headers = line.substring(pipe + 1).split('&').mapNotNull { pair ->
            val eq = pair.indexOf('=')
            if (eq <= 0) null else pair.substring(0, eq).lowercase() to pair.substring(eq + 1)
        }.toMap()
        return line.substring(0, pipe) to headers
    }
}
