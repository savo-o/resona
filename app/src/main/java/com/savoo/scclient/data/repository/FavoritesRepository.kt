package com.savoo.scclient.data.repository

import com.savoo.scclient.auth.TokenStore
import com.savoo.scclient.data.local.FavoritesDao
import com.savoo.scclient.data.model.FavoriteTrack
import com.savoo.scclient.data.model.Track
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single place every screen's heart button goes through, so favoriting a track anywhere in the
 * app (Search, Home, Artist, Playlist, Player, Favorites) consistently also syncs to the user's
 * real SoundCloud likes when Online favorites is on - not just the local Room favorite.
 */
@Singleton
class FavoritesRepository @Inject constructor(
    private val favoritesDao: FavoritesDao,
    private val trackRepository: TrackRepository,
    private val tokenStore: TokenStore,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun toggleTrackFavorite(track: Track) {
        val wasFavorite = favoritesDao.isTrackFavoriteSync(track.id)
        if (wasFavorite) {
            favoritesDao.removeTrack(track.id)
        } else {
            favoritesDao.addTrack(
                FavoriteTrack(
                    trackId = track.id,
                    title = track.title,
                    username = track.user.username,
                    artworkUrl = track.artworkUrl,
                    durationMs = track.durationMs,
                    permalinkUrl = track.permalinkUrl,
                    userId = track.user.id,
                    userAvatarUrl = track.user.avatarUrl,
                )
            )
        }

        val onlineEnabled = settingsRepository.settings.first().onlineFavoritesEnabled
        android.util.Log.d(TAG, "toggleTrackFavorite(${track.id}): wasFavorite=$wasFavorite onlineEnabled=$onlineEnabled loggedIn=${tokenStore.isLoggedIn.value}")
        if (onlineEnabled && tokenStore.isLoggedIn.value) {
            runCatching {
                if (wasFavorite) trackRepository.unlikeTrack(track.id) else trackRepository.likeTrack(track.id)
            }.onSuccess {
                android.util.Log.d(TAG, "online sync OK for ${track.id} (wasFavorite=$wasFavorite)")
            }.onFailure { e ->
                android.util.Log.e(TAG, "online sync FAILED for ${track.id} (wasFavorite=$wasFavorite)", e)
            }
        }
    }

    companion object {
        private const val TAG = "FavoritesRepository"
    }
}
