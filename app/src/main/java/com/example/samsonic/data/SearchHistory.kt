package com.example.samsonic.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/** The words the user has searched for, newest first, remembered across launches. */
class SearchHistory(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("samsonic_search", Context.MODE_PRIVATE)
    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<String>> = _entries.asStateFlow()

    /** Puts [query] first, dropping an earlier copy of it (ignoring case) and anything past [MAX]. */
    fun add(query: String) {
        val word = query.trim()
        if (word.isEmpty()) return
        save(listOf(word) + _entries.value.filterNot { it.equals(word, ignoreCase = true) })
    }

    fun remove(query: String) = save(_entries.value - query)

    private fun save(list: List<String>) {
        val kept = list.take(MAX)
        _entries.value = kept
        prefs.edit { putString(KEY, JSONArray(kept).toString()) }
    }

    private fun load(): List<String> = runCatching {
        val array = JSONArray(prefs.getString(KEY, null) ?: return emptyList())
        List(array.length()) { array.getString(it) }
    }.getOrDefault(emptyList())

    private companion object {
        const val KEY = "history"
        const val MAX = 10
    }
}
