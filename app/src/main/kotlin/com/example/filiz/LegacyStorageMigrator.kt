package com.example.filiz

import android.content.Context
import org.json.JSONArray
import java.io.File

internal class LegacyStorageMigrator(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun migrateIfNeeded(dataStore: FilizDataStore) {
        if (prefs.getBoolean(ROOM_STORAGE_MIGRATED_KEY, false)) return

        val legacyLibrary = loadLegacyLibrary()
        val legacyNotes = loadLegacyNotes()
        if (legacyLibrary.isNotEmpty() || legacyNotes.isNotEmpty()) {
            dataStore.importLegacy(legacyLibrary, legacyNotes)
        }
        prefs.edit()
            .putBoolean(ROOM_STORAGE_MIGRATED_KEY, true)
            .remove(LIBRARY_ITEMS_KEY)
            .remove(NOTES_KEY)
            .apply()
    }

    private fun loadLegacyLibrary(): List<LibraryItem> {
        val raw = prefs.getString(LIBRARY_ITEMS_KEY, "[]").orEmpty()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val json = array.getJSONObject(i)
                    val item = LibraryItem(
                        id = json.getString("id"),
                        name = json.getString("name"),
                        path = json.getString("path"),
                        addedAt = json.optLong("addedAt", 0L),
                        lastIndex = json.optInt("lastIndex", 0),
                        lastReadAt = json.optLong("lastReadAt", 0L),
                        textLength = json.optInt("textLength", 0)
                    )
                    if (File(item.path).exists()) add(item)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun loadLegacyNotes(): List<ReaderNote> {
        val raw = prefs.getString(NOTES_KEY, "[]").orEmpty()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val json = array.getJSONObject(i)
                    add(
                        ReaderNote(
                            id = json.getString("id"),
                            documentId = json.getString("documentId"),
                            documentName = json.optString("documentName", ""),
                            word = json.optString("word", ""),
                            context = json.optString("context", ""),
                            start = json.optInt("start", 0),
                            end = json.optInt("end", 0),
                            pageNumber = json.optInt("pageNumber", 1),
                            createdAt = json.optLong("createdAt", 0L)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val PREFS = "library"
        const val LIBRARY_ITEMS_KEY = "items"
        const val NOTES_KEY = "notes"
        const val ROOM_STORAGE_MIGRATED_KEY = "roomStorageMigrated"
    }
}
