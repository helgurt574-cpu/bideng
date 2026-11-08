package com.example.filiz.desktop.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine

interface SpeechEngine {
    fun getAvailableVoices(): List<VoiceInfo>
    fun setVoice(voiceId: String)
    fun setSpeed(speed: Float)
    fun setDefaultSleepTimer(minutes: Int?)
    suspend fun speak(
        text: String,
        startIndex: Int = 0,
        onStatus: (String) -> Unit = {},
        onRange: (Int, Int) -> Unit = { _, _ -> },
        onComplete: () -> Unit = {}
    )
    fun stop()
}

object TTSService : SpeechEngine {
    private var currentVoice: String = DEFAULT_EMBEDDED_VOICE.id
    private var currentSpeed: Float = 1.0f
    // Her okuma kendi oturum numarasıyla çalışır; durdurulan ya da yerine yenisi başlayan
    // okuma döngüsü kendiliğinden biter (iki sesin üst üste binmesini önler).
    private val generation = java.util.concurrent.atomic.AtomicInteger(0)
    @Volatile private var activeGeneration: Int = -1
    private val isSpeaking: Boolean get() = activeGeneration >= 0
    private fun isActive(session: Int): Boolean = activeGeneration == session
    // sherpa-onnx doğrudan JNI ile kullanılır: metin Unicode olarak gider (Windows'ta komut satırı
    // argümanları ANSI kod sayfasına çevrildiği için Türkçe/Arapça/Farsça harfler bozuluyordu).
    private var engine: OfflineTts? = null
    // Yerel TTS motoru iş parçacığı güvenli değil: oluşturma, seslendirme ve serbest bırakma tek tek yapılmalı.
    // Yoksa önceki okumanın generate() çağrısı sürerken yeni okuma motoru kullanır ya da siler ve uygulama çöker.
    private val engineLock = Any()
    private val muteOutput = System.getenv("BIDENG_SELFTEST_MUTE") != null
    private var engineVoiceId: String? = null
    private var currentLine: SourceDataLine? = null
    private var defaultSleepTimerMinutes: Int? = 30
    var playbackState by mutableStateOf(TtsPlaybackState())
        private set

    // Skiko (Compose) yerel kütüphanesi kendi C++ çalışma zamanını dışa açıyor; sherpa-onnx ondan sonra
    // yüklenirse sembolleri karışıp çöküyor. Bu yüzden arayüzden önce, uygulama açılırken yüklenir.
    fun preloadNativeLibraries() {
        runCatching {
            File(appHome(), "sherpa-native").takeIf { it.isDirectory }?.let {
                System.setProperty("sherpa_onnx.native.path", it.absolutePath)
            }
            com.k2fsa.sherpa.onnx.LibraryUtils.load()
        }.onFailure { it.printStackTrace() }
    }

    fun initialize() {
        currentVoice = PreferenceService.getTTSVoice().ifBlank { DEFAULT_EMBEDDED_VOICE.id }
        currentSpeed = PreferenceService.getTTSSpeed()
    }

    override fun getAvailableVoices(): List<VoiceInfo> {
        return EMBEDDED_VOICES.map { VoiceInfo(it.id, it.label, it.language) }
    }

    override suspend fun speak(
        text: String,
        startIndex: Int,
        onStatus: (String) -> Unit,
        onRange: (Int, Int) -> Unit,
        onComplete: () -> Unit
    ) = withContext(Dispatchers.IO) {
        speakWithSherpa(text, startIndex, onStatus, onRange, onComplete)
    }

