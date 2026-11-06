package com.example.filiz

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import java.util.concurrent.Executors

@Entity(tableName = "library_items")
data class LibraryItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val path: String,
    val addedAt: Long,
    val lastIndex: Int,
    val lastReadAt: Long,
    val textLength: Int
)

@Entity(
    tableName = "reader_notes",
    indices = [
        Index(value = ["documentId"]),
        Index(value = ["documentId", "start", "end"])
    ]
)
data class ReaderNoteEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val documentName: String,
    val word: String,
    val context: String,
    val start: Int,
    val end: Int,
    val pageNumber: Int,
    val createdAt: Long
)

@Dao
interface LibraryItemDao {
    @Query("SELECT * FROM library_items ORDER BY addedAt DESC")
    fun getAll(): List<LibraryItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAll(items: List<LibraryItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(item: LibraryItemEntity)

    @Query("DELETE FROM library_items")
    fun deleteAll()

    @Query("DELETE FROM library_items WHERE id NOT IN (:ids)")
    fun deleteMissing(ids: List<String>)

}

@Dao
interface ReaderNoteDao {
    @Query("SELECT * FROM reader_notes ORDER BY createdAt DESC")
    fun getAll(): List<ReaderNoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAll(notes: List<ReaderNoteEntity>)

    @Query("DELETE FROM reader_notes")
    fun deleteAll()

    @Query("DELETE FROM reader_notes WHERE id NOT IN (:ids)")
    fun deleteMissing(ids: List<String>)

}

@Database(
    entities = [LibraryItemEntity::class, ReaderNoteEntity::class],
    version = 1,
    exportSchema = true
)
abstract class FilizDatabase : RoomDatabase() {
    abstract fun libraryItems(): LibraryItemDao
    abstract fun readerNotes(): ReaderNoteDao

    companion object {
        @Volatile private var instance: FilizDatabase? = null

        fun get(context: Context): FilizDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FilizDatabase::class.java,
                    "filiz.db"
                )
                    .build()
                    .also { instance = it }
            }
        }
    }
}

class FilizDataStore(context: Context) {
    private val database = FilizDatabase.get(context)
    private val executor = Executors.newSingleThreadExecutor()

    fun loadLibrary(): List<LibraryItem> {
        return runOnDatabaseThread {
            database.libraryItems().getAll().map { it.toLibraryItem() }
        }
    }

    fun saveLibrary(items: List<LibraryItem>) {
        val entities = items.map { it.toEntity() }
        executeOnDatabaseThread {
            database.runInTransaction {
                if (entities.isEmpty()) {
                    database.libraryItems().deleteAll()
                } else {
                    database.libraryItems().deleteMissing(entities.map { it.id })
                    database.libraryItems().upsertAll(entities)
                }
            }
        }
    }

    fun saveLibraryItem(item: LibraryItem) {
        val entity = item.toEntity()
        executor.execute {
            database.libraryItems().upsert(entity)
        }
    }

    fun loadNotes(): List<ReaderNote> {
        return runOnDatabaseThread {
            database.readerNotes().getAll().map { it.toReaderNote() }
        }
    }

    fun saveNotes(notes: List<ReaderNote>) {
        val entities = notes.map { it.toEntity() }
        executeOnDatabaseThread {
            database.runInTransaction {
                if (entities.isEmpty()) {
                    database.readerNotes().deleteAll()
                } else {
                    database.readerNotes().deleteMissing(entities.map { it.id })
                    database.readerNotes().upsertAll(entities)
                }
            }
        }
    }

    fun importLegacy(libraryItems: List<LibraryItem>, notes: List<ReaderNote>) {
        val libraryEntities = libraryItems.map { it.toEntity() }
        val noteEntities = notes.map { it.toEntity() }
        runOnDatabaseThread {
            database.runInTransaction {
                if (libraryEntities.isNotEmpty()) {
                    database.libraryItems().upsertAll(libraryEntities)
                }
                if (noteEntities.isNotEmpty()) {
                    database.readerNotes().upsertAll(noteEntities)
                }
            }
        }
    }

    private fun <T> runOnDatabaseThread(block: () -> T): T {
        return executor.submit<T> { block() }.get()
    }

    private fun executeOnDatabaseThread(block: () -> Unit) {
        executor.execute { block() }
    }
}

private fun LibraryItem.toEntity(): LibraryItemEntity {
    return LibraryItemEntity(
        id = id,
        name = name,
        path = path,
        addedAt = addedAt,
        lastIndex = lastIndex,
        lastReadAt = lastReadAt,
        textLength = textLength
    )
}

private fun LibraryItemEntity.toLibraryItem(): LibraryItem {
    return LibraryItem(
        id = id,
        name = name,
        path = path,
        addedAt = addedAt,
        lastIndex = lastIndex,
        lastReadAt = lastReadAt,
        textLength = textLength
    )
}

private fun ReaderNote.toEntity(): ReaderNoteEntity {
    return ReaderNoteEntity(
        id = id,
        documentId = documentId,
        documentName = documentName,
        word = word,
        context = context,
        start = start,
        end = end,
        pageNumber = pageNumber,
        createdAt = createdAt
    )
}

private fun ReaderNoteEntity.toReaderNote(): ReaderNote {
    return ReaderNote(
        id = id,
        documentId = documentId,
        documentName = documentName,
        word = word,
        context = context,
        start = start,
        end = end,
        pageNumber = pageNumber,
        createdAt = createdAt
    )
}
