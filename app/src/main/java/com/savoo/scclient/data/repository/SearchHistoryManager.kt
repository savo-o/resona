package com.savoo.scclient.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.searchDataStore by preferencesDataStore(name = "search_history")

@Singleton
class SearchHistoryManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val HISTORY = stringPreferencesKey("history")
        val MAX_ITEMS = 20
    }

    val history: Flow<List<String>> = context.searchDataStore.data.map { prefs ->
        prefs[Keys.HISTORY]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    }

    suspend fun add(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        context.searchDataStore.edit { prefs ->
            val current = prefs[Keys.HISTORY]?.split(",")?.filter { it.isNotBlank() }?.toMutableList() ?: mutableListOf()
            current.remove(trimmed)
            current.add(0, trimmed)
            if (current.size > Keys.MAX_ITEMS) {
                prefs[Keys.HISTORY] = current.take(Keys.MAX_ITEMS).joinToString(",")
            } else {
                prefs[Keys.HISTORY] = current.joinToString(",")
            }
        }
    }

    suspend fun remove(query: String) {
        context.searchDataStore.edit { prefs ->
            val current = prefs[Keys.HISTORY]?.split(",")?.filter { it.isNotBlank() }?.toMutableList() ?: mutableListOf()
            current.remove(query.trim())
            prefs[Keys.HISTORY] = current.joinToString(",")
        }
    }

    suspend fun clear() {
        context.searchDataStore.edit { it.remove(Keys.HISTORY) }
    }
}
