package com.savoo.scclient.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.savoo.scclient.auth.TokenStore
import com.savoo.scclient.data.local.ExcludedArtistDao
import com.savoo.scclient.data.local.FavoritesDao
import com.savoo.scclient.data.model.ExcludedMixArtist
import com.savoo.scclient.data.model.FavoriteArtist
import com.savoo.scclient.data.model.FavoritePlaylist
import com.savoo.scclient.data.model.FavoriteTrack
import com.savoo.scclient.data.model.OfflineTrack
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.data.model.User
import com.savoo.scclient.data.repository.FavoritesRepository
import com.savoo.scclient.data.repository.SettingsRepository
import com.savoo.scclient.data.repository.TrackRepository
import com.savoo.scclient.player.OfflineTrackManager
import com.savoo.scclient.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
    private val excludedArtistDao: ExcludedArtistDao,
    private val tokenStore: TokenStore,
    private val trackRepository: TrackRepository,
    private val favoritesRepository: FavoritesRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _user = MutableStateFlow<User?>(null)
    val user = _user.asStateFlow()

    val favoriteTracks = favoritesDao.getAllTracks()
        .map { list -> list.map { it.toTrack() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteArtists: kotlinx.coroutines.flow.StateFlow<List<FavoriteArtist>> = favoritesDao.getAllArtists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoritePlaylists: kotlinx.coroutines.flow.StateFlow<List<FavoritePlaylist>> = favoritesDao.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val offlineTracks = offlineTrackManager.getAllOfflineTracks()
        .map { list -> list.map { it.toTrack() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val excludedArtists: kotlinx.coroutines.flow.StateFlow<List<ExcludedMixArtist>> = excludedArtistDao.excludedArtists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mixSelectableArtists: kotlinx.coroutines.flow.StateFlow<List<FavoriteArtist>> =
        combine(favoriteArtists, favoriteTracks, offlineTracks) { artists, favTracks, offTracks ->
            val byId = LinkedHashMap<Long, FavoriteArtist>()
            artists.forEach { byId[it.artistId] = it }
            (favTracks + offTracks).forEach { track ->
                if (track.user.id != 0L && track.user.id !in byId) {
                    byId[track.user.id] = FavoriteArtist(
                        artistId = track.user.id,
                        username = track.user.username,
                        fullName = null,
                        avatarUrl = track.user.avatarUrl,
                        followersCount = null,
                        permalinkUrl = null,
                        addedAt = 0L,
                    )
                }
            }
            byId.values.toList()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mixDiscoveryEnabled = settingsRepository.settings.map { it.mixDiscoveryEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _preferredArtistIds = MutableStateFlow<Set<Long>>(emptySet())
    val preferredArtistIds = _preferredArtistIds.asStateFlow()

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

        viewModelScope.launch {
            val onlineFavoritesEnabled = settingsRepository.settings.map { it.onlineFavoritesEnabled }
            combine(tokenStore.isLoggedIn, onlineFavoritesEnabled) { loggedIn, enabled -> loggedIn && enabled }
                .distinctUntilChanged()
                .collect { active ->
                    if (active) {
                        runCatching { trackRepository.getLikedTracks() }
                            .onSuccess {
                                com.savoo.scclient.debug.DebugLog.log(TAG, "synced ${it.size} online likes on Home")
                                favoritesRepository.syncOnlineLikes(it)
                            }
                            .onFailure { e -> com.savoo.scclient.debug.DebugLog.log(TAG, "online likes sync failed: $e") }
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
            val mix4Flow = combine(favoriteTracks, offlineTracks, favoriteArtists, excludedArtists) { fav, off, artists, excluded -> Mix4(fav, off, artists, excluded) }
            combine(mix4Flow, preferredArtistIds, mixDiscoveryEnabled) { mix4, preferred, discovery -> Mix6(mix4, preferred, discovery) }
                .debounce(400)
                .collectLatest { (mix4, preferred, discovery) ->
                    _mixTracks.value = buildMix(mix4.fav, mix4.off, mix4.artists, mix4.excluded, preferred, discovery)
                    _isMixLoading.value = false
                }
        }
    }

    private data class Mix4(
        val fav: List<Track>,
        val off: List<Track>,
        val artists: List<FavoriteArtist>,
        val excluded: List<ExcludedMixArtist>,
    )

    private data class Mix6(
        val mix4: Mix4,
        val preferred: Set<Long>,
        val discoveryEnabled: Boolean,
    )

    private suspend fun buildMix(
        favorites: List<Track>,
        offline: List<Track>,
        artists: List<FavoriteArtist>,
        excludedArtists: List<ExcludedMixArtist>,
        preferredArtistIds: Set<Long>,
        discoveryEnabled: Boolean,
    ): List<Track> = withContext(Dispatchers.IO) {
        val excludedArtistIds = excludedArtists.mapTo(HashSet()) { it.artistId }
        val excludedNamesLower = excludedArtists.map { it.username.trim().lowercase() }.filter { it.isNotEmpty() }
        fun isExcluded(track: Track): Boolean =
            track.user.id in excludedArtistIds || excludedNamesLower.any { track.title.lowercase().contains(it) }

        // Deliberately favorites + offline only, no recentTracks: the mix is meant to be favorites,
        // downloads, and favorite-artist discovery - pulling in whatever you happened to play most
        // recently blurred that into "why is this random recent thing in my mix".
        val localPool = (favorites + offline).distinctBy { it.id }.filterNot { isExcluded(it) }
        val knownTrackIds = localPool.mapTo(HashSet()) { it.id }
        val knownArtistIds = (artists.map { it.artistId } + favorites.map { it.user.id } + offline.map { it.user.id })
            .filter { it != 0L && it !in excludedArtistIds }
            .distinct()
        android.util.Log.d(TAG, "buildMix: favorites=${favorites.size} offline=${offline.size} favoriteArtists=${artists.size} knownArtistIds=${knownArtistIds.size} excluded=${excludedArtistIds.size} preferred=${preferredArtistIds.size} discovery=$discoveryEnabled")

        if (!discoveryEnabled) {
            return@withContext if (localPool.isEmpty()) emptyList() else localPool.shuffled()
        }

        // "Discovery": pull a few tracks from artists the user already likes that aren't in their
        // library yet, so the mix isn't just a reshuffle of songs they've already heard. The mix is
        // meant to feel like a near-endless radio (it loops, see playMix), so this pulls from enough
        // artists/tracks that a full loop doesn't feel like the same handful of songs on repeat.
        val discoveryArtistPool = if (preferredArtistIds.isNotEmpty()) {
            preferredArtistIds.filter { it !in excludedArtistIds }
        } else {
            knownArtistIds
        }
        // Fetched in parallel, each capped at ARTIST_FETCH_TIMEOUT_MS: these are sequential API calls
        // to a third party (SoundCloud), and one slow/stuck request used to stall mix generation for
        // as long as that single call took (seen in the wild taking 10+ seconds) since they ran one
        // after another. A timed-out artist just contributes no discovery tracks this round.
        val discovery = coroutineScope {
            discoveryArtistPool.shuffled().take(8).map { artistId ->
                async {
                    val artistTracks = runCatching {
                        withTimeoutOrNull(ARTIST_FETCH_TIMEOUT_MS) { trackRepository.getUserTracksPage(artistId) }
                    }
                        .onFailure { android.util.Log.w(TAG, "buildMix: getUserTracksPage($artistId) failed: $it") }
                        .getOrNull()
                        .orEmpty()
                    android.util.Log.d(TAG, "buildMix: artist=$artistId returned ${artistTracks.size} tracks")
                    artistTracks
                        .filterNot { it.id in knownTrackIds }
                        .shuffled()
                        .take(5)
                }
            }.awaitAll()
        }.flatten().distinctBy { it.id }.take(30)
        android.util.Log.d(TAG, "buildMix: discovery=${discovery.size} localPool=${localPool.size}")

        val pool = (localPool.shuffled() + discovery).distinctBy { it.id }
        if (pool.isEmpty()) emptyList() else pool.shuffled()
    }

    companion object {
        private const val TAG = "HomeViewModel"
        private const val ARTIST_FETCH_TIMEOUT_MS = 5000L

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

    fun playFrom(tracks: List<Track>, trackId: Long, tag: String? = null) {
        val idx = tracks.indexOfFirst { it.id == trackId }
        if (idx < 0) return
        // Same reasoning as playMix: these are Home's own short lists (recent/favorites/offline), and
        // stopping dead at the end of an 8-track "recent" queue reads as broken, not intentional -
        // Home playback should keep going regardless of which row you tapped into.
        playerController.playQueue(tracks, idx, repeatAll = true, tag = tag)
    }

    fun togglePreferredArtist(artistId: Long) {
        _preferredArtistIds.update { if (artistId in it) it - artistId else it + artistId }
    }

    fun setMixDiscoveryEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setMixDiscoveryEnabled(enabled) }
    }

    fun unexcludeArtist(artistId: Long) {
        viewModelScope.launch { excludedArtistDao.unexclude(artistId) }
    }

    fun applyMixPreferencesAndPlay() {
        viewModelScope.launch {
            val fresh = buildMix(
                favoriteTracks.value,
                offlineTracks.value,
                favoriteArtists.value,
                excludedArtists.value,
                preferredArtistIds.value,
                mixDiscoveryEnabled.value,
            )
            _mixTracks.value = fresh
            playMix(fresh)
        }
    }
}
