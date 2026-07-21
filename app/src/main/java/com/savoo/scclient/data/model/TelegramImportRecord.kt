package com.savoo.scclient.data.model

import androidx.room.Entity

/**
 * One row per Telegram audio message we've already processed, keyed by (chatId, messageId) so
 * re-running an import on the same chat skips messages it already handled instead of re-matching
 * or re-downloading them.
 */
@Entity(tableName = "telegram_import_records", primaryKeys = ["chatId", "messageId"])
data class TelegramImportRecord(
    val chatId: Long,
    val messageId: Long,
    val title: String,
    val performer: String?,
    val status: String, // ImportItemStatus.name
    val matchedTrackId: Long?,
    val reason: String?,
    val importedAt: Long = System.currentTimeMillis(),
)
