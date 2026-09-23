package com.savoo.scclient.ui.screens.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.savoo.scclient.R
import com.savoo.scclient.ui.haptics.rememberHaptics
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private enum class FloaterKind {
    SWITCH, SLIDER, PRESS_ME, LAUNCH, CHECKBOX, RADIOS, FAB, STAR_TOGGLE, LOADER, SEGMENTS, WAVY, GRAVITY, SECOND_SWITCH,
}

private enum class EggPhase { FLOAT, LINEUP, DANCE }

private const val LINEUP_SECONDS = 1.7f
private const val BEATS_PER_SECOND = PARTY_BPM / 60f

private class Floater(val kind: FloaterKind, random: Random) {
    var x by mutableFloatStateOf(Float.NaN)
    var y by mutableFloatStateOf(Float.NaN)
    var angle by mutableFloatStateOf(random.nextFloat() * 50f - 25f)
    var scaleX by mutableFloatStateOf(1f)
    var scaleY by mutableFloatStateOf(1f)
    var alpha by mutableFloatStateOf(1f)
    var vx = random.signed(20f, 60f)
    var vy = random.signed(20f, 60f)
    var spin = random.nextFloat() * 30f - 15f
    val bobPhase = random.nextFloat() * 2f * PI.toFloat()
    val dancePhase = random.nextFloat() * 2f * PI.toFloat()
    var width = 0
    var height = 0
    var dragging = false
    var densityScaled = false
    var fromX = 0f
    var fromY = 0f
    var fromAngle = 0f
}

private class SpaceStar(val x: Float, val y: Float, val radiusDp: Float, val phase: Float)

private class Confetto(
    val lane: Float,
    val delay: Float,
    val hue: Float,
    val widthDp: Float,
    val heightDp: Float,
    val spin: Float,
    val sway: Float,
)

private fun Random.signed(min: Float, max: Float): Float =
    (min + nextFloat() * (max - min)) * if (nextBoolean()) 1f else -1f

private fun lerp(from: Float, to: Float, t: Float): Float = from + (to - from) * t

private fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
    if (edge1 <= edge0) return if (value < edge0) 0f else 1f
    val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun stepFloaters(floaters: List<Floater>, dt: Float, areaW: Float, areaH: Float, density: Float, random: Random) {
    if (areaW <= 0f || areaH <= 0f) return
    val maxSpeed = 1600f * density
    val cruise = 60f * density
    val minSpeed = 10f * density

    floaters.forEach { f ->
        if (f.width == 0) return@forEach
        if (!f.densityScaled) {
            f.vx *= density
            f.vy *= density
            f.densityScaled = true
        }
        if (f.x.isNaN()) {
            f.x = random.nextFloat() * (areaW - f.width).coerceAtLeast(0f)
            f.y = random.nextFloat() * (areaH - f.height).coerceAtLeast(0f)
        }
        if (f.dragging) return@forEach

        val speed = hypot(f.vx, f.vy)
        when {
            speed > maxSpeed -> {
                f.vx *= maxSpeed / speed
                f.vy *= maxSpeed / speed
            }
            speed > cruise -> {
                val k = 1f - 0.7f * dt
                f.vx *= k
                f.vy *= k
            }
            speed < minSpeed -> {
                val a = random.nextFloat() * 2f * PI.toFloat()
                f.vx += cos(a) * minSpeed * dt * 4f
                f.vy += sin(a) * minSpeed * dt * 4f
            }
        }
        if (abs(f.spin) > 20f) f.spin *= 1f - 0.8f * dt

        f.x += f.vx * dt
        f.y += f.vy * dt
        f.angle += f.spin * dt

        val maxX = (areaW - f.width).coerceAtLeast(0f)
        val maxY = (areaH - f.height).coerceAtLeast(0f)
        if (f.x < 0f) { f.x = 0f; f.vx = abs(f.vx); f.spin = -f.spin * 0.8f + random.nextFloat() * 20f - 10f }
        if (f.x > maxX) { f.x = maxX; f.vx = -abs(f.vx); f.spin = -f.spin * 0.8f + random.nextFloat() * 20f - 10f }
        if (f.y < 0f) { f.y = 0f; f.vy = abs(f.vy); f.spin = -f.spin * 0.8f + random.nextFloat() * 20f - 10f }
        if (f.y > maxY) { f.y = maxY; f.vy = -abs(f.vy); f.spin = -f.spin * 0.8f + random.nextFloat() * 20f - 10f }
    }

    for (i in floaters.indices) {
        for (j in i + 1 until floaters.size) {
            val a = floaters[i]
            val b = floaters[j]
            if (a.x.isNaN() || b.x.isNaN() || a.width == 0 || b.width == 0) continue
            if (a.dragging && b.dragging) continue
            val dx = (b.x + b.width / 2f) - (a.x + a.width / 2f)
            val dy = (b.y + b.height / 2f) - (a.y + a.height / 2f)
            val dist = hypot(dx, dy)
            val minDist = (a.width + a.height) / 4f + (b.width + b.height) / 4f
            if (dist < 0.01f || dist >= minDist) continue
            val nx = dx / dist
            val ny = dy / dist
            val overlap = minDist - dist
            val avx = if (a.dragging) 0f else a.vx
            val avy = if (a.dragging) 0f else a.vy
            val bvx = if (b.dragging) 0f else b.vx
            val bvy = if (b.dragging) 0f else b.vy
            val approach = (bvx - avx) * nx + (bvy - avy) * ny
            when {
                a.dragging -> {
                    b.x += nx * overlap
                    b.y += ny * overlap
                    if (approach < 0f) { b.vx -= 2f * approach * nx; b.vy -= 2f * approach * ny }
                }
                b.dragging -> {
                    a.x -= nx * overlap
                    a.y -= ny * overlap
                    if (approach < 0f) { a.vx += 2f * approach * nx; a.vy += 2f * approach * ny }
                }
                else -> {
                    a.x -= nx * overlap / 2f
                    a.y -= ny * overlap / 2f
                    b.x += nx * overlap / 2f
                    b.y += ny * overlap / 2f
                    if (approach < 0f) {
                        a.vx += approach * nx
                        a.vy += approach * ny
                        b.vx -= approach * nx
                        b.vy -= approach * ny
                        a.spin += random.nextFloat() * 30f - 15f
                        b.spin += random.nextFloat() * 30f - 15f
                    }
                }
            }
        }
    }
}

private fun lineupSpan(floaters: List<Floater>, areaW: Float): Float {
    val totalWidth = floaters.sumOf { it.width }.toFloat()
    return totalWidth.coerceAtMost(areaW * 0.92f)
}

private fun layoutLineup(floaters: List<Floater>, progress: Float, areaW: Float, areaH: Float) {
    if (areaW <= 0f || areaH <= 0f) return
    val move = smoothstep(0f, 0.72f, progress)
    val fade = smoothstep(0.48f, 0.8f, progress)
    val totalWidth = floaters.sumOf { it.width }.toFloat()
    val available = areaW * 0.92f
    val gap = if (floaters.size > 1) (available - totalWidth) / (floaters.size - 1) else 0f
    var cursor = (areaW - (totalWidth + gap * (floaters.size - 1))) / 2f
    val centerY = areaH / 2f

    floaters.forEach { f ->
        val targetX = cursor
        val targetY = centerY - f.height / 2f
        cursor += f.width + gap
        if (f.x.isNaN()) return@forEach
        f.x = lerp(f.fromX, targetX, move)
        f.y = lerp(f.fromY, targetY, move)
        f.angle = lerp(f.fromAngle, 0f, move)
        f.scaleX = 1f
        f.scaleY = lerp(1f, 0.82f, fade)
        f.alpha = 1f - fade
    }
}

