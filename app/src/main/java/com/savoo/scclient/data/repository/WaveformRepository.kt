package com.savoo.scclient.data.repository

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.LruCache
import com.savoo.scclient.data.local.OfflineDao
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.data.remote.SoundCloudApi
import com.savoo.scclient.debug.DebugLog
import com.savoo.scclient.di.PlainHttpClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class WaveformRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: SoundCloudApi,
    private val offlineDao: OfflineDao,
    @PlainHttpClient private val client: OkHttpClient,
) {
    private val TAG = "WaveformRepository"
    private val memory = LruCache<Long, FloatArray>(MEMORY_ENTRIES)
    private val remoteDir by lazy { File(context.cacheDir, "waveforms").apply { mkdirs() } }

    fun waveform(track: Track): Flow<FloatArray> = flow {
        memory.get(track.id)?.let {
            emit(it)
            return@flow
        }
        val audio = localAudio(track.id)
        val samples = if (audio != null) {
            readSamples(File(audio.parentFile, "${track.id}.wave"))?.also { emit(it) }
                ?: decodeProgressively(track.id, audio)
        } else {
            remoteWaveform(track)?.also { emit(it) }
        } ?: return@flow
        memory.put(track.id, samples)
    }.flowOn(Dispatchers.IO)

    suspend fun getWaveform(track: Track): FloatArray? = waveform(track).lastOrNull()

    fun cachedCount(): Int = cachedFiles().size

    fun clearCache(): Int {
        memory.evictAll()
        val files = cachedFiles()
        files.forEach { it.delete() }
        DebugLog.log(TAG, "clearCache: ${files.size} files")
        return files.size
    }

    private fun cachedFiles(): List<File> =
        remoteDir.listFiles().orEmpty().toList() +
            File(context.filesDir, "offline").listFiles { file -> file.name.endsWith(".wave") }.orEmpty()

    private suspend fun localAudio(trackId: Long): File? {
        val offline = offlineDao.getOfflineTrack(trackId) ?: return null
        return File(offline.localPath).takeIf { it.exists() && it.length() > 0 }
    }

    private suspend fun FlowCollector<FloatArray>.decodeProgressively(trackId: Long, audio: File): FloatArray? {
        val started = System.currentTimeMillis()
        var last: FloatArray? = null
        var complete = false
        try {
            decode(audio).collect { (samples, done) ->
                last = samples
                complete = done
                emit(samples)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DebugLog.log(TAG, "decode($trackId) failed: ${e.message}")
        }
        val samples = last?.takeIf { complete } ?: return null
        DebugLog.log(TAG, "decode($trackId): ${samples.size} samples in ${System.currentTimeMillis() - started} ms")
        runCatching { writeSamples(File(audio.parentFile, "$trackId.wave"), samples) }
        return samples
    }

    private suspend fun remoteWaveform(track: Track): FloatArray? {
        if (track.id <= 0L) return null
        val cached = File(remoteDir, "${track.id}.bin")
        readSamples(cached)?.let { return it }
        val url = jsonUrl(track.waveformUrl)
            ?: jsonUrl(runCatching { api.getTrack(track.id).waveformUrl }.getOrNull())
            ?: return null
        val samples = runCatching { fetch(url) }
            .onFailure { DebugLog.log(TAG, "fetch(${track.id}) failed: ${it.message}") }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        runCatching {
            writeSamples(cached, samples)
            trimRemoteCache()
        }
        return samples
    }

    private fun jsonUrl(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        if (raw.endsWith(".json")) return raw
        val name = raw.substringAfterLast('/').substringBeforeLast('.').takeIf { it.isNotBlank() } ?: return null
        return "https://wave.sndcdn.com/$name.json"
    }

    private fun fetch(url: String): FloatArray {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val json = JSONObject(response.body?.string().orEmpty())
            val height = json.optInt("height", 140).coerceAtLeast(1).toFloat()
            val array = json.getJSONArray("samples")
            return FloatArray(array.length()) { (array.optDouble(it, 0.0).toFloat() / height).coerceIn(0f, 1f) }
        }
    }

    private fun decode(file: File): Flow<Pair<FloatArray, Boolean>> = callbackFlow {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)
        val trackIndex = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        }
        val format = trackIndex?.let { extractor.getTrackFormat(it) }
        val mime = format?.getString(MediaFormat.KEY_MIME)
        val durationUs = format?.takeIf { it.containsKey(MediaFormat.KEY_DURATION) }?.getLong(MediaFormat.KEY_DURATION) ?: 0L
        if (trackIndex == null || format == null || mime == null || durationUs <= 0L) {
            extractor.release()
            close()
            awaitClose()
            return@callbackFlow
        }
        extractor.selectTrack(trackIndex)

        val sums = DoubleArray(LOCAL_SAMPLES)
        val counts = IntArray(LOCAL_SAMPLES)
        var channels = format.getIntegerOrNull(MediaFormat.KEY_CHANNEL_COUNT) ?: 1
        var sampleRate = format.getIntegerOrNull(MediaFormat.KEY_SAMPLE_RATE) ?: 44100
        var inputDone = false
        var released = false
        var lastEmit = 0L

        fun snapshot(): FloatArray {
            val levels = FloatArray(LOCAL_SAMPLES) { if (counts[it] == 0) Float.NaN else sqrt(sums[it] / counts[it]).toFloat() }
            var peak = 0f
            for (v in levels) if (!v.isNaN() && v > peak) peak = v
            if (peak <= 0f) peak = 1f
            return FloatArray(LOCAL_SAMPLES) { if (levels[it].isNaN()) Float.NaN else (levels[it] / peak).coerceIn(0f, 1f) }
        }

        fun finished(): FloatArray {
            val levels = snapshot()
            for (i in levels.indices) if (levels[i].isNaN()) levels[i] = if (i > 0) levels[i - 1] else 0f
            return levels
        }

        val thread = HandlerThread("waveform-decode").apply { start() }
        val handler = Handler(thread.looper)
        val codec = MediaCodec.createDecoderByType(mime)

        fun releaseAll() {
            if (released) return
            released = true
            runCatching { codec.stop() }
            runCatching { codec.release() }
            runCatching { extractor.release() }
            thread.quitSafely()
        }

        codec.setCallback(object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(mc: MediaCodec, index: Int) {
                if (released || inputDone) return
                val buffer = mc.getInputBuffer(index) ?: return
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) {
                    mc.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    inputDone = true
                } else {
                    mc.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                    extractor.advance()
                }
            }

            override fun onOutputBufferAvailable(mc: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                if (released) return
                val buffer = mc.getOutputBuffer(index)
                if (buffer != null && info.size > 0) {
                    buffer.position(info.offset)
                    buffer.limit(info.offset + info.size)
                    val shorts = buffer.order(ByteOrder.nativeOrder()).asShortBuffer()
                    val ch = channels.coerceAtLeast(1)
                    val frames = shorts.remaining() / ch
                    var frame = 0
                    while (frame < frames) {
                        val timeUs = info.presentationTimeUs + frame * 1_000_000L / sampleRate
                        val bucket = (timeUs * LOCAL_SAMPLES / durationUs).toInt().coerceIn(0, LOCAL_SAMPLES - 1)
                        val value = shorts.get(frame * ch) / 32768.0
                        sums[bucket] += value * value
                        counts[bucket]++
                        frame += SAMPLE_STRIDE
                    }
                }
                mc.releaseOutputBuffer(index, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    trySend(finished() to true)
                    releaseAll()
                    close()
                    return
                }
                val now = SystemClock.uptimeMillis()
                if (now - lastEmit >= EMIT_INTERVAL_MS) {
                    lastEmit = now
                    trySend(snapshot() to false)
                }
            }

            override fun onError(mc: MediaCodec, e: MediaCodec.CodecException) {
                releaseAll()
                close(e)
            }

            override fun onOutputFormatChanged(mc: MediaCodec, outputFormat: MediaFormat) {
                channels = outputFormat.getIntegerOrNull(MediaFormat.KEY_CHANNEL_COUNT) ?: channels
                sampleRate = outputFormat.getIntegerOrNull(MediaFormat.KEY_SAMPLE_RATE) ?: sampleRate
            }
        }, handler)
        codec.configure(format, null, null, 0)
        codec.start()
        awaitClose { handler.post { releaseAll() } }
    }.buffer(Channel.CONFLATED)

    private fun MediaFormat.getIntegerOrNull(key: String): Int? =
        if (containsKey(key)) getInteger(key) else null

    private fun readSamples(file: File): FloatArray? {
        val bytes = runCatching { file.takeIf { it.exists() }?.readBytes() }.getOrNull() ?: return null
        if (bytes.isEmpty()) return null
        return FloatArray(bytes.size) { (bytes[it].toInt() and 0xFF) / 255f }
    }

    private fun writeSamples(file: File, samples: FloatArray) {
        file.writeBytes(ByteArray(samples.size) { (samples[it] * 255f).toInt().coerceIn(0, 255).toByte() })
    }

    private fun trimRemoteCache() {
        val files = remoteDir.listFiles() ?: return
        if (files.size <= REMOTE_ENTRIES) return
        files.sortedBy { it.lastModified() }.take(files.size - REMOTE_ENTRIES).forEach { it.delete() }
    }

    companion object {
        private const val MEMORY_ENTRIES = 32
        private const val REMOTE_ENTRIES = 500
        private const val LOCAL_SAMPLES = 600
        private const val SAMPLE_STRIDE = 8
        private const val EMIT_INTERVAL_MS = 80L
    }
}
