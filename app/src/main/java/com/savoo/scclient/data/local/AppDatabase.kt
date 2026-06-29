package com.savoo.scclient.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.savoo.scclient.data.model.FavoriteArtist
import com.savoo.scclient.data.model.FavoritePlaylist
import com.savoo.scclient.data.model.FavoriteTrack

@Database(
    entities = [FavoriteTrack::class, FavoriteArtist::class, FavoritePlaylist::class],
    version = 2,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoritesDao(): FavoritesDao

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

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "scclient.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
