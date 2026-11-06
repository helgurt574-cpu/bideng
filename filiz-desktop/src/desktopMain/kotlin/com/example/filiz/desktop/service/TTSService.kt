package com.example.filiz.desktop.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine

object TTSService {
    private var currentVoice: String = DEFAULT_EMBEDDED_VOICE.id
    private var currentSpeed: Float = 1.0f
    private var isSpeaking: Boolean = false
    private var currentProcess: Process? = null
    private var currentLine: SourceDataLine? = null
    private var defaultSleepTimerMinutes: Int? = 30
    var playbackState by mutableStateOf(TtsPlaybackState())
        private set

    fun initialize() {
        currentVoice = PreferenceService.getTTSVoice().ifBlank { DEFAULT_EMBEDDED_VOICE.id }
        currentSpeed = PreferenceService.getTTSSpeed()
    }

    fun getAvailableVoices(): List<VoiceInfo> {
        return EMBEDDED_VOICES.map { VoiceInfo(it.id, it.label, it.language) }
    }

    suspend fun speak(
        text: String,
        startIndex: Int = 0,
        onStatus: (String) -> Unit = {},
        onRange: (Int, Int) -> Unit = { _, _ -> },
        onComplete: () -> Unit = {}
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
        try {
            stop()
            isSpeaking = true
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
            val exe = sherpaExecutable(appHome)
            val modelRoot = File(appHome, voice.modelDir)
            val model = File(modelRoot, voice.modelName)
            val tokens = File(modelRoot, "tokens.txt")
            // Tüm sesler ortak (budanmış) espeak-ng verisini kullanır; eski paket düzeni için ses klasörüne de bakılır
            val espeak = File(appHome, "tts/espeak-ng-data").takeIf { File(it, "phontab").exists() }
                ?: File(modelRoot, "espeak-ng-data")
            val preflightError = preflightError(exe, model, tokens, espeak)
            if (preflightError != null) {
                onStatus(preflightError)
                playbackState = playbackState.copy(isSpeaking = false, status = preflightError)
                return
            }
            onStatus("Dahili ses hazırlanıyor: ${voice.label}")
            playbackState = playbackState.copy(status = "Dahili ses hazırlanıyor: ${voice.label}")
            val segments = text.cleanForSpeechPreservingOffsets().speechSegments(startIndex)
            for (segment in segments) {
                if (!isSpeaking) break
                if (sleepTimerExpired()) {
                    onStatus("Uyku zamanlayıcısı nedeniyle durduruldu.")
                    break
                }
                onRange(segment.start, segment.end)
                val output = File.createTempFile("bideng-tts-", ".wav")
                val ttsText = segment.text.toTtsArgument()
                val args = listOf(
                    exe.absolutePath,
                    "--vits-model=${model.absolutePath}",
                    "--vits-tokens=${tokens.absolutePath}",
                    "--vits-data-dir=${espeak.absolutePath}",
                    "--vits-length-scale=${lengthScaleForSpeed(currentSpeed)}",
                    "--output-filename=${output.absolutePath}",
                    ttsText
                )
                val process = ProcessBuilder(args).apply {
                    directory(exe.parentFile)
                    redirectErrorStream(true)
                    configureNativeLibraryPath(environment(), File(exe.parentFile.parentFile, "lib"))
                }.start()
                currentProcess = process
                val processOutput = process.inputStream.bufferedReader().readText()
                val exitCode = process.waitFor()
                currentProcess = null
                if (output.exists() && output.length() > 44) {
                    onStatus("Okunuyor.")
                    playbackState = playbackState.copy(
                        isSpeaking = true,
                        status = "Okunuyor",
                        currentText = segment.text.take(140),
                        currentRangeStart = segment.start,
                        currentRangeEnd = segment.end
                    )
                    playWav(output, segment, onRange)
                } else {
                    onStatus("TTS başlatılamadı: ${shortError(processOutput, exitCode)}")
                    playbackState = playbackState.copy(status = "TTS başlatılamadı")
                    runCatching { output.delete() }
                    continue
                }
                runCatching { output.delete() }
            }
            isSpeaking = false
            playbackState = playbackState.copy(isSpeaking = false, status = "Durdu", currentText = "", sleepTimerEndsAt = null)
            onRange(-1, -1)
            onComplete()
        } catch (e: Exception) {
            onStatus("TTS hatası: ${e.message ?: e::class.simpleName}")
            isSpeaking = false
            currentProcess = null
            playbackState = playbackState.copy(isSpeaking = false, status = "TTS hatası: ${e.message ?: e::class.simpleName}")
            onRange(-1, -1)
            onComplete()
        }
    }

