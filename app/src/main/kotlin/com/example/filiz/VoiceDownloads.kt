package com.example.filiz

import android.content.Context
import android.net.ConnectivityManager
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

// İsteğe bağlı seslerin cihazdaki yeri: filesDir/voices/<id>/{model.onnx, tokens.txt}
internal object VoiceStore {
    private var activeDownload: VoiceDownloader? = null
    private val bundledCache = mutableMapOf<String, Boolean>()

    // Model APK'nın asset'lerinde mi? (Çevrimdışı sürümde tüm sesler gömülüdür.)
    fun isBundled(context: Context, voice: TtsVoice): Boolean {
        if (voice.downloadUrl == null) return true
        return synchronized(bundledCache) {
            bundledCache.getOrPut(voice.id) {
                runCatching { context.assets.list(voice.modelDir).orEmpty().contains(voice.modelName) }.getOrDefault(false)
            }
        }
    }

    fun voiceDir(context: Context, voice: TtsVoice): File = File(context.filesDir, "voices/${voice.id}")

    fun isInstalled(context: Context, voice: TtsVoice): Boolean {
        if (isBundled(context, voice)) return true
        val dir = voiceDir(context, voice)
        val model = File(dir, voice.modelName)
        return model.isFile && model.length() == voice.downloadSize && File(dir, "tokens.txt").isFile
    }

    fun delete(context: Context, voice: TtsVoice) {
        if (isBundled(context, voice)) return
        voiceDir(context, voice).deleteRecursively()
    }

    // Çevrimdışı sürüme geçildiyse daha önce indirilen kopyalar gereksiz yer kaplar
    fun removeDownloadsOfBundledVoices(context: Context) {
        TTS_VOICES.filter { it.downloadUrl != null && isBundled(context, it) }
            .forEach { runCatching { voiceDir(context, it).deleteRecursively() } }
    }

    fun downloadingVoiceId(): String? = activeDownload?.voice?.id

    fun startDownload(
        context: Context,
        voice: TtsVoice,
        onProgress: (downloaded: Long, total: Long) -> Unit,
        onFinished: (Result<Unit>) -> Unit
    ): VoiceDownloader {
        activeDownload?.takeIf { it.voice.id == voice.id }?.let { running ->
            running.onProgress = onProgress
            running.onFinished = onFinished
            return running
        }
        activeDownload?.cancel()
        val downloader = VoiceDownloader(context.applicationContext, voice, onProgress, onFinished)
        activeDownload = downloader
        downloader.start()
        return downloader
    }

    internal fun finished(downloader: VoiceDownloader) {
        if (activeDownload === downloader) activeDownload = null
    }

    fun isMeteredConnection(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        return manager.isActiveNetworkMetered
    }
}

internal class VoiceDownloadCancelled : IOException("İndirme iptal edildi")

internal class VoiceDownloader(
    private val context: Context,
    val voice: TtsVoice,
    @Volatile var onProgress: (downloaded: Long, total: Long) -> Unit,
    @Volatile var onFinished: (Result<Unit>) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var cancelled = false

    fun start() {
        Thread({ run() }, "voice-download-${voice.id}").apply {
            priority = Thread.NORM_PRIORITY - 1
            start()
        }
    }

    // Yarım dosya saklanır; bir sonraki denemede kaldığı yerden devam edilir.
    fun cancel() {
        cancelled = true
    }

    private fun run() {
        val result = runCatching { download() }
        VoiceStore.finished(this)
        mainHandler.post { onFinished(result) }
    }

    private fun download() {
        val url = voice.downloadUrl ?: return
        val dir = VoiceStore.voiceDir(context, voice).apply { mkdirs() }
        val target = File(dir, voice.modelName)
        val part = File(dir, "${voice.modelName}.part")
        var existing = if (part.isFile) part.length() else 0L
        if (existing > voice.downloadSize) {
            part.delete()
            existing = 0L
        }

        if (existing < voice.downloadSize) {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Bideng-Android")
                if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
            }
            try {
                val code = connection.responseCode
                val append = when (code) {
                    HttpURLConnection.HTTP_PARTIAL -> true
                    HttpURLConnection.HTTP_OK -> false
                    else -> throw IOException("Sunucu yanıtı: $code")
                }
                if (!append) existing = 0L
                connection.inputStream.use { input ->
                    FileOutputStream(part, append).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var downloaded = existing
                        var lastReport = 0L
                        while (true) {
                            if (cancelled) throw VoiceDownloadCancelled()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            val now = System.currentTimeMillis()
                            if (now - lastReport > 200) {
                                lastReport = now
                                val snapshot = downloaded
                                mainHandler.post { onProgress(snapshot, voice.downloadSize) }
                            }
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }
        }

        if (part.length() != voice.downloadSize) {
            throw IOException("İndirme eksik kaldı (${part.length()} / ${voice.downloadSize} bayt)")
        }
        val expected = voice.sha256
        if (expected != null && sha256(part) != expected) {
            part.delete()
            throw IOException("Dosya doğrulanamadı, lütfen tekrar deneyin")
        }
        context.assets.open("${voice.modelDir}/tokens.txt").use { input ->
            FileOutputStream(File(dir, "tokens.txt")).use { output -> input.copyTo(output) }
        }
        if (target.exists()) target.delete()
        if (!part.renameTo(target)) throw IOException("Dosya kaydedilemedi")
        mainHandler.post { onProgress(voice.downloadSize, voice.downloadSize) }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                if (cancelled) throw VoiceDownloadCancelled()
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
