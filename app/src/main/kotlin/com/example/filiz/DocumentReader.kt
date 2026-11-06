package com.example.filiz

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Log
import android.provider.OpenableColumns
import com.googlecode.tesseract.android.TessBaseAPI
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.PDFTextStripperByArea
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.extractor.WordExtractor
import org.apache.poi.poifs.filesystem.POIFSFileSystem
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipInputStream

private val EPUB_SKIPPABLE_TYPES = setOf(
    "pagebreak",
    "page-list",
    "page-listing",
    "landmarks",
    "loi",
    "lot"
)
private val EPUB_SKIPPABLE_MARKERS = listOf(
    "pageno",
    "page-no",
    "page-num",
    "pagenum",
    "page_number",
    "page-number",
    "pagebreak",
    "page-break",
    "folio",
    "header",
    "footer",
    "running-head",
    "runninghead",
    "running-header",
    "running-footer"
)
private val EPUB_RAW_REMOVE_ELEMENT = Regex(
    """(?is)<([a-z][\w:.-]*)(?=[^>]*(?:class|id|name|role|epub:type|type)\s*=\s*["'][^"']*(?:pageno|page-no|page-num|page_number|pagebreak|page-break|pagenum|mbppagebreak|calibre_pb|folio|header|footer|running)[^"']*["'])[^>]*>.*?</\1\s*>"""
)
private val EPUB_RAW_REMOVE_SELF_CLOSING_ELEMENT = Regex(
    """(?is)<[a-z][\w:.-]*(?=[^>]*(?:class|id|name|role|epub:type|type)\s*=\s*["'][^"']*(?:pageno|page-no|page-num|page_number|pagebreak|page-break|pagenum|mbppagebreak|calibre_pb|folio|header|footer|running)[^"']*["'])[^>]*/\s*>"""
)
private val EPUB_RAW_REMOVE_HEADER_FOOTER = Regex(
    """(?is)<(?:header|footer)\b[^>]*>.*?</(?:header|footer)\s*>"""
)
private val EPUB_RAW_EMPTY_PAGE_ANCHOR = Regex(
    """(?is)<a\b(?=[^>]*(?:id|name)\s*=\s*["'][^"']*(?:page|pg|pageno|pagebreak)[^"']*["'])[^>]*>\s*</a\s*>"""
)
private val EPUB_RAW_PAGEBREAK_ENTITY = Regex(
    """(?i)&(?:#160|nbsp);?\s*(?:\d{1,4}|[-–—]\s*\d{1,4}\s*[-–—])\s*&(?:#160|nbsp);?"""
)
private const val PDF_TOP_REGION = "top"
private const val PDF_BOTTOM_REGION = "bottom"
private const val PDF_EDGE_REGION_RATIO = 0.10f
private const val OCR_PREVIEW_PAGES = 3
internal const val OCR_PREVIEW_MARKER = "[[FILIZ_OCR_PREVIEW]]"
private val TESSDATA_FILES = listOf("tur.traineddata", "eng.traineddata")
private val TESSERACT_LANGUAGES = listOf("tur+eng", "tur", "eng")

