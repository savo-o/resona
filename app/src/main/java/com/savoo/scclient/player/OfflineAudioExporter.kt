package com.savoo.scclient.player

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.provider.DocumentsContract
import com.savoo.scclient.data.model.OfflineTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineAudioExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val offline: OfflineTrackManager,
) {
    suspend fun prepare(trackId: Long): File = withContext(Dispatchers.IO) {
        val track = offline.getOfflineTrack(trackId) ?: throw IOException("Track is not downloaded")
        val source = File(track.localPath)
        check(source.isFile) { "Downloaded audio is missing" }
        val directory = File(context.cacheDir, "audio-export").apply { mkdirs() }
        val staging = File.createTempFile("track-", ".tmp", directory)
        var prepared: File? = null
        try {
            val mime = if (source.extension == "m3u8") mergeHls(source, staging) else {
                source.inputStream().use { input -> staging.outputStream().use { input.copyTo(it) } }
                when (source.extension.lowercase()) {
                    "mp3" -> "audio/mpeg"
                    "m4a", "mp4" -> "audio/mp4a-latm"
                    else -> throw IOException("Export supports MP3 and M4A audio")
                }
            }
            val cover = track.artworkUrl?.let { uri ->
                runCatching {
                    context.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() }
                }.getOrNull()
            }
            val extension = if (mime == "audio/mpeg") "mp3" else "m4a"
            val result = File(directory, "${track.username} - ${track.title}".replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").take(120) + "-${System.nanoTime()}.$extension")
            prepared = result
            AudioFileTags.write(staging, result, track, cover)
            result
        } catch (e: Exception) {
            prepared?.delete()
            throw e
        } finally {
            staging.delete()
        }
    }

    suspend fun export(trackId: Long, tree: Uri) = withContext(Dispatchers.IO) {
        val file = prepare(trackId)
        var destination: Uri? = null
        try {
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val track = offline.getOfflineTrack(trackId) ?: throw IOException("Track is not downloaded")
            val name = "${track.username} - ${track.title} [${track.trackId}].${file.extension}"
                .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            destination = DocumentsContract.createDocument(context.contentResolver, parent,
                if (file.extension == "mp3") "audio/mpeg" else "audio/mp4", name)
                ?: throw IOException("Cannot create audio file")
            context.contentResolver.openOutputStream(destination, "w")?.use { output ->
                file.inputStream().use { input ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val size = input.read(buffer)
                        if (size < 0) break
                        output.write(buffer, 0, size)
                    }
                }
            } ?: throw IOException("Cannot open destination")
        } catch (e: Exception) {
            destination?.let { runCatching { DocumentsContract.deleteDocument(context.contentResolver, it) } }
            throw e
        } finally {
            file.delete()
        }
    }

    private suspend fun mergeHls(manifest: File, output: File): String {
        val lines = manifest.readLines()
        check(lines.any { it == "#EXT-X-ENDLIST" }) { "Incomplete HLS download" }
        check(lines.none { it.startsWith("#EXT-X-KEY") }) { "Encrypted HLS cannot be exported" }
        val segments = mutableListOf<Pair<File, File?>>()
        var initialization: File? = null
        fun local(name: String): File {
            val file = File(manifest.parentFile, name).canonicalFile
            check(file.parentFile == manifest.parentFile?.canonicalFile && file.isFile) { "Invalid local HLS file" }
            return file
        }
        lines.forEach { line ->
            if (line.startsWith("#EXT-X-MAP")) {
                initialization = local(Regex("URI=\"([^\"]+)\"").find(line)?.groupValues?.get(1)
                    ?: throw IOException("Invalid HLS initialization"))
            } else if (line.isNotBlank() && !line.startsWith("#")) {
                segments += local(line.trim()) to initialization
            }
        }
        check(segments.isNotEmpty()) { "Empty HLS download" }
        var mime: String? = null
        var muxer: MediaMuxer? = null
        var muxerTrack = -1
        var rate = 0
        var channels = 0
        var timeline = 0L
        val raw = output.outputStream().buffered()
        try {
            segments.forEach { (segment, init) ->
                currentCoroutineContext().ensureActive()
                val combined = if (init != null) File.createTempFile("fragment-", ".mp4", context.cacheDir).apply {
                    outputStream().use { out ->
                        init.inputStream().use { it.copyTo(out) }
                        segment.inputStream().use { it.copyTo(out) }
                    }
                } else null
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource((combined ?: segment).absolutePath)
                    val index = (0 until extractor.trackCount).firstOrNull {
                        extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                    } ?: throw IOException("HLS segment has no audio")
                    val format = extractor.getTrackFormat(index)
                    val segmentMime = format.getString(MediaFormat.KEY_MIME) ?: throw IOException("Missing codec")
                    if (mime == null) {
                        mime = segmentMime
                        rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        check(mime == "audio/mpeg" || mime == "audio/mp4a-latm") { "Unsupported HLS audio codec: $mime" }
                        if (mime != "audio/mpeg") {
                            raw.close()
                            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                            muxerTrack = muxer!!.addTrack(format)
                            muxer!!.start()
                        }
                    }
                    check(segmentMime == mime && rate == format.getInteger(MediaFormat.KEY_SAMPLE_RATE) &&
                        channels == format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)) { "HLS audio format changed" }
                    extractor.selectTrack(index)
                    val buffer = ByteBuffer.allocate(2 * 1024 * 1024)
                    var firstTime = -1L
                    var endTime = timeline
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        buffer.clear()
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) break
                        val time = extractor.sampleTime
                        if (firstTime < 0) firstTime = time
                        val presentation = timeline + (time - firstTime).coerceAtLeast(0L)
                        val samples = if (mime == "audio/mpeg") {
                            if (size > 1 && ((buffer.get(1).toInt() shr 3) and 3) == 3) 1152L else 576L
                        } else 1024L
                        val duration = samples * 1000000L / rate
                        endTime = presentation + duration
                        if (mime == "audio/mpeg") {
                            val bytes = ByteArray(size)
                            buffer.position(0)
                            buffer.get(bytes)
                            val versionOne = ((bytes.getOrNull(1)?.toInt() ?: 0) shr 3) and 3 == 3
                            val mono = ((bytes.getOrNull(3)?.toInt() ?: 0) shr 6) and 3 == 3
                            val crc = (bytes.getOrNull(1)?.toInt() ?: 1) and 1 == 0
                            val xingOffset = 4 + (if (crc) 2 else 0) +
                                if (versionOne) { if (mono) 17 else 32 } else { if (mono) 9 else 17 }
                            fun marker(offset: Int): String = if (offset + 4 <= bytes.size)
                                String(bytes, offset, 4, Charsets.US_ASCII) else ""
                            if (marker(xingOffset) !in setOf("Xing", "Info") && marker(36) != "VBRI") {
                                raw.write(bytes)
                            }
                        } else {
                            buffer.position(0)
                            buffer.limit(size)
                            muxer!!.writeSampleData(muxerTrack, buffer, MediaCodec.BufferInfo().apply {
                                set(0, size, presentation, extractor.sampleFlags)
                            })
                        }
                        extractor.advance()
                    }
                    timeline = endTime
                } finally {
                    extractor.release()
                    combined?.delete()
                }
            }
            muxer?.stop()
        } finally {
            raw.close()
            muxer?.release()
        }
        return mime ?: throw IOException("Empty audio")
    }
}
