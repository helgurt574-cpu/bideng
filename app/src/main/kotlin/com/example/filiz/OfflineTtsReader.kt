package com.example.filiz

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.getOfflineTtsConfig
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private const val TTS_SEGMENT_MAX_CHARS = 340
private const val TTS_SEGMENT_MAX_SENTENCES = 2
private const val TTS_FIRST_SEGMENT_MAX_CHARS = 180
private const val TTS_FIRST_SEGMENT_MAX_SENTENCES = 1
private const val AUDIO_WRITE_CHUNK_SAMPLES = 4096

class OfflineTtsReader(
    private val context: Context,
    private val onStatus: (String) -> Unit,
    private val onRange: (Int, Int) -> Unit
) {
    private data class SpeechSegment(
        val text: String,
        val start: Int,
        val end: Int,
        val sentenceCount: Int = 1
    )

    private data class QueuedAudio(
        val samples: FloatArray,
        val rangeStart: Int,
        val rangeEnd: Int,
        val endOfSpeech: Boolean = false
    )

    private class AudioSamplesCallback(
        private val id: Int,
        private val generation: AtomicInteger,
        private val isReleased: () -> Boolean,
        private val queue: ArrayBlockingQueue<QueuedAudio>,
        private val rangeStart: Int,
        private val rangeEnd: Int
    ) : (FloatArray) -> Int {
        private var rangeQueued = false

        override fun invoke(samples: FloatArray): Int {
            if (id != generation.get() || isReleased()) return 1
            if (samples.isEmpty()) return 1
            val item = QueuedAudio(
                samples = samples.copyOf(),
                rangeStart = if (rangeQueued) -1 else rangeStart,
                rangeEnd = if (rangeQueued) -1 else rangeEnd
            )
            rangeQueued = true
            while (!isReleased()) {
                if (id != generation.get()) return 1
                if (queue.offer(item, 100, TimeUnit.MILLISECONDS)) return 1
            }
            return 1
        }
    }

    private val generation = AtomicInteger(0)
    private val prepareLock = Object()
    private val audioLock = Object()
    @Volatile private var prepared = false
    @Volatile private var released = false
    @Volatile private var speed = DEFAULT_TTS_SPEED
    @Volatile private var voice = DEFAULT_TTS_VOICE
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "FilizOfflineTts")
    }
    private val playbackExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "FilizTtsPlayback")
    }
    private var tts: OfflineTts? = null
    private var track: AudioTrack? = null

    fun setSpeed(value: Float) {
        speed = value.coerceIn(MIN_TTS_SPEED, MAX_TTS_SPEED)
    }

    fun setVoice(value: TtsVoice) {
        if (voice.id == value.id && prepared) return
        synchronized(prepareLock) {
            if (voice.id == value.id && prepared) return
            generation.incrementAndGet()
            resetAudio(resume = false)
            runCatching { track?.release() }
            runCatching { tts?.release() }
            track = null
            tts = null
            prepared = false
            voice = value
            onRange(-1, -1)
            onStatus("Ses hazırlanıyor: ${value.label}")
        }
    }

    fun prepareAsync() {
        executor.execute { prepareBlocking() }
    }

    fun speakFrom(text: String, startIndex: Int) {
        val id = generation.incrementAndGet()
        resetAudio(resume = true)

        executor.execute {
            runCatching {
                prepareBlocking()
                if (released || id != generation.get()) return@runCatching

                val engine = tts ?: return@runCatching
                onStatus("Ses hazırlanıyor...")
                val localTrack = track ?: return@runCatching
                runCatching { localTrack.play() }
                val audioQueue = ArrayBlockingQueue<QueuedAudio>(AUDIO_QUEUE_CAPACITY)
                val endOfSpeech = QueuedAudio(FloatArray(0), -1, -1, endOfSpeech = true)

                playbackExecutor.execute {
                    runCatching {
                        while (id == generation.get() && !released) {
                            val item = audioQueue.poll(100, TimeUnit.MILLISECONDS) ?: continue
                            if (item.endOfSpeech) break
                            if (item.rangeStart >= 0 && item.rangeEnd > item.rangeStart) {
                                onRange(item.rangeStart, item.rangeEnd)
                                onStatus("Okunuyor.")
                            }
                            writeSamples(localTrack, item.samples, id)
                        }
                        if (id == generation.get() && !released) {
                            onStatus("Okuma tamamlandı.")
                        }
                    }.onFailure { error ->
                        Log.e("Filiz", "Speech playback failed", error)
                    }
                }

                val speechText = text.cleanForSpeechPreservingOffsets()
                val segments = segmentText(speechText, startIndex)
                for (segment in segments) {
                    if (id != generation.get()) break

                    val callback = AudioSamplesCallback(
                        id,
                        generation,
                        { released },
                        audioQueue,
                        segment.start,
                        segment.end
                    )
                    engine.generateWithConfigAndCallback(
                        text = segment.text,
                        config = GenerationConfig(silenceScale = 0.0f, speed = speed),
                        callback = callback
                    )
                }
                if (id == generation.get() && !released) {
                    while (!audioQueue.offer(endOfSpeech, 100, TimeUnit.MILLISECONDS) &&
                        id == generation.get() &&
                        !released
                    ) {
                    }
                }
            }.onFailure { error ->
                Log.e("Filiz", "Speech generation failed", error)
                onStatus("Okuma başlatılamadı.")
            }
        }
    }

    fun stop() {
        generation.incrementAndGet()
        resetAudio(resume = false)
        onRange(-1, -1)
        onStatus("Durduruldu.")
    }

    fun release() {
        released = true
        generation.incrementAndGet()
        resetAudio(resume = false)
        onRange(-1, -1)
        executor.execute {
            synchronized(prepareLock) {
                synchronized(audioLock) {
                    runCatching {
                        track?.pause()
                        track?.flush()
                        track?.release()
                    }
                    track = null
                }
                runCatching { tts?.release() }
                tts = null
            }
            executor.shutdown()
            playbackExecutor.shutdown()
        }
    }

    private fun prepareBlocking() {
        if (prepared || released) return
        synchronized(prepareLock) {
            if (prepared || released) return
            runCatching {
                onStatus("Offline model hazırlanıyor...")

                val selectedVoice = voice
                val dataDir = copyEspeakDataIfNeeded(ESPEAK_DATA_ASSET)
                // Gömülü sesler asset'ten, indirilen sesler dosya sisteminden yüklenir
                val bundled = VoiceStore.isBundled(context, selectedVoice)
                val modelDir = if (bundled) {
                    selectedVoice.modelDir
                } else {
                    if (!VoiceStore.isInstalled(context, selectedVoice)) {
                        throw IllegalStateException("Ses indirilmemiş: ${selectedVoice.id}")
                    }
                    VoiceStore.voiceDir(context, selectedVoice).absolutePath
                }
                val config = getOfflineTtsConfig(
                    modelDir = modelDir,
                    modelName = selectedVoice.modelName,
                    acousticModelName = "",
                    vocoder = "",
                    voices = "",
                    lexicon = "",
                    dataDir = dataDir.absolutePath,
                    dictDir = "",
                    ruleFsts = "",
                    ruleFars = "",
                    numThreads = 4
                ).apply {
                    maxNumSentences = 1
                    silenceScale = 0.03f
                }

                val engine = OfflineTts(
                    assetManager = if (bundled) context.assets else null,
                    config = config
                )
                tts = engine
                track = createTrack(engine.sampleRate()).also { it.play() }
                prepared = true
                onStatus("Hazır. Dahili offline ses: ${selectedVoice.label}")
            }.onFailure { error ->
                Log.e("Filiz", "TTS prepare failed", error)
                onStatus("Model hazırlanamadı.")
            }
        }
    }

    private fun createTrack(sampleRate: Int): AudioTrack {
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        ).coerceAtLeast(sampleRate / 2)

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        val streamBuffer = (minBuffer * 4).coerceAtLeast(sampleRate * 2)

        return AudioTrack(
            attributes,
            format,
            streamBuffer,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
    }

    private fun resetAudio(resume: Boolean) {
        synchronized(audioLock) {
            runCatching {
                track?.pause()
                track?.flush()
                if (resume && !released) track?.play()
            }
        }
    }

    private fun writeSamples(localTrack: AudioTrack, samples: FloatArray, id: Int): Boolean {
        var offset = 0
        while (offset < samples.size && id == generation.get() && !released) {
            val count = minOf(AUDIO_WRITE_CHUNK_SAMPLES, samples.size - offset)
            val written = synchronized(audioLock) {
                if (id != generation.get() || released) return false
                localTrack.write(
                    samples,
                    offset,
                    count,
                    AudioTrack.WRITE_BLOCKING
                )
            }
            if (written <= 0) return false
            offset += written
        }
        return id == generation.get() && !released
    }

    private fun copyEspeakDataIfNeeded(espeakDir: String): File {
        val target = File(context.filesDir, espeakDir)
        val marker = File(target, ".ready")
        if (marker.exists() && runCatching { marker.readText() }.getOrNull() == ESPEAK_DATA_VERSION) return target

        onStatus("Dil verisi hazırlanıyor...")
        copyAssetTree(espeakDir, target)
        marker.writeText(ESPEAK_DATA_VERSION)
        removeLegacyEspeakCopies()
        return target
    }

    // Eski sürümler her ses için ayrı (19 MB) espeak kopyası çıkarıyordu; artık gerek yok.
    private fun removeLegacyEspeakCopies() {
        listOf("tr_TR_fahrettin_medium", "tr_TR_dfki_medium", "vits-piper-ar_JO-kareem-low").forEach { dir ->
            runCatching { File(context.filesDir, "tts/$dir/espeak-ng-data").deleteRecursively() }
        }
    }

    private fun copyAssetTree(assetPath: String, target: File) {
        val children = context.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            target.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
            return
        }

        target.mkdirs()
        for (child in children) {
            copyAssetTree("$assetPath/$child", File(target, child))
        }
    }

    private fun segmentText(text: String, startIndex: Int): Sequence<SpeechSegment> = sequence {
        var index = startIndex.coerceIn(0, text.length)
        var chunkText = StringBuilder()
        var chunkStart = -1
        var chunkEnd = -1
        var chunkSentenceCount = 0
        var emittedAnyChunk = false

        fun emitChunk(): SpeechSegment? {
            val spoken = chunkText.toString().replace(Regex("\\s+"), " ").trim()
            if (spoken.isBlank() || chunkStart < 0 || chunkEnd <= chunkStart) {
                chunkText = StringBuilder()
                chunkStart = -1
                chunkEnd = -1
                chunkSentenceCount = 0
                return null
            }
            val segment = SpeechSegment(spoken, chunkStart, chunkEnd, chunkSentenceCount.coerceAtLeast(1))
            emittedAnyChunk = true
            chunkText = StringBuilder()
            chunkStart = -1
            chunkEnd = -1
            chunkSentenceCount = 0
            return segment
        }

        while (index < text.length) {
            while (index < text.length && text[index].isWhitespace()) index++
            if (index >= text.length) break

            val sentenceStart = index
            val sentenceEnd = findSentenceEnd(text, sentenceStart)
            val spokenEnd = extendTrailingPunctuation(text, sentenceEnd)
            val segment = text.substring(sentenceStart, spokenEnd).replace(Regex("\\s+"), " ").trim()
            if (segment.isNotEmpty() && !isDisposableTtsSegment(segment)) {
                val maxChars = if (emittedAnyChunk) TTS_SEGMENT_MAX_CHARS else TTS_FIRST_SEGMENT_MAX_CHARS
                val maxSentences = if (emittedAnyChunk) TTS_SEGMENT_MAX_SENTENCES else TTS_FIRST_SEGMENT_MAX_SENTENCES
                val wouldOverflow = chunkText.isNotEmpty() &&
                    (chunkText.length + 1 + segment.length > maxChars ||
                        chunkSentenceCount >= maxSentences)
                if (wouldOverflow) {
                    emitChunk()?.let { yield(it) }
                }
                if (chunkText.isEmpty()) {
                    chunkStart = sentenceStart
                } else {
                    chunkText.append(' ')
                }
                chunkText.append(segment)
                chunkEnd = sentenceEnd
                chunkSentenceCount++
            }
            index = spokenEnd
        }
        emitChunk()?.let { yield(it) }
    }

    private fun String.cleanForSpeechPreservingOffsets(): String {
        if (isBlank()) return this
        return maskImportPathArtifactsPreservingOffsets()
            .maskRepeatedHeaderFooterLinesPreservingOffsets()
    }

    private fun String.maskImportPathArtifactsPreservingOffsets(): String {
        return ImportPathCleaner.maskPreservingOffsets(this)
    }

    private fun String.maskRepeatedHeaderFooterLinesPreservingOffsets(): String {
        val repeatedLines = repeatedEdgeLines()
        if (repeatedLines.isEmpty() && !containsLikelyDisposableLine()) return this
        val builder = StringBuilder(this)
        var lineStart = 0
        while (lineStart < length) {
            var lineEnd = lineStart
            while (lineEnd < length && this[lineEnd] != '\n' && this[lineEnd] != '\r') lineEnd++
            val line = substring(lineStart, lineEnd)
            val normalized = normalizeHeaderFooterLine(line)
            if (isStandaloneDisposableLine(line) || (normalized in repeatedLines && isLikelyHeaderFooterLine(line))) {
                for (index in lineStart until lineEnd) {
                    if (!builder[index].isWhitespace()) builder.setCharAt(index, ' ')
                }
            }
            while (lineEnd < length && (this[lineEnd] == '\n' || this[lineEnd] == '\r')) lineEnd++
            lineStart = lineEnd
        }
        return builder.toString()
    }

    private fun String.repeatedEdgeLines(): Set<String> {
        val pages = split(Regex("""\n\s*\n+"""))
            .map { it.lines().map(String::trim).filter(String::isNotBlank) }
            .filter { it.isNotEmpty() }
        if (pages.size < 3) return emptySet()
        val candidates = pages.flatMap { lines -> lines.take(3) + lines.takeLast(3) }
            .map(::normalizeHeaderFooterLine)
            .filter { it.length in 2..140 }
        val threshold = maxOf(3, (pages.size * 0.35f).toInt())
        return candidates
            .groupingBy { it }
            .eachCount()
            .filterValues { it >= threshold }
            .keys
    }

    private fun String.containsLikelyDisposableLine(): Boolean {
        return lineSequence().any(::isStandaloneDisposableLine)
    }

    private fun normalizeHeaderFooterLine(line: String): String {
        return line.trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("""\d+"""), "#")
            .replace(Regex("""\s+"""), " ")
    }

    private fun isLikelyHeaderFooterLine(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isBlank()) return false
        if (isStandaloneDisposableLine(trimmed)) return true
        return trimmed.length <= 120
    }

    private fun isStandaloneDisposableLine(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isBlank()) return false
        val normalized = trimmed.lowercase(Locale.ROOT)
        if (trimmed.matches(Regex("""^\d{1,4}$"""))) return true
        if (trimmed.matches(Regex("""^\d{1,4}\s*/\s*\d{1,4}$"""))) return true
        if (normalized.matches(Regex("""^(sayfa|page|rûpel|ripel|الصفحة)\s+\d{1,4}([/\s-]+\d{1,4})?$"""))) return true
        if (normalized.matches(Regex("""^/?input/import-\d+/.*\.(pdf|docx?|epub|odt|rtf|txt|html?)$"""))) return true
        if (normalized.matches(Regex(""".*/input/import-\d+/.*\.(pdf|docx?|epub|odt|rtf|txt|html?)$"""))) return true
        if (normalized.matches(Regex("""^file:\s*/{0,3}.*\.(pdf|docx?|epub|odt|rtf|txt|html?)$"""))) return true
        if (normalized.startsWith("http://") || normalized.startsWith("https://") || normalized.startsWith("www.")) return true
        if (trimmed.contains('@') && trimmed.matches(Regex("""\S+@\S+\.\S+"""))) return true
        return false
    }

    private fun isDisposableTtsSegment(segment: String): Boolean {
        val normalized = segment.trim().lowercase()
        if (normalized.isBlank()) return true
        if (segment.matches(Regex("""^\d{1,4}$"""))) return true
        if (segment.matches(Regex("""^\d{1,4}\s*/\s*\d{1,4}$"""))) return true
        if (normalized.matches(Regex("""^(sayfa|page|rûpel|ripel|الصفحة)\s+\d{1,4}([/\s-]+\d{1,4})?$"""))) return true
        if (normalized.startsWith("http://") || normalized.startsWith("https://") || normalized.startsWith("www.")) return true
        if (normalized.matches(Regex("""^/?input/import-\d+/.*\.(pdf|docx?|epub|odt|rtf|txt|html?)$"""))) return true
        return false
    }

    private fun findSentenceEnd(text: String, start: Int): Int {
        val maxEnd = (start + 260).coerceAtMost(text.length)
        var index = start
        while (index < maxEnd) {
            val char = text[index]
            val next = text.getOrNull(index + 1)
            if (char == '\n' && next == '\n') return index
            if (isSentenceEnding(char) && (next == null || next.isWhitespace())) {
                return index + 1
            }
            index++
        }

        var fallback = maxEnd
        while (fallback > start && !text[fallback - 1].isWhitespace()) fallback--
        return if (fallback > start) fallback else maxEnd
    }

    private fun extendTrailingPunctuation(text: String, start: Int): Int {
        var end = start
        while (end < text.length && isTrailingPunctuation(text[end])) {
            end++
        }
        return end
    }

    private fun isTrailingPunctuation(char: Char): Boolean {
        return char == '.' || char == ',' || char == ';' || char == ':' ||
            char == '!' || char == '?' || char == ')' || char == ']' || char == '}'
    }

    private fun isSentenceEnding(char: Char): Boolean {
        return char == '.' || char == '!' || char == '?'
    }
}
