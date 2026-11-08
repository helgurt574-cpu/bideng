package com.example.filiz

import android.content.Context

internal class ReaderPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadSelectedLibraryId(): String? {
        return prefs.getString(SELECTED_LIBRARY_ITEM_KEY, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun saveSelectedLibraryId(id: String) {
        prefs.edit()
            .putString(SELECTED_LIBRARY_ITEM_KEY, id)
            .apply()
    }

    fun loadReaderFontSize(): Float {
        return prefs.getFloat(FONT_SIZE_KEY, 22f)
            .coerceIn(16f, 32f)
    }

    fun loadReaderFontFamily(): String {
        val value = prefs.getString(FONT_FAMILY_KEY, DEFAULT_READER_FONT_ID).orEmpty()
        return readerFontOption(value).id
    }

    fun loadReaderLineSpacing(fontFamily: String): Float {
        return prefs.getFloat(LINE_SPACING_KEY, readerFontOption(fontFamily).lineSpacingMultiplier)
            .coerceIn(MIN_READER_LINE_SPACING, MAX_READER_LINE_SPACING)
    }

    fun saveReaderFontSettings(fontSize: Float, fontFamily: String, lineSpacing: Float) {
        prefs.edit()
            .putFloat(FONT_SIZE_KEY, fontSize)
            .putString(FONT_FAMILY_KEY, fontFamily)
            .putFloat(LINE_SPACING_KEY, lineSpacing)
            .apply()
    }

    fun loadSelectedVoiceId(): String {
        val value = prefs.getString(VOICE_ID_KEY, DEFAULT_TTS_VOICE.id).orEmpty()
        return TTS_VOICES.firstOrNull { it.id == value }?.id ?: DEFAULT_TTS_VOICE.id
    }

    fun saveSelectedVoiceId(id: String) {
        prefs.edit()
            .putString(VOICE_ID_KEY, id)
            .apply()
    }

    fun loadUiLanguage(): UiLanguage {
        return when (prefs.getString(UI_LANGUAGE_KEY, UiLanguage.Turkish.name)) {
            UiLanguage.Kurdish.name -> UiLanguage.Kurdish
            UiLanguage.Arabic.name -> UiLanguage.Arabic
            else -> UiLanguage.Turkish
        }
    }

    fun saveUiLanguage(value: UiLanguage) {
        prefs.edit()
            .putString(UI_LANGUAGE_KEY, value.name)
            .apply()
    }

    fun loadLockedOrientation(): Int? {
        return prefs.getInt(LOCKED_ORIENTATION_KEY, NO_LOCKED_ORIENTATION)
            .takeIf { it != NO_LOCKED_ORIENTATION }
    }

    fun saveLockedOrientation(orientation: Int?) {
        prefs.edit()
            .putInt(LOCKED_ORIENTATION_KEY, orientation ?: NO_LOCKED_ORIENTATION)
            .apply()
    }

    fun loadThemeMode(defaultValue: ThemeMode): ThemeMode {
        val saved = prefs.getString(THEME_MODE_KEY, null)
        return ThemeMode.entries.firstOrNull { it.name == saved } ?: defaultValue
    }

    fun saveThemeMode(mode: ThemeMode) {
        prefs.edit()
            .putString(THEME_MODE_KEY, mode.name)
            .apply()
    }

    // Duraklatılan sesli okumanın belge ve konumu; uygulama kapansa da devam edilebilsin.
    fun loadPausedPosition(documentId: String): Int {
        if (prefs.getString(PAUSED_DOCUMENT_KEY, null) != documentId) return -1
        return prefs.getInt(PAUSED_INDEX_KEY, -1)
    }

    fun savePausedPosition(documentId: String?, index: Int) {
        prefs.edit().apply {
            if (documentId == null || index < 0) {
                remove(PAUSED_DOCUMENT_KEY)
                remove(PAUSED_INDEX_KEY)
            } else {
                putString(PAUSED_DOCUMENT_KEY, documentId)
                putInt(PAUSED_INDEX_KEY, index)
            }
        }.apply()
    }

    fun loadSleepTimerMinutes(): Int {
        return prefs.getInt(SLEEP_TIMER_MINUTES_KEY, DEFAULT_SLEEP_TIMER_MINUTES)
            .takeIf { it in SLEEP_TIMER_OPTIONS }
            ?: DEFAULT_SLEEP_TIMER_MINUTES
    }

    fun saveSleepTimerMinutes(minutes: Int) {
        prefs.edit()
            .putInt(SLEEP_TIMER_MINUTES_KEY, minutes.takeIf { it in SLEEP_TIMER_OPTIONS } ?: DEFAULT_SLEEP_TIMER_MINUTES)
            .apply()
    }

    fun hasSeenFilePickerGuide(): Boolean {
        return prefs.getBoolean(FILE_PICKER_GUIDE_SEEN_KEY, false)
    }

    fun saveSeenFilePickerGuide() {
        prefs.edit()
            .putBoolean(FILE_PICKER_GUIDE_SEEN_KEY, true)
            .apply()
    }

    private companion object {
        const val PREFS = "library"
        const val SELECTED_LIBRARY_ITEM_KEY = "selectedItemId"
        const val FONT_SIZE_KEY = "fontSize"
        const val FONT_FAMILY_KEY = "fontFamily"
        const val LINE_SPACING_KEY = "lineSpacing"
        const val VOICE_ID_KEY = "voiceId"
        const val UI_LANGUAGE_KEY = "uiLanguage"
        const val LOCKED_ORIENTATION_KEY = "lockedOrientation"
        const val NO_LOCKED_ORIENTATION = Int.MIN_VALUE
        const val THEME_MODE_KEY = "themeMode"
        const val PAUSED_DOCUMENT_KEY = "pausedDocumentId"
        const val PAUSED_INDEX_KEY = "pausedIndex"
        const val SLEEP_TIMER_MINUTES_KEY = "sleepTimerMinutes"
        const val FILE_PICKER_GUIDE_SEEN_KEY = "filePickerGuideSeen"
    }
}
