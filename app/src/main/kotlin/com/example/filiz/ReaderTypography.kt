package com.example.filiz

internal const val DEFAULT_READER_FONT_ID = "sans-serif"
internal const val DEFAULT_READER_LINE_SPACING = 1.18f
internal const val MIN_READER_LINE_SPACING = 1.05f
internal const val MAX_READER_LINE_SPACING = 1.45f

internal data class ReaderFontOption(
    val id: String,
    val label: String,
    val description: String,
    val lineSpacingMultiplier: Float = DEFAULT_READER_LINE_SPACING,
    val lineSpacingExtra: Float = 6f
)

internal val READER_FONT_OPTIONS = listOf(
    ReaderFontOption(
        id = "sans-serif",
        label = "Roboto",
        description = "Temiz ve modern"
    ),
    ReaderFontOption(
        id = "serif",
        label = "Serif",
        description = "Uzun okuma icin klasik",
        lineSpacingMultiplier = 1.2f,
        lineSpacingExtra = 7f
    ),
    ReaderFontOption(
        id = "sans-serif-light",
        label = "Light",
        description = "Hafif ve sakin"
    ),
    ReaderFontOption(
        id = "sans-serif-medium",
        label = "Medium",
        description = "Daha belirgin"
    ),
    ReaderFontOption(
        id = "sans-serif-condensed",
        label = "Condensed",
        description = "Dar ekranlarda rahat"
    ),
    ReaderFontOption(
        id = "monospace",
        label = "Monospace",
        description = "Sabit aralikli"
    )
)

internal fun readerFontOption(id: String): ReaderFontOption {
    val normalized = when (id) {
        "sans" -> DEFAULT_READER_FONT_ID
        else -> id
    }
    return READER_FONT_OPTIONS.firstOrNull { it.id == normalized } ?: READER_FONT_OPTIONS.first()
}
