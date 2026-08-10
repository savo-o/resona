package com.savoo.scclient.ui.screens.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.savoo.scclient.R
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor

private const val FREQ_BINS = 40
private const val TIME_ROWS = 84
private const val ICON_W = 24
private const val ICON_H = 24
private const val ICON_OFFSET_X = (FREQ_BINS - ICON_W) / 2
private const val ICON_OFFSET_Y = (TIME_ROWS - ICON_H) / 2
private const val MSG_H = 14
private const val MSG_OFFSET_Y = (TIME_ROWS - MSG_H) / 2

private const val FRAME_MS = 40L
private const val MORPH_IN_MS = 1100L
private const val HOLD_MS = 2400L
private const val MORPH_OUT_MS = 1100L

private const val FREQ_MIN = 80f
private const val FREQ_MAX = 4200f

private val MESSAGES = listOf("RES0NA", "HELLO", "THANK YOU")
private val DEEP_SPACE_LINES = listOf("ПРОШУ", "ВЫКИНИ МЕНЯ", "В ОТКРЫТЫЙ", "КОСМОС")

private enum class EventKind { SOUND, ICON, MESSAGE }
private enum class IconKind { HEART, STAR, CAT, FACE, NOTE, LOGO }

private data class Note(val freqHz: Float, val startMs: Int, val durMs: Int, val amp: Float)

private class Jingle(val notes: List<Note>) {
    val totalDurationMs: Long = (notes.maxOf { it.startMs + it.durMs } + 150).toLong()
}

private val JINGLES = listOf(
    Jingle(
        listOf(
            Note(523.25f, 0, 260, 0.5f),
            Note(659.25f, 140, 260, 0.45f),
            Note(783.99f, 280, 380, 0.5f),
            Note(1046.5f, 460, 420, 0.4f),
        )
    ),
    Jingle(
        listOf(
            Note(880f, 0, 220, 0.45f),
            Note(783.99f, 120, 220, 0.4f),
            Note(659.25f, 240, 260, 0.45f),
            Note(523.25f, 400, 420, 0.5f),
        )
    ),
    Jingle(
        listOf(
            Note(659.25f, 0, 160, 0.5f),
            Note(987.77f, 90, 300, 0.42f),
        )
    ),
)

private object JingleSynth {
    const val SAMPLE_RATE = 44100

    fun render(jingle: Jingle): ShortArray {
        val totalSamples = (jingle.totalDurationMs * SAMPLE_RATE / 1000L).toInt().coerceAtLeast(1)
        val buffer = FloatArray(totalSamples)
        for (note in jingle.notes) {
            val startSample = (note.startMs * SAMPLE_RATE / 1000f).toInt()
            val durSamples = (note.durMs * SAMPLE_RATE / 1000f).toInt().coerceAtLeast(1)
            val attackSamples = (0.01f * SAMPLE_RATE).toInt().coerceAtLeast(1)
            for (i in 0 until durSamples) {
                val idx = startSample + i
                if (idx >= totalSamples) break
                val tSec = i / SAMPLE_RATE.toFloat()
                val envelope = if (i < attackSamples) {
                    i / attackSamples.toFloat()
                } else {
                    exp(-4.5f * (i - attackSamples) / durSamples.toFloat())
                }
                val fundamental = sin(2.0 * Math.PI * note.freqHz * tSec).toFloat()
                val harmonic = 0.28f * sin(2.0 * Math.PI * note.freqHz * 2f * tSec).toFloat()
                buffer[idx] += (fundamental + harmonic) * envelope * note.amp
            }
        }
        val out = ShortArray(totalSamples)
        for (i in buffer.indices) {
            out[i] = (buffer[i].coerceIn(-1f, 1f) * Short.MAX_VALUE * 0.6f).toInt().toShort()
        }
        return out
    }
}

private fun binForFreq(freq: Float): Float {
    val frac = ((ln(freq) - ln(FREQ_MIN)) / (ln(FREQ_MAX) - ln(FREQ_MIN))).coerceIn(0f, 1f)
    return frac * (FREQ_BINS - 1)
}

