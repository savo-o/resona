package com.savoo.scclient.data.repository

import android.content.Context
import android.net.Uri
import com.savoo.scclient.data.local.FavoritesDao
import com.savoo.scclient.data.model.FavoriteArtist
import com.savoo.scclient.data.model.FavoritePlaylist
import com.savoo.scclient.data.model.FavoriteTrack
import android.provider.OpenableColumns
import android.util.JsonReader
import android.util.JsonToken
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.FilterInputStream
import java.io.InputStream
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

data class FavoritesExport(
    val version: Int = 1,
    val exportedAt: String,
    val tracks: List<FavoriteTrack>,
    val artists: List<FavoriteArtist>,
    val playlists: List<FavoritePlaylist>,
)

class EmptyFavoritesBackupException : IllegalStateException("Favorites are empty, the existing backup was kept")

@Singleton
class FavoritesExporter @Inject constructor(
    private val favoritesDao: FavoritesDao,
    @ApplicationContext private val context: Context,
) {
    suspend fun exportToJson(): String = withContext(Dispatchers.IO) {
        val tracks = favoritesDao.getAllTracksSync()
        val artists = favoritesDao.getAllArtistsSync()
        val playlists = favoritesDao.getAllPlaylistsSync()

        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAt", java.time.Instant.now().toString())

        root.put("tracks", JSONArray().apply {
            tracks.forEach { t ->
                put(JSONObject().apply {
                    put("trackId", t.trackId)
                    put("title", t.title)
                    put("username", t.username)
                    put("artworkUrl", t.artworkUrl ?: JSONObject.NULL)
                    put("durationMs", t.durationMs)
                    put("permalinkUrl", t.permalinkUrl ?: JSONObject.NULL)
                    put("userId", t.userId)
                    put("userAvatarUrl", t.userAvatarUrl ?: JSONObject.NULL)
                    put("addedAt", t.addedAt)
                })
            }
        })

        root.put("artists", JSONArray().apply {
            artists.forEach { a ->
                put(JSONObject().apply {
                    put("artistId", a.artistId)
                    put("username", a.username)
                    put("fullName", a.fullName ?: JSONObject.NULL)
                    put("avatarUrl", a.avatarUrl ?: JSONObject.NULL)
                    put("followersCount", a.followersCount ?: JSONObject.NULL)
                    put("permalinkUrl", a.permalinkUrl ?: JSONObject.NULL)
                    put("addedAt", a.addedAt)
                })
            }
        })

        root.put("playlists", JSONArray().apply {
            playlists.forEach { p ->
                put(JSONObject().apply {
                    put("playlistId", p.playlistId)
                    put("title", p.title)
                    put("artworkUrl", p.artworkUrl ?: JSONObject.NULL)
                    put("trackCount", p.trackCount)
                    put("username", p.username)
                    put("permalinkUrl", p.permalinkUrl ?: JSONObject.NULL)
                    put("addedAt", p.addedAt)
                })
            }
        })

        root.toString(2)
    }

    suspend fun exportToFile(uri: Uri, protectExistingBackup: Boolean = false): Result<Unit> = runCatching {
        if (protectExistingBackup && isFavoritesEmpty() && destinationHasFavorites(uri)) {
            throw EmptyFavoritesBackupException()
        }
        val json = exportToJson()
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                out.bufferedWriter().use { it.write(json) }
            } ?: throw IllegalStateException("Cannot open file for writing")
        }
    }

    private suspend fun isFavoritesEmpty(): Boolean = withContext(Dispatchers.IO) {
        favoritesDao.getAllTracksSync().isEmpty() &&
            favoritesDao.getAllArtistsSync().isEmpty() &&
            favoritesDao.getAllPlaylistsSync().isEmpty()
    }

    private suspend fun destinationHasFavorites(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val input = context.contentResolver.openInputStream(uri) ?: return@runCatching false
            JsonReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                reader.beginObject()
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    if (name in FAVORITE_ARRAYS && reader.peek() == JsonToken.BEGIN_ARRAY) {
                        reader.beginArray()
                        if (reader.hasNext()) return@runCatching true
                        reader.endArray()
                    } else {
                        reader.skipValue()
                    }
                }
                false
            }
        }.getOrDefault(false)
    }

    suspend fun importFromFile(
        uri: Uri,
        onProgress: (ImportProgress) -> Unit = {},
    ): Result<ImportResult> = withContext(Dispatchers.IO) {
        val counter = ImportCounter()
        try {
            val totalBytes = fileSize(uri)
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Cannot open file for reading")
            val counting = CountingInputStream(input)
            val report = {
                val fraction = if (totalBytes != null && totalBytes > 0) {
                    (counting.bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f)
                } else {
                    null
                }
                onProgress(ImportProgress(fraction, counter.toResult()))
            }
            JsonReader(InputStreamReader(counting, Charsets.UTF_8)).use { reader ->
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "version" -> {
                            val version = reader.nextString().toDoubleOrNull()?.toInt() ?: 1
                            if (version > 1) throw IllegalArgumentException("Unsupported export version: $version")
                        }
                        "tracks" -> readEntries(reader, counter, report, ::toTrack, favoritesDao::addTracks, favoritesDao::addTrack) {
                            counter.tracks += it
                        }
                        "artists" -> readEntries(reader, counter, report, ::toArtist, favoritesDao::addArtists, favoritesDao::addArtist) {
                            counter.artists += it
                        }
                        "playlists" -> readEntries(reader, counter, report, ::toPlaylist, favoritesDao::addPlaylists, favoritesDao::addPlaylist) {
                            counter.playlists += it
                        }
                        else -> reader.skipValue()
                    }
                }
            }
            Result.success(counter.toResult())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(ImportFailedException(e, counter.toResult()))
        } catch (e: OutOfMemoryError) {
            Result.failure(ImportFailedException(IllegalStateException("Not enough memory to read this file"), counter.toResult()))
        }
    }

    private fun fileSize(uri: Uri): Long? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }
    }.getOrNull()

    private class ImportCounter {
        var tracks = 0
        var artists = 0
        var playlists = 0
        var skipped = 0

        fun toResult() = ImportResult(tracks, artists, playlists, skipped)
    }

    private suspend fun <T> readEntries(
        reader: JsonReader,
        counter: ImportCounter,
        report: () -> Unit,
        map: (Map<String, String?>) -> T,
        insertBatch: suspend (List<T>) -> Unit,
        insertOne: suspend (T) -> Unit,
        onImported: (Int) -> Unit,
    ) {
        if (reader.peek() != JsonToken.BEGIN_ARRAY) {
            reader.skipValue()
            return
        }
        val batch = ArrayList<T>(IMPORT_BATCH_SIZE)
        suspend fun flush() {
            if (batch.isEmpty()) return
            try {
                insertBatch(batch)
                onImported(batch.size)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                batch.forEach { entry ->
                    try {
                        insertOne(entry)
                        onImported(1)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        counter.skipped++
                    }
                }
            }
            batch.clear()
            report()
        }
        reader.beginArray()
        while (reader.hasNext()) {
            currentCoroutineContext().ensureActive()
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                reader.skipValue()
                counter.skipped++
                continue
            }
            val entry = try {
                map(reader.readFlatObject())
            } catch (e: IllegalArgumentException) {
                counter.skipped++
                null
            }
            if (entry != null) {
                batch.add(entry)
                if (batch.size >= IMPORT_BATCH_SIZE) flush()
            }
        }
        reader.endArray()
        flush()
    }

    private fun toTrack(t: Map<String, String?>) = FavoriteTrack(
        trackId = t.requireLong("trackId"),
        title = t.requireText("title"),
        username = t.requireText("username"),
        artworkUrl = t.optionalUrl("artworkUrl"),
        durationMs = t.requireLong("durationMs"),
        permalinkUrl = t.optionalUrl("permalinkUrl"),
        userId = t.requireLong("userId"),
        userAvatarUrl = t.optionalUrl("userAvatarUrl"),
        addedAt = t.optionalLong("addedAt") ?: System.currentTimeMillis(),
    )

    private fun toArtist(a: Map<String, String?>) = FavoriteArtist(
        artistId = a.requireLong("artistId"),
        username = a.requireText("username"),
        fullName = a.optionalString("fullName")?.take(MAX_IMPORT_TEXT_CHARS),
        avatarUrl = a.optionalUrl("avatarUrl"),
        followersCount = a.optionalLong("followersCount")?.takeIf { it > 0 },
        permalinkUrl = a.optionalUrl("permalinkUrl"),
        addedAt = a.optionalLong("addedAt") ?: System.currentTimeMillis(),
    )

    private fun toPlaylist(p: Map<String, String?>) = FavoritePlaylist(
        playlistId = p.requireLong("playlistId"),
        title = p.requireText("title"),
        artworkUrl = p.optionalUrl("artworkUrl"),
        trackCount = p.requireLong("trackCount").toInt(),
        username = p.requireText("username"),
        permalinkUrl = p.optionalUrl("permalinkUrl"),
        addedAt = p.optionalLong("addedAt") ?: System.currentTimeMillis(),
    )

    data class ImportProgress(val fraction: Float?, val result: ImportResult)

    class ImportFailedException(cause: Throwable, val partial: ImportResult) :
        IllegalStateException(cause.message, cause)

    data class ImportResult(
        val tracks: Int,
        val artists: Int,
        val playlists: Int,
        val skipped: Int = 0,
    ) {
        val total: Int get() = tracks + artists + playlists
    }
}

