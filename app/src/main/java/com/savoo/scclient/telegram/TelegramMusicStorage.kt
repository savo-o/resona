package com.savoo.scclient.telegram

import com.savoo.scclient.data.model.Track
import com.savoo.scclient.player.OfflineAudioExporter
import com.savoo.scclient.player.OfflineTrackManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelegramMusicStorage @Inject constructor(
    private val client: TelegramClient,
    private val offline: OfflineTrackManager,
    private val exporter: OfflineAudioExporter,
) {
    private val mutex = Mutex()

    suspend fun save(track: Track) = mutex.withLock {
        check(client.authState.value is TelegramAuthState.Ready) { "Sign in to Telegram in Import / Export first" }
        if (!offline.isOfflineTrackSync(track.id)) offline.saveForOffline(track).getOrThrow()
        val file = exporter.prepare(track.id)
        try {
            client.addProfileAudio(file, track.title, track.user.username, (track.durationMs / 1000).toInt())
        } finally {
            file.delete()
        }
    }
}