private fun formation(kind: Int, index: Int, count: Int, beat: Float): Offset = when (kind) {
    0 -> {
        val a = 2f * PI.toFloat() * index / count + beat * 0.22f
        Offset(0.5f + cos(a) * 0.33f, 0.5f + sin(a) * 0.3f)
    }
    1 -> Offset(
        0.12f + 0.76f * ((index + 0.5f) / count),
        0.5f + 0.26f * sin(index * 0.8f + beat * 1.35f),
    )
    2 -> {
        val side = index % 2
        val slot = index / 2
        val perSide = ((count + 1) / 2).coerceAtLeast(1)
        val spin = if (side == 0) 1f else -1f
        val a = 2f * PI.toFloat() * slot / perSide + beat * 0.55f * spin
        Offset(0.28f + side * 0.44f + cos(a) * 0.14f, 0.5f + sin(a) * 0.24f)
    }
    else -> Offset(
        0.5f + 0.32f * sin(beat * 0.9f + index * 0.55f),
        0.14f + 0.72f * ((index + 0.5f) / count),
    )
}

private fun layoutDance(floaters: List<Floater>, danceTime: Float, areaW: Float, areaH: Float, density: Float) {
    if (areaW <= 0f || areaH <= 0f) return
    val beat = danceTime * BEATS_PER_SECOND
    val block = floor(beat / 8f).toInt()
    val blend = smoothstep(6.6f, 8f, beat - block * 8f)
    val count = floaters.size
    val step = beat - floor(beat)
    val hop = sin(PI.toFloat() * step.pow(0.75f))
    val land = (1f - step).pow(5f)
    val accent = if (floor(beat).toInt() % 4 == 0) 1.35f else 1f

    floaters.forEachIndexed { index, f ->
        if (f.width == 0) return@forEachIndexed
        val a = formation(block.mod(4), index, count, beat)
        val b = formation((block + 1).mod(4), index, count, beat)
        val cx = lerp(a.x, b.x, blend) * areaW
        val cy = lerp(a.y, b.y, blend) * areaH
        val swing = sin(beat * PI.toFloat() / 2f + f.dancePhase)
        val shake = sin(beat * 8f * PI.toFloat() + f.dancePhase) * 4f * density * land

        f.x = (cx - f.width / 2f + swing * 12f * density + shake)
            .coerceIn(0f, (areaW - f.width).coerceAtLeast(0f))
        f.y = (cy - f.height / 2f - hop * 30f * density * accent)
            .coerceIn(0f, (areaH - f.height).coerceAtLeast(0f))
        f.angle = swing * 18f + hop * 8f * (if (index % 2 == 0) 1f else -1f)
        f.scaleX = 1f + 0.2f * land * accent - 0.05f * hop
        f.scaleY = 1f - 0.22f * land * accent + 0.12f * hop
        f.alpha = 1f
    }
}

