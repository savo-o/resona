package com.savoo.scclient.ui.screens.recap

import com.savoo.scclient.data.local.ArtistDiscoveryStat
import com.savoo.scclient.data.local.ArtistListenStat
import com.savoo.scclient.data.local.RecapTotals
import com.savoo.scclient.data.local.TrackListenStat
import com.savoo.scclient.data.local.TrackSkipStat
import com.savoo.scclient.data.repository.RecapMath
import com.savoo.scclient.data.repository.RecapStats
import java.time.LocalDate
import java.time.ZoneId

object RecapDemo {
    private const val MINUTE = 60_000L

    fun stats(year: Int, zone: ZoneId = ZoneId.systemDefault()): RecapStats {
        val (start, end) = RecapMath.yearBounds(year, zone)
        val monthly = listOf(620L, 540, 810, 930, 700, 1210, 1480, 1320, 990, 860, 1040, 760).map { it * MINUTE }
        val hourly = (0..23).map { hour ->
            when (hour) {
                in 0..3 -> 900L
                in 21..23 -> 1100L
                in 17..20 -> 600L
                else -> 150L
            } * MINUTE
        }
        return RecapStats(
            year = year,
            periodStart = start,
            periodEnd = end,
            startedMidYear = false,
            totals = RecapTotals(totalMs = monthly.sum(), playCount = 4312, trackCount = 1187, artistCount = 402),
            monthlyMs = monthly,
            hourlyMs = hourly,
            listenerTime = RecapMath.listenerTime(hourly),
            topArtists = listOf(
                ArtistListenStat(1, "Moonrise Kid", null, 1840 * MINUTE, 612),
                ArtistListenStat(2, "velvet hours", null, 1320 * MINUTE, 455),
                ArtistListenStat(3, "Saint Neon", null, 990 * MINUTE, 301),
                ArtistListenStat(4, "k1ra", null, 760 * MINUTE, 240),
                ArtistListenStat(5, "Glass Harbor", null, 540 * MINUTE, 188),
            ),
            topTracks = listOf(
                TrackListenStat(11, "night drive (slowed)", "Moonrise Kid", null, 412 * MINUTE, 131),
                TrackListenStat(12, "Paper Planes Over Tokyo", "velvet hours", null, 305 * MINUTE, 97),
                TrackListenStat(13, "3am thoughts", "k1ra", null, 260 * MINUTE, 88),
                TrackListenStat(14, "Bloom", "Saint Neon", null, 214 * MINUTE, 70),
                TrackListenStat(15, "static love", "Glass Harbor", null, 180 * MINUTE, 61),
            ),
            topGenres = listOf("phonk", "hyperpop", "lo-fi"),
            discoveredFrom = start,
            discoveries = listOf(
                ArtistDiscoveryStat(
                    artistId = 4,
                    artistName = "k1ra",
                    artworkUrl = null,
                    firstPlayedAt = LocalDate.of(year, 5, 17).atStartOfDay(zone).toInstant().toEpochMilli(),
                    totalMs = 760 * MINUTE,
                    playCount = 240,
                ),
            ),
            mostSkipped = listOf(TrackSkipStat(21, "Summer Anthem 2026", "Radio Friendly", null, 27)),
        )
    }
}
