package com.savoo.scclient.ui.screens.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import com.savoo.scclient.R
import com.savoo.scclient.ui.components.AlbumRow
import com.savoo.scclient.ui.components.ArtistRow
import com.savoo.scclient.ui.components.TrackRow

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
    val history by viewModel.history.collectAsState()

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
                        Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onQueryChange("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.search_clear))
                            }
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
                                .clickable { viewModel.clearHistory() }
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
                                onClick = { viewModel.selectFromHistory(query) },
                                onRemove = { viewModel.removeHistoryItem(query) },
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
                                onCheckedChange = { checked -> if (checked) viewModel.onTabChange(tab) },
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

                    when {
                        state.isLoading || state.isResolvingLink -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            LoadingIndicator()
                        }
                        state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.search_error, state.error ?: ""))
                        }
                        state.query.isNotBlank() && state.tracks.isEmpty() && state.artists.isEmpty() && state.albums.isEmpty() -> Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(R.string.search_nothing_found),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        state.query.isBlank() && history.isEmpty() -> Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(R.string.search_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HistoryRow(
    query: String,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
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
