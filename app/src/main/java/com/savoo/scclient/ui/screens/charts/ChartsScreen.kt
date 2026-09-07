package com.savoo.scclient.ui.screens.charts

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.savoo.scclient.R
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.ui.components.EmptyState
import com.savoo.scclient.ui.components.ExpressivePullToRefreshBox
import com.savoo.scclient.ui.components.TrackRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartsScreen(
    onBack: () -> Unit = {},
    viewModel: ChartsViewModel = hiltViewModel(),
) {
    val tracks by viewModel.tracks.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val hasError by viewModel.hasError.collectAsState()
    val playerState by viewModel.playerController.state.collectAsState()
    val unavailableReasons by viewModel.playerController.unavailableReasons.collectAsState()

    Scaffold(topBar = {
        TopAppBar(
            title = {
                Column {
                    Text(stringResource(R.string.charts_title))
                    Text(
                        stringResource(R.string.charts_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
            },
        )
    }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Spacer(Modifier.height(8.dp))

            if (tracks.isNotEmpty()) {
                Surface(
                    onClick = { viewModel.playAll() },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(56.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.charts_play_all), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            ExpressivePullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    isLoading -> ChartsSkeleton()
                    tracks.isEmpty() && hasError -> EmptyState(
                        icon = Icons.Filled.Leaderboard,
                        text = stringResource(R.string.charts_error),
                    )
                    tracks.isEmpty() -> EmptyState(
                        icon = Icons.Filled.Leaderboard,
                        text = stringResource(R.string.charts_empty),
                    )
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                            val isCurrent = playerState.currentTrack?.id == track.id
                            ChartTrackRow(
                                rank = index + 1,
                                track = track,
                                isPlaying = playerState.isPlaying && isCurrent,
                                isLoading = playerState.loadingTrackId == track.id,
                                unavailableReason = unavailableReasons[track.id],
                                onClick = {
                                    if (isCurrent) viewModel.playerController.togglePlayPause() else viewModel.playFrom(track.id)
                                },
                                modifier = Modifier.animateItem(),
                            )
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartTrackRow(
    rank: Int,
    track: Track,
    isPlaying: Boolean,
    isLoading: Boolean,
    unavailableReason: com.savoo.scclient.data.model.UnavailableReason?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(30.dp), contentAlignment = Alignment.Center) {
            Text(
                "$rank",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = if (rank <= 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
        Spacer(Modifier.width(6.dp))
        TrackRow(
            track = track,
            onClick = onClick,
            isPlaying = isPlaying,
            isLoading = isLoading,
            onTogglePlayPause = onClick,
            unavailableReason = unavailableReason,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ChartsSkeleton() {
    val transition = rememberInfiniteTransition(label = "chartsSkeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "chartsSkeletonAlpha",
    )
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(8) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alpha),
                modifier = Modifier.fillMaxWidth().height(76.dp),
            ) {}
        }
    }
}
