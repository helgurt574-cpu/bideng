package com.example.filiz

import java.util.UUID

internal sealed class AddReaderNoteResult {
    data class Added(val note: ReaderNote) : AddReaderNoteResult()
    data object Duplicate : AddReaderNoteResult()
    data object Invalid : AddReaderNoteResult()
}

internal class ReaderNoteManager {
    fun addNote(
        notes: List<ReaderNote>,
        documentText: String,
        documentId: String,
        documentName: String,
        startOffset: Int,
        endOffset: Int,
        pageNumber: Int,
        createdAt: Long = System.currentTimeMillis()
    ): AddReaderNoteResult {
        if (startOffset < 0 || endOffset < 0 || documentText.isBlank()) return AddReaderNoteResult.Invalid
        val selectionStart = minOf(startOffset, endOffset)
        val selectionEnd = maxOf(startOffset, endOffset)
        val start = selectionStart.coerceIn(0, documentText.length)
        val end = selectionEnd.coerceIn(0, documentText.length)
        if (start >= end || start !in documentText.indices) return AddReaderNoteResult.Invalid

        val excerpt = documentText.substring(start, end)
            .replace(Regex("\\s+"), " ")
            .trim()
        if (excerpt.isBlank()) return AddReaderNoteResult.Invalid

        val exists = notes.any {
            it.documentId == documentId && it.start == start && it.end == end
        }
        if (exists) return AddReaderNoteResult.Duplicate

        return AddReaderNoteResult.Added(
            ReaderNote(
                id = UUID.randomUUID().toString(),
                documentId = documentId,
                documentName = documentName,
                word = excerpt,
                context = excerpt,
                start = start,
                end = end,
                pageNumber = pageNumber,
                createdAt = createdAt
            )
        )
    }

    fun notesForScope(notes: List<ReaderNote>, documentId: String?, readerVisible: Boolean): List<ReaderNote> {
        return if (readerVisible && documentId != null) {
            notes.filter { it.documentId == documentId }.sortedBy { it.start }
        } else {
            notes.sortedByDescending { it.createdAt }
        }
    }
}
