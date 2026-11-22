package com.savoo.scclient.ui.screens.artist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.savoo.scclient.R
import com.savoo.scclient.data.model.ProfileFeedItem
import com.savoo.scclient.data.model.User
import com.savoo.scclient.data.repository.TrackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ProfileTab(val title: Int) {
    OVERVIEW(R.string.profile_overview), REPOSTS(R.string.profile_reposts), LIKES(R.string.profile_likes),
    FOLLOWERS(R.string.profile_followers), FOLLOWING(R.string.profile_following)
}

data class ProfileLibraryState(
    val tab: ProfileTab = ProfileTab.OVERVIEW,
    val feed: List<ProfileFeedItem> = emptyList(),
    val people: List<User> = emptyList(),
    val loading: Boolean = false,
    val failed: Boolean = false,
    val next: String? = null,
)

data class ProfileRelationship(val viewerId: Long? = null, val following: Boolean? = null, val busy: Boolean = false, val failed: Boolean = false, val followersDelta: Long = 0L)

@HiltViewModel
class ProfileLibraryViewModel @Inject constructor(
    private val repository: TrackRepository,
    private val tokens: com.savoo.scclient.auth.TokenStore,
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileLibraryState())
    val state = _state.asStateFlow()
    private val _relationship = MutableStateFlow(ProfileRelationship())
    val relationship = _relationship.asStateFlow()
    private var userId = 0L

    private fun loadRelationship(id: Long) {
        _relationship.value = ProfileRelationship()
        if (tokens.accessToken == null) return
        viewModelScope.launch {
            try {
                val viewer = repository.currentUserId() ?: return@launch
                val following = if (viewer == id) false else repository.isFollowing(viewer, id)
                if (userId == id) _relationship.value = ProfileRelationship(viewer, following)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (userId == id) _relationship.value = ProfileRelationship(failed = true)
            }
        }
    }

    fun toggleFollow() {
        val before = _relationship.value
        val viewer = before.viewerId ?: return
        val following = before.following ?: return
        if (before.busy || viewer == userId) return
        val target = userId
        _relationship.value = before.copy(busy = true, failed = false)
        viewModelScope.launch {
            try {
                repository.setFollowing(viewer, target, !following)
                if (userId == target) _relationship.value = before.copy(following = !following, followersDelta = before.followersDelta + if (following) -1L else 1L)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (userId == target) _relationship.value = before.copy(failed = true)
            }
        }
    }
    private var job: Job? = null

    fun select(id: Long, tab: ProfileTab) {
        if (userId == id && _state.value.tab == tab) return
        job?.cancel()
        val changedUser = userId != id
        userId = id
        if (changedUser) loadRelationship(id)
        _state.value = ProfileLibraryState(tab = tab)
        if (tab != ProfileTab.OVERVIEW) load()
    }

    fun load() {
        if (_state.value.loading || _state.value.tab == ProfileTab.OVERVIEW) return
        val before = _state.value
        val id = userId
        _state.value = before.copy(loading = true, failed = false)
        job = viewModelScope.launch {
            try {
                val result = when (before.tab) {
                    ProfileTab.FOLLOWERS, ProfileTab.FOLLOWING -> {
                        val page = repository.getProfilePeople(id, before.tab == ProfileTab.FOLLOWING, before.next)
                        before.copy(people = (before.people + page.items).distinctBy { it.id }, next = page.nextHref?.takeIf { it != before.next })
                    }
                    else -> {
                        val page = repository.getProfileFeed(id, before.tab == ProfileTab.LIKES, before.next)
                        before.copy(feed = (before.feed + page.items).filter { it.track != null || it.playlist != null }
                            .distinctBy { it.track?.let { t -> "track:${t.id}" } ?: "playlist:${it.playlist?.id}" }, next = page.nextHref?.takeIf { it != before.next })
                    }
                }
                _state.value = result.copy(loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.value = before.copy(failed = true, loading = false)
            }
        }
    }
}

@Composable
fun ProfileTabs(selected: ProfileTab, onSelect: (ProfileTab) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(ProfileTab.entries) { tab ->
            FilterChip(selected = tab == selected, onClick = { onSelect(tab) }, label = { Text(stringResource(tab.title)) }, shape = RoundedCornerShape(50))
        }
    }
}

@Composable
fun ProfileStats(user: User, onSelect: (ProfileTab) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Triple(user.followersCount, ProfileTab.FOLLOWERS, R.string.profile_followers),
            Triple(user.followingsCount, ProfileTab.FOLLOWING, R.string.profile_following),
            Triple(user.likesCount, ProfileTab.LIKES, R.string.profile_likes)).forEach { (count, tab, title) ->
            Surface(onClick = { onSelect(tab) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 16.dp)) {
                    Text(count?.let { java.text.NumberFormat.getIntegerInstance().format(it) } ?: "—", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun ProfileFollowButton(relationship: ProfileRelationship, userId: Long, onToggle: () -> Unit) {
    if (relationship.viewerId == null || relationship.viewerId == userId || relationship.following == null) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        FilledTonalButton(onClick = onToggle, enabled = !relationship.busy, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text(stringResource(if (relationship.following) R.string.profile_following_action else R.string.profile_follow))
        }
        if (relationship.failed) Text(stringResource(R.string.profile_follow_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
fun ProfilePlaylistRow(playlist: com.savoo.scclient.data.model.Playlist, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            coil.compose.AsyncImage(model = playlist.artworkUrl ?: playlist.tracks?.firstNotNullOfOrNull { it.artworkUrl }, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(68.dp).then(Modifier.clip(RoundedCornerShape(16.dp))))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(playlist.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(playlist.user.username, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Text(stringResource(R.string.artist_tracks_count, playlist.trackCount), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
