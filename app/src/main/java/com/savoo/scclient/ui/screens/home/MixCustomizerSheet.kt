package com.savoo.scclient.ui.screens.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.savoo.scclient.R
import com.savoo.scclient.data.model.ExcludedMixArtist
import com.savoo.scclient.data.model.FavoriteArtist
import com.savoo.scclient.ui.components.SwitchItem
import com.savoo.scclient.ui.haptics.rememberHapticTick
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MixCustomizerSheet(
    favoriteArtists: List<FavoriteArtist>,
    excludedArtists: List<ExcludedMixArtist>,
    preferredArtistIds: Set<Long>,
    discoveryEnabled: Boolean,
    onToggleArtist: (Long) -> Unit,
    onSetDiscoveryEnabled: (Boolean) -> Unit,
    onUnblockArtist: (Long) -> Unit,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = rememberHapticTick()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
        ) {
            var contentVisible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { contentVisible = true }
            val bgAlpha by animateFloatAsState(
                targetValue = if (contentVisible) 1f else 0f,
                animationSpec = tween(900, easing = FastOutSlowInEasing),
                label = "mixCustomizerBgAlpha",
            )
            val contentAlpha by animateFloatAsState(
                targetValue = if (contentVisible) 1f else 0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
                label = "mixCustomizerAlpha",
            )
            val contentOffset by animateDpAsState(
                targetValue = if (contentVisible) 0.dp else 36.dp,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                label = "mixCustomizerOffset",
            )

            FlowingOrbsBackground(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = bgAlpha })

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .graphicsLayer {
                        translationY = contentOffset.toPx()
                        alpha = contentAlpha
                    },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(onClick = { haptic(); onDismiss() }) {
                        Icon(Icons.Filled.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Text(
                    stringResource(R.string.mix_customizer_title),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.mix_customizer_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (favoriteArtists.isNotEmpty()) {
                    Spacer(Modifier.height(28.dp))
                    Text(
                        stringResource(R.string.mix_customizer_your_artists),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(favoriteArtists, key = { it.artistId }) { artist ->
                            SelectableArtistChip(
                                artist = artist,
                                selected = artist.artistId in preferredArtistIds,
                                onClick = { onToggleArtist(artist.artistId) },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SwitchItem(
                        title = stringResource(R.string.mix_customizer_discover_title),
                        subtitle = stringResource(R.string.mix_customizer_discover_desc),
                        checked = discoveryEnabled,
                        onCheckedChange = onSetDiscoveryEnabled,
                    )
                }

                if (excludedArtists.isNotEmpty()) {
                    Spacer(Modifier.height(28.dp))
                    Text(
                        stringResource(R.string.mix_customizer_blocked_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        excludedArtists.forEach { artist ->
                            BlockedArtistRow(artist = artist, onUnblock = { onUnblockArtist(artist.artistId) })
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))

                Surface(
                    onClick = { haptic(); onStart() },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.mix_customizer_start),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun FlowingOrbsBackground(modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "mixOrbs")
    val angleA by infinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing)),
        label = "orbAngleA",
    )
    val angleB by infinite.animateFloat(
        initialValue = 360f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(26000, easing = LinearEasing)),
        label = "orbAngleB",
    )
    val angleC by infinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(33000, easing = LinearEasing)),
        label = "orbAngleC",
    )
    val breathe by infinite.animateFloat(
        initialValue = 0.9f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(5200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "orbBreathe",
    )

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary

    fun softGradient(c: Color) = Brush.radialGradient(
        0f to c,
        0.5f to c.copy(alpha = c.alpha * 0.5f),
        1f to c.copy(alpha = 0f),
    )

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .requiredSize(420.dp)
                .graphicsLayer {
                    scaleX = breathe; scaleY = breathe
                    val rad = Math.toRadians(angleA.toDouble())
                    translationX = (cos(rad) * 90f).toFloat() - 140f
                    translationY = (sin(rad) * 90f).toFloat() - 140f
                }
                .background(softGradient(primary.copy(alpha = 0.55f)), CircleShape)
                .blur(70.dp, BlurredEdgeTreatment.Unbounded),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .requiredSize(460.dp)
                .graphicsLayer {
                    scaleX = breathe; scaleY = breathe
                    val rad = Math.toRadians(angleB.toDouble())
                    translationX = (cos(rad) * 80f).toFloat() + 120f
                    translationY = (sin(rad) * 80f).toFloat() + 120f
                }
                .background(softGradient(tertiary.copy(alpha = 0.5f)), CircleShape)
                .blur(80.dp, BlurredEdgeTreatment.Unbounded),
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .requiredSize(380.dp)
                .graphicsLayer {
                    scaleX = breathe; scaleY = breathe
                    val rad = Math.toRadians(angleC.toDouble())
                    translationX = (cos(rad) * 70f).toFloat()
                    translationY = (sin(rad) * 70f).toFloat()
                }
                .background(softGradient(secondary.copy(alpha = 0.45f)), CircleShape)
                .blur(65.dp, BlurredEdgeTreatment.Unbounded),
        )
    }
}

@Composable
private fun SelectableArtistChip(artist: FavoriteArtist, selected: Boolean, onClick: () -> Unit) {
    val haptic = rememberHapticTick()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "artistChipScale",
    )
    val ringAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(220),
        label = "artistChipRing",
    )
    val ringColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .width(76.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interactionSource, indication = null) { haptic(); onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                if (artist.avatarUrl != null) {
                    AsyncImage(
                        model = artist.avatarUrl.replace("-large", "-t500x500"),
                        contentDescription = artist.username,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        Icons.Filled.Headphones,
                        contentDescription = artist.username,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .graphicsLayer { alpha = ringAlpha }
                    .border(BorderStroke(3.dp, ringColor), CircleShape),
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(22.dp)
                        .graphicsLayer { alpha = ringAlpha }
                        .clip(CircleShape)
                        .background(ringColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            artist.username,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun BlockedArtistRow(artist: ExcludedMixArtist, onUnblock: () -> Unit) {
    val haptic = rememberHapticTick()
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.PersonOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                artist.username,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { haptic(); onUnblock() }) {
                Text(stringResource(R.string.mix_customizer_unblock))
            }
        }
    }
}
