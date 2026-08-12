package com.savoo.scclient.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.savoo.scclient.data.model.ExcludedMixArtist
import kotlinx.coroutines.flow.Flow

@Dao
interface ExcludedArtistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun exclude(entry: ExcludedMixArtist)

    @Query("SELECT * FROM excluded_mix_artists")
    fun excludedArtists(): Flow<List<ExcludedMixArtist>>

    @Query("DELETE FROM excluded_mix_artists WHERE artistId = :artistId")
    suspend fun unexclude(artistId: Long)
}
