package com.example.filiz.desktop.service

import com.example.filiz.desktop.model.LibraryItem
import com.example.filiz.desktop.model.Note
import com.example.filiz.desktop.model.NoteColor
import com.example.filiz.desktop.model.Bookmark
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Statement

object DatabaseService {
    private var connection: Connection? = null

    fun initialize(dbPath: String) {
        connection = DriverManager.getConnection("jdbc:sqlite:$dbPath")
        createTables()
    }

    private fun createTables() {
        val stmt = connection?.createStatement()
        stmt?.executeUpdate("""
            CREATE TABLE IF NOT EXISTS library_items (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                path TEXT NOT NULL,
                added_at INTEGER NOT NULL,
                last_index INTEGER DEFAULT 0,
                last_read_at INTEGER DEFAULT 0,
                text_length INTEGER DEFAULT 0
            )
        """.trimIndent())

        stmt?.executeUpdate("""
            CREATE TABLE IF NOT EXISTS notes (
                id TEXT PRIMARY KEY,
                document_id TEXT NOT NULL,
                start_index INTEGER NOT NULL,
                end_index INTEGER NOT NULL,
                text TEXT NOT NULL,
                color TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                FOREIGN KEY (document_id) REFERENCES library_items(id) ON DELETE CASCADE
            )
        """.trimIndent())

        stmt?.executeUpdate("""
            CREATE TABLE IF NOT EXISTS bookmarks (
                id TEXT PRIMARY KEY,
                document_id TEXT NOT NULL,
                item_index INTEGER NOT NULL,
                label TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                FOREIGN KEY (document_id) REFERENCES library_items(id) ON DELETE CASCADE
            )
        """.trimIndent())

        stmt?.close()
    }

