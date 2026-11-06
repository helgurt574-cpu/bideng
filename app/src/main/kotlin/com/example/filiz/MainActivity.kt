package com.example.filiz

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.pm.ActivityInfo
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.content.Intent
import android.content.ContentValues
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Typeface
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.provider.MediaStore
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.util.Log
import android.view.KeyEvent
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

private const val OPEN_DOCUMENT_REQUEST = 17
internal const val AUDIO_QUEUE_CAPACITY = 6
internal const val AUDIO_PREFETCH_CHUNKS = 1
private const val MAX_SEARCH_HIGHLIGHTS = 300
private const val LARGE_DOCUMENT_THRESHOLD = 120000
private const val DOCUMENT_WINDOW_CHARS = 70000
private const val MAX_DOCUMENT_NAME_LENGTH = 96
// Bump when extraction output changes so cached document text is rebuilt
// (v2: PDF ligature repair, v3: table/paragraph-aware DOCX/ODT/HTML/EPUB/RTF/DOC, scanned PDF OCR).
private const val DOCUMENT_TEXT_CACHE_VERSION = 3
private const val MIN_VISUAL_PDF_ZOOM = 1.0f
private const val MAX_VISUAL_PDF_ZOOM = 3.0f
internal const val DEFAULT_TTS_SPEED = 0.96f
internal const val MIN_TTS_SPEED = 0.70f
internal const val MAX_TTS_SPEED = 1.35f
private const val DEFAULT_AUTO_SCROLL_SPEED = 1.0f
private const val MIN_AUTO_SCROLL_SPEED = 0.4f
private const val MAX_AUTO_SCROLL_SPEED = 2.6f
internal const val DEFAULT_SLEEP_TIMER_MINUTES = 30
internal val SLEEP_TIMER_OPTIONS = listOf(0, 15, 30, 60)
data class LibraryItem(
    val id: String,
    val name: String,
    val path: String,
    val addedAt: Long,
    val lastIndex: Int,
    val lastReadAt: Long,
    val textLength: Int
)

private data class DocumentTextResult(
    val text: String,
    val isOcrPreview: Boolean,
    val isVisualPdfFallback: Boolean = false
)

data class ReaderNote(
    val id: String,
    val documentId: String,
    val documentName: String,
    val word: String,
    val context: String,
    val start: Int,
    val end: Int,
    val pageNumber: Int,
    val createdAt: Long
)

private enum class AppScreen {
    Library,
    Reader
}

internal enum class UiLanguage {
    Turkish,
    Kurdish,
    Arabic
}

