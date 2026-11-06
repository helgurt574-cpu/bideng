package com.example.filiz.desktop.model

data class LibraryItem(
    val id: String,
    val name: String,
    val path: String,
    val addedAt: Long,
    val lastIndex: Int,
    val lastReadAt: Long,
    val textLength: Int
)

data class Note(
    val id: String,
    val documentId: String,
    val startIndex: Int,
    val endIndex: Int,
    val text: String,
    val color: NoteColor = NoteColor.Yellow,
    val createdAt: Long = System.currentTimeMillis()
)

data class Bookmark(
    val id: String,
    val documentId: String,
    val index: Int,
    val label: String,
    val createdAt: Long = System.currentTimeMillis()
)

enum class NoteColor {
    Yellow,
    Pink,
    Green,
    Blue;

    val hex: String
        get() = when (this) {
            Yellow -> "#FFF59D"
            Pink -> "#F48FB1"
            Green -> "#81C784"
            Blue -> "#64B5F6"
        }
}

data class AppSettings(
    val darkMode: Boolean = true,
    val fontFamily: String = "sans-serif",
    val fontSize: Int = 18,
    val lineSpacing: Float = 1.5f,
    val ttsVoice: String = "",
    val ttsSpeed: Float = 1.0f,
    val uiLanguage: String = "tr",
    val windowWidth: Int = 1280,
    val windowHeight: Int = 820
)
