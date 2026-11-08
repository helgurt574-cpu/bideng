package com.example.filiz.desktop.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.filiz.desktop.model.LibraryItem
import com.example.filiz.desktop.model.Note
import com.example.filiz.desktop.service.DatabaseService
import com.example.filiz.desktop.service.DocumentContent
import com.example.filiz.desktop.service.DocumentReaderService
import com.example.filiz.desktop.service.PreferenceService
import com.example.filiz.desktop.service.SpeechEngine
import com.example.filiz.desktop.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class Screen { Library, Reader }

class LoadedDocument(val item: LibraryItem, val content: DocumentContent) {
    val title: String get() = item.name.substringBeforeLast('.').ifBlank { item.name }
}

class Toast(val message: String, val id: Long = System.nanoTime())

/** Uygulamanın arayüzden bağımsız durumu ve işlemleri (testlerde ekransız çalıştırılabilir). */
class AppState(
    private val scope: CoroutineScope,
    val dataDir: File,
    val speech: SpeechEngine,
    private val io: CoroutineDispatcher = Dispatchers.IO
) {
    var language by mutableStateOf(UiLanguage.fromId(PreferenceService.getUILanguage()))
        private set
    var themeMode by mutableStateOf(ThemeMode.fromId(PreferenceService.getThemeMode()))
        private set
    val strings: Strings get() = Strings(language)

    var screen by mutableStateOf(Screen.Library)
        private set
    var items by mutableStateOf(listOf<LibraryItem>())
        private set
    var searchActive by mutableStateOf(false)
    var searchQuery by mutableStateOf("")
    var document by mutableStateOf<LoadedDocument?>(null)
        private set
    var reader by mutableStateOf<ReaderController?>(null)
        private set
    var busyMessage by mutableStateOf<String?>(null)
        private set
    var toast by mutableStateOf<Toast?>(null)
        private set

    var notesDialogOpen by mutableStateOf(false)
    var renameTarget by mutableStateOf<LibraryItem?>(null)
    var deleteTarget by mutableStateOf<LibraryItem?>(null)
    var fullScreen by mutableStateOf(false)
    /** Okuyucu ekranı açıkken klavye olaylarını o işler (boşluk, Esc, Ctrl+F…). */
    var readerKeyHandler: ((androidx.compose.ui.input.key.KeyEvent) -> Boolean)? = null
    val notes = mutableStateListOf<Note>()

    private val contentCache = LinkedHashMap<String, DocumentContent>()
    private var openJob: Job? = null

    init {
        refresh()
    }

    // ---------- Kütüphane ----------

    fun refresh() {
        items = runCatching { DatabaseService.getAllLibraryItems() }.getOrDefault(emptyList())
        reloadNotes()
    }

    val lastOpened: LibraryItem?
        get() = items.filter { it.lastReadAt > 0 }.maxByOrNull { it.lastReadAt }

    val visibleItems: List<LibraryItem>
        get() {
            val sorted = items.sortedByDescending { it.addedAt }
            val query = searchQuery.trim()
            return if (query.isBlank()) sorted else sorted.filter { it.name.contains(query, ignoreCase = true) }
        }

    fun showToast(message: String) {
        toast = Toast(message)
    }

    private var picking = false

    /** "Belge ekle": seçiciyi açar, seçilenleri içe aktarır. Vazgeçilirse hiçbir şey yapmaz. */
    fun pickAndImport(parent: java.awt.Frame?) {
        if (picking) return
        picking = true
        scope.launch {
            try {
                val files = FilePicker.chooseDocuments(strings.chooseFiles, parent)
                if (files.isNotEmpty()) importFiles(files)
            } finally {
                picking = false
            }
        }
    }

    fun importFiles(files: List<File>) {
        val documents = files.flatMap { file ->
            when {
                file.isDirectory -> file.walkTopDown().filter { it.isFile && it.extension.lowercase() in DocumentReaderService.supportedExtensions }.toList()
                file.isFile && file.extension.lowercase() in DocumentReaderService.supportedExtensions -> listOf(file)
                else -> emptyList()
            }
        }.distinctBy { it.absolutePath }
        if (documents.isEmpty()) {
            showToast(strings.unsupported)
            return
        }
        scope.launch {
            var firstImported: LibraryItem? = null
            for (file in documents) {
                busyMessage = strings.importing(file.name)
                val result = withContext(io) { runCatching { importOne(file) } }
                result.onSuccess { (item, content) ->
                    contentCache[item.id] = content
                    if (firstImported == null) firstImported = item
                }.onFailure { error ->
                    showToast("${file.name}: ${error.message ?: error::class.simpleName}")
                }
            }
            busyMessage = null
            refresh()
            firstImported?.let { item ->
                if (documents.size > 1) showToast(strings.imported(item.name))
                open(item)
            }
        }
    }

    /** Dosyayı ~/.bideng/imports altına kopyalar (asıl dosya taşınsa da belge açılır) ve kütüphaneye ekler. */
    private fun importOne(file: File): Pair<LibraryItem, DocumentContent> {
        val existing = DatabaseService.getAllLibraryItems().firstOrNull { item ->
            item.name == file.name && File(item.path).let { it.exists() && it.length() == file.length() }
        }
        if (existing != null) {
            return existing to (contentCache[existing.id] ?: DocumentReaderService.readDocumentContent(File(existing.path)))
        }
        // Önce oku: okunamayan belge kütüphaneye eklenmesin
        val content = DocumentReaderService.readDocumentContent(file)
        val importsDir = File(dataDir, "imports").apply { mkdirs() }
        val base = file.nameWithoutExtension
        var target = File(importsDir, file.name)
        var counter = 2
        while (target.exists()) {
            target = File(importsDir, "$base ($counter).${file.extension}")
            counter++
        }
        file.copyTo(target)
        val now = System.currentTimeMillis()
        val item = LibraryItem(
            id = UUID.randomUUID().toString(),
            name = file.name,
            path = target.absolutePath,
            addedAt = now,
            lastIndex = 0,
            lastReadAt = 0,
            textLength = content.text.length
        )
        DatabaseService.addLibraryItem(item)
        return item to content
    }

    fun open(item: LibraryItem, autoResume: Boolean = false) {
        val file = File(item.path)
        if (!file.exists()) {
            showToast(strings.fileMissing)
            return
        }
        openJob?.cancel()
        openJob = scope.launch {
            busyMessage = strings.opening(item.name)
            val result = withContext(io) {
                runCatching { contentCache[item.id] ?: DocumentReaderService.readDocumentContent(file) }
            }
            busyMessage = null
            result.onSuccess { content ->
                contentCache[item.id] = content
                while (contentCache.size > 4) contentCache.remove(contentCache.keys.first())
                reader?.release()
                val opened = item.copy(lastReadAt = System.currentTimeMillis(), textLength = content.text.length)
                runCatching { DatabaseService.updateLibraryItem(opened) }
                val loaded = LoadedDocument(opened, content)
                document = loaded
                reader = ReaderController(loaded, speech, scope) { strings }
                screen = Screen.Reader
                searchActive = false
                refresh()
                if (autoResume) reader?.resumeWhenReady = true
            }.onFailure { error ->
                showToast(error.message ?: strings.fileMissing)
            }
        }
    }

    fun closeReader() {
        reader?.release()
        screen = Screen.Library
        refresh()
    }

    fun saveProgress(index: Int) {
        val current = document ?: return
        val updated = current.item.copy(lastIndex = index.coerceIn(0, current.content.text.length), lastReadAt = System.currentTimeMillis())
        runCatching { DatabaseService.updateLibraryItem(updated) }
        items = items.map { if (it.id == updated.id) updated else it }
    }

    fun rename(item: LibraryItem, newName: String) {
        val clean = newName.trim().replace(Regex("[\\\\/:*?\"<>|]"), " ").take(120)
        if (clean.isBlank()) return
        val updated = item.copy(name = clean)
        runCatching { DatabaseService.updateLibraryItem(updated) }
        if (document?.item?.id == item.id) document = LoadedDocument(updated, document!!.content)
        refresh()
    }

    fun delete(item: LibraryItem) {
        if (document?.item?.id == item.id) {
            reader?.release()
            reader = null
            document = null
            screen = Screen.Library
        }
        runCatching { DatabaseService.clearNotesForDocument(item.id) }
        runCatching { DatabaseService.deleteLibraryItem(item.id) }
        // Yalnızca uygulamanın kendi kopyası silinir; kullanıcının asıl dosyasına dokunulmaz
        File(item.path).takeIf { it.absolutePath.startsWith(File(dataDir, "imports").absolutePath) }?.delete()
        if (PreferenceService.getPausedIndex(item.id) >= 0) PreferenceService.setPausedPosition(null, -1)
        contentCache.remove(item.id)
        refresh()
    }

    // ---------- Notlar ----------

    fun reloadNotes() {
        notes.clear()
        notes.addAll(runCatching { DatabaseService.getAllNotes() }.getOrDefault(emptyList()))
    }

    fun notesInScope(): List<Note> {
        val docId = document?.item?.id
        return if (screen == Screen.Reader && docId != null) notes.filter { it.documentId == docId }.sortedBy { it.startIndex }
        else notes.sortedByDescending { it.createdAt }
    }

    fun addNote(range: IntRange) {
        val doc = document ?: return
        if (range.isEmpty()) return
        val text = doc.content.text.substring(range.first, (range.last + 1).coerceAtMost(doc.content.text.length)).trim()
        if (text.isBlank()) return
        if (notes.any { it.documentId == doc.item.id && it.startIndex == range.first && it.endIndex == range.last + 1 }) return
        runCatching {
            DatabaseService.addNote(Note(UUID.randomUUID().toString(), doc.item.id, range.first, range.last + 1, text))
        }
        reloadNotes()
        showToast(strings.noteAdded)
    }

    fun deleteNote(note: Note) {
        runCatching { DatabaseService.deleteNote(note.id) }
        reloadNotes()
    }

    fun deleteNotes(list: List<Note>) {
        list.forEach { runCatching { DatabaseService.deleteNote(it.id) } }
        reloadNotes()
    }

    fun documentName(id: String): String = items.firstOrNull { it.id == id }?.name ?: strings.untitled

    fun exportNotes(list: List<Note>): File? {
        if (list.isEmpty()) return null
        val format = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        val text = list.groupBy { documentName(it.documentId) }.entries.joinToString("\n\n") { (name, notes) ->
            name + "\n" + "=".repeat(name.length.coerceAtMost(60)) + "\n\n" +
                notes.sortedBy { it.startIndex }.joinToString("\n\n") { "• ${it.text}\n  (${format.format(Date(it.createdAt))})" }
        }
        val documents = File(System.getProperty("user.home"), "Documents").takeIf { it.isDirectory } ?: File(System.getProperty("user.home"))
        val base = if (screen == Screen.Reader) document?.title ?: "Bideng" else "Bideng"
        val file = File(documents, "${base.replace(Regex("[\\\\/:*?\"<>|]"), " ")}-notlar.txt")
        return runCatching { file.writeText(text, Charsets.UTF_8); file }.getOrNull()
    }

    fun goToNote(note: Note) {
        notesDialogOpen = false
        if (document?.item?.id == note.documentId && screen == Screen.Reader) {
            reader?.jumpTo(note.startIndex)
            return
        }
        items.firstOrNull { it.id == note.documentId }?.let { item ->
            open(item.copy(lastIndex = note.startIndex))
        }
    }

    // ---------- Görünüm ----------

    fun cycleTheme() = setTheme(themeMode.next())

    fun setTheme(mode: ThemeMode) {
        themeMode = mode
        PreferenceService.setThemeMode(mode.id)
    }

    fun changeLanguage(value: UiLanguage) {
        language = value
        PreferenceService.setUILanguage(value.id)
    }

    fun dismissToastLater(current: Toast) {
        scope.launch {
            delay(3500)
            if (toast === current) toast = null
        }
    }
}