private fun generateAmbientSlice(time: Float): FloatArray = FloatArray(FREQ_BINS) { f ->
    val freqPos = f / (FREQ_BINS - 1f)
    val decay = exp(-freqPos * 1.3f)
    val base = 0.30f + 0.22f * decay * (0.5f + 0.5f * sin(time * 0.7f + f * 0.35f))
    val wobble = 0.10f * sin(time * 1.6f + f * 0.9f + sin(time * 0.29f) * 2f)
    val noise = (Random.nextFloat() - 0.5f) * 0.08f
    var v = (base + wobble + noise).coerceIn(0f, 1f)
    if (Random.nextFloat() < 0.012f) v = v.coerceAtLeast(0.8f + Random.nextFloat() * 0.15f)
    v
}

private fun generateNoteSlice(jingle: Jingle, elapsedMs: Long): FloatArray {
    val slice = FloatArray(FREQ_BINS) { 0.05f + Random.nextFloat() * 0.05f }
    for (note in jingle.notes) {
        val local = elapsedMs - note.startMs
        if (local < 0 || local > note.durMs) continue
        val attackWindow = note.durMs * 0.08f
        val env = if (local < attackWindow) {
            (local / attackWindow.coerceAtLeast(1f))
        } else {
            exp(-3.5f * (local - attackWindow) / note.durMs.toFloat())
        }
        val centerBin = binForFreq(note.freqHz)
        val harmBin = binForFreq(note.freqHz * 2f)
        for (f in 0 until FREQ_BINS) {
            val dist = abs(f - centerBin)
            slice[f] = (slice[f] + exp(-dist * dist / 2.2f) * env * note.amp * 1.8f).coerceAtMost(1f)
            val hDist = abs(f - harmBin)
            slice[f] = (slice[f] + exp(-hDist * hDist / 2.2f) * env * note.amp * 0.5f).coerceAtMost(1f)
        }
    }
    return slice
}

private fun pointInTriangle(
    px: Float, py: Float,
    x1: Float, y1: Float,
    x2: Float, y2: Float,
    x3: Float, y3: Float,
): Boolean {
    fun sign(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float) =
        (ax - cx) * (by - cy) - (bx - cx) * (ay - cy)
    val d1 = sign(px, py, x1, y1, x2, y2)
    val d2 = sign(px, py, x2, y2, x3, y3)
    val d3 = sign(px, py, x3, y3, x1, y1)
    val hasNeg = d1 < 0 || d2 < 0 || d3 < 0
    val hasPos = d1 > 0 || d2 > 0 || d3 > 0
    return !(hasNeg && hasPos)
}

private fun pointInPolygon(x: Float, y: Float, poly: List<Pair<Float, Float>>): Boolean {
    var inside = false
    var j = poly.size - 1
    for (i in poly.indices) {
        val (xi, yi) = poly[i]
        val (xj, yj) = poly[j]
        if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) {
            inside = !inside
        }
        j = i
    }
    return inside
}

private fun isHeart(nx: Float, ny: Float): Boolean {
    val hx = nx * 1.15f
    val hy = -ny * 1.15f - 0.15f
    val a = hx * hx + hy * hy - 1f
    return a * a * a - hx * hx * hy * hy * hy <= 0f
}

private fun isStar(nx: Float, ny: Float): Boolean {
    val points = 5
    val outerR = 1.0f
    val innerR = 0.42f
    val verts = (0 until points * 2).map { i ->
        val r = if (i % 2 == 0) outerR else innerR
        val angle = -Math.PI / 2 + i * Math.PI / points
        Pair((r * cos(angle)).toFloat(), (r * sin(angle)).toFloat())
    }
    return pointInPolygon(nx, ny, verts)
}

private fun isFace(nx: Float, ny: Float): Boolean {
    val r = sqrt(nx * nx + ny * ny)
    val ring = r in 0.72f..1.0f
    val leftEye = (nx + 0.32f).pow(2) + (ny + 0.15f).pow(2) <= 0.05f
    val rightEye = (nx - 0.32f).pow(2) + (ny + 0.15f).pow(2) <= 0.05f
    val smileDist = sqrt(nx * nx + ((ny - 0.05f) * 1.4f).pow(2))
    val smile = ny > 0.1f && abs(smileDist - 0.55f) < 0.09f
    return ring || leftEye || rightEye || smile
}

