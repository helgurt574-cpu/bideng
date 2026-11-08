package com.example.filiz

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.io.File

internal const val TTS_NOTIFICATION_ID = 42
internal const val TTS_NOTIFICATION_CHANNEL = "tts_playback_silent_v5"
internal const val TTS_ACTION_START = "com.example.filiz.tts.START"
internal const val TTS_ACTION_STOP = "com.example.filiz.tts.STOP"
internal const val TTS_ACTION_CLOSE = "com.example.filiz.tts.CLOSE"
internal const val TTS_ACTION_STATUS = "com.example.filiz.tts.STATUS"
internal const val TTS_EXTRA_STATUS = "status"
internal const val TTS_EXTRA_READING = "reading"
internal const val TTS_EXTRA_RANGE_START = "rangeStart"
internal const val TTS_EXTRA_RANGE_END = "rangeEnd"

// Tüm sesler aynı (budanmış) espeak-ng verisini paylaşır.
internal const val ESPEAK_DATA_ASSET = "tts/espeak-ng-data"
// Asset'teki espeak verisi değişince (ör. yeni dil sözlüğü) artır; cihazdaki kopya yenilenir.
internal const val ESPEAK_DATA_VERSION = "2-tr-ar-en-ku-fa"

data class TtsVoice(
    val id: String,
    val label: String,
    val language: String,
    // tokens.txt her zaman bu asset klasöründe; model ya burada ya da indirilmiş dosyada
    val modelDir: String,
    val modelName: String,
    // null ise ses her APK'da gelir; değilse çevrimiçi sürümde internetten indirilir,
    // çevrimdışı sürümde (offline flavor) modeli zaten APK'nın içindedir
    val downloadUrl: String? = null,
    val downloadSize: Long = 0L,
    val sha256: String? = null
)

val TTS_VOICES = listOf(
    TtsVoice(
        id = "fahrettin",
        label = "Fahrettin",
        language = "tr",
        modelDir = "tts/tr_TR_fahrettin_medium",
        modelName = "model.onnx"
    ),
    TtsVoice(
        id = "dfki",
        label = "DFKI",
        language = "tr",
        modelDir = "tts/tr_TR_dfki_medium",
        modelName = "model.onnx",
        downloadUrl = "https://huggingface.co/csukuangfj/vits-piper-tr_TR-dfki-medium/resolve/main/tr_TR-dfki-medium.onnx",
        downloadSize = 63_201_422L,
        sha256 = "35c924643ba76043184b4b685b468ef8e06492d1c6eb029fbe12cac63b56ff82"
    ),
    TtsVoice(
        id = "ar_kareem_low",
        label = "Kareem",
        language = "ar",
        modelDir = "tts/vits-piper-ar_JO-kareem-low",
        modelName = "model.onnx",
        downloadUrl = "https://huggingface.co/csukuangfj/vits-piper-ar_JO-kareem-medium/resolve/main/ar_JO-kareem-medium.onnx",
        downloadSize = 63_201_421L,
        sha256 = "f942091e1fdb7221b289256cc2238295b5472d326029532f79bb84836380fe53"
    ),
    TtsVoice(
        id = "fa_amir",
        label = "Amir",
        language = "fa",
        modelDir = "tts/vits-piper-fa_IR-amir-medium",
        modelName = "model.onnx",
        downloadUrl = "https://huggingface.co/csukuangfj/vits-piper-fa_IR-amir-medium/resolve/main/fa_IR-amir-medium.onnx",
        downloadSize = 63_531_507L,
        sha256 = "5a832803df099857d92cf7ec1e340c7ef6910433f089d9aca9ff1ebf9b7fa6c5"
    ),
    TtsVoice(
        id = "fa_gyro",
        label = "Gyro",
        language = "fa",
        modelDir = "tts/vits-piper-fa_IR-gyro-medium",
        modelName = "model.onnx",
        downloadUrl = "https://huggingface.co/csukuangfj/vits-piper-fa_IR-gyro-medium/resolve/main/fa_IR-gyro-medium.onnx",
        downloadSize = 63_122_437L,
        sha256 = "79646f0a1942d8be05562f4103da6470bcf69337e65bd8a1ec95afe1c6a89e6b"
    )
)

val DEFAULT_TTS_VOICE = TTS_VOICES.first()

internal data class TtsPlaybackRequest(
    val text: String,
    val startIndex: Int,
    val speed: Float,
    val voiceId: String,
    val title: String,
    val sleepTimerMinutes: Int = DEFAULT_SLEEP_TIMER_MINUTES
) {
    fun selectedVoice(): TtsVoice {
        return TTS_VOICES.firstOrNull { it.id == voiceId } ?: DEFAULT_TTS_VOICE
    }
}

