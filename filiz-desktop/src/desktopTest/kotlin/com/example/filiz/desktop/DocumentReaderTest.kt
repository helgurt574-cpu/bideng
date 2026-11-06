package com.example.filiz.desktop

import com.example.filiz.desktop.service.DocumentReaderService
import com.example.filiz.desktop.service.UnreadableDocumentException
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class DocumentReaderTest {
    private fun doc(name: String): File = File(javaClass.getResource("/testdocs/$name")!!.toURI())

    // Tablo, resim, liste, Arapça ve Kürtçe içeren aynı belgenin her biçimi
    private val formats = listOf("pdf", "docx", "doc", "odt", "epub", "rtf", "html")

    @Test
    fun everyFormatKeepsStructure() {
        for (format in formats) {
            val content = DocumentReaderService.readDocumentContent(doc("bideng-test.$format"))
            val text = content.text
            val where = "[$format]"
            assertTrue(text.contains("Bideng Test Belgesi"), "$where başlık yok")
            // Tablo satırları ayrı satırda, hücreler virgülle
            // PDF'te tablo yapısı yoktur: hücreler aynı satırda boşlukla gelir
            val sep = if (format == "pdf") " " else ", "
            assertTrue(text.contains(listOf("Kuyucaklı Yusuf", "Sabahattin Ali", "232", "Okundu").joinToString(sep)), "$where tablo satırı bozuk:\n$text")
            assertTrue(text.contains(listOf("İnce Memed", "Yaşar Kemal", "380", "Sırada").joinToString(sep)), "$where tablo satırı bozuk")
            // Başlık ile paragraf birbirine yapışmamalı
            assertFalse(text.contains("1. Tablo Aşağıdaki"), "$where başlık paragrafa yapışmış")
            assertTrue(text.contains("هذا نص عربي قصير"), "$where Arapça yok")
            assertTrue(text.contains("Ev nivîseke kurt"), "$where Kürtçe yok")
            assertTrue(text.contains("Son paragraf: belge burada biter."), "$where son paragraf yok")
            if (format in setOf("docx", "odt", "doc", "html", "pdf")) {
                assertTrue(text.contains("• Birinci madde: sesli okuma"), "$where madde işareti yok: " + text.substringBefore("Birinci madde").takeLast(40) + "Birinci madde")
            }
            // Sayfa konumları metinle tutarlı
            for (page in content.pages) {
                assertEquals(page.text, text.substring(page.startIndex, page.endIndex), "$where sayfa konumu kaymış")
            }
        }
    }

    @Test
    fun pdfLinesAreReflowed() {
        val text = DocumentReaderService.readDocumentContent(doc("bideng-test.pdf")).text
        assertTrue(text.contains("çökmemeli ve metni anlamlı bir sırayla"), "PDF satır kırılması birleşmedi")
        assertTrue(text.contains("Okundu\nSaatleri Ayarlama"), "tablo satırları birleşmemeli")
        assertEquals("okumak ve dinlemek", DocumentReaderService.reflowPdfLines("oku-\nmak ve\ndinlemek"))
    }

    @Test
    fun htmlHeadIsNotRead() {
        val text = DocumentReaderService.readDocumentContent(doc("bideng-test.html")).text
        assertEquals(1, Regex("Bideng Test Belgesi").findAll(text).count(), "HTML başlığı iki kez okunuyor")
    }

    @Test
    fun persianTextFile() {
        val text = DocumentReaderService.readDocumentContent(doc("farsca-test.txt")).text
        assertTrue(text.startsWith("آزمایش خواندن فارسی"))
    }

    @Test
    fun scannedPdfGivesClearMessage() {
        val error = assertFailsWith<UnreadableDocumentException> {
            DocumentReaderService.readDocumentContent(doc("taranmis.pdf"))
        }
        assertTrue(error.message!!.contains("taranmış"))
    }

    @Test
    fun locationPagesDoNotSplitWords() {
        val file = File.createTempFile("uzun", ".txt").apply {
            writeText((1..400).joinToString(" ") { "Kelime$it ve cümle $it." })
            deleteOnExit()
        }
        val content = DocumentReaderService.readDocumentContent(file)
        assertTrue(content.pages.size > 1)
        for (page in content.pages) {
            assertTrue(page.text.first().isLetterOrDigit() && page.text.last() == '.', "konum kelime ortasından bölünmüş: ${page.text.takeLast(20)}")
        }
    }
}