private fun isCat(nx: Float, ny: Float): Boolean {
    val faceBody = sqrt(nx * nx + (ny * 1.05f).pow(2)) <= 0.68f
    val leftEar = pointInTriangle(nx, ny, -0.62f, -0.35f, -0.28f, -0.98f, -0.02f, -0.38f)
    val rightEar = pointInTriangle(nx, ny, 0.62f, -0.35f, 0.28f, -0.98f, 0.02f, -0.38f)
    val leftEye = (nx + 0.24f).pow(2) + (ny + 0.05f).pow(2) <= 0.02f
    val rightEye = (nx - 0.24f).pow(2) + (ny + 0.05f).pow(2) <= 0.02f
    val nose = pointInTriangle(nx, ny, -0.06f, 0.18f, 0.06f, 0.18f, 0f, 0.30f)
    val whiskerL = ny in 0.12f..0.16f && nx in -0.66f..-0.40f
    val whiskerR = ny in 0.12f..0.16f && nx in 0.40f..0.66f
    val body = faceBody && !leftEye && !rightEye
    return body || leftEar || rightEar || nose || whiskerL || whiskerR
}

private fun isNote(nx: Float, ny: Float): Boolean {
    val head = (nx - 0.05f).pow(2) / 0.16f + (ny - 0.55f).pow(2) / 0.10f <= 1f
    val stem = nx in 0.28f..0.36f && ny in -0.75f..0.55f
    val flag = pointInTriangle(nx, ny, 0.36f, -0.75f, 0.85f, -0.55f, 0.36f, -0.25f)
    return head || stem || flag
}

private fun emptyGrid(): Array<FloatArray> =
    Array(TIME_ROWS) { FloatArray(FREQ_BINS) { 0.08f + Random.nextFloat() * 0.05f } }

private var cachedLogoAlpha: Array<FloatArray>? = null

private fun logoAlphaGrid(context: Context): Array<FloatArray> {
    cachedLogoAlpha?.let { return it }
    val built = runCatching {
        val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.logo_git)
        val scaled = Bitmap.createScaledBitmap(bitmap, ICON_W, ICON_H, true)
        Array(ICON_W) { ix -> FloatArray(ICON_H) { iy -> AndroidColor.alpha(scaled.getPixel(ix, iy)) / 255f } }
    }.getOrNull() ?: Array(ICON_W) { FloatArray(ICON_H) { 0f } }
    cachedLogoAlpha = built
    return built
}

private fun buildIconGrid(kind: IconKind, context: Context): Array<FloatArray> {
    val grid = emptyGrid()
    if (kind == IconKind.LOGO) {
        val alpha = logoAlphaGrid(context)
        for (ix in 0 until ICON_W) {
            for (iy in 0 until ICON_H) {
                val a = alpha[ix][iy]
                if (a > 0.35f) {
                    grid[ICON_OFFSET_Y + iy][ICON_OFFSET_X + ix] = (0.5f + a * 0.5f).coerceIn(0f, 1f)
                }
            }
        }
        return grid
    }
    val mask: (Float, Float) -> Boolean = when (kind) {
        IconKind.HEART -> ::isHeart
        IconKind.STAR -> ::isStar
        IconKind.CAT -> ::isCat
        IconKind.FACE -> ::isFace
        IconKind.NOTE -> ::isNote
        IconKind.LOGO -> error("handled above")
    }
    for (iy in 0 until ICON_H) {
        for (ix in 0 until ICON_W) {
            val nx = (ix + 0.5f - ICON_W / 2f) / (ICON_W / 2f)
            val ny = (iy + 0.5f - ICON_H / 2f) / (ICON_H / 2f)
            if (mask(nx, ny)) {
                grid[ICON_OFFSET_Y + iy][ICON_OFFSET_X + ix] = 0.92f + Random.nextFloat() * 0.06f
            }
        }
    }
    return grid
}

private fun renderTextLineInto(grid: Array<FloatArray>, text: String, offsetY: Int) {
    val scale = 4
    val w = FREQ_BINS * scale
    val h = MSG_H * scale
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
    val canvas = AndroidCanvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        textSize = h * 0.7f
    }
    while (paint.measureText(text) > w * 0.94f && paint.textSize > 4f) {
        paint.textSize -= 1f
    }
    val fm = paint.fontMetrics
    val baseline = h / 2f - (fm.ascent + fm.descent) / 2f
    canvas.drawText(text, w / 2f, baseline, paint)
    for (gx in 0 until FREQ_BINS) {
        for (gy in 0 until MSG_H) {
            var sum = 0
            for (sx in 0 until scale) {
                for (sy in 0 until scale) {
                    sum += AndroidColor.alpha(bitmap.getPixel(gx * scale + sx, gy * scale + sy))
                }
            }
            val avg = sum / (scale * scale * 255f)
            val targetRow = offsetY + gy
            if (avg > 0.12f && targetRow in 0 until TIME_ROWS) {
                grid[targetRow][gx] = (0.55f + avg * 0.4f).coerceIn(0f, 1f)
            }
        }
    }
}