    private fun speakWithSherpa(
        text: String,
        startIndex: Int,
        onStatus: (String) -> Unit,
        onRange: (Int, Int) -> Unit,
        onComplete: () -> Unit
    ) {
        val session = synchronized(this) {
            stop()
            generation.incrementAndGet().also { activeGeneration = it }
        }
        try {
            if (defaultSleepTimerMinutes != null) {
                setSleepTimer(defaultSleepTimerMinutes)
            }
            playbackState = playbackState.copy(
                isSpeaking = true,
                status = "Hazırlanıyor",
                startedAt = System.currentTimeMillis()
            )
            val voice = EMBEDDED_VOICES.firstOrNull { it.id == currentVoice } ?: DEFAULT_EMBEDDED_VOICE
            val appHome = appHome()
            val modelRoot = File(appHome, voice.modelDir)
            val model = File(modelRoot, voice.modelName)
            val tokens = File(modelRoot, "tokens.txt")
            // Tüm sesler ortak (budanmış) espeak-ng verisini kullanır; eski paket düzeni için ses klasörüne de bakılır
            val espeak = File(appHome, "tts/espeak-ng-data").takeIf { File(it, "phontab").exists() }
                ?: File(modelRoot, "espeak-ng-data")
            val preflightError = preflightError(model, tokens, espeak)
            if (preflightError != null) {
                onStatus(preflightError)
                if (isActive(session)) activeGeneration = -1
                playbackState = playbackState.copy(isSpeaking = false, status = preflightError)
                onComplete()
                return
            }
            onStatus("Dahili ses hazırlanıyor: ${voice.label}")
            playbackState = playbackState.copy(status = "Dahili ses hazırlanıyor: ${voice.label}")
            val segments = text.cleanForSpeechPreservingOffsets().speechSegments(startIndex)
            for (segment in segments) {
                if (!isActive(session)) break
                if (sleepTimerExpired()) {
                    onStatus("Uyku zamanlayıcısı nedeniyle durduruldu.")
                    break
                }
                onRange(segment.start, segment.end)
                if (!isActive(session)) break
                val audio = synchronized(engineLock) {
                    if (!isActive(session)) return@synchronized null
                    engineFor(voice, appHome, model, tokens, espeak)
                        .generate(segment.text.toTtsArgument(), 0, currentSpeed.coerceIn(0.5f, 2.0f))
                } ?: break
                if (!isActive(session)) break
                if (audio.samples.isNotEmpty()) {
                    onStatus("Okunuyor.")
                    playbackState = playbackState.copy(
                        isSpeaking = true,
                        status = "Okunuyor",
                        currentText = segment.text.take(140),
                        currentRangeStart = segment.start,
                        currentRangeEnd = segment.end
                    )
                    playSamples(audio.samples, audio.sampleRate, segment, onRange, session)
                } else {
                    onStatus("TTS bu bölümü seslendiremedi.")
                }
            }
            // Yerine yeni bir okuma başladıysa onun durumuna dokunma
            if (!isActive(session)) return
            activeGeneration = -1
            playbackState = playbackState.copy(isSpeaking = false, status = "Durdu", currentText = "", sleepTimerEndsAt = null)
            onRange(-1, -1)
            onComplete()
        } catch (e: Exception) {
            if (!isActive(session)) return
            onStatus("TTS hatası: ${e.message ?: e::class.simpleName}")
            activeGeneration = -1
            playbackState = playbackState.copy(isSpeaking = false, status = "TTS hatası: ${e.message ?: e::class.simpleName}")
            onRange(-1, -1)
            onComplete()
        }
    }

    /**
     * Uygulama kapanırken çağrılır: süren generate() bitene kadar bekleyip motoru serbest bırakır.
     * Yoksa çıkışta yerel kütüphane, kullanımdaki motorun altından sökülür ve JVM çöker.
     */
    fun shutdown() {
        stop()
        synchronized(engineLock) {
            runCatching { engine?.release() }
            engine = null
            engineVoiceId = null
        }
    }

    override fun stop() {
        activeGeneration = -1
        currentLine?.let { line -> runCatching { line.stop(); line.flush(); line.close() } }
        currentLine = null
        playbackState = playbackState.copy(isSpeaking = false, status = "Durdu", currentText = "", sleepTimerEndsAt = null)
    }

    fun pause() {
        // PowerShell'de pause desteklenmiyor
    }

    fun resume() {
        // PowerShell'de resume desteklenmiyor
    }

    override fun setVoice(voiceId: String) {
        currentVoice = EMBEDDED_VOICES.firstOrNull { it.id == voiceId }?.id ?: DEFAULT_EMBEDDED_VOICE.id
    }

    override fun setSpeed(speed: Float) {
        currentSpeed = speed.coerceIn(0.5f, 2.0f)
    }

    fun isCurrentlySpeaking(): Boolean = isSpeaking

    fun setNowPlaying(documentName: String, pageLabel: String) {
        playbackState = playbackState.copy(documentName = documentName, pageLabel = pageLabel)
    }

    fun setSleepTimer(minutes: Int?) {
        playbackState = if (minutes == null) {
            playbackState.copy(sleepTimerEndsAt = null)
        } else {
            playbackState.copy(sleepTimerEndsAt = System.currentTimeMillis() + minutes * 60_000L)
        }
    }

    override fun setDefaultSleepTimer(minutes: Int?) {
        defaultSleepTimerMinutes = minutes
    }

    fun getDefaultSleepTimer(): Int? = defaultSleepTimerMinutes

