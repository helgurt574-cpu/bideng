package com.example.filiz

internal data class NoteSelectionRange(
    val anchor: Int,
    val start: Int,
    val end: Int
)

internal class NoteSelectionController {
    var active: Boolean = false
        private set
    var gestureActive: Boolean = false
        private set
    var pendingCommit: Boolean = false
        set(value) {
            field = value
        }
    var anchor: Int = -1
        private set
    var start: Int = -1
        private set
    var end: Int = -1
        private set

    fun canStart(text: String, offset: Int): Boolean {
        if (active || pendingCommit || text.isBlank()) return false
        if (offset < 0 || offset >= text.length) return false
        return text[offset].isWhitespace().not()
    }

    fun begin(text: String, offset: Int): NoteSelectionRange? {
        if (offset < 0 || text.isBlank()) return null
        val safeAnchor = offset.coerceIn(0, text.length)
        active = true
        gestureActive = false
        pendingCommit = false
        anchor = safeAnchor
        start = findWordStart(text, safeAnchor)
        end = findWordEnd(text, safeAnchor)
        return range()
    }

    fun update(text: String, offset: Int): NoteSelectionRange? {
        if (!active || anchor < 0 || offset < 0) return null
        val safeAnchor = anchor.coerceIn(0, text.length)
        val current = offset.coerceIn(0, text.length)
        start = if (current <= safeAnchor) {
            findWordStart(text, current)
        } else {
            findWordStart(text, safeAnchor)
        }
        end = if (current <= safeAnchor) {
            findWordEnd(text, safeAnchor)
        } else {
            findWordEnd(text, current)
        }
        return range()
    }

    fun moveStart(text: String, offset: Int): NoteSelectionRange? {
        if (!active || text.isBlank()) return null
        val wordStart = findWordStart(text, offset).coerceIn(0, text.length)
        val safeStart = if (wordStart < end) {
            wordStart
        } else {
            findWordStart(text, (end - 1).coerceAtLeast(0))
        }.coerceIn(0, text.length)
        if (safeStart == start) return null
        start = safeStart
        anchor = end.coerceIn(0, text.length)
        return range()
    }

    fun moveEnd(text: String, offset: Int): NoteSelectionRange? {
        if (!active || text.isBlank()) return null
        val wordEnd = findWordEnd(text, offset).coerceIn(0, text.length)
        val safeEnd = if (wordEnd > start) {
            wordEnd
        } else {
            findWordEnd(text, start.coerceIn(0, text.length))
        }.coerceIn(0, text.length)
        if (safeEnd == end) return null
        end = safeEnd
        anchor = start.coerceIn(0, text.length)
        return range()
    }

    fun commitRange(text: String): IntRange? {
        if (!active || start < 0 || end < 0) return null
        val safeAnchor = anchor.coerceIn(0, text.length)
        val commitStart = if (start == end) findWordStart(text, safeAnchor) else start
        val commitEnd = if (start == end) findWordEnd(text, safeAnchor) else end
        return commitStart until commitEnd
    }

    fun clear() {
        active = false
        gestureActive = false
        pendingCommit = false
        anchor = -1
        start = -1
        end = -1
    }

    fun range(): NoteSelectionRange {
        return NoteSelectionRange(anchor = anchor, start = start, end = end)
    }
}

internal fun findWordStart(text: String, offset: Int): Int {
    var i = offset.coerceIn(0, text.length)
    while (i < text.length && text[i].isWhitespace()) i++
    while (i > 0 && !text[i - 1].isWhitespace()) i--
    return i
}

internal fun findWordEnd(text: String, offset: Int): Int {
    var i = offset.coerceIn(0, text.length)
    while (i < text.length && !text[i].isWhitespace()) i++
    return i
}
