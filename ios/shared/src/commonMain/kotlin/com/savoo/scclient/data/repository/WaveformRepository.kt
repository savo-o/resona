package com.savoo.scclient.data.repository

import com.savoo.scclient.compat.*
import kotlinx.coroutines.IO
import com.savoo.scclient.compat.Context
import com.savoo.scclient.data.local.OfflineDao
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.data.remote.SoundCloudApi
import com.savoo.scclient.debug.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.Flow
import com.savoo.scclient.compat.http.OkHttpClient
import com.savoo.scclient.compat.http.Request
import com.savoo.scclient.compat.JSONObject
import com.savoo.scclient.compat.File
class WaveformRepository(
    private val context: Context,
    private val api: SoundCloudApi,
    private val offlineDao: OfflineDao,
    private val client: OkHttpClient,
) {
    private val TAG = "WaveformRepository"
    private val memory = LinkedHashMap<Long, FloatArray>()
    private val remoteDir by lazy { File(context.cacheDir, "waveforms").apply { mkdirs() } }

    fun waveform(track: Track): Flow<FloatArray> = flow {
        memory.get(track.id)?.let {
            emit(it)
            return@flow
        }
        val audio = localAudio(track.id)
        val samples = audio?.parentFile?.let { readSamples(File(it, "${track.id}.wave")) }?.also { emit(it) }
            ?: remoteWaveform(track)?.also { emit(it) }
            ?: return@flow
        memory[track.id] = samples
        while (memory.size > MEMORY_ENTRIES) memory.remove(memory.keys.first())
    }.flowOn(Dispatchers.IO)

    suspend fun getWaveform(track: Track): FloatArray? = waveform(track).lastOrNull()

    fun cachedCount(): Int = cachedFiles().size

    fun clearCache(): Int {
        memory.clear()
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
        return File(offline.localPath).takeIf { !it.isDirectory && it.exists() && it.length() > 0 }
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
    }
}