    fun sleepRemainingSeconds(): Long? {
        return playbackState.sleepTimerEndsAt?.let { ((it - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L) }
    }

    // Kurulum paketlerinde sesler ve sherpa, Compose'un uygulama kaynakları klasöründedir
    private fun appHome(): File {
        System.getProperty("compose.application.resources.dir")?.takeIf { it.isNotBlank() }?.let { dir ->
            File(dir).takeIf { File(it, "tts").isDirectory }?.let { return it }
        }
        System.getProperty("bideng.app.dir")?.takeIf { it.isNotBlank() }?.let { return File(it) }
        return File(System.getProperty("user.dir"))
    }

    /** Yalnızca [engineLock] tutulurken çağrılır. */
    private fun engineFor(voice: EmbeddedVoice, appHome: File, model: File, tokens: File, espeak: File): OfflineTts {
        engine?.takeIf { engineVoiceId == voice.id }?.let { return it }
        engine?.release()
        engine = null
        // Paketteki yerel kütüphaneler (onnxruntime + sherpa-onnx-jni) bu klasörden yüklenir
        File(appHome, "sherpa-native").takeIf { it.isDirectory }?.let {
            System.setProperty("sherpa_onnx.native.path", it.absolutePath)
        }
        val config = OfflineTtsConfig.builder()
            .setModel(
                OfflineTtsModelConfig.builder()
                    .setVits(
                        OfflineTtsVitsModelConfig.builder()
                            .setModel(model.absolutePath)
                            .setTokens(tokens.absolutePath)
                            .setDataDir(espeak.absolutePath)
                            .build()
                    )
                    .setNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(1, 4))
                    .setDebug(false)
                    .build()
            )
            .setMaxNumSentences(1)
            .build()
        return OfflineTts(config).also {
            engine = it
            engineVoiceId = voice.id
        }
    }

    private fun playSamples(
        samples: FloatArray,
        sampleRate: Int,
        segment: SpeechSegment,
        onRange: (Int, Int) -> Unit,
        session: Int
    ) {
        if (muteOutput) {
            // Ses kartı olmayan test makineleri: çalma süresi kadar beklenir
            var played = 0
            while (isActive(session) && played < samples.size && !sleepTimerExpired()) {
                Thread.sleep(50)
                played += sampleRate / 20
            }
            return
        }
        val format = AudioFormat(sampleRate.toFloat(), 16, 1, true, false)
        val line = AudioSystem.getLine(DataLine.Info(SourceDataLine::class.java, format)) as SourceDataLine
        currentLine = line
        line.open(format)
        line.start()
        val chunk = 4096
        val bytes = ByteArray(chunk * 2)
        var index = 0
        while (isActive(session) && index < samples.size) {
            if (sleepTimerExpired()) {
                activeGeneration = -1
                break
            }
            val count = minOf(chunk, samples.size - index)
            for (i in 0 until count) {
                val value = (samples[index + i].coerceIn(-1f, 1f) * 32767f).toInt()
                bytes[i * 2] = (value and 0xFF).toByte()
                bytes[i * 2 + 1] = ((value shr 8) and 0xFF).toByte()
            }
            line.write(bytes, 0, count * 2)
            index += count
        }
        if (isActive(session)) {
            line.drain()
        } else {
            line.flush()
        }
        runCatching { line.stop(); line.close() }
        if (currentLine == line) currentLine = null
    }

    private fun sleepTimerExpired(): Boolean {
        val deadline = playbackState.sleepTimerEndsAt ?: return false
        return System.currentTimeMillis() >= deadline
    }

    private fun String.cleanForSpeechPreservingOffsets(): String {
        val lines = splitToSequence('\n').toList()
        val repeated = lines
            .map { it.trim() }
            .filter { it.length in 4..90 }
            .groupingBy { it }
            .eachCount()
        return buildString(length) {
            lines.forEachIndexed { index, line ->
                val trimmed = line.trim()
                val skip = shouldSkipForTts(trimmed, repeated[trimmed] ?: 0)
                append(if (skip) " ".repeat(line.length) else line)
                if (index < lines.lastIndex) append('\n')
            }
        }
    }

    private fun shouldSkipForTts(line: String, repeatCount: Int): Boolean {
        if (line.isBlank()) return false
        if (line.length <= 4 && line.all { it.isDigit() || it.isWhitespace() || it == '-' }) return true
        if (line.matches(Regex("""(?i)^(sayfa|page)\s*\d+(\s*/\s*\d+)?$"""))) return true
        if (line.matches(Regex("""^\d+\s*/\s*\d+$"""))) return true
        if (line.contains(Regex("""https?://|www\.""", RegexOption.IGNORE_CASE))) return true
        if (line.contains(Regex("""\b[\w._%+-]+@[\w.-]+\.[A-Za-z]{2,}\b"""))) return true
        if (repeatCount >= 3 && line.length <= 90) return true
        return false
    }