class DocumentReader(
    private val activity: Activity,
    private val ocrProgress: ((currentPage: Int, totalPages: Int) -> Unit)? = null
) {
    companion object {
        private val warmedUp = AtomicBoolean(false)

        fun warmUpParsers() {
            if (!warmedUp.compareAndSet(false, true)) return
            runCatching {
                PDFTextStripper()
                PDFTextStripperByArea()
                Class.forName(PDDocument::class.java.name)
                Class.forName(HWPFDocument::class.java.name)
                Class.forName(WordExtractor::class.java.name)
                Class.forName(POIFSFileSystem::class.java.name)
                Class.forName(XmlPullParserFactory::class.java.name)
            }
        }
    }

    fun read(uri: Uri): String {
        val name = displayName(uri).lowercase(Locale.US)
        val mime = activity.contentResolver.getType(uri).orEmpty().lowercase(Locale.US)
        return when {
            name.endsWith(".pdf") || mime == "application/pdf" -> readPdf(uri)
            name.endsWith(".docx") || mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> readDocx(uri)
            name.endsWith(".doc") || mime == "application/msword" -> readDoc(uri)
            name.endsWith(".epub") || mime == "application/epub+zip" -> readEpub(uri)
            name.endsWith(".odt") || mime == "application/vnd.oasis.opendocument.text" -> readOdt(uri)
            name.endsWith(".rtf") || mime == "application/rtf" || mime == "text/rtf" -> readRtf(uri)
            name.endsWith(".html") || name.endsWith(".htm") || mime == "text/html" -> readHtml(uri)
            name.endsWith(".txt") || mime.startsWith("text/") -> readPlainText(uri)
            isProbablyZipDocument(name, mime) -> readZipLike(uri)
            else -> readPlainText(uri)
        }.cleanExtractedText().trim().ifBlank {
            readFallback(uri)
        }
    }

    fun readFullPdfOcr(uri: Uri): String {
        return ocrPdf(uri, pageLimit = null, includePreviewMessage = false)
    }

    private fun readPlainText(uri: Uri): String {
        val encodings = listOf(
            Charsets.UTF_8,
            Charset.forName("windows-1254"),
            Charset.forName("ISO-8859-9")
        )
        for (charset in encodings) {
            activity.contentResolver.openInputStream(uri)?.use { input ->
                val reader = input.reader(charset)
                val builder = StringBuilder()
                val buffer = CharArray(4096)
                while (true) {
                    val read = reader.read(buffer)
                    if (read <= 0) break
                    builder.append(buffer, 0, read)
                }
                if (builder.isNotBlank()) return builder.toString()
            }
        }
        return ""
    }

    private fun readDocx(uri: Uri): String {
        activity.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name == "word/document.xml") {
                        return normalizeTabularLayout(parseOfficeXmlText(zip, wordProcessingML = true))
                    }
                }
            }
        }
        return ""
    }

    private fun readDoc(uri: Uri): String {
        activity.contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readBytes()
            if (bytes.isEmpty()) return ""
            val fs = POIFSFileSystem(bytes.inputStream())
            HWPFDocument(fs).use { document ->
                WordExtractor(document).use { extractor ->
                    // Word hücre sonu işaretlerini (\u0007) sekmeye çevirip tabloyu satır satır düzenle
                    return normalizeTabularLayout(extractor.text.replace('\u0007', '\t')).trim()
                }
            }
        }
        return ""
    }

    private fun readOdt(uri: Uri): String {
        activity.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name == "content.xml") {
                        return normalizeTabularLayout(parseOfficeXmlText(zip, wordProcessingML = false))
                    }
                }
            }
        }
        return ""
    }

    private fun readRtf(uri: Uri): String {
        val bytes = activity.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return ""
        if (bytes.isEmpty()) return ""
        return normalizeTabularLayout(RtfTextExtractor.extract(bytes))
    }

    private fun readHtml(uri: Uri): String {
        val markup = readPlainText(uri)
            .replace(Regex("(?is)<head\\b.*?</head>"), " ")
            .replace(Regex("(?is)<(script|style|noscript)\\b.*?</\\1>"), " ")
            // Tablo: hücreler virgülle, satırlar ayrı satırda
            .replace(Regex("(?i)</t[dh]>\\s*(?=<t[dh][\\s>])"), ", ")
            .replace(Regex("(?i)</tr>"), "<br>")
            .replace(Regex("(?i)<img\\b[^>]*>"), " ")
        @Suppress("DEPRECATION")
        val text = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            android.text.Html.fromHtml(markup, android.text.Html.FROM_HTML_MODE_COMPACT).toString()
        } else {
            android.text.Html.fromHtml(markup).toString()
        }
        return text
            .replace('\uFFFC', ' ')
            .replace('\u00A0', ' ')
            .replace(Regex("[ \t]+\n"), "\n")
            .replace(Regex("[ \t]{2,}"), " ")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun readZipLike(uri: Uri): String {
        activity.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val entryName = entry.name.lowercase(Locale.US)
                    when {
                        entryName == "word/document.xml" -> return normalizeTabularLayout(parseOfficeXmlText(zip, wordProcessingML = true))
                        entryName == "content.xml" -> return normalizeTabularLayout(parseOfficeXmlText(zip, wordProcessingML = false))
                        entryName.endsWith(".xhtml") || entryName.endsWith(".html") || entryName.endsWith(".htm") -> {
                            val text = parseXmlText(zip)
                            if (text.isNotBlank()) return text
                        }
                    }
                }
            }
        }
        return ""
    }

    private fun readEpub(uri: Uri): String {
        val entries = mutableMapOf<String, ByteArray>()
        activity.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory) {
                        entries[entry.name] = zip.readBytes()
                    }
                }
            }
        }
        if (entries.isEmpty()) return ""

        val opfPath = entries["META-INF/container.xml"]?.let(::findOpfPath).orEmpty()
        val orderedContentPaths = if (opfPath.isNotBlank()) {
            val opfBytes = entries[opfPath]
            if (opfBytes != null) epubSpinePaths(opfPath, opfBytes) else emptyList()
        } else {
            emptyList()
        }
        val fallbackPaths = entries.keys
            .filter { it.isHtmlLikePath() }
            .sortedWith(compareBy<String> { it.isLikelyNavigationPath() }.thenBy { it })

        val paths = (orderedContentPaths.ifEmpty { fallbackPaths })
            .distinct()
            .filter { entries.containsKey(it) }

        val rawSections = paths
            .mapNotNull { path -> entries[path]?.let { parseHtmlDocumentText(it) } }
            .filter { it.isNotBlank() }
        val repeatedEdgeLines = repeatedDocumentEdgeLines(rawSections)
        val repeatedShortLines = repeatedExactShortLines(rawSections)
        val output = StringBuilder()
        val sectionTitleCounts = mutableMapOf<String, Int>()
        for (text in rawSections) {
            val cleanedText = cleanEpubSectionText(text, sectionTitleCounts, repeatedEdgeLines, repeatedShortLines)
            if (cleanedText.isNotBlank()) {
                output.append(cleanedText).append("\n\n")
            }
        }
        return output.toString()
    }

    private fun findOpfPath(containerBytes: ByteArray): String {
        val parser = newXmlParser(containerBytes)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name.equals("rootfile", ignoreCase = true)) {
                return parser.getAttributeValue(null, "full-path").orEmpty()
            }
            event = parser.next()
        }
        return ""
    }

    private fun epubSpinePaths(opfPath: String, opfBytes: ByteArray): List<String> {
        val manifest = linkedMapOf<String, String>()
        val spineIds = mutableListOf<String>()
        val parser = newXmlParser(opfBytes)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name.lowercase(Locale.US)) {
                    "item" -> {
                        val id = parser.getAttributeValue(null, "id").orEmpty()
                        val href = parser.getAttributeValue(null, "href").orEmpty()
                        val mediaType = parser.getAttributeValue(null, "media-type").orEmpty()
                        if (id.isNotBlank() && href.isNotBlank() && (href.isHtmlLikePath() || mediaType.contains("xhtml") || mediaType.contains("html"))) {
                            manifest[id] = resolveEpubPath(opfPath, href)
                        }
                    }
                    "itemref" -> {
                        parser.getAttributeValue(null, "idref").orEmpty().takeIf { it.isNotBlank() }?.let(spineIds::add)
                    }
                }
            }
            event = parser.next()
        }
        return spineIds.mapNotNull { manifest[it] }
    }

    private fun parseHtmlDocumentText(bytes: ByteArray): String {
        val parser = newXmlParser(sanitizeEpubMarkup(bytes).toByteArray(Charsets.UTF_8))
        val builder = StringBuilder()
        var skipDepth = 0
        // Tablo hücresi içindeyken paragraflar bölünmez, hücreler virgülle ayrılır
        var cellDepth = 0
        var cellsInRow = 0
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name.lowercase(Locale.US)
                    if (skipDepth > 0) {
                        skipDepth++
                    } else if (tag.isSkippableEpubElement(parser)) {
                        skipDepth++
                    } else if (tag == "td" || tag == "th") {
                        if (cellsInRow > 0 && builder.isNotEmpty() && !builder.last().isWhitespace()) builder.append(',')
                        cellsInRow++
                        cellDepth++
                    } else if (tag == "tr") {
                        cellsInRow = 0
                        appendLineBreak(builder)
                    } else if (cellDepth > 0) {
                        if (tag.isBlockTag() && builder.isNotEmpty() && !builder.last().isWhitespace()) builder.append(' ')
                    } else if (tag.isBlockTag()) {
                        appendParagraphBreak(builder)
                    }
                }
                XmlPullParser.TEXT -> {
                    if (skipDepth == 0) appendTextChunk(builder, parser.text.orEmpty(), keepNumbers = cellDepth > 0)
                }
                XmlPullParser.END_TAG -> {
                    val tag = parser.name.lowercase(Locale.US)
                    if (skipDepth > 0) {
                        skipDepth--
                    } else if (tag == "td" || tag == "th") {
                        cellDepth = (cellDepth - 1).coerceAtLeast(0)
                    } else if (tag == "tr") {
                        appendLineBreak(builder)
                    } else if (cellDepth > 0) {
                        // hücre içindeki blok sonu: boşluk yeter
                    } else if (tag.isBlockTag()) {
                        appendParagraphBreak(builder)
                    }
                }
            }
            event = parser.next()
        }
        return builder.toString()
            .replace(Regex("[ \t]+\n"), "\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun appendLineBreak(builder: StringBuilder) {
        if (builder.isEmpty() || builder.last() == '\n') return
        while (builder.isNotEmpty() && builder.last() == ' ') builder.setLength(builder.length - 1)
        builder.append('\n')
    }

    private fun appendTextChunk(builder: StringBuilder, raw: String, keepNumbers: Boolean = false) {
        val text = raw.replace(Regex("\\s+"), " ").trim()
        if (text.isBlank()) return
        // Tablo hücresindeki "232" gibi sayılar sayfa numarası sanılıp atılmasın
        if (!keepNumbers && isStandalonePageMarker(text)) return
        if (builder.isNotEmpty() && !builder.last().isWhitespace()) builder.append(' ')
        builder.append(text)
    }

    private fun appendParagraphBreak(builder: StringBuilder) {
        if (builder.isEmpty()) return
        if (builder.endsWith("\n\n")) return
        if (builder.last() == '\n') builder.append('\n') else builder.append("\n\n")
    }

    private fun String.isBlockTag(): Boolean {
        return this in setOf("body", "section", "article", "nav", "h1", "h2", "h3", "h4", "h5", "h6", "p", "div", "blockquote", "li", "ul", "ol", "table", "tr", "br")
    }

    private fun String.isSkippableEpubElement(parser: XmlPullParser): Boolean {
        if (this == "script" || this == "style" || this == "header" || this == "footer") return true
        val epubType = parser.attributeValueEndingWith("type").lowercase(Locale.US)
        if (epubType.split(Regex("\\s+")).any { it in EPUB_SKIPPABLE_TYPES }) return true
        val className = parser.getAttributeValue(null, "class").orEmpty().lowercase(Locale.US)
        val id = parser.getAttributeValue(null, "id").orEmpty().lowercase(Locale.US)
        val role = parser.getAttributeValue(null, "role").orEmpty().lowercase(Locale.US)
        val marker = "$className $id $role"
        if (EPUB_SKIPPABLE_MARKERS.any { marker.contains(it) }) return true
        if (marker.contains("mbppagebreak") || marker.contains("calibre_pb")) return true
        if ((this == "span" || this == "a" || this == "small") && marker.matches(Regex(""".*(^|[\s_-])(page|pg|pageno|page-no|page_number|pagebreak)([\s_/-]?\d+)?($|[\s_-]).*"""))) {
            return true
        }
        return false
    }

    private fun sanitizeEpubMarkup(bytes: ByteArray): String {
        return bytes.toString(Charsets.UTF_8)
            .replace(EPUB_RAW_PAGEBREAK_ENTITY, " ")
            .replace(EPUB_RAW_REMOVE_HEADER_FOOTER, " ")
            .replace(EPUB_RAW_EMPTY_PAGE_ANCHOR, " ")
            .replace(EPUB_RAW_REMOVE_SELF_CLOSING_ELEMENT, " ")
            .replace(EPUB_RAW_REMOVE_ELEMENT, " ")
    }

    private fun XmlPullParser.attributeValueEndingWith(localName: String): String {
        for (index in 0 until attributeCount) {
            if (getAttributeName(index).orEmpty().substringAfter(':').equals(localName, ignoreCase = true)) {
                return getAttributeValue(index).orEmpty()
            }
        }
        return ""
    }

    private fun newXmlParser(bytes: ByteArray): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance()
        return factory.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setInput(ByteArrayInputStream(bytes), Charsets.UTF_8.name())
        }
    }

    private fun String.isHtmlLikePath(): Boolean {
        val name = lowercase(Locale.US).substringBefore('#')
        return name.endsWith(".xhtml") || name.endsWith(".html") || name.endsWith(".htm")
    }

    private fun String.isLikelyNavigationPath(): Boolean {
        val name = lowercase(Locale.US).substringAfterLast('/')
        return name.contains("nav") || name.contains("toc") || name.contains("contents")
    }

    private fun resolveEpubPath(opfPath: String, href: String): String {
        val cleanHref = href.substringBefore('#')
        val base = opfPath.substringBeforeLast('/', missingDelimiterValue = "")
        return if (base.isBlank()) cleanHref else "$base/$cleanHref"
    }

    private fun cleanEpubSectionText(
        text: String,
        sectionTitleCounts: MutableMap<String, Int>,
        repeatedEdgeLines: Set<String>,
        repeatedShortLines: Set<String>
    ): String {
        if (text.isBlank()) return text
        val lines = text.lines()
        val contentIndexes = lines.indices.filter { lines[it].trim().isNotBlank() }
        if (contentIndexes.isEmpty()) return ""
        val mutableLines = lines.toMutableList()
        val firstContentIndex = contentIndexes.first()
        val first = mutableLines[firstContentIndex].trim()
        val normalized = normalizeRepeatedLine(first)
        val seen = sectionTitleCounts.getOrDefault(normalized, 0)
        sectionTitleCounts[normalized] = seen + 1
        if (seen >= 2 && first.length in 2..140) {
            mutableLines[firstContentIndex] = ""
        }
        val removableIndexes = (contentIndexes.take(2) + contentIndexes.takeLast(2)).toSet()
        for (index in removableIndexes) {
            val line = mutableLines[index]
            val normalizedLine = normalizeRepeatedLine(line)
            if (normalizedLine in repeatedEdgeLines && isLikelyPdfHeaderFooter(line)) {
                mutableLines[index] = ""
            }
        }
        removeEpubRepeatedAndPageMarkerLines(mutableLines, contentIndexes, repeatedShortLines)
        return mutableLines.joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun removeEpubRepeatedAndPageMarkerLines(
        lines: MutableList<String>,
        contentIndexes: List<Int>,
        repeatedShortLines: Set<String>
    ) {
        for (index in contentIndexes) {
            val line = lines[index].trim()
            val exact = normalizeExactLine(line)
            if (isStandalonePageMarker(line) || (exact in repeatedShortLines && line.length <= 100)) {
                lines[index] = ""
            }
        }
    }

    private fun readPdf(uri: Uri): String {
        val pdfText = runCatching {
            activity.contentResolver.openInputStream(uri)?.use { input ->
                PDDocument.load(input).use { document ->
                    if (document.isEncrypted) {
                        throw IllegalStateException("PDF şifreli olduğu için okunamıyor.")
                    }
                    val output = StringBuilder()
                    val textStripper = LigatureRepairingTextStripper().apply {
                        sortByPosition = true
                    }
                    for (page in 1..document.numberOfPages) {
                        val pageText = textStripper.apply {
                            startPage = page
                            endPage = page
                        }.getText(document).trim()
                        if (pageText.isNotBlank()) {
                            output.append(pageText).append("\n\n")
                        }
                    }
                    output.toString()
                }
            }.orEmpty()
        }.getOrElse { error ->
            if (error is IllegalStateException) throw error
            ""
        }.trim()

        if (pdfText.isNotBlank() && !pdfText.isProbablyUnreadableExtractedText()) return pdfText
        // Metin katmanı yoksa (taranmış PDF) ilk sayfaları OCR ile oku; tamamı arka planda hazırlanır
        val ocrText = runCatching { ocrPdfPreview(uri) }
            .onFailure { Log.w("Filiz", "PDF OCR failed", it) }
            .getOrDefault("")
        if (ocrText.removePrefix(OCR_PREVIEW_MARKER).isNotBlank()) return ocrText
        throw IllegalStateException("PDF'de güvenilir metin bulunamadı. Dosya taranmış, ClearScan kodlamalı veya bozuk PDF yapısında olabilir.")
    }

    private fun String.cleanExtractedText(): String {
        return this
    }

    private fun String.isProbablyUnreadableExtractedText(): Boolean {
        val sample = take(8000)
        val visible = sample.filterNot { it.isWhitespace() }
        if (visible.length < 80) return false
        val suspicious = visible.count { char ->
            char.isISOControl() ||
                !(char in ' '..'~' ||
                    char in '\u00A0'..'\u024F' ||
                    char == '₺' ||
                    char == '€' ||
                    char == '£')
        }
        return suspicious.toFloat() / visible.length > 0.12f
    }

    private fun extractPdfEdgeText(document: PDDocument, pageNumber: Int): String {
        val page = document.getPage(pageNumber - 1)
        val box = page.mediaBox ?: return ""
        val width = box.width.coerceAtLeast(1f)
        val height = box.height.coerceAtLeast(1f)
        val edgeHeight = (height * PDF_EDGE_REGION_RATIO).coerceAtLeast(1f)
        val stripper = PDFTextStripperByArea().apply {
            sortByPosition = true
            addRegion(PDF_TOP_REGION, RectF(0f, 0f, width, edgeHeight))
            addRegion(PDF_BOTTOM_REGION, RectF(0f, height - edgeHeight, width, height))
        }
        return runCatching {
            stripper.extractRegions(page)
            listOf(
                stripper.getTextForRegion(PDF_TOP_REGION),
                stripper.getTextForRegion(PDF_BOTTOM_REGION)
            ).joinToString("\n").trim()
        }.getOrDefault("")
    }

    private fun String.edgeCandidateText(): String {
        val lines = lines().map { it.trim() }.filter { it.isNotBlank() }
        return (lines.take(2) + lines.takeLast(2)).joinToString("\n")
    }

    private fun repeatedPdfHeaderFooterLines(pages: List<String>): Set<String> {
        return repeatedDocumentEdgeLines(pages)
    }

    private fun repeatedExactShortLines(sections: List<String>): Set<String> {
        if (sections.size < 3) return emptySet()
        val threshold = maxOf(3, kotlin.math.ceil(sections.size * 0.30).toInt())
        return sections
            .flatMap { section ->
                section.lines()
                    .map { it.trim() }
                    .filter { it.length in 2..100 }
                    .map(::normalizeExactLine)
                    .distinct()
            }
            .groupingBy { it }
            .eachCount()
            .filterValues { it >= threshold }
            .keys
    }

    private fun repeatedDocumentEdgeLines(pages: List<String>): Set<String> {
        if (pages.size < 3) return emptySet()
        val candidates = mutableListOf<String>()
        pages.forEach { page ->
            val lines = page.lines().map { it.trim() }.filter { it.isNotBlank() }
            candidates += lines.take(2)
            candidates += lines.takeLast(2)
        }
        val threshold = maxOf(3, kotlin.math.ceil(pages.size * 0.30).toInt())
        return candidates
            .map { normalizeRepeatedLine(it) }
            .filter { it.length in 2..140 }
            .groupingBy { it }
            .eachCount()
            .filterValues { it >= threshold }
            .keys
    }

    private fun cleanPdfPageText(pageText: String, repeatedLines: Set<String>): String {
        val originalLines = pageText.lines()
        val contentIndexes = originalLines.indices
            .filter { originalLines[it].trim().isNotBlank() }
        if (contentIndexes.isEmpty()) return pageText
        val removableIndexes = (contentIndexes.take(2) + contentIndexes.takeLast(2)).toSet()
        return originalLines
            .filterIndexed { index, line ->
                val normalized = normalizeRepeatedLine(line)
                val shouldRemove = index in removableIndexes &&
                    (
                        isStandalonePageMarker(line) ||
                            (normalized in repeatedLines && isLikelyPdfHeaderFooter(line))
                    )
                !shouldRemove
            }
            .joinToString("\n")
            .trim()
    }

    private fun normalizeRepeatedLine(line: String): String {
        return line.trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("""\d+"""), "#")
            .replace(Regex("""\s+"""), " ")
    }

    private fun normalizeExactLine(line: String): String {
        return line.trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("""\s+"""), " ")
    }

    private fun isLikelyPdfHeaderFooter(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isBlank()) return false
        if (isStandalonePageMarker(trimmed)) return true
        return trimmed.length <= 140
    }

    private fun isStandalonePageMarker(line: String): Boolean {
        val trimmed = line.trim()
        val normalized = trimmed.lowercase(Locale.ROOT)
        if (trimmed.matches(Regex("""^\d{1,3}$"""))) return true
        if (trimmed.matches(Regex("""^-+\s*\d{1,4}\s*-+$"""))) return true
        if (trimmed.matches(Regex("""^\d{1,4}\s*/\s*\d{1,4}$"""))) return true
        if (normalized.matches(Regex("""^(sayfa|page|rûpel|ripel|الصفحة)\s+\d{1,4}([/\s-]+\d{1,4})?$"""))) return true
        return false
    }

    private fun ocrPdfPreview(uri: Uri): String {
        return ocrPdf(uri, pageLimit = OCR_PREVIEW_PAGES, includePreviewMessage = true)
    }

    private fun ocrPdf(uri: Uri, pageLimit: Int?, includePreviewMessage: Boolean): String {
        val pfd = activity.contentResolver.openFileDescriptor(uri, "r") ?: return ""
        pfd.use {
            PdfRenderer(it).use { renderer ->
                val output = StringBuilder()
                val pagesToRead = pageLimit?.let { minOf(renderer.pageCount, it) } ?: renderer.pageCount
                if (includePreviewMessage && pagesToRead < renderer.pageCount) {
                    output.append(OCR_PREVIEW_MARKER).append('\n')
                }
                val tess = createTesseract()
                    ?: throw IllegalStateException("OCR motoru başlatılamadı.")
                try {
                    for (index in 0 until pagesToRead) {
                        ocrProgress?.invoke(index + 1, pagesToRead)
                        val page = renderer.openPage(index)
                        page.use {
                            val bitmap = renderPageToBitmap(it)
                            try {
                                tess.setImage(bitmap)
                                val text = tess.utF8Text.orEmpty()
                                if (text.isNotBlank()) {
                                    output.append(text).append("\n\n")
                                }
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                } finally {
                    tess.end()
                }
                return output.toString()
            }
        }
    }

    private fun renderPageToBitmap(page: PdfRenderer.Page): Bitmap {
        val width = page.width.coerceAtLeast(1)
        val height = page.height.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bitmap
    }

    private fun createTesseract(): TessBaseAPI? {
        val dataPath = copyTessDataIfNeeded().absolutePath + File.separator
        for (language in TESSERACT_LANGUAGES) {
            val tess = TessBaseAPI()
            val ok = runCatching { tess.init(dataPath, language) }.getOrDefault(false)
            if (ok) return tess
            tess.end()
        }
        return null
    }

    // DOCX (WordprocessingML) ve ODT içeriğini paragraf, satır sonu ve tablo yapısını koruyarak okur.
    private fun parseOfficeXmlText(input: java.io.InputStream, wordProcessingML: Boolean): String {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setInput(input, Charsets.UTF_8.name())
        }
        val builder = StringBuilder()
        var skipDepth = 0
        var inTextRun = false
        var cellDepth = 0
        var cellsInRow = 0
        val paragraphTags = if (wordProcessingML) setOf("p") else setOf("p", "h")
        val rowTag = if (wordProcessingML) "tr" else "table-row"
        val cellTag = if (wordProcessingML) "tc" else "table-cell"
        // Görünmeyen ya da okuma akışını bozan öğeler: alan kodları, silinmiş metin, dipnotlar, yorumlar, resim açıklamaları
        val skipTags = if (wordProcessingML) {
            setOf("instrText", "delText", "drawing", "pict", "object")
        } else {
            setOf("note", "annotation", "title", "desc", "tracked-changes")
        }
        fun appendSeparator(separator: Char) {
            if (builder.isEmpty()) return
            if (separator == '\n') {
                while (builder.isNotEmpty() && (builder.last() == ' ' || builder.last() == '\t')) builder.setLength(builder.length - 1)
                if (builder.isNotEmpty() && builder.last() != '\n') builder.append('\n')
            } else if (!builder.last().isWhitespace()) {
                builder.append(separator)
            }
        }
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name
                    when {
                        skipDepth > 0 -> skipDepth++
                        tag in skipTags -> skipDepth = 1
                        tag == "t" && wordProcessingML -> inTextRun = true
                        tag == "tab" -> builder.append('\t')
                        tag == "br" || tag == "line-break" -> {
                            val pageBreak = parser.getAttributeValue(null, "type") == "page" ||
                                (0 until parser.attributeCount).any { parser.getAttributeName(it) == "type" && parser.getAttributeValue(it) == "page" }
                            if (cellDepth > 0) appendSeparator(' ') else if (pageBreak) appendSeparator('\n') else builder.append('\n')
                        }
                        tag == "s" && !wordProcessingML -> {
                            val count = (0 until parser.attributeCount)
                                .firstOrNull { parser.getAttributeName(it) == "c" }
                                ?.let { parser.getAttributeValue(it).toIntOrNull() } ?: 1
                            repeat(count.coerceIn(1, 8)) { builder.append(' ') }
                        }
                        tag == rowTag -> {
                            cellsInRow = 0
                            appendSeparator('\n')
                        }
                        tag == cellTag -> {
                            if (cellsInRow > 0) builder.append('\t')
                            cellsInRow++
                            cellDepth++
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (skipDepth == 0 && (!wordProcessingML || inTextRun)) {
                        val text = parser.text.orEmpty()
                        builder.append(if (wordProcessingML) text else text.replace(Regex("\\s+"), " "))
                    }
                }
                XmlPullParser.END_TAG -> {
                    val tag = parser.name
                    when {
                        skipDepth > 0 -> skipDepth--
                        tag == "t" && wordProcessingML -> inTextRun = false
                        tag == cellTag -> cellDepth = (cellDepth - 1).coerceAtLeast(0)
                        tag == rowTag -> appendSeparator('\n')
                        tag in paragraphTags -> if (cellDepth > 0) appendSeparator(' ') else appendSeparator('\n')
                    }
                }
            }
            event = parser.next()
        }
        return builder.toString()
            .replace(Regex("[ ]{2,}"), " ")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    // Sekmeyle ayrılmış tablo hücrelerini "a, b, c" biçimine, liste işaretlerinden sonraki sekmeyi boşluğa çevirir.
    private fun normalizeTabularLayout(text: String): String {
        return text.lineSequence().joinToString("\n") { rawLine ->
            // Word'ün Symbol/Wingdings madde imleri özel kullanım alanında gelir (ör. U+F0B7); "•" yap
            val line = rawLine.trimStart(' ', '\t').trimEnd().replace(Regex("^[\uF000-\uF0FF]"), "•")
            val marker = Regex("""^(\d{1,3}[.)]|[•·▪◦●○■\-–])\t+""").find(line)
            val body = if (marker != null) {
                marker.value.trimEnd('\t') + " " + line.substring(marker.range.last + 1)
            } else {
                line
            }
            body.split(Regex("\\s*\\t+\\s*"))
                .filter { it.isNotBlank() }
                .joinToString(", ")
        }.replace(Regex("\n{3,}"), "\n\n").trim()
    }

    private fun parseXmlText(input: java.io.InputStream): String {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setInput(input, Charsets.UTF_8.name())
        }
        val builder = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.TEXT) {
                val text = parser.text?.trim().orEmpty()
                if (text.isNotEmpty()) {
                    if (builder.isNotEmpty() && !builder.last().isWhitespace()) builder.append(' ')
                    builder.append(text)
                }
            }
            event = parser.next()
        }
        return builder.toString().replace(Regex("\\s+"), " ").trim()
    }

    private fun readFallback(uri: Uri): String {
        val mime = activity.contentResolver.getType(uri).orEmpty().lowercase(Locale.US)
        val name = displayName(uri).lowercase(Locale.US)
        return when {
            mime.contains("msword") || name.endsWith(".doc") -> "Bu dosya eski Word `.doc` biçiminde ama metin çıkarılamadı."
            else -> ""
        }
    }

    private fun copyTessDataIfNeeded(): File {
        val target = File(activity.filesDir, "tesseract")
        val marker = File(target, ".ready-v3")
        val tessdataDir = File(target, "tessdata")
        tessdataDir.mkdirs()

        if (marker.exists() && TESSDATA_FILES.all { File(tessdataDir, it).length() > 1024L }) {
            return target
        }

        marker.delete()
        TESSDATA_FILES.forEach { fileName ->
            val outFile = File(tessdataDir, fileName)
            outFile.delete()
            activity.assets.open("tessdata/$fileName").use { input ->
                FileOutputStream(outFile).use { output -> input.copyTo(output) }
            }
        }
        marker.writeText("ok")
        return target
    }

    private fun isProbablyZipDocument(name: String, mime: String): Boolean {
        return mime.contains("zip") ||
            name.endsWith(".docx") ||
            name.endsWith(".odt") ||
            name.endsWith(".epub")
    }

    private fun displayName(uri: Uri): String {
        runCatching {
            activity.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) return cursor.getString(index)
            }
        }
        return uri.lastPathSegment.orEmpty().substringAfterLast('/')
    }
}
