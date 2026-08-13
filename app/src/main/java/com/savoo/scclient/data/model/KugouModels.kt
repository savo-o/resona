package com.savoo.scclient.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class KugouSongSearchResponse(
    val data: KugouSongSearchData? = null,
)

@JsonClass(generateAdapter = true)
data class KugouSongSearchData(
    val lists: List<KugouSongItem> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class KugouSongItem(
    @Json(name = "FileHash") val fileHash: String? = null,
    @Json(name = "SongName") val songName: String? = null,
    @Json(name = "SingerName") val singerName: String? = null,
    @Json(name = "Duration") val duration: Int? = null,
)

@JsonClass(generateAdapter = true)
data class KugouLyricsSearchResponse(
    val candidates: List<KugouLyricsCandidate> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class KugouLyricsCandidate(
    val id: String? = null,
    val accesskey: String? = null,
    val duration: Long? = null,
)

@JsonClass(generateAdapter = true)
data class KugouLyricsDownloadResponse(
    val content: String? = null,
)
