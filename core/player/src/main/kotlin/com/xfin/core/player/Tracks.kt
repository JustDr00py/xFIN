package com.xfin.core.player

import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.Tracks
import java.util.Locale

/** Selectable audio or subtitle tracks with human-readable labels. */
internal fun Tracks.trackOptions(type: Int): List<TrackOption> = groups
    .filter { it.type == type }
    .flatMap { group ->
        (0 until group.length).filter { group.isTrackSupported(it) }.map { i ->
            val format = group.getTrackFormat(i)
            val label = listOfNotNull(
                format.label,
                "Closed captions".takeIf {
                    format.sampleMimeType == MimeTypes.APPLICATION_CEA608 || format.sampleMimeType == MimeTypes.APPLICATION_CEA708
                },
                format.language?.let { Locale.forLanguageTag(it).displayLanguage.ifBlank { it } },
                format.sampleMimeType?.substringAfter('/')?.uppercase()?.takeIf { type == C.TRACK_TYPE_AUDIO },
                format.channelCount.takeIf { it > 0 && type == C.TRACK_TYPE_AUDIO }?.let(::channelLabel),
            ).distinct().joinToString(" · ").ifBlank { "Track ${i + 1}" }
            TrackOption(group, i, label, group.isTrackSelected(i))
        }
    }

private fun channelLabel(channels: Int) = when (channels) {
    1 -> "Mono"
    2 -> "Stereo"
    6 -> "5.1"
    8 -> "7.1"
    else -> "${channels}ch"
}
