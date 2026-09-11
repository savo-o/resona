package com.savoo.scclient.ui.screens.stats

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.savoo.scclient.R
import com.savoo.scclient.data.local.ArtistListenStat
import com.savoo.scclient.data.local.TrackListenStat
import com.savoo.scclient.data.repository.StatsPeriod
import com.savoo.scclient.data.repository.StatsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import com.savoo.scclient.ui.components.EmptyState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StatisticsViewModel @Inject constructor(
    statsRepository: StatsRepository,
) : ViewModel() {
    private val _period = MutableStateFlow(StatsPeriod.ALL)
    val period = _period.asStateFlow()

    fun setPeriod(value: StatsPeriod) {
        _period.value = value
    }

    val totalMsListened = _period
        .flatMapLatest { statsRepository.totalMsListened(it.since()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val totalPlays = _period
        .flatMapLatest { statsRepository.totalPlays(it.since()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val topArtists = _period
        .flatMapLatest { statsRepository.topArtists(10, it.since()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topTracks = _period
        .flatMapLatest { statsRepository.topTracks(10, it.since()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topGenre = _period
        .flatMapLatest { statsRepository.topGenre(it.since()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val hasData = combine(
        statsRepository.totalPlays(),
        statsRepository.topArtists(1),
    ) { plays, artists -> plays > 0 || artists.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
}

private fun periodLabelRes(period: StatsPeriod): Int = when (period) {
    StatsPeriod.WEEK -> R.string.statistics_period_week
    StatsPeriod.MONTH -> R.string.statistics_period_month
    StatsPeriod.YEAR -> R.string.statistics_period_year
    StatsPeriod.ALL -> R.string.statistics_period_all
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatisticsScreen(
    onBack: () -> Unit = {},
    viewModel: StatisticsViewModel = hiltViewModel(),
) {
    val totalMsListened by viewModel.totalMsListened.collectAsState()
    val totalPlays by viewModel.totalPlays.collectAsState()
    val topArtists by viewModel.topArtists.collectAsState()
    val topTracks by viewModel.topTracks.collectAsState()
    val topGenre by viewModel.topGenre.collectAsState()
    val hasData by viewModel.hasData.collectAsState()
    val period by viewModel.period.collectAsState()
    var showRankingInfo by remember { mutableStateOf(false) }
    var showWrapped by remember { mutableStateOf(false) }

    if (showWrapped) {
        WrappedSheet(
            totalHours = (totalMsListened / 3_600_000L).toInt(),
            totalPlays = totalPlays,
            topGenre = topGenre,
            topArtists = topArtists,
            topTracks = topTracks,
            onDismiss = { showWrapped = false },
        )
    }

    if (showRankingInfo) {
        AlertDialog(
            onDismissRequest = { showRankingInfo = false },
            title = { Text(stringResource(R.string.statistics_top_artists_info_title)) },
            text = { Text(stringResource(R.string.statistics_top_artists_info_body)) },
            confirmButton = {
                TextButton(onClick = { showRankingInfo = false }) {
                    Text(stringResource(R.string.statistics_top_artists_info_dismiss))
                }
            },
        )
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.statistics_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
            },
        )
    }) { padding ->
        if (!hasData) {
            EmptyState(
                icon = Icons.Filled.BarChart,
                text = stringResource(R.string.statistics_empty),
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    ButtonGroup(modifier = Modifier.fillMaxWidth()) {
                        StatsPeriod.entries.forEachIndexed { index, option ->
                            val shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                StatsPeriod.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            }
                            ToggleButton(
                                checked = period == option,
                                onCheckedChange = { checked -> if (checked) viewModel.setPeriod(option) },
                                modifier = Modifier.weight(1f),
                                shapes = shapes,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    stringResource(periodLabelRes(option)),
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        StatCard(
                            targetValue = (totalMsListened / 60_000L).toInt(),
                            label = stringResource(R.string.statistics_hours_listened),
                            formatValue = { formatListenDuration(it) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                        StatCard(
                            targetValue = totalPlays,
                            label = stringResource(R.string.statistics_total_plays),
                            formatValue = { it.toString() },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }

                if (topArtists.isNotEmpty()) {
                    item { Spacer(Modifier.height(4.dp)) }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.statistics_top_artists),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { showRankingInfo = true }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    Icons.Outlined.Info,
                                    contentDescription = stringResource(R.string.statistics_top_artists_info_title),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                    val maxMs = topArtists.first().totalMs.coerceAtLeast(1L)
                    itemsIndexed(topArtists, key = { _, stat -> stat.artistId }) { index, stat ->
                        ArtistStatRow(rank = index + 1, stat = stat, shareOfMax = stat.totalMs.toFloat() / maxMs)
                    }
                }

                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun StatCard(targetValue: Int, label: String, formatValue: @Composable (Int) -> String, modifier: Modifier = Modifier) {
    var animateTo by remember { mutableStateOf(0) }
    LaunchedEffect(targetValue) { animateTo = targetValue }
    val animatedValue by animateIntAsState(
        targetValue = animateTo,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "statCountUp",
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
            Text(
                formatValue(animatedValue),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ArtistStatRow(rank: Int, stat: ArtistListenStat, shareOfMax: Float) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$rank",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 10.dp),
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (stat.artworkUrl != null) {
                    AsyncImage(
                        model = stat.artworkUrl.replace("-large", "-t500x500"),
                        contentDescription = stat.artistName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxSize()) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.Headphones,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    stat.artistName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(R.string.statistics_play_count, stat.playCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(shareOfMax.coerceIn(0.04f, 1f))
                            .height(4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
}

@Composable
private fun formatListenDuration(totalMinutes: Int): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        stringResource(R.string.statistics_duration_hm, hours, minutes)
    } else {
        stringResource(R.string.statistics_duration_m, minutes)
    }
}
