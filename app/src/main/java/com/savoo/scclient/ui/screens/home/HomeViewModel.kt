package com.savoo.scclient.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.savoo.scclient.auth.TokenStore
import com.savoo.scclient.data.local.FavoritesDao
import com.savoo.scclient.data.model.FavoriteArtist
import com.savoo.scclient.data.model.FavoriteTrack
import com.savoo.scclient.data.model.OfflineTrack
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.data.model.User
import com.savoo.scclient.data.repository.TrackRepository
import com.savoo.scclient.player.OfflineTrackManager
import com.savoo.scclient.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private fun FavoriteTrack.toTrack() = Track(
    id = trackId,
    title = title,
    durationMs = durationMs,
    artworkUrl = artworkUrl,
    user = User(id = userId, username = username, avatarUrl = userAvatarUrl),
    permalinkUrl = permalinkUrl,
)

private fun OfflineTrack.toTrack() = Track(
    id = trackId,
    title = title,
    durationMs = durationMs,
    artworkUrl = artworkUrl,
    user = User(id = userId, username = username, avatarUrl = userAvatarUrl),
    permalinkUrl = permalinkUrl,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    val playerController: PlayerController,
    favoritesDao: FavoritesDao,
    offlineTrackManager: OfflineTrackManager,
    private val tokenStore: TokenStore,
    private val trackRepository: TrackRepository,
) : ViewModel() {

    private val _user = MutableStateFlow<User?>(null)
    val user = _user.asStateFlow()

    val favoriteTracks = favoritesDao.getAllTracks()
        .map { list -> list.map { it.toTrack() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteArtists: kotlinx.coroutines.flow.StateFlow<List<FavoriteArtist>> = favoritesDao.getAllArtists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val offlineTracks = offlineTrackManager.getAllOfflineTracks()
        .map { list -> list.map { it.toTrack() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _mixTracks = MutableStateFlow<List<Track>>(emptyList())
    val mixTracks = _mixTracks.asStateFlow()

    // True until the mix has been built at least once - lets the UI show a loading placeholder
    // instead of the "nothing here yet" empty state while favorites/discovery are still loading.
    private val _isMixLoading = MutableStateFlow(true)
    val isMixLoading = _isMixLoading.asStateFlow()

    init {
        viewModelScope.launch {
            tokenStore.isLoggedIn.collect { loggedIn ->
                if (loggedIn) {
                    runCatching { trackRepository.getMe() }.onSuccess { _user.value = it }
                } else {
                    _user.value = null
                }
            }
        }

        // Rebuilds only when favorites/offline/artists actually change (not on every playback tick,
        // e.g. from recentTracks) - otherwise the mix (and its hero artwork) reshuffled under the
        // user's feet on every track change, which is what made the play button feel unpredictable.
        //
        // The debounce matters: favoriteTracks/offlineTracks/favoriteArtists are StateFlows that
        // start at emptyList() (their stateIn initial value) and only pick up the real Room data a
        // beat later, so combine() fires once immediately with everything empty. Without the
        // debounce that transient near-empty mix gets published and is playable - tap Play in that
        // window and you get a tiny "mix" (sometimes just the one track that happened to be in
        // recentTracks) that then loops forever, which is what "sometimes just one track" was.
        viewModelScope.launch {
            combine(favoriteTracks, offlineTracks, favoriteArtists) { fav, off, artists -> Triple(fav, off, artists) }
                .debounce(400)
                .collectLatest { (fav, off, artists) ->
                    _mixTracks.value = buildMix(fav, off, artists)
                    _isMixLoading.value = false
                }
        }
    }

    private suspend fun buildMix(
        favorites: List<Track>,
        offline: List<Track>,
        artists: List<FavoriteArtist>,
    ): List<Track> = withContext(Dispatchers.IO) {
        // Deliberately favorites + offline only, no recentTracks: the mix is meant to be favorites,
        // downloads, and favorite-artist discovery - pulling in whatever you happened to play most
        // recently blurred that into "why is this random recent thing in my mix".
        val localPool = (favorites + offline).distinctBy { it.id }
        val knownTrackIds = localPool.mapTo(HashSet()) { it.id }
        val knownArtistIds = (artists.map { it.artistId } + favorites.map { it.user.id } + offline.map { it.user.id })
            .filter { it != 0L }
            .distinct()
        android.util.Log.d(TAG, "buildMix: favorites=${favorites.size} offline=${offline.size} favoriteArtists=${artists.size} knownArtistIds=${knownArtistIds.size}")

        // "Discovery": pull a few tracks from artists the user already likes that aren't in their
        // library yet, so the mix isn't just a reshuffle of songs they've already heard. The mix is
        // meant to feel like a near-endless radio (it loops, see playMix), so this pulls from enough
        // artists/tracks that a full loop doesn't feel like the same handful of songs on repeat.
        val discovery = knownArtistIds.shuffled().take(6).flatMap { artistId ->
            val artistTracks = runCatching { trackRepository.getUserTracksPage(artistId) }
                .onFailure { android.util.Log.w(TAG, "buildMix: getUserTracksPage($artistId) failed: $it") }
                .getOrDefault(emptyList())
            android.util.Log.d(TAG, "buildMix: artist=$artistId returned ${artistTracks.size} tracks")
            artistTracks
                .filterNot { it.id in knownTrackIds }
                .shuffled()
                .take(5)
        }.distinctBy { it.id }.take(25)
        android.util.Log.d(TAG, "buildMix: discovery=${discovery.size} localPool=${localPool.size}")

        val pool = (localPool.shuffled() + discovery).distinctBy { it.id }
        if (pool.isEmpty()) emptyList() else pool.shuffled()
    }

    companion object {
        private const val TAG = "HomeViewModel"

        // Lets the hero Play button tell "the mix is the queue that's actually active" apart from
        // "the current track merely happens to also be somewhere in the mix's pool" - those aren't the
        // same thing (e.g. a track played from Jump Back In can also be part of the mix), and
        // confusing the two made the button silently toggle pause/resume on a leftover small queue
        // instead of starting the real mix.
        const val MIX_QUEUE_TAG = "home_mix"
    }

    fun playMix(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        // Re-shuffled fresh every time the mix is (re)started, not just when the underlying pool
        // changes: mixTracks itself is a stable snapshot (see buildMix), so starting the mix, playing
        // something else, then starting the mix again used to hand back the exact same order every
        // time - felt like "the same mix" rather than a different session of the same pool. Shuffling
        // a copy here doesn't affect what the hero artwork is showing (that reads mixTracks directly).
        playerController.playQueue(tracks.shuffled(), repeatAll = true, tag = MIX_QUEUE_TAG)
    }

    fun playFrom(tracks: List<Track>, trackId: Long) {
        val idx = tracks.indexOfFirst { it.id == trackId }
        if (idx < 0) return
        // Same reasoning as playMix: these are Home's own short lists (recent/favorites/offline), and
        // stopping dead at the end of an 8-track "recent" queue reads as broken, not intentional -
        // Home playback should keep going regardless of which row you tapped into.
        playerController.playQueue(tracks, idx, repeatAll = true)
    }
}
