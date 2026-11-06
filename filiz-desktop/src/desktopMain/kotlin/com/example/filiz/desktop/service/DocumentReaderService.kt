package com.example.filiz.desktop.service

import org.apache.pdfbox.Loader
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.extractor.WordExtractor
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import java.util.zip.ZipFile
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants

/**
 * Belgeleri okunabilir, sesli okumaya uygun metne çevirir.
 * Başlık, paragraf ve liste yapısı korunur; tablolar satır satır, hücreler virgülle ayrılır.
 */
object DocumentReaderService {

    val supportedExtensions = setOf("pdf", "epub", "docx", "doc", "odt", "rtf", "html", "htm", "txt")

    fun readDocument(file: File): String = readDocumentContent(file).text

    fun readDocumentContent(file: File): DocumentContent {
        val extension = file.extension.lowercase()
        return runCatching {
            when (extension) {
                "pdf" -> readPdf(file)
                "epub" -> readEpub(file)
                "docx" -> locationContent(normalizeTabularLayout(zipEntry(file, "word/document.xml") { parseOfficeXml(it, wordProcessingML = true) }))
                "odt" -> locationContent(normalizeTabularLayout(zipEntry(file, "content.xml") { parseOfficeXml(it, wordProcessingML = false) }))
                "doc" -> locationContent(readDoc(file))
                "rtf" -> locationContent(normalizeTabularLayout(RtfTextExtractor.extract(file.readBytes())))
                "html", "htm" -> locationContent(htmlText(Jsoup.parse(file, null)))
                "txt" -> locationContent(readTxt(file))
                else -> throw UnsupportedOperationException("Desteklenmeyen dosya biçimi: .$extension")
            }
        }.getOrElse { error ->
            if (error is UnsupportedOperationException || error is UnreadableDocumentException) throw error
            throw IllegalStateException("${file.name} okunamadı: ${error.message ?: error::class.simpleName}", error)
        }.also { content ->
            if (content.text.isBlank()) throw UnreadableDocumentException("Belgede okunabilir metin bulunamadı.")
        }
    }

    // ---------- PDF ----------

    private fun readPdf(file: File): DocumentContent {
        Loader.loadPDF(file).use { document ->
            if (document.isEncrypted) throw UnreadableDocumentException("Bu PDF şifreli olduğu için açılamıyor.")
            val stripper = LigatureRepairingTextStripper().apply { sortByPosition = true }
            val pages = (1..document.numberOfPages).map { number ->
                stripper.startPage = number
                stripper.endPage = number
                val text = stripper.getText(document)
                    .replace("\r", "")
                    .replace(Regex("[ \\t]+\\n"), "\n")
                    .replace(Regex("\\n{3,}"), "\n\n")
                    .trim()
                    .let(::reflowPdfLines)
                DocumentPage(number = number, label = "Sayfa $number", title = "Sayfa $number", text = text)
            }
            if (pages.all { it.text.isBlank() }) {
                throw UnreadableDocumentException(
                    "Bu PDF taranmış (yalnızca resimden oluşan) bir belge gibi görünüyor. " +
                        "Masaüstü sürümü taranmış PDF'leri henüz okuyamıyor; Android sürümü OCR ile açabilir."
                )
            }
            return buildContent(pages.filter { it.text.isNotBlank() }, PageUnit.RealPage)
        }
    }

    /**
     * PDF'teki satır sonları sayfa düzeninden gelir; cümle ortasındaki kırılmaları birleştirir.
     * Yalnızca önceki satır noktalama ile bitmiyor ve sonraki satır küçük harfle başlıyorsa birleşir;
     * başlıklar, tablo satırları ve listeler (büyük harf / işaret ile başlar) olduğu gibi kalır.
     */
    internal fun reflowPdfLines(text: String): String {
        val out = StringBuilder(text.length)
        val lines = text.split('\n')
        for ((index, line) in lines.withIndex()) {
            if (index == 0) { out.append(line); continue }
            val previous = out.trimEnd()
            val next = line.trimStart()
            val first = next.firstOrNull()
            val last = previous.lastOrNull()
            val joinable = first != null && last != null && first.isLowerCase() &&
                last !in ".!?:;…؟" && previous.isNotEmpty()
            when {
                joinable && last == '-' && previous.length > 1 && previous[previous.length - 2].isLetter() -> {
                    out.setLength(previous.length - 1) // satır sonu tirelemesi: "oku-\nmak" -> "okumak"
                    out.append(next)
                }
                joinable -> { out.setLength(previous.length); out.append(' ').append(next) }
                else -> out.append('\n').append(line)
            }
        }
        return out.toString()
    }

    // ---------- EPUB ----------