private fun buildMessageGrid(text: String): Array<FloatArray> {
    val grid = emptyGrid()
    renderTextLineInto(grid, text, MSG_OFFSET_Y)
    return grid
}

private fun buildMultilineMessageGrid(lines: List<String>): Array<FloatArray> {
    val grid = emptyGrid()
    val totalH = lines.size * MSG_H
    val startY = ((TIME_ROWS - totalH) / 2).coerceAtLeast(0)
    lines.forEachIndexed { index, line -> renderTextLineInto(grid, line, startY + index * MSG_H) }
    return grid
}

private fun lerpGrid(a: Array<FloatArray>, b: Array<FloatArray>, frac: Float, jitter: Float): Array<FloatArray> =
    Array(TIME_ROWS) { t ->
        FloatArray(FREQ_BINS) { f ->
            val v = a[t][f] + (b[t][f] - a[t][f]) * frac
            (v + (Random.nextFloat() - 0.5f) * jitter).coerceIn(0f, 1f)
        }
    }

private fun isAudioAllowed(context: Context): Boolean {
    val am = context.getSystemService(AudioManager::class.java) ?: return false
    if (am.ringerMode != AudioManager.RINGER_MODE_NORMAL) return false
    return am.getStreamVolume(AudioManager.STREAM_MUSIC) > 0
}

private fun isDeepOffline(context: Context): Boolean {
    val wifiOff = runCatching {
        val wm = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        wm?.isWifiEnabled == false
    }.getOrDefault(false)
    val bluetoothOff = runCatching {
        Settings.Global.getInt(context.contentResolver, "bluetooth_on", 1) == 0
    }.getOrDefault(false)
    val noNetwork = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        cm?.activeNetwork == null
    }.getOrDefault(false)
    return wifiOff && bluetoothOff && noNetwork
}

private fun tryCreateTrack(pcm: ShortArray): AudioTrack? = runCatching {
    val track = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setSampleRate(JingleSynth.SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
        )
        .setBufferSizeInBytes(pcm.size * 2)
        .setTransferMode(AudioTrack.MODE_STATIC)
        .build()
    track.write(pcm, 0, pcm.size)
    track
}.getOrNull()

private class SpectrumEngine(private val context: Context) {
    var grid by mutableStateOf(emptyGrid())
        private set

