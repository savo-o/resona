package com.savoo.scclient.player

import android.app.Application
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@androidx.annotation.OptIn(UnstableApi::class)
class HlsOfflineDownloaderTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun preservesSegmentsAndDiscontinuitiesPastOneMinute() = runBlocking {
        val playlist = "#EXTM3U\n#EXT-X-VERSION:7\n#EXT-X-TARGETDURATION:10\n#EXT-X-MAP:URI=\"init.mp4\"\n" +
            (0..8).joinToString("") {
                (if (it == 6) "#EXT-X-DISCONTINUITY\n" else "") + "#EXTINF:10.0,\n$it.m4s\n"
            } + "#EXT-X-ENDLIST\n"
        val client = client { path -> if (path == "/playlist.m3u8") playlist else path }
        val directory = temporaryFolder.newFolder("track")
        val file = HlsOfflineDownloader(client).download("https://cdn.example/playlist.m3u8", directory, null)
        val parsed = file.inputStream().use { HlsPlaylistParser().parse(Uri.fromFile(file), it) } as HlsMediaPlaylist
        assertEquals(9, parsed.segments.size)
        assertEquals(90000000L, parsed.durationUs)
        assertEquals(1, parsed.segments[6].relativeDiscontinuitySequence)
        assertEquals("/6.m4s", directory.resolve(parsed.segments[6].url).readText())
        assertEquals("/init.mp4", directory.resolve(parsed.segments[0].initializationSegment!!.url).readText())
        assertFalse(file.readText().contains("https://"))
    }

    @Test fun resolvesRelativeMasterPlaylistAndSlicesByteRangesWhenServerIgnoresRange() = runBlocking {
        val client = client { path ->
            when (path) {
                "/master.m3u8" -> "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=160000\naudio/stream.m3u8\n"
                "/audio/stream.m3u8" -> "#EXTM3U\n#EXT-X-TARGETDURATION:10\n#EXTINF:10,\n#EXT-X-BYTERANGE:3@2\n../data.mp3\n#EXTINF:10,\n#EXT-X-BYTERANGE:3\n../data.mp3\n#EXT-X-ENDLIST\n"
                "/data.mp3" -> "0123456789"
                else -> throw IOException("Unexpected URL: $path")
            }
        }
        val directory = temporaryFolder.newFolder("ranges")
        val file = HlsOfflineDownloader(client).download("https://cdn.example/master.m3u8", directory, null)
        assertEquals("234", directory.resolve("segment0.mp3").readText())
        assertEquals("567", directory.resolve("segment1.mp3").readText())
        assertFalse(file.readText().contains("BYTERANGE"))
    }

    @Test fun removesIncompleteDownloadOnFailure() = runBlocking {
        val client = client { path ->
            if (path.endsWith("m3u8")) "#EXTM3U\n#EXT-X-TARGETDURATION:10\n#EXTINF:10,\na.mp3\n#EXTINF:10,\nb.mp3\n#EXT-X-ENDLIST\n"
            else if (path == "/a.mp3") "audio" else throw IOException("Network failure")
        }
        val directory = temporaryFolder.newFolder("failed")
        assertTrue(runCatching { HlsOfflineDownloader(client).download("https://cdn.example/playlist.m3u8", directory, null) }.isFailure)
        assertFalse(directory.exists())
    }

    private fun client(body: (String) -> String): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body(chain.request().url.encodedPath).toResponseBody()).build()
        }.build()
}