    fun stop() {
        currentLine?.stop()
        currentLine?.close()
        currentLine = null
        currentProcess?.destroyForcibly()
        currentProcess = null
        isSpeaking = false
        playbackState = playbackState.copy(isSpeaking = false, status = "Durdu", currentText = "", sleepTimerEndsAt = null)
    }

    fun pause() {
        // PowerShell'de pause desteklenmiyor
    }

    fun resume() {
        // PowerShell'de resume desteklenmiyor
    }

    fun setVoice(voiceId: String) {
        currentVoice = EMBEDDED_VOICES.firstOrNull { it.id == voiceId }?.id ?: DEFAULT_EMBEDDED_VOICE.id
    }

    fun setSpeed(speed: Float) {
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

    fun setDefaultSleepTimer(minutes: Int?) {
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

    private val isWindows: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
    private val isMac: Boolean = System.getProperty("os.name").orEmpty().startsWith("Mac", ignoreCase = true)

    private fun sherpaExecutable(appHome: File): File {
        val exe = File(appHome, if (isWindows) "sherpa/bin/sherpa-onnx-offline-tts.exe" else "sherpa/bin/sherpa-onnx-offline-tts")
        if (isWindows || !exe.exists() || exe.canExecute()) return exe
        // Kurulum paketleri (deb/dmg) çalıştırma iznini koruyamıyor ve kurulum klasörü salt okunur:
        // motoru bir kez kullanıcı klasörüne kopyalayıp orada çalıştırılabilir yap.
        if (runCatching { exe.setExecutable(true) }.getOrDefault(false) && exe.canExecute()) return exe
        val sherpaHome = exe.parentFile.parentFile
        val runtime = File(System.getProperty("user.home"), ".bideng/sherpa-runtime")
        val copied = File(runtime, "bin/${exe.name}")
        val upToDate = copied.exists() && copied.length() == exe.length() &&
            sherpaHome.walkTopDown().filter { it.isFile }.all { source ->
                File(runtime, source.relativeTo(sherpaHome).path).let { it.exists() && it.length() == source.length() }
            }
        if (!upToDate) {
            runtime.deleteRecursively()
            sherpaHome.copyRecursively(runtime, overwrite = true)
        }
        copied.setExecutable(true)
        return copied
    }

    private fun configureNativeLibraryPath(environment: MutableMap<String, String>, libDir: File) {
        if (isWindows || !libDir.isDirectory) return
        val key = if (isMac) "DYLD_LIBRARY_PATH" else "LD_LIBRARY_PATH"
        val existing = environment[key].orEmpty()
        environment[key] = if (existing.isBlank()) libDir.absolutePath else "${libDir.absolutePath}${File.pathSeparator}$existing"
    }

    private fun lengthScaleForSpeed(speed: Float): String {
        return "%.3f".format(java.util.Locale.US, (1.0f / speed.coerceIn(0.7f, 1.35f)))
    }

    private fun playWav(
        file: File,
        segment: SpeechSegment,
        onRange: (Int, Int) -> Unit
    ) {
        AudioSystem.getAudioInputStream(file).use { stream ->
            val format = stream.format
            val info = DataLine.Info(SourceDataLine::class.java, format)
            val line = AudioSystem.getLine(info) as SourceDataLine
            currentLine = line
            line.open(format)
            line.start()
            val buffer = ByteArray(8192)
            while (isSpeaking) {
                if (sleepTimerExpired()) {
                    isSpeaking = false
                    break
                }
                val read = stream.read(buffer, 0, buffer.size)
                if (read <= 0) break
                line.write(buffer, 0, read)
            }
            if (isSpeaking) {
                onRange(segment.end, segment.end)
            }
            if (isSpeaking) {
                line.drain()
            } else {
                line.flush()
            }
            line.stop()
            line.close()
            if (currentLine == line) currentLine = null
        }
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

    private fun preflightError(exe: File, model: File, tokens: File, espeak: File): String? {
        if (!exe.exists()) return "TTS motoru bulunamadı: ${exe.path}"
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