    // Library Items
    fun addLibraryItem(item: LibraryItem) {
        val stmt = connection?.prepareStatement("""
            INSERT OR REPLACE INTO library_items
            (id, name, path, added_at, last_index, last_read_at, text_length)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent())

        stmt?.apply {
            setString(1, item.id)
            setString(2, item.name)
            setString(3, item.path)
            setLong(4, item.addedAt)
            setInt(5, item.lastIndex)
            setLong(6, item.lastReadAt)
            setInt(7, item.textLength)
            executeUpdate()
            close()
        }
    }

    fun getAllLibraryItems(): List<LibraryItem> {
        val items = mutableListOf<LibraryItem>()
        val stmt = connection?.createStatement()
        val rs = stmt?.executeQuery("SELECT * FROM library_items ORDER BY last_read_at DESC")

        rs?.use {
            while (it.next()) {
                items.add(LibraryItem(
                    id = it.getString("id"),
                    name = it.getString("name"),
                    path = it.getString("path"),
                    addedAt = it.getLong("added_at"),
                    lastIndex = it.getInt("last_index"),
                    lastReadAt = it.getLong("last_read_at"),
                    textLength = it.getInt("text_length")
                ))
            }
        }
        return items
    }

    fun getLibraryItem(id: String): LibraryItem? {
        val stmt = connection?.prepareStatement("SELECT * FROM library_items WHERE id = ?")
        stmt?.setString(1, id)
        val rs = stmt?.executeQuery()

        rs?.use {
            if (it.next()) {
                return LibraryItem(
                    id = it.getString("id"),
                    name = it.getString("name"),
                    path = it.getString("path"),
                    addedAt = it.getLong("added_at"),
                    lastIndex = it.getInt("last_index"),
                    lastReadAt = it.getLong("last_read_at"),
                    textLength = it.getInt("text_length")
                )
            }
        }
        return null
    }

    fun updateLibraryItem(item: LibraryItem) {
        val stmt = connection?.prepareStatement("""
            UPDATE library_items
            SET name = ?, last_index = ?, last_read_at = ?, text_length = ?
            WHERE id = ?
        """.trimIndent())

        stmt?.apply {
            setString(1, item.name)
            setInt(2, item.lastIndex)
            setLong(3, item.lastReadAt)
            setInt(4, item.textLength)
            setString(5, item.id)
            executeUpdate()
            close()
        }
    }

    fun deleteLibraryItem(id: String) {
        val stmt = connection?.prepareStatement("DELETE FROM library_items WHERE id = ?")
        stmt?.setString(1, id)
        stmt?.executeUpdate()
        stmt?.close()
    }

    // Notes
    fun addNote(note: Note) {
        val stmt = connection?.prepareStatement("""
            INSERT INTO notes
            (id, document_id, start_index, end_index, text, color, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent())

        stmt?.apply {
            setString(1, note.id)
            setString(2, note.documentId)
            setInt(3, note.startIndex)
            setInt(4, note.endIndex)
            setString(5, note.text)
            setString(6, note.color.name)
            setLong(7, note.createdAt)
            executeUpdate()
            close()
        }
    }

    fun getNotesForDocument(documentId: String): List<Note> {
        val notes = mutableListOf<Note>()
        val stmt = connection?.prepareStatement("SELECT * FROM notes WHERE document_id = ? ORDER BY start_index")
        stmt?.setString(1, documentId)
        val rs = stmt?.executeQuery()

        rs?.use {
            while (it.next()) {
                notes.add(Note(
                    id = it.getString("id"),
                    documentId = it.getString("document_id"),
                    startIndex = it.getInt("start_index"),
                    endIndex = it.getInt("end_index"),
                    text = it.getString("text"),
                    color = NoteColor.valueOf(it.getString("color")),
                    createdAt = it.getLong("created_at")
                ))
            }
        }
        return notes
    }

    fun deleteNote(id: String) {
        val stmt = connection?.prepareStatement("DELETE FROM notes WHERE id = ?")
        stmt?.setString(1, id)
        stmt?.executeUpdate()
        stmt?.close()
    }

    fun clearNotesForDocument(documentId: String) {
        val stmt = connection?.prepareStatement("DELETE FROM notes WHERE document_id = ?")
        stmt?.setString(1, documentId)
        stmt?.executeUpdate()
        stmt?.close()
    }

    fun getAllNotes(): List<Note> {
        val notes = mutableListOf<Note>()
        val stmt = connection?.createStatement()
        val rs = stmt?.executeQuery("SELECT * FROM notes ORDER BY created_at DESC")

        rs?.use {
            while (it.next()) {
                notes.add(Note(
                    id = it.getString("id"),
                    documentId = it.getString("document_id"),
                    startIndex = it.getInt("start_index"),
                    endIndex = it.getInt("end_index"),
                    text = it.getString("text"),
                    color = NoteColor.valueOf(it.getString("color")),
                    createdAt = it.getLong("created_at")
                ))
            }
        }
        stmt?.close()
        return notes
    }

    fun addBookmark(bookmark: Bookmark) {
        val stmt = connection?.prepareStatement("""
            INSERT OR REPLACE INTO bookmarks
            (id, document_id, item_index, label, created_at)
            VALUES (?, ?, ?, ?, ?)
        """.trimIndent())

        stmt?.apply {
            setString(1, bookmark.id)
            setString(2, bookmark.documentId)
            setInt(3, bookmark.index)
            setString(4, bookmark.label)
            setLong(5, bookmark.createdAt)
            executeUpdate()
            close()
        }
    }

    fun getBookmarksForDocument(documentId: String): List<Bookmark> {
        val bookmarks = mutableListOf<Bookmark>()
        val stmt = connection?.prepareStatement("SELECT * FROM bookmarks WHERE document_id = ? ORDER BY item_index")
        stmt?.setString(1, documentId)
        val rs = stmt?.executeQuery()

        rs?.use {
            while (it.next()) {
                bookmarks.add(Bookmark(
                    id = it.getString("id"),
                    documentId = it.getString("document_id"),
                    index = it.getInt("item_index"),
                    label = it.getString("label"),
                    createdAt = it.getLong("created_at")
                ))
            }
        }
        stmt?.close()
        return bookmarks
    }

    fun deleteBookmark(id: String) {
        val stmt = connection?.prepareStatement("DELETE FROM bookmarks WHERE id = ?")
        stmt?.setString(1, id)
        stmt?.executeUpdate()
        stmt?.close()
    }
}
