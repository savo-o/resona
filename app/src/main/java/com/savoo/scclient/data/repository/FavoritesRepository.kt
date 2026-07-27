package com.savoo.scclient.data.repository

import com.savoo.scclient.auth.TokenStore
import com.savoo.scclient.data.local.FavoritesDao
import com.savoo.scclient.data.model.FavoriteTrack
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.debug.DebugLog
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private const val SOURCE_LOCAL = "LOCAL"
private const val SOURCE_ONLINE = "ONLINE"
private const val SOURCE_BOTH = "BOTH"

/**
 * Single place every screen's heart button goes through, so favoriting a track anywhere in the
 * app (Search, Home, Artist, Playlist, Player, Favorites) consistently also syncs to the user's
 * real SoundCloud likes when Online favorites is on - not just the local Room favorite.
 *
 * Online likes are also mirrored into the same local `favorites` table (tagged via
 * [FavoriteTrack.source]) rather than kept as a separate in-memory list, so they show up
 * everywhere local favorites already do (e.g. Home's "From Your Favorites") and sort naturally
 * with everything else instead of always trailing at the end of the list.
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
        DebugLog.log(TAG, "toggleTrackFavorite(${track.id}): wasFavorite=$wasFavorite")
        if (wasFavorite) {
            favoritesDao.removeTrack(track.id)
            val onlineEnabled = settingsRepository.settings.first().onlineFavoritesEnabled
            if (onlineEnabled && tokenStore.isLoggedIn.value) {
                runCatching { trackRepository.unlikeTrack(track.id) }
                    .onSuccess { DebugLog.log(TAG, "online unlike OK for ${track.id}") }
                    .onFailure { e -> DebugLog.log(TAG, "online unlike FAILED for ${track.id}: $e") }
            }
        } else {
            addTrackFavorite(track)
        }
    }

    /** Adds a track as a local favorite, also pushing it to the user's real SoundCloud likes when
     * Online favorites is on. Used both by the heart button (via [toggleTrackFavorite]) and by
     * bulk-import flows (Telegram import, SoundCloud "import from profile", the JSON
     * import/export feature) so a batch-imported favorite ends up online too, not just local. */
    suspend fun addTrackFavorite(track: Track) {
        addTrackFavorite(track.toFavoriteTrack(source = SOURCE_LOCAL))
    }

    /** Same as [addTrackFavorite], for callers that already have a fully-built [FavoriteTrack]
     * (e.g. the JSON import/export feature and SoundCloud profile import, which fetch full
     * favorite records directly rather than generic [Track]s). */
    suspend fun addTrackFavorite(favorite: FavoriteTrack) {
        favoritesDao.addTrack(favorite.copy(source = SOURCE_LOCAL))
        DebugLog.log(TAG, "addTrackFavorite(${favorite.trackId}): added locally")

        val onlineEnabled = settingsRepository.settings.first().onlineFavoritesEnabled
        if (onlineEnabled && tokenStore.isLoggedIn.value) {
            runCatching { trackRepository.likeTrack(favorite.trackId) }
                .onSuccess {
                    favoritesDao.updateTrackSource(favorite.trackId, SOURCE_BOTH)
                    DebugLog.log(TAG, "online like OK for ${favorite.trackId}")
                }
                .onFailure { e -> DebugLog.log(TAG, "online like FAILED for ${favorite.trackId}: $e") }
        }
    }

    /** Reconciles the freshly-fetched set of real SoundCloud likes into the local favorites
     * table: new online likes get added (source=ONLINE), existing local favorites that are also
     * liked online get upgraded to BOTH, and rows no longer liked online get downgraded back to
     * LOCAL (if they were BOTH) or removed entirely (if they were ONLINE-only). */
    suspend fun syncOnlineLikes(onlineTracks: List<Track>) {
        DebugLog.log(TAG, "syncOnlineLikes: reconciling ${onlineTracks.size} online likes")
        val onlineIds = onlineTracks.map { it.id }.toSet()
        val existing = favoritesDao.getAllTracksSync().associateBy { it.trackId }

        for (track in onlineTracks) {
            val row = existing[track.id]
            when {
                row == null -> favoritesDao.addTrack(track.toFavoriteTrack(source = SOURCE_ONLINE))
                row.source == SOURCE_LOCAL -> favoritesDao.updateTrackSource(track.id, SOURCE_BOTH)
            }
        }

        for (row in existing.values) {
            if (row.trackId !in onlineIds) {
                when (row.source) {
                    SOURCE_ONLINE -> favoritesDao.removeTrack(row.trackId)
                    SOURCE_BOTH -> favoritesDao.updateTrackSource(row.trackId, SOURCE_LOCAL)
                }
            }
        }
    }

    private fun Track.toFavoriteTrack(source: String) = FavoriteTrack(
        trackId = id,
        title = title,
        username = user.username,
        artworkUrl = artworkUrl,
        durationMs = durationMs,
        permalinkUrl = permalinkUrl,
        userId = user.id,
        userAvatarUrl = user.avatarUrl,
        source = source,
    )

    companion object {
        private const val TAG = "FavoritesRepository"
    }
}
