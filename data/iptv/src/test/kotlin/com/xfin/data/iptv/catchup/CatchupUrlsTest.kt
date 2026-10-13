package com.xfin.data.iptv.catchup

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class CatchupUrlsTest {
    // 2026-10-14 18:00:00 UTC to 19:30 UTC; "now" is 20:00 UTC.
    private val start = 1_792_000_800_000L
    private val end = start + 90 * 60_000L
    private val now = start + 120 * 60_000L

    @Test fun xtreamUsesProviderTimezone() = assertEquals(
        "http://p.tv:8080/timeshift/u/p%20w/90/2026-10-14:19-00/123.ts",
        CatchupUrls.xtream("http://p.tv:8080/", "u", "p w", "123", start, end, ZoneId.of("Europe/London")),
    )

    @Test fun xtreamRoundsPartialMinutesUp() = assertEquals(
        "http://p.tv/timeshift/u/p/2/2026-10-14:18-00/1.ts",
        CatchupUrls.xtream("http://p.tv", "u", "p", "1", start, start + 61_000),
    )

    @Test fun defaultFillsPlaceholders() = assertEquals(
        "http://a.tv/arch?s=1792000800&e=1792006200&d=5400&now=1792008000&o=7200",
        CatchupUrls.m3u("default", "http://a.tv/arch?s={utc}&e={utcend}&d={duration}&now={lutc}&o={offset}", "x", start, end, now),
    )

    @Test fun formattedPlaceholders() = assertEquals(
        "http://a.tv/2026-10-14-18-00/20261014193000.ts",
        CatchupUrls.m3u("default", "http://a.tv/{utc:Y-m-d-H-M}/\${end:YmdHMS}.ts", "x", start, end, now),
    )

    @Test fun appendAddsToStream() = assertEquals(
        "http://a.tv/live.m3u8?utc=1792000800",
        CatchupUrls.m3u("append", "?utc={utc}", "http://a.tv/live.m3u8", start, end, now),
    )

    @Test fun shiftAddsUtcAndLutc() = assertEquals(
        "http://a.tv/live.m3u8?x=1&utc=1792000800&lutc=1792008000",
        CatchupUrls.m3u("shift", null, "http://a.tv/live.m3u8?x=1", start, end, now),
    )

    @Test fun flussonicHls() = assertEquals(
        "http://f.tv/ch1/index-1792000800-5400.m3u8?token=t",
        CatchupUrls.m3u("flussonic", null, "http://f.tv/ch1/index.m3u8?token=t", start, end, now),
    )

    @Test fun flussonicTs() = assertEquals(
        "http://f.tv/ch1/timeshift_abs-1792000800.ts",
        CatchupUrls.m3u("fs", null, "http://f.tv/ch1/mpegts", start, end, now),
    )

    @Test fun xcFromStreamUrl() = assertEquals(
        "http://x.tv:80/timeshift/u/p/90/2026-10-14:18-00/77.ts",
        CatchupUrls.m3u("xc", null, "http://x.tv:80/live/u/p/77.m3u8", start, end, now),
    )

    @Test fun unknownWithoutSourceIsNull() = assertEquals(null, CatchupUrls.m3u("vod", null, "http://a.tv/x", start, end, now))
}