    private fun String.speechSegments(baseIndex: Int): List<SpeechSegment> {
        val source = this
        if (source.isBlank()) return emptyList()
        val segments = mutableListOf<SpeechSegment>()
        var index = 0
        while (index < source.length) {
            while (index < source.length && source[index].isWhitespace()) index++
            if (index >= source.length) break
            val end = findReadableBlockEnd(source, index)
            val segmentText = source.substring(index, end).replace(Regex("\\s+"), " ").trim()
            if (segmentText.isNotBlank()) {
                segments += SpeechSegment(
                    text = segmentText,
                    start = baseIndex + index,
                    end = baseIndex + end
                )
            }
            index = end
            while (index < source.length && source[index].isWhitespace()) index++
        }
        return segments
    }

    private fun findReadableBlockEnd(text: String, start: Int): Int {
        val maxEnd = (start + 360).coerceAtMost(text.length)
        var index = start
        var lastSentenceEnd = -1
        while (index < text.length && index < maxEnd) {
            val char = text[index]
            if (char == '.' || char == '!' || char == '?' || char == '。' || char == '؟') {
                var end = index + 1
                while (end < text.length && text[end].isWhitespace()) end++
                return end
            }
            if (char == '\n' && index > start + 80) {
                return index + 1
            }
            index++
        }
        if (maxEnd >= text.length) return text.length
        if (lastSentenceEnd > start) return lastSentenceEnd
        val space = text.lastIndexOf(' ', startIndex = maxEnd)
        return if (space > start + 80) space else maxEnd
    }

    private fun String.toTtsArgument(): String {
        val cleaned = replace(Regex("[\\u0000-\\u001F\\u007F]+"), " ")
            .replace('“', '"')
            .replace('”', '"')
            .replace('‘', '\'')
            .replace('’', '\'')
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "." }

        return if (cleaned.startsWith("-")) {
            ". $cleaned"
        } else {
            cleaned
        }
    }

    private fun preflightError(model: File, tokens: File, espeak: File): String? {
        if (!model.exists()) return "TTS modeli bulunamadı: ${model.path}"
        if (!tokens.exists()) return "TTS tokens dosyası bulunamadı: ${tokens.path}"
        if (!File(espeak, "phontab").exists()) return "TTS dil verisi eksik: ${espeak.path}"
        return null
    }

    private fun shortError(output: String, exitCode: Int): String {
        return output
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { it.contains("parse-options.cc:Read:373") }
            .filterNot { it.startsWith("OfflineTtsConfig(") }
            .lastOrNull()
            ?.take(180)
            ?: "Sherpa çıkış kodu $exitCode"
    }

}

data class VoiceInfo(
    val id: String,
    val name: String,
    val language: String
)

data class TtsPlaybackState(
    val isSpeaking: Boolean = false,
    val status: String = "Durdu",
    val documentName: String = "",
    val pageLabel: String = "",
    val currentText: String = "",
    val currentRangeStart: Int = -1,
    val currentRangeEnd: Int = -1,
    val startedAt: Long = 0L,
    val sleepTimerEndsAt: Long? = null
)

private data class SpeechSegment(
    val text: String,
    val start: Int,
    val end: Int
)

private data class EmbeddedVoice(
    val id: String,
    val label: String,
    val language: String,
    val modelDir: String,
    val modelName: String
)

private val EMBEDDED_VOICES = listOf(
    EmbeddedVoice(id = "fahrettin", label = "Fahrettin", language = "tr", modelDir = "tts/tr_TR_fahrettin_medium", modelName = "model.onnx"),
    EmbeddedVoice(id = "dfki", label = "DFKI", language = "tr", modelDir = "tts/tr_TR_dfki_medium", modelName = "model.onnx"),
    EmbeddedVoice(id = "ar_kareem_low", label = "Arapça - Kareem", language = "ar", modelDir = "tts/vits-piper-ar_JO-kareem-low", modelName = "model.onnx"),
    EmbeddedVoice(id = "fa_amir", label = "Farsça - Amir", language = "fa", modelDir = "tts/vits-piper-fa_IR-amir-medium", modelName = "model.onnx"),
    EmbeddedVoice(id = "fa_gyro", label = "Farsça - Gyro", language = "fa", modelDir = "tts/vits-piper-fa_IR-gyro-medium", modelName = "model.onnx")
)

private val DEFAULT_EMBEDDED_VOICE = EMBEDDED_VOICES.first()
