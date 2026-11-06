package com.example.filiz.desktop.service

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.extractor.WordExtractor
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.jsoup.Jsoup
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.util.zip.ZipFile

object DocumentReaderService {

    fun readDocument(file: File): String {
        return readDocumentContent(file).text
    }

    fun readDocumentContent(file: File): DocumentContent {
        return when (file.extension.lowercase()) {
            "pdf" -> readPDF(file)
            "txt" -> readPlainText(readTXT(file))
            "doc" -> readPlainText(readDOC(file))
            "docx" -> readPlainText(readDOCX(file))
            "epub" -> readEPUB(file)
            "html", "htm" -> readPlainText(readHTML(file))
            else -> throw UnsupportedOperationException("Desteklenmeyen dosya formatı: ${file.extension}")
        }
    }

    private fun readPDF(file: File): DocumentContent {
        return try {
            Loader.loadPDF(file).use { document ->
                val pageTexts = (1..document.numberOfPages).map { pageNumber ->
                    PDFTextStripper().apply {
                        startPage = pageNumber
                        endPage = pageNumber
                    }.getText(document).trim()
                }
                buildContent(pageTexts.mapIndexed { index, text ->
                    DocumentPage(
                        number = index + 1,
                        label = "Sayfa ${index + 1}",
                        title = "Sayfa ${index + 1}",
                        text = text
                    )
                }, pageUnit = PageUnit.RealPage)
            }
        } catch (e: Exception) {
            throw IllegalStateException("PDF okuma hatası: ${e.message}", e)
        }
    }

    private fun readTXT(file: File): String {
        return try {
            // Önce UTF-8 dene, başarısız olursa diğer kodlamaları dene
            val encodings = listOf("UTF-8", "windows-1254", "ISO-8859-9", "windows-1252")

            for (encoding in encodings) {
                try {
                    val text = String(Files.readAllBytes(file.toPath()), Charset.forName(encoding))
                    // Geçerli karakter kontrolü
                    if (!text.contains("�")) {
                        return text
                    }
                } catch (e: Exception) {
                    // Sonraki kodlamayı dene
                }
            }

            // Son çare UTF-8
            String(Files.readAllBytes(file.toPath()), Charset.forName("UTF-8"))
        } catch (e: Exception) {
            throw IllegalStateException("TXT okuma hatası: ${e.message}", e)
        }
    }

    private fun readDOCX(file: File): String {
        return try {
            XWPFDocument(file.inputStream()).use { document ->
                document.paragraphs.joinToString("\n") { it.text }
            }
        } catch (e: Exception) {
            throw IllegalStateException("DOCX okuma hatası: ${e.message}", e)
        }
    }

    private fun readDOC(file: File): String {
        return try {
            HWPFDocument(file.inputStream()).use { document ->
                WordExtractor(document).use { extractor ->
                    extractor.paragraphText.joinToString("\n") { it.trimEnd() }.trim()
                }
            }
        } catch (e: Exception) {
            throw IllegalStateException("DOC okuma hatası: ${e.message}", e)
        }
    }

    private fun readHTML(file: File): String {
        return try {
            Jsoup.parse(file, "UTF-8").text()
        } catch (e: Exception) {
            throw IllegalStateException("HTML okuma hatası: ${e.message}", e)
        }
    }

    private fun readEPUB(file: File): DocumentContent {
        return try {
            ZipFile(file).use { zip ->
                val chapters = zip.entries().asSequence()
                    .filter { !it.isDirectory && (it.name.endsWith(".xhtml", true) || it.name.endsWith(".html", true) || it.name.endsWith(".htm", true)) }
                    .sortedBy { it.name }
                    .mapIndexed { index, entry ->
                        val text = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { reader ->
                            Jsoup.parse(reader.readText()).text().trim()
                        }
                        DocumentPage(
                            number = index + 1,
                            label = "Bölüm ${index + 1}",
                            title = entry.name.substringAfterLast('/').substringBeforeLast('.').ifBlank { "Bölüm ${index + 1}" },
                            text = text
                        )
                    }
                    .filter { it.text.isNotBlank() }
                    .toList()
                if (chapters.isEmpty()) throw IllegalStateException("EPUB içinde okunabilir metin bulunamadı")
                buildContent(chapters, pageUnit = PageUnit.Chapter)
            }
        } catch (e: Exception) {
            throw IllegalStateException("EPUB okuma hatası: ${e.message}", e)
        }
    }

    private fun readPlainText(text: String): DocumentContent {
        return buildContent(
            text.chunked(3000).ifEmpty { listOf("") }.mapIndexed { index, chunk ->
                DocumentPage(
                    number = index + 1,
                    label = "Konum ${index + 1}",
                    title = "Konum ${index + 1}",
                    text = chunk
                )
            },
            pageUnit = PageUnit.Location
        )
    }

    private fun buildContent(pages: List<DocumentPage>, pageUnit: PageUnit): DocumentContent {
        var cursor = 0
        val indexedPages = pages.ifEmpty {
            listOf(DocumentPage(number = 1, label = "Sayfa 1", title = "Sayfa 1", text = ""))
        }.mapIndexed { index, page ->
            val separator = if (index == 0) "" else "\n\n"
            cursor += separator.length
            val start = cursor
            cursor += page.text.length
            page.copy(startIndex = start, endIndex = cursor)
        }
        return DocumentContent(
            text = indexedPages.joinToString("\n\n") { it.text }.trim(),
            pages = indexedPages,
            pageUnit = pageUnit
        )
    }

    fun getDocumentInfo(file: File): DocumentInfo {
        return try {
            val text = readDocument(file)
            val wordCount = text.split(Regex("\\s+")).size
            val charCount = text.length
            val pageCount = (text.length / 2500).coerceAtLeast(1)

            DocumentInfo(
                wordCount = wordCount,
                charCount = charCount,
                pageCount = pageCount
            )
        } catch (e: Exception) {
            DocumentInfo(0, 0, 0)
        }
    }
}

enum class PageUnit {
    RealPage,
    Chapter,
    Location
}

data class DocumentContent(
    val text: String,
    val pages: List<DocumentPage>,
    val pageUnit: PageUnit
)

data class DocumentPage(
    val number: Int,
    val label: String,
    val title: String,
    val text: String,
    val startIndex: Int = 0,
    val endIndex: Int = 0
)

data class DocumentInfo(
    val wordCount: Int,
    val charCount: Int,
    val pageCount: Int
)
