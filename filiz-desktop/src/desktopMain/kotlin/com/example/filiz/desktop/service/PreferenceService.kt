package com.example.filiz.desktop.service

import java.io.File
import java.util.Properties

/**
 * Uygulama ayarları (~/.bideng/settings.properties).
 * Eski sürüm JSON'a yazmaya çalışıyordu ama serileştirme eklentisi olmadığı için hiçbir ayar kaydedilmiyordu.
 */
object PreferenceService {
    private var file: File? = null
    private val props = Properties()

    fun initialize(dir: File) {
        dir.mkdirs()
        file = File(dir, "settings.properties")
        props.clear()
        file?.takeIf { it.exists() }?.let { settings ->
            runCatching { settings.reader(Charsets.UTF_8).use { props.load(it) } }
        }
    }

    private fun save() {
        val target = file ?: return
        runCatching { target.writer(Charsets.UTF_8).use { props.store(it, "Bideng") } }
    }

    private fun string(key: String, default: String): String = props.getProperty(key) ?: default
    private fun int(key: String, default: Int): Int = props.getProperty(key)?.toIntOrNull() ?: default
    private fun float(key: String, default: Float): Float = props.getProperty(key)?.toFloatOrNull() ?: default
    private fun put(key: String, value: Any?) {
        if (value == null) props.remove(key) else props.setProperty(key, value.toString())
        save()
    }

    // Görünüm
    fun getThemeMode(): String = string("themeMode", "dark")
    fun setThemeMode(value: String) = put("themeMode", value)
    fun getFontFamily(): String = string("fontFamily", "sans-serif")
    fun setFontFamily(value: String) = put("fontFamily", value)
    fun getFontSize(): Int = int("fontSize", 21).coerceIn(14, 36)
    fun setFontSize(value: Int) = put("fontSize", value.coerceIn(14, 36))
    fun getLineSpacing(): Float = float("lineSpacing", 1.55f).coerceIn(1.2f, 2.2f)
    fun setLineSpacing(value: Float) = put("lineSpacing", value.coerceIn(1.2f, 2.2f))
    fun getUILanguage(): String = string("uiLanguage", "tr")
    fun setUILanguage(value: String) = put("uiLanguage", value)

    // Sesli okuma
    fun getTTSVoice(): String = string("ttsVoice", "")
    fun setTTSVoice(value: String) = put("ttsVoice", value)
    fun getTTSSpeed(): Float = float("ttsSpeed", 1.0f).coerceIn(0.5f, 2.0f)
    fun setTTSSpeed(value: Float) = put("ttsSpeed", value.coerceIn(0.5f, 2.0f))
    fun getSleepMinutes(): Int = int("sleepMinutes", 30)
    fun setSleepMinutes(value: Int) = put("sleepMinutes", value)

    // Duraklatılan sesli okumanın yeri; uygulama kapansa da devam edilebilsin
    fun getPausedIndex(documentId: String): Int =
        if (string("pausedDocumentId", "") == documentId) int("pausedIndex", -1) else -1
    fun setPausedPosition(documentId: String?, index: Int) {
        if (documentId == null || index < 0) {
            props.remove("pausedDocumentId")
            props.remove("pausedIndex")
            save()
        } else {
            props.setProperty("pausedDocumentId", documentId)
            props.setProperty("pausedIndex", index.toString())
            save()
        }
    }

    // Pencere
    fun getWindowWidth(): Int = int("windowWidth", 1180)
    fun getWindowHeight(): Int = int("windowHeight", 820)
    fun setWindowSize(width: Int, height: Int) {
        props.setProperty("windowWidth", width.toString())
        props.setProperty("windowHeight", height.toString())
        save()
    }
}
