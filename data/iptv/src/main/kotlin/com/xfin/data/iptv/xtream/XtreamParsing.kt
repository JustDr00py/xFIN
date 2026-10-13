package com.xfin.data.iptv.xtream

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Xtream panels are notoriously inconsistent: numbers arrive as strings, booleans as "0"/"1",
 * fields go missing or are null. Everything is read leniently from the JSON tree instead of
 * strict data classes.
 */
internal object XtreamParsing {
    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun account(root: JsonElement): XtreamAccount {
        val info = (root as? JsonObject)?.get("user_info") as? JsonObject
            ?: throw XtreamAuthException("Server did not return account info")
        if (info.int("auth") == 0) throw XtreamAuthException("Invalid username or password")
        val status = info.string("status")
        if (status != null && !status.equals("Active", ignoreCase = true)) {
            throw XtreamAuthException("Account status: $status")
        }
        return XtreamAccount(
            status = status,
            expiresAt = info.long("exp_date")?.times(1000),
            maxConnections = info.int("max_connections"),
            activeConnections = info.int("active_cons"),
        )
    }

    fun categories(root: JsonElement): List<XtreamCategory> =
        (root as? JsonArray).orEmpty().mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj.string("category_id") ?: return@mapNotNull null
            XtreamCategory(id, obj.string("category_name")?.trim().orEmpty().ifEmpty { "Uncategorized" })
        }

    fun liveStream(element: JsonElement): XtreamLiveStream? {
        val obj = element as? JsonObject ?: return null
        val id = obj.long("stream_id") ?: return null
        return XtreamLiveStream(
            streamId = id,
            number = obj.int("num"),
            name = obj.string("name")?.trim().orEmpty().ifEmpty { "Channel $id" },
            icon = obj.string("stream_icon")?.takeIf { it.isNotBlank() },
            epgChannelId = obj.string("epg_channel_id")?.takeIf { it.isNotBlank() },
            categoryId = obj.string("category_id"),
            archiveDays = if (obj.int("tv_archive") == 1) obj.int("tv_archive_duration") ?: 0 else 0,
            directSource = obj.string("direct_source")?.takeIf { it.isNotBlank() },
        )
    }

    private fun JsonObject.string(key: String): String? = when (val v = get(key)) {
        null, JsonNull -> null
        is JsonPrimitive -> v.contentOrNull
        else -> null
    }

    private fun JsonObject.long(key: String): Long? = string(key)?.trim()?.let {
        it.toLongOrNull() ?: it.toDoubleOrNull()?.toLong()
    }

    private fun JsonObject.int(key: String): Int? = long(key)?.toInt()
}
