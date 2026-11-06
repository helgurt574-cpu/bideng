package com.example.filiz.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.filiz.desktop.service.DocumentReaderService
import com.example.filiz.desktop.ui.components.BidengDialog
import com.example.filiz.desktop.ui.components.ConfirmDialog
import com.example.filiz.desktop.ui.components.NotesDialog
import com.example.filiz.desktop.ui.components.PrimaryButton
import com.example.filiz.desktop.ui.components.QuietButton
import com.example.filiz.desktop.ui.screens.LibraryScreen
import com.example.filiz.desktop.ui.screens.ReaderScreen
import com.example.filiz.desktop.ui.screens.fieldColors
import com.example.filiz.desktop.ui.theme.Bideng
import com.example.filiz.desktop.ui.theme.BidengTheme
import java.awt.FileDialog
import java.awt.Frame
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDropEvent
import java.io.File

/** Sistem dosya seçicisi (çoklu seçim). */
fun chooseDocuments(title: String): List<File> {
    val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD).apply {
        isMultipleMode = true
        directory = System.getProperty("user.home")
        filenameFilter = java.io.FilenameFilter { _, name ->
            name.substringAfterLast('.', "").lowercase() in DocumentReaderService.supportedExtensions
        }
    }
    dialog.isVisible = true
    return dialog.files?.toList().orEmpty()
}

/** Pencere düzeyindeki kısayollar. */
fun handleAppKey(state: AppState, event: KeyEvent, onImport: () -> Unit): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    if (state.notesDialogOpen || state.renameTarget != null || state.deleteTarget != null) return false
    return when {
        event.isCtrlPressed && event.key == Key.O -> { onImport(); true }
        event.key == Key.F11 -> { state.fullScreen = !state.fullScreen; true }
        state.screen == Screen.Reader -> state.readerKeyHandler?.invoke(event) ?: false
        event.key == Key.Escape && state.searchActive -> { state.searchActive = false; state.searchQuery = ""; true }
        event.isCtrlPressed && event.key == Key.F -> { state.searchActive = true; true }
        else -> false
    }
}

@Composable
fun App(state: AppState, window: ComposeWindow? = null, onImport: () -> Unit = { state.importFiles(chooseDocuments(state.strings.chooseFiles)) }) {
    val strings = Strings(state.language)
    BidengTheme(state.themeMode) {
        CompositionLocalProvider(
            LocalStrings provides strings,
            LocalLayoutDirection provides if (state.language.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
        ) {
            if (window != null) {
                DisposableEffect(window) {
                    val previous = window.dropTarget
                    window.dropTarget = DropTarget(window, object : DropTargetAdapter() {
                        override fun drop(event: DropTargetDropEvent) {
                            runCatching {
                                event.acceptDrop(DnDConstants.ACTION_COPY)
                                if (event.transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                                    @Suppress("UNCHECKED_CAST")
                                    val files = event.transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<File>
                                    state.importFiles(files.orEmpty())
                                }
                                event.dropComplete(true)
                            }.onFailure { event.dropComplete(false) }
                        }
                    })
                    onDispose { window.dropTarget = previous }
                }
            }

            Box(Modifier.fillMaxSize()) {
                when (state.screen) {
                    Screen.Library -> LibraryScreen(state, onImport)
                    Screen.Reader -> ReaderScreen(state)
                }
                val toast = state.toast
                AnimatedVisibility(
                    visible = toast != null,
                    enter = fadeIn(), exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 132.dp)
                ) {
                    Surface(
                        color = Bideng.colors.text,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.widthIn(max = 640.dp).testTag("toast")
                    ) {
                        Text(toast?.message.orEmpty(), color = Bideng.colors.background, modifier = Modifier.padding(horizontal = 20.dp, vertical = 11.dp))
                    }
                }
                if (toast != null) LaunchedEffect(toast) { state.dismissToastLater(toast) }
            }

            if (state.notesDialogOpen) {
                var confirmClear by remember { mutableStateOf(false) }
                val list = state.notesInScope()
                NotesDialog(
                    notes = list,
                    showDocumentNames = state.screen == Screen.Library,
                    documentName = state::documentName,
                    onOpen = state::goToNote,
                    onDelete = state::deleteNote,
                    onDeleteAll = { confirmClear = true },
                    onExport = {
                        val file = state.exportNotes(list)
                        if (file != null) state.showToast(strings.exported(file.absolutePath))
                    },
                    onDismiss = { state.notesDialogOpen = false }
                )
                if (confirmClear) {
                    ConfirmDialog(
                        title = strings.clearNotesTitle,
                        message = strings.clearNotesMessage(list.size),
                        confirmLabel = strings.delete,
                        danger = true,
                        onConfirm = { state.deleteNotes(list) },
                        onDismiss = { confirmClear = false }
                    )
                }
            }

            state.renameTarget?.let { item ->
                var value by remember(item.id) {
                    val base = item.name.substringBeforeLast('.', item.name)
                    mutableStateOf(TextFieldValue(item.name, TextRange(0, base.length)))
                }
                val focus = remember { FocusRequester() }
                BidengDialog(
                    title = strings.renameTitle,
                    onDismiss = { state.renameTarget = null },
                    confirm = { PrimaryButton(strings.save, { state.rename(item, value.text); state.renameTarget = null }, enabled = value.text.isNotBlank()) },
                    dismiss = { QuietButton(strings.cancel, { state.renameTarget = null }) }
                ) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        label = { Text(strings.documentName) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = fieldColors(),
                        modifier = Modifier.focusRequester(focus).testTag("renameField")
                    )
                    LaunchedEffect(Unit) { focus.requestFocus() }
                }
            }

            state.deleteTarget?.let { item ->
                ConfirmDialog(
                    title = strings.deleteTitle,
                    message = strings.deleteMessage(item.name),
                    confirmLabel = strings.delete,
                    danger = true,
                    onConfirm = { state.delete(item) },
                    onDismiss = { state.deleteTarget = null }
                )
            }
        }
    }
}