class MainActivity : Activity() {
    private lateinit var statusText: TextView
    private lateinit var documentView: TextView
    private lateinit var readerScroll: ScrollView
    private lateinit var speedText: TextView
    private lateinit var pageText: TextView
    private lateinit var readerProgress: SeekBar
    private lateinit var libraryList: LinearLayout
    private lateinit var percentText: TextView
    private lateinit var playStopButton: ImageView
    private lateinit var autoOrStopButton: ImageView
    private lateinit var voiceValueText: TextView
    private lateinit var rewindButton: ImageView
    private lateinit var forwardButton: ImageView
    private lateinit var dataStore: FilizDataStore
    private lateinit var readerPreferences: ReaderPreferences
    private val readerNoteManager = ReaderNoteManager()
    private var libraryItems: MutableList<LibraryItem> = mutableListOf()
    private var readerNotes: MutableList<ReaderNote> = mutableListOf()
    private var librarySearchQuery: String = ""
    private var selectedLibraryId: String? = null
    private var documentText: String = sampleText()
    private var documentPageCount: Int = 0
    private var visualPdfMode: Boolean = false
    private var visualPdfPageIndex: Int = 0
    private var visualPdfZoom: Float = MIN_VISUAL_PDF_ZOOM
    private lateinit var visualPdfView: ImageView
    private lateinit var visualPdfHorizontalScroll: HorizontalScrollView
    private lateinit var visualPdfScaleDetector: ScaleGestureDetector
    private var visualPdfScaling: Boolean = false
    private var readerProgressDragging: Boolean = false
    private var documentSpannable = SpannableString(documentText)
    private var documentWindowStart: Int = 0
    private var documentWindowEnd: Int = documentText.length
    private var currentHighlight: BackgroundColorSpan? = null
    private val searchResultSpans: MutableList<BackgroundColorSpan> = mutableListOf()
    private val noteSpans: MutableList<Any> = mutableListOf()
    private var highlightStart: Int = -1
    private var highlightEnd: Int = -1
    private var currentReadIndex: Int = 0
    private var textSearchQuery: String = ""
    private var textSearchLastStart: Int = -1
    private var textSearchCachedQuery: String = ""
    private var textSearchCachedTotal: Int = -1
    private val searchGeneration = AtomicInteger(0)
    private val documentOpenGeneration = AtomicInteger(0)
    private var themeMode: ThemeMode = ThemeMode.Dark
    private val isDarkMode: Boolean get() = themeMode == ThemeMode.Dark
    // Duraklatılan sesli okumanın kaldığı yer (-1: duraklatılmış okuma yok)
    private var pausedIndex: Int = -1
    private var lastSpokenStart: Int = -1
    private var pendingJumpIndex: Int = -1
    private var librarySearchActive: Boolean = false
    // Okurken kullanıcı elle kaydırırsa ekran okunan yere geri çekilmez
    private var followReading: Boolean = true
    private var readerDragStartY: Float = 0f
    private var bottomInsetTarget: View? = null
    private var bottomInsetBasePadding: Int = 0
    private var speechSpeed: Float = DEFAULT_TTS_SPEED
    private var selectedVoiceId: String = DEFAULT_TTS_VOICE.id
    private var readerFontSize: Float = 22f
    private var readerFontFamily: String = DEFAULT_READER_FONT_ID
    private var readerLineSpacing: Float = DEFAULT_READER_LINE_SPACING
    private var uiLanguage: UiLanguage = UiLanguage.Turkish
    private var sleepTimerMinutes: Int = DEFAULT_SLEEP_TIMER_MINUTES
    private var activeScreen: AppScreen = AppScreen.Library
    private var swipeStartX: Float = 0f
    private var swipeStartY: Float = 0f
    private var documentTouchDownOffset: Int = -1
    private var isReading: Boolean = false
    private var controlsExpanded: Boolean = false
    private var readerChromeVisible: Boolean = false
    private var readerSearchVisible: Boolean = false
    private val noteSelection = NoteSelectionController()
    private var noteSelectionSpan: BackgroundColorSpan? = null
    private var selectionOverlay: FrameLayout? = null
    private var selectionStartHandle: View? = null
    private var selectionEndHandle: View? = null
    private var autoScrollEnabled: Boolean = false
    private var autoScrollSpeed: Float = DEFAULT_AUTO_SCROLL_SPEED
    private var autoScrollTouchY: Float = 0f
    private var lockedOrientation: Int? = null
    private var autoScrollToast: Toast? = null
    private val fullOcrInProgress = mutableSetOf<String>()
    private val documentExecutor = Executors.newSingleThreadExecutor()
    private val autoScrollHandler = Handler(Looper.getMainLooper())
    private val readerTouchController = ReaderTouchController(Handler(Looper.getMainLooper()))
    private val ttsStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != TTS_ACTION_STATUS) return
            val status = intent.getStringExtra(TTS_EXTRA_STATUS).orEmpty()
            val reading = intent.getBooleanExtra(TTS_EXTRA_READING, false)
            val start = intent.getIntExtra(TTS_EXTRA_RANGE_START, -1)
            val end = intent.getIntExtra(TTS_EXTRA_RANGE_END, -1)
            val wasReading = isReading
            if (start >= 0) lastSpokenStart = start
            setReadingState(reading)
            if (reading && pausedIndex >= 0) {
                setPausedPosition(-1)
            } else if (wasReading && !reading) {
                // Bildirimden duraklatma ya da uyku zamanlayıcısı: kaldığı yeri hatırla
                if (status == "Okuma tamamlandı.") {
                    setPausedPosition(-1)
                } else if (lastSpokenStart >= 0) {
                    setPausedPosition(lastSpokenStart)
                }
            }
            if (start >= 0 && end > start && end <= documentText.length) {
                highlightRange(start, end)
                if (followReading || isDocumentIndexVisible(start)) {
                    followReading = true
                    smoothScrollToReadIndex(start)
                }
                refreshPlaybackControls()
            } else if (status.isBlank() && start < 0 && end < 0 && pausedIndex < 0) {
                highlightRange(-1, -1)
            }
            if (status.isNotBlank() && ::statusText.isInitialized && activeScreen == AppScreen.Reader) {
                statusText.text = if (!reading && pausedIndex >= 0 && status == "Durduruldu.") textPaused() else status
            }
        }
    }
    private val autoScrollRunnable = object : Runnable {
        override fun run() {
            if (!autoScrollEnabled || activeScreen != AppScreen.Reader || !::readerScroll.isInitialized) return
            val maxScroll = maxScrollY()
            if (readerScroll.scrollY >= maxScroll) {
                if (useWindowedDocument() && documentWindowEnd < documentText.length) {
                    currentReadIndex = documentWindowEnd.coerceIn(0, documentText.length)
                    scrollToCurrentReadIndex()
                    autoScrollHandler.postDelayed(this, 80L)
                    return
                }
                setAutoScrollEnabled(false)
                return
            }
            val step = (autoScrollSpeed * resources.displayMetrics.density * 1.7f).toInt().coerceAtLeast(1)
            readerScroll.scrollBy(0, step)
            autoScrollHandler.postDelayed(this, 50L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        readerPreferences = ReaderPreferences(applicationContext)
        dataStore = FilizDataStore(applicationContext)
        LegacyStorageMigrator(applicationContext).migrateIfNeeded(dataStore)
        readerFontSize = readerPreferences.loadReaderFontSize()
        readerFontFamily = readerPreferences.loadReaderFontFamily()
        readerLineSpacing = readerPreferences.loadReaderLineSpacing(readerFontFamily)
        uiLanguage = readerPreferences.loadUiLanguage()
        sleepTimerMinutes = readerPreferences.loadSleepTimerMinutes()
        selectedVoiceId = readerPreferences.loadSelectedVoiceId()
        if (!VoiceStore.isInstalled(this, selectedVoice())) selectedVoiceId = DEFAULT_TTS_VOICE.id
        Thread { VoiceStore.removeDownloadsOfBundledVoices(applicationContext) }.start()
        val systemIsNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        themeMode = readerPreferences.loadThemeMode(if (systemIsNight) ThemeMode.Dark else ThemeMode.Light)
        lockedOrientation = readerPreferences.loadLockedOrientation()
        libraryItems = loadLibrary().toMutableList()
        readerNotes = loadNotes().toMutableList()
        selectedLibraryId = readerPreferences.loadSelectedLibraryId()
        if (selectedLibraryId != null && libraryItems.none { it.id == selectedLibraryId }) {
            selectedLibraryId = null
        }
        activeScreen = AppScreen.Library
        readerChromeVisible = false
        applyScreenOrientation()
        setContentView(buildUi())
        setDocumentText(documentText)
        PDFBoxResourceLoader.init(applicationContext)
        Thread {
            DocumentReader.warmUpParsers()
        }.apply {
            name = "document-parser-warmup"
            priority = Thread.MIN_PRIORITY
            start()
        }
        registerTtsStatusReceiver()
        registerBackHandler()
    }

    private fun registerBackHandler() {
        // Android 13+ (ve targetSdk 36 ile Android 16+ zorunlu olarak) geri hareketi
        // onBackPressed() yerine OnBackInvokedCallback üzerinden gelir.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) { handleBack() }
        }
    }

    override fun onDestroy() {
        setAutoScrollEnabled(false)
        documentExecutor.shutdownNow()
        runCatching { unregisterReceiver(ttsStatusReceiver) }
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (activeScreen == AppScreen.Reader) {
            rebuildReader()
        } else {
            setContentView(buildUi())
            statusText.text = textLibraryStatus()
        }
    }

    private fun registerTtsStatusReceiver() {
        val filter = IntentFilter(TTS_ACTION_STATUS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(ttsStatusReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(ttsStatusReceiver, filter)
        }
    }

    private fun tr(turkish: String, kurdish: String, arabic: String): String {
        return when (uiLanguage) {
            UiLanguage.Turkish -> turkish
            UiLanguage.Kurdish -> kurdish
            UiLanguage.Arabic -> arabic
        }
    }

    private fun textLoadingModel() = tr("Model hazırlanıyor...", "Model amade dibe...", "يتم تجهيز النموذج...")
    private fun textReadyToRead() = tr("✓ Okuma hazır. Metne tıklayarak başla.", "✓ Xwendin amade ye. Bi tikandina nivîsê dest pê bike.", "✓ القراءة جاهزة. ابدأ بالنقر على النص.")
    private fun textSelectDocument() = tr("📂 Bir belge seç veya dosya ekle.", "📂 Belgeyeke hilbijêre an pelê zêde bike.", "📂 اختر مستندًا أو أضف ملفًا.")
    private fun textLibraryStatus() = subtitleLibrary()
    private fun titleLibrary() = tr("Kütüphane", "Pirtûkxane", "المكتبة")
    private fun textClose() = tr("Kapat", "Bigire", "إغلاق")
    private fun textAddDocument() = tr("Belge ekle", "Belge zêde bike", "إضافة مستند")
    private fun subtitleLibrary() = if (libraryItems.isEmpty()) {
        tr("Henüz belge eklenmedi", "Hêj belge nehatiye zêdekirin", "لم تتم إضافة أي مستند بعد")
    } else if (librarySearchQuery.isNotBlank()) {
        val count = filteredLibraryItems().size
        tr("$count belge bulundu", "$count belge hatin dîtin", "تم العثور على $count مستند")
    } else {
        tr("${libraryItems.size} belge · okumaların burada saklanır", "${libraryItems.size} belge · xwendinên te li vir tên parastin", "${libraryItems.size} مستند · قراءاتك محفوظة هنا")
    }
    private fun textLibraryEmptyTitle() = tr("Kütüphanen boş", "Pirtûkxaneya te vala ye", "مكتبتك فارغة")
    private fun textLibraryEmptyBody() = tr(
        "PDF, EPUB, Word veya metin dosyası ekle; Bideng senin için sesli okusun.",
        "Bi veguhastinê dikarî PDF, EPUB, Word an jî pelê nivîsê zêde bikî.",
        "يمكنك إضافة ملف PDF أو EPUB أو Word أو نص عبر الاستيراد."
    )
    private fun textRename() = tr("✏️ Adlandır", "✏️ Nav biguherîne", "✏️ إعادة تسمية")
    private fun textDelete() = tr("🗑️ Sil", "🗑️ Jê bibe", "🗑️ حذف")
    private fun textSave() = tr("Kaydet", "Tomar bike", "حفظ")
    private fun textCancel() = tr("İptal", "Betal", "إلغاء")
    private fun textDeletePlain() = tr("Sil", "Jê bibe", "حذف")
    private fun textDeleteNoteTitle() = tr("Not silinsin mi?", "Bila not were jêbirin?", "هل تريد حذف الملاحظة؟")
    private fun textDeleteNoteMessage(note: ReaderNote) = tr(
        "\"${note.word.take(80)}\" notu silinsin mi?",
        "Bila nota \"${note.word.take(80)}\" were jêbirin?",
        "هل تريد حذف الملاحظة \"${note.word.take(80)}\"؟"
    )
    private fun textClearNotesTitle() = tr("Notlar temizlensin mi?", "Bila not paqij bibin?", "هل تريد مسح الملاحظات؟")
    private fun textClearNotesMessage(count: Int) = tr(
        "$count not silinsin mi?",
        "Bila $count not werin jêbirin?",
        "هل تريد حذف $count ملاحظات؟"
    )
    private fun dialogRenameTitle() = tr("✏️ Belgeyi Yeniden Adlandır", "✏️ Navê belgeyê nû bike", "✏️ إعادة تسمية المستند")
    private fun dialogDeleteTitle() = tr("🗑️ Belgeyi Sil", "🗑️ Belgeyê jê bibe", "🗑️ حذف المستند")
    private fun dialogDeleteMessage(name: String) = tr("$name kitaplıktan silinsin mi?", "Bila $name ji pirtûkxaneyê were jêbirin?", "هل تريد حذف $name من المكتبة؟")
    private fun textSettingsOpen() = tr("⚙️ Ayarlar açık", "⚙️ Mîheng vekirî ne", "⚙️ الإعدادات مفتوحة")
    private fun textReadingMode() = tr("📖 Okuma modu", "📖 Moda xwendinê", "📖 وضع القراءة")
    private fun textVoiceButton() = tr("🔊 Ses", "🔊 Deng", "🔊 الصوت")
    private fun textSpeechSpeedLabel(speed: String) = tr("Konuşma hızı: $speed", "Lezîna axaftinê: $speed", "سرعة النطق: $speed")
    private fun textSleepTimerLabel() = tr("Uyku: ${sleepTimerLabel()}", "Xew: ${sleepTimerLabel()}", "المؤقت: ${sleepTimerLabel()}")
    private fun textSleepTimerShortLabel() = tr("😴 ${sleepTimerLabel()}", "😴 ${sleepTimerLabel()}", "😴 ${sleepTimerLabel()}")
    private fun textSleepTimerTitle() = tr("Uyku zamanlayıcısı", "Demjimêra xewê", "مؤقت النوم")
    private fun textSleepTimerStatus() = tr("Uyku zamanlayıcısı: ${sleepTimerLabel()}", "Demjimêra xewê: ${sleepTimerLabel()}", "مؤقت النوم: ${sleepTimerLabel()}")
    private fun textSleepTimerOff() = tr("Kapalı", "Girtî", "إيقاف")
    private fun textSleepTimerMinutes(minutes: Int) = tr("$minutes dk", "$minutes deq", "$minutes د")
    private fun textReadingSpeedStatus(speed: String) = tr("⚡ Okuma hızı: $speed", "⚡ Lezîna xwendinê: $speed", "⚡ سرعة القراءة: $speed")
    private fun textAutoScrollSpeedToast(speed: String) = tr("Kaydırma hızı: $speed", "Lezîna kişandinê: $speed", "سرعة التمرير: $speed")
    private fun textAutoScrollStopped() = tr("Kaydırma durdu", "Kişandin rawestiya", "توقف التمرير")
    private fun textRotationButton() = if (lockedOrientation == null) {
        tr("Döndür", "Bizivirîne", "تدوير")
    } else {
        tr("Kilitli", "Girtî", "مقفل")
    }
    private fun textRotationAuto() = tr("Ekran döndürme açık", "Zivirandina ekranê vekirî ye", "تدوير الشاشة مفعّل")
    private fun textRotationLocked() = tr("Ekran bu yönde kilitlendi", "Ekran di vê alî de hate girtin", "تم قفل الشاشة على هذا الاتجاه")
    private fun textImportingDocument() = tr("📥 Belge kütüphaneye ekleniyor...", "📥 Belge tê zêdekirin bo pirtûkxaneyê...", "📥 جارٍ إضافة المستند إلى المكتبة...")
    private fun textDocumentReadyName(name: String) = tr("✓ $name hazır. Okumak için metne dokun.", "✓ $name amade ye. Ji bo xwendinê li nivîsê bikeve.", "✓ $name جاهز. اضغط على النص لبدء القراءة.")
    private fun textDocumentOpenFailed(message: String) = tr("⚠️ Belge açılamadı: $message", "⚠️ Belge nekarî were vekirin: $message", "⚠️ تعذر فتح المستند: $message")
    private fun textUnsupportedReadablePdf() = tr(
        "Bu PDF uygulama ile uyumlu değil. Dosyadan sesli okunabilecek metin çıkarılamadığı için açılmıyor.",
        "Ev PDF bi sepanê re lihev nayê. Ji pelê nivîsa ku bi deng were xwendin nehat derxistin, ji ber vê venabe.",
        "ملف PDF هذا غير متوافق مع التطبيق. لا يمكن فتحه لأنه لا يحتوي على نص يمكن استخراجه للقراءة الصوتية."
    )
    private fun textFilePickerTitle() = tr("Belge içe aktar", "Belgeyê têxe hundir", "استيراد مستند")
    private fun textFilePickerGuide() = tr(
        "Android dosya seçici açılacak. PDF, EPUB, Word veya metin dosyanı seç; Bideng dosyayı güvenli biçimde kitaplığına kopyalayacak.",
        "Hilbijêrê pelan ê Androidê vedibe. PDF, EPUB, Word an pelê nivîsê hilbijêre; Bideng wê ew bi ewlehî kopî bike pirtûkxaneyê.",
        "سيفتح منتقي ملفات Android. اختر PDF أو EPUB أو Word أو ملفًا نصيًا؛ سيحفظه Bideng بأمان في المكتبة."
    )
    private fun textFilePickerContinue() = tr("Dosya seç", "Pel hilbijêre", "اختيار ملف")
    private fun textDocumentMissing() = tr("⚠️ Belge dosyası bulunamadı", "⚠️ Pela belgeyê nehate dîtin", "⚠️ لم يتم العثور على ملف المستند")
    private fun textNoExportNotes() = tr("Dışa aktarılacak not yok", "Tu not ji bo derxistinê tune ye", "لا توجد ملاحظات لتصديرها")
    private fun textNotesCleared() = tr("Notlar temizlendi", "Not paqij bûn", "تم مسح الملاحظات")
    private fun textNotesSaved() = tr("Notlar Belgeler'e kaydedildi", "Notan di belgeyan de tomarkirin", "تم حفظ الملاحظات في المستندات")
    private fun textNotesSaveFailed() = tr("Notlar kaydedilemedi", "Notan nehate tomarkirin", "تعذر حفظ الملاحظات")
    private fun textSearchQueryEmpty() = tr("Aranacak kelime yaz", "Peyveke ji bo lêgerînê binivîse", "اكتب كلمة للبحث")
    private fun textSearchRunning(query: String) = tr("\"$query\" aranıyor...", "\"$query\" tê gerandin...", "جارٍ البحث عن \"$query\"...")
    private fun textSearchNotFound(query: String) = tr("\"$query\" bulunamadı", "\"$query\" nehat dîtin", "لم يتم العثور على \"$query\"")
    private fun textSearchFound(query: String, current: Int, total: Int) = tr("\"$query\" bulundu: $current / $total", "\"$query\" hat dîtin: $current / $total", "تم العثور على \"$query\": $current / $total")
    private fun textSearchFoundInLargeDocument(query: String) = tr("\"$query\" bulundu", "\"$query\" hat dîtin", "تم العثور على \"$query\"")
    private fun textNoteExists() = tr("Bu not zaten var", "Ev not jixwe heye", "هذه الملاحظة موجودة بالفعل")
    private fun textNoteAdded() = tr("Not eklendi", "Not hat zêdekirin", "تمت إضافة الملاحظة")
    private fun textNoteSelectionActive() = tr("Not seçimi: imleçleri sürükle, Not ile kaydet.", "Hilbijartina notê: nîşankeran bikişîne, bi Not tomar bike.", "تحديد الملاحظة: اسحب المؤشرين ثم احفظ بزر Not.")
    private fun textLanguageButton() = tr("🌐 Dil", "🌐 Ziman", "🌐 اللغة")
    private fun pageLabel(current: Int, total: Int) = tr("Sayfa $current / $total", "Rûpel $current / $total", "الصفحة $current / $total")
    private fun textLanguageChanged() = tr("Dil değiştirildi.", "Ziman guherî.", "تم تغيير اللغة.")
    private fun languageLabel(language: UiLanguage) = when (language) {
        UiLanguage.Turkish -> "Türkçe"
        UiLanguage.Kurdish -> "Kurdî (Kurmancî)"
        UiLanguage.Arabic -> "العربية"
    }

    private fun applyScreenOrientation() {
        requestedOrientation = lockedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_FULL_USER
    }

    private fun toggleRotationLock() {
        lockedOrientation = if (lockedOrientation == null) {
            if (isLandscape()) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            null
        }
        readerPreferences.saveLockedOrientation(lockedOrientation)
        applyScreenOrientation()
        showShortToast(if (lockedOrientation == null) textRotationAuto() else textRotationLocked())
        if (activeScreen == AppScreen.Reader) rebuildReader() else setContentView(buildUi())
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        trackPendingNoteLongPress(event)
        trackReaderDrag(event)
        if (!noteSelection.active && !noteSelection.pendingCommit) {
            if (handleAutoScrollGesture(event)) return true
        }
        return super.dispatchTouchEvent(event)
    }

    private fun trackReaderDrag(event: MotionEvent) {
        if (activeScreen != AppScreen.Reader || !isReading || !isTouchInsideReaderScroll(event)) return
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> readerDragStartY = event.rawY
            MotionEvent.ACTION_MOVE -> if (followReading && kotlin.math.abs(event.rawY - readerDragStartY) > dp(16)) {
                followReading = false
                statusText.text = tr(
                    "Buradan okumak için oynat'a dokun",
                    "Ji bo ji vir bixwînî li lêdanê bixe",
                    "اضغط تشغيل للقراءة من هنا"
                )
                refreshPlaybackControls()
            }
        }
    }

    private fun isDocumentIndexVisible(index: Int): Boolean {
        if (!::documentView.isInitialized || !::readerScroll.isInitialized || visualPdfMode) return false
        if (index < 0 || !rangeIntersectsWindow(index, index + 1)) return false
        val layout = documentView.layout ?: return false
        val local = documentToLocalOffset(index).coerceIn(0, documentSpannable.length)
        val line = layout.getLineForOffset(local)
        val top = documentView.top + documentView.totalPaddingTop + layout.getLineTop(line)
        val bottom = documentView.top + documentView.totalPaddingTop + layout.getLineBottom(line)
        val viewTop = readerScroll.scrollY
        val viewBottom = viewTop + readerScroll.height
        return bottom > viewTop && top < viewBottom
    }

    // Okuma sürerken kullanıcı başka bir yere kaydırdıysa oynat tuşu "buradan oku" olur.
    private fun readingAwayFromView(): Boolean {
        return isReading && !followReading && !(lastSpokenStart >= 0 && isDocumentIndexVisible(lastSpokenStart))
    }

    private fun trackPendingNoteLongPress(event: MotionEvent) {
        readerTouchController.trackPendingLongPress(event, dp(4), noteSelection.active)
    }

    @Deprecated("Android 12 ve altı için; yeni sürümler registerBackHandler() kullanır")
    override fun onBackPressed() {
        handleBack()
    }

    private fun handleBack() {
        when {
            noteSelection.active || noteSelection.pendingCommit -> {
                noteSelection.pendingCommit = false
                clearNoteSelection()
            }
            autoScrollEnabled -> {
                setAutoScrollEnabled(false)
                showShortToast(textAutoScrollStopped())
            }
            activeScreen == AppScreen.Reader && readerSearchVisible -> {
                readerSearchVisible = false
                rebuildReader()
            }
            activeScreen == AppScreen.Reader && controlsExpanded -> setControlsExpanded(false)
            activeScreen == AppScreen.Reader -> switchScreen(AppScreen.Library)
            librarySearchActive || librarySearchQuery.isNotBlank() -> clearLibrarySearch()
            // Kök ekranda uygulamayı kapatmak yerine arka plana al (Android 12+ davranışı).
            else -> moveTaskToBack(true)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != OPEN_DOCUMENT_REQUEST || resultCode != RESULT_OK) return

        val uris = selectedDocumentUris(data)
        if (uris.isEmpty()) return
        val openGeneration = documentOpenGeneration.incrementAndGet()
        statusText.text = textImportingDocument()

        documentExecutor.execute {
            val result = runCatching {
                val libraryIdsBeforeImport = libraryItems.map { it.id }.toSet()
                val item = importToLibrary(uris.first())
                val documentTextResult = runCatching { readDocumentText(item, openGeneration) }
                    .getOrElse { error ->
                        if (item.id !in libraryIdsBeforeImport) {
                            removeImportedLibraryItem(item)
                        }
                        throw error
                    }
                Triple(item, documentTextResult, uris.drop(1))
            }
            runOnUiThread {
                if (openGeneration != documentOpenGeneration.get()) return@runOnUiThread
                result.onSuccess { (item, documentTextResult, remainingUris) ->
                    if (isReading || pausedIndex >= 0) {
                        closePlaybackService()
                        setReadingState(false)
                    }
                    pausedIndex = -1
                    lastSpokenStart = -1
                    selectedLibraryId = item.id
                    readerPreferences.saveSelectedLibraryId(item.id)
                    currentReadIndex = item.lastIndex
                    documentPageCount = readDocumentPageCount(item)
                    visualPdfMode = documentTextResult.isVisualPdfFallback
                    visualPdfPageIndex = 0
                    visualPdfZoom = MIN_VISUAL_PDF_ZOOM
                    activeScreen = AppScreen.Reader
                    applyScreenOrientation()
                    readerChromeVisible = true
                    controlsExpanded = false
                    setContentView(buildUi())
                    setDocumentText(documentTextResult.text.ifBlank { "⚠️ Belgeden okunabilir metin bulunamadı." })
                    currentReadIndex = item.lastIndex.coerceIn(0, documentText.length)
                    updateSelectedDocumentStats(documentText.length)
                    if (visualPdfMode) {
                        renderVisualPdfPage()
                    } else {
                        scrollToCurrentReadIndex()
                    }
                    statusText.text = if (remainingUris.isNotEmpty()) {
                        tr(
                            "İlk belge hazır. ${remainingUris.size} belge arka planda ekleniyor...",
                            "Belgeya yekem amade ye. ${remainingUris.size} belge li paşxaneyê tê zêdekirin...",
                            "المستند الأول جاهز. تتم إضافة ${remainingUris.size} مستند في الخلفية..."
                        )
                    } else {
                        textDocumentReadyName(item.name)
                    }
                    importRemainingDocuments(remainingUris)
                    if (documentTextResult.isOcrPreview) {
                        startFullOcrInBackground(item)
                    }
                }.onFailure { error ->
                    statusText.text = textDocumentOpenFailed(error.message ?: "bilinmeyen hata")
                }
            }
        }
    }

    private fun buildUi(): View {
        val palette = palette()
        val reader = activeScreen == AppScreen.Reader
        bottomInsetTarget = null
        bottomInsetBasePadding = 0
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(if (reader) palette.readerBackground else palette.background)
            setOnApplyWindowInsetsListener { view, insets ->
                val left: Int
                val top: Int
                val right: Int
                val bottom: Int
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                    val ime = insets.getInsets(WindowInsets.Type.ime())
                    left = bars.left
                    top = bars.top
                    right = bars.right
                    bottom = maxOf(bars.bottom, ime.bottom)
                } else {
                    @Suppress("DEPRECATION")
                    left = insets.systemWindowInsetLeft
                    @Suppress("DEPRECATION")
                    top = insets.systemWindowInsetTop
                    @Suppress("DEPRECATION")
                    right = insets.systemWindowInsetRight
                    @Suppress("DEPRECATION")
                    bottom = insets.systemWindowInsetBottom
                }
                // Alt panel varsa gezinme çubuğunun altına kadar uzansın
                val target = bottomInsetTarget
                view.setPadding(left, top, right, if (target == null) bottom else 0)
                target?.setPadding(target.paddingLeft, target.paddingTop, target.paddingRight, bottomInsetBasePadding + bottom)
                insets
            }
        }
        window.decorView.layoutDirection = uiLayoutDirection()
        statusText = statusTextView()
        if (reader) {
            buildReaderScreen(root, palette)
        } else {
            buildLibraryScreen(root, palette)
        }
        val topColor = if (reader) palette.readerBackground else palette.background
        val bottomColor = if (reader && bottomInsetTarget != null) palette.surface else topColor
        applySystemBarStyle(palette, topColor, bottomColor)
        return root
    }

    private fun buildLibraryScreen(root: LinearLayout, palette: UiPalette) {
        val landscape = isLandscape()
        val side = dp(if (landscape) 24 else 16)
        val appBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(side, dp(10), dp(8), dp(8))
        }
        if (librarySearchActive) {
            appBar.addView(iconAction(R.drawable.ic_ui_back, textClose(), palette) { clearLibrarySearch() })
            val field = inputField(tr("Belge adı ara", "Navê belgeyê bigere", "ابحث باسم المستند"), palette, R.drawable.ic_ui_search).apply {
                setText(librarySearchQuery)
                setSelection(text.length)
                imeOptions = EditorInfo.IME_ACTION_SEARCH
                setOnEditorActionListener { view, _, _ ->
                    hideKeyboard(view)
                    true
                }
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                    override fun afterTextChanged(s: Editable?) {
                        librarySearchQuery = s?.toString().orEmpty()
                        refreshLibraryList()
                    }
                })
            }
            appBar.addView(field, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                leftMargin = dp(4)
                rightMargin = dp(8)
            })
            showKeyboard(field)
        } else {
            val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            titles.addView(textOf(titleLibrary(), if (landscape) 22f else 26f, palette.text, medium = true))
            statusText.apply {
                textSize = 13f
                setTextColor(palette.mutedText)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                text = textLibraryStatus()
            }
            titles.addView(statusText)
            appBar.addView(titles, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            appBar.addView(iconAction(R.drawable.ic_ui_search, tr("Ara", "Bigere", "بحث"), palette) {
                librarySearchActive = true
                setContentView(buildUi())
            })
            appBar.addView(iconAction(themeIconRes(), tr("Tema", "Tema", "السمة"), palette) { cycleTheme() })
            appBar.addView(iconAction(R.drawable.ic_ui_notes, tr("Notlar", "Not", "الملاحظات"), palette) { showNotesDialog() })
        }
        root.addView(appBar, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        val frame = FrameLayout(this)
        libraryList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            isVerticalScrollBarEnabled = false
            setPadding(side, dp(4), side, dp(if (librarySearchActive || libraryItems.isEmpty()) 16 else 96))
            addView(libraryList, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        frame.addView(scroll, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        if (!librarySearchActive && libraryItems.isNotEmpty()) {
            val fab = pillButton(textAddDocument(), R.drawable.ic_ui_plus, palette, Tone.Filled, heightDp = 56, horizontalPaddingDp = 22) {
                openDocumentPicker()
            }.apply {
                textSize = 15f
                elevation = dp(6).toFloat()
            }
            frame.addView(fab, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                dp(56),
                Gravity.BOTTOM or Gravity.END
            ).apply {
                rightMargin = side
                leftMargin = side
                bottomMargin = dp(16)
            })
        }
        root.addView(frame, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        refreshLibraryList()
    }

    private fun refreshLibraryList() {
        if (!::libraryList.isInitialized) return
        libraryList.removeAllViews()
        val palette = palette()
        if (libraryItems.isEmpty()) {
            libraryList.addView(buildEmptyLibrary(palette), LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            return
        }

        val searching = librarySearchActive || librarySearchQuery.isNotBlank()
        val selectedItem = libraryItems.firstOrNull { it.id == selectedLibraryId }
        if (!searching && selectedItem != null) {
            libraryList.addView(buildContinueCard(selectedItem, palette), LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) })
        }

        val visibleItems = filteredLibraryItems()
        libraryList.addView(textOf(
            if (searching) tr("Sonuçlar · ${visibleItems.size}", "Encam · ${visibleItems.size}", "النتائج · ${visibleItems.size}")
            else tr("Belgeler · ${libraryItems.size}", "Belge · ${libraryItems.size}", "المستندات · ${libraryItems.size}"),
            13f,
            palette.mutedText,
            medium = true
        ).apply { setPadding(dp(4), dp(20), dp(4), dp(10)) })

        if (visibleItems.isEmpty()) {
            libraryList.addView(textOf(
                tr("Bu aramaya uyan belge yok", "Ji bo vê lêgerînê belge tune", "لا توجد مستندات مطابقة"),
                15f,
                palette.mutedText
            ).apply {
                centerText()
                setPadding(dp(20), dp(40), dp(20), dp(40))
            }, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            return
        }

        // Yatayda iki sütunlu ızgara, dikeyde tek sütun
        val columns = if (isLandscape()) 2 else 1
        var row: LinearLayout? = null
        visibleItems.sortedByDescending { it.addedAt }.forEachIndexed { index, item ->
            if (index % columns == 0) {
                row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    isBaselineAligned = false
                }
                libraryList.addView(row, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(10) })
            }
            row?.addView(buildLibraryCard(item, palette), LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply { if (index % columns != 0) leftMargin = dp(10) })
        }
        if (columns > 1 && visibleItems.size % columns != 0) {
            row?.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f).apply { leftMargin = dp(10) })
        }
    }

    private fun buildEmptyLibrary(palette: UiPalette): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(if (isLandscape()) 24 else 80), dp(24), dp(24))
            addView(FrameLayout(this@MainActivity).apply {
                background = roundedDrawable(palette.primaryContainer, dp(48))
                addView(iconView(R.drawable.ic_ui_book, palette.onPrimaryContainer), FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER))
            }, LinearLayout.LayoutParams(dp(96), dp(96)))
            addView(textOf(textLibraryEmptyTitle(), 20f, palette.text, medium = true).apply {
                centerText()
                setPadding(0, dp(20), 0, 0)
            }, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            addView(textOf(textLibraryEmptyBody(), 14.5f, palette.mutedText).apply {
                centerText()
                setLineSpacing(0f, 1.25f)
                setPadding(dp(12), dp(8), dp(12), dp(24))
            }, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            addView(pillButton(textAddDocument(), R.drawable.ic_ui_plus, palette, Tone.Filled, heightDp = 52) {
                openDocumentPicker()
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(52)))
        }
    }

    private fun buildContinueCard(item: LibraryItem, palette: UiPalette): LinearLayout {
        val paused = readerPreferences.loadPausedPosition(item.id) >= 0
        val onContainer = palette.onPrimaryContainer
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(16), dp(14), dp(16))
            background = rippleBackground(palette.primaryContainer, dp(24).toFloat(), palette)
            isClickable = true
            setOnClickListener { openLibraryItem(item.id) }
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(textOf(
            if (paused) tr("Sesli okuma duraklatıldı", "Xwendina bi deng hate sekinandin", "القراءة الصوتية متوقفة مؤقتًا")
            else tr("Kaldığın yerden devam et", "Ji cihê ku lê mayî berdewam bike", "تابع من حيث توقفت"),
            12.5f,
            withAlpha(onContainer, 190),
            medium = true
        ))
        texts.addView(textOf(item.name, 17f, onContainer, medium = true).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(4), 0, 0)
        })
        val progressRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, 0)
        }
        progressRow.addView(
            roundedProgress(progressValue(item), 1000, palette.primary, withAlpha(onContainer, 40)),
            LinearLayout.LayoutParams(0, dp(5), 1f)
        )
        progressRow.addView(textOf(progressPercent(item), 12f, withAlpha(onContainer, 200), medium = true).apply {
            setPadding(dp(10), 0, 0, 0)
        })
        texts.addView(progressRow, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        card.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(
            iconAction(
                if (paused) R.drawable.ic_ui_play else R.drawable.ic_ui_chev_right,
                if (paused) tr("Devam et", "Berdewam bike", "متابعة") else tr("Aç", "Veke", "فتح"),
                palette,
                Tone.Filled,
                sizeDp = 52,
                iconDp = 24
            ) { openLibraryItem(item.id, autoStart = paused) },
            LinearLayout.LayoutParams(dp(52), dp(52)).apply { leftMargin = dp(14) }
        )
        return card
    }

    private fun buildLibraryCard(item: LibraryItem, palette: UiPalette): LinearLayout {
        val selected = item.id == selectedLibraryId
        val type = documentTypeLabel(item.name)
        val typeColor = documentTypeColor(type)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(14), dp(4), dp(14))
            background = rippleBackground(
                palette.surface,
                dp(20).toFloat(),
                palette,
                stroke = if (selected) withAlpha(palette.primary, 150) else palette.outline
            )
            isClickable = true
            setOnClickListener { openLibraryItem(item.id) }
        }
        card.addView(TextView(this).apply {
            text = type.take(4)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.04f
            setTextColor(typeColor)
            gravity = Gravity.CENTER
            background = roundedDrawable(withAlpha(typeColor, if (palette.isDark) 46 else 28), dp(14))
        }, LinearLayout.LayoutParams(dp(48), dp(48)))

        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, dp(4), 0)
        }
        texts.addView(textOf(item.name, 15.5f, palette.text, medium = true).apply {
            maxLines = 2
            // Yatay ızgarada yan yana kartların yüksekliği eşit kalsın
            if (isLandscape()) minLines = 2
            ellipsize = TextUtils.TruncateAt.END
        })
        texts.addView(textOf(
            "${progressPercent(item)} ${tr("okundu", "hate xwendin", "مقروء")} · ${lastReadLabel(item)}",
            12f,
            palette.mutedText
        ).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(3), 0, 0)
        })
        texts.addView(
            roundedProgress(progressValue(item), 1000, typeColor, palette.surfaceAlt),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(4)).apply { topMargin = dp(10) }
        )
        card.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        lateinit var more: ImageView
        more = iconAction(R.drawable.ic_ui_more, tr("Seçenekler", "Vebijark", "خيارات"), palette, sizeDp = 40, iconDp = 20) {
            showActionMenu(more, listOf(
                Triple(R.drawable.ic_ui_edit, tr("Yeniden adlandır", "Nav biguherîne", "إعادة تسمية"), { renameLibraryItem(item.id) }),
                Triple(R.drawable.ic_ui_trash, textDeletePlain(), { deleteLibraryItem(item.id) })
            ))
        }
        more.imageTintList = ColorStateList.valueOf(palette.mutedText)
        card.addView(more, LinearLayout.LayoutParams(dp(40), dp(40)))
        return card
    }

    private fun buildReaderScreen(root: LinearLayout, palette: UiPalette) {
        if (readerChromeVisible) {
            root.addView(buildReaderTopBar(palette), LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val documentContainer = FrameLayout(this).apply {
            setBackgroundColor(palette.readerBackground)
            clipChildren = false
            clipToPadding = false
        }

        documentView = TextView(this).apply {
            setTextColor(palette.readerText)
            val side = readerSidePadding()
            setPadding(side, dp(12), side, dp(40))
            gravity = Gravity.START
            textAlignment = View.TEXT_ALIGNMENT_TEXT_START
            includeFontPadding = true
            highlightColor = withAlpha(palette.primary, 70)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                breakStrategy = Layout.BREAK_STRATEGY_BALANCED
                hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NORMAL
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                justificationMode = Layout.JUSTIFICATION_MODE_NONE
            }
            applyReaderFontTo(this)
            setOnTouchListener { _, event -> handleDocumentTouch(event) }
        }

        readerScroll = ScrollView(this).apply {
            isFillViewport = true
            setPadding(0, 0, 0, 0)
            setOnScrollChangeListener { _, _, _, _, _ ->
                if (readerTouchController.hasPendingLongPress() && !noteSelection.active) {
                    readerTouchController.cancelPendingLongPress()
                }
                updateCurrentIndexFromScroll()
                updatePageIndicator()
                maybeTurnDocumentWindow()
                positionNoteSelectionHandles()
                if (isReading && !followReading) refreshPlaybackControls()
            }
            if (visualPdfMode) {
                visualPdfHorizontalScroll = HorizontalScrollView(this@MainActivity).apply {
                    isFillViewport = true
                    setHorizontalScrollBarEnabled(true)
                }
                visualPdfView = ImageView(this@MainActivity).apply {
                    adjustViewBounds = true
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setBackgroundColor(if (palette.isDark) Color.rgb(18, 18, 18) else Color.WHITE)
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                    setOnTouchListener { _, event -> handleVisualPdfTouch(event) }
                }
                visualPdfHorizontalScroll.addView(
                    visualPdfView,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                addView(
                    visualPdfHorizontalScroll,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            } else {
                addView(
                    documentView,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
        }
        documentContainer.addView(readerScroll, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        selectionOverlay = buildSelectionOverlay(palette)
        documentContainer.addView(selectionOverlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        root.addView(documentContainer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        if (readerChromeVisible) {
            val bottom = when {
                noteSelection.active -> buildNoteSelectionDock(palette)
                readerSearchVisible -> buildReaderSearchDock(palette)
                else -> buildReaderPanel(palette)
            }
            root.addView(bottom, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            bottomInsetTarget = bottom
            bottomInsetBasePadding = bottom.paddingBottom
        }
    }

    private fun readerTitle(): String {
        if (selectedLibraryId == null) return tr("Okuyucu", "Xwendevan", "القارئ")
        val name = currentDocumentName()
        return name.substringBeforeLast('.').ifBlank { name }
    }

    private fun buildReaderTopBar(palette: UiPalette): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(2))
            setBackgroundColor(palette.readerBackground)
        }
        bar.addView(iconAction(R.drawable.ic_ui_back, titleLibrary(), palette) { switchScreen(AppScreen.Library) })
        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), 0, dp(4), 0)
        }
        titles.addView(textOf(readerTitle(), 15.5f, palette.readerText, medium = true).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        statusText.apply {
            textSize = 12f
            setTextColor(palette.mutedText)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        titles.addView(statusText)
        bar.addView(titles, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(iconAction(R.drawable.ic_ui_notes, tr("Notlar", "Not", "الملاحظات"), palette) { showNotesDialog() })
        bar.addView(iconAction(themeIconRes(), tr("Tema", "Tema", "السمة"), palette) { cycleTheme() })
        return bar
    }

    private fun buildReaderPanel(palette: UiPalette): LinearLayout {
        val landscape = isLandscape()
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = topRoundedPanel(palette)
            setPadding(dp(16), dp(10), dp(16), dp(8))
        }
        // Yatayda ayarlar açıkken yer kazanmak için oynatma satırı gizlenir
        val settingsOnly = controlsExpanded && landscape
        if (controlsExpanded) {
            panel.addView(buildSettingsSection(palette), LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        if (!settingsOnly) {
            if (controlsExpanded) {
                panel.addView(View(this).apply { setBackgroundColor(palette.outline) }, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(1)
                ).apply {
                    topMargin = dp(14)
                    bottomMargin = dp(6)
                })
            }
            panel.addView(
                if (visualPdfMode) buildVisualPdfControls(palette) else buildTransport(palette),
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        return panel
    }

    private fun buildTransport(palette: UiPalette): LinearLayout {
        val landscape = isLandscape()
        val container = LinearLayout(this).apply {
            orientation = if (landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        container.addView(
            buildPageProgressRow(palette),
            if (landscape) LinearLayout.LayoutParams(0, dp(44), 1f)
            else LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(34))
        )
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        fun cell(view: View, sizeDp: Int) {
            val holder = FrameLayout(this)
            holder.addView(view, FrameLayout.LayoutParams(dp(sizeDp), dp(sizeDp), Gravity.CENTER))
            controls.addView(holder, if (landscape) {
                LinearLayout.LayoutParams(dp(sizeDp + 8), dp(64))
            } else {
                LinearLayout.LayoutParams(0, dp(68), 1f)
            })
        }
        cell(iconAction(R.drawable.ic_ui_tune, tr("Okuma ayarları", "Mîhengên xwendinê", "إعدادات القراءة"), palette,
            if (controlsExpanded) Tone.Container else Tone.Plain) { setControlsExpanded(!controlsExpanded) }, 44)
        cell(iconAction(R.drawable.ic_ui_search, tr("Metinde ara", "Di nivîsê de bigere", "ابحث في النص"), palette) {
            showReaderSearchBar()
        }, 44)
        rewindButton = iconAction(R.drawable.ic_ui_skip_back, tr("Önceki cümle", "Hevoka berê", "الجملة السابقة"), palette, sizeDp = 48, iconDp = 22) {
            skipSentence(-1)
        }
        cell(rewindButton, 48)
        playStopButton = iconAction(R.drawable.ic_ui_play, tr("Oynat", "Lê bide", "تشغيل"), palette, Tone.Filled, sizeDp = 60, iconDp = 28) {
            togglePlayback()
        }.apply { elevation = dp(3).toFloat() }
        cell(playStopButton, 60)
        forwardButton = iconAction(R.drawable.ic_ui_skip_fwd, tr("Sonraki cümle", "Hevoka din", "الجملة التالية"), palette, sizeDp = 48, iconDp = 22) {
            skipSentence(1)
        }
        cell(forwardButton, 48)
        autoOrStopButton = iconAction(R.drawable.ic_ui_scroll_down, tr("Otomatik kaydır", "Kişandina xweber", "التمرير التلقائي"), palette) {
            if (isReading || pausedIndex >= 0) {
                stopReadingCompletely()
            } else {
                setAutoScrollEnabled(!autoScrollEnabled)
            }
        }
        cell(autoOrStopButton, 44)
        container.addView(controls, if (landscape) {
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(8) }
        } else {
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        refreshPlaybackControls()
        return container
    }

    private fun buildVisualPdfControls(palette: UiPalette): LinearLayout {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        container.addView(buildPageProgressRow(palette), LinearLayout.LayoutParams.MATCH_PARENT, dp(34))
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        controls.addView(iconAction(R.drawable.ic_ui_tune, tr("Okuma ayarları", "Mîhengên xwendinê", "إعدادات القراءة"), palette) {
            setControlsExpanded(!controlsExpanded)
        })
        controls.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))
        controls.addView(iconAction(R.drawable.ic_ui_chev_left, tr("Önceki sayfa", "Rûpela berê", "الصفحة السابقة"), palette, Tone.Tonal, 52) {
            goToVisualPdfPage(visualPdfPageIndex - 1)
        }, LinearLayout.LayoutParams(dp(52), dp(52)))
        controls.addView(textOf("PDF", 13f, palette.mutedText, medium = true).apply {
            centerText()
        }, LinearLayout.LayoutParams(dp(64), LinearLayout.LayoutParams.WRAP_CONTENT))
        controls.addView(iconAction(R.drawable.ic_ui_chev_right, tr("Sonraki sayfa", "Rûpela din", "الصفحة التالية"), palette, Tone.Tonal, 52) {
            goToVisualPdfPage(visualPdfPageIndex + 1)
        }, LinearLayout.LayoutParams(dp(52), dp(52)))
        controls.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))
        controls.addView(View(this), LinearLayout.LayoutParams(dp(44), dp(44)))
        container.addView(controls, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(64)))
        return container
    }

    private fun buildPageProgressRow(palette: UiPalette): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            pageText = textOf(pageLabel(1, 1), 12f, palette.mutedText, medium = true)
            addView(pageText)
            readerProgress = styledSeekBar(palette).apply {
                max = 1
                progress = 0
                setPadding(dp(14), 0, dp(14), 0)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (!fromUser || !::readerScroll.isInitialized) return
                        if (visualPdfMode) {
                            goToVisualPdfPage(progress)
                            return
                        }
                        goToDocumentProgress(progress, seekBar?.max ?: 1000)
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {
                        readerProgressDragging = true
                    }

                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        readerProgressDragging = false
                        updatePageIndicator()
                    }
                })
            }
            addView(readerProgress, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
            percentText = textOf("%0", 12f, palette.mutedText, medium = true)
            addView(percentText)
        }
    }

    private fun buildSettingsSection(palette: UiPalette): LinearLayout {
        val landscape = isLandscape()
        val section = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            textOf(tr("Okuma ayarları", "Mîhengên xwendinê", "إعدادات القراءة"), 15f, palette.text, medium = true),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        header.addView(iconAction(R.drawable.ic_ui_chev_down, textClose(), palette, Tone.Tonal, 36, 20) {
            setControlsExpanded(false)
        }, LinearLayout.LayoutParams(dp(36), dp(36)))
        section.addView(header, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        val columns = LinearLayout(this).apply {
            orientation = if (landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val voiceRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(10), dp(10))
            background = rippleBackground(palette.surfaceAlt, dp(18).toFloat(), palette)
            isClickable = true
            setOnClickListener { showVoiceDialog() }
        }
        voiceRow.addView(iconView(R.drawable.ic_ui_voice, palette.primary), LinearLayout.LayoutParams(dp(22), dp(22)))
        val voiceTexts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, dp(8), 0)
        }
        voiceTexts.addView(textOf(tr("Ses", "Deng", "الصوت"), 12f, palette.mutedText))
        voiceValueText = textOf(selectedVoice().label, 15f, palette.text, medium = true).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        voiceTexts.addView(voiceValueText)
        voiceRow.addView(voiceTexts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        voiceRow.addView(iconView(R.drawable.ic_ui_chev_right, palette.mutedText), LinearLayout.LayoutParams(dp(20), dp(20)))
        left.addView(voiceRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10) })

        val speedHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(12), dp(4), 0)
        }
        speedHeader.addView(
            textOf(tr("Konuşma hızı", "Lezîna axaftinê", "سرعة النطق"), 13f, palette.mutedText, medium = true),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        speedText = textOf(speedLabel(), 13f, palette.primary, medium = true)
        speedHeader.addView(speedText)
        left.addView(speedHeader, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        left.addView(styledSeekBar(palette).apply {
            max = ((MAX_TTS_SPEED - MIN_TTS_SPEED) * 100).toInt()
            progress = ((speechSpeed - MIN_TTS_SPEED) * 100).toInt().coerceIn(0, max)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    speechSpeed = (MIN_TTS_SPEED + progress / 100f).coerceIn(MIN_TTS_SPEED, MAX_TTS_SPEED)
                    speedText.text = speedLabel()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                // Hız, kaydırma bitince bir kez uygulanır; her adımda okumayı yeniden başlatmaz
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    setSpeed(speechSpeed)
                }
            })
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(36)))

        val tiles = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val locked = lockedOrientation != null
        listOf(
            settingTile(tr("Görünüm", "Xuyang", "المظهر"), R.drawable.ic_ui_type, palette) { showAppearanceDialog() },
            settingTile(tr("Dil", "Ziman", "اللغة"), R.drawable.ic_ui_globe, palette) { showLanguageDialog() },
            settingTile(sleepTimerLabel(), R.drawable.ic_ui_timer, palette) { showSleepTimerDialog() },
            settingTile(textRotationButton(), if (locked) R.drawable.ic_ui_lock else R.drawable.ic_ui_rotate, palette, active = locked) {
                toggleRotationLock()
            }
        ).forEachIndexed { index, tile ->
            tiles.addView(tile, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (index > 0) leftMargin = dp(8)
            })
        }
        right.addView(tiles, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10) })

        if (landscape) {
            columns.addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            columns.addView(right, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(16) })
        } else {
            columns.addView(left, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            columns.addView(right, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        section.addView(columns, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        return section
    }

    private fun buildReaderSearchDock(palette: UiPalette): LinearLayout {
        val input = inputField(tr("Metinde ara", "Di nivîsê de bigere", "ابحث في النص"), palette, R.drawable.ic_ui_search).apply {
            setText(textSearchQuery)
            setSelection(text.length)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _, actionId, event ->
                val isSearchAction = actionId == EditorInfo.IME_ACTION_SEARCH ||
                    actionId == EditorInfo.IME_ACTION_DONE ||
                    actionId == EditorInfo.IME_ACTION_GO
                val isEnterKey = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP
                if (isSearchAction || isEnterKey) {
                    findAndGoTo(text.toString(), restart = true)
                    hideKeyboard(this)
                    clearFocus()
                    true
                } else {
                    false
                }
            }
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = topRoundedPanel(palette)
            setPadding(dp(14), dp(12), dp(8), dp(12))
        }
        panel.addView(input, LinearLayout.LayoutParams(0, dp(48), 1f))
        panel.addView(iconAction(R.drawable.ic_ui_chev_up, tr("Önceki sonuç", "Encama berê", "النتيجة السابقة"), palette) {
            val query = input.text.toString().ifBlank { textSearchQuery }
            findAndGoTo(query, restart = false, direction = -1)
        }, LinearLayout.LayoutParams(dp(44), dp(44)).apply { leftMargin = dp(4) })
        panel.addView(iconAction(R.drawable.ic_ui_chev_down, tr("Sonraki sonuç", "Encama din", "النتيجة التالية"), palette) {
            val query = input.text.toString().ifBlank { textSearchQuery }
            findAndGoTo(query, restart = false, direction = 1)
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        panel.addView(iconAction(R.drawable.ic_ui_close, textClose(), palette) {
            hideKeyboard(input)
            readerSearchVisible = false
            rebuildReader()
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        if (textSearchQuery.isBlank()) showKeyboard(input)
        return panel
    }

    private fun buildNoteSelectionDock(palette: UiPalette): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = topRoundedPanel(palette)
            setPadding(dp(18), dp(12), dp(12), dp(12))
            addView(textOf(
                tr("Tutamaçları sürükleyerek seçimi ayarla", "Ji bo sererastkirina hilbijartinê destikan bikişîne", "اسحب المقبضين لضبط التحديد"),
                13f,
                palette.mutedText
            ).apply { maxLines = 2 }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(pillButton(textCancel(), null, palette, Tone.Plain, heightDp = 44, horizontalPaddingDp = 14) {
                clearNoteSelection()
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(44)))
            addView(pillButton(tr("Not ekle", "Not zêde bike", "أضف ملاحظة"), R.drawable.ic_ui_note_add, palette, Tone.Filled, heightDp = 44, horizontalPaddingDp = 18) {
                commitNoteSelection()
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(44)).apply { leftMargin = dp(4) })
        }
    }

    private fun handleGlobalSwipe(event: MotionEvent): Boolean {
        if (noteSelection.active || noteSelection.pendingCommit) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swipeStartX = event.rawX
                swipeStartY = event.rawY
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.rawX - swipeStartX
                val dy = event.rawY - swipeStartY
                if (activeScreen == AppScreen.Reader && swipeStartY > window.decorView.height - dp(230)) {
                    if (dy < -dp(55)) {
                        setControlsExpanded(true)
                        return true
                    }
                    if (dy > dp(55)) {
                        setControlsExpanded(false)
                        return true
                    }
                    return false
                }
            }
        }
        return false
    }

    private fun handleAutoScrollGesture(event: MotionEvent): Boolean {
        if (!autoScrollEnabled || activeScreen != AppScreen.Reader || noteSelection.active || noteSelection.pendingCommit) return false
        if (!isTouchInsideReaderScroll(event)) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                autoScrollTouchY = event.rawY
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = event.rawY - autoScrollTouchY
                if (kotlin.math.abs(dy) >= dp(20)) {
                    if (dy < 0) {
                        setAutoScrollSpeed(autoScrollSpeed + 0.2f)
                    } else {
                        setAutoScrollSpeed(autoScrollSpeed - 0.2f)
                    }
                    autoScrollTouchY = event.rawY
                    showShortToast(textAutoScrollSpeedToast(autoSpeedLabel()))
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> return true
        }
        return true
    }

    private fun isTouchInsideReaderScroll(event: MotionEvent): Boolean {
        if (!::readerScroll.isInitialized) return false
        val location = IntArray(2)
        readerScroll.getLocationOnScreen(location)
        val left = location[0].toFloat()
        val top = location[1].toFloat()
        val right = left + readerScroll.width
        val bottom = top + readerScroll.height
        return event.rawX in left..right && event.rawY in top..bottom
    }

    private fun switchScreen(screen: AppScreen) {
        if (activeScreen == screen) {
            if (screen == AppScreen.Reader && (controlsExpanded || readerSearchVisible || readerChromeVisible)) {
                val savedIndex = currentReadIndex
                controlsExpanded = false
                readerSearchVisible = false
                readerChromeVisible = true
                setContentView(buildUi())
                setDocumentText(documentText)
                currentReadIndex = savedIndex.coerceIn(0, documentText.length)
                scrollToCurrentReadIndex()
                statusText.text = textReadingMode()
            }
            return
        }
        // Uygulama yeniden açıldığında son belge henüz yüklenmemişse onu aç
        val pendingId = selectedLibraryId
        if (screen == AppScreen.Reader && documentText.isBlank() && pendingId != null) {
            openLibraryItem(pendingId)
            return
        }
        saveSelectedProgress()
        activeScreen = screen
        if (screen != AppScreen.Reader) {
            visualPdfMode = false
        }
        applyScreenOrientation()
        if (screen == AppScreen.Library) {
            setAutoScrollEnabled(false)
            if (isReading) pauseReading()
            librarySearchActive = false
        }
        if (screen == AppScreen.Reader) {
            readerChromeVisible = true
            controlsExpanded = false
        }
        setContentView(buildUi())
        if (screen == AppScreen.Reader) {
            val savedIndex = currentReadIndex
            setDocumentText(documentText)
            currentReadIndex = savedIndex.coerceIn(0, documentText.length)
            if (visualPdfMode) renderVisualPdfPage() else scrollToCurrentReadIndex()
            statusText.text = selectedLibraryId?.let { textReadyToRead() } ?: textSelectDocument()
        } else {
            statusText.text = textLibraryStatus()
        }
    }

    private fun clearLibrarySearch() {
        librarySearchActive = false
        librarySearchQuery = ""
        hideKeyboard(window.decorView)
        setContentView(buildUi())
    }

    private fun filteredLibraryItems(): List<LibraryItem> {
        val query = librarySearchQuery.trim()
        if (query.isBlank()) return libraryItems
        return libraryItems.filter { it.name.contains(query, ignoreCase = true) }
    }

    private fun documentTypeLabel(name: String): String {
        val lower = name.lowercase(Locale.US)
        return when {
            lower.endsWith(".pdf") -> "PDF"
            lower.endsWith(".doc") || lower.endsWith(".docx") -> "WORD"
            lower.endsWith(".epub") -> "EPUB"
            lower.endsWith(".odt") -> "ODT"
            lower.endsWith(".txt") -> "TXT"
            lower.endsWith(".html") || lower.endsWith(".htm") -> "HTML"
            else -> "DOC"
        }
    }

    private fun progressValue(item: LibraryItem): Int {
        val total = item.textLength.coerceAtLeast(1)
        return ((item.lastIndex.coerceAtLeast(0).toFloat() / total) * 1000).toInt().coerceIn(0, 1000)
    }

    private fun progressPercent(item: LibraryItem): String {
        return "%${(progressValue(item) / 10).coerceIn(0, 100)}"
    }

    private fun lastReadLabel(item: LibraryItem): String {
        if (item.lastReadAt <= 0L) return tr("henüz açılmadı", "hêj nehatiye vekirin", "لم يُفتح بعد")
        val format = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
        return format.format(Date(item.lastReadAt))
    }

    private fun renameLibraryItem(id: String) {
        val item = libraryItems.firstOrNull { it.id == id } ?: return
        val palette = palette()
        val input = inputField(tr("Belge adı", "Navê belgeyê", "اسم المستند"), palette).apply {
            setText(item.name)
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        val save: () -> Boolean = {
            val newName = safeDocumentName(input.text.toString().trim())
            if (newName.isNotBlank()) {
                val index = libraryItems.indexOfFirst { it.id == id }
                if (index >= 0) {
                    libraryItems[index] = libraryItems[index].copy(name = newName)
                    saveLibrary()
                    refreshLibraryList()
                }
            }
            hideKeyboard(input)
            true
        }
        val dialog = showModernDialog(
            dialogRenameTitle(),
            content = input,
            actions = listOf(
                DialogAction(textCancel(), ActionStyle.Text) {
                    hideKeyboard(input)
                    true
                },
                DialogAction(textSave(), ActionStyle.Filled, save)
            )
        )
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                save()
                dialog.dismiss()
                true
            } else {
                false
            }
        }
        // Uzantı hariç adı seçili getir, yazmaya hemen başlanabilsin
        val dot = item.name.lastIndexOf('.')
        input.setSelection(0, if (dot > 0) dot else item.name.length)
        showKeyboard(input)
    }

    private fun deleteLibraryItem(id: String) {
        val item = libraryItems.firstOrNull { it.id == id } ?: return
        showModernDialog(
            dialogDeleteTitle(),
            message = dialogDeleteMessage(item.name),
            actions = listOf(
                DialogAction(textCancel(), ActionStyle.Text),
                DialogAction(textDeletePlain(), ActionStyle.Danger) {
                    runCatching { File(item.path).delete() }
                    deleteDocumentTextCaches(item.id)
                    libraryItems.removeAll { it.id == id }
                    if (selectedLibraryId == id) {
                        if (isReading || pausedIndex >= 0) {
                            closePlaybackService()
                            setReadingState(false)
                        }
                        setPausedPosition(-1)
                        selectedLibraryId = null
                        documentText = sampleText()
                        documentPageCount = 0
                        currentReadIndex = 0
                        readerPreferences.saveSelectedLibraryId("")
                    }
                    saveLibrary()
                    setContentView(buildUi())
                    true
                }
            )
        )
    }

    private fun buildSelectionOverlay(palette: UiPalette): FrameLayout {
        val overlay = FrameLayout(this).apply {
            isClickable = false
            isFocusable = false
            clipChildren = false
            clipToPadding = false
        }
        selectionStartHandle = selectionHandleView(palette.primary, isStartHandle = true)
        selectionEndHandle = selectionHandleView(palette.primary, isStartHandle = false)
        overlay.addView(selectionStartHandle)
        overlay.addView(selectionEndHandle)
        overlay.post { positionNoteSelectionHandles() }
        return overlay
    }

    private fun selectionHandleView(color: Int, isStartHandle: Boolean): View {
        return View(this).apply {
            background = roundedDrawable(color, dp(9))
            alpha = 1.0f
            visibility = if (noteSelection.active && noteSelection.start >= 0 && noteSelection.end > noteSelection.start) View.VISIBLE else View.GONE
            setOnTouchListener { _, event ->
                handleSelectionHandleTouch(event, isStartHandle)
            }
        }
    }

    private fun handleSelectionHandleTouch(event: MotionEvent, isStartHandle: Boolean): Boolean {
        if (!noteSelection.active || !::documentView.isInitialized) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                readerScroll.requestDisallowInterceptTouchEvent(true)
                documentView.parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                readerScroll.requestDisallowInterceptTouchEvent(true)
                documentView.parent?.requestDisallowInterceptTouchEvent(true)
                val offset = offsetForRawPosition(event.rawX, event.rawY)
                if (isStartHandle) {
                    moveNoteSelectionStart(offset)
                } else {
                    moveNoteSelectionEnd(offset)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                readerScroll.requestDisallowInterceptTouchEvent(false)
                documentView.parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return true
    }

    private fun offsetForRawPosition(rawX: Float, rawY: Float): Int {
        val location = IntArray(2)
        documentView.getLocationOnScreen(location)
        val localX = rawX - location[0]
        val localY = rawY - location[1]
        return localToDocumentOffset(documentView.getOffsetForPosition(localX, localY))
            .coerceIn(0, documentText.length)
    }

    private fun positionNoteSelectionHandles() {
        val overlay = selectionOverlay ?: return
        val startHandle = selectionStartHandle ?: return
        val endHandle = selectionEndHandle ?: return
        if (!noteSelection.active || noteSelection.start < 0 || noteSelection.end <= noteSelection.start) {
            startHandle.visibility = View.GONE
            endHandle.visibility = View.GONE
            return
        }
        val layout = documentView.layout ?: return
        if (!rangeIntersectsWindow(noteSelection.start, noteSelection.end)) {
            startHandle.visibility = View.GONE
            endHandle.visibility = View.GONE
            return
        }
        val startOffset = documentToLocalOffset(noteSelection.start).coerceIn(0, documentSpannable.length)
        val endOffset = documentToLocalOffset(noteSelection.end).coerceIn(0, documentSpannable.length)
        val startLine = layout.getLineForOffset(startOffset)
        val endLine = layout.getLineForOffset((endOffset - 1).coerceAtLeast(startOffset))
        val startX = (documentView.totalPaddingLeft + layout.getPrimaryHorizontal(startOffset) - dp(9)).toInt()
        val startY = (documentView.top + layout.getLineTop(startLine) - readerScroll.scrollY - dp(4)).toInt()
        val endX = (documentView.totalPaddingLeft + layout.getPrimaryHorizontal(endOffset) - dp(9)).toInt()
        val endY = (documentView.top + layout.getLineBottom(endLine) - readerScroll.scrollY - dp(4)).toInt()
        placeSelectionHandle(overlay, startHandle, startX, startY)
        placeSelectionHandle(overlay, endHandle, endX, endY)
    }

    private fun placeSelectionHandle(
        overlay: FrameLayout,
        handle: View,
        left: Int,
        top: Int
    ) {
        val maxLeft = (overlay.width - dp(18)).coerceAtLeast(0)
        val maxTop = (overlay.height - dp(36)).coerceAtLeast(0)
        val safeLeft = left.coerceIn(0, maxLeft)
        val safeTop = top.coerceIn(0, maxTop)
        val lp = handle.layoutParams as? FrameLayout.LayoutParams ?: FrameLayout.LayoutParams(dp(18), dp(36))
        lp.width = dp(18)
        lp.height = dp(36)
        lp.leftMargin = safeLeft
        lp.topMargin = safeTop
        handle.layoutParams = lp
        handle.visibility = View.VISIBLE
    }

    private fun hideKeyboard(view: View) {
        val manager = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        manager?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun setControlsExpanded(expanded: Boolean) {
        if (controlsExpanded == expanded) return
        val savedIndex = currentReadIndex
        readerChromeVisible = true
        controlsExpanded = expanded
        setContentView(buildUi())
        setDocumentText(documentText)
        currentReadIndex = savedIndex.coerceIn(0, documentText.length)
        if (visualPdfMode) renderVisualPdfPage(false) else scrollToCurrentReadIndex()
    }

    private fun setReaderChromeVisible(visible: Boolean) {
        if (activeScreen != AppScreen.Reader || readerChromeVisible == visible) return
        val savedIndex = currentReadIndex
        readerChromeVisible = visible
        if (!visible) controlsExpanded = false
        setContentView(buildUi())
        setDocumentText(documentText)
        currentReadIndex = savedIndex.coerceIn(0, documentText.length)
        scrollToCurrentReadIndex()

    }

    private fun readerStatusText(readerText: String): String {
        return if (activeScreen == AppScreen.Library) textLibraryStatus() else readerText
    }

    private fun roundedDrawable(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius.toFloat()
            setColor(color)
        }
    }

    // ---------- Arayüz yardımcıları ----------

    private val mediumTypeface: Typeface by lazy { Typeface.create("sans-serif-medium", Typeface.NORMAL) }

    private enum class Tone { Plain, Tonal, Filled, Container, Danger }

    private enum class ActionStyle { Text, Filled, Danger, DangerText }

    // onClick true dönerse diyalog kapanır
    private class DialogAction(val label: String, val style: ActionStyle, val onClick: () -> Boolean = { true })

    private fun toneColors(tone: Tone, palette: UiPalette): Pair<Int, Int> = when (tone) {
        Tone.Plain -> Color.TRANSPARENT to palette.text
        Tone.Tonal -> palette.surfaceAlt to palette.text
        Tone.Filled -> palette.primary to palette.onPrimary
        Tone.Container -> palette.primaryContainer to palette.onPrimaryContainer
        Tone.Danger -> palette.danger to Color.WHITE
    }

    private fun rippleBackground(
        fill: Int,
        radiusPx: Float,
        palette: UiPalette,
        stroke: Int = Color.TRANSPARENT,
        strokeWidthDp: Int = 1
    ): RippleDrawable {
        val content = GradientDrawable().apply {
            cornerRadius = radiusPx
            setColor(fill)
            if (stroke != Color.TRANSPARENT) setStroke(dp(strokeWidthDp), stroke)
        }
        val mask = GradientDrawable().apply {
            cornerRadius = radiusPx
            setColor(Color.WHITE)
        }
        val rippleColor = if (fill == palette.primary || fill == palette.danger) {
            withAlpha(Color.WHITE, 70)
        } else {
            withAlpha(palette.text, if (palette.isDark) 46 else 30)
        }
        return RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask)
    }

    private fun topRoundedPanel(palette: UiPalette): GradientDrawable {
        val r = dp(26).toFloat()
        return GradientDrawable().apply {
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            setColor(palette.surface)
            setStroke(dp(1), palette.outline)
        }
    }

    private fun uiLayoutDirection(): Int {
        return if (uiLanguage == UiLanguage.Arabic) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
    }

    private fun textOf(value: CharSequence?, sizeSp: Float, color: Int, medium: Boolean = false): TextView {
        return TextView(this).apply {
            // Hizalama metnin diline göre değil arayüz yönüne göre (Arapça arayüzde sağa)
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            text = value
            textSize = sizeSp
            setTextColor(color)
            if (medium) typeface = mediumTypeface
        }
    }

    private fun TextView.centerText() {
        gravity = Gravity.CENTER
        textAlignment = View.TEXT_ALIGNMENT_CENTER
    }

    private fun statusTextView(): TextView {
        // Durum mesajlarının başındaki emojiler sade görünüm için atılır
        return object : TextView(this) {
            override fun setText(text: CharSequence?, type: TextView.BufferType?) {
                super.setText(cleanLabel(text), type)
            }
        }.apply { textAlignment = View.TEXT_ALIGNMENT_VIEW_START }
    }

    private fun iconView(iconRes: Int, tint: Int): ImageView {
        return ImageView(this).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(tint)
        }
    }

    private fun iconAction(
        iconRes: Int,
        description: String,
        palette: UiPalette,
        tone: Tone = Tone.Plain,
        sizeDp: Int = 44,
        iconDp: Int = 22,
        onClick: () -> Unit
    ): ImageView {
        val (fill, tint) = toneColors(tone, palette)
        return ImageView(this).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(tint)
            scaleType = ImageView.ScaleType.FIT_CENTER
            val pad = dp((sizeDp - iconDp) / 2)
            setPadding(pad, pad, pad, pad)
            background = rippleBackground(fill, dp(sizeDp) / 2f, palette)
            contentDescription = description
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
        }
    }

    private fun pillButton(
        label: String,
        iconRes: Int?,
        palette: UiPalette,
        tone: Tone,
        heightDp: Int = 44,
        horizontalPaddingDp: Int = 20,
        onClick: () -> Unit
    ): TextView {
        val (fill, tint) = toneColors(tone, palette)
        val contentColor = if (tone == Tone.Plain) palette.primary else tint
        return TextView(this).apply {
            text = label
            textSize = 14.5f
            typeface = mediumTypeface
            gravity = Gravity.CENTER
            maxLines = 1
            setTextColor(contentColor)
            setPadding(dp(if (iconRes != null) horizontalPaddingDp - 4 else horizontalPaddingDp), 0, dp(horizontalPaddingDp), 0)
            minHeight = dp(heightDp)
            if (iconRes != null) {
                val drawable = getDrawable(iconRes)?.mutate()?.apply {
                    setBounds(0, 0, dp(20), dp(20))
                    setTint(contentColor)
                }
                setCompoundDrawablesRelative(drawable, null, null, null)
                compoundDrawablePadding = dp(8)
            }
            background = rippleBackground(fill, dp(heightDp) / 2f, palette)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    private fun settingTile(
        label: String,
        iconRes: Int,
        palette: UiPalette,
        active: Boolean = false,
        onClick: () -> Unit
    ): LinearLayout {
        val fill = if (active) palette.primaryContainer else palette.surfaceAlt
        val tint = if (active) palette.onPrimaryContainer else palette.text
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(12), dp(4), dp(10))
            background = rippleBackground(fill, dp(18).toFloat(), palette)
            isClickable = true
            isFocusable = true
            contentDescription = label
            setOnClickListener { onClick() }
            addView(iconView(iconRes, tint), LinearLayout.LayoutParams(dp(22), dp(22)))
            addView(textOf(label, 12f, tint, medium = true).apply {
                centerText()
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(6), 0, 0)
            }, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun roundedProgress(value: Int, maxValue: Int, color: Int, track: Int): ProgressBar {
        return ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            val radius = dp(3).toFloat()
            val background = GradientDrawable().apply {
                cornerRadius = radius
                setColor(track)
            }
            val fill = ClipDrawable(
                GradientDrawable().apply {
                    cornerRadius = radius
                    setColor(color)
                },
                Gravity.START,
                ClipDrawable.HORIZONTAL
            )
            progressDrawable = LayerDrawable(arrayOf(background, fill)).apply {
                setId(0, android.R.id.background)
                setId(1, android.R.id.progress)
            }
            max = maxValue
            progress = value
        }
    }

    private fun styledSeekBar(palette: UiPalette): SeekBar {
        return SeekBar(this).apply {
            progressTintList = ColorStateList.valueOf(palette.primary)
            thumbTintList = ColorStateList.valueOf(palette.primary)
            progressBackgroundTintList = ColorStateList.valueOf(palette.mutedText)
            splitTrack = false
        }
    }

    private fun inputField(hintText: String, palette: UiPalette, iconRes: Int? = null): EditText {
        return EditText(this).apply {
            hint = hintText
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            textSize = 16f
            setTextColor(palette.text)
            setHintTextColor(palette.mutedText)
            highlightColor = withAlpha(palette.primary, 90)
            val radius = dp(16).toFloat()
            val normal = GradientDrawable().apply {
                cornerRadius = radius
                setColor(palette.surfaceAlt)
            }
            val focused = GradientDrawable().apply {
                cornerRadius = radius
                setColor(palette.surfaceAlt)
                setStroke(dp(2), palette.primary)
            }
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), focused)
                addState(intArrayOf(), normal)
            }
            setPadding(dp(16), dp(12), dp(16), dp(12))
            if (iconRes != null) {
                val drawable = getDrawable(iconRes)?.mutate()?.apply {
                    setBounds(0, 0, dp(20), dp(20))
                    setTint(palette.mutedText)
                }
                setCompoundDrawablesRelative(drawable, null, null, null)
                compoundDrawablePadding = dp(10)
            }
        }
    }

    private fun showKeyboard(view: View) {
        view.requestFocus()
        view.postDelayed({
            val manager = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            manager?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        }, 120L)
    }

    private fun showModernDialog(
        title: String,
        message: CharSequence? = null,
        content: View? = null,
        actions: List<DialogAction> = emptyList(),
        scrollable: Boolean = false
    ): Dialog {
        val palette = palette()
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = uiLayoutDirection()
            background = roundedDrawable(palette.surface, dp(28))
            setPadding(dp(24), dp(22), dp(24), dp(16))
        }
        card.addView(
            textOf(cleanLabel(title), 20f, palette.text, medium = true),
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        if (!message.isNullOrBlank()) {
            card.addView(textOf(message, 14.5f, palette.mutedText).apply {
                setLineSpacing(0f, 1.25f)
                setPadding(0, dp(10), 0, 0)
            }, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        if (content != null) {
            val holder: View = if (scrollable) {
                ScrollView(this).apply {
                    isVerticalScrollBarEnabled = false
                    addView(content)
                }
            } else {
                content
            }
            // Ağırlık sayesinde uzun içerik ekrana sığacak kadar daralır, butonlar görünür kalır
            card.addView(holder, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply { topMargin = dp(16) })
        }
        if (actions.isNotEmpty()) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                setPadding(0, dp(18), 0, 0)
            }
            actions.forEachIndexed { index, action ->
                val tone = when (action.style) {
                    ActionStyle.Filled -> Tone.Filled
                    ActionStyle.Danger -> Tone.Danger
                    else -> Tone.Plain
                }
                val button = pillButton(action.label, null, palette, tone, heightDp = 42, horizontalPaddingDp = 16) {}
                if (action.style == ActionStyle.DangerText) button.setTextColor(palette.danger)
                button.setOnClickListener { if (action.onClick()) dialog.dismiss() }
                row.addView(button, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(42)
                ).apply { if (index > 0) leftMargin = dp(6) })
            }
            card.addView(row, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        dialog.setContentView(card)
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val width = (resources.displayMetrics.widthPixels - dp(32)).coerceAtMost(dp(460))
            setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
            setDimAmount(0.5f)
        }
        return dialog
    }

    private fun showChoiceDialog(
        title: String,
        options: List<String>,
        selected: Int,
        subtitles: List<String?> = emptyList(),
        onSelect: (Int) -> Unit
    ) {
        val palette = palette()
        lateinit var dialog: Dialog
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        options.forEachIndexed { index, option ->
            val isSelected = index == selected
            val textColor = if (isSelected) palette.onPrimaryContainer else palette.text
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(13), dp(14), dp(13))
                background = rippleBackground(
                    if (isSelected) palette.primaryContainer else Color.TRANSPARENT,
                    dp(16).toFloat(),
                    palette
                )
                isClickable = true
                setOnClickListener {
                    dialog.dismiss()
                    onSelect(index)
                }
            }
            val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(textOf(option, 15.5f, textColor, medium = isSelected))
            subtitles.getOrNull(index)?.takeIf { it.isNotBlank() }?.let {
                texts.addView(textOf(it, 12.5f, if (isSelected) withAlpha(textColor, 190) else palette.mutedText).apply {
                    setPadding(0, dp(2), 0, 0)
                })
            }
            row.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            if (isSelected) {
                row.addView(iconView(R.drawable.ic_ui_check, textColor), LinearLayout.LayoutParams(dp(22), dp(22)))
            }
            list.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { if (index > 0) topMargin = dp(4) })
        }
        dialog = showModernDialog(
            title,
            content = list,
            actions = listOf(DialogAction(textCancel(), ActionStyle.Text)),
            scrollable = true
        )
    }

    private fun showActionMenu(anchor: View, actions: List<Triple<Int, String, () -> Unit>>) {
        val palette = palette()
        lateinit var popup: PopupWindow
        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = uiLayoutDirection()
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        actions.forEachIndexed { index, (iconRes, title, action) ->
            // Son eylem (ör. Sil) tehlikeli renkte gösterilir
            val color = if (index == actions.lastIndex && actions.size > 1) palette.danger else palette.text
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumWidth = dp(200)
                setPadding(dp(14), 0, dp(20), 0)
                background = rippleBackground(Color.TRANSPARENT, dp(14).toFloat(), palette)
                isClickable = true
                setOnClickListener {
                    popup.dismiss()
                    action()
                }
            }
            row.addView(iconView(iconRes, color), LinearLayout.LayoutParams(dp(20), dp(20)))
            row.addView(textOf(title, 15f, color).apply { setPadding(dp(14), 0, 0, 0) })
            menu.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)))
        }
        popup = PopupWindow(menu, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            setBackgroundDrawable(GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(palette.surface)
                setStroke(dp(1), palette.outline)
            })
            elevation = dp(10).toFloat()
            isOutsideTouchable = true
        }
        popup.showAsDropDown(anchor, 0, -dp(6), Gravity.END)
    }

    private fun themeIconRes(): Int = when (themeMode) {
        ThemeMode.Light -> R.drawable.ic_ui_sun
        ThemeMode.Sepia -> R.drawable.ic_ui_sepia
        ThemeMode.Dark -> R.drawable.ic_ui_moon
    }

    private fun themeName(mode: ThemeMode): String = when (mode) {
        ThemeMode.Light -> tr("Açık", "Ronî", "فاتح")
        ThemeMode.Sepia -> tr("Sepya", "Sepya", "سيبيا")
        ThemeMode.Dark -> tr("Koyu", "Tarî", "داكن")
    }

    private fun cycleTheme() {
        val next = when (themeMode) {
            ThemeMode.Light -> ThemeMode.Sepia
            ThemeMode.Sepia -> ThemeMode.Dark
            ThemeMode.Dark -> ThemeMode.Light
        }
        setThemeMode(next)
        showShortToast(tr("Tema: ${themeName(next)}", "Tema: ${themeName(next)}", "السمة: ${themeName(next)}"))
    }

    private fun setThemeMode(mode: ThemeMode) {
        if (themeMode == mode) return
        themeMode = mode
        readerPreferences.saveThemeMode(mode)
        if (activeScreen == AppScreen.Reader) {
            rebuildReader()
        } else {
            setContentView(buildUi())
        }
    }

    private fun palette(): UiPalette {
        return paletteForTheme(themeMode)
    }

    private fun applySystemBarStyle(palette: UiPalette, topColor: Int, bottomColor: Int) {
        // Android 15+ uygulamayı kenardan kenara çizer; renkler kök görünümden gelir
        if (Build.VERSION.SDK_INT < 35) {
            @Suppress("DEPRECATION")
            window.statusBarColor = topColor
            @Suppress("DEPRECATION")
            window.navigationBarColor = bottomColor
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        val light = !palette.isDark
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.decorView
            window.insetsController?.setSystemBarsAppearance(if (light) mask else 0, mask)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val lightBarFlags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0
            val decorView = window.decorView
            @Suppress("DEPRECATION")
            decorView.systemUiVisibility = if (light) {
                decorView.systemUiVisibility or lightBarFlags
            } else {
                decorView.systemUiVisibility and lightBarFlags.inv()
            }
        }
    }

    private fun togglePlayback() {
        when {
            // Okurken aşağı/yukarı kaydırıldıysa: ekranda görünen yerden okumaya başla
            readingAwayFromView() -> speakFromCurrent()
            isReading -> pauseReading()
            // Duraklatılan cümle ekrandaysa oradan devam; değilse ekranda görünen yerden başla
            pausedIndex >= 0 && isDocumentIndexVisible(pausedIndex) -> speakFrom(pausedIndex)
            else -> speakFromCurrent()
        }
    }

    private fun speakFromCurrent() {
        speakFrom(visibleTopWordStart())
    }

    private fun speakFrom(index: Int) {
        if (documentText.isBlank()) return
        if (autoScrollEnabled) {
            setAutoScrollEnabled(false)
        }
        currentReadIndex = index.coerceIn(0, documentText.length)
        lastSpokenStart = currentReadIndex
        followReading = true
        setPausedPosition(-1)
        saveSelectedProgress()
        setReadingState(true)
        statusText.text = tr("Okuma başlıyor...", "Xwendin dest pê dike...", "تبدأ القراءة...")
        TtsPlaybackRequestStore.save(
            this,
            TtsPlaybackRequest(
                text = documentText,
                startIndex = currentReadIndex,
                speed = speechSpeed,
                voiceId = selectedVoiceId,
                title = shortDocumentName(currentDocumentName()),
                sleepTimerMinutes = sleepTimerMinutes
            )
        )
        requestPlaybackNotificationPermissionIfNeeded()
        val intent = Intent(this, TtsPlaybackService::class.java).setAction(TTS_ACTION_START)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    // Okumayı durdurur ama okunan cümleyi hatırlar; oynat tuşu tam oradan devam eder.
    private fun pauseReading() {
        val position = if (lastSpokenStart >= 0) lastSpokenStart else currentReadIndex
        runCatching { startService(Intent(this, TtsPlaybackService::class.java).setAction(TTS_ACTION_STOP)) }
        setReadingState(false)
        setPausedPosition(position)
        statusText.text = textPaused()
        highlightPausedSentence()
    }

    // Duraklatılan cümle vurgulu kalır; kullanıcı nereden devam edeceğini görür.
    private fun highlightPausedSentence() {
        autoScrollHandler.postDelayed({
            if (isReading || pausedIndex < 0 || documentText.isBlank()) return@postDelayed
            val end = sentenceStartAfter(pausedIndex).coerceAtLeast(pausedIndex + 1).coerceAtMost(documentText.length)
            highlightRange(pausedIndex, end)
        }, 250L)
    }

    // Tamamen durdurur: kaldığı yer unutulur, bildirim kapanır.
    private fun stopReadingCompletely() {
        closePlaybackService()
        setReadingState(false)
        setPausedPosition(-1)
        highlightRange(-1, -1)
        statusText.text = tr("Sesli okuma durduruldu", "Xwendina bi deng rawestiya", "تم إيقاف القراءة الصوتية")
    }

    private fun closePlaybackService() {
        runCatching { startService(Intent(this, TtsPlaybackService::class.java).setAction(TTS_ACTION_CLOSE)) }
    }

    private fun setPausedPosition(index: Int) {
        if (pausedIndex == index) return
        pausedIndex = index
        readerPreferences.savePausedPosition(selectedLibraryId, index)
        refreshPlaybackControls()
    }

    private fun textPaused() = tr(
        "Duraklatıldı · devam etmek için oynat'a dokun",
        "Sekinî · ji bo berdewamiyê li lêdanê bixe",
        "متوقف مؤقتًا · اضغط تشغيل للمتابعة"
    )

    private fun requestPlaybackNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) return
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 73)
    }

    private fun setReadingState(reading: Boolean) {
        isReading = reading
        refreshPlaybackControls()
        if (activeScreen == AppScreen.Library && ::statusText.isInitialized) {
            statusText.text = textLibraryStatus()
        }
    }

    private fun refreshPlaybackControls() {
        val active = isReading || pausedIndex >= 0
        if (::playStopButton.isInitialized) {
            val away = readingAwayFromView()
            playStopButton.setImageResource(if (isReading && !away) R.drawable.ic_ui_pause else R.drawable.ic_ui_play)
            playStopButton.contentDescription = when {
                away -> tr("Buradan oku", "Ji vir bixwîne", "اقرأ من هنا")
                isReading -> tr("Duraklat", "Bisekinîne", "إيقاف مؤقت")
                pausedIndex >= 0 -> tr("Devam et", "Berdewam bike", "متابعة")
                else -> tr("Oynat", "Lê bide", "تشغيل")
            }
        }
        listOfNotNull(
            if (::rewindButton.isInitialized) rewindButton else null,
            if (::forwardButton.isInitialized) forwardButton else null
        ).forEach {
            it.isEnabled = active
            it.alpha = if (active) 1f else 0.35f
        }
        if (::autoOrStopButton.isInitialized) {
            autoOrStopButton.setImageResource(if (active) R.drawable.ic_ui_stop else R.drawable.ic_ui_scroll_down)
            autoOrStopButton.contentDescription = if (active) {
                tr("Durdur", "Rawestîne", "إيقاف")
            } else {
                tr("Otomatik kaydır", "Kişandina xweber", "التمرير التلقائي")
            }
        }
    }

    // Okunan cümlenin başına göre bir önceki ya da sonraki cümleye atlar.
    private fun skipSentence(direction: Int) {
        if (documentText.isBlank()) return
        val base = when {
            lastSpokenStart >= 0 -> lastSpokenStart
            pausedIndex >= 0 -> pausedIndex
            else -> currentReadIndex
        }.coerceIn(0, documentText.length)
        val target = if (direction < 0) sentenceStartBefore(base) else sentenceStartAfter(base)
        speakFrom(target)
    }

    private fun isSentenceEnd(c: Char): Boolean = c == '.' || c == '!' || c == '?' || c == '\n' || c == '…' || c == '؟'

    private fun sentenceStartBefore(index: Int): Int {
        var i = (index - 2).coerceAtLeast(0)
        while (i > 0 && !isSentenceEnd(documentText[i - 1])) i--
        while (i < documentText.length && documentText[i].isWhitespace()) i++
        return i.coerceIn(0, documentText.length)
    }

    private fun sentenceStartAfter(index: Int): Int {
        var i = index.coerceIn(0, documentText.length)
        while (i < documentText.length && !isSentenceEnd(documentText[i])) i++
        while (i < documentText.length && (isSentenceEnd(documentText[i]) || documentText[i].isWhitespace())) i++
        return i.coerceIn(0, (documentText.length - 1).coerceAtLeast(0))
    }

    private fun changeSpeed(delta: Float) {
        setSpeed(speechSpeed + delta)
    }

    private fun setSpeed(value: Float) {
        val wasReading = isReading
        speechSpeed = value.coerceIn(MIN_TTS_SPEED, MAX_TTS_SPEED)
        if (::speedText.isInitialized) speedText.text = speedLabel()
        statusText.text = textReadingSpeedStatus(speedLabel())
        if (wasReading) {
            restartReadingWithCurrentSpeed()
        }
    }

    private fun restartReadingWithCurrentSpeed() {
        if (documentText.isBlank()) return
        speakFrom(if (lastSpokenStart >= 0) lastSpokenStart else currentReadIndex)
    }

    private fun speedLabel(): String = "${String.format(Locale.US, "%.2f", speechSpeed)}x"

    private fun sleepTimerLabel(): String {
        return if (sleepTimerMinutes <= 0) textSleepTimerOff() else textSleepTimerMinutes(sleepTimerMinutes)
    }

    private fun showReaderSearchBar() {
        if (activeScreen != AppScreen.Reader) return
        if (isReading) {
            showShortToast(tr("Okuma sırasında arama kapalı", "Dema xwendinê de lêgerîn girtî ye", "البحث مغلق أثناء القراءة"))
            return
        }
        readerChromeVisible = true
        controlsExpanded = false
        readerSearchVisible = true
        rebuildReader()
    }

    private fun showNotesDialog() {
        val notes = notesForVisibleScope()
        lateinit var dialog: Dialog
        val actions = mutableListOf<DialogAction>()
        if (notes.isNotEmpty()) {
            actions.add(DialogAction(tr("Tümünü sil", "Hemûyan jê bibe", "حذف الكل"), ActionStyle.DangerText) {
                confirmClearNotes(notes)
                true
            })
            actions.add(DialogAction(tr("Dışa aktar", "Derxe derve", "تصدير"), ActionStyle.Filled) {
                val baseName = if (activeScreen == AppScreen.Reader) {
                    currentDocumentName().substringBeforeLast('.').ifBlank { currentDocumentName() }
                } else {
                    "Bideng"
                }
                exportNotesAsTxt(notes, baseName)
                true
            })
        } else {
            actions.add(DialogAction(textClose(), ActionStyle.Text))
        }
        dialog = showModernDialog(
            tr("Notlar", "Not", "الملاحظات"),
            message = if (notes.isEmpty()) {
                tr(
                    "Henüz not yok. Okurken bir kelimeye uzun basıp seçimi ayarlayarak not ekleyebilirsin.",
                    "Hêj not tune ye. Di dema xwendinê de bi demeke dirêj li ser peyvekê bixe û not zêde bike.",
                    "لا توجد ملاحظات بعد. اضغط مطولًا على كلمة أثناء القراءة لإضافة ملاحظة."
                )
            } else {
                null
            },
            content = if (notes.isEmpty()) null else buildNotesDialogContent(
                notes,
                onOpen = { note ->
                    dialog.dismiss()
                    goToNote(note)
                },
                onDelete = { note ->
                    confirmDeleteNote(note) {
                        dialog.dismiss()
                        showNotesDialog()
                    }
                }
            ),
            actions = actions,
            scrollable = true
        )
    }

    private fun goToNote(note: ReaderNote) {
        if (activeScreen == AppScreen.Reader && note.documentId == currentDocumentId()) {
            currentReadIndex = note.start.coerceIn(0, documentText.length)
            scrollToCurrentReadIndex()
            return
        }
        if (libraryItems.any { it.id == note.documentId }) {
            pendingJumpIndex = note.start
            openLibraryItem(note.documentId)
        }
    }

    private fun buildNotesDialogContent(
        notes: List<ReaderNote>,
        onOpen: (ReaderNote) -> Unit,
        onDelete: (ReaderNote) -> Unit
    ): View {
        val palette = palette()
        val format = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val groupedNotes = if (activeScreen == AppScreen.Reader) {
            listOf(currentDocumentName() to notes.sortedBy { it.start })
        } else {
            notes.groupBy { it.documentName.ifBlank { tr("Adsız kitap", "Pirtûka bênav", "كتاب بلا اسم") } }
                .toList()
                .sortedBy { it.first.lowercase(Locale.ROOT) }
                .map { (title, grouped) -> title to grouped.sortedBy { it.start } }
        }
        groupedNotes.forEach { (title, documentNotes) ->
            if (activeScreen != AppScreen.Reader) {
                list.addView(textOf(title, 13f, palette.primary, medium = true).apply {
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(dp(4), dp(10), dp(4), dp(8))
                })
            }
            documentNotes.forEach { note ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(14), dp(12), dp(4), dp(12))
                    background = rippleBackground(palette.surfaceAlt, dp(16).toFloat(), palette)
                    isClickable = true
                    setOnClickListener { onOpen(note) }
                }
                val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                texts.addView(textOf("“${note.word.trim()}”", 14.5f, palette.text).apply {
                    maxLines = 4
                    ellipsize = TextUtils.TruncateAt.END
                    setLineSpacing(0f, 1.15f)
                })
                texts.addView(textOf(
                    "${tr("Sayfa", "Rûpel", "الصفحة")} ${note.pageNumber} · ${format.format(Date(note.createdAt))}",
                    12f,
                    palette.mutedText
                ).apply { setPadding(0, dp(6), 0, 0) })
                row.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(iconAction(R.drawable.ic_ui_trash, textDeletePlain(), palette, sizeDp = 40, iconDp = 19) {
                    onDelete(note)
                }.apply { imageTintList = ColorStateList.valueOf(palette.mutedText) }, LinearLayout.LayoutParams(dp(40), dp(40)))
                list.addView(row, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) })
            }
        }
        return list
    }

    private fun notesForVisibleScope(): List<ReaderNote> {
        return readerNoteManager.notesForScope(
            notes = readerNotes,
            documentId = selectedLibraryId ?: "sample",
            readerVisible = activeScreen == AppScreen.Reader
        )
    }

    private fun buildNotesText(notes: List<ReaderNote>): String {
        val format = SimpleDateFormat("dd.MM HH:mm", Locale("tr", "TR"))
        if (notes.isEmpty()) return ""
        return notes
            .groupBy { it.documentName.ifBlank { tr("Adsız kitap", "Pirtûka bênav", "كتاب بلا اسم") } }
            .toList()
            .sortedBy { it.first.lowercase(Locale.ROOT) }
            .joinToString("\n\n") { (title, documentNotes) ->
                "$title\n" + documentNotes
                    .sortedBy { it.start }
                    .joinToString("\n\n") { note -> noteDisplayText(note, format) }
            }
    }

    private fun noteDisplayText(note: ReaderNote, format: SimpleDateFormat): String {
        return "• ${note.word}\nsayfa ${note.pageNumber} • ${format.format(Date(note.createdAt))}"
    }

    private fun deleteNote(id: String) {
        val removed = readerNotes.removeAll { it.id == id }
        if (!removed) return
        saveNotes()
        setDocumentText(documentText)
        showShortToast(textDeletePlain())
    }

    private fun confirmDeleteNote(note: ReaderNote, afterDelete: () -> Unit) {
        showModernDialog(
            textDeleteNoteTitle(),
            message = textDeleteNoteMessage(note),
            actions = listOf(
                DialogAction(textCancel(), ActionStyle.Text),
                DialogAction(textDeletePlain(), ActionStyle.Danger) {
                    deleteNote(note.id)
                    afterDelete()
                    true
                }
            )
        )
    }

    private fun confirmClearNotes(notes: List<ReaderNote>) {
        showModernDialog(
            textClearNotesTitle(),
            message = textClearNotesMessage(notes.size),
            actions = listOf(
                DialogAction(textCancel(), ActionStyle.Text),
                DialogAction(textDeletePlain(), ActionStyle.Danger) {
                    val ids = notes.map { it.id }.toSet()
                    readerNotes.removeAll { it.id in ids }
                    saveNotes()
                    setDocumentText(documentText)
                    showShortToast(textNotesCleared())
                    true
                }
            )
        )
    }

    private fun exportNotesAsTxt(notes: List<ReaderNote>, fileBaseName: String) {
        val text = buildNotesText(notes)
        val fileName = "${sanitizeFileName(fileBaseName)}-notlar.txt"
        if (saveTextToDocuments(fileName, text)) {
            showShortToast(textNotesSaved())
        } else {
            showShortToast(textNotesSaveFailed())
        }
    }

    private fun findAndGoTo(query: String, restart: Boolean = true, direction: Int = 1): Boolean {
        if (isReading) {
            showShortToast(tr("Okuma sırasında arama kapalı", "Dema xwendinê de lêgerîn girtî ye", "البحث مغلق أثناء القراءة"))
            return false
        }
        val needle = query.trim()
        if (needle.isBlank()) {
            showShortToast(textSearchQueryEmpty())
            return false
        }
        if (restart || !needle.equals(textSearchQuery, ignoreCase = true)) {
            textSearchQuery = needle
            textSearchLastStart = -1
            textSearchCachedQuery = ""
            textSearchCachedTotal = -1
        }
        if (useWindowedDocument()) {
            findAndGoToLargeDocument(needle, direction)
            return true
        }
        val found = findTextIndex(documentText, needle, direction, textSearchLastStart, currentReadIndex)
        return applySearchResult(needle, found, showMatchCount = true)
    }

    private fun findAndGoToLargeDocument(needle: String, direction: Int) {
        val id = searchGeneration.incrementAndGet()
        val text = documentText
        val lastStart = textSearchLastStart
        val currentIndex = currentReadIndex
        statusText.text = textSearchRunning(needle)
        documentExecutor.execute {
            val found = findTextIndex(text, needle, direction, lastStart, currentIndex)
            val total = if (found >= 0) cachedOrCountTextMatchesIn(text, needle) else 0
            val current = if (found >= 0) currentTextMatchNumberFromRatio(found, total) else 0
            runOnUiThread {
                if (id != searchGeneration.get() || text !== documentText) return@runOnUiThread
                applySearchResult(needle, found, total, current)
            }
        }
    }

    private fun findTextIndex(text: String, needle: String, direction: Int, lastStart: Int, currentIndex: Int): Int {
        return if (direction >= 0) {
            val start = if (lastStart >= 0) {
                (lastStart + needle.length).coerceIn(0, text.length)
            } else {
                currentIndex.coerceIn(0, text.length)
            }
            text.indexOf(needle, startIndex = start, ignoreCase = true)
                .takeIf { it >= 0 }
                ?: text.indexOf(needle, ignoreCase = true)
        } else {
            val before = if (lastStart >= 0) {
                (lastStart - 1).coerceAtLeast(0)
            } else {
                (currentIndex - 1).coerceAtLeast(0)
            }
            text.lastIndexOf(needle, startIndex = before, ignoreCase = true)
                .takeIf { it >= 0 }
                ?: text.lastIndexOf(needle, ignoreCase = true)
        }
    }

    private fun applySearchResult(
        needle: String,
        found: Int,
        totalMatches: Int? = null,
        currentMatch: Int? = null,
        showMatchCount: Boolean = true
    ): Boolean {
        if (found < 0) {
            highlightStart = -1
            highlightEnd = -1
            clearSearchResultHighlights()
            currentHighlight?.let { documentSpannable.removeSpan(it) }
            currentHighlight = null
            if (::documentView.isInitialized) documentView.text = documentSpannable
            statusText.text = textSearchNotFound(needle)
            showShortToast(textSearchNotFound(needle))
            return false
        }
        textSearchLastStart = found
        currentReadIndex = found
        highlightRange(found, (found + needle.length).coerceAtMost(documentText.length))
        scrollToCurrentReadIndex()
        val total = totalMatches ?: cachedOrCountTextMatches(needle)
        val current = currentMatch ?: currentTextMatchNumberFromRatio(found, total)
        val message = if (showMatchCount || totalMatches != null) {
            textSearchFound(needle, current, total)
        } else {
            textSearchFoundInLargeDocument(needle)
        }
        statusText.text = message
        showShortToast(message)
        return true
    }

    private fun countTextMatches(query: String): Int {
        return countTextMatchesIn(documentText, query)
    }

    private fun cachedOrCountTextMatches(query: String): Int {
        if (query.equals(textSearchCachedQuery, ignoreCase = true) && textSearchCachedTotal >= 0) {
            return textSearchCachedTotal
        }
        val total = countTextMatches(query)
        textSearchCachedQuery = query
        textSearchCachedTotal = total
        return total
    }

    private fun cachedOrCountTextMatchesIn(text: String, query: String): Int {
        if (text === documentText && query.equals(textSearchCachedQuery, ignoreCase = true) && textSearchCachedTotal >= 0) {
            return textSearchCachedTotal
        }
        val total = countTextMatchesIn(text, query)
        if (text === documentText) {
            textSearchCachedQuery = query
            textSearchCachedTotal = total
        }
        return total
    }

    private fun countTextMatchesIn(text: String, query: String): Int {
        var count = 0
        var index = text.indexOf(query, ignoreCase = true)
        while (index >= 0) {
            count++
            index = text.indexOf(query, startIndex = index + query.length.coerceAtLeast(1), ignoreCase = true)
        }
        return count
    }

    private fun currentTextMatchNumber(query: String, found: Int): Int {
        return currentTextMatchNumberIn(documentText, query, found)
    }

    private fun currentTextMatchNumberFromRatio(found: Int, total: Int): Int {
        if (total <= 1 || documentText.isBlank()) return total.coerceAtLeast(1)
        return (((found.coerceIn(0, documentText.length) * total.toFloat()) / documentText.length)
            .toInt() + 1)
            .coerceIn(1, total)
    }

    private fun currentTextMatchNumberIn(text: String, query: String, found: Int): Int {
        var count = 0
        var index = text.indexOf(query, ignoreCase = true)
        while (index >= 0 && index <= found) {
            count++
            index = text.indexOf(query, startIndex = index + query.length.coerceAtLeast(1), ignoreCase = true)
        }
        return count.coerceAtLeast(1)
    }

    private fun goToPage(page: Int?) {
        if (page == null || !::readerScroll.isInitialized) return
        val pageHeight = readerScroll.height.coerceAtLeast(1)
        val totalPages = ((maxScrollY() + pageHeight + pageHeight - 1) / pageHeight).coerceAtLeast(1)
        val targetPage = page.coerceIn(1, totalPages)
        readerScroll.post {
            readerScroll.scrollTo(0, ((targetPage - 1) * pageHeight).coerceAtMost(maxScrollY()))
            updatePageIndicator()
            updateCurrentIndexFromScroll()
        }
    }

    private fun goToDocumentProgress(progress: Int, max: Int) {
        if (documentText.isBlank()) return
        val ratio = progress.toFloat() / max.coerceAtLeast(1)
        currentReadIndex = (documentText.length * ratio).toInt().coerceIn(0, documentText.length)
        scrollToCurrentReadIndex()
        saveSelectedProgress()
    }

    private fun showVoiceDialog() {
        val palette = palette()
        lateinit var dialog: Dialog
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        TTS_VOICES.forEachIndexed { index, voice ->
            val installed = VoiceStore.isInstalled(this, voice)
            val bundled = VoiceStore.isBundled(this, voice)
            val selected = voice.id == selectedVoiceId
            val textColor = if (selected) palette.onPrimaryContainer else palette.text
            val status = when {
                bundled -> tr("uygulamayla gelir", "bi sepanê re tê", "مضمّن في التطبيق")
                installed -> tr("indirildi", "hate daxistin", "تم التنزيل") + " · " + formatMegabytes(voice.downloadSize)
                VoiceStore.downloadingVoiceId() == voice.id -> tr("indiriliyor…", "tê daxistin…", "جارٍ التنزيل…")
                else -> tr("indirilebilir", "dikare were daxistin", "متاح للتنزيل") + " · " + formatMegabytes(voice.downloadSize)
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(12), dp(8), dp(12))
                background = rippleBackground(if (selected) palette.primaryContainer else Color.TRANSPARENT, dp(16).toFloat(), palette)
                isClickable = true
                setOnClickListener {
                    dialog.dismiss()
                    if (installed) selectVoice(voice) else confirmVoiceDownload(voice)
                }
            }
            val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(textOf(voice.label, 15.5f, textColor, medium = selected))
            texts.addView(textOf("${voiceLanguageName(voice)} · $status", 12.5f, if (selected) withAlpha(textColor, 190) else palette.mutedText).apply {
                setPadding(0, dp(2), 0, 0)
            })
            row.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            when {
                selected -> row.addView(iconView(R.drawable.ic_ui_check, textColor), LinearLayout.LayoutParams(dp(22), dp(22)).apply { rightMargin = dp(8) })
                !installed -> row.addView(iconView(R.drawable.ic_ui_download, palette.primary), LinearLayout.LayoutParams(dp(22), dp(22)).apply { rightMargin = dp(8) })
                !bundled -> row.addView(iconAction(R.drawable.ic_ui_trash, textDeletePlain(), palette, sizeDp = 40, iconDp = 19) {
                    dialog.dismiss()
                    confirmVoiceDelete(voice)
                }.apply { imageTintList = ColorStateList.valueOf(palette.mutedText) }, LinearLayout.LayoutParams(dp(40), dp(40)))
            }
            list.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { if (index > 0) topMargin = dp(4) })
        }
        list.addView(textOf(
            tr(
                "İndirilen sesler internet olmadan da çalışır.",
                "Dengên daxistî bê înternetê jî dixebitin.",
                "الأصوات التي تم تنزيلها تعمل دون إنترنت."
            ),
            12.5f,
            palette.mutedText
        ).apply { setPadding(dp(4), dp(14), dp(4), 0) })
        dialog = showModernDialog(
            tr("Ses seçimi", "Hilbijartina dengê", "اختيار الصوت"),
            content = list,
            actions = listOf(DialogAction(textClose(), ActionStyle.Text)),
            scrollable = true
        )
    }

    private fun voiceLanguageName(voice: TtsVoice): String = when (voice.language) {
        "ar" -> tr("Arapça", "Erebî", "العربية")
        "fa" -> tr("Farsça", "Farisî", "الفارسية")
        else -> tr("Türkçe", "Tirkî", "التركية")
    }

    private fun formatMegabytes(bytes: Long): String {
        return String.format(Locale.US, "%.0f MB", bytes / 1_048_576.0)
    }

    private fun selectVoice(voice: TtsVoice) {
        selectedVoiceId = voice.id
        readerPreferences.saveSelectedVoiceId(selectedVoiceId)
        if (::voiceValueText.isInitialized) {
            voiceValueText.text = voice.label
        }
        statusText.text = "${tr("Ses", "Deng", "الصوت")}: ${voice.label}"
        if (isReading) restartReadingWithCurrentSpeed()
    }

    private fun confirmVoiceDownload(voice: TtsVoice) {
        val size = formatMegabytes(voice.downloadSize)
        val meteredNote = if (VoiceStore.isMeteredConnection(this)) {
            "\n\n" + tr(
                "Mobil veri kullanıyorsun; Wi‑Fi önerilir.",
                "Tu daneyên mobîl bi kar tînî; Wi‑Fi tê pêşniyarkirin.",
                "أنت تستخدم بيانات الجوال؛ يُنصح باستخدام Wi‑Fi."
            )
        } else {
            ""
        }
        showModernDialog(
            tr("Ses indirilsin mi?", "Bila deng were daxistin?", "هل تريد تنزيل الصوت؟"),
            message = tr(
                "${voice.label} (${voiceLanguageName(voice)}) sesi yaklaşık $size indirilecek. İndirdikten sonra internet olmadan çalışır.",
                "Dengê ${voice.label} (${voiceLanguageName(voice)}) nêzîkî $size dê were daxistin. Piştî daxistinê bê înternetê dixebite.",
                "سيتم تنزيل صوت ${voice.label} (${voiceLanguageName(voice)}) بحجم $size تقريبًا. بعد التنزيل يعمل دون إنترنت."
            ) + meteredNote,
            actions = listOf(
                DialogAction(textCancel(), ActionStyle.Text),
                DialogAction(tr("İndir", "Daxe", "تنزيل"), ActionStyle.Filled) {
                    startVoiceDownload(voice)
                    true
                }
            )
        )
    }

    private fun startVoiceDownload(voice: TtsVoice) {
        val palette = palette()
        val progress = roundedProgress(0, 1000, palette.primary, palette.surfaceAlt)
        val label = textOf(tr("Bağlanıyor…", "Tê girêdan…", "جارٍ الاتصال…"), 13f, palette.mutedText).apply {
            setPadding(0, dp(10), 0, 0)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(progress, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(6)))
            addView(label)
        }
        lateinit var downloader: VoiceDownloader
        val dialog = showModernDialog(
            tr("${voice.label} indiriliyor", "${voice.label} tê daxistin", "جارٍ تنزيل ${voice.label}"),
            message = tr(
                "Bağlantı koparsa indirme sonra kaldığı yerden devam eder.",
                "Ger girêdan qut bibe, daxistin paşê ji cihê lê mayî berdewam dike.",
                "إذا انقطع الاتصال سيستأنف التنزيل لاحقًا من حيث توقف."
            ),
            content = content,
            actions = listOf(DialogAction(textCancel(), ActionStyle.Text) {
                downloader.cancel()
                true
            })
        )
        dialog.setCancelable(false)
        downloader = VoiceStore.startDownload(
            this,
            voice,
            onProgress = { downloaded, total ->
                val ratio = if (total > 0) downloaded.toDouble() / total else 0.0
                progress.progress = (ratio * 1000).toInt()
                label.text = "${formatMegabytes(downloaded)} / ${formatMegabytes(total)} · %${(ratio * 100).toInt()}"
            },
            onFinished = { result ->
                if (dialog.isShowing) dialog.dismiss()
                result.onSuccess {
                    selectVoice(voice)
                    showShortToast(tr("${voice.label} sesi hazır", "Dengê ${voice.label} amade ye", "صوت ${voice.label} جاهز"))
                }.onFailure { error ->
                    if (error is VoiceDownloadCancelled) {
                        showShortToast(tr(
                            "İndirme durduruldu; sonra kaldığı yerden devam eder",
                            "Daxistin rawestiya; paşê berdewam dike",
                            "توقف التنزيل؛ سيستأنف لاحقًا"
                        ))
                    } else {
                        showModernDialog(
                            tr("İndirme tamamlanamadı", "Daxistin temam nebû", "تعذر إكمال التنزيل"),
                            message = tr(
                                "İnternet bağlantını kontrol edip tekrar dene.",
                                "Girêdana înternetê kontrol bike û dîsa biceribîne.",
                                "تحقق من اتصال الإنترنت وحاول مرة أخرى."
                            ) + "\n\n(${error.message.orEmpty()})",
                            actions = listOf(
                                DialogAction(textClose(), ActionStyle.Text),
                                DialogAction(tr("Tekrar dene", "Dîsa biceribîne", "إعادة المحاولة"), ActionStyle.Filled) {
                                    startVoiceDownload(voice)
                                    true
                                }
                            )
                        )
                    }
                }
            }
        )
    }

    private fun confirmVoiceDelete(voice: TtsVoice) {
        showModernDialog(
            tr("Ses silinsin mi?", "Bila deng were jêbirin?", "هل تريد حذف الصوت؟"),
            message = tr(
                "${voice.label} sesi cihazdan silinecek (${formatMegabytes(voice.downloadSize)}). İstediğinde tekrar indirebilirsin.",
                "Dengê ${voice.label} dê ji cîhazê were jêbirin (${formatMegabytes(voice.downloadSize)}). Dikarî dîsa daxî.",
                "سيتم حذف صوت ${voice.label} من الجهاز (${formatMegabytes(voice.downloadSize)}). يمكنك تنزيله مجددًا لاحقًا."
            ),
            actions = listOf(
                DialogAction(textCancel(), ActionStyle.Text),
                DialogAction(textDeletePlain(), ActionStyle.Danger) {
                    VoiceStore.delete(this, voice)
                    if (selectedVoiceId == voice.id) selectVoice(DEFAULT_TTS_VOICE)
                    showVoiceDialog()
                    true
                }
            )
        )
    }

    private fun showLanguageDialog() {
        val languages = UiLanguage.entries.toTypedArray()
        val current = languages.indexOf(uiLanguage).coerceAtLeast(0)
        showChoiceDialog(
            tr("Arayüz dili", "Zimanê navrûyê", "لغة الواجهة"),
            languages.map { languageLabel(it) },
            current
        ) { which ->
            uiLanguage = languages[which]
            readerPreferences.saveUiLanguage(uiLanguage)
            if (activeScreen == AppScreen.Reader) {
                rebuildReader()
            } else {
                setContentView(buildUi())
            }
            showShortToast(textLanguageChanged())
        }
    }

    private fun showSleepTimerDialog() {
        val labels = SLEEP_TIMER_OPTIONS.map { minutes ->
            if (minutes == 0) textSleepTimerOff() else textSleepTimerMinutes(minutes)
        }
        val current = SLEEP_TIMER_OPTIONS.indexOf(sleepTimerMinutes).coerceAtLeast(0)
        showChoiceDialog(
            textSleepTimerTitle(),
            labels,
            current,
            subtitles = SLEEP_TIMER_OPTIONS.map { minutes ->
                if (minutes == 0) tr("Okuma kendiliğinden durmaz", "Xwendin bi xwe ranawestê", "لن تتوقف القراءة تلقائيًا")
                else tr("Süre dolunca okuma durur", "Dema dem biqede xwendin radiweste", "تتوقف القراءة عند انتهاء المدة")
            }
        ) { which ->
            sleepTimerMinutes = SLEEP_TIMER_OPTIONS.getOrElse(which) { DEFAULT_SLEEP_TIMER_MINUTES }
            readerPreferences.saveSleepTimerMinutes(sleepTimerMinutes)
            if (activeScreen == AppScreen.Reader) rebuildReader()
            statusText.text = textSleepTimerStatus()
        }
    }

    // Tema, yazı tipi, boyut ve satır aralığı tek bir pencerede; değişiklikler anında metne yansır.
    private fun showAppearanceDialog() {
        val palette = palette()
        lateinit var dialog: Dialog
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        fun sectionTitle(text: String, value: TextView? = null): LinearLayout {
            return LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(18), 0, dp(8))
                addView(textOf(text, 13f, palette.mutedText, medium = true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                if (value != null) addView(value)
            }
        }

        content.addView(sectionTitle(tr("Tema", "Tema", "السمة")).apply { setPadding(0, 0, 0, dp(8)) })
        val themes = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        ThemeMode.entries.forEachIndexed { index, mode ->
            val modePalette = paletteForTheme(mode)
            val selected = mode == themeMode
            val swatch = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(12), dp(6), dp(10))
                background = rippleBackground(
                    modePalette.readerBackground,
                    dp(18).toFloat(),
                    palette,
                    stroke = if (selected) palette.primary else palette.outline,
                    strokeWidthDp = if (selected) 2 else 1
                )
                isClickable = true
                setOnClickListener {
                    if (mode != themeMode) {
                        dialog.dismiss()
                        setThemeMode(mode)
                        showAppearanceDialog()
                    }
                }
                addView(textOf("Aa", 20f, modePalette.readerText, medium = true).apply { centerText() })
                addView(textOf(themeName(mode), 12f, modePalette.mutedText, medium = true).apply {
                    centerText()
                    setPadding(0, dp(2), 0, 0)
                })
            }
            themes.addView(swatch, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (index > 0) leftMargin = dp(10)
            })
        }
        content.addView(themes, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        val sizeValue = textOf("${readerFontSize.toInt()}", 13f, palette.primary, medium = true)
        content.addView(sectionTitle(tr("Yazı boyutu", "Mezinahiya nivîsê", "حجم الخط"), sizeValue))
        val sizeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        sizeRow.addView(textOf("A", 13f, palette.mutedText, medium = true))
        sizeRow.addView(styledSeekBar(palette).apply {
            max = 16
            progress = (readerFontSize.toInt() - 16).coerceIn(0, max)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    readerFontSize = (16 + progress).toFloat()
                    sizeValue.text = "${readerFontSize.toInt()}"
                    applyReaderFont()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    saveReaderFontSettings()
                    scrollToCurrentReadIndex()
                }
            })
        }, LinearLayout.LayoutParams(0, dp(36), 1f))
        sizeRow.addView(textOf("A", 21f, palette.mutedText, medium = true))
        content.addView(sizeRow, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        val spacingValue = textOf(textLineSpacingValue(), 13f, palette.primary, medium = true)
        content.addView(sectionTitle(tr("Satır aralığı", "Navbera rêzan", "تباعد الأسطر"), spacingValue))
        content.addView(styledSeekBar(palette).apply {
            max = ((MAX_READER_LINE_SPACING - MIN_READER_LINE_SPACING) * 100).toInt()
            progress = ((readerLineSpacing - MIN_READER_LINE_SPACING) * 100).toInt().coerceIn(0, max)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    readerLineSpacing = MIN_READER_LINE_SPACING + progress / 100f
                    spacingValue.text = textLineSpacingValue()
                    applyReaderFont()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    saveReaderFontSettings()
                    scrollToCurrentReadIndex()
                }
            })
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(36)))

        content.addView(sectionTitle(tr("Yazı tipi", "Curenivîs", "نوع الخط")))
        val fontChips = mutableListOf<Pair<TextView, String>>()
        fun styleChip(chip: TextView, selected: Boolean) {
            chip.background = rippleBackground(
                if (selected) palette.primaryContainer else palette.surfaceAlt,
                dp(18).toFloat(),
                palette
            )
            chip.setTextColor(if (selected) palette.onPrimaryContainer else palette.text)
        }
        val chipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        READER_FONT_OPTIONS.forEachIndexed { index, option ->
            val chip = TextView(this).apply {
                text = option.label
                textSize = 14f
                typeface = Typeface.create(option.id, Typeface.NORMAL)
                gravity = Gravity.CENTER
                setPadding(dp(16), 0, dp(16), 0)
                isClickable = true
                setOnClickListener {
                    readerFontFamily = option.id
                    readerLineSpacing = readerLineSpacing.coerceAtLeast(option.lineSpacingMultiplier)
                    saveReaderFontSettings()
                    applyReaderFont()
                    fontChips.forEach { (view, id) -> styleChip(view, id == readerFontFamily) }
                }
            }
            styleChip(chip, option.id == readerFontOption(readerFontFamily).id)
            fontChips.add(chip to option.id)
            chipRow.addView(chip, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(38)).apply {
                if (index > 0) leftMargin = dp(8)
            })
        }
        content.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(chipRow)
        }, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        dialog = showModernDialog(
            tr("Görünüm", "Xuyang", "المظهر"),
            content = content,
            actions = listOf(DialogAction(tr("Tamam", "Temam", "تم"), ActionStyle.Filled)),
            scrollable = true
        )
    }

    private fun selectedVoice(): TtsVoice {
        return TTS_VOICES.firstOrNull { it.id == selectedVoiceId } ?: DEFAULT_TTS_VOICE
    }

    private fun applyReaderFont() {
        if (::documentView.isInitialized) {
            applyReaderFontTo(documentView)
        }
    }

    private fun applyReaderFontTo(view: TextView) {
        val option = readerFontOption(readerFontFamily)
        view.textSize = readerFontSize
        view.typeface = Typeface.create(option.id, Typeface.NORMAL)
        view.setLineSpacing(option.lineSpacingExtra, readerLineSpacing)
    }

    private fun textLineSpacingValue(): String {
        return "${(readerLineSpacing * 100).toInt()}%"
    }

    private fun findNearbyWordStart(text: String, offset: Int): Int {
        if (text.isBlank()) return 0
        var i = offset.coerceIn(0, text.length - 1)
        while (i > 0 && !text[i - 1].isWhitespace()) i--
        return i.coerceIn(0, text.length)
    }

    private fun setAutoScrollEnabled(enabled: Boolean) {
        if (enabled && isReading) {
            showShortToast(tr("Sesli okuma açıkken otomatik kaydırma başlamaz", "Dema xwendina bi deng vekirî be kişandina xweber dest pê nake", "لا يبدأ التمرير التلقائي أثناء القراءة الصوتية"))
            return
        }
        autoScrollEnabled = enabled
        autoScrollHandler.removeCallbacks(autoScrollRunnable)
        if (enabled) {
            readerChromeVisible = false
            controlsExpanded = false
            statusText.text = tr("⏺️ Otomatik kaydırma: ${autoSpeedLabel()}", "⏺️ Kişandina xweber: ${autoSpeedLabel()}", "⏺️ التمرير التلقائي: ${autoSpeedLabel()}")
            if (activeScreen == AppScreen.Reader) {
                rebuildReader()
            }
            autoScrollHandler.postDelayed(autoScrollRunnable, 80L)
            return
        } else if (::statusText.isInitialized) {
            readerChromeVisible = true
            controlsExpanded = false
            statusText.text = tr("⏸️ Otomatik kaydırma kapalı", "⏸️ Kişandina xweber girtî ye", "⏸️ التمرير التلقائي متوقف")
        }
        rebuildReaderChrome()
    }

    private fun setAutoScrollSpeed(value: Float) {
        autoScrollSpeed = value.coerceIn(MIN_AUTO_SCROLL_SPEED, MAX_AUTO_SCROLL_SPEED)
        if (activeScreen == AppScreen.Reader && autoScrollEnabled) {
            statusText.text = tr("⏺️ Kaydırma hızı: ${autoSpeedLabel()}", "⏺️ Lezîna kişandinê: ${autoSpeedLabel()}", "⏺️ سرعة التمرير: ${autoSpeedLabel()}")
        }
    }

    private fun autoScrollIcon(): String = if (autoScrollEnabled) "⏸" else "⏬"

    private fun autoSpeedLabel(): String = "${String.format(Locale.US, "%.1f", autoScrollSpeed)}x"

    private fun showShortToast(message: String) {
        autoScrollToast?.cancel()
        autoScrollToast = Toast.makeText(this, message, Toast.LENGTH_SHORT).also { it.show() }
    }

    private fun rebuildReaderChrome() {
        if (activeScreen != AppScreen.Reader || !readerChromeVisible || controlsExpanded) return
        rebuildReader()
    }

    private fun rebuildReader() {
        val savedIndex = currentReadIndex
        val savedScrollY = if (::readerScroll.isInitialized) readerScroll.scrollY else 0
        setContentView(buildUi())
        setDocumentText(documentText)
        currentReadIndex = savedIndex.coerceIn(0, documentText.length)
        if (visualPdfMode) {
            renderVisualPdfPage(false)
        } else if (useWindowedDocument()) {
            scrollToCurrentReadIndex()
        } else if (::readerScroll.isInitialized) {
            readerScroll.post {
                readerScroll.scrollTo(0, savedScrollY.coerceIn(0, maxScrollY()))
                updatePageIndicator()
            }
        }
    }

    private fun updatePageIndicator() {
        if (!::readerScroll.isInitialized || !::pageText.isInitialized) return
        if (visualPdfMode) {
            val totalPages = documentPageCount.coerceAtLeast(1)
            val currentPage = (visualPdfPageIndex + 1).coerceIn(1, totalPages)
            val percent = if (totalPages <= 1) 0 else (((currentPage - 1) * 100f) / (totalPages - 1)).toInt().coerceIn(0, 100)
            pageText.text = pageLabel(currentPage, totalPages)
            if (::percentText.isInitialized) percentText.text = "%$percent"
            if (::readerProgress.isInitialized && !readerProgressDragging) {
                readerProgress.max = (totalPages - 1).coerceAtLeast(1)
                readerProgress.progress = visualPdfPageIndex.coerceIn(0, readerProgress.max)
            }
            return
        }
        val progressPermille = if (documentText.isBlank()) {
            0
        } else {
            ((currentReadIndex.coerceIn(0, documentText.length) * 1000f) / documentText.length).toInt().coerceIn(0, 1000)
        }
        val percent = (progressPermille / 10f).toInt().coerceIn(0, 100)
        val estimatedTotalPages = estimatedReaderPages().coerceAtLeast(1)
        val estimatedCurrentPage = ((progressPermille * estimatedTotalPages.toFloat()) / 1000f)
            .toInt()
            .plus(1)
            .coerceIn(1, estimatedTotalPages)
        pageText.text = pageLabel(estimatedCurrentPage, estimatedTotalPages)
        if (::percentText.isInitialized) percentText.text = "%$percent"
        if (::readerProgress.isInitialized && !readerProgressDragging) {
            readerProgress.max = 1000
            readerProgress.progress = progressPermille.coerceIn(0, readerProgress.max)
        }
    }

    private fun updateCurrentIndexFromScroll() {
        if (visualPdfMode) return
        if (!::documentView.isInitialized || !::readerScroll.isInitialized) return
        currentReadIndex = visibleTopWordStart()
        saveSelectedProgress()
    }

    private fun goToVisualPdfPage(pageIndex: Int) {
        if (!visualPdfMode) return
        val maxIndex = (documentPageCount - 1).coerceAtLeast(0)
        visualPdfPageIndex = pageIndex.coerceIn(0, maxIndex)
        renderVisualPdfPage()
    }

    private fun setVisualPdfZoom(zoom: Float) {
        if (!visualPdfMode) return
        val nextZoom = zoom.coerceIn(MIN_VISUAL_PDF_ZOOM, MAX_VISUAL_PDF_ZOOM)
        if (kotlin.math.abs(nextZoom - visualPdfZoom) < 0.01f) return
        visualPdfZoom = nextZoom
        renderVisualPdfPage(resetScroll = false)
    }

    private fun handleVisualPdfTouch(event: MotionEvent): Boolean {
        if (!::visualPdfScaleDetector.isInitialized) {
            visualPdfScaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    visualPdfScaling = true
                    return true
                }

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    setVisualPdfZoom(visualPdfZoom * detector.scaleFactor)
                    return true
                }

                override fun onScaleEnd(detector: ScaleGestureDetector) {
                    visualPdfScaling = false
                }
            })
        }
        visualPdfScaleDetector.onTouchEvent(event)
        if (event.pointerCount > 1 || visualPdfScaling) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swipeStartX = event.rawX
                swipeStartY = event.rawY
                return true
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.rawX - swipeStartX
                val dy = event.rawY - swipeStartY
                if (kotlin.math.abs(dx) > dp(72) && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.4f) {
                    if (dx < 0) goToVisualPdfPage(visualPdfPageIndex + 1) else goToVisualPdfPage(visualPdfPageIndex - 1)
                    return true
                }
            }
        }
        return visualPdfZoom > MIN_VISUAL_PDF_ZOOM
    }

    private fun renderVisualPdfPage() {
        renderVisualPdfPage(resetScroll = true)
    }

    private fun renderVisualPdfPage(resetScroll: Boolean) {
        if (!visualPdfMode || !::visualPdfView.isInitialized) return
        val item = selectedLibraryId?.let { id -> libraryItems.firstOrNull { it.id == id } } ?: return
        val file = File(item.path)
        if (!file.exists()) return
        val baseWidth = (resources.displayMetrics.widthPixels - dp(32)).coerceAtLeast(dp(280))
        val targetWidth = (baseWidth * visualPdfZoom).toInt().coerceIn(dp(280), dp(2400))
        val bitmap = runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    val pageIndex = visualPdfPageIndex.coerceIn(0, (renderer.pageCount - 1).coerceAtLeast(0))
                    visualPdfPageIndex = pageIndex
                    documentPageCount = renderer.pageCount
                    renderer.openPage(pageIndex).use { page ->
                        val scale = targetWidth.toFloat() / page.width.coerceAtLeast(1)
                        val targetHeight = (page.height * scale).toInt().coerceAtLeast(1)
                        Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }
            }
        }.getOrElse { error ->
            statusText.text = textDocumentOpenFailed(error.message ?: "PDF sayfası görüntülenemedi")
            return
        }
        visualPdfView.layoutParams = visualPdfView.layoutParams.apply {
            width = bitmap.width
            height = bitmap.height
        }
        visualPdfView.setImageBitmap(bitmap)
        readerScroll.post {
            if (resetScroll) {
                readerScroll.scrollTo(0, 0)
                if (::visualPdfHorizontalScroll.isInitialized) visualPdfHorizontalScroll.scrollTo(0, 0)
            }
            updatePageIndicator()
        }
        statusText.text = tr(
            "PDF görüntüleme modu: ${visualPdfPageIndex + 1}/${documentPageCount.coerceAtLeast(1)} • ${(visualPdfZoom * 100).toInt()}%",
            "Moda dîtina PDF: ${visualPdfPageIndex + 1}/${documentPageCount.coerceAtLeast(1)} • ${(visualPdfZoom * 100).toInt()}%",
            "وضع عرض PDF: ${visualPdfPageIndex + 1}/${documentPageCount.coerceAtLeast(1)} • ${(visualPdfZoom * 100).toInt()}%"
        )
    }

    private fun visibleTopWordStart(): Int {
        if (!::readerScroll.isInitialized || !::documentView.isInitialized || documentText.isBlank()) {
            return currentReadIndex.coerceIn(0, documentText.length)
        }
        val layout = documentView.layout ?: return currentReadIndex.coerceIn(0, documentText.length)
        val vertical = (readerScroll.scrollY + documentView.totalPaddingTop + dp(6)).coerceAtLeast(0)
        val line = layout.getLineForVertical(vertical)
        val lineStart = localToDocumentOffset(layout.getLineStart(line)).coerceIn(0, documentText.length)
        return findNearbyWordStart(documentText, lineStart)
    }

    private fun maybeTurnDocumentWindow() {
        if (!useWindowedDocument() || !::readerScroll.isInitialized) return
        if (readerScroll.scrollY >= maxScrollY() - dp(48) && documentWindowEnd < documentText.length) {
            currentReadIndex = documentWindowEnd.coerceIn(0, documentText.length)
            scrollToCurrentReadIndex()
        } else if (readerScroll.scrollY <= dp(12) && documentWindowStart > 0) {
            currentReadIndex = (documentWindowStart - 1).coerceAtLeast(0)
            scrollToCurrentReadIndex()
        }
    }

    private fun maxScrollY(): Int {
        if (!::readerScroll.isInitialized || readerScroll.childCount == 0) return 0
        return (readerScroll.getChildAt(0).height - readerScroll.height).coerceAtLeast(0)
    }

    private fun estimatedReaderPages(): Int {
        return documentPageCount.takeIf { it > 0 } ?: ((documentText.length + 2999) / 3000).coerceAtLeast(1)
    }

    private fun openDocumentPicker() {
        if (!readerPreferences.hasSeenFilePickerGuide()) {
            readerPreferences.saveSeenFilePickerGuide()
            showModernDialog(
                textFilePickerTitle(),
                message = textFilePickerGuide(),
                actions = listOf(
                    DialogAction(textCancel(), ActionStyle.Text),
                    DialogAction(textFilePickerContinue(), ActionStyle.Filled) {
                        launchDocumentPickerIntent()
                        true
                    }
                )
            )
            return
        }
        launchDocumentPickerIntent()
    }

    private fun launchDocumentPickerIntent() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(
                    "text/plain",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/epub+zip",
                    "application/pdf",
                    "application/vnd.oasis.opendocument.text",
                    "application/rtf",
                    "text/rtf",
                    "text/html",
                    "application/msword"
                )
            )
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(intent, OPEN_DOCUMENT_REQUEST)
    }

    private fun openLibraryItem(id: String, autoStart: Boolean = false) {
        val item = libraryItems.firstOrNull { it.id == id } ?: return
        val openGeneration = documentOpenGeneration.incrementAndGet()
        librarySearchQuery = ""
        val file = File(item.path)
        if (!file.exists()) {
                statusText.text = textDocumentMissing()
            return
        }

        statusText.text = tr("📄 ${shortDocumentName(item.name)} açılıyor...", "📄 ${shortDocumentName(item.name)} tê vekirin...", "📄 جارٍ فتح ${shortDocumentName(item.name)}...")
        documentExecutor.execute {
            val result = runCatching { readDocumentText(item, openGeneration) }
            runOnUiThread {
                if (openGeneration != documentOpenGeneration.get()) return@runOnUiThread
                result.onSuccess { documentTextResult ->
                    if (item.id != selectedLibraryId && (isReading || pausedIndex >= 0)) {
                        closePlaybackService()
                        setReadingState(false)
                    }
                    selectedLibraryId = item.id
                    readerPreferences.saveSelectedLibraryId(item.id)
                    pausedIndex = readerPreferences.loadPausedPosition(item.id)
                    lastSpokenStart = -1
                    documentPageCount = readDocumentPageCount(item)
                    visualPdfMode = documentTextResult.isVisualPdfFallback
                    visualPdfPageIndex = 0
                    visualPdfZoom = MIN_VISUAL_PDF_ZOOM
                    activeScreen = AppScreen.Reader
                    applyScreenOrientation()
                    readerChromeVisible = true
                    controlsExpanded = false
                    setContentView(buildUi())
                    setDocumentText(documentTextResult.text.ifBlank { "⚠️ Belgeden okunabilir metin bulunamadı." })
                    val jumpIndex = pendingJumpIndex
                    pendingJumpIndex = -1
                    currentReadIndex = (if (jumpIndex >= 0) jumpIndex else item.lastIndex).coerceIn(0, documentText.length)
                    updateSelectedDocumentStats(documentText.length)
                    if (visualPdfMode) {
                        renderVisualPdfPage()
                    } else {
                        scrollToCurrentReadIndex()
                    }
                    statusText.text = if (pausedIndex >= 0) textPaused() else textDocumentReadyName(item.name)
                    refreshPlaybackControls()
                    if (pausedIndex >= 0) highlightPausedSentence()
                    if (documentTextResult.isOcrPreview) {
                        startFullOcrInBackground(item)
                    }
                    if (autoStart) {
                        if (pausedIndex >= 0) speakFrom(pausedIndex) else speakFromCurrent()
                    }
                }.onFailure { error ->
                    statusText.text = textDocumentOpenFailed(error.message ?: "bilinmeyen hata")
                }
            }
        }
    }

    private fun importToLibrary(uri: Uri): LibraryItem {
        val name = safeDocumentName(displayName(uri).ifBlank { "Belge" })
        val existing = libraryItems.firstOrNull {
            it.name.equals(name, ignoreCase = true) && File(it.path).exists()
        }
        if (existing != null) {
            selectedLibraryId = existing.id
            readerPreferences.saveSelectedLibraryId(existing.id)
            return existing
        }
        val id = UUID.randomUUID().toString()
        val targetDir = File(filesDir, "library").apply { mkdirs() }
        val extension = name.substringAfterLast('.', "").takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
        val target = File(targetDir, "$id$extension")
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output, bufferSize = 256 * 1024) }
        } ?: error("Dosya okunamadı")

        val item = LibraryItem(
            id = id,
            name = name,
            path = target.absolutePath,
            addedAt = System.currentTimeMillis(),
            lastIndex = 0,
            lastReadAt = 0L,
            textLength = 0
        )
        libraryItems.add(item)
        saveLibrary()
        readerPreferences.saveSelectedLibraryId(item.id)
        return item
    }

    private fun importRemainingDocuments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        documentExecutor.execute {
            val importedCount = uris.count { uri ->
                runCatching { importToLibrary(uri) }.isSuccess
            }
            runOnUiThread {
                refreshLibraryList()
                if (importedCount > 0 && activeScreen == AppScreen.Reader) {
                    statusText.text = tr(
                        "$importedCount belge daha kitaplığa eklendi",
                        "$importedCount belge din li pirtûkxaneyê hate zêdekirin",
                        "تمت إضافة $importedCount مستند إضافي إلى المكتبة"
                    )
                }
            }
        }
    }

    private fun removeImportedLibraryItem(item: LibraryItem) {
        libraryItems.removeAll { it.id == item.id }
        runCatching { File(item.path).delete() }
        deleteDocumentTextCaches(item.id)
        saveLibrary()
    }

    private fun selectedDocumentUris(data: Intent?): List<Uri> {
        val clipData = data?.clipData
        if (clipData != null && clipData.itemCount > 0) {
            return (0 until clipData.itemCount).mapNotNull { index -> clipData.getItemAt(index)?.uri }
        }
        return data?.data?.let(::listOf).orEmpty()
    }

    private fun readDocumentText(item: LibraryItem, openGeneration: Int? = null): DocumentTextResult {
        val cache = documentTextCacheFile(item.id)
        if (cache.exists()) {
            val cached = runCatching { cache.readText() }.getOrDefault("")
            if (cached.isNotBlank()) {
                if (item.name.endsWith(".pdf", ignoreCase = true) && cached.isOldVisualPdfFallbackCache()) {
                    runCatching { cache.delete() }
                } else {
                    return DocumentTextResult(cached, isOcrPreview = false)
                }
            }
        }
        val isPdf = item.name.endsWith(".pdf", ignoreCase = true)
        // Çıkarım tüm biçimlerde iyileşti; eski önbellek yalnızca dosya artık okunamıyorsa yedek olarak kullanılır.
        val legacyText = takeLegacyDocumentText(item.id)
        val reader = DocumentReader(this, ocrProgress = ocrProgressReporter(item, openGeneration))
        val text = runCatching {
            reader.read(Uri.fromFile(File(item.path)))
        }.getOrElse { error ->
            if (legacyText.isNotBlank() && !legacyText.isOldVisualPdfFallbackCache()) {
                cache.parentFile?.mkdirs()
                runCatching { cache.writeText(legacyText) }
                return DocumentTextResult(legacyText, isOcrPreview = false)
            }
            if (isPdf) throw IllegalStateException(textUnsupportedReadablePdf())
            throw error
        }
        val isOcrPreview = text.startsWith(OCR_PREVIEW_MARKER)
        val displayText = if (isOcrPreview) text.removePrefix(OCR_PREVIEW_MARKER).trimStart() else text
        if (displayText.isNotBlank() && !isOcrPreview) {
            cache.parentFile?.mkdirs()
            runCatching { cache.writeText(displayText) }
        }
        return DocumentTextResult(displayText, isOcrPreview)
    }

    /** Reads and removes a cache file written by an older cache version, or returns "" if there is none. */
    private fun takeLegacyDocumentText(id: String): String {
        val legacy = legacyDocumentTextCacheFiles(id).filter { it.exists() }
        val text = legacy.firstNotNullOfOrNull { file -> runCatching { file.readText() }.getOrNull()?.takeIf { it.isNotBlank() } }.orEmpty()
        legacy.forEach { file -> runCatching { file.delete() } }
        return text
    }

    private fun String.isOldVisualPdfFallbackCache(): Boolean {
        return startsWith("PDF görüntüleme modu") ||
            startsWith("Moda dîtina PDF") ||
            startsWith("وضع عرض PDF")
    }

    private fun ocrProgressReporter(
        item: LibraryItem,
        openGeneration: Int? = null
    ): ((currentPage: Int, totalPages: Int) -> Unit)? {
        if (!item.name.endsWith(".pdf", ignoreCase = true)) return null
        return { currentPage, totalPages ->
            if (currentPage == 1 || currentPage == totalPages || currentPage % 5 == 0) {
                runOnUiThread {
                    if (openGeneration != null && openGeneration != documentOpenGeneration.get()) return@runOnUiThread
                    if (!::statusText.isInitialized) return@runOnUiThread
                    statusText.text = tr(
                        "OCR işleniyor: $currentPage/$totalPages sayfa. Uzun kitaplarda bu zaman alabilir.",
                        "OCR tê xebitandin: rûpel $currentPage/$totalPages. Di pirtûkên dirêj de ev dikare dem bigire.",
                        "تتم معالجة OCR: الصفحة $currentPage/$totalPages. قد يستغرق ذلك وقتًا في الكتب الطويلة."
                    )
                }
            }
        }
    }

    private fun startFullOcrInBackground(item: LibraryItem) {
        if (!item.name.endsWith(".pdf", ignoreCase = true)) return
        if (!fullOcrInProgress.add(item.id)) return
        statusText.text = tr(
            "Taranmış PDF açıldı. Tam OCR arka planda hazırlanıyor...",
            "PDF-a wêneyî vebû. OCR-a tevahî li paşxaneyê tê amadekirin...",
            "تم فتح PDF ممسوح ضوئيًا. يتم تجهيز OCR الكامل في الخلفية..."
        )
        documentExecutor.execute {
            val cache = documentTextCacheFile(item.id)
            val result = runCatching {
                DocumentReader(this, ocrProgress = ocrProgressReporter(item)).readFullPdfOcr(Uri.fromFile(File(item.path))).trim()
            }
            runOnUiThread {
                fullOcrInProgress.remove(item.id)
                result.onSuccess { text ->
                    if (text.isNotBlank()) {
                        cache.parentFile?.mkdirs()
                        runCatching { cache.writeText(text) }
                        val index = libraryItems.indexOfFirst { it.id == item.id }
                        if (index >= 0) {
                            libraryItems[index] = libraryItems[index].copy(textLength = text.length)
                            dataStore.saveLibraryItem(libraryItems[index])
                        }
                        if (selectedLibraryId == item.id && activeScreen == AppScreen.Reader) {
                            statusText.text = tr(
                                "Tam OCR hazır. Belgeyi yeniden açınca tüm metin gösterilecek.",
                                "OCR-a tevahî amade ye. Dema belge dîsa veke hemû nivîs dê xuya bibe.",
                                "اكتمل OCR الكامل. سيظهر النص كاملًا عند فتح المستند مجددًا."
                            )
                        }
                    }
                }.onFailure {
                    if (selectedLibraryId == item.id && activeScreen == AppScreen.Reader) {
                        statusText.text = tr(
                            "Tam OCR tamamlanamadı.",
                            "OCR-a tevahî nehat qedandin.",
                            "تعذر إكمال OCR الكامل."
                        )
                    }
                }
            }
        }
    }

    private fun readDocumentPageCount(item: LibraryItem): Int {
        if (!item.name.endsWith(".pdf", ignoreCase = true)) return 0
        val file = File(item.path)
        if (!file.exists()) return 0
        return runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    renderer.pageCount
                }
            }
        }.getOrDefault(0)
    }

    private fun documentTextCacheFile(id: String): File {
        return File(documentTextCacheDir(), "$id.full.v$DOCUMENT_TEXT_CACHE_VERSION.txt")
    }

    private fun legacyDocumentTextCacheFiles(id: String): List<File> {
        val dir = documentTextCacheDir()
        return listOf(File(dir, "$id.full.txt")) +
            (2 until DOCUMENT_TEXT_CACHE_VERSION).map { version -> File(dir, "$id.full.v$version.txt") }
    }

    private fun deleteDocumentTextCaches(id: String) {
        (listOf(documentTextCacheFile(id)) + legacyDocumentTextCacheFiles(id)).forEach { file ->
            runCatching { file.delete() }
        }
    }

    private fun documentTextCacheDir(): File = File(filesDir, "text-cache").apply { mkdirs() }

    private fun safeDocumentName(raw: String): String {
        val clean = raw.replace(Regex("\\s+"), " ").trim().ifBlank { "Belge" }
        if (clean.length <= MAX_DOCUMENT_NAME_LENGTH) return clean
        val extension = clean.substringAfterLast('.', "").takeIf { it.isNotBlank() && it.length <= 8 }?.let { ".$it" }.orEmpty()
        val baseLimit = (MAX_DOCUMENT_NAME_LENGTH - extension.length - 1).coerceAtLeast(24)
        return clean.substring(0, baseLimit).trimEnd() + "…" + extension
    }

    private fun shortDocumentName(name: String): String = safeDocumentName(name)

    private fun saveSelectedProgress() {
        val id = selectedLibraryId ?: return
        val index = libraryItems.indexOfFirst { it.id == id }
        if (index < 0) return
        libraryItems[index] = libraryItems[index].copy(
            lastIndex = currentReadIndex,
            lastReadAt = System.currentTimeMillis(),
            textLength = documentText.length.coerceAtLeast(libraryItems[index].textLength)
        )
        dataStore.saveLibraryItem(libraryItems[index])
        readerPreferences.saveSelectedLibraryId(id)
    }

    private fun updateSelectedDocumentStats(textLength: Int) {
        val id = selectedLibraryId ?: return
        val index = libraryItems.indexOfFirst { it.id == id }
        if (index < 0) return
        libraryItems[index] = libraryItems[index].copy(
            textLength = textLength.coerceAtLeast(0),
            lastReadAt = System.currentTimeMillis()
        )
        dataStore.saveLibraryItem(libraryItems[index])
    }

    private fun saveReaderFontSettings() {
        readerPreferences.saveReaderFontSettings(readerFontSize, readerFontFamily, readerLineSpacing)
    }

    private fun saveLibrary() {
        dataStore.saveLibrary(libraryItems)
    }

    private fun loadLibrary(): List<LibraryItem> {
        return dataStore.loadLibrary().filter { File(it.path).exists() }
    }

    private fun saveNotes() {
        dataStore.saveNotes(readerNotes)
    }

    private fun loadNotes(): List<ReaderNote> {
        return dataStore.loadNotes()
    }

    private fun saveTextToDocuments(fileName: String, text: String): Boolean {
        val bytes = text.toByteArray(Charsets.UTF_8)
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/Bideng")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val uri = contentResolver.insert(collection, values) ?: error("Kayıt alanı açılamadı")
                contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(bytes)
                    output.flush()
                } ?: error("Dosya yazılamadı")
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    "Bideng"
                ).apply { mkdirs() }
                File(dir, fileName).outputStream().use { output ->
                    output.write(bytes)
                    output.flush()
                }
            }
            true
        }.getOrDefault(false)
    }

    private fun displayName(uri: Uri): String {
        runCatching {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) return cursor.getString(index)
            }
        }
        return uri.lastPathSegment.orEmpty().substringAfterLast('/')
    }

    private fun handleDocumentTouch(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            documentTouchDownOffset = if (::documentView.isInitialized) {
                localToDocumentOffset(documentView.getOffsetForPosition(event.x, event.y))
            } else {
                -1
            }
            readerTouchController.onDocumentDown(
                event = event,
                offset = documentTouchDownOffset,
                noteSelectionActive = noteSelection.active,
                noteSelectionPendingCommit = noteSelection.pendingCommit,
                canStartNoteSelection = ::canStartNoteSelectionAt,
                documentView = documentView,
                onLongPress = ::startNoteSelection
            )
            return false
        }
        if (event.action == MotionEvent.ACTION_MOVE) {
            if (noteSelection.active && ::documentView.isInitialized) {
                return false
            }
            readerTouchController.onDocumentMove(event, noteSelection.active, dp(10))
            return false
        }
        if (event.action != MotionEvent.ACTION_UP && event.action != MotionEvent.ACTION_CANCEL) return true
        readerTouchController.onDocumentUpOrCancel()
        val downOffset = documentTouchDownOffset
        documentTouchDownOffset = -1
        if (documentText.isBlank()) return true

        return runCatching {
            val layout = documentView.layout ?: return true
            val localX = event.x - documentView.totalPaddingLeft
            val localY = event.y - documentView.totalPaddingTop
            val upOffset = localToDocumentOffset(documentView.getOffsetForPosition(event.x, event.y))
            if (noteSelection.pendingCommit && noteSelection.active) {
                noteSelection.pendingCommit = false
                return true
            }
            if (noteSelection.active) {
                return true
            }
            if (isReading && isReaderDoubleTap(event)) {
                pauseReading()
                return true
            }
            val tappedWordStart = resolveTappedWordStart(upOffset, downOffset)
            if (tappedWordStart < 0) {
                setReaderChromeVisible(true)
                return true
            }
            if (tappedWordStart < documentText.length) {
                startReadingAtWord(tappedWordStart)
            }
            true
        }.getOrElse { error ->
            Log.e("Filiz", "Touch handling failed", error)
            statusText.text = tr("⚠️ Dokunma işlenemedi", "⚠️ Lêdan nehat pêvajokirin", "⚠️ تعذر معالجة اللمس")
            true
        }
    }

    private fun addNoteAtOffset(offset: Int) {
        addNoteAtSelection(offset, offset)
    }

    private fun canStartNoteSelectionAt(offset: Int): Boolean {
        return noteSelection.canStart(documentText, offset)
    }

    private fun resolveTappedWordStart(upOffset: Int, downOffset: Int): Int {
        val candidates = listOf(upOffset, downOffset)
        for (candidate in candidates) {
            if (candidate < 0 || candidate >= documentText.length) continue
            if (documentText[candidate].isWhitespace()) continue
            val wordStart = findWordStart(documentText, candidate)
            if (wordStart < documentText.length) return wordStart
        }
        return -1
    }

    private fun startReadingAtWord(offset: Int) {
        val wordStart = findWordStart(documentText, offset)
        val wordEnd = findWordEnd(documentText, wordStart)
        if (wordStart >= documentText.length || wordEnd <= wordStart) return
        readerChromeVisible = true
        controlsExpanded = false
        readerSearchVisible = false
        currentReadIndex = wordStart
        highlightRange(wordStart, wordEnd)
        scrollToCurrentReadIndex()
        speakFrom(wordStart)
    }

    private fun addNoteAtSelection(startOffset: Int, endOffset: Int) {
        when (val result = readerNoteManager.addNote(
            notes = readerNotes,
            documentText = documentText,
            documentId = currentDocumentId(),
            documentName = currentDocumentName(),
            startOffset = startOffset,
            endOffset = endOffset,
            pageNumber = currentPageNumber()
        )) {
            is AddReaderNoteResult.Added -> {
                readerNotes.add(result.note)
                saveNotes()
                setDocumentText(documentText)
                currentReadIndex = result.note.start
                scrollToCurrentReadIndex()
                readerChromeVisible = true
                controlsExpanded = false
                readerSearchVisible = false
                rebuildReaderChrome()
                showShortToast(textNoteAdded())
            }
            AddReaderNoteResult.Duplicate -> showShortToast(textNoteExists())
            AddReaderNoteResult.Invalid -> Unit
        }
    }

    private fun startNoteSelection(offset: Int) {
        val savedIndex = currentReadIndex
        val savedScrollY = if (::readerScroll.isInitialized) readerScroll.scrollY else 0
        if (noteSelection.begin(documentText, offset) == null) return
        readerChromeVisible = true
        controlsExpanded = false
        readerSearchVisible = false
        setContentView(buildUi())
        setDocumentText(documentText, preserveNoteSelection = true)
        currentReadIndex = savedIndex.coerceIn(0, documentText.length)
        if (::statusText.isInitialized) statusText.text = textNoteSelectionActive()
        if (::readerScroll.isInitialized) {
            readerScroll.post {
                readerScroll.scrollTo(0, savedScrollY.coerceIn(0, maxScrollY()))
                positionNoteSelectionHandles()
            }
        }
        positionNoteSelectionHandles()
    }

    private fun updateNoteSelection(offset: Int) {
        if (noteSelection.update(documentText, offset) == null) return
        applyNoteSelection()
        if (::documentView.isInitialized) {
            documentView.text = documentSpannable
            documentView.postInvalidateOnAnimation()
        }
        positionNoteSelectionHandles()
    }

    private fun moveNoteSelectionStart(offset: Int) {
        if (noteSelection.moveStart(documentText, offset) == null) return
        refreshActiveNoteSelection()
    }

    private fun moveNoteSelectionEnd(offset: Int) {
        if (noteSelection.moveEnd(documentText, offset) == null) return
        refreshActiveNoteSelection()
    }

    private fun refreshActiveNoteSelection() {
        applyNoteSelection()
        if (::documentView.isInitialized) {
            documentView.text = documentSpannable
            documentView.postInvalidateOnAnimation()
        }
        positionNoteSelectionHandles()
    }

    private fun commitNoteSelection() {
        val range = noteSelection.commitRange(documentText)
        if (range == null) {
            clearNoteSelection()
            return
        }
        clearNoteSelection(rebuild = false)
        addNoteAtSelection(range.first, range.last + 1)
    }

    private fun clearNoteSelection(rebuild: Boolean = true) {
        readerTouchController.cancelPendingLongPress()
        noteSelection.clear()
        noteSelectionSpan?.let { documentSpannable.removeSpan(it) }
        noteSelectionSpan = null
        selectionStartHandle?.visibility = View.GONE
        selectionEndHandle?.visibility = View.GONE
        if (::documentView.isInitialized) {
            documentView.invalidate()
        }
        if (rebuild) {
            rebuildReaderChrome()
        }
    }

    private fun applyNoteSelection() {
        noteSelectionSpan?.let { documentSpannable.removeSpan(it) }
        noteSelectionSpan = null
        val start = noteSelection.start
        val end = noteSelection.end
        if (start >= 0 && end > start && rangeIntersectsWindow(start, end)) {
            val localStart = documentToLocalOffset(start).coerceIn(0, documentSpannable.length)
            val localEnd = documentToLocalOffset(end).coerceIn(0, documentSpannable.length)
            if (localEnd <= localStart) return
            val color = if (isDarkMode) {
                Color.argb(170, Color.red(LEMON_HIGHLIGHT), Color.green(LEMON_HIGHLIGHT), Color.blue(LEMON_HIGHLIGHT))
            } else {
                Color.argb(125, Color.red(LEMON_HIGHLIGHT), Color.green(LEMON_HIGHLIGHT), Color.blue(LEMON_HIGHLIGHT))
            }
            noteSelectionSpan = BackgroundColorSpan(color).also {
                documentSpannable.setSpan(it, localStart, localEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (::documentView.isInitialized) {
                documentView.text = documentSpannable
                documentView.postInvalidateOnAnimation()
            }
            positionNoteSelectionHandles()
        }
    }

    private fun currentDocumentId(): String = selectedLibraryId ?: "sample"

    private fun currentPageNumber(): Int {
        if (!::readerScroll.isInitialized) return 1
        if (useWindowedDocument()) {
            return (documentWindowStart / DOCUMENT_WINDOW_CHARS + 1).coerceAtLeast(1)
        }
        val pageHeight = readerScroll.height.coerceAtLeast(1)
        val totalPages = ((maxScrollY() + pageHeight + pageHeight - 1) / pageHeight).coerceAtLeast(1)
        return (readerScroll.scrollY / pageHeight + 1).coerceIn(1, totalPages)
    }

    private fun currentDocumentName(): String {
        return selectedLibraryId
            ?.let { id -> libraryItems.firstOrNull { it.id == id }?.name }
            ?: getString(R.string.app_name)
    }

    private fun sanitizeFileName(name: String): String {
        return name
            .replace(Regex("[\\\\/:*?\"<>|]+"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { getString(R.string.app_name) }
    }

    private fun isReaderDoubleTap(event: MotionEvent): Boolean {
        return readerTouchController.isDoubleTap(event, dp(32))
    }

    private fun useWindowedDocument(): Boolean = documentText.length > LARGE_DOCUMENT_THRESHOLD

    private fun localToDocumentOffset(offset: Int): Int {
        return (documentWindowStart + offset).coerceIn(0, documentText.length)
    }

    private fun documentToLocalOffset(offset: Int): Int {
        return (offset - documentWindowStart).coerceIn(0, documentSpannable.length)
    }

    private fun rangeIntersectsWindow(start: Int, end: Int): Boolean {
        return end > documentWindowStart && start < documentWindowEnd
    }

    private fun ensureDocumentWindowForIndex(index: Int) {
        if (!useWindowedDocument()) return
        val safeIndex = index.coerceIn(0, documentText.length)
        val buffer = DOCUMENT_WINDOW_CHARS / 5
        if (safeIndex in (documentWindowStart + buffer)..(documentWindowEnd - buffer)) return
        rebuildDocumentSpannable(safeIndex)
        applySearchResultHighlights()
        applyStoredHighlight()
        applyStoredNotes()
        if (noteSelection.active) {
            applyNoteSelection()
        }
        if (::documentView.isInitialized) {
            documentView.text = documentSpannable
        }
    }

    private fun rebuildDocumentSpannable(centerIndex: Int) {
        if (!useWindowedDocument()) {
            documentWindowStart = 0
            documentWindowEnd = documentText.length
            documentSpannable = SpannableString(documentText)
            return
        }
        val safeCenter = centerIndex.coerceIn(0, documentText.length)
        val halfWindow = DOCUMENT_WINDOW_CHARS / 2
        var start = (safeCenter - halfWindow).coerceIn(0, documentText.length)
        var end = (start + DOCUMENT_WINDOW_CHARS).coerceAtMost(documentText.length)
        start = findWindowBoundaryStart(start)
        end = findWindowBoundaryEnd(end)
        if (end <= start) {
            start = 0
            end = documentText.length.coerceAtMost(DOCUMENT_WINDOW_CHARS)
        }
        documentWindowStart = start
        documentWindowEnd = end
        documentSpannable = SpannableString(documentText.substring(start, end))
    }

    private fun findWindowBoundaryStart(offset: Int): Int {
        var i = offset.coerceIn(0, documentText.length)
        while (i > 0 && !documentText[i - 1].isWhitespace()) i--
        return i.coerceIn(0, documentText.length)
    }

    private fun findWindowBoundaryEnd(offset: Int): Int {
        var i = offset.coerceIn(0, documentText.length)
        while (i < documentText.length && !documentText[i].isWhitespace()) i++
        return i.coerceIn(0, documentText.length)
    }

    private fun setDocumentText(text: String, preserveNoteSelection: Boolean = false) {
        val sameText = text == documentText
        documentText = text
        currentReadIndex = 0
        rebuildDocumentSpannable(currentReadIndex)
        currentHighlight = null
        searchResultSpans.clear()
        noteSpans.clear()
        if (!preserveNoteSelection) {
            clearNoteSelection(rebuild = false)
        } else {
            noteSelectionSpan = null
        }
        if (!sameText) {
            highlightStart = -1
            highlightEnd = -1
            textSearchQuery = ""
            textSearchLastStart = -1
            textSearchCachedQuery = ""
            textSearchCachedTotal = -1
        }
        applySearchResultHighlights()
        applyStoredHighlight()
        applyStoredNotes()
        if (preserveNoteSelection) {
            applyNoteSelection()
        }
        if (::documentView.isInitialized) {
            documentView.text = documentSpannable
        }
    }

    private fun scrollToCurrentReadIndex() {
        if (!::documentView.isInitialized || !::readerScroll.isInitialized) return
        ensureDocumentWindowForIndex(currentReadIndex)
        documentView.post {
            val layout = documentView.layout ?: return@post
            val offset = documentToLocalOffset(currentReadIndex).coerceIn(0, documentSpannable.length)
            val line = layout.getLineForOffset(offset)
            val targetY = (layout.getLineTop(line) - dp(24)).coerceAtLeast(0)
            readerScroll.scrollTo(0, targetY)
            updatePageIndicator()
        }
    }

    private fun smoothScrollToReadIndex(index: Int) {
        if (!::documentView.isInitialized || !::readerScroll.isInitialized || activeScreen != AppScreen.Reader) return
        ensureDocumentWindowForIndex(index)
        documentView.post {
            val layout = documentView.layout ?: return@post
            val offset = documentToLocalOffset(index).coerceIn(0, documentSpannable.length)
            val line = layout.getLineForOffset(offset)
            val targetY = (layout.getLineTop(line) - readerScroll.height / 3).coerceAtLeast(0)
            readerScroll.smoothScrollTo(0, targetY.coerceIn(0, maxScrollY()))
            updatePageIndicator()
        }
    }

    private fun highlightRange(start: Int, end: Int) {
        currentHighlight?.let { documentSpannable.removeSpan(it) }
        currentHighlight = null
        clearSearchResultHighlights()
        if (start >= 0 && end > start && end <= documentText.length) {
            highlightStart = start
            highlightEnd = end
            currentReadIndex = start
            saveSelectedProgress()
            ensureDocumentWindowForIndex(start)
            applySearchResultHighlights()
            applyStoredHighlight()
        } else {
            highlightStart = -1
            highlightEnd = -1
        }
        if (::documentView.isInitialized) {
            documentView.text = documentSpannable
            documentView.invalidate()
        }
    }

    private fun clearSearchResultHighlights() {
        searchResultSpans.forEach { documentSpannable.removeSpan(it) }
        searchResultSpans.clear()
    }

    private fun applySearchResultHighlights() {
        clearSearchResultHighlights()
        val query = textSearchQuery.trim()
        if (query.isBlank() || documentSpannable.isEmpty()) return
        val total = if (query.equals(textSearchCachedQuery, ignoreCase = true)) textSearchCachedTotal else -1
        if (total > MAX_SEARCH_HIGHLIGHTS) return
        val resultColor = if (isDarkMode) {
            Color.argb(42, Color.red(LEMON_HIGHLIGHT), Color.green(LEMON_HIGHLIGHT), Color.blue(LEMON_HIGHLIGHT))
        } else {
            Color.argb(34, Color.red(LEMON_HIGHLIGHT), Color.green(LEMON_HIGHLIGHT), Color.blue(LEMON_HIGHLIGHT))
        }
        var index = documentText.indexOf(query, startIndex = documentWindowStart, ignoreCase = true)
        var count = 0
        while (index >= 0 && index < documentWindowEnd && count < MAX_SEARCH_HIGHLIGHTS) {
            val end = (index + query.length).coerceAtMost(documentWindowEnd)
            if (!(index == highlightStart && end == highlightEnd)) {
                val localStart = documentToLocalOffset(index).coerceIn(0, documentSpannable.length)
                val localEnd = documentToLocalOffset(end).coerceIn(0, documentSpannable.length)
                if (localEnd <= localStart) {
                    count++
                    index = documentText.indexOf(query, startIndex = index + query.length.coerceAtLeast(1), ignoreCase = true)
                    continue
                }
                BackgroundColorSpan(resultColor).also {
                    searchResultSpans.add(it)
                    documentSpannable.setSpan(it, localStart, localEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            count++
            index = documentText.indexOf(query, startIndex = index + query.length.coerceAtLeast(1), ignoreCase = true)
        }
    }

    private fun applyStoredHighlight() {
        currentHighlight?.let { documentSpannable.removeSpan(it) }
        currentHighlight = null
        val start = highlightStart
        val end = highlightEnd
        if (start >= 0 && end > start && rangeIntersectsWindow(start, end)) {
            val localStart = documentToLocalOffset(start).coerceIn(0, documentSpannable.length)
            val localEnd = documentToLocalOffset(end).coerceIn(0, documentSpannable.length)
            if (localEnd <= localStart) return
            val highlightColor = if (isDarkMode) {
                Color.argb(95, Color.red(LEMON_HIGHLIGHT), Color.green(LEMON_HIGHLIGHT), Color.blue(LEMON_HIGHLIGHT))
            } else {
                Color.argb(70, Color.red(LEMON_HIGHLIGHT), Color.green(LEMON_HIGHLIGHT), Color.blue(LEMON_HIGHLIGHT))
            }
            currentHighlight = BackgroundColorSpan(highlightColor).also {
                documentSpannable.setSpan(it, localStart, localEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    private fun applyStoredNotes() {
        noteSpans.forEach { documentSpannable.removeSpan(it) }
        noteSpans.clear()
        val documentId = currentDocumentId()
        val noteColor = if (isDarkMode) LEMON_HIGHLIGHT else Color.rgb(120, 92, 0)
        val noteBackground = if (isDarkMode) {
            Color.argb(68, Color.red(LEMON_HIGHLIGHT), Color.green(LEMON_HIGHLIGHT), Color.blue(LEMON_HIGHLIGHT))
        } else {
            Color.argb(44, Color.red(LEMON_HIGHLIGHT), Color.green(LEMON_HIGHLIGHT), Color.blue(LEMON_HIGHLIGHT))
        }
        readerNotes
            .filter { it.documentId == documentId && it.start >= 0 && it.end <= documentText.length && it.end > it.start && rangeIntersectsWindow(it.start, it.end) }
            .forEach { note ->
                val localStart = documentToLocalOffset(note.start).coerceIn(0, documentSpannable.length)
                val localEnd = documentToLocalOffset(note.end).coerceIn(0, documentSpannable.length)
                if (localEnd <= localStart) return@forEach
                val underline = UnderlineSpan()
                val color = ForegroundColorSpan(noteColor)
                val background = BackgroundColorSpan(noteBackground)
                noteSpans.add(underline)
                noteSpans.add(color)
                noteSpans.add(background)
                documentSpannable.setSpan(underline, localStart, localEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                documentSpannable.setSpan(color, localStart, localEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                documentSpannable.setSpan(background, localStart, localEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun isCompactWidth(): Boolean {
        val widthDp = resources.configuration.screenWidthDp
        return widthDp > 0 && widthDp < 420
    }

    private fun isLandscape(): Boolean {
        return resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    // Yatayda satırlar çok uzamasın diye metni ortalanmış bir sütunda tut.
    private fun readerSidePadding(): Int {
        val widthDp = resources.configuration.screenWidthDp
        val maxColumnDp = 720
        val extra = if (widthDp > maxColumnDp + 40) (widthDp - maxColumnDp - 40) / 2 else 0
        return dp(22 + extra)
    }

    private fun android.content.ContentResolver.takePersistableUriPermissionSafe(
        uri: Uri,
        flags: Int
    ) {
        val mode = flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        if (mode and Intent.FLAG_GRANT_READ_URI_PERMISSION == 0) return
        runCatching { takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }
}

private fun sampleText(): String {
    return ""
}
