package com.example.filiz.desktop

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.example.filiz.desktop.service.DatabaseService
import com.example.filiz.desktop.service.PreferenceService
import com.example.filiz.desktop.service.SpeechEngine
import com.example.filiz.desktop.service.VoiceInfo
import com.example.filiz.desktop.ui.App
import com.example.filiz.desktop.ui.AppState
import com.example.filiz.desktop.ui.Screen
import com.example.filiz.desktop.ui.UiLanguage
import com.example.filiz.desktop.ui.theme.ThemeMode
import kotlinx.coroutines.delay
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Gerçek ses yerine cümle cümle konum bildiren sahte motor. */
class FakeSpeech : SpeechEngine {
    data class Call(val start: Int)
    val calls = mutableListOf<Call>()
    @Volatile private var session = 0

    override fun getAvailableVoices() = listOf(VoiceInfo("fahrettin", "Fahrettin", "tr"), VoiceInfo("ar_kareem_low", "Kareem", "ar"))
    override fun setVoice(voiceId: String) = Unit
    override fun setSpeed(speed: Float) = Unit
    override fun setDefaultSleepTimer(minutes: Int?) = Unit
    override fun stop() { session++ }

    // Gerçek motor gibi arka plan iş parçacığında, gerçek zamanla çalışır (test saatini kilitlemez)
    override suspend fun speak(text: String, startIndex: Int, onStatus: (String) -> Unit, onRange: (Int, Int) -> Unit, onComplete: () -> Unit) =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { speakImpl(text, startIndex, onStatus, onRange) }

    private suspend fun speakImpl(text: String, startIndex: Int, onStatus: (String) -> Unit, onRange: (Int, Int) -> Unit) {
        val mine = ++session
        calls += Call(startIndex)
        onStatus("Okunuyor.")
        var i = 0
        // Birkaç cümle okur, sonra durdurulana kadar son cümlede bekler (testler kararlı olsun)
        repeat(4) {
            if (session != mine) return
            val end = text.indexOfAny(charArrayOf('.', '\n'), i).let { if (it < 0) text.length else it + 1 }
            if (end <= i) return@repeat
            onRange(startIndex + i, startIndex + end)
            delay(150)
            if (end < text.length) i = end
            while (i < text.length - 1 && text[i].isWhitespace()) i++
        }
        while (session == mine) delay(50)
    }
}

@OptIn(ExperimentalTestApi::class)
class AppUiTest {
    private val docs = File(javaClass.getResource("/testdocs")!!.toURI())
    private val shots = File("build/screenshots").apply { mkdirs() }

