package com.savoo.scclient.ui.screens.settings

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.view.SoundEffectConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.savoo.scclient.BuildConfig
import com.savoo.scclient.R
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sin
import kotlinx.coroutines.launch

private const val EASTER_EGG_TAP_THRESHOLD = 7

private data class VersionEasterEgg(
    val emoji: String,
    val titleRes: Int,
    val messageRes: Int,
)

private fun easterEggFor(version: String): VersionEasterEgg = when (version) {
    "0.5" -> VersionEasterEgg("🎧", R.string.easter_egg_title_0_5, R.string.easter_egg_message_0_5)
    else -> VersionEasterEgg("🥚", R.string.easter_egg_title_default, R.string.easter_egg_message_default)
}

private class ScratchSoundPlayer {
    private val sampleRate = 44100
    private val audioTrack = AudioTrack(
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build(),
        AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build(),
        AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            .coerceAtLeast(4096),
        AudioTrack.MODE_STREAM,
        AudioManager.AUDIO_SESSION_ID_GENERATE,
    )

    @Volatile private var targetHz = 0f
    @Volatile private var running = true
    private var phase = 0.0

    private val thread = Thread {
        val chunk = ShortArray(512)
        audioTrack.play()
        while (running) {
            val freq = targetHz
            if (freq <= 1f) {
                chunk.fill(0)
                phase = 0.0
            } else {
                val step = 2.0 * Math.PI * freq / sampleRate
                for (i in chunk.indices) {
                    phase += step
                    chunk[i] = (sin(phase) * Short.MAX_VALUE * 0.35).toInt().toShort()
                }
                phase %= 2.0 * Math.PI
            }
            audioTrack.write(chunk, 0, chunk.size)
        }
        audioTrack.stop()
        audioTrack.release()
    }.apply { start() }

    fun setAngularVelocity(degreesPerSecond: Float) {
        val speed = abs(degreesPerSecond)
        targetHz = if (speed < 20f) 0f else (80f + speed * 0.9f).coerceAtMost(900f)
    }

    fun release() {
        running = false
    }
}

data class AboutLink(
    val icon: ImageVector,
    val title: String,
    val url: String,
)

private val aboutLinks = listOf(
    AboutLink(Icons.Filled.Code, "GitHub", "https://github.com/savo-o/resona"),
    AboutLink(Icons.AutoMirrored.Filled.Send, "Telegram", "https://t.me/resona_tg"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutBottomSheet(
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val view = LocalView.current
    var easterEggTapCount by remember { mutableIntStateOf(0) }
    var showEasterEgg by remember { mutableStateOf(false) }

    val registerEasterEggTap = {
        view.playSoundEffect(SoundEffectConstants.CLICK)
        easterEggTapCount++
        if (easterEggTapCount >= EASTER_EGG_TAP_THRESHOLD) {
            easterEggTapCount = 0
            showEasterEgg = true
        }
    }

    if (showEasterEgg) {
        VinylEasterEggDialog(
            egg = easterEggFor(BuildConfig.VERSION_NAME),
            onDismiss = { showEasterEgg = false },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo_git),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { registerEasterEggTap() },
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )

            Text(
                text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { registerEasterEggTap() },
            )

            if (BuildConfig.BUILD_TYPE == "canary") {
                Text(
                    text = stringResource(R.string.about_build_canary),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(20.dp))

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            aboutLinks.forEach { link ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenUrl(link.url) }
                        .padding(horizontal = 4.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = link.icon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = when (link.title) {
                            "GitHub" -> stringResource(R.string.about_github)
                            else -> link.title
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun VinylEasterEggDialog(
    egg: VersionEasterEgg,
    onDismiss: () -> Unit,
) {
    val rotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val scratchPlayer = remember { ScratchSoundPlayer() }
    var isDragging by remember { mutableStateOf(false) }
    var angularVelocity by remember { mutableFloatStateOf(0f) }
    var isSpinning by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        onDispose { scratchPlayer.release() }
    }

    LaunchedEffect(isSpinning) {
        while (isSpinning) {
            rotation.animateTo(
                targetValue = rotation.value + 360f,
                animationSpec = tween(durationMillis = 2200, easing = LinearEasing),
            )
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(egg.titleRes),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(20.dp))

                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .rotate(rotation.value)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = {
                                    isDragging = true
                                    isSpinning = false
                                    scope.launch { rotation.stop() }
                                },
                                onDragEnd = {
                                    isDragging = false
                                    scratchPlayer.setAngularVelocity(0f)
                                    scope.launch {
                                        rotation.animateDecay(
                                            initialVelocity = angularVelocity,
                                            animationSpec = exponentialDecay(frictionMultiplier = 1.5f),
                                        )
                                        isSpinning = true
                                    }
                                },
                                onDragCancel = {
                                    isDragging = false
                                    scratchPlayer.setAngularVelocity(0f)
                                    isSpinning = true
                                },
                                onDrag = { change, _ ->
                                    val center = Offset(size.width / 2f, size.height / 2f)
                                    val prevPos = change.position - change.positionChange()
                                    val prevAngle = atan2(prevPos.y - center.y, prevPos.x - center.x)
                                    val currAngle = atan2(
                                        change.position.y - center.y,
                                        change.position.x - center.x,
                                    )
                                    var deltaDeg = Math.toDegrees((currAngle - prevAngle).toDouble()).toFloat()
                                    if (deltaDeg > 180f) deltaDeg -= 360f
                                    if (deltaDeg < -180f) deltaDeg += 360f

                                    val deltaTimeMs = (change.uptimeMillis - change.previousUptimeMillis)
                                        .coerceAtLeast(1L)
                                    angularVelocity = deltaDeg / deltaTimeMs * 1000f
                                    scratchPlayer.setAngularVelocity(angularVelocity)

                                    scope.launch { rotation.snapTo(rotation.value + deltaDeg) }
                                    change.consume()
                                },
                            )
                        },
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(color = Color(0xFF161616))
                        val grooveCount = 12
                        for (i in 1..grooveCount) {
                            drawCircle(
                                color = Color.White.copy(alpha = 0.07f),
                                radius = size.minDimension / 2f * (i / (grooveCount + 1f)),
                                style = Stroke(width = 1.dp.toPx()),
                            )
                        }
                    }
                    Image(
                        painter = painterResource(id = R.drawable.logo_git),
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(76.dp)
                            .clip(CircleShape),
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    text = stringResource(egg.messageRes),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(20.dp))

                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    }
}
