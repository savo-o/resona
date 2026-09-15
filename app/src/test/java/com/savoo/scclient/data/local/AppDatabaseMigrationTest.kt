package com.savoo.scclient.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun releaseUserUpgradesFromVersion11KeepingLibrary() {
        helper.createDatabase(TEST_DB, 11).use { db ->
            db.execSQL(
                "INSERT INTO favorites (trackId, title, username, artworkUrl, durationMs, permalinkUrl, userId, userAvatarUrl, addedAt, source, genre) " +
                    "VALUES (1, 'Ёжик Тестовый', 'Test Artist', NULL, 120000, NULL, 10, NULL, 1000, 'LOCAL', 'rap')"
            )
            db.execSQL(
                "INSERT INTO favorite_artists (artistId, username, fullName, avatarUrl, followersCount, permalinkUrl, addedAt) " +
                    "VALUES (10, 'Test Artist', NULL, NULL, 5000, NULL, 1000)"
            )
            db.execSQL(
                "INSERT INTO offline_tracks (trackId, title, username, artworkUrl, durationMs, permalinkUrl, userId, userAvatarUrl, localPath, savedAt, fileSizeBytes, sourceFolderUri, genre) " +
                    "VALUES (1, 'Ёжик Тестовый', 'Test Artist', NULL, 120000, NULL, 10, NULL, '/offline/1.mp3', 2000, 123456, NULL, NULL)"
            )
            db.execSQL(
                "INSERT INTO play_history (trackId, title, artistId, artistName, artworkUrl, msPlayed, playedAt, genre) " +
                    "VALUES (1, 'Ёжик Тестовый', 10, 'Test Artist', NULL, 60000, 3000, NULL)"
            )
            insertLyricsCache(db, trackId = 1, provider = "LRCLIB")
            insertLyricsCache(db, trackId = 1, provider = "KUGOU")
        }

        helper.runMigrationsAndValidate(TEST_DB, LATEST_VERSION, true, *AppDatabase.ALL_MIGRATIONS).use { db ->
            db.query("SELECT title, titleKey, artistKey FROM favorites WHERE trackId = 1").use {
                assertTrue(it.moveToFirst())
                assertEquals("Ёжик Тестовый", it.getString(0))
                assertEquals("ежик тестовый", it.getString(1))
                assertEquals("test artist", it.getString(2))
            }
            assertEquals(1, count(db, "favorite_artists"))
            assertEquals(1, count(db, "offline_tracks"))
            db.query("SELECT hiddenFromHistory FROM play_history").use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
            }
            db.query("SELECT provider, sourceDurationMs FROM lyrics_cache").use {
                assertEquals(1, it.count)
                assertTrue(it.moveToFirst())
                assertEquals("LRCLIB", it.getString(0))
                assertTrue(it.isNull(1))
            }
            assertEquals(0, count(db, "lyrics_sync"))
        }
    }

    @Test
    fun everyShippedVersionOpensWithRoom() {
        for (version in FIRST_TESTED_VERSION..LATEST_VERSION) {
            val name = "open-$version.db"
            helper.createDatabase(name, version).close()
            val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(*AppDatabase.ALL_MIGRATIONS)
                .allowMainThreadQueries()
                .build()
            database.openHelper.writableDatabase
            database.close()
        }
    }

    @Test
    fun migrate13To14DropsKugouCacheAndAddsSyncTable() {
        helper.createDatabase(TEST_DB, 13).use { db ->
            insertLyricsCache(db, trackId = 1, provider = "LRCLIB")
            insertLyricsCache(db, trackId = 2, provider = "KUGOU")
        }

        helper.runMigrationsAndValidate(TEST_DB, 14, true, *AppDatabase.ALL_MIGRATIONS).use { db ->
            db.query("SELECT trackId FROM lyrics_cache").use {
                assertEquals(1, it.count)
                assertTrue(it.moveToFirst())
                assertEquals(1L, it.getLong(0))
            }
            assertEquals(0, count(db, "lyrics_sync"))
        }
    }

    @Test
    fun migrate14To15KeepsCorrections() {
        helper.createDatabase(TEST_DB, 14).use { db ->
            db.execSQL(
                "INSERT INTO lyrics_sync (trackId, provider, offsetMs, driftMsPerMin, title, artist, trackDurationMs, lyricsDurationMs, lastLineMs, updatedAt) " +
                    "VALUES (900001, 'LRCLIB', -250, 1000, 'Невалидный трек', 'Test Artist', 120000, 122000, 110000, 1700000000000)"
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 15, true, *AppDatabase.ALL_MIGRATIONS).use { db ->
            db.query("SELECT offsetMs, driftMsPerMin, title, lyricsDurationMs, updatedAt FROM lyrics_sync WHERE trackId = 900001").use {
                assertTrue(it.moveToFirst())
                assertEquals(-250L, it.getLong(0))
                assertEquals(1000L, it.getLong(1))
                assertEquals("Невалидный трек", it.getString(2))
                assertEquals(122000L, it.getLong(3))
                assertEquals(1700000000000L, it.getLong(4))
            }
        }
    }

    @Test
    fun migrate14To15RepairsIntermediateDevSchema() {
        helper.createDatabase(TEST_DB, 14).use { db ->
            db.execSQL("DROP TABLE lyrics_sync")
            db.execSQL(
                "CREATE TABLE lyrics_sync (trackId INTEGER NOT NULL, provider TEXT NOT NULL, offsetMs INTEGER NOT NULL, " +
                    "driftMsPerMin INTEGER NOT NULL, PRIMARY KEY(trackId, provider))"
            )
            db.execSQL("INSERT INTO lyrics_sync VALUES (1, 'LRCLIB', 300, 600)")
            db.execSQL("INSERT INTO lyrics_sync VALUES (1, 'KUGOU', 100, 100)")

            db.execSQL(
                "CREATE TABLE lyrics_cache_old (trackId INTEGER NOT NULL, provider TEXT NOT NULL, type TEXT NOT NULL, " +
                    "content TEXT, source TEXT, fetchedAt INTEGER NOT NULL, PRIMARY KEY(trackId, provider))"
            )
            db.execSQL("INSERT INTO lyrics_cache_old SELECT trackId, provider, type, content, source, fetchedAt FROM lyrics_cache")
            db.execSQL("DROP TABLE lyrics_cache")
            db.execSQL("ALTER TABLE lyrics_cache_old RENAME TO lyrics_cache")
            insertLyricsCache(db, trackId = 1, provider = "LRCLIB")
            insertLyricsCache(db, trackId = 1, provider = "KUGOU")
        }

        helper.runMigrationsAndValidate(TEST_DB, 15, true, *AppDatabase.ALL_MIGRATIONS).use { db ->
            db.query("SELECT provider, offsetMs, driftMsPerMin, title, updatedAt FROM lyrics_sync").use {
                assertEquals(1, it.count)
                assertTrue(it.moveToFirst())
                assertEquals("LRCLIB", it.getString(0))
                assertEquals(300L, it.getLong(1))
                assertEquals(600L, it.getLong(2))
                assertTrue(it.isNull(3))
                assertEquals(0L, it.getLong(4))
            }
            db.query("SELECT provider FROM lyrics_cache").use {
                assertEquals(1, it.count)
                assertTrue(it.moveToFirst())
                assertFalse(it.getString(0) == "KUGOU")
            }
        }
    }

    private fun insertLyricsCache(db: SupportSQLiteDatabase, trackId: Long, provider: String) {
        db.execSQL(
            "INSERT INTO lyrics_cache (trackId, provider, type, content, source, fetchedAt) VALUES (?, ?, 'SYNCED', '[00:01.00]line', NULL, 0)",
            arrayOf(trackId, provider),
        )
    }

    private fun count(db: SupportSQLiteDatabase, table: String): Int =
        db.query("SELECT COUNT(*) FROM $table").use {
            it.moveToFirst()
            it.getInt(0)
        }

    private companion object {
        const val TEST_DB = "migration-test.db"
        const val FIRST_TESTED_VERSION = 11
        const val LATEST_VERSION = 15
    }
}
