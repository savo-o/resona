package com.savoo.scclient.player

import com.savoo.scclient.compat.File
import com.savoo.scclient.compat.IOException
import com.savoo.scclient.data.model.OfflineTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.AVFoundation.AVAssetExportPresetAppleM4A
import platform.AVFoundation.AVAssetExportSession
import platform.AVFoundation.AVAssetExportSessionStatusCompleted
import platform.AVFoundation.AVFileTypeAppleM4A
import platform.AVFoundation.AVMetadataIdentifieriTunesMetadataArtist
import platform.AVFoundation.AVMetadataIdentifieriTunesMetadataCoverArt
import platform.AVFoundation.AVMetadataIdentifieriTunesMetadataSongName
import platform.AVFoundation.AVMetadataIdentifieriTunesMetadataUserComment
import platform.AVFoundation.AVMetadataIdentifieriTunesMetadataUserGenre
import platform.AVFoundation.AVMetadataItem
import platform.AVFoundation.AVMutableMetadataItem
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.metadata
import platform.AVFoundation.setMetadata
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.setValue
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal actual suspend fun exportOfflineAudio(
    track: OfflineTrack,
    source: File,
    destination: File,
    onProgress: (Float) -> Unit,
) = withContext(Dispatchers.Default) {
    val asset = AVURLAsset(uRL = NSURL.fileURLWithPath(source.path), options = null)
    val exporter = AVAssetExportSession.exportSessionWithAsset(asset, AVAssetExportPresetAppleM4A)
        ?: throw IOException("AVFoundation cannot export this track")
    val m4a = AVFileTypeAppleM4A ?: throw IOException("M4A export is unavailable")
    val tags = listOfNotNull(
        metadata(AVMetadataIdentifieriTunesMetadataSongName, track.title),
        metadata(AVMetadataIdentifieriTunesMetadataArtist, track.username),
        metadata(AVMetadataIdentifieriTunesMetadataUserGenre, track.genre),
        metadata(AVMetadataIdentifieriTunesMetadataUserComment, track.permalinkUrl),
        metadata(AVMetadataIdentifieriTunesMetadataCoverArt, localArtwork(track.artworkUrl)),
    )
    val replacedIds = tags.map { it.identifier }.toSet()
    val originalTags = asset.metadata().filterIsInstance<AVMetadataItem>()
    exporter.setMetadata(originalTags.filterNot { it.identifier in replacedIds } + tags)
    exporter.outputURL = NSURL.fileURLWithPath(destination.path)
    exporter.outputFileType = m4a
    val progress = launch {
        while (true) {
            onProgress(exporter.progress.coerceIn(0f, 1f))
            delay(150)
        }
    }
    try {
        suspendCancellableCoroutine<Unit> { continuation ->
            continuation.invokeOnCancellation { exporter.cancelExport() }
            exporter.exportAsynchronouslyWithCompletionHandler {
                if (!continuation.isActive) return@exportAsynchronouslyWithCompletionHandler
                if (exporter.status == AVAssetExportSessionStatusCompleted) continuation.resume(Unit)
                else continuation.resumeWithException(IOException(exporter.error?.localizedDescription ?: "Audio export failed"))
            }
        }
        if (destination.length() == 0L) throw IOException("Exported audio is empty")
        onProgress(1f)
    } finally {
        progress.cancel()
    }
}

private fun localArtwork(artworkUrl: String?): NSData? {
    if (artworkUrl?.startsWith("file://") != true) return null
    val path = NSURL.URLWithString(artworkUrl)?.path ?: artworkUrl.removePrefix("file://")
    return NSData.dataWithContentsOfFile(path)
}

private fun metadata(identifier: String?, value: Any?): AVMetadataItem? {
    if (identifier == null || value == null || (value is String && value.isBlank())) return null
    return AVMutableMetadataItem().apply {
        setIdentifier(identifier)
        setValue(value, forKey = "value")
    }
}
