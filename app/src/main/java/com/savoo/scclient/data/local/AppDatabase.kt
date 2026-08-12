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
import com.savoo.scclient.data.model.OfflineTrack
import com.savoo.scclient.data.model.PlayEvent
import com.savoo.scclient.data.model.TelegramImportRecord

@Database(
    entities = [FavoriteTrack::class, FavoriteArtist::class, FavoritePlaylist::class, OfflineTrack::class, TelegramImportRecord::class, PlayEvent::class, ExcludedMixArtist::class],
    version = 6,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoritesDao(): FavoritesDao
    abstract fun offlineDao(): OfflineDao
    abstract fun telegramImportDao(): TelegramImportDao
    abstract fun playHistoryDao(): PlayHistoryDao
    abstract fun excludedArtistDao(): ExcludedArtistDao

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
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
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

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "scclient.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()
    }
}
