package com.nan.webwrapper

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class HistoryRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getHistory(): List<HistoryEntry> {
        val jsonString = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
        val jsonArray = JSONArray(jsonString)
        val items = mutableListOf<HistoryEntry>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.optJSONObject(i) ?: continue
            val url = obj.optString("url")
            val time = obj.optLong("time", 0L)
            val customName = obj.optString("customName").takeIf { it.isNotBlank() }
            val logoPath = obj.optString("logoPath").takeIf { it.isNotBlank() }
            if (url.isNotBlank()) {
                items.add(HistoryEntry(url, time, customName, logoPath))
            }
        }
        return items.sortedByDescending { it.timestamp }
    }

    fun addEntry(url: String) {
        val cleaned = url.trim()
        if (cleaned.isBlank()) return
        val current = getHistory().filterNot { it.url.equals(cleaned, ignoreCase = true) }
        val updated = current + HistoryEntry(cleaned, System.currentTimeMillis())
        save(updated)
    }

    fun deleteEntry(entry: HistoryEntry) {
        val updated = getHistory().filterNot { it.url.equals(entry.url, ignoreCase = true) }
        save(updated)
    }

    fun updateEntry(oldEntry: HistoryEntry, newName: String?, newLogoPath: String?) {
        val current = getHistory().toMutableList()
        val index = current.indexOfFirst { it.url.equals(oldEntry.url, ignoreCase = true) }
        if (index >= 0) {
            current[index] = HistoryEntry(
                url = oldEntry.url,
                timestamp = oldEntry.timestamp,
                customName = newName?.takeIf { it.isNotBlank() },
                logoPath = newLogoPath?.takeIf { it.isNotBlank() }
            )
            save(current)
        }
    }

    fun clear() {
        save(emptyList())
    }

    private fun save(items: List<HistoryEntry>) {
        val jsonArray = JSONArray()
        items.forEach { item ->
            val obj = JSONObject()
            obj.put("url", item.url)
            obj.put("time", item.timestamp)
            item.customName?.let { obj.put("customName", it) }
            item.logoPath?.let { obj.put("logoPath", it) }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_HISTORY, jsonArray.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "history_prefs"
        private const val KEY_HISTORY = "history_list"
    }
}

