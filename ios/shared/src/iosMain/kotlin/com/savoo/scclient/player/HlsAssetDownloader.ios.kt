package com.savoo.scclient.player

import com.savoo.scclient.compat.File
import com.savoo.scclient.compat.IOException
import com.savoo.scclient.compat.Platform
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.AVFoundation.AVAssetDownloadDelegateProtocol
import platform.AVFoundation.AVAssetDownloadTask
import platform.AVFoundation.AVAssetDownloadURLSession
import platform.AVFoundation.AVURLAsset
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeRange
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSError
import platform.Foundation.NSObject
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSUUID
import platform.Foundation.NSValue
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal actual suspend fun downloadHlsAsset(url: String, title: String, onProgress: ((Float) -> Unit)?): File =
    withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val delegate = AssetDownloadDelegate(continuation, onProgress)
            val configuration = NSURLSessionConfiguration.backgroundSessionConfigurationWithIdentifier(
                "${Platform.bundleId}.offline.${NSUUID().UUIDString}",
            )
            val session = AVAssetDownloadURLSession.sessionWithConfiguration(configuration, delegate, NSOperationQueue.mainQueue)
            continuation.invokeOnCancellation {
                dispatch_async(dispatch_get_main_queue()) {
                    session.invalidateAndCancel()
                    delegate.location?.deleteRecursively()
                }
            }
            try {
                val assetUrl = NSURL.URLWithString(url) ?: throw IOException("Invalid HLS URL")
                val asset = AVURLAsset(uRL = assetUrl, options = null)
                val task = session.assetDownloadTaskWithURLAsset(asset, title, null, null)
                    ?: throw IOException("Cannot create HLS download")
                onProgress?.invoke(0f)
                task.resume()
            } catch (e: Exception) {
                session.invalidateAndCancel()
                if (continuation.isActive) continuation.resumeWithException(e)
            }
        }
    }

private class AssetDownloadDelegate(
    private val continuation: CancellableContinuation<File>,
    private val onProgress: ((Float) -> Unit)?,
) : NSObject(), AVAssetDownloadDelegateProtocol {
    var location: File? = null
        private set

    @ObjCSignatureOverride
    override fun URLSession(session: NSURLSession, assetDownloadTask: AVAssetDownloadTask, willDownloadToURL: NSURL) {
        location = willDownloadToURL.path?.let { File(it) }
    }

    @ObjCSignatureOverride
    override fun URLSession(session: NSURLSession, assetDownloadTask: AVAssetDownloadTask, didFinishDownloadingToURL: NSURL) {
        location = didFinishDownloadingToURL.path?.let { File(it) }
    }

    @ObjCSignatureOverride
    override fun URLSession(
        session: NSURLSession,
        assetDownloadTask: AVAssetDownloadTask,
        didLoadTimeRange: CValue<CMTimeRange>,
        totalTimeRangesLoaded: List<*>,
        timeRangeExpectedToLoad: CValue<CMTimeRange>,
    ) {
        val expected = timeRangeExpectedToLoad.useContents { CMTimeGetSeconds(duration) }
        if (!expected.isFinite() || expected <= 0.0) return
        val loaded = totalTimeRangesLoaded.filterIsInstance<NSValue>().sumOf { value ->
            memScoped {
                val range = alloc<CMTimeRange>()
                value.getValue(range.ptr, sizeOf<CMTimeRange>().toULong())
                CMTimeGetSeconds(range.duration).takeIf { it.isFinite() && it > 0.0 } ?: 0.0
            }
        }
        onProgress?.invoke((loaded / expected).toFloat().coerceIn(0f, 1f))
    }

    @ObjCSignatureOverride
    override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) {
        session.finishTasksAndInvalidate()
        val file = location
        if (!continuation.isActive) {
            file?.deleteRecursively()
            return
        }
        if (didCompleteWithError != null || file == null || !file.exists()) {
            file?.deleteRecursively()
            val failure = if ((task.response as? NSHTTPURLResponse)?.statusCode == 429L) {
                RateLimitedException("Download rate limited: 429")
            } else {
                IOException(didCompleteWithError?.localizedDescription ?: "HLS download is incomplete")
            }
            continuation.resumeWithException(failure)
        } else {
            onProgress?.invoke(1f)
            continuation.resume(file)
        }
    }
}
