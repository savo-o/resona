package com.savoo.scclient.ui.screens.recap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import coil.compose.AsyncImage
import com.savoo.scclient.ui.components.MorphingArtworkShape
import com.savoo.scclient.ui.components.hiResArtwork
import com.savoo.scclient.ui.components.pathIn
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

private val EaseOutQuint = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

@Composable
fun rememberReveal(delayMs: Int): State<Float> {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        progress.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow))
    }
    return progress.asState()
}

fun Modifier.revealed(progress: State<Float>, offset: Dp = 28.dp, fromScale: Float = 0.92f): Modifier = graphicsLayer {
    val value = progress.value
    alpha = value.coerceIn(0f, 1f)
    translationY = (1f - value) * offset.toPx()
    val scale = fromScale + (1f - fromScale) * value
    scaleX = scale
    scaleY = scale
}

@Composable
fun rememberCountUp(target: Long, delayMs: Int, durationMs: Int = 1600): State<Long> {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(target) {
        delay(delayMs.toLong())
        progress.animateTo(1f, tween(durationMs, easing = EaseOutQuint))
    }
    return remember(target) { derivedStateOf { (target * progress.value).toLong() } }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val backdropMorphs: List<Morph> by lazy {
    listOf(
        Morph(MaterialShapes.Cookie12Sided.normalized(), MaterialShapes.Clover8Leaf.normalized()),
        Morph(MaterialShapes.Sunny.normalized(), MaterialShapes.SoftBurst.normalized()),
        Morph(MaterialShapes.Flower.normalized(), MaterialShapes.PuffyDiamond.normalized()),
    )
}

private data class Blob(val x: Float, val y: Float, val scale: Float, val speed: Float, val drift: Float)

private val blobs = listOf(
    Blob(x = 0.92f, y = 0.12f, scale = 0.95f, speed = 1f, drift = 0.9f),
    Blob(x = 0.05f, y = 0.78f, scale = 0.8f, speed = -0.7f, drift = 1.3f),
    Blob(x = 0.75f, y = 0.98f, scale = 0.5f, speed = 1.4f, drift = 0.6f),
)

@Composable
fun RecapBackdrop(accent: Color, slide: Int, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "recapBackdrop")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(48_000, easing = LinearEasing)),
        label = "rotation",
    )
    val morph by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6_500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "morph",
    )
    val shift by animateFloatAsState(
        targetValue = slide.toFloat(),
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessVeryLow),
        label = "shift",
    )
    val base = lerp(accent, Color.Black, 0.78f)
    val glow = lerp(accent, Color.White, 0.12f)

    Canvas(modifier.fillMaxSize()) {
        drawRect(base)
        blobs.forEachIndexed { index, blob ->
            val side = size.minDimension * blob.scale * (1f + 0.06f * sin(shift * 1.7f + index))
            val center = Offset(
                size.width * (blob.x + 0.09f * sin(shift * blob.drift + index * 2f)),
                size.height * (blob.y + 0.05f * cos(shift * blob.drift + index)),
            )
            val path = backdropMorphs[index].pathIn(morph, Size(side, side))
            translate(center.x - side / 2f, center.y - side / 2f) {
                rotate(rotation * blob.speed + shift * 24f, pivot = Offset(side / 2f, side / 2f)) {
                    drawPath(
                        path,
                        brush = Brush.radialGradient(
                            colors = listOf(glow.copy(alpha = 0.55f), accent.copy(alpha = 0.18f)),
                            center = Offset(side / 2f, side / 2f),
                            radius = side * 0.6f,
                        ),
                    )
                }
            }
        }
        drawRect(
            Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.25f),
                0.35f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.55f),
            )
        )
    }
}

@Composable
fun MorphArtwork(
    url: String?,
    from: RoundedPolygon,
    to: RoundedPolygon,
    size: Dp,
    delayMs: Int,
    modifier: Modifier = Modifier,
    spin: Boolean = false,
    placeholder: ImageVector = Icons.Filled.Headphones,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        progress.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessVeryLow))
    }
    val morph = remember(from, to) { Morph(from.normalized(), to.normalized()) }
    val rotation = if (spin) {
        val transition = rememberInfiniteTransition(label = "artworkSpin")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(22_000, easing = LinearEasing)),
            label = "spin",
        )
    } else {
        null
    }
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                val value = progress.value
                alpha = value.coerceIn(0f, 1f)
                val scale = 0.55f + 0.45f * value
                scaleX = scale
                scaleY = scale
                rotationZ = (rotation?.value ?: 0f) + (1f - value) * -40f
            }
            .clip(MorphingArtworkShape(morph, progress.value.coerceIn(0f, 1f)))
            .background(Color.White.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        val counter = Modifier.graphicsLayer { rotationZ = -((rotation?.value ?: 0f) + (1f - progress.value) * -40f) }
        if (url != null) {
            AsyncImage(
                model = hiResArtwork(url),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = counter.fillMaxSize().graphicsLayer { scaleX = 1.45f; scaleY = 1.45f },
            )
        } else {
            Icon(placeholder, contentDescription = null, tint = Color.White, modifier = counter.size(size * 0.36f))
        }
    }
}
