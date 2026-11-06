package com.example.filiz

import android.graphics.Color

internal val LEMON_HIGHLIGHT = Color.rgb(255, 249, 168)

internal enum class ThemeMode {
    Light,
    Sepia,
    Dark
}

// Sakin, göz yormayan bir palet: yüzeyler nötr, renk sadece vurgu için.
internal data class UiPalette(
    val isDark: Boolean,
    val background: Int,
    val surface: Int,
    val surfaceAlt: Int,
    val outline: Int,
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val secondary: Int,
    val accent: Int,
    val danger: Int,
    val text: Int,
    val mutedText: Int,
    val readerBackground: Int,
    val readerText: Int,
    val highlight: Int = LEMON_HIGHLIGHT
)

internal fun paletteForTheme(mode: ThemeMode): UiPalette {
    return when (mode) {
        ThemeMode.Dark -> UiPalette(
            isDark = true,
            background = Color.rgb(17, 19, 24),
            surface = Color.rgb(27, 30, 37),
            surfaceAlt = Color.rgb(38, 42, 51),
            outline = Color.rgb(48, 52, 63),
            primary = Color.rgb(168, 156, 255),
            onPrimary = Color.rgb(27, 20, 70),
            primaryContainer = Color.rgb(50, 45, 96),
            onPrimaryContainer = Color.rgb(227, 222, 255),
            secondary = Color.rgb(94, 205, 190),
            accent = Color.rgb(242, 182, 97),
            danger = Color.rgb(242, 122, 138),
            text = Color.rgb(228, 229, 234),
            mutedText = Color.rgb(148, 151, 163),
            readerBackground = Color.rgb(20, 22, 27),
            readerText = Color.rgb(217, 215, 210)
        )
        ThemeMode.Sepia -> UiPalette(
            isDark = false,
            background = Color.rgb(241, 231, 211),
            surface = Color.rgb(248, 240, 223),
            surfaceAlt = Color.rgb(232, 220, 195),
            outline = Color.rgb(220, 205, 176),
            primary = Color.rgb(140, 90, 43),
            onPrimary = Color.WHITE,
            primaryContainer = Color.rgb(235, 216, 184),
            onPrimaryContainer = Color.rgb(62, 38, 16),
            secondary = Color.rgb(93, 122, 84),
            accent = Color.rgb(185, 118, 30),
            danger = Color.rgb(181, 71, 58),
            text = Color.rgb(61, 49, 34),
            mutedText = Color.rgb(124, 107, 83),
            readerBackground = Color.rgb(246, 237, 217),
            readerText = Color.rgb(59, 47, 32)
        )
        ThemeMode.Light -> UiPalette(
            isDark = false,
            background = Color.rgb(246, 244, 240),
            surface = Color.WHITE,
            surfaceAlt = Color.rgb(238, 235, 230),
            outline = Color.rgb(227, 223, 216),
            primary = Color.rgb(86, 70, 214),
            onPrimary = Color.WHITE,
            primaryContainer = Color.rgb(231, 226, 255),
            onPrimaryContainer = Color.rgb(35, 24, 107),
            secondary = Color.rgb(13, 128, 116),
            accent = Color.rgb(197, 123, 18),
            danger = Color.rgb(211, 58, 78),
            text = Color.rgb(31, 29, 36),
            mutedText = Color.rgb(110, 106, 117),
            readerBackground = Color.rgb(252, 251, 248),
            readerText = Color.rgb(38, 36, 43)
        )
    }
}

internal fun documentTypeColor(type: String): Int {
    return when (type) {
        "PDF" -> Color.rgb(225, 85, 85)
        "WORD" -> Color.rgb(66, 133, 230)
        "EPUB" -> Color.rgb(32, 165, 120)
        "TXT" -> Color.rgb(214, 145, 30)
        "HTML" -> Color.rgb(20, 160, 190)
        "ODT" -> Color.rgb(130, 100, 220)
        else -> Color.rgb(110, 120, 140)
    }
}

internal fun withAlpha(color: Int, alpha: Int): Int {
    return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
}

// Arayüz metinlerinin başındaki emoji/simgeleri temizler ("📚 Kütüphane" -> "Kütüphane").
internal fun cleanLabel(text: CharSequence?): CharSequence? {
    if (text == null || text.isEmpty() || text !is String) return text
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        if (Character.isLetterOrDigit(cp) || cp == '"'.code || cp == '('.code || cp == '%'.code || cp == '-'.code) break
        i += Character.charCount(cp)
    }
    return if (i in 1 until text.length) text.substring(i) else text
}
