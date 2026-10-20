package com.xfin.feature.player

import com.xfin.core.model.MediaSegment
import com.xfin.core.model.SegmentType

/** How long the skip button stays up on its own at the start of a segment; later it shows with the controls. */
internal const val SKIP_PROMPT_MS = 10_000L

/** Near the end of a segment skipping saves nothing, so the button is gone. */
private const val SKIP_MIN_LEFT_MS = 2_000L

/** The segment playback is in, if enough of it is left to be worth skipping. */
internal fun List<MediaSegment>.skippableAt(positionMs: Long): MediaSegment? =
    firstOrNull { positionMs >= it.startMs && positionMs < it.endMs - SKIP_MIN_LEFT_MS }

internal val MediaSegment.skipLabel: String
    get() = when (type) {
        SegmentType.INTRO -> "Skip intro"
        SegmentType.RECAP -> "Skip recap"
        SegmentType.OUTRO -> "Skip credits"
    }
