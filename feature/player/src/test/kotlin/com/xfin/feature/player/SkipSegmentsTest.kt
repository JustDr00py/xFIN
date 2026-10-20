package com.xfin.feature.player

import com.xfin.core.model.MediaSegment
import com.xfin.core.model.SegmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SkipSegmentsTest {
    private val recap = MediaSegment(SegmentType.RECAP, startMs = 0, endMs = 45_000)
    private val intro = MediaSegment(SegmentType.INTRO, startMs = 60_000, endMs = 120_000)
    private val credits = MediaSegment(SegmentType.OUTRO, startMs = 1_300_000, endMs = 1_380_000)
    private val segments = listOf(recap, intro, credits)

    @Test
    fun findsTheSegmentPlaybackIsIn() {
        assertEquals(recap, segments.skippableAt(0))
        assertEquals(intro, segments.skippableAt(60_000))
        assertEquals(intro, segments.skippableAt(100_000))
        assertEquals(credits, segments.skippableAt(1_350_000))
    }

    @Test
    fun nothingBetweenSegments() {
        assertNull(segments.skippableAt(50_000))
        assertNull(segments.skippableAt(120_000))
        assertNull(emptyList<MediaSegment>().skippableAt(60_000))
    }

    @Test
    fun goneInTheLastTwoSeconds() {
        assertEquals(intro, segments.skippableAt(117_999))
        assertNull(segments.skippableAt(118_000))
    }

    @Test
    fun labels() {
        assertEquals("Skip recap", recap.skipLabel)
        assertEquals("Skip intro", intro.skipLabel)
        assertEquals("Skip credits", credits.skipLabel)
    }
}
