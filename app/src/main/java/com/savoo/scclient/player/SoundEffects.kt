package com.savoo.scclient.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class SoundPreset(val rate: Float, val reverb: Float, val bass: Float) {
    OFF(1f, 0f, 0f),
    SLOWED_REVERB(0.85f, 0.55f, 0.25f),
    SLOWED(0.8f, 0f, 0.15f),
    SPED_UP(1.2f, 0f, 0f),
    NIGHTCORE(1.3f, 0f, 0.1f),
    CUSTOM(1f, 0f, 0f),
}

data class SoundMode(
    val preset: SoundPreset = SoundPreset.OFF,
    val customRate: Float = 1f,
    val customReverb: Float = 0f,
    val customBass: Float = 0f,
) {
    val rate: Float get() = if (preset == SoundPreset.CUSTOM) customRate else preset.rate
    val reverb: Float get() = if (preset == SoundPreset.CUSTOM) customReverb else preset.reverb
    val bass: Float get() = if (preset == SoundPreset.CUSTOM) customBass else preset.bass
    val isActive: Boolean get() = preset != SoundPreset.OFF

    companion object {
        const val MIN_RATE = 0.7f
        const val MAX_RATE = 1.4f
    }
}

@Singleton
class SoundEffects @Inject constructor() {
    @Volatile var reverb: Float = 0f
    @Volatile var bass: Float = 0f
    @Volatile var outputRate: Float = 1f

    private val beatTimes = LongArray(BEAT_HISTORY)
    private val beatLevels = FloatArray(BEAT_HISTORY)
    @Volatile private var beatWrite = 0

    internal fun pushBeat(timeNanos: Long, level: Float) {
        val index = beatWrite % BEAT_HISTORY
        beatTimes[index] = timeNanos
        beatLevels[index] = level
        beatWrite = index + 1
    }

    fun beatLevelAt(timeNanos: Long): Float {
        val newest = beatWrite
        for (step in 1..BEAT_HISTORY) {
            val index = (newest - step + BEAT_HISTORY) % BEAT_HISTORY
            val time = beatTimes[index]
            if (time == 0L) return 0f
            if (time <= timeNanos) {
                return if (timeNanos - time > STALE_NANOS) 0f else beatLevels[index]
            }
        }
        return 0f
    }

    private companion object {
        const val BEAT_HISTORY = 512
        const val STALE_NANOS = 150_000_000L
    }
}

@UnstableApi
class SoundEffectsAudioProcessor(private val effects: SoundEffects) : BaseAudioProcessor() {

    private var sampleRate = 0
    private var channels = 0
    private var reverbs: Array<Freeverb> = emptyArray()
    private var shelves: Array<LowShelf> = emptyArray()
    private var appliedBass = -1f
    private var beatLowpass = 0f
    private var beatLowpassCoef = 0f
    private var beatSum = 0f
    private var beatCount = 0
    private var beatChunkFrames = 441
    private var beatPeak = 0f
    private var beatClockNanos = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun onFlush() {
        sampleRate = inputAudioFormat.sampleRate
        channels = inputAudioFormat.channelCount
        val processed = channels.coerceAtMost(2)
        reverbs = Array(processed) { channel -> Freeverb(sampleRate, stereoSpread = if (channel == 1) 23 else 0) }
        shelves = Array(processed) { LowShelf() }
        appliedBass = -1f
        beatLowpass = 0f
        beatLowpassCoef = if (sampleRate > 0) (1.0 - kotlin.math.exp(-2.0 * PI * BEAT_LOWPASS_HZ / sampleRate)).toFloat() else 0f
        beatChunkFrames = (sampleRate / 100).coerceAtLeast(1)
        beatSum = 0f
        beatCount = 0
        beatClockNanos = 0L
    }

    override fun onReset() {
        reverbs = emptyArray()
        shelves = emptyArray()
        sampleRate = 0
        channels = 0
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val output = replaceOutputBuffer(size)
        val reverb = effects.reverb
        val bass = effects.bass
        val input = inputBuffer.order(ByteOrder.nativeOrder())

        trackBeat(input.duplicate().order(ByteOrder.nativeOrder()), size)

        if ((reverb <= 0f && bass <= 0f) || channels == 0 || channels > 2) {
            output.put(input)
            output.flip()
            return
        }

        if (bass != appliedBass) {
            shelves.forEach { it.configure(sampleRate, BASS_FREQUENCY_HZ, bass * MAX_BASS_DB) }
            appliedBass = bass
        }
        val headroom = if (bass > 0f) 10f.pow(-(bass * MAX_BASS_DB) / 40f) else 1f
        val roomSize = 0.72f + 0.26f * reverb
        val wet = reverb * 0.9f
        val dry = 1f - reverb * 0.35f
        reverbs.forEach { it.setRoom(roomSize, damping = 0.45f) }

        val frames = size / (2 * channels)
        repeat(frames) {
            val left = input.getShort() / 32768f
            val right = if (channels == 2) input.getShort() / 32768f else left

            var l = if (bass > 0f) shelves[0].process(left) * headroom else left
            var r = if (channels == 2) {
                if (bass > 0f) shelves[1].process(right) * headroom else right
            } else {
                l
            }

            if (reverb > 0f) {
                val mono = (l + r) * FREEVERB_GAIN
                val wetL = reverbs[0].process(mono)
                if (channels == 2) {
                    val wetR = reverbs[1].process(mono)
                    l = l * dry + (wetL * 0.8f + wetR * 0.2f) * wet
                    r = r * dry + (wetR * 0.8f + wetL * 0.2f) * wet
                } else {
                    l = l * dry + wetL * wet
                }
            }

            output.putShort(toPcm(l))
            if (channels == 2) output.putShort(toPcm(r))
        }
        output.flip()
    }

