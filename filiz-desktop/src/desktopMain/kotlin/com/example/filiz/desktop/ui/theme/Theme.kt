package com.example.filiz.desktop.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

enum class ThemeMode(val id: String) {
    Light("light"),
    Sepia("sepia"),
    Dark("dark");

    fun next(): ThemeMode = when (this) {
        Light -> Sepia
        Sepia -> Dark
        Dark -> Light
    }

    companion object {
        fun fromId(id: String): ThemeMode = entries.firstOrNull { it.id == id } ?: Dark
    }
}

/** Android sürümüyle aynı, göz yormayan palet: yüzeyler nötr, renk yalnızca vurgu için. */
data class BidengColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val outline: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val accent: Color,
    val danger: Color,
    val text: Color,
    val muted: Color,
    val readerBackground: Color,
    val readerText: Color,
    val readingHighlight: Color,
    val noteHighlight: Color,
    val searchHighlight: Color
)

fun bidengColors(mode: ThemeMode): BidengColors = when (mode) {
    ThemeMode.Dark -> BidengColors(
        isDark = true,
        background = Color(0xFF111318), surface = Color(0xFF1B1E25), surfaceAlt = Color(0xFF262A33),
        outline = Color(0xFF30343F), primary = Color(0xFFA89CFF), onPrimary = Color(0xFF1B1446),
        primaryContainer = Color(0xFF322D60), onPrimaryContainer = Color(0xFFE3DEFF),
        accent = Color(0xFFF2B661), danger = Color(0xFFF27A8A), text = Color(0xFFE4E5EA), muted = Color(0xFF9497A3),
        readerBackground = Color(0xFF14161B), readerText = Color(0xFFD9D7D2),
        readingHighlight = Color(0x5FFFF9A8), noteHighlight = Color(0x44FFF9A8), searchHighlight = Color(0x5568B5FF)
    )
    ThemeMode.Sepia -> BidengColors(
        isDark = false,
        background = Color(0xFFF1E7D3), surface = Color(0xFFF8F0DF), surfaceAlt = Color(0xFFE8DCC3),
        outline = Color(0xFFDCCDB0), primary = Color(0xFF8C5A2B), onPrimary = Color.White,
        primaryContainer = Color(0xFFEBD8B8), onPrimaryContainer = Color(0xFF3E2610),
        accent = Color(0xFFB9761E), danger = Color(0xFFB5473A), text = Color(0xFF3D3122), muted = Color(0xFF7C6B53),
        readerBackground = Color(0xFFF6EDD9), readerText = Color(0xFF3B2F20),
        readingHighlight = Color(0x66FFE27A), noteHighlight = Color(0x44D9A441), searchHighlight = Color(0x4D6FA8DC)
    )
    ThemeMode.Light -> BidengColors(
        isDark = false,
        background = Color(0xFFF6F4F0), surface = Color.White, surfaceAlt = Color(0xFFEEEBE6),
        outline = Color(0xFFE3DFD8), primary = Color(0xFF5646D6), onPrimary = Color.White,
        primaryContainer = Color(0xFFE7E2FF), onPrimaryContainer = Color(0xFF23186B),
        accent = Color(0xFFC57B12), danger = Color(0xFFD33A4E), text = Color(0xFF1F1D24), muted = Color(0xFF6E6A75),
        readerBackground = Color(0xFFFCFBF8), readerText = Color(0xFF26242B),
        readingHighlight = Color(0x80FFF59D), noteHighlight = Color(0x55FFE082), searchHighlight = Color(0x5590CAF9)
    )
}

val LocalBidengColors = staticCompositionLocalOf { bidengColors(ThemeMode.Dark) }

object Bideng {
    val colors: BidengColors
        @Composable get() = LocalBidengColors.current
}

@Composable
fun BidengTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val c = bidengColors(mode)
    val scheme = if (c.isDark) {
        darkColorScheme(
            primary = c.primary, onPrimary = c.onPrimary, primaryContainer = c.primaryContainer,
            onPrimaryContainer = c.onPrimaryContainer, secondary = c.primary, background = c.background,
            onBackground = c.text, surface = c.surface, onSurface = c.text, surfaceVariant = c.surfaceAlt,
            onSurfaceVariant = c.muted, outline = c.outline, outlineVariant = c.outline, error = c.danger,
            surfaceContainer = c.surface, surfaceContainerHigh = c.surface, surfaceContainerHighest = c.surfaceAlt,
            surfaceContainerLow = c.surface, inverseSurface = c.text, inverseOnSurface = c.background
        )
    } else {
        lightColorScheme(
            primary = c.primary, onPrimary = c.onPrimary, primaryContainer = c.primaryContainer,
            onPrimaryContainer = c.onPrimaryContainer, secondary = c.primary, background = c.background,
            onBackground = c.text, surface = c.surface, onSurface = c.text, surfaceVariant = c.surfaceAlt,
            onSurfaceVariant = c.muted, outline = c.outline, outlineVariant = c.outline, error = c.danger,
            surfaceContainer = c.surface, surfaceContainerHigh = c.surface, surfaceContainerHighest = c.surfaceAlt,
            surfaceContainerLow = c.surface, inverseSurface = c.text, inverseOnSurface = c.background
        )
    }
    val base = Typography()
    val typography = base.copy(
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Medium, fontSize = 28.sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Medium, fontSize = 21.sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium, fontSize = 16.sp),
        bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp)
    )
    MaterialTheme(colorScheme = scheme, typography = typography) {
        CompositionLocalProvider(LocalBidengColors provides c, content = content)
    }
}
