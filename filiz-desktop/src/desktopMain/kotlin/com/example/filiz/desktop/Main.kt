package com.example.filiz.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.example.filiz.desktop.service.DatabaseService
import com.example.filiz.desktop.service.PreferenceService
import com.example.filiz.desktop.service.TTSService
import com.example.filiz.desktop.ui.App
import com.example.filiz.desktop.ui.AppState
import com.example.filiz.desktop.ui.chooseDocuments
import com.example.filiz.desktop.ui.handleAppKey
import java.awt.Dimension
import java.io.File
import kotlin.system.exitProcess

fun main() {
    // Sesli okuma kütüphaneleri arayüzden (Skia) ÖNCE yüklenmeli; aksi hâlde yerel semboller karışıp çöküyor.
    TTSService.preloadNativeLibraries()

    val appDir = (System.getenv("BIDENG_HOME")?.let(::File) ?: File(System.getProperty("user.home"), ".bideng")).apply { mkdirs() }
    val selfTestDocument = System.getenv("BIDENG_SELFTEST")?.let(::File)
    DatabaseService.initialize(File(appDir, "library.db").absolutePath)
    PreferenceService.initialize(appDir)
    TTSService.initialize()
    TTSService.setDefaultSleepTimer(PreferenceService.getSleepMinutes().takeIf { it > 0 })

    application {
        val windowState = rememberWindowState(
            width = PreferenceService.getWindowWidth().dp,
            height = PreferenceService.getWindowHeight().dp
        )
        val scope = rememberCoroutineScope()
        val state = remember { AppState(scope, appDir, TTSService) }
        val import = { state.importFiles(chooseDocuments(state.strings.chooseFiles)) }

        LaunchedEffect(windowState) {
            snapshotFlow { windowState.size }.collect { size ->
                if (windowState.placement == WindowPlacement.Floating) {
                    PreferenceService.setWindowSize(size.width.value.toInt(), size.height.value.toInt())
                }
            }
        }
        LaunchedEffect(state.fullScreen) {
            windowState.placement = if (state.fullScreen) WindowPlacement.Fullscreen else WindowPlacement.Floating
        }

        Window(
            onCloseRequest = {
                state.reader?.release()
                TTSService.stop()
                exitApplication()
            },
            title = "Bideng",
            state = windowState,
            icon = painterResource("icons/ic_launcher.png"),
            onPreviewKeyEvent = { handleAppKey(state, it, import) }
        ) {
            LaunchedEffect(Unit) { window.minimumSize = Dimension(760, 560) }
            if (selfTestDocument != null) {
                LaunchedEffect(Unit) {
                    val ok = runSelfTest(state, selfTestDocument)
                    TTSService.stop()
                    exitProcess(if (ok) 0 else 1)
                }
            }
            App(state, window, import)
        }
    }
}
