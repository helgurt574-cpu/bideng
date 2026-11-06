package com.example.filiz.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.filiz.desktop.model.LibraryItem
import com.example.filiz.desktop.model.Note
import com.example.filiz.desktop.model.NoteColor
import com.example.filiz.desktop.model.Bookmark
import com.example.filiz.desktop.service.DatabaseService
import com.example.filiz.desktop.service.DocumentPage
import com.example.filiz.desktop.service.PageUnit
import com.example.filiz.desktop.service.PreferenceService
import com.example.filiz.desktop.service.TTSService
import com.example.filiz.desktop.ui.LibraryDocument
import com.example.filiz.desktop.ui.Strings
import com.example.filiz.desktop.ui.UiLanguage
import com.example.filiz.desktop.ui.theme.NoteHighlightBlue
import com.example.filiz.desktop.ui.theme.NoteHighlightGreen
import com.example.filiz.desktop.ui.theme.NoteHighlightPink
import com.example.filiz.desktop.ui.theme.NoteHighlightYellow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

private const val READER_TARGET_OFFSET_PX = 180
private const val ESTIMATED_READER_PAGE_HEIGHT_PX = 2600

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    document: LibraryDocument,
    onBack: () -> Unit,
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit,
    desktopMode: Boolean = false,
    zenMode: Boolean = false,
    preferencesRequest: Int = 0
) {
    val scope = rememberCoroutineScope()
    val pages = remember(document.text, document.pages) {
        document.pages.ifEmpty { buildLocationPages(document.text) }
    }
    val totalPages = pages.size.coerceAtLeast(1)
    val initialPageIndex = remember(document.id, document.lastIndex, pages) {
        pageIndexForOffset(pages, document.lastIndex.coerceAtLeast(0))
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialPageIndex)
    var currentPage by remember { mutableStateOf((listState.firstVisibleItemIndex + 1).coerceIn(1, totalPages)) }
    var isReading by remember { mutableStateOf(false) }
    var autoScrollEnabled by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showInfoPanel by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchRanges by remember { mutableStateOf<List<IntRange>>(emptyList()) }
    var currentSearchIndex by remember { mutableStateOf(-1) }
    var uiLanguage by remember { mutableStateOf(UiLanguage.fromId(PreferenceService.getUILanguage())) }
    val strings = remember(uiLanguage) { Strings(uiLanguage) }
    var status by remember { mutableStateOf(strings.readyToRead) }
    var fontSize by remember { mutableStateOf(PreferenceService.getFontSize().coerceIn(16, 32)) }
    var lineSpacing by remember { mutableStateOf(PreferenceService.getLineSpacing().coerceIn(1.0f, 2.2f)) }
    var fontFamily by remember { mutableStateOf(PreferenceService.getFontFamily()) }
    var ttsSpeed by remember { mutableStateOf(PreferenceService.getTTSSpeed().coerceIn(0.7f, 1.35f)) }
    var voices by remember { mutableStateOf<List<com.example.filiz.desktop.service.VoiceInfo>>(emptyList()) }
    var selectedVoice by remember { mutableStateOf(PreferenceService.getTTSVoice()) }
    var notes by remember { mutableStateOf(DatabaseService.getNotesForDocument(document.id)) }
    var bookmarks by remember { mutableStateOf(DatabaseService.getBookmarksForDocument(document.id)) }
    var noteDraft by remember { mutableStateOf("") }
    var activeRange by remember { mutableStateOf(IntRange.EMPTY) }
    var selectedText by remember { mutableStateOf("") }
    var selectedRange by remember { mutableStateOf(IntRange.EMPTY) }
    var readingSession by remember { mutableStateOf(0) }
    var selectedNoteColor by remember { mutableStateOf(NoteColor.Yellow) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(listState.firstVisibleItemIndex) {
        currentPage = (listState.firstVisibleItemIndex + 1).coerceIn(1, totalPages)
        val index = pages.getOrNull(listState.firstVisibleItemIndex)?.startIndex ?: 0
        TTSService.setNowPlaying(document.name, pageLabel(strings, document.pageUnit, currentPage, totalPages))
        scope.launch(Dispatchers.IO) {
            DatabaseService.updateLibraryItem(
                LibraryItem(document.id, document.name, document.path, document.addedAt, index, System.currentTimeMillis(), document.text.length)
            )
        }
    }

    LaunchedEffect(autoScrollEnabled) {
        while (autoScrollEnabled) {
            delay(80)
            val consumed = listState.scrollBy(1.4f)
            if (consumed == 0f && !listState.canScrollForward) {
                autoScrollEnabled = false
                status = strings.autoScrollOff
            }
        }
    }

    LaunchedEffect(isReading, activeRange) {
        while (isReading) {
            delay(260)
            val range = activeRange
            if (!range.isEmpty()) {
                val target = scrollTargetForOffset(pages, range.first)
                listState.animateScrollToItem(target.first, target.second)
            }
        }
    }

    LaunchedEffect(showSettings) {
        if (showSettings && voices.isEmpty()) voices = TTSService.getAvailableVoices()
    }

    LaunchedEffect(preferencesRequest) {
        if (preferencesRequest > 0) {
            fontSize = PreferenceService.getFontSize()
            lineSpacing = PreferenceService.getLineSpacing()
            fontFamily = PreferenceService.getFontFamily()
            ttsSpeed = PreferenceService.getTTSSpeed()
            selectedVoice = PreferenceService.getTTSVoice()
            TTSService.setSpeed(ttsSpeed)
            TTSService.setVoice(selectedVoice)
            status = strings.settingsOpen
        }
    }
    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            delay(3500)
            toastMessage = null
        }
    }

    fun currentIndex(): Int = pages.getOrNull(listState.firstVisibleItemIndex)?.startIndex?.coerceIn(0, document.text.length) ?: 0

    fun stopReading() {
        readingSession++
        TTSService.stop()
        isReading = false
        activeRange = IntRange.EMPTY
        status = strings.readingStopped
    }

    fun toggleReading() {
        if (isReading) {
            stopReading()
            return
        }
        autoScrollEnabled = false
        val start = currentIndex()
        val textToRead = document.text.substring(start).trim()
        if (textToRead.isBlank()) return
        TTSService.setSpeed(ttsSpeed)
        TTSService.setNowPlaying(document.name, pageLabel(strings, document.pageUnit, currentPage, totalPages))
        if (selectedVoice.isNotBlank()) TTSService.setVoice(selectedVoice)
        val session = readingSession + 1
        readingSession = session
        isReading = true
        status = strings.readingStarts
        scope.launch {
            TTSService.speak(
                text = textToRead,
                startIndex = start,
                onStatus = { status = it },
                onRange = { rangeStart, rangeEnd ->
                    if (isReading && session == readingSession) {
                        activeRange = if (rangeStart >= 0 && rangeEnd > rangeStart) {
                            rangeStart until rangeEnd
                        } else {
                            IntRange.EMPTY
                        }
                        if (rangeStart >= 0) {
                            scope.launch {
                                if (!isReading || session != readingSession) return@launch
                                val target = scrollTargetForOffset(pages, rangeStart)
                                listState.animateScrollToItem(target.first, target.second)
                            }
                        }
                    }
                },
                onComplete = {
                    if (session == readingSession) isReading = false
                }
            )
            if (session == readingSession) {
                isReading = false
                activeRange = IntRange.EMPTY
            }
            if (!status.startsWith("TTS") && !status.contains("bulunamadı") && !status.contains("eksik")) {
                status = strings.readyToRead
            }
        }
    }

    fun addNote() {
        val start = currentIndex()
        val pageText = pages.getOrNull(listState.firstVisibleItemIndex)?.text.orEmpty()
        val noteText = selectedText.ifBlank { noteDraft }.ifBlank { pageText.take(220).trim() }.ifBlank { document.name }
        val noteStart = if (!selectedRange.isEmpty()) selectedRange.first else start
        val note = Note(
            id = UUID.randomUUID().toString(),
            documentId = document.id,
            startIndex = noteStart,
            endIndex = (noteStart + noteText.length).coerceAtMost(document.text.length),
            text = noteText,
            color = selectedNoteColor,
            createdAt = System.currentTimeMillis()
        )
        scope.launch {
            withContext(Dispatchers.IO) { DatabaseService.addNote(note) }
            notes = withContext(Dispatchers.IO) { DatabaseService.getNotesForDocument(document.id) }
        }
        noteDraft = ""
        val hadSelection = !selectedRange.isEmpty()
        selectedText = ""
        selectedRange = IntRange.EMPTY
        status = if (hadSelection) strings.selectedNoteAdded else strings.noteAdded
    }

    fun addBookmark() {
        val index = currentIndex()
        val page = pages.getOrNull(pageIndexForOffset(pages, index))?.label ?: "Konum ${currentPage}"
        val bookmark = Bookmark(
            id = UUID.randomUUID().toString(),
            documentId = document.id,
            index = index,
            label = page,
            createdAt = System.currentTimeMillis()
        )
        scope.launch {
            withContext(Dispatchers.IO) { DatabaseService.addBookmark(bookmark) }
            bookmarks = withContext(Dispatchers.IO) { DatabaseService.getBookmarksForDocument(document.id) }
        }
        status = "Yer imi eklendi: Konum $page"
    }

    fun clearAllNotes() {
        scope.launch {
            withContext(Dispatchers.IO) { DatabaseService.clearNotesForDocument(document.id) }
            notes = emptyList()
        }
        status = strings.text("Bu kitabin notlari silindi", "Noten ve pirtuke hatin jêbirin", "تم حذف ملاحظات هذا الكتاب")
    }

    fun exportNotesAsTxt() {
        if (notes.isEmpty()) {
            status = strings.noNotes
            return
        }
        val documentsDir = File(System.getProperty("user.home"), "Documents").takeIf { it.exists() } ?: File(System.getProperty("user.home"))
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val safeName = document.name.replace(Regex("[^A-Za-z0-9._-]+"), "_").take(60).ifBlank { "kitap" }
        val file = File(documentsDir, "Bideng-notlar-$safeName-$stamp.txt")
        file.writeText(
            notes.joinToString("\n\n") { note ->
                "- ${note.text}\nkonum: ${notePageLabel(note, pages)}\naralik: ${note.startIndex}-${note.endIndex}"
            },
            Charsets.UTF_8
        )
        status = "Notlar dışa aktarıldı: ${file.name}"
        toastMessage = "Notlar dışa aktarıldı: ${file.name}"
    }

    fun runSearch(query: String, direction: Int = 1, restart: Boolean = false) {
        val needle = query.trim()
        if (needle.isBlank()) {
            status = strings.text("Aranacak kelime yaz", "Peyveke ji bo legerine binivise", "اكتب كلمة للبحث")
            return
        }
        val ranges = if (restart || !needle.equals(searchQuery, ignoreCase = true) || searchRanges.isEmpty()) {
            buildSearchRanges(document.text, needle)
        } else {
            searchRanges
        }
        searchQuery = needle
        searchRanges = ranges
        if (ranges.isEmpty()) {
            currentSearchIndex = -1
                        status = "\"$needle\" ${strings.notFound}"
            return
        }
        val nextIndex = when {
            restart || currentSearchIndex !in ranges.indices -> 0
            direction >= 0 -> (currentSearchIndex + 1) % ranges.size
            else -> if (currentSearchIndex - 1 < 0) ranges.lastIndex else currentSearchIndex - 1
        }
        currentSearchIndex = nextIndex
        val range = ranges[nextIndex]
        scope.launch {
            val target = scrollTargetForOffset(pages, range.first)
            listState.animateScrollToItem(target.first, target.second)
        }
        status = "\"$needle\" ${nextIndex + 1}/${ranges.size}"
    }

        Scaffold(
            modifier = Modifier.onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Spacebar -> {
                        if (!showSettings && !showSearch && !showNotes) {
                            toggleReading()
                            true
                        } else {
                            false
                        }
                    }
                    Key.F -> {
                        if (event.isCtrlPressed) {
                            showSearch = true
                            true
                        } else false
                    }
                    Key.B -> {
                        if (event.isCtrlPressed) {
                            addBookmark()
                            true
                        } else false
                    }
                    Key.N -> {
                        if (event.isCtrlPressed) {
                            showNotes = true
                            true
                        } else false
                    }
                    Key.Plus, Key.Equals -> {
                        fontSize = (fontSize + 1).coerceAtMost(32)
                        PreferenceService.setFontSize(fontSize)
                        true
                    }
                    Key.Minus -> {
                        fontSize = (fontSize - 1).coerceAtLeast(16)
                        PreferenceService.setFontSize(fontSize)
                        true
                    }
                    Key.DirectionRight, Key.PageDown -> {
                        scope.launch { listState.animateScrollToItem((listState.firstVisibleItemIndex + 1).coerceAtMost(totalPages - 1)) }
                        true
                    }
                    Key.DirectionLeft, Key.PageUp -> {
                        scope.launch { listState.animateScrollToItem((listState.firstVisibleItemIndex - 1).coerceAtLeast(0)) }
                        true
                    }
                    else -> false
                }
            },
            topBar = {
                if (!desktopMode) {
                    TopAppBar(
                        title = {
                            Column {
                                Text(document.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(pageLabel(strings, document.pageUnit, currentPage, totalPages), style = MaterialTheme.typography.bodySmall)
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                stopReading()
                                onBack()
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                            }
                        },
                        actions = {
                            IconButton(onClick = { showNotes = true }) {
                                Icon(Icons.Default.Note, contentDescription = "Notlar")
                            }
                            IconButton(onClick = { showSettings = true; status = strings.settingsOpen }) {
                                Icon(Icons.Default.Settings, contentDescription = "Ayarlar")
                            }
                            IconButton(onClick = { showSearch = true }) {
                                Icon(Icons.Default.Search, contentDescription = "Ara")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            titleContentColor = MaterialTheme.colorScheme.onPrimary,
                            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                            actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            },
            bottomBar = {
                Column {
                    ReaderDock(
                        currentPage = currentPage,
                        totalPages = totalPages,
                        pageUnit = document.pageUnit,
                        strings = strings,
                        isReading = isReading,
                        autoScrollEnabled = autoScrollEnabled,
                        showInfoPanel = showInfoPanel,
                        onPrevious = {
                            scope.launch { listState.animateScrollToItem((listState.firstVisibleItemIndex - 1).coerceAtLeast(0)) }
                        },
                        onPageSelected = { page ->
                            scope.launch { listState.animateScrollToItem((page - 1).coerceIn(0, totalPages - 1)) }
                        },
                        onPlayStop = ::toggleReading,
                        onNext = {
                            scope.launch { listState.animateScrollToItem((listState.firstVisibleItemIndex + 1).coerceAtMost(totalPages - 1)) }
                        },
                        onAutoScroll = {
                            if (isReading) {
                                status = strings.noAutoScrollWhileReading
                            } else {
                                autoScrollEnabled = !autoScrollEnabled
                                status = if (autoScrollEnabled) strings.autoScrollOn else strings.autoScrollOff
                            }
                        },
                        onSettings = { showSettings = true; status = strings.settingsOpen },
                        onSearch = { showSearch = true },
                        onNotes = { showNotes = true },
                        onToggleInfo = { showInfoPanel = !showInfoPanel },
                        onAddNote = {
                            if (selectedText.isBlank()) {
                                status = strings.text("Önce metinden kelime ya da cümle seç", "Pêşî peyv an hevokek hilbijêre", "حدد كلمة أو جملة أولاً")
                                showNotes = true
                            } else {
                                addNote()
                                showNotes = true
                            }
                        },
                        onBookmark = ::addBookmark
                    )
                    if (!desktopMode && !zenMode) {
                        NavigationBar {
                            NavigationBarItem(
                                selected = false,
                                onClick = {
                                    stopReading()
                                    onBack()
                                },
                            icon = { Icon(Icons.Default.Menu, contentDescription = null) },
                            label = { Text(strings.libraryTab) }
                            )
                            NavigationBarItem(
                                selected = true,
                                onClick = {},
                            icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                            label = { Text(strings.readerTab) }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Row(Modifier.fillMaxSize()) {
                    ReaderPages(
                        pages = pages,
                        listState = listState,
                        fontSize = fontSize,
                        lineSpacing = lineSpacing,
                        fontFamily = fontFamily,
                        activeRange = activeRange,
                        noteRanges = notes.map { it.startIndex until it.endIndex },
                        searchRanges = searchRanges,
                        currentSearchRange = searchRanges.getOrNull(currentSearchIndex) ?: IntRange.EMPTY,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onSelection = { text, range ->
                            selectedText = text
                            selectedRange = range
                            if (text.isNotBlank()) status = strings.noteReady
                        }
                    )
                    if (!zenMode && showInfoPanel) {
                        ReaderInfoPanel(
                            modifier = Modifier.width(320.dp).fillMaxHeight(),
                            document = document,
                            currentPage = currentPage,
                            totalPages = totalPages,
                            strings = strings,
                            notes = notes,
                            bookmarks = bookmarks,
                            onGoToIndex = { index ->
                                scope.launch {
                                    val target = scrollTargetForOffset(pages, index)
                                    listState.animateScrollToItem(target.first, target.second)
                                }
                            },
                            onDeleteBookmark = { bookmark ->
                                scope.launch {
                                    withContext(Dispatchers.IO) { DatabaseService.deleteBookmark(bookmark.id) }
                                    bookmarks = withContext(Dispatchers.IO) { DatabaseService.getBookmarksForDocument(document.id) }
                                }
                            },
                            onDeleteNote = { note ->
                                scope.launch {
                                    withContext(Dispatchers.IO) { DatabaseService.deleteNote(note.id) }
                                    notes = withContext(Dispatchers.IO) { DatabaseService.getNotesForDocument(document.id) }
                                }
                            },
                            onClearNotes = ::clearAllNotes,
                            onExportNotes = ::exportNotesAsTxt,
                            onClose = { showInfoPanel = false }
                        )
                    }
                }

                if (showSearch) {
                    InlineSearchBar(
                        query = searchQuery,
                        onQuery = { searchQuery = it },
                        onDismiss = { showSearch = false },
                        onFind = { runSearch(searchQuery, restart = true) },
                        onPrevious = { runSearch(searchQuery, direction = -1) },
                        onNext = { runSearch(searchQuery, direction = 1) },
                        matchLabel = if (searchRanges.isNotEmpty() && currentSearchIndex in searchRanges.indices) {
                            "${currentSearchIndex + 1}/${searchRanges.size}"
                        } else {
                            "0/0"
                        },
                        strings = strings,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
                    )
                }
                toastMessage?.let { message ->
                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.inverseSurface,
                        tonalElevation = 4.dp
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

    if (showSettings) {
        SettingsDialog(
            fontSize = fontSize,
            onFontSize = {
                fontSize = it
                PreferenceService.setFontSize(it)
            },
            lineSpacing = lineSpacing,
            onLineSpacing = {
                lineSpacing = it
                PreferenceService.setLineSpacing(it)
            },
            fontFamily = fontFamily,
            onFontFamily = {
                fontFamily = it
                PreferenceService.setFontFamily(it)
            },
            ttsSpeed = ttsSpeed,
            onTtsSpeed = {
                ttsSpeed = it
                PreferenceService.setTTSSpeed(it)
                TTSService.setSpeed(it)
            },
            voices = voices,
            selectedVoice = selectedVoice,
            onVoice = {
                selectedVoice = it
                PreferenceService.setTTSVoice(it)
                TTSService.setVoice(it)
            },
            isDarkMode = isDarkMode,
            onToggleTheme = onToggleTheme,
            strings = strings,
            uiLanguage = uiLanguage,
            onUiLanguage = {
                uiLanguage = it
                PreferenceService.setUILanguage(it.id)
                status = strings.text("Dil değiştirildi.", "Ziman guherî.", "تم تغيير اللغة.")
            },
            onClose = { showSettings = false }
        )
    }

    if (showNotes) {
        NotesDialog(
            notes = notes,
            pages = pages,
            noteDraft = noteDraft,
            selectedText = selectedText,
            selectedColor = selectedNoteColor,
            strings = strings,
            onNoteDraft = { noteDraft = it },
            onColor = { selectedNoteColor = it },
            onAdd = ::addNote,
            onDelete = { note ->
                scope.launch {
                    withContext(Dispatchers.IO) { DatabaseService.deleteNote(note.id) }
                    notes = withContext(Dispatchers.IO) { DatabaseService.getNotesForDocument(document.id) }
                }
            },
            onClearAll = ::clearAllNotes,
            onExport = ::exportNotesAsTxt,
            onDismiss = { showNotes = false }
        )
    }
}

@Composable
private fun ReaderPages(
    pages: List<DocumentPage>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    fontSize: Int,
    lineSpacing: Float,
    fontFamily: String,
    activeRange: IntRange,
    noteRanges: List<IntRange>,
    searchRanges: List<IntRange>,
    currentSearchRange: IntRange,
    modifier: Modifier = Modifier,
    onSelection: (String, IntRange) -> Unit
) {
    val family = when (fontFamily) {
        "sans-serif", "sans-serif-light", "sans-serif-medium", "sans-serif-condensed" -> FontFamily.SansSerif
        "serif" -> FontFamily.Serif
        "monospace" -> FontFamily.Monospace
        "Serif" -> FontFamily.Serif
        "Sans Serif" -> FontFamily.SansSerif
        "Monospace" -> FontFamily.Monospace
        else -> FontFamily.Default
    }
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(pages.withIndex().toList()) { indexedPage ->
            val pageInfo = indexedPage.value
            val page = pageInfo.text
            val pageStart = pageInfo.startIndex
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                Surface(
                    modifier = Modifier.widthIn(max = 920.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))
                ) {
                    var fieldValue by remember(page) { mutableStateOf(TextFieldValue(page)) }
                    BasicTextField(
                        value = fieldValue,
                        onValueChange = { value ->
                            val cleanValue = value.copy(text = page)
                            fieldValue = cleanValue
                            val start = minOf(cleanValue.selection.start, cleanValue.selection.end).coerceIn(0, page.length)
                            val end = maxOf(cleanValue.selection.start, cleanValue.selection.end).coerceIn(0, page.length)
                            if (end > start) {
                                onSelection(page.substring(start, end), pageStart + start until pageStart + end)
                            }
                        },
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 34.dp, vertical = 32.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize * lineSpacing).sp,
                            fontFamily = family,
                            textAlign = TextAlign.Start,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        decorationBox = { innerTextField ->
                            Box(Modifier.fillMaxWidth()) {
                                innerTextField()
                            }
                        }
                    )
                    if (activeRange.isEmpty() && noteRanges.isEmpty() && searchRanges.isEmpty() && currentSearchRange.isEmpty()) {
                        // No overlay needed. BasicTextField is used so selected text can be captured for notes.
                    } else {
                        Text(
                            text = highlightedPageText(page, pageStart, activeRange, noteRanges, searchRanges, currentSearchRange),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 34.dp, vertical = 32.dp)
                                .background(Color.Transparent),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = fontSize.sp,
                                lineHeight = (fontSize * lineSpacing).sp,
                                fontFamily = family,
                                textAlign = TextAlign.Start,
                                color = Color.Transparent
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun highlightedPageText(
    page: String,
    pageStart: Int,
    activeRange: IntRange,
    noteRanges: List<IntRange>,
    searchRanges: List<IntRange>,
    currentSearchRange: IntRange
) = buildAnnotatedString {
    append(page)
    searchRanges.forEach { searchRange ->
        val localStart = (searchRange.first - pageStart).coerceIn(0, page.length)
        val localEnd = (searchRange.last + 1 - pageStart).coerceIn(0, page.length)
        if (localEnd > localStart) {
            addStyle(
                SpanStyle(background = Color(0x554FC3F7), color = Color(0xFF111111)),
                localStart,
                localEnd
            )
        }
    }
    if (!currentSearchRange.isEmpty()) {
        val localStart = (currentSearchRange.first - pageStart).coerceIn(0, page.length)
        val localEnd = (currentSearchRange.last + 1 - pageStart).coerceIn(0, page.length)
        if (localEnd > localStart) {
            addStyle(
                SpanStyle(background = Color(0x665EEAD4), color = Color(0xFFEFFFFB)),
                localStart,
                localEnd
            )
        }
    }
    noteRanges.forEach { noteRange ->
        val localStart = (noteRange.first - pageStart).coerceIn(0, page.length)
        val localEnd = (noteRange.last + 1 - pageStart).coerceIn(0, page.length)
        if (localEnd > localStart) {
            addStyle(
                SpanStyle(background = Color(0x33FFF7B0)),
                localStart,
                localEnd
            )
        }
    }
    if (!activeRange.isEmpty()) {
        val localStart = (activeRange.first - pageStart).coerceIn(0, page.length)
        val localEnd = (activeRange.last + 1 - pageStart).coerceIn(0, page.length)
        if (localEnd > localStart) {
            addStyle(
                SpanStyle(
                    background = Color(0x44BFDBFE),
                    color = Color.Unspecified,
                    fontWeight = FontWeight.Normal
                ),
                localStart,
                localEnd
            )
        }
    }
}

@Composable
private fun ReaderInfoPanel(
    modifier: Modifier,
    document: com.example.filiz.desktop.ui.LibraryDocument,
    currentPage: Int,
    totalPages: Int,
    strings: Strings,
    notes: List<Note>,
    bookmarks: List<Bookmark>,
    onGoToIndex: (Int) -> Unit,
    onDeleteBookmark: (Bookmark) -> Unit,
    onDeleteNote: (Note) -> Unit,
    onClearNotes: () -> Unit,
    onExportNotes: () -> Unit,
    onClose: () -> Unit
) {
    var pendingDeleteNote by remember { mutableStateOf<Note?>(null) }
    val wordCount = remember(document.text) { document.text.split(Regex("\\s+")).count { it.isNotBlank() } }
    val minutes = (wordCount / 180).coerceAtLeast(1)
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                .padding(14.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(strings.info, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.close)
                }
            }
            Text(document.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            InfoMetric(strings.text("İlerleme", "Pêşketin", "التقدم"), pageLabel(strings, document.pageUnit, currentPage, totalPages))
            InfoMetric(strings.text("Kelime", "Peyv", "كلمة"), wordCount.toString())
            InfoMetric(strings.text("Tahmini süre", "Dema texmînî", "المدة المقدرة"), "$minutes dk")

            HorizontalDivider()
            Text(strings.bookmark, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            if (bookmarks.isEmpty()) {
                Text("Henüz yer imi yok", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                bookmarks.forEach { bookmark ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onGoToIndex(bookmark.index) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(bookmark.label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { onDeleteBookmark(bookmark) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = strings.delete, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            HorizontalDivider()
            Text(strings.notes, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Button(onClick = onExportNotes, enabled = notes.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text(strings.exportNotesTxt)
            }
            TextButton(onClick = onClearNotes, enabled = notes.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text(strings.clearNotes)
            }
            if (notes.isEmpty()) {
                Text("Henüz not yok", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                notes.take(8).forEach { note ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f).clickable { onGoToIndex(note.startIndex) },
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(notePageLabel(note, document.pages), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(note.text, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { pendingDeleteNote = note }) {
                            Text(strings.delete)
                        }
                    }
                }
            }

            HorizontalDivider()
            Text(if (document.pageUnit == PageUnit.RealPage) strings.text("Sayfalar", "Rûpel", "الصفحات") else strings.text("Konumlar", "Cih", "المواضع"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            document.pages.take(40).forEachIndexed { index, page ->
                Text(
                    text = page.label,
                    modifier = Modifier.fillMaxWidth().clickable { onGoToIndex(page.startIndex) }.padding(vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (index + 1 == currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
    pendingDeleteNote?.let { note ->
        AlertDialog(
            onDismissRequest = { pendingDeleteNote = null },
            title = { Text(strings.deleteNoteQuestion) },
            text = { Text("\"${note.text.take(90)}\" notu silinsin mi?") },
            confirmButton = {
                Button(onClick = {
                    onDeleteNote(note)
                    pendingDeleteNote = null
                }) { Text(strings.delete) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteNote = null }) { Text(strings.cancel) }
            }
        )
    }
}

@Composable
private fun InfoMetric(label: String, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ReaderDock(
    currentPage: Int,
    totalPages: Int,
    pageUnit: PageUnit,
    strings: Strings,
    isReading: Boolean,
    autoScrollEnabled: Boolean,
    showInfoPanel: Boolean,
    onPrevious: () -> Unit,
    onPageSelected: (Int) -> Unit,
    onPlayStop: () -> Unit,
    onNext: () -> Unit,
    onAutoScroll: () -> Unit,
    onSettings: () -> Unit,
    onSearch: () -> Unit,
    onNotes: () -> Unit,
    onToggleInfo: () -> Unit,
    onAddNote: () -> Unit,
    onBookmark: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(pageLabel(strings, pageUnit, currentPage, totalPages), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(145.dp))
                Slider(
                    value = currentPage.toFloat(),
                    onValueChange = { onPageSelected(it.roundToInt()) },
                    valueRange = 1f..totalPages.toFloat().coerceAtLeast(1f),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onSearch) { Icon(Icons.Default.Search, contentDescription = strings.find) }
                IconButton(onClick = onNotes) { Icon(Icons.Default.Note, contentDescription = strings.notes) }
                IconButton(onClick = onBookmark) { Icon(Icons.Default.Bookmark, contentDescription = strings.bookmark) }
                TextButton(onClick = onToggleInfo) {
                    Icon(
                        if (showInfoPanel) Icons.Default.Close else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (showInfoPanel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (showInfoPanel) strings.closePanel else strings.info)
                }
                TextButton(onClick = onAddNote, modifier = Modifier.heightIn(min = 36.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(strings.addNote)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onPrevious, enabled = isReading || currentPage > 1) {
                    Icon(Icons.Default.FastRewind, contentDescription = strings.previous)
                }
                Button(onClick = onPlayStop, modifier = Modifier.width(156.dp).heightIn(min = 38.dp)) {
                    Icon(if (isReading) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isReading) strings.stop else strings.readAloud)
                }
                IconButton(onClick = onNext, enabled = isReading || currentPage < totalPages) {
                    Icon(Icons.Default.FastForward, contentDescription = strings.next)
                }
                IconButton(onClick = onAutoScroll) {
                    Icon(
                        Icons.Default.AutoMode,
                        contentDescription = strings.autoScroll,
                        tint = if (autoScrollEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsPanel(
    fontSize: Int,
    onFontSize: (Int) -> Unit,
    lineSpacing: Float,
    onLineSpacing: (Float) -> Unit,
    fontFamily: String,
    onFontFamily: (String) -> Unit,
    ttsSpeed: Float,
    onTtsSpeed: (Float) -> Unit,
    voices: List<com.example.filiz.desktop.service.VoiceInfo>,
    selectedVoice: String,
    onVoice: (String) -> Unit,
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit,
    strings: Strings,
    uiLanguage: UiLanguage,
    onUiLanguage: (UiLanguage) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(strings.settings, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Kapat") }
        }
        Text(strings.voice, fontWeight = FontWeight.Bold)
        Text("${strings.speechSpeed}: ${"%.2f".format(ttsSpeed)}x", style = MaterialTheme.typography.bodySmall)
        Slider(value = ttsSpeed, onValueChange = onTtsSpeed, valueRange = 0.7f..1.35f)
        if (voices.isEmpty()) {
            Text(strings.embeddedVoicesMissing, style = MaterialTheme.typography.bodySmall)
        } else {
            LazyColumn(modifier = Modifier.heightIn(min = 140.dp, max = 260.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
        Text(strings.languageTitle.removePrefix("🌐 "), fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            UiLanguage.entries.forEach { language ->
                AssistChip(
                    onClick = { onUiLanguage(language) },
                    label = { Text(language.label) },
                    leadingIcon = { if (language == uiLanguage) Text("✓") }
                )
            }
        }
        HorizontalDivider()
        Text(strings.readingView, fontWeight = FontWeight.Bold)
        Text("${strings.textSize}: $fontSize sp", style = MaterialTheme.typography.bodySmall)
        Slider(value = fontSize.toFloat(), onValueChange = { onFontSize(it.roundToInt()) }, valueRange = 16f..32f)
        Text("${strings.lineSpacing}: ${(lineSpacing * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall)
        Slider(value = lineSpacing, onValueChange = onLineSpacing, valueRange = 1.0f..2.2f)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "sans-serif" to "Roboto",
                "serif" to "Serif",
                "sans-serif-light" to "Light",
                "sans-serif-medium" to "Medium",
                "sans-serif-condensed" to "Condensed",
                "monospace" to "Monospace"
            ).forEach { (family, label) ->
                AssistChip(
                    onClick = { onFontFamily(family) },
                    label = { Text(label) },
                    leadingIcon = { if (family == fontFamily) Text("✓") }
                )
            }
        }
    }
}

@Composable
private fun InlineSearchBar(
    query: String,
    onQuery: (String) -> Unit,
    onFind: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    matchLabel: String,
    onDismiss: () -> Unit,
    strings: Strings,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.widthIn(min = 520.dp, max = 720.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                placeholder = { Text(strings.searchHint) },
                singleLine = true,
                modifier = Modifier.weight(1f).onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                        if (event.isShiftPressed) onPrevious() else onNext()
                        true
                    } else {
                        false
                    }
                }
            )
            Text(matchLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = onPrevious) { Text(strings.previous) }
            TextButton(onClick = onNext) { Text(strings.next) }
            Button(onClick = onFind) { Text(strings.find) }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = strings.close)
            }
        }
    }
}

@Composable
private fun SettingsDialog(
    fontSize: Int,
    onFontSize: (Int) -> Unit,
    lineSpacing: Float,
    onLineSpacing: (Float) -> Unit,
    fontFamily: String,
    onFontFamily: (String) -> Unit,
    ttsSpeed: Float,
    onTtsSpeed: (Float) -> Unit,
    voices: List<com.example.filiz.desktop.service.VoiceInfo>,
    selectedVoice: String,
    onVoice: (String) -> Unit,
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit,
    strings: Strings,
    uiLanguage: UiLanguage,
    onUiLanguage: (UiLanguage) -> Unit,
    onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(strings.settings) },
        text = {
            SettingsPanel(
                fontSize = fontSize,
                onFontSize = onFontSize,
                lineSpacing = lineSpacing,
                onLineSpacing = onLineSpacing,
                fontFamily = fontFamily,
                onFontFamily = onFontFamily,
                ttsSpeed = ttsSpeed,
                onTtsSpeed = onTtsSpeed,
                voices = voices,
                selectedVoice = selectedVoice,
                onVoice = onVoice,
                isDarkMode = isDarkMode,
                onToggleTheme = onToggleTheme,
                strings = strings,
                uiLanguage = uiLanguage,
                onUiLanguage = onUiLanguage,
                onClose = onClose,
                modifier = Modifier.widthIn(min = 620.dp, max = 760.dp).heightIn(max = 620.dp)
            )
        },
        confirmButton = { Button(onClick = onClose) { Text(strings.ok) } }
    )
}

@Composable
private fun NotesDialog(
    notes: List<Note>,
    pages: List<DocumentPage>,
    noteDraft: String,
    selectedText: String,
    selectedColor: NoteColor,
    strings: Strings,
    onNoteDraft: (String) -> Unit,
    onColor: (NoteColor) -> Unit,
    onAdd: () -> Unit,
    onDelete: (Note) -> Unit,
    onClearAll: () -> Unit,
    onExport: () -> Unit,
    onDismiss: () -> Unit
) {
    var pendingDelete by remember { mutableStateOf<Note?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.notes) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = noteDraft,
                    onValueChange = onNoteDraft,
                    placeholder = {
                        Text(
                            if (selectedText.isNotBlank()) {
                                strings.selectedTextHint
                            } else {
                                strings.noteHint
                            }
                        )
                    },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                if (selectedText.isNotBlank()) {
                    Text(
                        text = "${strings.selectedTextPrefix}: ${selectedText.take(140)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(strings.addNote)
                }
                Text("Not rengi", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NoteColor.entries.forEach { color ->
                        val selected = color == selectedColor
                        Surface(
                            modifier = Modifier.size(34.dp).clickable { onColor(color) },
                            shape = RoundedCornerShape(8.dp),
                            color = color.toComposeColor(),
                            border = androidx.compose.foundation.BorderStroke(
                                if (selected) 3.dp else 1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        ) {}
                    }
                }
                Button(onClick = onExport, enabled = notes.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                    Text("Notları TXT olarak dışa aktar")
                }
                TextButton(onClick = { confirmClearAll = true }, enabled = notes.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                    Text("Tüm notları sil")
                }
                HorizontalDivider()
                if (notes.isEmpty()) {
                    Text(strings.noNotes)
                } else {
                    LazyColumn(modifier = Modifier.height(260.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(notes) { note ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, note.color.toComposeColor(), RoundedCornerShape(8.dp))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        notePageLabel(note, pages),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(note.text, maxLines = 4, overflow = TextOverflow.Ellipsis)
                                }
                                TextButton(onClick = { pendingDelete = note }) {
                                    Text("Sil")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text(strings.ok) } }
    )

    val noteToDelete = pendingDelete
    if (noteToDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Not silinsin mi?") },
            text = { Text("\"${noteToDelete.text.take(80)}\" notu silinsin mi?") },
            confirmButton = {
                Button(onClick = {
                    onDelete(noteToDelete)
                    pendingDelete = null
                }) {
                    Text("Sil")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(strings.cancel)
                }
            }
        )
    }
    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("Tüm notlar silinsin mi?") },
            text = { Text("Bu kitaba ait ${notes.size} not silinsin mi?") },
            confirmButton = {
                Button(onClick = {
                    onClearAll()
                    confirmClearAll = false
                }) {
                    Text("Sil")
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

private fun pageLabel(strings: Strings, pageUnit: PageUnit, current: Int, total: Int): String {
    val percent = ((current.toFloat() / total.coerceAtLeast(1)) * 100).roundToInt().coerceIn(0, 100)
    return when (pageUnit) {
        PageUnit.RealPage -> strings.text("Sayfa $current / $total · %$percent", "Rûpel $current / $total · %$percent", "صفحة $current / $total · %$percent")
        PageUnit.Chapter -> strings.text("Bölüm $current / $total · %$percent", "Beş $current / $total · %$percent", "فصل $current / $total · %$percent")
        PageUnit.Location -> strings.pageLabel(current, total, percent).replace("Sayfa", "Konum")
    }
}

private fun notePageLabel(note: Note, pages: List<DocumentPage>): String {
    return pages.getOrNull(pageIndexForOffset(pages, note.startIndex))?.label ?: "Konum 1"
}

private fun pageIndexForOffset(pages: List<DocumentPage>, offset: Int): Int {
    if (pages.isEmpty()) return 0
    val index = pages.indexOfLast { offset >= it.startIndex }
    return index.coerceIn(0, pages.lastIndex)
}

private fun scrollTargetForOffset(pages: List<DocumentPage>, offset: Int): Pair<Int, Int> {
    val pageIndex = pageIndexForOffset(pages, offset)
    val page = pages.getOrNull(pageIndex) ?: return 0 to 0
    val length = (page.endIndex - page.startIndex).coerceAtLeast(1)
    val localStart = (offset - page.startIndex).coerceIn(0, length)
    val targetOffset = ((localStart.toFloat() / length) * ESTIMATED_READER_PAGE_HEIGHT_PX)
        .roundToInt()
        .minus(READER_TARGET_OFFSET_PX)
        .coerceAtLeast(0)
    return pageIndex to targetOffset
}

private fun buildLocationPages(text: String): List<DocumentPage> {
    var cursor = 0
    return text.chunked(3000).ifEmpty { listOf("") }.mapIndexed { index, chunk ->
        val start = cursor
        cursor += chunk.length
        DocumentPage(
            number = index + 1,
            label = "Konum ${index + 1}",
            title = "Konum ${index + 1}",
            text = chunk,
            startIndex = start,
            endIndex = cursor
        )
    }
}

private fun NoteColor.toComposeColor(): Color = when (this) {
    NoteColor.Yellow -> NoteHighlightYellow
    NoteColor.Pink -> NoteHighlightPink
    NoteColor.Green -> NoteHighlightGreen
    NoteColor.Blue -> NoteHighlightBlue
}

private fun buildSearchRanges(text: String, query: String): List<IntRange> {
    if (query.isBlank()) return emptyList()
    val ranges = mutableListOf<IntRange>()
    var index = text.indexOf(query, ignoreCase = true)
    val step = query.length.coerceAtLeast(1)
    while (index >= 0) {
        ranges += index until (index + query.length).coerceAtMost(text.length)
        index = text.indexOf(query, startIndex = index + step, ignoreCase = true)
    }
    return ranges
}
