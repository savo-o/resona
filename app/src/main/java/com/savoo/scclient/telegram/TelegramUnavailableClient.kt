package com.savoo.scclient.telegram

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default [TelegramClient] binding: the app ships without TDLib's native library bundled, so the
 * Telegram import screen has something to inject and show a "not set up yet" state instead of
 * crashing. Swap the Hilt binding in di/TelegramModule.kt for TdLibTelegramClient once the real
 * library is in app/libs/ - see the setup notes there.
 */
@Singleton
class TelegramUnavailableClient @Inject constructor() : TelegramClient {
    override val authState: StateFlow<TelegramAuthState> = MutableStateFlow(TelegramAuthState.Unavailable)
    override val isAvailable: Boolean = false

    override suspend fun start() {}
    override suspend fun sendPhoneNumber(phoneNumber: String) {}
    override suspend fun sendCode(code: String) {}
    override suspend fun sendPassword(password: String) {}
    override suspend fun logOut() {}

    override suspend fun getChats(limit: Int): Result<List<TelegramChatSummary>> =
        Result.failure(IllegalStateException("Telegram import is not set up in this build"))

    override fun observeChatAvatarUpdates(): Flow<Pair<Long, String>> = emptyFlow()

    override fun getAudioMessages(chatId: Long): Flow<TelegramAudioMessage> = emptyFlow()

    override fun downloadFile(fileId: Int, expectedSizeBytes: Long, limitBytes: Long): Flow<Pair<Float, File?>> = emptyFlow()
}
