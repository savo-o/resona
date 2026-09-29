package com.savoo.scclient.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.savoo.scclient.data.local.AppDatabase
import com.savoo.scclient.data.model.PlayEvent
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class RecapRepositoryTest {

    private val zone = ZoneId.of("Europe/Moscow")
    private lateinit var db: AppDatabase
    private lateinit var repository: RecapRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RecapRepository(db.playHistoryDao())
    }

    @After
    fun tearDown() = db.close()

    private fun at(year: Int, month: Int, day: Int, hour: Int): Long =
        LocalDateTime.of(year, month, day, hour, 0).atZone(zone).toInstant().toEpochMilli()

    private suspend fun play(
        trackId: Long,
        artistId: Long,
        playedAt: Long,
        minutes: Long,
        skipped: Boolean = false,
        hidden: Boolean = false,
        genre: String? = null,
    ) {
        db.playHistoryDao().insert(
            PlayEvent(
                trackId = trackId,
                title = "track $trackId",
                artistId = artistId,
                artistName = "artist $artistId",
                artworkUrl = null,
                msPlayed = minutes * 60_000,
                playedAt = playedAt,
                genre = genre,
                hiddenFromHistory = hidden,
                skipped = skipped,
            )
        )
    }

    @Test
    fun longTimeUserGetsYearTotalsAndDiscoveriesSinceJanuary() = runBlocking {
        play(1, 10, at(2025, 11, 3, 14), 30)
        play(1, 10, at(2026, 1, 5, 23), 60, genre = "phonk")
        play(2, 20, at(2026, 3, 10, 1), 120, genre = "phonk")
        play(3, 20, at(2026, 3, 11, 2), 90, hidden = true, genre = "hyperpop")
        play(4, 30, at(2026, 7, 1, 9), 20, skipped = true)
        play(4, 30, at(2026, 7, 2, 9), 0, skipped = true)
        play(5, 40, at(2027, 1, 2, 12), 500)

        val stats = repository.compute(2026, zone)

        assertFalse(stats.startedMidYear)
        assertEquals(290L * 60_000, stats.totals.totalMs)
        assertEquals(4, stats.totals.playCount)
        assertEquals(4, stats.totals.trackCount)
        assertEquals(3, stats.totals.artistCount)
        assertEquals(3, stats.topMonth)
        assertEquals(210L * 60_000, stats.monthlyMs[2])
        assertEquals(ListenerTime.NIGHT, stats.listenerTime)
        assertEquals(listOf(20L, 10L, 30L), stats.topArtists.map { it.artistId })
        assertEquals(listOf("phonk", "hyperpop"), stats.topGenres)
        assertEquals(listOf(20L, 30L), stats.discoveries.map { it.artistId })
        assertEquals(210L * 60_000, stats.discoveries.first().totalMs)
        assertEquals(4L, stats.mostSkipped.single().trackId)
        assertEquals(2, stats.mostSkipped.single().skipCount)
    }

    @Test
    fun midYearUserSkipsWarmUpMonthForDiscoveries() = runBlocking {
        play(1, 10, at(2026, 9, 12, 18), 600)
        play(2, 20, at(2026, 9, 20, 18), 60)
        play(3, 30, at(2026, 10, 25, 19), 45)

        val stats = repository.compute(2026, zone)

        assertTrue(stats.startedMidYear)
        assertEquals(at(2026, 9, 12, 18), stats.periodStart)
        assertTrue(stats.eligible)
        assertEquals(ListenerTime.EVENING, stats.listenerTime)
        assertEquals(listOf(30L), stats.discoveries.map { it.artistId })
    }

    @Test
    fun userWhoStartedInDecemberHasNoDiscoveriesAndIsNotEligible() = runBlocking {
        play(1, 10, at(2026, 12, 20, 12), 90)

        val stats = repository.compute(2026, zone)

        assertNull(stats.discoveredFrom)
        assertTrue(stats.discoveries.isEmpty())
        assertFalse(stats.eligible)
    }

    @Test
    fun emptyHistoryProducesEmptyRecap() = runBlocking {
        val stats = repository.compute(2026, zone)

        assertEquals(0L, stats.totals.totalMs)
        assertNull(stats.listenerTime)
        assertNull(stats.topMonth)
        assertFalse(stats.eligible)
    }

    @Test
    fun bannerWindowCoversDecemberAndFirstHalfOfJanuary() {
        assertNull(RecapMath.recapYearFor(LocalDate.of(2026, 11, 30)))
        assertEquals(2026, RecapMath.recapYearFor(LocalDate.of(2026, 12, 1)))
        assertEquals(2026, RecapMath.recapYearFor(LocalDate.of(2026, 12, 29)))
        assertEquals(2026, RecapMath.recapYearFor(LocalDate.of(2027, 1, 15)))
        assertNull(RecapMath.recapYearFor(LocalDate.of(2027, 1, 16)))
    }
}
