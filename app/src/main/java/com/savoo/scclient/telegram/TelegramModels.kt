package com.savoo.scclient.telegram

/** Mirrors TDLib's `AuthorizationState` union in terms this app's UI actually needs. */
sealed class TelegramAuthState {
    data object Unavailable : TelegramAuthState()
    data object LoggedOut : TelegramAuthState()
    data object Connecting : TelegramAuthState()
    data object WaitPhoneNumber : TelegramAuthState()
    data class WaitCode(val phoneNumber: String, val codeHint: String?) : TelegramAuthState()
    data class WaitPassword(val passwordHint: String?) : TelegramAuthState()
    data object Ready : TelegramAuthState()
    data class Error(val message: String) : TelegramAuthState()
}

data class TelegramChatSummary(
    val chatId: Long,
    val title: String,
    val avatarUrl: String? = null,
    val isChannel: Boolean = false,
)

/** A `messageAudio` pulled from chat history, with whatever tags Telegram itself has for it. */
data class TelegramAudioMessage(
    val chatId: Long,
    val messageId: Long,
    val fileId: Int,
    val fileUniqueId: String,
    val fileSizeBytes: Long,
    val title: String?,
    val performer: String?,
    val durationSec: Int,
    val dateUnixSec: Int,
)

enum class ImportItemStatus { MATCHED, DOWNLOADED_OFFLINE, FAILED, SKIPPED_DUPLICATE }

data class ImportItemResult(
    val message: TelegramAudioMessage,
    val resolvedTitle: String,
    val resolvedPerformer: String?,
    val status: ImportItemStatus,
    val matchedTrackId: Long? = null,
    val reason: String? = null,
)

data class ImportProgress(
    val totalScanned: Int,
    val matched: Int,
    val downloadedOffline: Int,
    val failed: Int,
    val skippedDuplicate: Int,
    val currentTitle: String?,
    /** Most recently resolved items, newest first - lets the UI show live results as they stream in. */
    val recentResults: List<ImportItemResult> = emptyList(),
) {
    val processed: Int get() = matched + downloadedOffline + failed + skippedDuplicate
}
