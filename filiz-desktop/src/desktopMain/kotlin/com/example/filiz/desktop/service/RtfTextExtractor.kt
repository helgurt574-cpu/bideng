package com.example.filiz.desktop.service

import java.nio.charset.Charset

// RTF'den okunabilir düz metin çıkarır: font/renk/stil tablolarını ve gömülü resimleri atlar,
// kod sayfası (\ansicpgNNNN, \'hh) ve Unicode (\uN) karakterlerini çözer, tabloyu satır/sekme olarak korur.
internal object RtfTextExtractor {
    private val SKIPPED_DESTINATIONS = setOf(
        "fonttbl", "colortbl", "stylesheet", "info", "pict", "object", "objdata", "header", "headerl",
        "headerr", "headerf", "footer", "footerl", "footerr", "footerf", "footnote", "listtable",
        "listoverridetable", "rsidtbl", "generator", "xmlnstbl", "themedata", "colorschememapping",
        "latentstyles", "datastore", "fldinst", "shppict", "nonshppict", "revtbl", "pgdsctbl",
        "filetbl", "mmathPr", "wgrffmtfilter", "pnseclvl", "pntext", "pntxta", "pntxtb", "bkmkstart",
        "bkmkend", "annotation", "atnid", "atnauthor", "docvar", "userprops", "background", "shpinst",
        "sp", "sn", "sv", "listtext", "fchars", "lchars", "xe", "tc", "template", "operator"
    )

    private class State(var skip: Boolean, var unicodeSkip: Int)

    fun extract(bytes: ByteArray): String {
        // Bayt bayt okunur; 8 bitlik karakterler \'hh ile, Unicode \uN ile gelir
        val rtf = String(bytes, Charsets.ISO_8859_1)
        if (!rtf.trimStart().startsWith("{\\rtf")) return String(bytes, Charsets.UTF_8)
        var charset: Charset = Charset.forName("windows-1252")
        val out = StringBuilder()
        val stack = ArrayDeque<State>()
        var state = State(skip = false, unicodeSkip = 1)
        var pendingSkip = 0
        val pendingBytes = java.io.ByteArrayOutputStream()

        fun flushBytes() {
            if (pendingBytes.size() > 0) {
                if (!state.skip) out.append(String(pendingBytes.toByteArray(), charset))
                pendingBytes.reset()
            }
        }

        fun emit(text: String) {
            flushBytes()
            if (!state.skip) out.append(text)
        }

        var i = 0
        val n = rtf.length
        while (i < n) {
            val c = rtf[i]
            when {
                c == '{' -> {
                    flushBytes()
                    stack.addLast(State(state.skip, state.unicodeSkip))
                    i++
                }
                c == '}' -> {
                    flushBytes()
                    state = stack.removeLastOrNull() ?: state
                    i++
                }
                c == '\\' && i + 1 < n -> {
                    val next = rtf[i + 1]
                    when {
                        next == '\'' && i + 3 < n -> {
                            val hex = rtf.substring(i + 2, i + 4).toIntOrNull(16)
                            i += 4
                            if (pendingSkip > 0) {
                                pendingSkip--
                            } else if (hex != null && !state.skip) {
                                pendingBytes.write(hex)
                            }
                        }
                        next == '\\' || next == '{' || next == '}' -> {
                            if (pendingSkip > 0) pendingSkip-- else emit(next.toString())
                            i += 2
                        }
                        next == '~' -> { emit(" "); i += 2 }
                        next == '_' -> { emit("-"); i += 2 }
                        next == '-' -> i += 2
                        next == '*' -> {
                            // Tanınmayan, atlanabilir hedef
                            state.skip = true
                            i += 2
                        }
                        next == '\n' || next == '\r' -> { emit("\n"); i += 2 }
                        next.isLetter() -> {
                            var j = i + 1
                            while (j < n && rtf[j].isLetter()) j++
                            val word = rtf.substring(i + 1, j)
                            var k = j
                            if (k < n && (rtf[k] == '-' || rtf[k].isDigit())) {
                                k++
                                while (k < n && rtf[k].isDigit()) k++
                            }
                            val param = rtf.substring(j, k).toIntOrNull()
                            if (k < n && rtf[k] == ' ') k++
                            i = k
                            when (word) {
                                in SKIPPED_DESTINATIONS -> { flushBytes(); state.skip = true }
                                "ansicpg" -> param?.let { cp ->
                                    runCatching { Charset.forName("windows-$cp") }.getOrNull()?.let { charset = it }
                                }
                                "uc" -> state.unicodeSkip = param ?: 1
                                "u" -> {
                                    val code = param ?: 0
                                    emit((if (code < 0) code + 65536 else code).toChar().toString())
                                    pendingSkip = state.unicodeSkip
                                }
                                "par", "line" -> emit("\n")
                                "sect", "page" -> emit("\n\n")
                                "tab", "cell" -> emit("\t")
                                "row" -> emit("\n")
                                "emdash" -> emit("—")
                                "endash" -> emit("–")
                                "bullet" -> emit("•")
                                "lquote" -> emit("‘")
                                "rquote" -> emit("’")
                                "ldblquote" -> emit("“")
                                "rdblquote" -> emit("”")
                            }
                        }
                        else -> i += 2
                    }
                }
                c == '\r' || c == '\n' -> i++
                else -> {
                    if (pendingSkip > 0) {
                        pendingSkip--
                    } else {
                        emit(c.toString())
                    }
                    i++
                }
            }
        }
        flushBytes()
        return out.toString()
            .replace(' ', ' ')
            .replace(Regex("[ ]{2,}"), " ")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }
}