internal object TtsPlaybackRequestStore {
    private const val PREFS = "tts_playback_request"
    private const val KEY_TEXT_FILE = "textFile"
    private const val KEY_START_INDEX = "startIndex"
    private const val KEY_SPEED = "speed"
    private const val KEY_VOICE_ID = "voiceId"
    private const val KEY_TITLE = "title"
    private const val KEY_SLEEP_TIMER_MINUTES = "sleepTimerMinutes"
    private const val TEXT_FILE_NAME = "tts_playback_text.txt"

    fun save(context: android.content.Context, request: TtsPlaybackRequest) {
        val file = File(context.cacheDir, TEXT_FILE_NAME)
        file.writeText(request.text)
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TEXT_FILE, file.absolutePath)
            .putInt(KEY_START_INDEX, request.startIndex)
            .putFloat(KEY_SPEED, request.speed)
            .putString(KEY_VOICE_ID, request.voiceId)
            .putString(KEY_TITLE, request.title)
            .putInt(KEY_SLEEP_TIMER_MINUTES, request.sleepTimerMinutes)
            .apply()
    }

    fun load(context: android.content.Context): TtsPlaybackRequest? {
        val prefs = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        val textFile = prefs.getString(KEY_TEXT_FILE, null)?.let(::File) ?: return null
        val text = textFile.takeIf { it.exists() }?.readText().orEmpty()
        if (text.isBlank()) return null
        return TtsPlaybackRequest(
            text = text,
            startIndex = prefs.getInt(KEY_START_INDEX, 0).coerceIn(0, text.length),
            speed = prefs.getFloat(KEY_SPEED, DEFAULT_TTS_SPEED).coerceIn(MIN_TTS_SPEED, MAX_TTS_SPEED),
            voiceId = prefs.getString(KEY_VOICE_ID, DEFAULT_TTS_VOICE.id).orEmpty(),
            title = prefs.getString(KEY_TITLE, "").orEmpty(),
            sleepTimerMinutes = prefs.getInt(KEY_SLEEP_TIMER_MINUTES, DEFAULT_SLEEP_TIMER_MINUTES)
                .takeIf { it in SLEEP_TIMER_OPTIONS }
                ?: DEFAULT_SLEEP_TIMER_MINUTES
        )
    }

    fun clear(context: android.content.Context) {
        val prefs = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        prefs.getString(KEY_TEXT_FILE, null)?.let { path ->
            runCatching { File(path).delete() }
        }
        prefs.edit().clear().apply()
    }
}

class TtsPlaybackService : Service() {
    private var tts: OfflineTtsReader? = null
    private var mediaSession: MediaSession? = null
    private var currentRequest: TtsPlaybackRequest? = null
    private val sleepHandler = Handler(Looper.getMainLooper())
    private val sleepRunnable = Runnable {
        broadcastStatus("Uyku zamanlayıcısı doldu.", reading = false)
        closePlayback()
    }
    @Volatile private var reading = false
    @Volatile private var pausedByUser = false
    @Volatile private var lastRangeStart = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        mediaSession = MediaSession(this, "BidengPlayback").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onStop() {
                    closePlayback()
                }

                override fun onPause() {
                    stopPlayback()
                }

