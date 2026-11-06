package com.example.filiz.desktop.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object DesktopCommands {
    var importRequest by mutableStateOf(0)
        private set
    var preferencesRequest by mutableStateOf(0)
        private set
    var focusLibraryRequest by mutableStateOf(0)
        private set
    var focusReaderRequest by mutableStateOf(0)
        private set
    var toggleFocusRequest by mutableStateOf(0)
        private set

    fun requestImport() {
        importRequest++
    }

    fun requestPreferences() {
        preferencesRequest++
    }

    fun requestLibrary() {
        focusLibraryRequest++
    }

    fun requestReader() {
        focusReaderRequest++
    }

    fun requestToggleFocus() {
        toggleFocusRequest++
    }
}