@Composable
fun ZeroGravityEasterEggDialog(onDismiss: () -> Unit) {
    val random = remember { Random(System.nanoTime()) }
    val floaters = remember { FloaterKind.entries.map { Floater(it, random) } }
    val stars = remember {
        List(160) {
            SpaceStar(
                x = random.nextFloat(),
                y = random.nextFloat(),
                radiusDp = 0.4f + random.nextFloat() * 1.4f,
                phase = random.nextFloat() * 2f * PI.toFloat(),
            )
        }
    }
    val confetti = remember {
        List(90) {
            Confetto(
                lane = random.nextFloat(),
                delay = random.nextFloat() * 4f,
                hue = random.nextFloat() * 360f,
                widthDp = 4f + random.nextFloat() * 5f,
                heightDp = 8f + random.nextFloat() * 8f,
                spin = random.signed(0.6f, 2.2f),
                sway = random.nextFloat() * 2f * PI.toFloat(),
            )
        }
    }
    var time by remember { mutableFloatStateOf(0f) }
    var phase by remember { mutableStateOf(EggPhase.FLOAT) }
    var lineupTime by remember { mutableFloatStateOf(0f) }
    var danceTime by remember { mutableFloatStateOf(0f) }
    var flash by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current.density
    val haptics = rememberHaptics()
    val context = LocalContext.current
    val player = remember { PartyPlayer(context) }
    val beamPath = remember { Path() }

    DisposableEffect(player) {
        onDispose { player.stop() }
    }

    LaunchedEffect(phase) {
        if (phase != EggPhase.LINEUP) return@LaunchedEffect
        runCatching { player.prepare() }
        while (isActive && lineupTime < LINEUP_SECONDS) {
            withFrameNanos { }
        }
        runCatching { player.play() }
        haptics.success()
        flash = 1f
        danceTime = 0f
        phase = EggPhase.DANCE
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF04060D)),
        ) {
            val areaW by rememberUpdatedState(constraints.maxWidth.toFloat())
            val areaH by rememberUpdatedState(constraints.maxHeight.toFloat())

            LaunchedEffect(floaters) {
                var last = 0L
                while (isActive) {
                    withFrameNanos { now ->
                        val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(1f / 20f)
                        last = now
                        time += dt
                        if (flash > 0f) flash = (flash - dt * 3.4f).coerceAtLeast(0f)
                        when (phase) {
                            EggPhase.FLOAT -> stepFloaters(floaters, dt, areaW, areaH, density, random)
                            EggPhase.LINEUP -> {
                                lineupTime += dt
                                layoutLineup(floaters, (lineupTime / LINEUP_SECONDS).coerceIn(0f, 1f), areaW, areaH)
                            }
                            EggPhase.DANCE -> {
                                danceTime += dt
                                layoutDance(floaters, danceTime, areaW, areaH, density)
                            }
                        }
                    }
                }
            }

            val startParty = {
                if (phase == EggPhase.FLOAT) {
                    floaters.forEach { f ->
                        f.dragging = false
                        f.fromX = if (f.x.isNaN()) 0f else f.x
                        f.fromY = if (f.y.isNaN()) 0f else f.y
                        f.fromAngle = f.angle
                    }
                    lineupTime = 0f
                    phase = EggPhase.LINEUP
                }
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val beat = danceTime * BEATS_PER_SECOND
                val pulse = (1f - (beat - floor(beat))).pow(3f)

                if (phase == EggPhase.DANCE) {
                    drawRect(Color.hsv((beat * 13f).mod(360f), 0.62f, 0.14f))
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val reach = hypot(size.width, size.height)
                    repeat(10) { index ->
                        val start = ((index * 36f + beat * 11f) * PI.toFloat() / 180f)
                        val end = start + 19f * PI.toFloat() / 180f
                        beamPath.rewind()
                        beamPath.moveTo(center.x, center.y)
                        beamPath.lineTo(center.x + cos(start) * reach, center.y + sin(start) * reach)
                        beamPath.lineTo(center.x + cos(end) * reach, center.y + sin(end) * reach)
                        beamPath.close()
                        drawPath(
                            path = beamPath,
                            color = Color.hsv((beat * 22f + index * 36f).mod(360f), 0.8f, 1f)
                                .copy(alpha = 0.04f + 0.06f * pulse),
                        )
                    }
                }

                stars.forEach { star ->
                    val twinkle = (0.5f + 0.4f * sin(time * 1.6f + star.phase)).coerceIn(0f, 1f)
                    val drift = (star.y + time * 0.003f * star.radiusDp).mod(1f)
                    val color = if (phase == EggPhase.DANCE) {
                        Color.hsv((star.phase * 57f + beat * 26f).mod(360f), 0.7f, 1f)
                    } else {
                        Color.White
                    }
                    val radius = star.radiusDp * this.density * if (phase == EggPhase.DANCE) 1f + 1.4f * pulse else 1f
                    drawCircle(
                        color = color.copy(alpha = twinkle),
                        radius = radius,
                        center = Offset(star.x * size.width, drift * size.height),
                    )
                }
            }

            floaters.forEach { floater ->
                key(floater.kind) {
                    Box(
                        modifier = Modifier
                            .offset {
                                if (floater.x.isNaN()) IntOffset.Zero
                                else IntOffset(floater.x.roundToInt(), floater.y.roundToInt())
                            }
                            .onSizeChanged {
                                floater.width = it.width
                                floater.height = it.height
                            }
                            .pointerInput(floater) {
                                val tracker = VelocityTracker()
                                var total = Offset.Zero
                                detectDragGestures(
                                    onDragStart = {
                                        if (phase == EggPhase.FLOAT) {
                                            haptics.click()
                                            floater.dragging = true
                                            total = Offset.Zero
                                            tracker.resetTracking()
                                        }
                                    },
                                    onDragEnd = {
                                        if (floater.dragging) {
                                            val velocity = tracker.calculateVelocity()
                                            floater.vx = velocity.x
                                            floater.vy = velocity.y
                                            floater.spin += velocity.x * 0.05f / density
                                            floater.dragging = false
                                        }
                                    },
                                    onDragCancel = { floater.dragging = false },
                                    onDrag = { change, dragAmount ->
                                        if (floater.dragging) {
                                            change.consume()
                                            total += dragAmount
                                            tracker.addPosition(change.uptimeMillis, total)
                                            floater.x = (floater.x + dragAmount.x).coerceIn(0f, (areaW - floater.width).coerceAtLeast(0f))
                                            floater.y = (floater.y + dragAmount.y).coerceIn(0f, (areaH - floater.height).coerceAtLeast(0f))
                                        }
                                    },
                                )
                            }
                            .graphicsLayer {
                                alpha = if (floater.x.isNaN()) 0f else floater.alpha
                                rotationZ = floater.angle
                                translationY = if (phase == EggPhase.FLOAT) {
                                    sin(time * 1.2f + floater.bobPhase) * 5.dp.toPx()
                                } else {
                                    0f
                                }
                                val drag = if (floater.dragging) 1.08f else 1f
                                scaleX = floater.scaleX * drag
                                scaleY = floater.scaleY * drag
                            },
                    ) {
                        FloaterContent(kind = floater.kind, onLaunch = startParty)
                    }
                }
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val beat = danceTime * BEATS_PER_SECOND
                val pulse = (1f - (beat - floor(beat))).pow(3f)

                if (phase == EggPhase.LINEUP) {
                    val progress = (lineupTime / LINEUP_SECONDS).coerceIn(0f, 1f)
                    val barAlpha = smoothstep(0.42f, 0.72f, progress)
                    if (barAlpha > 0f) {
                        val tallest = floaters.maxOf { it.height }.toFloat().coerceAtLeast(24f * this.density)
                        val stretch = smoothstep(0.7f, 1f, progress)
                        val barWidth = lerp(lineupSpan(floaters, size.width), size.width, stretch)
                        val barHeight = lerp(tallest * 0.9f, 10f * this.density, smoothstep(0.58f, 1f, progress))
                        drawRoundRect(
                            color = Color.White.copy(alpha = barAlpha),
                            topLeft = Offset((size.width - barWidth) / 2f, (size.height - barHeight) / 2f),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(barHeight / 2f),
                        )
                    }
                }

                if (phase == EggPhase.DANCE) {
                    confetti.forEach { piece ->
                        if (beat < piece.delay) return@forEach
                        val local = (beat - piece.delay).mod(4f)
                        val progress = local / 4f
                        val cx = (piece.lane + sin(local * 2.4f + piece.sway) * 0.05f) * size.width
                        val cy = (-0.12f + progress * 1.28f) * size.height
                        val alpha = (1f - smoothstep(0.72f, 1f, progress)) * 0.95f
                        val w = piece.widthDp * this.density
                        val h = piece.heightDp * this.density
                        rotate(degrees = local * piece.spin * 180f, pivot = Offset(cx, cy)) {
                            drawRect(
                                color = Color.hsv(piece.hue, 0.85f, 1f).copy(alpha = alpha),
                                topLeft = Offset(cx - w / 2f, cy - h / 2f),
                                size = Size(w, h),
                            )
                        }
                    }
                    drawRect(Color.White.copy(alpha = 0.09f * pulse))
                }

                if (flash > 0f) {
                    drawRect(Color.White.copy(alpha = flash * 0.85f))
                }
            }

            Text(
                text = stringResource(
                    if (phase == EggPhase.DANCE) R.string.easter_egg_party else R.string.easter_egg_caption,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = if (phase == EggPhase.DANCE) 0.85f else 0.4f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp),
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.close),
                    tint = Color.White.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun FloatingCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FloaterContent(kind: FloaterKind, onLaunch: () -> Unit) {
    val haptics = rememberHaptics()
    when (kind) {
        FloaterKind.SWITCH, FloaterKind.SECOND_SWITCH -> FloatingCard {
            var checked by remember { mutableStateOf(kind == FloaterKind.SWITCH) }
            Switch(checked = checked, onCheckedChange = { haptics.click(); checked = it })
        }
        FloaterKind.SLIDER -> FloatingCard {
            var value by remember { mutableFloatStateOf(0.4f) }
            Slider(value = value, onValueChange = { value = it }, modifier = Modifier.width(180.dp))
        }
        FloaterKind.PRESS_ME -> Button(onClick = { haptics.click() }) {
            Text(stringResource(R.string.easter_egg_press_me))
        }
        FloaterKind.LAUNCH -> FilledTonalButton(onClick = { haptics.success(); onLaunch() }) {
            Icon(Icons.Filled.RocketLaunch, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.easter_egg_launch))
        }
        FloaterKind.CHECKBOX -> FloatingCard {
            var checked by remember { mutableStateOf(false) }
            Checkbox(checked = checked, onCheckedChange = { haptics.click(); checked = it })
        }
        FloaterKind.RADIOS -> FloatingCard {
            var selected by remember { mutableIntStateOf(0) }
            Row {
                repeat(3) { index ->
                    RadioButton(selected = selected == index, onClick = { haptics.click(); selected = index })
                }
            }
        }
        FloaterKind.FAB -> FloatingActionButton(onClick = { haptics.click() }) {
            Icon(Icons.Filled.Add, contentDescription = null)
        }
        FloaterKind.STAR_TOGGLE -> {
            var checked by remember { mutableStateOf(true) }
            FilledIconToggleButton(checked = checked, onCheckedChange = { haptics.like(); checked = it }) {
                Icon(if (checked) Icons.Filled.Star else Icons.Filled.StarBorder, contentDescription = null)
            }
        }
        FloaterKind.LOADER -> FloatingCard {
            LoadingIndicator()
        }
        FloaterKind.SEGMENTS -> {
            var selected by remember { mutableIntStateOf(1) }
            val labels = listOf("A", "B", "C")
            Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                labels.forEachIndexed { index, label ->
                    ToggleButton(
                        checked = selected == index,
                        onCheckedChange = { haptics.click(); selected = index },
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            labels.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                    ) {
                        Text(label)
                    }
                }
            }
        }
        FloaterKind.WAVY -> FloatingCard {
            LinearWavyProgressIndicator(modifier = Modifier.width(140.dp))
        }
        FloaterKind.GRAVITY -> {
            var selected by remember { mutableStateOf(false) }
            FilterChip(
                selected = selected,
                onClick = { haptics.click(); selected = !selected },
                label = { Text(stringResource(R.string.easter_egg_gravity)) },
                leadingIcon = if (selected) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                } else {
                    null
                },
                colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            )
        }
    }
}