    private fun readEpub(file: File): DocumentContent {
        ZipFile(file).use { zip ->
            val container = zip.getEntry("META-INF/container.xml")?.let { zip.getInputStream(it).readBytes().decodeToString() }
            val opfPath = container?.let {
                Jsoup.parse(it, "", Parser.xmlParser()).selectFirst("rootfile")?.attr("full-path")
            }.orEmpty()
            val spine = if (opfPath.isNotBlank() && zip.getEntry(opfPath) != null) {
                val opf = Jsoup.parse(zip.getInputStream(zip.getEntry(opfPath)).readBytes().decodeToString(), "", Parser.xmlParser())
                val base = opfPath.substringBeforeLast('/', "")
                val manifest = opf.select("manifest > item").associate { it.attr("id") to it.attr("href") }
                opf.select("spine > itemref").mapNotNull { manifest[it.attr("idref")] }
                    .map { href -> if (base.isBlank()) href.substringBefore('#') else "$base/${href.substringBefore('#')}" }
                    .map { java.net.URLDecoder.decode(it, Charsets.UTF_8) }
            } else {
                emptyList()
            }
            val paths = spine.ifEmpty {
                zip.entries().asSequence()
                    .map { it.name }
                    .filter { it.endsWith(".xhtml", true) || it.endsWith(".html", true) || it.endsWith(".htm", true) }
                    .sorted()
                    .toList()
            }
            val chapters = paths.mapNotNull { path ->
                val entry = zip.getEntry(path) ?: return@mapNotNull null
                val text = htmlText(Jsoup.parse(zip.getInputStream(entry).readBytes().decodeToString()))
                text.takeIf { it.isNotBlank() }
            }
            if (chapters.isEmpty()) throw UnreadableDocumentException("EPUB içinde okunabilir metin bulunamadı.")
            return buildContent(
                chapters.mapIndexed { index, text ->
                    val title = text.lineSequence().firstOrNull { it.isNotBlank() }?.take(60) ?: "Bölüm ${index + 1}"
                    DocumentPage(number = index + 1, label = "Bölüm ${index + 1}", title = title, text = text)
                },
                PageUnit.Chapter
            )
        }
    }

    // ---------- HTML (EPUB bölümleri dahil) ----------

    private val blockTags = setOf(
        "p", "div", "section", "article", "header", "footer", "aside", "nav", "main", "blockquote", "pre",
        "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li", "dl", "dt", "dd", "figure", "figcaption",
        "table", "thead", "tbody", "tfoot", "address", "hr", "body"
    )
    private val skippedTags = setOf("script", "style", "noscript", "head", "title", "svg", "math", "button", "select", "template")

