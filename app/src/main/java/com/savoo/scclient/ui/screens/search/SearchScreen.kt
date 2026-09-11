package com.savoo.scclient.ui.screens.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import com.savoo.scclient.R
import com.savoo.scclient.data.model.restrictionReason
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.ui.components.AlbumRow
import com.savoo.scclient.ui.components.ArtistRow
import com.savoo.scclient.ui.components.ExpressivePullToRefreshBox
import com.savoo.scclient.ui.components.TrackActionsSheet
import com.savoo.scclient.ui.components.TrackRow
import com.savoo.scclient.ui.haptics.rememberHapticTick

private fun iconFor(tab: SearchTab): ImageVector = when (tab) {
    SearchTab.TRACKS -> Icons.Filled.MusicNote
    SearchTab.ARTISTS -> Icons.Filled.Person
    SearchTab.ALBUMS -> Icons.Filled.Album
}

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchScreen(
    onArtistClick: (Long) -> Unit = {},
    onPlaylistClick: (Long) -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val playerState by viewModel.playerController.state.collectAsState()
    val unavailableReasons by viewModel.playerController.unavailableReasons.collectAsState()
    val history by viewModel.history.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val haptic = rememberHapticTick()
    var actionsTrack by remember { mutableStateOf<Track?>(null) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.navEvent.collect { event ->
            when (event) {
                is SearchNavEvent.Artist -> onArtistClick(event.userId)
                is SearchNavEvent.Playlist -> onPlaylistClick(event.playlistId)
            }
        }
    }

    Scaffold { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                Text(
                    text = stringResource(R.string.nav_search),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )

                SearchBarDefaults.InputField(
                    query = state.query,
                    onQueryChange = viewModel::onQueryChange,
                    onSearch = { viewModel.onQuerySubmit() },
                    expanded = false,
                    onExpandedChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = {
                        val hasQuery = state.query.isNotEmpty()
                        val rotation by animateFloatAsState(
                            targetValue = if (hasQuery) 180f else 0f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                            label = "searchIconRotation",
                        )
                        IconButton(
                            onClick = { if (hasQuery) { haptic(); viewModel.onQueryChange("") } },
                            enabled = hasQuery,
                        ) {
                            Icon(
                                imageVector = if (rotation < 90f) Icons.Filled.Search else Icons.Filled.Clear,
                                contentDescription = if (hasQuery) stringResource(R.string.search_clear) else null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.graphicsLayer { rotationZ = rotation },
                            )
                        }
                    },
                )

                Spacer(Modifier.height(8.dp))

                if (state.query.isBlank() && history.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.search_history),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            stringResource(R.string.search_clear_history),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { haptic(); viewModel.clearHistory() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(history, key = { it }) { query ->
                            HistoryRow(
                                query = query,
                                onClick = { haptic(); viewModel.selectFromHistory(query) },
                                onRemove = { haptic(); viewModel.removeHistoryItem(query) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                } else {
                    val tabs = SearchTab.entries
                    val selectedIndex = tabs.indexOf(state.activeTab).coerceAtLeast(0)
                    ButtonGroup(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            val shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                tabs.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            }
                            ToggleButton(
                                checked = selectedIndex == index,
                                onCheckedChange = { checked -> if (checked) { haptic(); viewModel.onTabChange(tab) } },
                                modifier = Modifier.weight(1f),
                                shapes = shapes,
                            ) {
                                Icon(
                                    iconFor(tab),
                                    contentDescription = null,
                                    modifier = Modifier.size(ToggleButtonDefaults.IconSize),
                                )
                                Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing))
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    ExpressivePullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = { haptic(); viewModel.refreshResults() },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                    when {
                        state.isLoading || state.isResolvingLink -> PullableCenteredContent {
                            LoadingIndicator()
                        }
                        state.error != null -> PullableCenteredContent {
                            SearchErrorState(error = state.error!!)
                        }
                        state.query.isNotBlank() && state.tracks.isEmpty() && state.artists.isEmpty() && state.albums.isEmpty() -> PullableCenteredContent {
                            Text(
                                stringResource(R.string.search_nothing_found),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        state.query.isBlank() && history.isEmpty() -> SearchEmptyState(
                            modifier = Modifier.fillMaxSize(),
                        )
                        else -> AnimatedContent(
                            targetState = state.activeTab,
                            transitionSpec = {
                                fadeIn(spring(dampingRatio = 1f, stiffness = Spring.StiffnessLow)) togetherWith
                                    fadeOut(spring(dampingRatio = 1f, stiffness = Spring.StiffnessLow))
                            },
                            label = "tabContent"
                        ) { tab ->
                            when (tab) {
                            SearchTab.TRACKS -> LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(state.tracks, key = { it.id }) { track ->
                                    val isFav by viewModel.isFavoriteFlow(track.id).collectAsState(initial = false)
                                    val isCurrentTrack = playerState.currentTrack?.id == track.id
                                    TrackRow(
                                        track = track,
                                        onClick = { viewModel.playTrack(track) },
                                        isFavorite = isFav,
                                        isLoading = playerState.loadingTrackId == track.id,
                                        isPlaying = playerState.isPlaying && isCurrentTrack,
                                        onToggleFavorite = { viewModel.toggleFavorite(track) },
                                        onTogglePlayPause = {
                                            if (isCurrentTrack) viewModel.playerController.togglePlayPause()
                                            else viewModel.playTrack(track)
                                        },
                                        unavailableReason = unavailableReasons[track.id] ?: track.restrictionReason(),
                                        onLongPress = { actionsTrack = track },
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                                item {
                                    SearchLoadMoreFooter(
                                        visible = state.nextTracksHref != null,
                                        loadedCount = state.tracks.size,
                                        onLoadMore = { viewModel.loadMore() },
                                    )
                                }
                                }
                                SearchTab.ARTISTS -> LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(state.artists, key = { it.id }) { user ->
                                        ArtistRow(
                                            user = user,
                                            onClick = { onArtistClick(user.id) },
                                            modifier = Modifier.animateItem(),
                                        )
                                    }
                                item {
                                    SearchLoadMoreFooter(
                                        visible = state.nextArtistsHref != null,
                                        loadedCount = state.artists.size,
                                        onLoadMore = { viewModel.loadMore() },
                                    )
                                }
                                }
                                SearchTab.ALBUMS -> LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(state.albums, key = { it.id }) { playlist ->
                                        AlbumRow(
                                            playlist = playlist,
                                            onClick = { onPlaylistClick(playlist.id) },
                                            modifier = Modifier.animateItem(),
                                        )
                                    }
                                item {
                                    SearchLoadMoreFooter(
                                        visible = state.nextAlbumsHref != null,
                                        loadedCount = state.albums.size,
                                        onLoadMore = { viewModel.loadMore() },
                                    )
                                }
                                }
                            }
                        }
                    }
                    }
                }
            }
        }
    }

    actionsTrack?.let { track ->
        TrackActionsSheet(
            track = track,
            onDismiss = { actionsTrack = null },
            onPlayNext = { viewModel.playerController.playNext(listOf(track)) },
            onAddToQueue = { viewModel.playerController.addToQueue(listOf(track)) },
        )
    }
}