    private val timeSlices = ArrayDeque<FloatArray>().apply {
        repeat(TIME_ROWS) { addLast(FloatArray(FREQ_BINS)) }
    }
    private var t = 0f
    private var activeTrack: AudioTrack? = null
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        job = scope.launch { loop() }
    }

    fun release() {
        job?.cancel()
        activeTrack?.let { runCatching { it.stop(); it.release() } }
        activeTrack = null
    }

    private fun snapshot(): Array<FloatArray> = Array(timeSlices.size) { i -> timeSlices[i] }

    private fun publish(grid: Array<FloatArray>? = null) {
        this.grid = grid ?: snapshot()
    }

    private fun advanceAmbientSlice() {
        t += FRAME_MS / 1000f
        timeSlices.removeLast()
        timeSlices.addFirst(generateAmbientSlice(t))
    }

    private suspend fun loop() {
        repeat(TIME_ROWS) { advanceAmbientSlice() }
        publish()
        while (currentCoroutineContext().isActive) {
            runAmbientFor(Random.nextLong(3_500, 9_000))
            if (!currentCoroutineContext().isActive) break
            runRandomEvent()
        }
    }

    private suspend fun runAmbientFor(durationMs: Long) {
        var elapsed = 0L
        while (elapsed < durationMs && currentCoroutineContext().isActive) {
            delay(FRAME_MS)
            elapsed += FRAME_MS
            advanceAmbientSlice()
            publish()
        }
    }

    private suspend fun animatePhase(durationMs: Long, onFrame: (frac: Float) -> Unit) {
        var elapsed = 0L
        while (elapsed < durationMs && currentCoroutineContext().isActive) {
            onFrame((elapsed.toFloat() / durationMs).coerceIn(0f, 1f))
            delay(FRAME_MS)
            elapsed += FRAME_MS
        }
        if (currentCoroutineContext().isActive) onFrame(1f)
    }

    private suspend fun runRandomEvent() {
        val deepOffline = isDeepOffline(context)
        if (deepOffline && Random.nextFloat() < 0.45f) {
            runPatternEvent(buildMultilineMessageGrid(DEEP_SPACE_LINES))
            return
        }
        val allowSound = isAudioAllowed(context)
        val kind = if (allowSound && Random.nextFloat() < 0.7f) {
            EventKind.SOUND
        } else {
            listOf(EventKind.ICON, EventKind.MESSAGE).random()
        }
        when (kind) {
            EventKind.SOUND -> runSoundEvent()
            EventKind.ICON -> runPatternEvent(buildIconGrid(IconKind.entries.random(), context))
            EventKind.MESSAGE -> {
                val target = if (deepOffline) buildMultilineMessageGrid(DEEP_SPACE_LINES) else buildMessageGrid(MESSAGES.random())
                runPatternEvent(target)
            }
        }
    }

    private suspend fun runPatternEvent(target: Array<FloatArray>) {
        val start = snapshot()
        animatePhase(MORPH_IN_MS) { frac -> publish(lerpGrid(start, target, frac, 0.03f)) }
        animatePhase(HOLD_MS) { publish(lerpGrid(target, target, 1f, 0.04f)) }
        val fresh = Array(TIME_ROWS) { i -> generateAmbientSlice(t + i * 0.05f) }
        animatePhase(MORPH_OUT_MS) { frac -> publish(lerpGrid(target, fresh, frac, 0.03f)) }
        timeSlices.clear()
        fresh.forEach { timeSlices.addLast(it) }
    }

    private suspend fun runSoundEvent() {
        val jingle = JINGLES.random()
        val pcm = JingleSynth.render(jingle)
        val track = tryCreateTrack(pcm)
        try {
            track?.let {
                activeTrack = it
                it.play()
            }
            var elapsed = 0L
            while (elapsed < jingle.totalDurationMs && currentCoroutineContext().isActive) {
                timeSlices.removeLast()
                timeSlices.addFirst(generateNoteSlice(jingle, elapsed))
                publish()
                delay(FRAME_MS)
                elapsed += FRAME_MS
            }
        } finally {
            track?.let { runCatching { it.stop(); it.release() } }
            if (activeTrack === track) activeTrack = null
        }
    }
}

private fun valueToColor(v: Float): Color {
    val bg = Color(0xFF070C16)
    val deepBlue = Color(0xFF13306B)
    val blue = Color(0xFF3E8EFF)
    val orange = Color(0xFFFF7A1F)
    val hot = Color(0xFFFFD166)
    return when {
        v < 0.18f -> lerp(bg, deepBlue, v / 0.18f)
        v < 0.62f -> lerp(deepBlue, blue, (v - 0.18f) / 0.44f)
        v < 0.85f -> lerp(blue, orange, (v - 0.62f) / 0.23f)
        else -> lerp(orange, hot, ((v - 0.85f) / 0.15f).coerceIn(0f, 1f))
    }
}

@Composable
private fun SpectrogramCanvas(grid: Array<FloatArray>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val rows = grid.size
        val cols = grid.firstOrNull()?.size ?: return@Canvas
        val cellW = size.width / cols
        val cellH = size.height / rows
        for (t in 0 until rows) {
            val slice = grid[t]
            for (f in 0 until cols) {
                drawRect(
                    color = valueToColor(slice[f]),
                    topLeft = Offset(f * cellW, t * cellH),
                    size = Size((cellW - 0.6f).coerceAtLeast(1f), (cellH - 0.6f).coerceAtLeast(1f)),
                )
            }
        }
    }
}

@Composable
fun SpectrumEasterEggDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val engine = remember { SpectrumEngine(context.applicationContext) }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        engine.start(scope)
        onDispose { engine.release() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF070C16)),
        ) {
            SpectrogramCanvas(grid = engine.grid, modifier = Modifier.fillMaxSize())

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.close),
                    tint = Color.White.copy(alpha = 0.55f),
                )
            }

            Text(
                text = stringResource(R.string.easter_egg_caption),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.35f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp),
            )
        }
    }
}
