package com.savoo.scclient.ui.navigation

import android.content.Intent
import android.net.Uri

sealed class DeepLinkTarget {
    data class Artist(val userId: Long) : DeepLinkTarget()
    data class Playlist(val playlistId: Long) : DeepLinkTarget()
    data class ResolveUrl(val url: String) : DeepLinkTarget()
    data object None : DeepLinkTarget()

    companion object {
        private val VALID_HOSTS = setOf("soundcloud.com", "m.soundcloud.com", "www.soundcloud.com")

        fun fromIntent(intent: Intent): DeepLinkTarget {
            val data = intent.data ?: return None
            val host = data.host ?: return None
            val path = data.pathSegments ?: return None

            if (host !in VALID_HOSTS) return None
            if (path.isEmpty()) return None

            val normalizedUrl = data.toString()
                .replace("m.soundcloud.com", "soundcloud.com")
                .replace("www.soundcloud.com", "soundcloud.com")

            return when (path[0]) {
                "users" -> {
                    val userId = path.getOrNull(1)?.toLongOrNull()
                    if (userId != null) Artist(userId) else ResolveUrl(normalizedUrl)
                }
                "playlists" -> {
                    val playlistId = path.getOrNull(1)?.toLongOrNull()
                    if (playlistId != null) Playlist(playlistId) else ResolveUrl(normalizedUrl)
                }
                else -> ResolveUrl(normalizedUrl)
            }
        }
    }
}
