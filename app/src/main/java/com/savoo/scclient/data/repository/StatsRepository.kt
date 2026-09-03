package com.savoo.scclient.data.repository

import com.savoo.scclient.data.local.ArtistListenStat
import com.savoo.scclient.data.local.PlayHistoryDao
import com.savoo.scclient.data.local.TrackListenStat
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatsRepository @Inject constructor(
    private val playHistoryDao: PlayHistoryDao,
) {
    val totalMsListened: Flow<Long> = playHistoryDao.totalMsPlayed()
    val totalPlays: Flow<Int> = playHistoryDao.totalPlays()

    fun topArtists(limit: Int = 10): Flow<List<ArtistListenStat>> = playHistoryDao.topArtists(limit)
    fun topTracks(limit: Int = 10): Flow<List<TrackListenStat>> = playHistoryDao.topTracks(limit)
    val topGenre: Flow<String?> = playHistoryDao.topGenre()
}