    /** Ana pencere ve açık diyaloglar dahil tüm katmanları tek görüntüde birleştirir. */
    private fun SemanticsNodeInteractionsProvider.shot(name: String) {
        val roots = onAllNodes(isRoot())
        val count = roots.fetchSemanticsNodes().size
        val base = roots[0].captureToImage().toAwtImage()
        val out = java.awt.image.BufferedImage(base.getWidth(null), base.getHeight(null), java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.drawImage(base, 0, 0, null)
        for (i in 1 until count) {
            val node = roots[i].fetchSemanticsNode()
            val image = roots[i].captureToImage().toAwtImage()
            g.drawImage(image, node.boundsInRoot.left.toInt(), node.boundsInRoot.top.toInt(), null)
        }
        g.dispose()
        ImageIO.write(out, "png", File(shots, "$name.png"))
    }

    private fun freshDataDir(): File {
        val dir = createTempDirectory("bideng-test").toFile()
        DatabaseService.initialize(File(dir, "library.db").absolutePath)
        PreferenceService.initialize(dir)
        return dir
    }

    private fun createTempDirectory(prefix: String) = kotlin.io.path.createTempDirectory(prefix)

    @Test
    fun fullFlow() = runDesktopComposeUiTest(width = 1180, height = 820) {
        val dataDir = freshDataDir()
        val speech = FakeSpeech()
        lateinit var state: AppState
        setContent {
            val scope = rememberCoroutineScope()
            state = remember { AppState(scope, dataDir, speech) }
            App(state, onImport = {})
        }

        // Boş kütüphane
        onNodeWithText("Kütüphanen boş").assertExists()
        shot("01-bos-kutuphane")

        // 8 belgeyi içe aktar (tablo, resim, liste, Arapça, Kürtçe, Farsça)
        val files = listOf("pdf", "docx", "doc", "odt", "epub", "rtf", "html").map { File(docs, "bideng-test.$it") } + File(docs, "farsca-test.txt")
        state.importFiles(files + File(docs, "taranmis.pdf"))
        waitUntil(30_000) { state.items.size == 8 && state.screen == Screen.Reader }
        waitForIdle()
        // Taranmış PDF eklenmemeli, anlaşılır bir mesaj verilmeli
        assertTrue(state.items.none { it.name == "taranmis.pdf" })

        // İlk belge okuyucuda açıldı, tablo satırı düzgün
        onAllNodesWithText("Kuyucaklı Yusuf", substring = true, useUnmergedTree = true).fetchSemanticsNodes().let {
            assertTrue(it.isNotEmpty(), "okuyucuda metin yok")
        }
        shot("02-okuyucu-koyu")

        // Notlar penceresi açılıp kapanmalı (eski sürümde geri dönülemiyordu)
        onNodeWithContentDescription("Notlar").performClick()
        onNodeWithText("Henüz not yok", substring = true).assertExists()
        shot("03-notlar-bos")
        onNodeWithText("Kapat").performClick()
        waitForIdle()
        assertEquals(false, state.notesDialogOpen)
        assertEquals(Screen.Reader, state.screen)

        // Oynat → duraklat → devam → durdur
        val reader = state.reader!!
        onNodeWithContentDescription("Oynat").performClick()
        waitUntil(5_000) { reader.isReading && !reader.activeRange.isEmpty() }
        assertEquals(0, speech.calls.last().start)
        mainClock.advanceTimeBy(400)
        waitUntil(5_000) { reader.lastSpokenStart > 0 }
        shot("04-okunuyor")
        val spoken = reader.lastSpokenStart
        onNodeWithContentDescription("Duraklat").performClick()
        waitForIdle()
        assertEquals(false, reader.isReading)
        assertEquals(spoken, reader.pausedIndex, "duraklatılan cümle hatırlanmalı")
        onNodeWithTag("readerStatus").assertExists()
        shot("05-duraklatildi")
        onNodeWithContentDescription("Devam et").performClick()
        waitUntil(5_000) { reader.isReading }
        assertEquals(spoken, speech.calls.last().start, "devam, kalınan cümleden başlamalı")
        onNodeWithContentDescription("Durdur").performClick()
        waitForIdle()
        assertEquals(-1, reader.pausedIndex)
        assertEquals(false, reader.isReading)

        // Okuma ayarları paneli ve görünüm penceresi
        onNodeWithContentDescription("Okuma ayarları").performClick()
        onNodeWithTag("settingsSection").assertExists()
        shot("06-ayarlar")
        onNodeWithContentDescription("Görünüm").performClick()
        onNodeWithText("Yazı boyutu").assertExists()
        shot("07-gorunum")
        onNodeWithText("Sepya").performClick()
        onNodeWithText("Tamam").performClick()
        waitForIdle()
        assertEquals(ThemeMode.Sepia, state.themeMode)
        shot("08-okuyucu-sepya")
        onNodeWithContentDescription("Kapat").performClick()

        // Metinde ara
        onNodeWithContentDescription("Metinde ara").performClick()
        onNodeWithTag("readerSearch").performTextReplacement("Kürtçe")
        waitUntil(5_000) { onAllNodesWithText("1 / ", substring = true).fetchSemanticsNodes().isNotEmpty() }
        shot("09-arama")
        onNodeWithContentDescription("Kapat").performClick()

        // Not ekle ve notlardan sil
        state.addNote(0 until 19)
        onNodeWithContentDescription("Notlar").performClick()
        onNodeWithText("“Bideng Test Belgesi”", substring = true).assertExists()
        shot("10-notlar")
        onNodeWithContentDescription("Sil").performClick()
        waitForIdle()
        assertTrue(state.notes.isEmpty())
        onNodeWithText("Kapat").performClick()

        // Geri → kütüphane
        onNodeWithContentDescription("Kütüphane").performClick()
        waitForIdle()
        assertEquals(Screen.Library, state.screen)
        onNodeWithTag("continueCard").assertExists()
        shot("11-kutuphane-sepya")
        state.setTheme(ThemeMode.Light)
        waitForIdle()
        shot("12-kutuphane-acik")

        // Yeniden adlandır
        val target = state.items.first { it.name == "bideng-test.rtf" }
        onNode(hasContentDescription("Seçenekler: bideng-test.rtf")).performClick()
        onNodeWithText("Yeniden adlandır").performClick()
        onNodeWithTag("renameField").performTextReplacement("Zengin Metin.rtf")
        shot("13-yeniden-adlandir")
        onNodeWithText("Kaydet").performClick()
        waitForIdle()
        assertEquals("Zengin Metin.rtf", state.items.first { it.id == target.id }.name)

        // Sil
        onNode(hasContentDescription("Seçenekler: Zengin Metin.rtf")).performClick()
        onNodeWithText("Sil").performClick()
        onNodeWithText("Belge silinsin mi?").assertExists()
        onAllNodesWithText("Sil").fetchSemanticsNodes().size.let { assertTrue(it >= 1) }
        onNode(androidx.compose.ui.test.hasText("Sil") and androidx.compose.ui.test.hasClickAction()).performClick()
        waitForIdle()
        assertEquals(7, state.items.size)

        // Arapça arayüz: sağdan sola
        state.setTheme(ThemeMode.Dark)
        state.changeLanguage(UiLanguage.Arabic)
        waitForIdle()
        onNodeWithText("المكتبة").assertExists()
        shot("14-kutuphane-arapca")
        state.changeLanguage(UiLanguage.Turkish)

        // Duraklatılmış okumayı kütüphaneden sürdürme
        val docItem = state.items.first { it.name == "bideng-test.docx" }
        state.open(docItem)
        waitUntil(10_000) { state.screen == Screen.Reader && state.document?.item?.id == docItem.id }
        waitForIdle()
        onNodeWithContentDescription("Oynat").performClick()
        waitUntil(5_000) { state.reader!!.isReading }
        mainClock.advanceTimeBy(400)
        waitUntil(5_000) { state.reader!!.lastSpokenStart > 0 }
        val pausedAt = state.reader!!.lastSpokenStart
        onNodeWithContentDescription("Kütüphane").performClick() // geri giderken okuma duraklatılır
        waitForIdle()
        assertEquals(pausedAt, PreferenceService.getPausedIndex(docItem.id))
        onNodeWithText("Sesli okuma duraklatıldı").assertExists()
        shot("15-kutuphane-duraklatildi")
        onNodeWithContentDescription("Devam et").performClick()
        waitUntil(10_000) { state.screen == Screen.Reader && state.reader?.isReading == true }
        assertEquals(pausedAt, speech.calls.last().start)

        // Elle uzağa kaydırınca okuma geri çekmez, ▶ "buradan oku" olur
        onNodeWithTag("readerText").performMouseInput { repeat(30) { scroll(8f) } }
        waitUntil(5_000) { !state.reader!!.followReading }
        // Ekran boşta kendini sürekli yeniden oluşturmamalı (işlemciyi boşa yakan döngü)
        mainClock.autoAdvance = false
        val before = com.example.filiz.desktop.ui.screens.ReaderDiagnostics.compositions
        repeat(20) { mainClock.advanceTimeByFrame() }
        val growth = com.example.filiz.desktop.ui.screens.ReaderDiagnostics.compositions - before
        println("20 karede yeniden oluşturma: $growth")
        mainClock.autoAdvance = true
        assertTrue(growth < 5, "okuyucu boşta sürekli yeniden oluşturuluyor: $growth")
        shot("16-buradan-oku")
        onNodeWithContentDescription("Buradan okumak için ▶").assertExists()
        state.reader!!.stop()
        mainClock.advanceTimeBy(200)
        Unit
    }
}
