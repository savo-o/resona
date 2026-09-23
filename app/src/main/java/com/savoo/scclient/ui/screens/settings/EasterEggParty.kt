package com.savoo.scclient.ui.screens.settings

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

const val PARTY_BPM = 128f

private const val SAMPLE_RATE = 44100
private const val LOOP_BEATS = 16
private const val TAIL_SAMPLES = SAMPLE_RATE / 2

private enum class Wave { SINE, SQUARE, SAW, NOISE }

private fun midiFreq(note: Int): Float = 440f * 2f.pow((note - 69) / 12f)

private fun mixVoice(
    buf: FloatArray,
    start: Int,
    length: Int,
    freqStart: Float,
    freqEnd: Float,
    amp: Float,
    decay: Float,
    wave: Wave,
    random: Random,
) {
    if (length <= 0 || start >= buf.size) return
    var phase = 0f
    var detuned = 0f
    var n = 0
    var i = start
    val end = min(buf.size, start + length)
    while (i < end) {
        val t = n / SAMPLE_RATE.toFloat()
        val glide = n.toFloat() / length
        val freq = freqStart + (freqEnd - freqStart) * glide
        val env = exp(-decay * t) * min(1f, n / 48f)
        val sample = when (wave) {
            Wave.SINE -> sin(phase * 2f * PI.toFloat())
            Wave.SQUARE -> if (phase < 0.5f) 1f else -1f
            Wave.SAW -> (2f * phase - 1f) * 0.6f + (2f * detuned - 1f) * 0.4f
            Wave.NOISE -> random.nextFloat() * 2f - 1f
        }
        buf[i] += sample * env * amp
        phase += freq / SAMPLE_RATE
        detuned += freq * 1.007f / SAMPLE_RATE
        if (phase >= 1f) phase -= 1f
        if (detuned >= 1f) detuned -= 1f
        i++
        n++
    }
}

private fun renderPartyLoop(): ShortArray {
    val random = Random(20260923)
    val beatSamples = (60f / PARTY_BPM * SAMPLE_RATE).toInt()
    val sixteenth = beatSamples / 4
    val total = beatSamples * LOOP_BEATS
    val buf = FloatArray(total + TAIL_SAMPLES)

    val chords = arrayOf(
        intArrayOf(66, 69, 73),
        intArrayOf(62, 66, 69),
        intArrayOf(69, 73, 76),
        intArrayOf(64, 68, 71),
    )
    val arpSteps = intArrayOf(0, 2, 3, 6, 8, 10, 11, 14)
    val arpDegrees = intArrayOf(0, 1, 2, 1, 3, 2, 1, 0)

    for (bar in 0 until 4) {
        val chord = chords[bar]
        val barStart = bar * 4 * beatSamples
        val root = chord[0] - 24

        for (beat in 0 until 4) {
            val at = barStart + beat * beatSamples
            mixVoice(buf, at, (0.22f * SAMPLE_RATE).toInt(), 150f, 46f, 0.95f, 15f, Wave.SINE, random)
            mixVoice(buf, at, (0.03f * SAMPLE_RATE).toInt(), 1400f, 600f, 0.25f, 120f, Wave.NOISE, random)

            if (beat % 2 == 1) {
                mixVoice(buf, at - sixteenth / 3, (0.09f * SAMPLE_RATE).toInt(), 1f, 1f, 0.16f, 45f, Wave.NOISE, random)
                mixVoice(buf, at, (0.16f * SAMPLE_RATE).toInt(), 1f, 1f, 0.34f, 20f, Wave.NOISE, random)
            }

            for (half in 0 until 2) {
                val hatAt = at + half * beatSamples / 2
                val hatAmp = if (half == 1) 0.15f else 0.08f
                mixVoice(buf, hatAt, (0.06f * SAMPLE_RATE).toInt(), 1f, 1f, hatAmp, 75f, Wave.NOISE, random)
                mixVoice(buf, hatAt, (0.11f * SAMPLE_RATE).toInt(), midiFreq(root), midiFreq(root), 0.38f, 7f, Wave.SQUARE, random)
            }

            val stabAt = at + beatSamples / 2
            chord.forEach { note ->
                mixVoice(buf, stabAt, (0.13f * SAMPLE_RATE).toInt(), midiFreq(note), midiFreq(note), 0.09f, 13f, Wave.SQUARE, random)
            }
        }

        arpSteps.forEachIndexed { index, step ->
            val degree = arpDegrees[index]
            val note = chord[degree % 3] + 12 * (degree / 3) + 12
            mixVoice(
                buf,
                barStart + step * sixteenth,
                (0.3f * SAMPLE_RATE).toInt(),
                midiFreq(note),
                midiFreq(note),
                0.2f,
                6f,
                Wave.SAW,
                random,
            )
        }
    }

    val rollStart = total - beatSamples
    for (hit in 0 until 8) {
        val at = rollStart + hit * (beatSamples / 8)
        mixVoice(buf, at, (0.07f * SAMPLE_RATE).toInt(), 1f, 1f, 0.1f + hit * 0.035f, 40f, Wave.NOISE, random)
    }

    for (i in total until buf.size) {
        buf[i - total] += buf[i]
    }

    var peak = 0f
    for (i in 0 until total) {
        val v = kotlin.math.abs(buf[i])
        if (v > peak) peak = v
    }
    val gain = if (peak > 0f) 0.85f / peak else 1f
    val pcm = ShortArray(total)
    for (i in 0 until total) {
        pcm[i] = (tanh(buf[i] * gain * 1.2f) * Short.MAX_VALUE * 0.92f).toInt().toShort()
    }
    return pcm
}

class PartyPlayer(context: Context) {

    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var track: AudioTrack? = null
    private var focusRequest: AudioFocusRequest? = null

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    suspend fun prepare() {
        if (track != null) return
        val pcm = withContext(Dispatchers.Default) { renderPartyLoop() }
        val built = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        built.write(pcm, 0, pcm.size)
        built.setLoopPoints(0, pcm.size, -1)
        track = built
    }

    fun play() {
        val ready = track ?: return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .build()
        focusRequest = request
        audioManager.requestAudioFocus(request)
        runCatching { ready.play() }
    }

    fun stop() {
        track?.let { active ->
            runCatching { active.stop() }
            runCatching { active.release() }
        }
        track = null
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }
}
