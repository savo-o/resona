package com.savoo.scclient.telegram

import android.content.Context
import com.savoo.scclient.R
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.player.OfflineAudioExporter
import com.savoo.scclient.player.OfflineTrackManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelegramMusicStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: TelegramClient,
    private val offline: OfflineTrackManager,
    private val exporter: OfflineAudioExporter,
) {
    private val mutex = Mutex()

    suspend fun save(track: Track) = mutex.withLock {
        client.restoreSession()
        val state = withTimeoutOrNull(15_000) {
            client.authState.first { it !is TelegramAuthState.Connecting }
        } ?: error(context.getString(R.string.telegram_auth_timeout))
        if (state is TelegramAuthState.Error) error(state.message)
        check(state is TelegramAuthState.Ready) { "Sign in to Telegram in Import / Export first" }
        if (!offline.isOfflineTrackSync(track.id)) offline.saveForOffline(track).getOrThrow()
        val file = exporter.prepare(track.id)
        try {
            client.addProfileAudio(file, track.title, track.user.username, (track.durationMs / 1000).toInt())
        } finally {
            file.delete()
        }
    }
}
