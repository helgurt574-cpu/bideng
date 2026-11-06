package com.example.filiz.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import com.example.filiz.desktop.ui.App
import com.example.filiz.desktop.service.PreferenceService
import com.example.filiz.desktop.service.DatabaseService
import com.example.filiz.desktop.service.TTSService
import java.io.File

fun main() {
    // App data directory oluştur
    val appDir = File(System.getProperty("user.home"), ".bideng")
    if (!appDir.exists()) appDir.mkdirs()

    // Servisleri başlat
    val dbPath = File(appDir, "library.db").absolutePath
    DatabaseService.initialize(dbPath)
    PreferenceService.initialize(appDir)
    TTSService.initialize()

    application {
        val windowState = rememberWindowState(
            width = PreferenceService.getWindowWidth().dp,
            height = PreferenceService.getWindowHeight().dp
        )

        LaunchedEffect(windowState) {
            snapshotFlow { windowState.size }.collect { size ->
                PreferenceService.setWindowSize(size.width.value.toInt(), size.height.value.toInt())
            }
        }

        Window(
            onCloseRequest = ::exitApplication,
            title = "Bideng",
            state = windowState,
            icon = painterResource("icons/ic_launcher.png"),
            undecorated = false,
            resizable = true
        ) {
            App(window)
        }
    }
}
