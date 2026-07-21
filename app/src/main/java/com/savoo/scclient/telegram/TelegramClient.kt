package com.savoo.scclient.telegram

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * App-facing seam over whatever Telegram backend is actually wired in. The default binding
 * ([TelegramUnavailableClient]) is a no-op until a real TDLib-backed implementation is built and
 * swapped in via Hilt - see TdLibTelegramClient.kt.reference for the production implementation.
 */
interface TelegramClient {
    val authState: StateFlow<TelegramAuthState>

    /** True once this binding is backed by a real Telegram connection (as opposed to the stub). */
    val isAvailable: Boolean

    suspend fun start()
    suspend fun sendPhoneNumber(phoneNumber: String)
    suspend fun sendCode(code: String)
    suspend fun sendPassword(password: String)
    suspend fun logOut()

    suspend fun getChats(limit: Int = 200): Result<List<TelegramChatSummary>>

    /** Emits (chatId, local file uri) as chat avatars queued by [getChats] finish downloading. */
    fun observeChatAvatarUpdates(): Flow<Pair<Long, String>>

    /** Emits every `messageAudio` found in the chat's history, oldest to newest, until exhausted. */
    fun getAudioMessages(chatId: Long): Flow<TelegramAudioMessage>

    /**
     * Downloads a file by id, emitting progress in [0f, 1f], completing with the local file.
     * Pass [limitBytes] > 0 to stop once that many bytes from the start are ready (e.g. to read
     * ID3 tags without pulling the whole track) - 0 downloads the file in full.
     */
    fun downloadFile(fileId: Int, expectedSizeBytes: Long, limitBytes: Long = 0L): Flow<Pair<Float, File?>>
}