    private fun trackBeat(input: ByteBuffer, size: Int) {
        if (channels == 0 || sampleRate == 0) return
        val now = System.nanoTime()
        val rate = effects.outputRate.coerceAtLeast(0.1f)
        val chunkNanos = (beatChunkFrames * 1_000_000_000L / (sampleRate * rate)).toLong()
        if (beatClockNanos < now - chunkNanos * 4) beatClockNanos = now
        val frames = size / (2 * channels)
        repeat(frames) {
            var mono = 0f
            repeat(channels) { mono += input.getShort() / 32768f }
            mono /= channels
            beatLowpass += (mono - beatLowpass) * beatLowpassCoef
            beatSum += beatLowpass * beatLowpass
            if (++beatCount >= beatChunkFrames) {
                val rms = sqrt(beatSum / beatCount)
                beatPeak = maxOf(rms, beatPeak * BEAT_PEAK_DECAY)
                val level = if (beatPeak < BEAT_SILENCE) 0f else ((rms / beatPeak - 0.6f) / 0.4f).coerceIn(0f, 1f)
                beatClockNanos += chunkNanos
                effects.pushBeat(beatClockNanos, level)
                beatSum = 0f
                beatCount = 0
            }
        }
    }

    private fun toPcm(value: Float): Short =
        (value * 32767f).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()

    private companion object {
        const val BASS_FREQUENCY_HZ = 100.0
        const val MAX_BASS_DB = 9.0f
        const val FREEVERB_GAIN = 0.015f
        const val BEAT_LOWPASS_HZ = 150.0
        const val BEAT_PEAK_DECAY = 0.997f
        const val BEAT_SILENCE = 0.004f
    }
}

private class Freeverb(sampleRate: Int, stereoSpread: Int) {
    private val scale = sampleRate / 44100.0
    private val combs = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
        .map { Comb(((it + stereoSpread) * scale).roundToInt().coerceAtLeast(1)) }
    private val allpasses = intArrayOf(556, 441, 341, 225)
        .map { Allpass(((it + stereoSpread) * scale).roundToInt().coerceAtLeast(1)) }

    fun setRoom(roomSize: Float, damping: Float) {
        combs.forEach {
            it.feedback = roomSize
            it.damp = damping
        }
    }

    fun process(input: Float): Float {
        var out = 0f
        for (comb in combs) out += comb.process(input)
        for (allpass in allpasses) out = allpass.process(out)
        return out * 3f
    }

    private class Comb(size: Int) {
        private val buffer = FloatArray(size)
        private var index = 0
        private var store = 0f
        var feedback = 0.8f
        var damp = 0.5f

        fun process(input: Float): Float {
            val output = buffer[index]
            store = output * (1f - damp) + store * damp
            buffer[index] = input + store * feedback
            if (++index >= buffer.size) index = 0
            return output
        }
    }

    private class Allpass(size: Int) {
        private val buffer = FloatArray(size)
        private var index = 0

        fun process(input: Float): Float {
            val buffered = buffer[index]
            buffer[index] = input + buffered * 0.5f
            if (++index >= buffer.size) index = 0
            return buffered - input
        }
    }
}

private class LowShelf {
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0
    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    fun configure(sampleRate: Int, frequency: Double, gainDb: Float) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2 * PI * frequency / sampleRate
        val cosW = cos(w0)
        val alpha = sin(w0) / 2 * sqrt(2.0)
        val sqrtA2Alpha = 2 * sqrt(a) * alpha
        val a0 = (a + 1) + (a - 1) * cosW + sqrtA2Alpha
        b0 = a * ((a + 1) - (a - 1) * cosW + sqrtA2Alpha) / a0
        b1 = 2 * a * ((a - 1) - (a + 1) * cosW) / a0
        b2 = a * ((a + 1) - (a - 1) * cosW - sqrtA2Alpha) / a0
        a1 = -2 * ((a - 1) + (a + 1) * cosW) / a0
        a2 = ((a + 1) + (a - 1) * cosW - sqrtA2Alpha) / a0
    }

    fun process(input: Float): Float {
        val x = input.toDouble()
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1
        x1 = x
        y2 = y1
        y1 = y
        return y.toFloat()
    }
}
