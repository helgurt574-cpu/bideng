package com.example.filiz.desktop.service

import com.example.filiz.desktop.model.AppSettings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

object PreferenceService {
    private val json = Json { ignoreUnknownKeys = true }
    private var appDir: File? = null
    private var settingsFile: File? = null
    private var currentSettings: AppSettings = AppSettings()

    fun initialize(dir: File) {
        appDir = dir
        settingsFile = File(dir, "settings.json")
        loadSettings()
    }

    private fun loadSettings() {
        if (settingsFile?.exists() == true) {
            try {
                currentSettings = json.decodeFromString(settingsFile?.readText() ?: "")
            } catch (e: Exception) {
                currentSettings = AppSettings()
            }
        }
    }

    private fun saveSettings() {
        try {
            settingsFile?.writeText(json.encodeToString(currentSettings))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getDarkMode(): Boolean = currentSettings.darkMode
    fun setDarkMode(value: Boolean) {
        currentSettings = currentSettings.copy(darkMode = value)
        saveSettings()
    }

    fun getFontFamily(): String = currentSettings.fontFamily
    fun setFontFamily(value: String) {
        currentSettings = currentSettings.copy(fontFamily = value)
        saveSettings()
    }

    fun getFontSize(): Int = currentSettings.fontSize
    fun setFontSize(value: Int) {
        currentSettings = currentSettings.copy(fontSize = value)
        saveSettings()
    }

    fun getLineSpacing(): Float = currentSettings.lineSpacing
    fun setLineSpacing(value: Float) {
        currentSettings = currentSettings.copy(lineSpacing = value)
        saveSettings()
    }

    fun getTTSVoice(): String = currentSettings.ttsVoice
    fun setTTSVoice(value: String) {
        currentSettings = currentSettings.copy(ttsVoice = value)
        saveSettings()
    }

    fun getTTSSpeed(): Float = currentSettings.ttsSpeed
    fun setTTSSpeed(value: Float) {
        currentSettings = currentSettings.copy(ttsSpeed = value)
        saveSettings()
    }

    fun getUILanguage(): String = currentSettings.uiLanguage
    fun setUILanguage(value: String) {
        currentSettings = currentSettings.copy(uiLanguage = value)
        saveSettings()
    }

    fun getAllSettings(): AppSettings = currentSettings

    fun getWindowWidth(): Int = currentSettings.windowWidth
    fun getWindowHeight(): Int = currentSettings.windowHeight
    fun setWindowSize(width: Int, height: Int) {
        currentSettings = currentSettings.copy(windowWidth = width, windowHeight = height)
        saveSettings()
    }

    fun resetToDefaults() {
        currentSettings = AppSettings()
        saveSettings()
    }
}
