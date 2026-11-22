package com.savoo.scclient.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.Locale

@androidx.annotation.OptIn(UnstableApi::class)
internal class HlsOfflineDownloader(private val client: OkHttpClient) {
    suspend fun download(url: String, directory: File, onProgress: ((Float) -> Unit)?): File {
        directory.mkdirs()
        try {
            var playlist = loadPlaylist(url)
            if (playlist is HlsMultivariantPlaylist) {
                val variant = playlist.variants.firstOrNull()?.url
                    ?: playlist.audios.firstOrNull()?.url
                    ?: throw IOException("Empty HLS master playlist")
                playlist = loadPlaylist(variant.toString())
            }
            val media = playlist as? HlsMediaPlaylist ?: throw IOException("Expected HLS media playlist")
            if (!media.hasEndTag || media.segments.isEmpty()) throw IOException("Incomplete HLS playlist")
            val manifest = StringBuilder("#EXTM3U\n#EXT-X-VERSION:7\n#EXT-X-PLAYLIST-TYPE:VOD\n")
                .append("#EXT-X-TARGETDURATION:").append((media.targetDurationUs + 999999) / 1000000).append('\n')
                .append("#EXT-X-MEDIA-SEQUENCE:").append(media.mediaSequence).append('\n')
            val initializations = mutableMapOf<HlsMediaPlaylist.Segment, String>()
            var previousInit: HlsMediaPlaylist.Segment? = null
            var discontinuity = 0
            media.segments.forEachIndexed { index, segment ->
                currentCoroutineContext().ensureActive()
                if (segment.fullSegmentEncryptionKeyUri != null || segment.drmInitData != null || segment.hasGapTag) {
                    throw IOException("Unsupported encrypted or missing HLS segment")
                }
                repeat(segment.relativeDiscontinuitySequence - discontinuity) {
                    manifest.append("#EXT-X-DISCONTINUITY\n")
                }
                discontinuity = segment.relativeDiscontinuitySequence
                val init = segment.initializationSegment
                if (init != null && init != previousInit) {
                    val name = initializations[init] ?: "init${initializations.size}.mp4".also {
                        downloadSegment(media.baseUri, init, File(directory, it))
                        initializations[init] = it
                    }
                    manifest.append("#EXT-X-MAP:URI=\"").append(name).append("\"\n")
                }
                previousInit = init
                val extension = segment.url.substringBefore('?').substringAfterLast('.', "bin")
                    .takeIf { it in setOf("mp3", "aac", "ts", "m4s", "mp4") } ?: "bin"
                val name = "segment$index.$extension"
                downloadSegment(media.baseUri, segment, File(directory, name))
                manifest.append(String.format(Locale.US, "#EXTINF:%.6f,\n", segment.durationUs / 1000000.0))
                    .append(name).append('\n')
                onProgress?.invoke((index + 1).toFloat() / media.segments.size)
            }
            manifest.append("#EXT-X-ENDLIST\n")
            return File(directory, "index.m3u8").apply { writeText(manifest.toString()) }
        } catch (e: Exception) {
            directory.deleteRecursively()
            throw e
        }
    }

    private fun loadPlaylist(url: String): HlsPlaylist =
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (response.code == 429) throw RateLimitedException("Download rate limited: 429")
            if (!response.isSuccessful) throw IOException("HLS playlist failed: ${response.code}")
            val body = response.body ?: throw IOException("Empty HLS response")
            body.byteStream().use { HlsPlaylistParser().parse(Uri.parse(response.request.url.toString()), it) }
        }

    private suspend fun downloadSegment(base: String, segment: HlsMediaPlaylist.Segment, target: File) {
        val url = base.toHttpUrl().resolve(segment.url) ?: throw IOException("Invalid HLS segment URL")
        val request = Request.Builder().url(url)
        if (segment.byteRangeLength != C.LENGTH_UNSET.toLong()) {
            request.header("Range", "bytes=${segment.byteRangeOffset}-${segment.byteRangeOffset + segment.byteRangeLength - 1}")
        }
        client.newCall(request.build()).execute().use { response ->
            if (response.code == 429) throw RateLimitedException("Download rate limited: 429")
            if (!response.isSuccessful) throw IOException("HLS segment failed: ${response.code}")
            val body = response.body ?: throw IOException("Empty HLS segment")
            body.byteStream().use { input ->
                if (segment.byteRangeLength != C.LENGTH_UNSET.toLong() && response.code != 206) {
                    var remaining = segment.byteRangeOffset
                    while (remaining > 0) {
                        currentCoroutineContext().ensureActive()
                        val skipped = input.skip(remaining)
                        if (skipped > 0) remaining -= skipped
                        else if (input.read() < 0) throw IOException("Truncated HLS byte range")
                        else remaining--
                    }
                }
                target.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var remaining = segment.byteRangeLength
                    while (remaining != 0L) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer, 0, if (remaining < 0) buffer.size else minOf(buffer.size.toLong(), remaining).toInt())
                        if (count < 0) {
                            if (remaining > 0) throw IOException("Truncated HLS segment")
                            break
                        }
                        output.write(buffer, 0, count)
                        if (remaining > 0) remaining -= count
                    }
                }
            }
            if (target.length() == 0L) throw IOException("Empty HLS segment")
        }
    }
}
