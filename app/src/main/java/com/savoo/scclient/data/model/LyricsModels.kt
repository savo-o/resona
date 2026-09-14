package com.savoo.scclient.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LyricsSearchResult(
    val id: Long? = null,
    @Json(name = "trackName") val trackName: String? = null,
    @Json(name = "artistName") val artistName: String? = null,
    val duration: Double? = null,
    val instrumental: Boolean? = null,
    @Json(name = "plainLyrics") val plainLyrics: String? = null,
    @Json(name = "syncedLyrics") val syncedLyrics: String? = null,
)

data class LyricsLine(
    val timeMs: Long,
    val text: String,
)

sealed interface LyricsResult {
    data class Synced(val lines: List<LyricsLine>, val sourceDurationMs: Long? = null) : LyricsResult
    data class Plain(val text: String, val source: String) : LyricsResult
    data object NotFound : LyricsResult
}

data class LyricsSync(
    val offsetMs: Long = 0L,
    val driftMsPerMin: Long = 0L,
) {
    val isDefault: Boolean get() = offsetMs == 0L && driftMsPerMin == 0L

    fun lyricsTimeAt(positionMs: Long): Long = positionMs + offsetMs + positionMs * driftMsPerMin / 60_000L

    fun withOffset(offsetMs: Long): LyricsSync = copy(offsetMs = offsetMs.coerceIn(-MAX_OFFSET_MS, MAX_OFFSET_MS))

    fun withDrift(driftMsPerMin: Long): LyricsSync =
        copy(driftMsPerMin = driftMsPerMin.coerceIn(-MAX_DRIFT_MS_PER_MIN, MAX_DRIFT_MS_PER_MIN))

    companion object {
        const val MAX_OFFSET_MS = 60_000L
        const val MAX_DRIFT_MS_PER_MIN = 6_000L
        const val AUTO_DRIFT_MIN_DURATION_GAP_MS = 700L

        fun automatic(trackDurationMs: Long?, lyricsDurationMs: Long?): LyricsSync {
            if (trackDurationMs == null || lyricsDurationMs == null || trackDurationMs <= 0L || lyricsDurationMs <= 0L) return LyricsSync()
            if (kotlin.math.abs(lyricsDurationMs - trackDurationMs) < AUTO_DRIFT_MIN_DURATION_GAP_MS) return LyricsSync()
            val drift = Math.round((lyricsDurationMs.toDouble() / trackDurationMs - 1.0) * 60_000.0)
            return LyricsSync().withDrift(drift)
        }
    }
}
