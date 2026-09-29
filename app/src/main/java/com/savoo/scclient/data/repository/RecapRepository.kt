package com.savoo.scclient.data.repository

import com.savoo.scclient.data.local.ArtistDiscoveryStat
import com.savoo.scclient.data.local.ArtistListenStat
import com.savoo.scclient.data.local.PlayHistoryDao
import com.savoo.scclient.data.local.PlayMoment
import com.savoo.scclient.data.local.RecapTotals
import com.savoo.scclient.data.local.TrackListenStat
import com.savoo.scclient.data.local.TrackSkipStat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

enum class ListenerTime { MORNING, DAY, EVENING, NIGHT }

data class RecapStats(
    val year: Int,
    val periodStart: Long,
    val periodEnd: Long,
    val startedMidYear: Boolean,
    val totals: RecapTotals,
    val monthlyMs: List<Long>,
    val hourlyMs: List<Long>,
    val listenerTime: ListenerTime?,
    val topArtists: List<ArtistListenStat>,
    val topTracks: List<TrackListenStat>,
    val topGenres: List<String>,
    val discoveredFrom: Long?,
    val discoveries: List<ArtistDiscoveryStat>,
    val mostSkipped: List<TrackSkipStat>,
) {
    val eligible: Boolean get() = totals.totalMs >= RecapMath.MIN_ELIGIBLE_MS
    val topMonth: Int? get() = monthlyMs.withIndex().filter { it.value > 0 }.maxByOrNull { it.value }?.index?.plus(1)
}

object RecapMath {
    const val MIN_ELIGIBLE_MS = 10L * 60 * 60 * 1000
    const val WARM_UP_MS = 30L * 24 * 60 * 60 * 1000
    private const val LAST_JANUARY_DAY = 15

    fun recapYearFor(date: LocalDate): Int? = when {
        date.monthValue == 12 -> date.year
        date.monthValue == 1 && date.dayOfMonth <= LAST_JANUARY_DAY -> date.year - 1
        else -> null
    }

    fun yearBounds(year: Int, zone: ZoneId): Pair<Long, Long> {
        val start = LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.of(year + 1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        return start to end
    }

    fun discoveredFrom(firstPlayedAt: Long?, yearStart: Long, yearEnd: Long): Long? {
        if (firstPlayedAt == null) return null
        val from = if (firstPlayedAt < yearStart) yearStart else firstPlayedAt + WARM_UP_MS
        return from.takeIf { it < yearEnd }
    }

    fun monthlyMs(moments: List<PlayMoment>, zone: ZoneId): List<Long> {
        val months = LongArray(12)
        moments.forEach { moment ->
            val month = Instant.ofEpochMilli(moment.playedAt).atZone(zone).monthValue
            months[month - 1] += moment.msPlayed
        }
        return months.toList()
    }

    fun hourlyMs(moments: List<PlayMoment>, zone: ZoneId): List<Long> {
        val hours = LongArray(24)
        moments.forEach { moment ->
            val hour = Instant.ofEpochMilli(moment.playedAt).atZone(zone).hour
            hours[hour] += moment.msPlayed
        }
        return hours.toList()
    }

    fun listenerTime(hourlyMs: List<Long>): ListenerTime? {
        if (hourlyMs.all { it == 0L }) return null
        return ListenerTime.entries.maxBy { time -> hoursOf(time).sumOf { hourlyMs[it] } }
    }

    fun hoursOf(time: ListenerTime): List<Int> = when (time) {
        ListenerTime.MORNING -> (5..11).toList()
        ListenerTime.DAY -> (12..17).toList()
        ListenerTime.EVENING -> (18..22).toList()
        ListenerTime.NIGHT -> listOf(23, 0, 1, 2, 3, 4)
    }
}

@Singleton
class RecapRepository @Inject constructor(
    private val playHistoryDao: PlayHistoryDao,
) {
    suspend fun compute(year: Int, zone: ZoneId = ZoneId.systemDefault()): RecapStats = withContext(Dispatchers.IO) {
        val (yearStart, yearEnd) = RecapMath.yearBounds(year, zone)
        val firstPlayedAt = playHistoryDao.firstPlayedAt()
        val periodStart = maxOf(yearStart, firstPlayedAt ?: yearStart)
        val discoveredFrom = RecapMath.discoveredFrom(firstPlayedAt, yearStart, yearEnd)
        val moments = playHistoryDao.playMoments(yearStart, yearEnd)
        val hourly = RecapMath.hourlyMs(moments, zone)
        RecapStats(
            year = year,
            periodStart = periodStart,
            periodEnd = yearEnd,
            startedMidYear = firstPlayedAt != null && firstPlayedAt > yearStart,
            totals = playHistoryDao.recapTotals(yearStart, yearEnd),
            monthlyMs = RecapMath.monthlyMs(moments, zone),
            hourlyMs = hourly,
            listenerTime = RecapMath.listenerTime(hourly),
            topArtists = playHistoryDao.recapTopArtists(yearStart, yearEnd, TOP_LIMIT),
            topTracks = playHistoryDao.recapTopTracks(yearStart, yearEnd, TOP_LIMIT),
            topGenres = playHistoryDao.recapTopGenres(yearStart, yearEnd, GENRE_LIMIT),
            discoveredFrom = discoveredFrom,
            discoveries = discoveredFrom?.let { playHistoryDao.recapDiscoveries(it, yearEnd, DISCOVERY_LIMIT) }.orEmpty(),
            mostSkipped = playHistoryDao.recapMostSkipped(yearStart, yearEnd, SKIP_LIMIT),
        )
    }

    private companion object {
        const val TOP_LIMIT = 5
        const val GENRE_LIMIT = 3
        const val DISCOVERY_LIMIT = 3
        const val SKIP_LIMIT = 3
    }
}
