package com.example.filiz

internal object ImportPathCleaner {
    private val importPathPatterns = listOf(
        Regex("""(?i)(?:file:\s*)?(?:[/\\][^\s/\\]+)*[/\\]input[/\\]import-\d+[/\\][^\s\r\n]*?\.(?:pdf|docx?|epub|odt|rtf|txt|html?)"""),
        Regex("""(?i)\binput[/\\]import-\d+[/\\][^\s\r\n]*?\.(?:pdf|docx?|epub|odt|rtf|txt|html?)""")
    )

    fun removeFromDisplay(text: String): String {
        if (text.isBlank()) return text
        return importPathPatterns.fold(text) { current, pattern ->
            current.replace(pattern, " ")
        }.replace(Regex("[ \t]{2,}"), " ")
    }

    fun maskPreservingOffsets(text: String): String {
        if (text.isBlank()) return text
        val chars = text.toCharArray()
        importPathPatterns.forEach { pattern ->
            pattern.findAll(text).forEach { match ->
                for (index in match.range) chars[index] = ' '
            }
        }
        return String(chars)
    }
}