private val FAVORITE_ARRAYS = setOf("tracks", "artists", "playlists")
private const val IMPORT_BATCH_SIZE = 500
private const val MAX_IMPORT_TEXT_CHARS = 300
private const val MAX_IMPORT_URL_CHARS = 2048

private class CountingInputStream(private val source: InputStream) : FilterInputStream(source) {
    @Volatile
    var bytesRead = 0L
        private set

    override fun read(): Int = super.read().also { if (it >= 0) bytesRead++ }

    override fun read(b: ByteArray, off: Int, len: Int): Int =
        super.read(b, off, len).also { if (it > 0) bytesRead += it }

    override fun skip(n: Long): Long = super.skip(n).also { bytesRead += it }
}

private fun JsonReader.readFlatObject(): Map<String, String?> {
    val fields = HashMap<String, String?>()
    beginObject()
    while (hasNext()) {
        val name = nextName()
        fields[name] = when (peek()) {
            JsonToken.NULL -> nextNull().let { null }
            JsonToken.STRING, JsonToken.NUMBER -> nextString()
            JsonToken.BOOLEAN -> nextBoolean().toString()
            else -> skipValue().let { null }
        }
    }
    endObject()
    return fields
}

private fun Map<String, String?>.requireString(key: String): String =
    this[key] ?: throw IllegalArgumentException("Missing $key")

private fun Map<String, String?>.optionalString(key: String): String? =
    this[key]?.takeIf { it.isNotEmpty() && it != "null" }

private fun Map<String, String?>.requireText(key: String): String =
    requireString(key).take(MAX_IMPORT_TEXT_CHARS)

private fun Map<String, String?>.optionalUrl(key: String): String? =
    optionalString(key)?.takeIf { it.length <= MAX_IMPORT_URL_CHARS }

private fun Map<String, String?>.optionalLong(key: String): Long? =
    this[key]?.let { it.toLongOrNull() ?: it.toDoubleOrNull()?.toLong() }

private fun Map<String, String?>.requireLong(key: String): Long =
    optionalLong(key) ?: throw IllegalArgumentException("Missing $key")
