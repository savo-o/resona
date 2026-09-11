package com.savoo.scclient.data.repository

import com.savoo.scclient.data.local.ArtistListenStat
import com.savoo.scclient.data.local.PlayHistoryDao
import com.savoo.scclient.data.local.TrackListenStat
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

enum class StatsPeriod(val days: Int?) {
    WEEK(7),
    MONTH(30),
    YEAR(365),
    ALL(null);

    fun since(now: Long = System.currentTimeMillis()): Long =
        days?.let { now - it * 86_400_000L } ?: 0L
}

@Singleton
class StatsRepository @Inject constructor(
    private val playHistoryDao: PlayHistoryDao,
) {
    fun totalMsListened(since: Long = 0L): Flow<Long> = playHistoryDao.totalMsPlayed(since)
    fun totalPlays(since: Long = 0L): Flow<Int> = playHistoryDao.totalPlays(since)
    fun topArtists(limit: Int = 10, since: Long = 0L): Flow<List<ArtistListenStat>> =
        playHistoryDao.topArtists(limit, since)
    fun topTracks(limit: Int = 10, since: Long = 0L): Flow<List<TrackListenStat>> =
        playHistoryDao.topTracks(limit, since)
    fun topGenre(since: Long = 0L): Flow<String?> = playHistoryDao.topGenre(since)
}
