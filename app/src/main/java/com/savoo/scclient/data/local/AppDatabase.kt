package com.savoo.scclient.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.savoo.scclient.data.model.ExcludedMixArtist
import com.savoo.scclient.data.model.FavoriteArtist
import com.savoo.scclient.data.model.FavoritePlaylist
import com.savoo.scclient.data.model.FavoriteTrack
import com.savoo.scclient.data.model.LyricsCacheEntity
import com.savoo.scclient.data.model.OfflineTrack
import com.savoo.scclient.data.model.PlayEvent
import com.savoo.scclient.data.model.TelegramImportRecord
import com.savoo.scclient.data.model.UnavailableTrackEntity

@Database(
    entities = [FavoriteTrack::class, FavoriteArtist::class, FavoritePlaylist::class, OfflineTrack::class, TelegramImportRecord::class, PlayEvent::class, ExcludedMixArtist::class, LyricsCacheEntity::class, UnavailableTrackEntity::class],
    version = 11,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoritesDao(): FavoritesDao
    abstract fun offlineDao(): OfflineDao
    abstract fun telegramImportDao(): TelegramImportDao
    abstract fun playHistoryDao(): PlayHistoryDao
    abstract fun excludedArtistDao(): ExcludedArtistDao
    abstract fun lyricsCacheDao(): LyricsCacheDao
    abstract fun unavailableTrackDao(): UnavailableTrackDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS favorite_artists (
                        artistId INTEGER NOT NULL PRIMARY KEY,
                        username TEXT NOT NULL,
                        fullName TEXT,
                        avatarUrl TEXT,
                        followersCount INTEGER,
                        permalinkUrl TEXT,
                        addedAt INTEGER NOT NULL
                    )
                """)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS favorite_playlists (
                        playlistId INTEGER NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        artworkUrl TEXT,
                        trackCount INTEGER NOT NULL,
                        username TEXT NOT NULL,
                        permalinkUrl TEXT,
                        addedAt INTEGER NOT NULL
                    )
                """)
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS offline_tracks (
                        trackId INTEGER NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        username TEXT NOT NULL,
                        artworkUrl TEXT,
                        durationMs INTEGER NOT NULL,
                        permalinkUrl TEXT,
                        userId INTEGER NOT NULL,
                        userAvatarUrl TEXT,
                        localPath TEXT NOT NULL,
                        savedAt INTEGER NOT NULL,
                        fileSizeBytes INTEGER NOT NULL
                    )
                """)
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS telegram_import_records (
                        chatId INTEGER NOT NULL,
                        messageId INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        performer TEXT,
                        status TEXT NOT NULL,
                        matchedTrackId INTEGER,
                        reason TEXT,
                        importedAt INTEGER NOT NULL,
                        PRIMARY KEY(chatId, messageId)
                    )
                """)
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE favorites ADD COLUMN source TEXT NOT NULL DEFAULT 'LOCAL'")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS play_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        trackId INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        artistId INTEGER NOT NULL,
                        artistName TEXT NOT NULL,
                        artworkUrl TEXT,
                        msPlayed INTEGER NOT NULL,
                        playedAt INTEGER NOT NULL
                    )
                """)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS excluded_mix_artists (
                        artistId INTEGER NOT NULL PRIMARY KEY,
                        username TEXT NOT NULL,
                        excludedAt INTEGER NOT NULL
                    )
                """)
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS play_history_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        trackId INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        artistId INTEGER NOT NULL,
                        artistName TEXT NOT NULL,
                        artworkUrl TEXT,
                        msPlayed INTEGER NOT NULL,
                        playedAt INTEGER NOT NULL
                    )
                """)
                db.execSQL("""
                    INSERT INTO play_history_new (id, trackId, title, artistId, artistName, artworkUrl, msPlayed, playedAt)
                    SELECT id, trackId, title, artistId, artistName, artworkUrl, msPlayed, playedAt FROM play_history
                """)
                db.execSQL("DROP TABLE play_history")
                db.execSQL("ALTER TABLE play_history_new RENAME TO play_history")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE offline_tracks ADD COLUMN sourceFolderUri TEXT")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE play_history ADD COLUMN genre TEXT")
                db.execSQL("ALTER TABLE favorites ADD COLUMN genre TEXT")
                db.execSQL("ALTER TABLE offline_tracks ADD COLUMN genre TEXT")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS lyrics_cache (
                        trackId INTEGER NOT NULL,
                        provider TEXT NOT NULL,
                        type TEXT NOT NULL,
                        content TEXT,
                        source TEXT,
                        fetchedAt INTEGER NOT NULL,
                        PRIMARY KEY(trackId, provider)
                    )
                """)
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS unavailable_tracks (
                        trackId INTEGER NOT NULL PRIMARY KEY,
                        reason TEXT NOT NULL,
                        markedAt INTEGER NOT NULL
                    )
                """)
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "scclient.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                .build()
    }
}
