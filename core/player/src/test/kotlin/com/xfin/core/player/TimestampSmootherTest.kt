package com.xfin.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimestampSmootherTest {

    @Test
    fun `steady stream passes through unchanged`() {
        val video = TimestampSmoother().track(drivesEpochs = true)
        // Decode order with B-frames: PTS steps back a little, which is not a jump.
        listOf(0L, 120_000, 40_000, 80_000, 240_000, 160_000, 200_000).forEach {
            assertEquals(it, video.adjust(it))
        }
    }

    @Test
    fun `backward feed switch continues the timeline and keeps AV sync`() {
        val smoother = TimestampSmoother()
        val video = smoother.track(drivesEpochs = true)
        val audio = smoother.track(drivesEpochs = true)
        // 27 s into the old feed, interleaved A/V.
        var t = 0L
        while (t <= 27_000_000) {
            video.adjust(t)
            audio.adjust(t + 5_000)
            t += 40_000
        }
        val lastVideo = t - 40_000
        // The backup feed starts at 0.8 s: a 26.2 s jump back, as seen on the TV.
        val newVideo = video.adjust(800_000)
        val newAudio = audio.adjust(805_000)
        assertEquals(lastVideo + 40_000, newVideo)
        // Same offset for both tracks, so their spacing (sync) is unchanged.
        assertEquals(5_000, newAudio - newVideo)
        // And the timeline keeps going from there.
        assertEquals(newVideo + 40_000, video.adjust(840_000))
    }

    @Test
    fun `forward jump is smoothed too`() {
        val audio = TimestampSmoother().track(drivesEpochs = true)
        audio.adjust(0)
        audio.adjust(21_333)
        assertEquals(42_666L, audio.adjust(3_600_000_000))
    }

    @Test
    fun `a long gap between captions shifts nothing`() {
        val captions = TimestampSmoother().track(drivesEpochs = false)
        captions.adjust(0)
        assertEquals(10_000_000L, captions.adjust(10_000_000))
    }

    @Test
    fun `captions follow a jump started by video`() {
        val smoother = TimestampSmoother()
        val video = smoother.track(drivesEpochs = true)
        val captions = smoother.track(drivesEpochs = false)
        var t = 0L
        while (t <= 30_000_000) { video.adjust(t); t += 40_000 }
        val newVideo = video.adjust(1_000_000)
        assertEquals(newVideo, captions.adjust(1_000_000))
        assertTrue(newVideo > 30_000_000)
    }
}
