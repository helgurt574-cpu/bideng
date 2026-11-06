package com.example.filiz.desktop.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.example.filiz.desktop.model.LibraryItem
import com.example.filiz.desktop.model.Note
import com.example.filiz.desktop.service.DatabaseService
import com.example.filiz.desktop.service.DocumentPage
import com.example.filiz.desktop.service.DocumentReaderService
import com.example.filiz.desktop.service.PageUnit
import com.example.filiz.desktop.service.PreferenceService
import com.example.filiz.desktop.service.TTSService
import com.example.filiz.desktop.service.VoiceInfo
import com.example.filiz.desktop.ui.screens.ReaderScreen
import com.example.filiz.desktop.ui.theme.FilizTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDropEvent
import java.awt.event.KeyEvent
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun App(window: Window? = null) {
    val scope = rememberCoroutineScope()
    var isDarkMode by remember { mutableStateOf(PreferenceService.getDarkMode()) }
    var libraryItems by remember { mutableStateOf(DatabaseService.getAllLibraryItems()) }
    var selectedDocument by remember { mutableStateOf<LibraryDocument?>(null) }
    var openDocuments by remember { mutableStateOf<List<LibraryDocument>>(emptyList()) }
    var selectedSection by remember { mutableStateOf(DesktopSection.Home) }
    var zenMode by remember { mutableStateOf(false) }
    var sidebarVisible by remember { mutableStateOf(true) }
    var libraryOpen by remember { mutableStateOf(false) }
    var preferencesRequest by remember { mutableStateOf(0) }
    var uiLanguage by remember { mutableStateOf(UiLanguage.fromId(PreferenceService.getUILanguage())) }
    var pendingDelete by remember { mutableStateOf<LibraryItem?>(null) }
    var libraryQuery by remember { mutableStateOf("") }
    var libraryWidth by remember { mutableStateOf(320.dp) }
    var statusMessage by remember { mutableStateOf("Hazır") }
    var loadingMessage by remember { mutableStateOf<String?>(null) }
    var showAppSettings by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var fontSize by remember { mutableStateOf(PreferenceService.getFontSize()) }
    var lineSpacing by remember { mutableStateOf(PreferenceService.getLineSpacing()) }
    var fontFamily by remember { mutableStateOf(PreferenceService.getFontFamily()) }
    var ttsSpeed by remember { mutableStateOf(PreferenceService.getTTSSpeed()) }
    var selectedVoice by remember { mutableStateOf(PreferenceService.getTTSVoice()) }
    var defaultSleepTimer by remember { mutableStateOf<Int?>(TTSService.getDefaultSleepTimer()) }
    var voices by remember { mutableStateOf<List<VoiceInfo>>(emptyList()) }
    val strings = remember(uiLanguage) { Strings(uiLanguage) }
    val appFocusRequester = remember { FocusRequester() }
    val ttsPlayback = TTSService.playbackState
    val miniPlayerVisible = ttsPlayback.isSpeaking || ttsPlayback.currentText.isNotBlank() || ttsPlayback.sleepTimerEndsAt != null
    val visibleLibraryItems = remember(libraryItems, libraryQuery) {
        if (libraryQuery.isBlank()) {
            libraryItems
        } else {
            libraryItems.filter { it.name.contains(libraryQuery, ignoreCase = true) }
        }
    }

    fun refreshLibrary() {
        libraryItems = DatabaseService.getAllLibraryItems()
    }

    fun refreshLibraryAsync() {
        scope.launch {
            libraryItems = withContext(Dispatchers.IO) { DatabaseService.getAllLibraryItems() }
        }
    }

    fun showDocument(document: LibraryDocument) {
        openDocuments = (openDocuments.filterNot { it.id == document.id } + document).takeLast(8)
        selectedDocument = document
        selectedSection = DesktopSection.Reader
        statusMessage = "Açıldı: ${document.name}"
    }

    fun closeDocument(documentId: String) {
        val nextDocuments = openDocuments.filterNot { it.id == documentId }
        openDocuments = nextDocuments
        if (selectedDocument?.id == documentId) {
            selectedDocument = nextDocuments.lastOrNull()
            selectedSection = if (selectedDocument == null) DesktopSection.Home else DesktopSection.Reader
        }
    }

    fun openItem(item: LibraryItem) {
        val file = File(item.path)
        if (!file.exists()) return
        loadingMessage = "Açılıyor: ${item.name}"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { DocumentReaderService.readDocumentContent(file) }
            }
            loadingMessage = null
            result.onSuccess { content ->
                showDocument(LibraryDocument(
                    id = item.id,
                    name = item.name,
                    path = item.path,
                    text = content.text,
                    pages = content.pages,
                    pageUnit = content.pageUnit,
                    lastIndex = item.lastIndex,
                    lastReadAt = item.lastReadAt,
                    addedAt = item.addedAt
                ))
            }.onFailure { error ->
                statusMessage = error.message ?: "Belge açılamadı"
            }
        }
    }

    fun importFile(file: File) {
        if (!file.exists() || file.isDirectory) return
        val canonicalPath = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
        statusMessage = "İçe aktarılıyor: ${file.name}"
        libraryItems.firstOrNull { runCatching { File(it.path).canonicalPath }.getOrDefault(it.path) == canonicalPath }?.let { existing ->
            openItem(existing)
            return
        }
        loadingMessage = "İçe aktarılıyor: ${file.name}"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    DatabaseService.getAllLibraryItems()
                        .firstOrNull { runCatching { File(it.path).canonicalPath }.getOrDefault(it.path) == canonicalPath }
                        ?.let { return@runCatching it to DocumentReaderService.readDocumentContent(File(it.path)) }
                    val content = DocumentReaderService.readDocumentContent(file)
                    val item = LibraryItem(
                        id = UUID.randomUUID().toString(),
                        name = file.name,
                        path = canonicalPath,
                        addedAt = System.currentTimeMillis(),
                        lastIndex = 0,
                        lastReadAt = System.currentTimeMillis(),
                        textLength = content.text.length
                    )
                    DatabaseService.addLibraryItem(item)
                    item to content
                }
            }
            loadingMessage = null
            result.onSuccess { (item, content) ->
                refreshLibrary()
                showDocument(
                    LibraryDocument(
                        id = item.id,
                        name = item.name,
                        path = item.path,
                        text = content.text,
                        pages = content.pages,
                        pageUnit = content.pageUnit,
                        lastIndex = item.lastIndex,
                        lastReadAt = item.lastReadAt,
                        addedAt = item.addedAt
                    )
                )
                libraryOpen = false
                statusMessage = "Kitaplığa eklendi: ${item.name}"
            }.onFailure { error ->
                statusMessage = error.message ?: "Belge içe aktarılamadı"
            }
        }
    }

    fun importDroppedText(text: String) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return
        val importsDir = File(System.getProperty("user.home"), ".bideng/imports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(importsDir, "suruklenen-metin-$stamp.txt")
        file.writeText(cleanText, Charsets.UTF_8)
        importFile(file)
    }

    fun importFiles(files: List<File>) {
        val documents = files.flatMap { file ->
            when {
                file.isDirectory -> file.walkTopDown().filter { it.isFile && it.extension.lowercase() in supportedExtensions }.toList()
                file.isFile && file.extension.lowercase() in supportedExtensions -> listOf(file)
                else -> emptyList()
            }
        }.distinctBy { it.absolutePath }
        if (documents.isEmpty()) {
            statusMessage = "Desteklenen belge bulunamadı"
            return
        }
        statusMessage = "${documents.size} belge içe aktarılıyor"
        documents.forEach(::importFile)
    }

    fun importWithChooser() {
        val dialog = FileDialog(null as Frame?, "Belge içe aktar", FileDialog.LOAD).apply {
            isMultipleMode = true
            directory = System.getProperty("user.home")
            filenameFilter = java.io.FilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase() in supportedExtensions }
        }
        dialog.isVisible = true
        val files = dialog.files?.toList().orEmpty()
        if (files.isNotEmpty()) {
            importFiles(files)
        }
    }

    LaunchedEffect(DesktopCommands.importRequest) {
        if (DesktopCommands.importRequest > 0) importWithChooser()
    }
    LaunchedEffect(DesktopCommands.preferencesRequest) {
        if (DesktopCommands.preferencesRequest > 0) {
            showAppSettings = true
        }
    }
    LaunchedEffect(showAppSettings) {
        if (showAppSettings && voices.isEmpty()) {
            voices = withContext(Dispatchers.IO) { TTSService.getAvailableVoices() }
        }
    }
    LaunchedEffect(DesktopCommands.focusLibraryRequest) {
        if (DesktopCommands.focusLibraryRequest > 0) {
            selectedSection = DesktopSection.Library
            libraryOpen = true
        }
    }
    LaunchedEffect(DesktopCommands.focusReaderRequest) {
        if (DesktopCommands.focusReaderRequest > 0) {
            if (selectedDocument == null) libraryItems.firstOrNull()?.let(::openItem)
            selectedSection = DesktopSection.Reader
        }
    }
    LaunchedEffect(DesktopCommands.toggleFocusRequest) {
        if (DesktopCommands.toggleFocusRequest > 0) zenMode = !zenMode
    }
    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            kotlinx.coroutines.delay(3500)
            toastMessage = null
        }
    }
    LaunchedEffect(Unit) {
        appFocusRequester.requestFocus()
    }
    DisposableEffect(Unit) {
        val dispatcher = java.awt.KeyEventDispatcher { event ->
            if (event.id == KeyEvent.KEY_PRESSED && event.keyCode == KeyEvent.VK_F11) {
                zenMode = !zenMode
                true
            } else {
                false
            }
        }
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher)
        onDispose {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(dispatcher)
        }
    }
    DisposableEffect(window) {
        if (window == null) {
            return@DisposableEffect onDispose { }
        }
        val previousDropTarget = window.dropTarget
        val dropTarget = DropTarget(window, object : DropTargetAdapter() {
            override fun drop(event: DropTargetDropEvent) {
                runCatching {
                    event.acceptDrop(DnDConstants.ACTION_COPY)
                    val transferable = event.transferable
                    when {
                        transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor) -> {
                            @Suppress("UNCHECKED_CAST")
                            val droppedFiles = transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<File>
                            importFiles(droppedFiles.orEmpty())
                        }
                        transferable.isDataFlavorSupported(DataFlavor.stringFlavor) -> {
                            val text = transferable.getTransferData(DataFlavor.stringFlavor) as? String
                            if (!text.isNullOrBlank()) importDroppedText(text)
                        }
                        else -> statusMessage = "Desteklenen sürükle-bırak içeriği bulunamadı"
                    }
                    event.dropComplete(true)
                }.onFailure {
                    event.dropComplete(false)
                    statusMessage = it.message ?: "Sürükle-bırak tamamlanamadı"
                }
            }
        })
        window.dropTarget = dropTarget
        onDispose {
            window.dropTarget = previousDropTarget
        }
    }

    FilizTheme(darkTheme = isDarkMode) {
        CompositionLocalProvider(
            LocalLayoutDirection provides if (uiLanguage == UiLanguage.Arabic) LayoutDirection.Rtl else LayoutDirection.Ltr
        ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(appFocusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        event.key == Key.F11 -> {
                            zenMode = !zenMode
                            true
                        }
                        event.isCtrlPressed && event.key == Key.O -> {
                            importWithChooser()
                            true
                        }
                        event.isCtrlPressed && event.key == Key.Comma -> {
                            showAppSettings = true
                            true
                        }
                        event.isCtrlPressed && event.key == Key.W -> {
                            selectedDocument?.id?.let(::closeDocument)
                            true
                        }
                        else -> false
                    }
                }
                ,
            color = MaterialTheme.colorScheme.background
        ) {
            Box(Modifier.fillMaxSize()) {
                if (!zenMode) {
                    AppCommandBar(
                        modifier = Modifier.align(Alignment.TopStart).fillMaxWidth(),
                        strings = strings,
                        document = selectedDocument,
                        onImport = ::importWithChooser,
                        onLibrary = {
                            selectedSection = DesktopSection.Library
                            libraryOpen = true
                        },
                        onReader = {
                            if (selectedDocument == null) libraryItems.firstOrNull()?.let(::openItem)
                            selectedSection = DesktopSection.Reader
                            libraryOpen = false
                        },
                        onToggleFocus = { zenMode = !zenMode },
                        onSettings = { showAppSettings = true }
                    )
                }
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(top = if (zenMode) 0.dp else 46.dp, bottom = if (zenMode) 0.dp else 24.dp)
                ) {
                    if (!zenMode && sidebarVisible) {
                        AppSidebar(
                            selectedSection = selectedSection,
                            libraryOpen = libraryOpen,
                            strings = strings,
                            selectedDocument = selectedDocument,
                            isDarkMode = isDarkMode,
                            onHome = {
                                selectedSection = DesktopSection.Home
                                libraryOpen = false
                            },
                            onLibrary = {
                                selectedSection = DesktopSection.Library
                                libraryOpen = !libraryOpen
                            },
                            onReader = {
                                if (selectedDocument == null) libraryItems.firstOrNull()?.let(::openItem)
                                selectedSection = DesktopSection.Reader
                                libraryOpen = false
                            },
                            onNotes = {
                                selectedSection = DesktopSection.Notes
                                libraryOpen = false
                            },
                            onSettings = {
                                showAppSettings = true
                                libraryOpen = false
                            },
                            onToggleTheme = {
                                isDarkMode = !isDarkMode
                                PreferenceService.setDarkMode(isDarkMode)
                            },
                            onToggleFocus = { zenMode = !zenMode },
                            onHideSidebar = {
                                sidebarVisible = false
                                libraryOpen = false
                            }
                        )
                    }

                    if (!zenMode && sidebarVisible) Divider(Modifier.fillMaxHeight().width(1.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))

                    AnimatedVisibility(visible = libraryOpen && !zenMode) {
                        val animatedWidth by animateDpAsState(libraryWidth)
                        LibraryPane(
                            modifier = Modifier.width(animatedWidth).fillMaxHeight(),
                            items = visibleLibraryItems,
                            query = libraryQuery,
                            onQuery = { libraryQuery = it },
                            selectedId = selectedDocument?.id,
                            strings = strings,
                            onImport = ::importWithChooser,
                            onOpen = ::openItem,
                            onDelete = { item -> pendingDelete = item }
                        )

                        Box(
                            Modifier
                                .fillMaxHeight()
                                .width(6.dp)
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                                .pointerHoverIcon(PointerIcon.Hand)
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        libraryWidth = (libraryWidth + dragAmount.x.dp).coerceIn(260.dp, 520.dp)
                                    }
                                }
                        )
                    }

                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        val document = selectedDocument
                        Column(Modifier.fillMaxSize()) {
                            if (!zenMode && openDocuments.isNotEmpty()) {
                                DocumentTabs(
                                    documents = openDocuments,
                                    selectedId = selectedDocument?.id,
                                    onSelect = {
                                        selectedDocument = it
                                        selectedSection = DesktopSection.Reader
                                    },
                                    onClose = ::closeDocument
                                )
                            }
                            Crossfade(targetState = selectedSection to document?.id) {
                                when {
                                    selectedSection == DesktopSection.Home -> HomeDashboard(
                                        items = libraryItems,
                                        strings = strings,
                                        onImport = ::importWithChooser,
                                        onOpen = ::openItem
                                    )
                                    selectedSection == DesktopSection.Notes -> NotesDashboard(
                                        strings = strings,
                                        libraryItems = libraryItems,
                                        onOpenDocument = { id ->
                                            libraryItems.firstOrNull { it.id == id }?.let(::openItem)
                                        },
                                        onMessage = {
                                            statusMessage = it
                                            toastMessage = it
                                        }
                                    )
                                    document == null -> EmptyReaderPane(strings = strings, onImport = ::importWithChooser)
                                    else -> ReaderScreen(
                                        document = document,
                                        onBack = { selectedSection = DesktopSection.Library },
                                        isDarkMode = isDarkMode,
                                        onToggleTheme = {
                                            isDarkMode = !isDarkMode
                                            PreferenceService.setDarkMode(isDarkMode)
                                        },
                                        desktopMode = true,
                                        zenMode = zenMode,
                                        preferencesRequest = preferencesRequest
                                    )
                                }
                            }
                        }
                    }
                }
                if (!zenMode && miniPlayerVisible) {
                    MiniTtsPlayer(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp, start = 16.dp, end = 16.dp),
                        strings = strings,
                        onStop = { TTSService.stop() },
                        onSleep = { minutes -> TTSService.setSleepTimer(minutes) }
                    )
                }
                if (!zenMode) {
                    AppStatusBar(
                        modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
                        message = statusMessage,
                        document = selectedDocument,
                        openCount = openDocuments.size
                    )
                }
                if (!zenMode && !sidebarVisible) {
                    Surface(
                        shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                        modifier = Modifier.align(Alignment.CenterStart).size(width = 22.dp, height = 76.dp)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            IconButton(
                                onClick = { sidebarVisible = true },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Menüyü aç")
                            }
                        }
                    }
                }
                toastMessage?.let { message ->
                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.inverseSurface
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                loadingMessage?.let { message ->
                    Surface(
                        modifier = Modifier.align(Alignment.Center),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    ) {
                        Row(
                            Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            Text(message)
                        }
                    }
                }
                if (showAppSettings) {
                    AppSettingsDialog(
                        isDarkMode = isDarkMode,
                        onToggleTheme = {
                            isDarkMode = !isDarkMode
                            PreferenceService.setDarkMode(isDarkMode)
                            preferencesRequest++
                        },
                        fontSize = fontSize,
                        onFontSize = {
                            fontSize = it
                            PreferenceService.setFontSize(it)
                            preferencesRequest++
                        },
                        lineSpacing = lineSpacing,
                        onLineSpacing = {
                            lineSpacing = it
                            PreferenceService.setLineSpacing(it)
                            preferencesRequest++
                        },
                        fontFamily = fontFamily,
                        onFontFamily = {
                            fontFamily = it
                            PreferenceService.setFontFamily(it)
                            preferencesRequest++
                        },
                        ttsSpeed = ttsSpeed,
                        onTtsSpeed = {
                            ttsSpeed = it
                            PreferenceService.setTTSSpeed(it)
                            TTSService.setSpeed(it)
                            preferencesRequest++
                        },
                        voices = voices,
                        selectedVoice = selectedVoice,
                        onVoice = {
                            selectedVoice = it
                            PreferenceService.setTTSVoice(it)
                            TTSService.setVoice(it)
                            preferencesRequest++
                        },
                        defaultSleepTimer = defaultSleepTimer,
                        onDefaultSleepTimer = {
                            defaultSleepTimer = it
                            TTSService.setDefaultSleepTimer(it)
                        },
                        uiLanguage = uiLanguage,
                        onUiLanguage = {
                            uiLanguage = it
                            PreferenceService.setUILanguage(it.id)
                        },
                        strings = strings,
                        onDismiss = { showAppSettings = false }
                    )
                }

                val itemToDelete = pendingDelete
                if (itemToDelete != null) {
                    AlertDialog(
                        onDismissRequest = { pendingDelete = null },
                        title = { Text(strings.deleteBookQuestion) },
                        text = { Text("\"${itemToDelete.name}\" kitaplıktan silinsin mi?") },
                        confirmButton = {
                            Button(onClick = {
                                DatabaseService.deleteLibraryItem(itemToDelete.id)
                                if (selectedDocument?.id == itemToDelete.id) selectedDocument = null
                                refreshLibrary()
                                pendingDelete = null
                            }) {
                                Text(strings.delete)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingDelete = null }) {
                                Text(strings.cancel)
                            }
                        }
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun LibraryPane(
    modifier: Modifier,
    items: List<LibraryItem>,
    query: String,
    onQuery: (String) -> Unit,
    selectedId: String?,
    strings: Strings,
    onImport: () -> Unit,
    onOpen: (LibraryItem) -> Unit,
    onDelete: (LibraryItem) -> Unit
) {
    Column(modifier.background(MaterialTheme.colorScheme.surface).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(strings.libraryTab, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("${items.size} belge", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TooltipIconButton("${strings.importDocument} (Ctrl+O)") {
                IconButton(onClick = onImport) {
                    Icon(Icons.Default.Add, contentDescription = strings.importDocument)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text(strings.searchLibrary) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        if (items.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(strings.emptyLibrary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text("Ctrl+O ile ilk belgeyi içe aktarabilirsin.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text(strings.importDocument) }
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                items(items, key = { it.id }) { item ->
                    LibraryRow(
                        item = item,
                        selected = item.id == selectedId,
                        strings = strings,
                        onOpen = { onOpen(item) },
                        onDelete = { onDelete(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryRow(
    item: LibraryItem,
    selected: Boolean,
    strings: Strings,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    ContextMenuArea(
        items = {
            listOf(
                ContextMenuItem("Aç") { onOpen() },
                ContextMenuItem("Dosya konumunu aç") {
                    val parent = File(item.path).parentFile
                    if (parent != null && parent.exists() && Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().open(parent)
                    }
                },
                ContextMenuItem("Kitaplıktan sil") { onDelete() }
            )
        }
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(onClick = onOpen),
            shape = RoundedCornerShape(8.dp),
            color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f) else MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
            ),
        ) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                BookCoverBadge(item.name)
                Column(Modifier.weight(1f)) {
                    Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text("${item.textLength} ${strings.characters}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DocumentTabs(
    documents: List<LibraryDocument>,
    selectedId: String?,
    onSelect: (LibraryDocument) -> Unit,
    onClose: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        documents.forEach { document ->
            Surface(
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand).clickable { onSelect(document) },
                shape = RoundedCornerShape(8.dp),
                color = if (document.id == selectedId) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                border = BorderStroke(
                    1.dp,
                    if (document.id == selectedId) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                ),
            ) {
                Row(
                    modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(document.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(150.dp))
                    IconButton(onClick = { onClose(document.id) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Sekmeyi kapat", modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeDashboard(
    items: List<LibraryItem>,
    strings: Strings,
    onImport: () -> Unit,
    onOpen: (LibraryItem) -> Unit
) {
    val lastItem = items.firstOrNull()
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp).widthIn(max = 980.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(strings.continueReading, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(strings.emptyReaderHelp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Button(onClick = onImport) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(strings.importDocument)
            }
            if (lastItem != null) {
                Button(onClick = { onOpen(lastItem) }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(strings.openLastBook)
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(strings.summary, fontWeight = FontWeight.Bold)
                Text("${items.size} belge kitaplıkta")
                if (lastItem != null) {
                    Text("${strings.lastRead}: ${lastItem.name}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Konum: %${progressPercent(lastItem.lastIndex, lastItem.textLength)}")
                } else {
                    Text(strings.emptyLibrary)
                }
            }
        }
    }
}

@Composable
private fun AppCommandBar(
    modifier: Modifier,
    strings: Strings,
    document: LibraryDocument?,
    onImport: () -> Unit,
    onLibrary: () -> Unit,
    onReader: () -> Unit,
    onToggleFocus: () -> Unit,
    onSettings: () -> Unit
) {
    Surface(
        modifier = modifier.height(46.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Bideng", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (document != null) {
                Text(
                    document.name,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            TextButton(onClick = onImport) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(strings.importDocument)
            }
            TextButton(onClick = onLibrary) {
                Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(strings.libraryTab)
            }
            TextButton(onClick = onReader) {
                Icon(Icons.Default.AutoStories, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(strings.readerTab)
            }
            TooltipIconButton("${strings.focus} (F11)") {
                IconButton(onClick = onToggleFocus) {
                    Icon(Icons.Default.AutoStories, contentDescription = strings.focus)
                }
            }
            TooltipIconButton("${strings.settings} (Ctrl+,)") {
                IconButton(onClick = onSettings) {
                    Icon(Icons.Default.Settings, contentDescription = strings.settings)
                }
            }
        }
    }
}

@Composable
private fun NotesDashboard(strings: Strings) {
    NotesDashboard(strings = strings, libraryItems = emptyList(), onOpenDocument = {}, onMessage = {})
}

@Composable
private fun NotesDashboard(
    strings: Strings,
    libraryItems: List<LibraryItem>,
    onOpenDocument: (String) -> Unit,
    onMessage: (String) -> Unit
) {
    var notes by remember { mutableStateOf(DatabaseService.getAllNotes()) }
    var pendingDeleteNote by remember { mutableStateOf<Note?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }
    val libraryById = remember(libraryItems) { libraryItems.associateBy { it.id } }
    fun exportAllNotes() {
        if (notes.isEmpty()) {
            onMessage(strings.noNotes)
            return
        }
        val documentsDir = File(System.getProperty("user.home"), "Documents").takeIf { it.exists() } ?: File(System.getProperty("user.home"))
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(documentsDir, "Bideng-tum-notlar-$stamp.txt")
        file.writeText(
            notes.groupBy { it.documentId }.entries.joinToString("\n\n") { (documentId, documentNotes) ->
                val title = libraryById[documentId]?.name ?: "Bilinmeyen kitap"
                "$title\n" + documentNotes.joinToString("\n") { note -> "- ${note.text}" }
            },
            Charsets.UTF_8
        )
        onMessage("Tüm notlar dışa aktarıldı: ${file.name}")
    }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(strings.notes, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { confirmClearAll = true }, enabled = notes.isNotEmpty()) {
                Text(strings.deleteAll)
            }
            Button(onClick = ::exportAllNotes, enabled = notes.isNotEmpty()) {
                Text(strings.exportAllNotes)
            }
        }
        if (notes.isEmpty()) {
            Text(strings.noNotes, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                notes.groupBy { it.documentId }.forEach { (documentId, groupedNotes) ->
                    item(key = "header-$documentId") {
                        val title = libraryById[documentId]?.name ?: "Bilinmeyen kitap"
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            TextButton(onClick = { onOpenDocument(documentId) }) { Text(strings.openBook) }
                        }
                    }
                    items(groupedNotes, key = { it.id }) { note ->
                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Konum ${(note.startIndex / 3000) + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    Text(note.text, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                }
                                TextButton(onClick = { pendingDeleteNote = note }) {
                                    Text(strings.delete)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDeleteNote?.let { note ->
        AlertDialog(
            onDismissRequest = { pendingDeleteNote = null },
            title = { Text(strings.deleteNoteQuestion) },
            text = { Text("\"${note.text.take(80)}\" notu silinsin mi?") },
            confirmButton = {
                Button(onClick = {
                    DatabaseService.deleteNote(note.id)
                    notes = DatabaseService.getAllNotes()
                    pendingDeleteNote = null
                    onMessage("Not silindi")
                }) {
                    Text(strings.delete)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteNote = null }) {
                    Text(strings.cancel)
                }
            }
        )
    }
    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text(strings.deleteAllNotesQuestion) },
            text = { Text("Tüm kitaplara ait ${notes.size} not silinsin mi?") },
            confirmButton = {
                Button(onClick = {
                    notes.forEach { DatabaseService.deleteNote(it.id) }
                    notes = DatabaseService.getAllNotes()
                    confirmClearAll = false
                    onMessage("Tüm notlar silindi")
                }) {
                    Text(strings.delete)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}

@Composable
private fun AppSidebar(
    selectedSection: DesktopSection,
    libraryOpen: Boolean,
    strings: Strings,
    selectedDocument: LibraryDocument?,
    isDarkMode: Boolean,
    onHome: () -> Unit,
    onLibrary: () -> Unit,
    onReader: () -> Unit,
    onNotes: () -> Unit,
    onSettings: () -> Unit,
    onToggleTheme: () -> Unit,
    onToggleFocus: () -> Unit,
    onHideSidebar: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(228.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Bideng", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                selectedDocument?.name ?: "Offline e-kitap okuyucu",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(8.dp))
        SidebarItem(strings.home, Icons.Default.Home, selectedSection == DesktopSection.Home, onHome)
        SidebarItem(strings.libraryTab, Icons.AutoMirrored.Filled.LibraryBooks, libraryOpen || selectedSection == DesktopSection.Library, onLibrary)
        SidebarItem(strings.readerTab, Icons.Default.AutoStories, selectedSection == DesktopSection.Reader, onReader)
        SidebarItem(strings.notes, Icons.Default.Note, selectedSection == DesktopSection.Notes, onNotes)
        SidebarItem(strings.focus, Icons.Default.AutoStories, false, onToggleFocus)
        Spacer(Modifier.weight(1f))
        SidebarItem(strings.settings, Icons.Default.Settings, false, onSettings)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TooltipIconButton("Menüyü kapat") {
                IconButton(onClick = onHideSidebar, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menüyü kapat")
                }
            }
        }
    }
}

@Composable
private fun SidebarItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .animateContentSize()
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f) else Color.Transparent
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                icon,
                contentDescription = label,
                modifier = Modifier.size(19.dp),
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EmptyReaderPane(strings: Strings, onImport: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.AutoStories, contentDescription = null, modifier = Modifier.size(46.dp), tint = MaterialTheme.colorScheme.primary)
            Text(strings.readyToRead, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(strings.emptyReaderHelp, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onImport) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(strings.importDocument)
            }
        }
    }
}

@Composable
private fun BookCoverBadge(title: String) {
    val letter = title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "B"
    Surface(
        modifier = Modifier.size(width = 34.dp, height = 44.dp),
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(letter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TooltipIconButton(label: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState()
    ) {
        content()
    }
}

@Composable
private fun AppStatusBar(
    modifier: Modifier,
    message: String,
    document: LibraryDocument?,
    openCount: Int
) {
    Surface(modifier = modifier.height(24.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(message, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (document != null) Text(document.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$openCount sekme", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MiniTtsPlayer(
    modifier: Modifier,
    strings: Strings,
    onStop: () -> Unit,
    onSleep: (Int?) -> Unit
) {
    val state = TTSService.playbackState
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(state.sleepTimerEndsAt) {
        while (state.sleepTimerEndsAt != null) {
            kotlinx.coroutines.delay(1000)
            tick++
        }
    }
    val remaining = remember(state.sleepTimerEndsAt, tick) {
        TTSService.sleepRemainingSeconds()?.let { seconds ->
            "%02d:%02d".format(seconds / 60, seconds % 60)
        }
    }
    Surface(
        modifier = modifier.widthIn(max = 920.dp).fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                if (state.isSpeaking) Icons.Default.PlayArrow else Icons.Default.Stop,
                contentDescription = null,
                tint = if (state.isSpeaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    state.documentName.ifBlank { "Sesli okuma" },
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOfNotNull(
                        state.pageLabel.takeIf { it.isNotBlank() },
                        state.status,
                        state.currentText.takeIf { it.isNotBlank() }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (remaining != null) {
                Text("${strings.sleep} $remaining", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = { onSleep(null) }) { Text(strings.cancel) }
            }
            Button(onClick = onStop, enabled = state.isSpeaking) {
                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(strings.stop)
            }
        }
    }
}

@Composable
private fun AppSettingsDialog(
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit,
    fontSize: Int,
    onFontSize: (Int) -> Unit,
    lineSpacing: Float,
    onLineSpacing: (Float) -> Unit,
    fontFamily: String,
    onFontFamily: (String) -> Unit,
    ttsSpeed: Float,
    onTtsSpeed: (Float) -> Unit,
    voices: List<VoiceInfo>,
    selectedVoice: String,
    onVoice: (String) -> Unit,
    defaultSleepTimer: Int?,
    onDefaultSleepTimer: (Int?) -> Unit,
    uiLanguage: UiLanguage,
    onUiLanguage: (UiLanguage) -> Unit,
    strings: Strings,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.settings) },
        text = {
            Column(
                modifier = Modifier
                    .widthIn(min = 620.dp, max = 760.dp)
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(strings.appTheme, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.LightMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text(if (isDarkMode) strings.darkTheme else strings.lightTheme)
                        Text(strings.appliesEverywhere, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = isDarkMode, onCheckedChange = { onToggleTheme() })
                }
                HorizontalDivider()
                Text(strings.readingView, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("${strings.textSize}: $fontSize sp", style = MaterialTheme.typography.bodySmall)
                Slider(value = fontSize.toFloat(), onValueChange = { onFontSize(it.roundToInt()) }, valueRange = 16f..32f)
                Text("${strings.lineSpacing}: ${(lineSpacing * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall)
                Slider(value = lineSpacing, onValueChange = onLineSpacing, valueRange = 1.0f..2.2f)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        "sans-serif" to "Roboto",
                        "serif" to "Serif",
                        "sans-serif-light" to "Light",
                        "sans-serif-medium" to "Medium",
                        "monospace" to "Monospace"
                    ).forEach { (family, label) ->
                        AssistChip(
                            onClick = { onFontFamily(family) },
                            label = { Text(label) },
                            leadingIcon = { if (family == fontFamily) Text("✓") }
                        )
                    }
                }
                HorizontalDivider()
                Text(strings.voice, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("${strings.speechSpeed}: ${"%.2f".format(ttsSpeed)}x", style = MaterialTheme.typography.bodySmall)
                Slider(value = ttsSpeed, onValueChange = onTtsSpeed, valueRange = 0.7f..1.35f)
                Text(strings.sleepTimer, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(null, 15, 30, 60).forEach { minutes ->
                        val label = when (minutes) {
                            null -> strings.off
                            15 -> strings.minutes15
                            30 -> strings.minutes30
                            else -> strings.minutes60
                        }
                        AssistChip(
                            onClick = { onDefaultSleepTimer(minutes) },
                            label = { Text(label) },
                            leadingIcon = { if (minutes == defaultSleepTimer) Text("✓") }
                        )
                    }
                }
                if (voices.isEmpty()) {
                    Text(strings.embeddedVoicesMissing, style = MaterialTheme.typography.bodySmall)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(min = 90.dp, max = 180.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(voices) { voice ->
                            AssistChip(
                                onClick = { onVoice(voice.id) },
                                label = { Text(voice.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingIcon = { if (voice.id == selectedVoice) Text("✓") }
                            )
                        }
                    }
                }
                HorizontalDivider()
                Text(strings.languageTitle.removePrefix("🌐 "), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UiLanguage.entries.forEach { language ->
                        AssistChip(
                            onClick = { onUiLanguage(language) },
                            label = { Text(language.label) },
                            leadingIcon = { if (language == uiLanguage) Text("✓") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(strings.ok)
            }
        }
    )
}

private enum class DesktopSection {
    Home,
    Library,
    Reader,
    Notes
}

private val supportedExtensions = setOf("pdf", "txt", "doc", "docx", "epub", "html", "htm")

private fun progressPercent(index: Int, total: Int): Int {
    if (total <= 0) return 0
    return ((index.toFloat() / total) * 100).toInt().coerceIn(0, 100)
}

data class LibraryDocument(
    val id: String,
    val name: String,
    val path: String,
    val text: String,
    val pages: List<DocumentPage> = emptyList(),
    val pageUnit: PageUnit = PageUnit.Location,
    val lastIndex: Int = 0,
    val lastReadAt: Long = System.currentTimeMillis(),
    val addedAt: Long = System.currentTimeMillis()
)
