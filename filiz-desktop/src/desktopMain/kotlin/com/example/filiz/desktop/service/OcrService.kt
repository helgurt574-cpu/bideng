package com.example.filiz.desktop.service

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.bytedeco.tesseract.TessBaseAPI
import org.bytedeco.tesseract.global.tesseract.PSM_AUTO
import java.awt.image.DataBufferByte
import java.io.File
import java.security.MessageDigest

/**
 * Taranmış (yalnızca resimden oluşan) PDF'leri Tesseract ile okur; tamamen çevrimdışı.
 * Dil verileri pakete gömülüdür (tools/prepare_app_resources.py → tessdata/).
 * Belgenin dili ilk yazılı sayfada Türkçe+İngilizce, Farsça ve Arapça ağırlıklı modeller
 * denenip güveni en yüksek olan seçilerek bulunur (Farsça ی/ک ile Arapça ي/ك ayrımı için ikisi de denenir). Sonuç diskte saklanır; belge ikinci kez taranmaz.
 */
object OcrService {
    private const val DPI = 300f
    private val SCRIPTS = listOf("tur+eng", "fas+ara", "ara+fas")
    private val REQUIRED = listOf("tur", "eng", "ara", "fas").map { "$it.traineddata" }

    /** OCR sonuçlarının saklandığı klasör (Main ayarlar); null ise saklanmaz. */
    @Volatile var cacheDir: File? = null

    /** Son taranan belge için seçilen Tesseract dil kümesi (tanılama ve testler için). */
    @Volatile var lastLanguage: String? = null
        private set

    fun tessdataDir(): File? {
        val candidates = listOfNotNull(
            System.getProperty("compose.application.resources.dir")?.takeIf { it.isNotBlank() }?.let { File(it, "tessdata") },
            System.getProperty("bideng.app.dir")?.takeIf { it.isNotBlank() }?.let { File(it, "tessdata") },
            File(System.getProperty("user.dir"), "tessdata"),
            // Kaynaktan çalıştırma: prepare_app_resources.py çıktısı
            File(System.getProperty("user.dir"), "build/app-resources/common/tessdata")
        )
        return candidates.firstOrNull { dir -> REQUIRED.all { File(dir, it).isFile } }
    }

    fun isAvailable(): Boolean = tessdataDir() != null

    /** Her sayfanın metnini döndürür (boş sayfalar boş metin). [onPage]: (okunan sayfa, toplam). */
    fun readPdf(file: File, document: PDDocument, onPage: ((Int, Int) -> Unit)? = null): List<String> {
        val cached = cacheFile(file)
        cached?.takeIf { it.isFile }?.let { return it.readText().split(PAGE_BREAK) }
        val dataDir = tessdataDir() ?: throw IllegalStateException("OCR dil verileri bulunamadı.")
        val renderer = PDFRenderer(document)
        val total = document.numberOfPages
        val pages = ArrayList<String>(total)
        var api: TessBaseAPI? = null
        try {
            for (index in 0 until total) {
                onPage?.invoke(index + 1, total)
                val image = renderer.renderImageWithDPI(index, DPI, ImageType.GRAY)
                val pixels = (image.raster.dataBuffer as DataBufferByte).data
                val text = if (api == null) {
                    // Dil seçimi: her yazı sistemiyle dene, güveni yüksek olanla devam et
                    val tries = SCRIPTS.map { language ->
                        val candidate = open(dataDir, language)
                        val result = recognize(candidate, pixels, image.width, image.height)
                        Triple(candidate, result, if (result.isBlank()) -1 else candidate.MeanTextConf())
                    }
                    val best = tries.maxBy { it.third }
                    System.err.println("OCR dil seçimi: " + tries.joinToString { "${it.first.GetInitLanguagesAsString()?.string}=${it.third}" })
                    lastLanguage = SCRIPTS[tries.indexOf(best)]
                    tries.filter { it !== best }.forEach { close(it.first) }
                    if (best.second.isBlank()) close(best.first) else api = best.first
                    best.second
                } else {
                    recognize(api, pixels, image.width, image.height)
                }
                pages += text
            }
        } finally {
            api?.let(::close)
        }
        cached?.runCatching {
            parentFile.mkdirs()
            writeText(pages.joinToString(PAGE_BREAK))
        }
        return pages
    }

    private const val PAGE_BREAK = "\u000c"

    private fun open(dataDir: File, language: String): TessBaseAPI {
        val api = TessBaseAPI()
        if (api.Init(dataDir.absolutePath, language) != 0) {
            api.close()
            throw IllegalStateException("OCR motoru başlatılamadı ($language).")
        }
        api.SetPageSegMode(PSM_AUTO)
        return api
    }

    private fun close(api: TessBaseAPI) {
        api.End()
        api.close()
    }

    private fun recognize(api: TessBaseAPI, pixels: ByteArray, width: Int, height: Int): String {
        api.SetImage(pixels, width, height, 1, width)
        api.SetSourceResolution(DPI.toInt())
        val output = api.GetUTF8Text() ?: return ""
        return try {
            output.getString(Charsets.UTF_8)
        } finally {
            output.deallocate()
        }.replace("\r", "").trim()
    }

    private fun cacheFile(file: File): File? {
        val dir = cacheDir ?: return null
        val key = "${file.absolutePath}|${file.length()}|${file.lastModified()}"
        val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, "$hash.txt")
    }
}
