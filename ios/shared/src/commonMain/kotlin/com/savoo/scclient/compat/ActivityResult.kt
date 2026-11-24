package com.savoo.scclient.compat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

sealed interface ActivityResultContract<I, O>

object ActivityResultContracts {
    class CreateDocument(val mimeType: String) : ActivityResultContract<String, Uri?>
    class OpenDocument : ActivityResultContract<Array<String>, Uri?>
    class OpenDocumentTree : ActivityResultContract<Uri?, Uri?>
    class GetContent : ActivityResultContract<String, Uri?>

    class PickVisualMedia : ActivityResultContract<PickVisualMediaRequest, Uri?> {
        sealed interface VisualMediaType
        object ImageOnly : VisualMediaType
        object ImageAndVideo : VisualMediaType
    }
}

class PickVisualMediaRequest(val mediaType: ActivityResultContracts.PickVisualMedia.VisualMediaType = ActivityResultContracts.PickVisualMedia.ImageOnly)

class ManagedActivityResultLauncher<I> internal constructor(private val onLaunch: (I) -> Unit) {
    fun launch(input: I) = onLaunch(input)
}

expect object FilePickers {
    fun createDocument(fileName: String, mimeType: String, onResult: (Uri?) -> Unit)
    fun openDocument(mimeTypes: List<String>, onResult: (Uri?) -> Unit)
    fun pickImage(onResult: (Uri?) -> Unit)
    fun exportFiles(paths: List<String>, onResult: (Boolean) -> Unit)
    fun exportFile(path: String)
}

@Suppress("UNCHECKED_CAST")
@Composable
fun <I, O> rememberLauncherForActivityResult(
    contract: ActivityResultContract<I, O>,
    onResult: (O) -> Unit,
): ManagedActivityResultLauncher<I> {
    val callback by rememberUpdatedState(onResult)
    return remember(contract) {
        ManagedActivityResultLauncher { input: I ->
            val deliver: (Uri?) -> Unit = { uri -> callback(uri as O) }
            when (contract) {
                is ActivityResultContracts.CreateDocument -> FilePickers.createDocument(input as String, contract.mimeType, deliver)
                is ActivityResultContracts.OpenDocument -> FilePickers.openDocument((input as Array<String>).toList(), deliver)
                is ActivityResultContracts.GetContent -> FilePickers.openDocument(listOf(input as String), deliver)
                is ActivityResultContracts.PickVisualMedia -> FilePickers.pickImage(deliver)
                is ActivityResultContracts.OpenDocumentTree -> deliver(null)
            }
        }
    }
}

class UriPermission(val uri: Uri, val isWritePermission: Boolean, val isReadPermission: Boolean)

class Cursor internal constructor(private val columns: List<String>, private val rows: List<List<Any?>>) : AutoCloseable {
    private var position = -1
    val count: Int get() = rows.size
    fun moveToFirst(): Boolean { position = 0; return rows.isNotEmpty() }
    fun moveToNext(): Boolean { position++; return position < rows.size }
    fun getColumnIndex(name: String): Int = columns.indexOf(name)
    fun getColumnIndexOrThrow(name: String): Int = columns.indexOf(name).also { require(it >= 0) }
    fun isNull(index: Int): Boolean = rows[position][index] == null
    fun getLong(index: Int): Long = (rows[position][index] as? Number)?.toLong() ?: 0L
    fun getInt(index: Int): Int = getLong(index).toInt()
    fun getString(index: Int): String? = rows[position][index]?.toString()
    override fun close() = Unit
}

object OpenableColumns {
    const val DISPLAY_NAME = "_display_name"
    const val SIZE = "_size"
}

object ContentResolver {
    private val pendingExports = HashSet<String>()
    private val granted = LinkedHashSet<String>()

    internal fun markPendingExport(path: String) {
        pendingExports += path
    }

    fun openInputStream(uri: Uri): InputStream? = runCatching { FileInputStream(File(uri.filePath())) }.getOrNull()

    fun openOutputStream(uri: Uri, mode: String = "w"): OutputStream? {
        val path = uri.filePath()
        File(path).parentFile?.mkdirs()
        val stream = runCatching { FileOutputStream(File(path), append = mode.contains('a')) }.getOrNull() ?: return null
        return object : OutputStream(null) {
            override fun write(bytes: ByteArray, off: Int, len: Int) = stream.write(bytes, off, len)
            override fun flush() = stream.flush()
            override fun close() {
                stream.close()
                if (pendingExports.remove(path)) FilePickers.exportFile(path)
            }
        }
    }

    fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        val file = File(uri.filePath())
        if (!file.exists()) return null
        val cols = projection?.toList() ?: listOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val row = cols.map {
            when (it) {
                OpenableColumns.SIZE -> file.length()
                OpenableColumns.DISPLAY_NAME -> file.name
                else -> null
            }
        }
        return Cursor(cols, listOf(row))
    }

    fun takePersistableUriPermission(uri: Uri, flags: Int) {
        granted += uri.toString()
    }

    fun releasePersistableUriPermission(uri: Uri, flags: Int) {
        granted -= uri.toString()
    }

    val persistedUriPermissions: List<UriPermission>
        get() = granted.map { Uri.parse(it) }.filter { File(it.filePath()).parentFile?.exists() == true }
            .map { UriPermission(it, isWritePermission = true, isReadPermission = true) }
}

val Context.contentResolver: ContentResolver get() = ContentResolver

fun Uri.filePath(): String = toString().removePrefix("file://").decodeUrlComponent()
