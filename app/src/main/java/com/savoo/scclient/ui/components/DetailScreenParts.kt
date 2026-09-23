package com.savoo.scclient.ui.components

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.savoo.scclient.R
import com.savoo.scclient.ui.haptics.rememberHapticTick

fun hiResArtwork(url: String?): String? = url?.replace("-large", "-t500x500")

@Composable
fun rememberCollapseProgress(listState: LazyListState, collapseDistance: Dp): Float {
    val distancePx = with(LocalDensity.current) { collapseDistance.toPx() }
    val progress by remember(listState, distancePx) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / distancePx).coerceIn(0f, 1f)
        }
    }
    return progress
}

@Composable
fun rememberArtworkColor(url: String?): Color? {
    val context = LocalContext.current
    var color by remember(url) { mutableStateOf<Color?>(null) }
    LaunchedEffect(url) {
        if (url == null) return@LaunchedEffect
        runCatching {
            val request = ImageRequest.Builder(context)
                .data(hiResArtwork(url))
                .allowHardware(false)
                .size(128)
                .build()
            val result = context.imageLoader.execute(request) as? SuccessResult ?: return@runCatching
            val bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: return@runCatching
            val palette = Palette.from(bitmap).generate()
            val swatch = palette.vibrantSwatch ?: palette.dominantSwatch ?: palette.mutedSwatch
            swatch?.let { color = Color(it.rgb) }
        }
    }
    return color
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CollapsingDetailTopBar(
    title: String,
    progress: Float,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundProgress: Float = progress,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val buttonBackground = containerColor.copy(alpha = 0.72f * (1f - backgroundProgress))
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor.copy(alpha = backgroundProgress))
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(
                onClick = onBack,
                shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = buttonBackground,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
                    .graphicsLayer {
                        alpha = ((progress - 0.6f) / 0.4f).coerceIn(0f, 1f)
                        translationY = (1f - alpha) * 12.dp.toPx()
                    },
            )
            actions()
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DetailActionRow(
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    isPlayingThis: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    playEnabled: Boolean = true,
    onShare: (() -> Unit)? = null,
    extraActions: @Composable RowScope.() -> Unit = {},
) {
    val haptic = rememberHapticTick()
    var likeBounce by remember { mutableStateOf(false) }
    val likeScale by animateFloatAsState(
        targetValue = if (likeBounce) 1.25f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        finishedListener = { likeBounce = false },
        label = "detailLike",
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToggleButton(
            checked = isFavorite,
            onCheckedChange = { haptic(); likeBounce = true; onToggleFavorite() },
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Icon(
                if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = null,
                modifier = Modifier.size(18.dp).graphicsLayer { scaleX = likeScale; scaleY = likeScale },
            )
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(if (isFavorite) R.string.detail_in_favorites else R.string.detail_add_to_favorites),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        if (onShare != null) {
            IconButton(onClick = { haptic(); onShare() }, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_share))
            }
        }
        extraActions()
        Spacer(Modifier.weight(1f))
        FilledTonalIconButton(
            onClick = { haptic(); onShuffle() },
            enabled = playEnabled,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(48.dp),
        ) {
            Icon(Icons.Filled.Shuffle, contentDescription = stringResource(R.string.player_shuffle))
        }
        Spacer(Modifier.width(10.dp))
        FilledIconButton(
            onClick = { haptic(); onPlay() },
            enabled = playEnabled,
            shapes = IconButtonDefaults.shapes(
                shape = if (isPlayingThis) RoundedCornerShape(20.dp) else CircleShape,
                pressedShape = RoundedCornerShape(14.dp),
            ),
            modifier = Modifier.size(64.dp),
        ) {
            AnimatedContent(
                targetState = isPlayingThis,
                transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.6f)) togetherWith (fadeOut() + scaleOut(targetScale = 0.6f)) },
                label = "detailPlayIcon",
            ) { playing ->
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.play_all),
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}

@Composable
fun DetailSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 24.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

data class DetailCardItem(
    val id: Long,
    val title: String,
    val subtitle: String?,
    val artworkUrl: String?,
)

@Composable
fun DetailCardCarousel(
    items: List<DetailCardItem>,
    onClick: (Long) -> Unit,
    circular: Boolean = false,
) {
    val haptic = rememberHapticTick()
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(items, key = { it.id }) { item ->
            Column(
                horizontalAlignment = if (circular) Alignment.CenterHorizontally else Alignment.Start,
                modifier = Modifier
                    .width(if (circular) 120.dp else 150.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { haptic(); onClick(item.id) }
                    .padding(bottom = 10.dp),
            ) {
                Surface(
                    shape = if (circular) CircleShape else RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                ) {
                    if (item.artworkUrl != null) {
                        AsyncImage(
                            model = hiResArtwork(item.artworkUrl),
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(40.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = if (circular) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
                item.subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun formatTotalDuration(totalMs: Long): String {
    val totalMinutes = (totalMs / 60_000L).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) stringResource(R.string.detail_duration_hours, hours, minutes)
    else stringResource(R.string.detail_duration_minutes, minutes.coerceAtLeast(1))
}
