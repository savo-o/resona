package com.savoo.scclient.ui.navigation

import android.content.Intent

sealed class DeepLinkTarget {
    data class Artist(val userId: Long) : DeepLinkTarget()
    data class Playlist(val playlistId: Long) : DeepLinkTarget()
    data class ResolveUrl(val url: String) : DeepLinkTarget()
    data object None : DeepLinkTarget()

    companion object {
        private val VALID_HOSTS = setOf(
            "soundcloud.com", "m.soundcloud.com", "www.soundcloud.com", "on.soundcloud.com"
        )

        fun fromIntent(intent: Intent): DeepLinkTarget {
            if (intent.action == Intent.ACTION_SEND) {
                val shared = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return None
                val url = Regex("""https?://\S+""").find(shared)?.value?.trimEnd('.', ',', ';', '!', '?', ')', '"', '\'') ?: return None
                return fromUrl(url)
            }

            val data = intent.data ?: return None
            return fromUrl(data.toString())
        }

        private fun fromUrl(rawUrl: String): DeepLinkTarget {
            val data = android.net.Uri.parse(rawUrl)
            val host = data.host ?: return None
            val path = data.pathSegments ?: return None

            if (host !in VALID_HOSTS) return None
            if (path.isEmpty()) return None

            val normalizedUrl = data.toString()
                .replace("m.soundcloud.com", "soundcloud.com")
                .replace("www.soundcloud.com", "soundcloud.com")

            return when (host) {
                "on.soundcloud.com" -> ResolveUrl(data.toString())
                else -> when (path[0]) {
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
}