    private fun htmlText(document: org.jsoup.nodes.Document): String {
        val builder = StringBuilder()
        appendHtml(document.body() ?: document, builder, inCell = false)
        return builder.toString()
            .replace(' ', ' ')
            .replace(Regex("[ \\t]+\\n"), "\n")
            .replace(Regex("\\n[ \\t]+"), "\n")
            .replace(Regex("[ \\t]{2,}"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun appendHtml(node: Node, builder: StringBuilder, inCell: Boolean) {
        when (node) {
            is TextNode -> {
                val text = node.text().replace(Regex("\\s+"), " ")
                if (text.isBlank()) {
                    if (builder.isNotEmpty() && !builder.last().isWhitespace()) builder.append(' ')
                } else {
                    builder.append(text)
                }
            }
            is Element -> {
                val tag = node.normalName()
                if (tag in skippedTags) return
                // EPUB sayfa numarası işaretleri okunmasın
                val type = node.attr("epub:type") + " " + node.attr("role") + " " + node.className()
                if (type.contains("pagebreak") || type.contains("doc-pagebreak")) return
                when {
                    tag == "br" -> builder.append(if (inCell) " " else "\n")
                    tag == "img" -> Unit
                    tag == "tr" -> {
                        lineBreak(builder)
                        val cells = node.children().filter { it.normalName() == "td" || it.normalName() == "th" }
                        cells.forEachIndexed { index, cell ->
                            if (index > 0) builder.append(", ")
                            val cellText = StringBuilder()
                            cell.childNodes().forEach { appendHtml(it, cellText, inCell = true) }
                            builder.append(cellText.toString().replace(Regex("\\s+"), " ").trim())
                        }
                        lineBreak(builder)
                    }
                    tag in blockTags && !inCell -> {
                        paragraphBreak(builder, tag)
                        if (tag == "li") builder.append("• ")
                        node.childNodes().forEach { appendHtml(it, builder, inCell) }
                        paragraphBreak(builder, tag)
                    }
                    else -> {
                        if (tag in blockTags && inCell && builder.isNotEmpty() && !builder.last().isWhitespace()) builder.append(' ')
                        node.childNodes().forEach { appendHtml(it, builder, inCell) }
                    }
                }
            }
        }
    }

    private fun lineBreak(builder: StringBuilder) {
        while (builder.isNotEmpty() && builder.last() == ' ') builder.setLength(builder.length - 1)
        if (builder.isNotEmpty() && builder.last() != '\n') builder.append('\n')
    }

    private fun paragraphBreak(builder: StringBuilder, tag: String) {
        while (builder.isNotEmpty() && builder.last() == ' ') builder.setLength(builder.length - 1)
        if (builder.isEmpty()) return
        // Liste öğeleri tek satır sonuyla, diğer bloklar boş satırla ayrılır
        val wanted = if (tag == "li" || tag == "dt" || tag == "dd") "\n" else "\n\n"
        if (!builder.endsWith(wanted)) {
            if (builder.last() == '\n' && wanted == "\n\n") builder.append('\n') else if (builder.last() != '\n') builder.append(wanted)
        }
    }

    // ---------- DOCX / ODT ----------

    private fun <T> zipEntry(file: File, name: String, read: (InputStream) -> T): T {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry(name) ?: throw UnreadableDocumentException("Belge yapısı tanınmadı ($name yok).")
            return zip.getInputStream(entry).use(read)
        }
    }

    private fun parseOfficeXml(input: InputStream, wordProcessingML: Boolean): String {
        val factory = XMLInputFactory.newInstance().apply {
            setProperty(XMLInputFactory.SUPPORT_DTD, false)
            setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
            setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true)
        }
        val reader = factory.createXMLStreamReader(input, "UTF-8")
        val builder = StringBuilder()
        var skipDepth = 0
        var inTextRun = false
        var cellDepth = 0
        var cellsInRow = 0
        // Liste paragrafları: Word madde imini metne değil numaralandırma ayarına (numPr) yazar
        var paragraphStart = 0
        var listParagraph = false
        var pendingListItem = false
        // pPr içindeki <w:tab> sekme durağı tanımıdır, metindeki sekme değil
        var inParagraphProps = false
        val paragraphTags = if (wordProcessingML) setOf("p") else setOf("p", "h")
        val rowTag = if (wordProcessingML) "tr" else "table-row"
        val cellTag = if (wordProcessingML) "tc" else "table-cell"
        val skipTags = if (wordProcessingML) {
            setOf("instrText", "delText", "drawing", "pict", "object")
        } else {
            setOf("note", "annotation", "title", "desc", "tracked-changes")
        }
        fun separator(char: Char) {
            if (builder.isEmpty()) return
            if (char == '\n') {
                while (builder.isNotEmpty() && (builder.last() == ' ' || builder.last() == '\t')) builder.setLength(builder.length - 1)
                if (builder.isNotEmpty() && builder.last() != '\n') builder.append('\n')
            } else if (!builder.last().isWhitespace()) {
                builder.append(char)
            }
        }
        while (reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> {
                    val tag = reader.localName
                    when {
                        skipDepth > 0 -> skipDepth++
                        tag in skipTags -> skipDepth = 1
                        tag in paragraphTags -> {
                            paragraphStart = builder.length
                            listParagraph = pendingListItem
                            pendingListItem = false
                        }
                        tag == "pPr" && wordProcessingML -> inParagraphProps = true
                        tag == "numId" && wordProcessingML && inParagraphProps -> {
                            val id = (0 until reader.attributeCount)
                                .firstOrNull { reader.getAttributeLocalName(it) == "val" }
                                ?.let { reader.getAttributeValue(it) }
                            listParagraph = id != null && id != "0"
                        }
                        tag == "list-item" && !wordProcessingML -> pendingListItem = true
                        tag == "t" && wordProcessingML -> inTextRun = true
                        tag == "tab" -> if (!inParagraphProps) builder.append('\t')
                        tag == "br" || tag == "line-break" -> {
                            val pageBreak = (0 until reader.attributeCount).any {
                                reader.getAttributeLocalName(it) == "type" && reader.getAttributeValue(it) == "page"
                            }
                            if (cellDepth > 0) separator(' ') else if (pageBreak) separator('\n') else builder.append('\n')
                        }
                        tag == "s" && !wordProcessingML -> {
                            val count = (0 until reader.attributeCount)
                                .firstOrNull { reader.getAttributeLocalName(it) == "c" }
                                ?.let { reader.getAttributeValue(it).toIntOrNull() } ?: 1
                            repeat(count.coerceIn(1, 8)) { builder.append(' ') }
                        }
                        tag == rowTag -> {
                            cellsInRow = 0
                            separator('\n')
                        }
                        tag == cellTag -> {
                            if (cellsInRow > 0) builder.append('\t')
                            cellsInRow++
                            cellDepth++
                        }
                    }
                }
                XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> {
                    if (skipDepth == 0 && (!wordProcessingML || inTextRun)) {
                        val text = reader.text
                        builder.append(if (wordProcessingML) text else text.replace(Regex("\\s+"), " "))
                    }
                }
                XMLStreamConstants.END_ELEMENT -> {
                    val tag = reader.localName
                    when {
                        skipDepth > 0 -> skipDepth--
                        tag == "t" && wordProcessingML -> inTextRun = false
                        tag == "pPr" && wordProcessingML -> inParagraphProps = false
                        tag == cellTag -> cellDepth = (cellDepth - 1).coerceAtLeast(0)
                        tag == rowTag -> separator('\n')
                        tag in paragraphTags -> {
                            if (listParagraph && cellDepth == 0 && builder.length > paragraphStart) {
                                builder.insert(paragraphStart, "• ")
                            }
                            listParagraph = false
                            if (cellDepth > 0) separator(' ') else separator('\n')
                        }
                    }
                }
            }
        }
        reader.close()
        return builder.toString().replace(Regex("[ ]{2,}"), " ").replace(Regex("\\n{3,}"), "\n\n").trim()
    }