                override fun onPlay() {
                    startPlayback()
                }
            })
            isActive = true
        }
        updatePlaybackState(false)
        tts = OfflineTtsReader(
            context = applicationContext,
            onStatus = { status ->
                val isTerminal = status == "Durduruldu." ||
                    status == "Okuma tamamlandı." ||
                    status == "Okuma başlatılamadı." ||
                    status == "Model hazırlanamadı."
                if (status == "Okunuyor.") reading = true
                if (isTerminal) reading = false
                updatePlaybackState(reading)
                broadcastStatus(status = status, reading = reading)
                if (reading || !isTerminal) {
                    updateNotification(status)
                } else if (pausedByUser && status == "Durduruldu.") {
                    pausedByUser = false
                    updateNotification("Duraklatıldı")
                } else {
                    currentRequest = null
                    clearSleepTimer()
                    TtsPlaybackRequestStore.clear(this)
                    stopForegroundCompat()
                    stopSelf()
                }
            },
            onRange = { start, end ->
                broadcastStatus(status = "", reading = reading, start = start, end = end)
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            TTS_ACTION_STOP -> {
                stopPlayback()
                return START_NOT_STICKY
            }
            TTS_ACTION_CLOSE -> {
                closePlayback()
                return START_NOT_STICKY
            }
            TTS_ACTION_START, null -> startPlayback()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        clearSleepTimer()
        tts?.release()
        tts = null
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    private fun startPlayback() {
        val request = TtsPlaybackRequestStore.load(this)
        if (request == null) {
            startForeground(TTS_NOTIFICATION_ID, buildNotification("Okuma başlatılamadı."))
            broadcastStatus("Okuma başlatılamadı.", reading = false)
            stopForegroundCompat()
            stopSelf()
            return
        }
        currentRequest = request
        pausedByUser = false
        lastRangeStart = request.startIndex
        reading = true
        scheduleSleepTimer(request.sleepTimerMinutes)
        updatePlaybackState(true)
        startForeground(TTS_NOTIFICATION_ID, buildNotification("Okuma başlıyor..."))
        tts?.setVoice(request.selectedVoice())
        tts?.setSpeed(request.speed)
        tts?.speakFrom(request.text, request.startIndex)
    }

    private fun stopPlayback() {
        reading = false
        pausedByUser = true
        clearSleepTimer()
        updatePlaybackState(false)
        currentRequest?.let { request ->
            TtsPlaybackRequestStore.save(this, request.copy(startIndex = lastRangeStart.coerceIn(0, request.text.length)))
        }
        tts?.stop()
        updateNotification("Duraklatıldı")
    }

    private fun closePlayback() {
        reading = false
        pausedByUser = false
        clearSleepTimer()
        updatePlaybackState(false)
        tts?.stop()
        currentRequest = null
        TtsPlaybackRequestStore.clear(this)
        stopForegroundCompat()
        stopSelf()
    }

    private fun scheduleSleepTimer(minutes: Int) {
        clearSleepTimer()
        if (minutes <= 0) return
        sleepHandler.postDelayed(sleepRunnable, minutes * 60_000L)
    }

    private fun clearSleepTimer() {
        sleepHandler.removeCallbacks(sleepRunnable)
    }

    private fun broadcastStatus(
        status: String,
        reading: Boolean,
        start: Int = -1,
        end: Int = -1
    ) {
        Intent(TTS_ACTION_STATUS).apply {
            setPackage(packageName)
            putExtra(TTS_EXTRA_STATUS, status)
            putExtra(TTS_EXTRA_READING, reading)
            putExtra(TTS_EXTRA_RANGE_START, start)
            putExtra(TTS_EXTRA_RANGE_END, end)
        }.also { sendBroadcast(it) }
        if (start >= 0) lastRangeStart = start
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            TTS_NOTIFICATION_CHANNEL,
            "Sesli okuma",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Sesli okuma kontrollerini sessiz mini oynatıcı olarak gösterir"
            setShowBadge(false)
            enableVibration(false)
            vibrationPattern = longArrayOf(0L)
            setSound(null, null)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun updateNotification(status: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(TTS_NOTIFICATION_ID, buildNotification(status))
    }

    private fun buildNotification(status: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playPauseIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, TtsPlaybackService::class.java).setAction(if (reading) TTS_ACTION_STOP else TTS_ACTION_START),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val closeIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, TtsPlaybackService::class.java).setAction(TTS_ACTION_CLOSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, TTS_NOTIFICATION_CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val appName = getString(R.string.app_name)
        val documentTitle = currentRequest?.title.orEmpty().ifBlank { "Sesli okuma" }
        val displayText = if (status.isBlank()) documentTitle else "$documentTitle • $status"
        val playPauseLabel = if (reading) "Duraklat" else "Devam et"
        val playPauseIcon = if (reading) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        return builder
            .setSmallIcon(R.drawable.ic_stat_bideng_tts)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher))
            .setContentTitle(appName)
            .setContentText(displayText)
            .setSubText(appName)
            .setTicker("$appName: $displayText")
            .setContentIntent(openIntent)
            .setOngoing(reading)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setPriority(Notification.PRIORITY_LOW)
            .setDefaults(0)
            .setSound(null)
            .setVibrate(longArrayOf(0L))
            .setColor(Color.rgb(86, 70, 214))
            .setColorized(true)
            .addAction(
                Notification.Action.Builder(
                    playPauseIcon,
                    playPauseLabel,
                    playPauseIntent
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Kapat",
                    closeIntent
                ).build()
            )
            .build()
    }

    private fun updatePlaybackState(isPlaying: Boolean) {
        val state = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_STOPPED
        val actions = PlaybackState.ACTION_STOP or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PLAY_PAUSE
        mediaSession?.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build()
        )
    }

    private fun stopForegroundCompat() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        }
    }
}
