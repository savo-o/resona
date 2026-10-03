package com.savoo.scclient.data.repository

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
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

    val history: Flow<List<String>> = context.searchDataStore.data.map { prefs -> read(prefs) }

    suspend fun add(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        context.searchDataStore.edit { prefs ->
            val current = read(prefs).toMutableList()
            current.remove(trimmed)
            current.add(0, trimmed)
            write(prefs, current.take(Keys.MAX_ITEMS))
        }
    }

    suspend fun remove(query: String) {
        context.searchDataStore.edit { prefs ->
            val current = read(prefs).toMutableList()
            current.remove(query.trim())
            write(prefs, current)
        }
    }

    suspend fun clear() {
        context.searchDataStore.edit { it.remove(Keys.HISTORY) }
    }

    private fun read(prefs: Preferences): List<String> {
        val raw = prefs[Keys.HISTORY] ?: return emptyList()
        val items = if (raw.startsWith("[")) {
            runCatching {
                val array = JSONArray(raw)
                List(array.length()) { array.optString(it) }
            }.getOrElse { raw.split(",") }
        } else {
            raw.split(",")
        }
        return items.map { it.trim() }.filter { it.isNotBlank() }.distinct()
    }

    private fun write(prefs: MutablePreferences, items: List<String>) {
        prefs[Keys.HISTORY] = JSONArray(items).toString()
    }
}
