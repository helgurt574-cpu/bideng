package com.example.filiz.desktop

import com.example.filiz.desktop.service.TTSService
import com.example.filiz.desktop.ui.AppState
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * Paketlenmiş uygulamayı uçtan uca dener: BIDENG_SELFTEST=<belge> ile başlatılınca gerçek pencere
 * açıkken belgeyi içe aktarır ve her gömülü sesle gerçekten seslendirir. Sonuç standart çıktıya yazılır.
 */
suspend fun runSelfTest(state: AppState, document: File): Boolean {
    fun log(message: String) = println("SELFTEST: $message").also { System.out.flush() }
    delay(1500)
    log("içe aktarılıyor ${document.name}")
    state.importFiles(listOf(document))
    var lastToast: String? = null
    val reader = withTimeoutOrNull(30_000) {
        while (state.reader == null) {
            state.toast?.message?.let { lastToast = it }
            delay(50)
        }
        state.reader
    } ?: return false.also { log("belge açılmadı, kullanıcıya gösterilen mesaj: $lastToast") }
    log("açıldı: ${reader.text.length} karakter")

    var ok = true
    for (voice in TTSService.getAvailableVoices()) {
        TTSService.setVoice(voice.id)
        reader.speakFrom(0)
        val spoke = withTimeoutOrNull(90_000) {
            while (reader.activeRange.isEmpty() && reader.isReading) delay(50)
            !reader.activeRange.isEmpty()
        } ?: false
        log("ses ${voice.id}: ${if (spoke) "OKUYOR" else "HATA"} (durum=${reader.status})")
        ok = ok && spoke
        delay(2500)
        reader.pause()
        val paused = reader.pausedIndex >= 0
        reader.toggle()
        delay(1500)
        log("ses ${voice.id}: duraklat=${paused} devam=${reader.isReading}")
        ok = ok && paused && reader.isReading
        reader.stop()
        delay(300)
    }
    // Sabırsız kullanıcı: okurken hızlıca oynat/duraklat, ileri/geri, ses ve hız değiştir
    val voices = TTSService.getAvailableVoices()
    val random = java.util.Random(7)
    repeat(60) { step ->
        when (random.nextInt(6)) {
            0, 1 -> reader.toggle()
            2 -> reader.skip(if (random.nextBoolean()) 1 else -1)
            3 -> { TTSService.setVoice(voices[random.nextInt(voices.size)].id); reader.restartIfReading() }
            4 -> { TTSService.setSpeed(0.5f + random.nextInt(4) * 0.5f); reader.restartIfReading() }
            5 -> reader.speakFrom(random.nextInt(reader.text.length.coerceAtLeast(1)))
        }
        delay(50L + random.nextInt(900))
        if (step % 20 == 19) log("zorlama adımı ${step + 1}/60")
    }
    TTSService.setSpeed(1f)
    TTSService.setVoice(voices.first().id)
    reader.speakFrom(0)
    val recovered = withTimeoutOrNull(60_000) {
        while (reader.activeRange.isEmpty() && reader.isReading) delay(50)
        !reader.activeRange.isEmpty()
    } ?: false
    log("zorlamadan sonra okuma: ${if (recovered) "OKUYOR" else "HATA"}")
    ok = ok && recovered
    reader.stop()

    state.notesDialogOpen = true
    delay(500)
    state.notesDialogOpen = false
    state.closeReader()
    delay(500)
    // Okurken kapatma: çağıran, ses motoru tam çalışırken uygulamayı kapatır
    state.open(reader.document.item)
    withTimeoutOrNull(10_000) { while (state.reader === reader || state.reader == null) delay(50) }
    state.reader?.speakFrom(0)
    delay(400)
    log(if (ok) "BAŞARILI" else "BAŞARISIZ")
    return ok
}
