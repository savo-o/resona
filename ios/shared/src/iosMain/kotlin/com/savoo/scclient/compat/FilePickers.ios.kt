package com.savoo.scclient.compat

import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSItemProvider
import platform.Foundation.NSURL
import platform.Foundation.writeToFile
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeData
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

actual object FilePickers {
    private val retained = HashSet<Any>()

    actual fun createDocument(fileName: String, mimeType: String, onResult: (Uri?) -> Unit) {
        val dir = File(AppContext.filesDir, "Exports").apply { mkdirs() }
        val file = File(dir, fileName.ifBlank { "export" })
        ContentResolver.markPendingExport(file.path)
        onResult(Uri.parse("file://" + file.path))
    }

    actual fun openDocument(mimeTypes: List<String>, onResult: (Uri?) -> Unit) {
        val types = mimeTypes.mapNotNull { mime ->
            when {
                mime == "*/*" -> UTTypeData
                else -> UTType.typeWithMIMEType(mime)
            }
        }.ifEmpty { listOf(UTTypeData) }
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = types, asCopy = true)
        val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
            override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
                retained.remove(this)
                val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
                onResult(url?.path?.let { copyIntoSandbox(it) })
            }

            override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                retained.remove(this)
                onResult(null)
            }
        }
        retained += delegate
        picker.delegate = delegate
        present(picker)
    }

    actual fun pickImage(onResult: (Uri?) -> Unit) {
        val config = PHPickerConfiguration()
        config.filter = PHPickerFilter.imagesFilter
        config.selectionLimit = 1
        val picker = PHPickerViewController(configuration = config)
        val delegate = object : NSObject(), PHPickerViewControllerDelegateProtocol {
            override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
                retained.remove(this)
                picker.dismissViewControllerAnimated(true, null)
                val provider: NSItemProvider = (didFinishPicking.firstOrNull() as? PHPickerResult)?.itemProvider ?: run {
                    onResult(null)
                    return
                }
                provider.loadDataRepresentationForTypeIdentifier(UTTypeImage.identifier) { data: NSData?, _ ->
                    val path = data?.let {
                        val target = File(AppContext.cacheDir, "picked_${currentTimeMillis()}.img").path
                        if (it.writeToFile(target, true)) target else null
                    }
                    dispatch_async(dispatch_get_main_queue()) { onResult(path?.let { p -> Uri.parse("file://$p") }) }
                }
            }
        }
        retained += delegate
        picker.delegate = delegate
        present(picker)
    }

    actual fun exportFiles(paths: List<String>, onResult: (Boolean) -> Unit) {
        dispatch_async(dispatch_get_main_queue()) {
            val picker = UIDocumentPickerViewController(
                forExportingURLs = paths.map { NSURL.fileURLWithPath(it) },
                asCopy = true,
            )
            val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
                override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
                    retained.remove(this)
                    onResult(didPickDocumentsAtURLs.isNotEmpty())
                }

                override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                    retained.remove(this)
                    onResult(false)
                }
            }
            retained += delegate
            picker.delegate = delegate
            if (!present(picker)) {
                retained.remove(delegate)
                onResult(false)
            }
        }
    }

    actual fun exportFile(path: String) {
        dispatch_async(dispatch_get_main_queue()) {
            val picker = UIDocumentPickerViewController(forExportingURLs = listOf(NSURL.fileURLWithPath(path)), asCopy = true)
            present(picker)
        }
    }

    private fun copyIntoSandbox(path: String): Uri? {
        val target = File(AppContext.cacheDir, "imports/${path.substringAfterLast('/')}")
        target.parentFile?.mkdirs()
        target.delete()
        val ok = NSFileManager.defaultManager.copyItemAtPath(path, target.path, null)
        return if (ok) Uri.parse("file://" + target.path) else null
    }

    private fun present(controller: UIViewController): Boolean {
        var top = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return false
        while (top.presentedViewController != null) top = top.presentedViewController!!
        top.presentViewController(controller, animated = true, completion = null)
        return true
    }
}