// Pull to refresh only reacts to nested scroll, so a plain centered Box would leave these states
// unrefreshable - the one-item list keeps the centering and makes the gesture work.
@Composable
private fun PullableCenteredContent(content: @Composable () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Box(
                modifier = Modifier.fillParentMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SearchErrorState(error: SearchError, modifier: Modifier = Modifier) {
    val icon = when (error.kind) {
        SearchErrorKind.NETWORK -> Icons.Filled.CloudOff
        SearchErrorKind.AUTH -> Icons.Filled.Key
        SearchErrorKind.NOT_FOUND, SearchErrorKind.TRACK_GONE -> Icons.Filled.SearchOff
        SearchErrorKind.SERVER -> Icons.Filled.Dns
        SearchErrorKind.LINK -> Icons.Filled.LinkOff
        SearchErrorKind.UNKNOWN -> Icons.Outlined.ErrorOutline
    }
    val titleRes = when (error.kind) {
        SearchErrorKind.NETWORK -> R.string.search_error_network_title
        SearchErrorKind.AUTH -> R.string.search_error_auth_title
        SearchErrorKind.NOT_FOUND -> R.string.search_error_not_found_title
        SearchErrorKind.TRACK_GONE -> R.string.search_error_track_gone_title
        SearchErrorKind.SERVER -> R.string.search_error_server_title
        SearchErrorKind.LINK -> R.string.search_error_link_title
        SearchErrorKind.UNKNOWN -> R.string.search_error_unknown_title
    }
    val descRes = when (error.kind) {
        SearchErrorKind.NETWORK -> R.string.search_error_network_desc
        SearchErrorKind.AUTH -> R.string.search_error_auth_desc
        SearchErrorKind.NOT_FOUND -> R.string.search_error_not_found_desc
        SearchErrorKind.TRACK_GONE -> R.string.search_error_track_gone_desc
        SearchErrorKind.SERVER -> R.string.search_error_server_desc
        SearchErrorKind.LINK -> R.string.search_error_link_desc
        SearchErrorKind.UNKNOWN -> R.string.search_error_unknown_desc
    }
    val hints = when (error.kind) {
        SearchErrorKind.NETWORK -> listOf(
            Icons.Filled.WifiOff to R.string.search_error_hint_check_connection,
            Icons.Filled.Refresh to R.string.search_error_hint_pull_to_retry,
        )
        SearchErrorKind.AUTH -> listOf(
            Icons.Filled.Key to R.string.search_error_hint_client_id,
            Icons.Filled.Refresh to R.string.search_error_hint_pull_to_retry,
        )
        SearchErrorKind.SERVER -> listOf(
            Icons.Filled.Schedule to R.string.search_error_hint_wait,
            Icons.Filled.Refresh to R.string.search_error_hint_pull_to_retry,
        )
        SearchErrorKind.NOT_FOUND -> listOf(
            Icons.Filled.Tune to R.string.search_error_hint_change_query,
        )
        SearchErrorKind.TRACK_GONE -> listOf(
            Icons.Filled.Tune to R.string.search_error_hint_search_by_name,
        )
        SearchErrorKind.LINK -> listOf(
            Icons.Filled.Tune to R.string.search_error_hint_check_link,
        )
        SearchErrorKind.UNKNOWN -> listOf(
            Icons.Filled.Refresh to R.string.search_error_hint_pull_to_retry,
        )
    }

    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(72.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(descRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        val detail = error.detail
        if (!detail.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(24.dp))
        hints.forEachIndexed { index, (hintIcon, hintRes) ->
            if (index > 0) Spacer(Modifier.height(12.dp))
            SearchEmptyHint(hintIcon, stringResource(hintRes))
        }
    }
}

@Composable
private fun SearchEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(72.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.TravelExplore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.search_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.search_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        SearchEmptyHint(Icons.Filled.Tune, stringResource(R.string.search_empty_hint_tabs))
        Spacer(Modifier.height(12.dp))
        SearchEmptyHint(Icons.Outlined.History, stringResource(R.string.search_empty_hint_history))
    }
}

@Composable
private fun SearchEmptyHint(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HistoryRow(
    query: String,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(color = MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                query,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Filled.Clear,
                    contentDescription = stringResource(R.string.search_remove),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchLoadMoreFooter(
    visible: Boolean,
    loadedCount: Int,
    onLoadMore: () -> Unit,
) {
    if (!visible) return
    LaunchedEffect(loadedCount) { onLoadMore() }
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        LoadingIndicator(modifier = Modifier.size(28.dp), color = MaterialTheme.colorScheme.primary)
    }
}