/** Sesli okuma: oynat, duraklat (kaldığı cümleyi hatırlar), devam, durdur, cümle atla, "buradan oku". */
class ReaderController(
    val document: LoadedDocument,
    private val speech: SpeechEngine,
    private val scope: CoroutineScope,
    private val strings: () -> Strings
) {
    val text: String = document.content.text

    var isReading by mutableStateOf(false)
        private set
    var pausedIndex by mutableStateOf(PreferenceService.getPausedIndex(document.item.id).takeIf { it in text.indices } ?: -1)
        private set
    var activeRange by mutableStateOf(IntRange.EMPTY)
        private set
    var followReading by mutableStateOf(true)
    var status by mutableStateOf("")
        private set
    var lastSpokenStart by mutableStateOf(-1)
        private set
    var resumeWhenReady = false

    // Ekran tarafından bağlanır
    var visibleStart: () -> Int = { document.item.lastIndex }
    var isVisible: (Int) -> Boolean = { false }
    var scrollTo: (Int) -> Unit = {}

    private var session = 0

    init {
        if (pausedIndex >= 0) {
            activeRange = pausedIndex until sentenceEnd(pausedIndex)
            status = strings().paused
        } else {
            status = strings().readyHint
        }
    }

    fun readingAway(): Boolean = isReading && !followReading && !(lastSpokenStart >= 0 && isVisible(lastSpokenStart))

    fun toggle() {
        when {
            readingAway() -> speakFrom(visibleStart())
            isReading -> pause()
            pausedIndex >= 0 && isVisible(pausedIndex) -> speakFrom(pausedIndex)
            else -> speakFrom(visibleStart())
        }
    }

    fun speakFrom(index: Int) {
        if (text.isBlank()) return
        val start = wordStart(index.coerceIn(0, (text.length - 1).coerceAtLeast(0)))
        val current = ++session
        isReading = true
        followReading = true
        lastSpokenStart = start
        setPaused(-1)
        activeRange = IntRange.EMPTY
        status = strings().preparing
        scope.launch {
            speech.speak(
                text = text.substring(start),
                startIndex = start,
                onStatus = { message ->
                    if (current == session) status = when {
                        message.startsWith("Okunuyor") -> strings().reading
                        message.startsWith("Dahili ses hazırlanıyor") || message.startsWith("Hazırlanıyor") -> strings().preparing
                        else -> message
                    }
                },
                // Motor bu geri çağrıları arka plan iş parçacığından yapar: yalnızca durum güncellenir,
                // kaydırmayı arayüz tarafı (ReaderScreen) bu durumu izleyerek yapar.
                onRange = { a, b ->
                    if (current == session && a >= 0 && b > a) {
                        lastSpokenStart = a
                        activeRange = a until b
                    }
                },
                onComplete = {
                    if (current == session && isReading) {
                        isReading = false
                        activeRange = IntRange.EMPTY
                        status = strings().finished
                        setPaused(-1)
                    }
                }
            )
        }
    }

    fun pause() {
        if (!isReading) return
        val position = if (lastSpokenStart >= 0) lastSpokenStart else visibleStart()
        session++
        speech.stop()
        isReading = false
        setPaused(position)
        activeRange = position until sentenceEnd(position)
        status = strings().paused
    }

    fun stop() {
        session++
        speech.stop()
        isReading = false
        setPaused(-1)
        activeRange = IntRange.EMPTY
        status = strings().stopped
    }

    fun skip(direction: Int) {
        val base = when {
            lastSpokenStart >= 0 -> lastSpokenStart
            pausedIndex >= 0 -> pausedIndex
            else -> visibleStart()
        }
        speakFrom(if (direction < 0) sentenceStartBefore(base) else sentenceStartAfter(base))
    }

    fun restartIfReading() {
        if (isReading) speakFrom(if (lastSpokenStart >= 0) lastSpokenStart else visibleStart())
    }

    fun jumpTo(index: Int) {
        followReading = false
        scrollTo(index)
    }

    /** Okuyucudan çıkarken: okuyorsa duraklat (kaldığı yer saklanır). */
    fun release() {
        if (isReading) pause()
    }

    private fun setPaused(index: Int) {
        pausedIndex = index
        PreferenceService.setPausedPosition(document.item.id, index)
    }

    private fun isSentenceEnd(c: Char) = c == '.' || c == '!' || c == '?' || c == '\n' || c == '…' || c == '؟'

    fun sentenceEnd(index: Int): Int {
        var i = index.coerceIn(0, text.length)
        while (i < text.length && !isSentenceEnd(text[i])) i++
        return (i + 1).coerceAtMost(text.length)
    }

    private fun sentenceStartBefore(index: Int): Int {
        var i = (index - 2).coerceAtLeast(0)
        while (i > 0 && !isSentenceEnd(text[i - 1])) i--
        while (i < text.length && text[i].isWhitespace()) i++
        return i.coerceIn(0, text.length)
    }

    private fun sentenceStartAfter(index: Int): Int {
        var i = index.coerceIn(0, text.length)
        while (i < text.length && !isSentenceEnd(text[i])) i++
        while (i < text.length && (isSentenceEnd(text[i]) || text[i].isWhitespace())) i++
        return i.coerceIn(0, (text.length - 1).coerceAtLeast(0))
    }

    fun wordStart(index: Int): Int {
        var i = index.coerceIn(0, text.length)
        while (i > 0 && !text[i - 1].isWhitespace()) i--
        while (i < text.length && text[i].isWhitespace()) i++
        return i.coerceIn(0, text.length)
    }
}
