package com.savoo.scclient.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.savoo.scclient.data.model.TelegramImportRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface TelegramImportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: TelegramImportRecord)

    @Query("SELECT messageId FROM telegram_import_records WHERE chatId = :chatId")
    suspend fun importedMessageIds(chatId: Long): List<Long>

    @Query("SELECT * FROM telegram_import_records WHERE chatId = :chatId ORDER BY importedAt DESC")
    fun history(chatId: Long): Flow<List<TelegramImportRecord>>

    @Query("DELETE FROM telegram_import_records WHERE chatId = :chatId")
    suspend fun clearHistory(chatId: Long)
}