    // ---------- DOC / TXT ----------

    private fun readDoc(file: File): String {
        file.inputStream().use { input ->
            HWPFDocument(input).use { document ->
                WordExtractor(document).use { extractor ->
                    // Word hücre sonu işaretlerini sekmeye çevirip tabloyu satır satır düzenle
                    return normalizeTabularLayout(extractor.text.replace('\u0007', '\t')).trim()
                }
            }
        }
    }

    private fun readTxt(file: File): String {
        val bytes = file.readBytes()
        for (name in listOf("UTF-8", "windows-1254", "ISO-8859-9")) {
            val charset = runCatching { Charset.forName(name) }.getOrNull() ?: continue
            val decoder = charset.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            val text = runCatching { decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString() }.getOrNull()
            if (text != null) return text.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n').trim()
        }
        return String(bytes, Charsets.UTF_8).trim()
    }

    /** Sekmeyle ayrılmış tablo hücrelerini "a, b, c" biçimine, liste işaretlerinden sonraki sekmeyi boşluğa çevirir. */
    internal fun normalizeTabularLayout(text: String): String {
        return text.replace("\r\n", "\n").replace('\r', '\n').lineSequence().joinToString("\n") { rawLine ->
            // Word'ün Symbol/Wingdings madde imleri özel kullanım alanında gelir (ör. U+F0B7)
            val line = rawLine.trimStart(' ', '\t').trimEnd().replace(Regex("^[\\uF000-\\uF0FF]"), "•")
            val marker = Regex("""^(\d{1,3}[.)]|[•·▪◦●○■\-–])\t+""").find(line)
            val body = if (marker != null) marker.value.trimEnd('\t') + " " + line.substring(marker.range.last + 1) else line
            body.split(Regex("\\s*\\t+\\s*")).filter { it.isNotBlank() }.joinToString(", ")
        }.replace(Regex("\\n{3,}"), "\n\n").trim()
    }

    // ---------- Sayfalama ----------

    /** Sayfası olmayan biçimler, paragraf sınırlarında yaklaşık 3000 karakterlik "konum"lara bölünür. */
    private fun locationContent(text: String): DocumentContent {
        val clean = text.trim()
        val pages = mutableListOf<DocumentPage>()
        var start = 0
        while (start < clean.length) {
            var end = (start + 3000).coerceAtMost(clean.length)
            if (end < clean.length) {
                val paragraph = clean.lastIndexOf("\n", end).takeIf { it > start + 1200 }
                val sentence = clean.lastIndexOfAny(charArrayOf('.', '!', '?', '؟'), end).takeIf { it > start + 1200 }?.plus(1)
                val space = clean.lastIndexOf(' ', end).takeIf { it > start }
                end = paragraph ?: sentence ?: space ?: end
            }
            val number = pages.size + 1
            pages += DocumentPage(number = number, label = "Konum $number", title = "Konum $number", text = clean.substring(start, end).trim())
            start = end
            while (start < clean.length && clean[start].isWhitespace()) start++
        }
        return buildContent(pages, PageUnit.Location)
    }

    private fun buildContent(pages: List<DocumentPage>, pageUnit: PageUnit): DocumentContent {
        var cursor = 0
        val indexed = pages.ifEmpty { listOf(DocumentPage(1, "Sayfa 1", "Sayfa 1", "")) }.mapIndexed { index, page ->
            if (index > 0) cursor += 2
            val start = cursor
            cursor += page.text.length
            page.copy(startIndex = start, endIndex = cursor)
        }
        return DocumentContent(text = indexed.joinToString("\n\n") { it.text }, pages = indexed, pageUnit = pageUnit)
    }
}

class UnreadableDocumentException(message: String) : IllegalStateException(message)

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
