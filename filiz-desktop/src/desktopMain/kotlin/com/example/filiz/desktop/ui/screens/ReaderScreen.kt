package com.example.filiz.desktop.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowDown
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.filiz.desktop.service.PageUnit
import com.example.filiz.desktop.service.PreferenceService
import com.example.filiz.desktop.ui.AppState
import com.example.filiz.desktop.ui.LocalStrings
import com.example.filiz.desktop.ui.UiLanguage
import com.example.filiz.desktop.ui.components.AppearanceDialog
import com.example.filiz.desktop.ui.components.ChoiceDialog
import com.example.filiz.desktop.ui.components.PrimaryButton
import com.example.filiz.desktop.ui.components.QuietButton
import com.example.filiz.desktop.ui.components.bidengSliderColors
import com.example.filiz.desktop.ui.theme.Bideng
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SLEEP_OPTIONS = listOf(0, 15, 30, 60)

/** Testler için: okuyucu ekranının kaç kez yeniden oluşturulduğu (sonsuz döngüleri yakalamak için). */
object ReaderDiagnostics {
    @Volatile var compositions = 0L
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun ReaderScreen(state: AppState) {
    val doc = state.document ?: return
    val reader = state.reader ?: return
    SideEffect { ReaderDiagnostics.compositions++ }
    val s = LocalStrings.current
    val c = Bideng.colors
    val pages = doc.content.pages
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Yerleşim bilgileri gözlenmeyen haritalarda: her yerleşimde yeniden çizim tetiklenmesin (sonsuz döngü)
    val layouts = remember(doc) { HashMap<Int, TextLayoutResult>() }
    val textTops = remember(doc) { HashMap<Int, Int>() }

    var settingsOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var autoScroll by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(listOf<IntRange>()) }
    var currentResult by remember { mutableIntStateOf(-1) }
    var selection by remember { mutableStateOf<IntRange?>(null) }
    var clearSelectionToken by remember { mutableIntStateOf(0) }
    var textFieldFocused by remember { mutableStateOf(false) }
    var tapJob by remember { mutableStateOf<Job?>(null) }

    var fontSize by remember { mutableIntStateOf(PreferenceService.getFontSize()) }
    var lineSpacing by remember { mutableStateOf(PreferenceService.getLineSpacing()) }
    var fontFamily by remember { mutableStateOf(PreferenceService.getFontFamily()) }
    var speed by remember { mutableStateOf(PreferenceService.getTTSSpeed()) }
    var sleepMinutes by remember { mutableIntStateOf(PreferenceService.getSleepMinutes()) }
    val voices = remember { state.speech.getAvailableVoices() }
    var voiceId by remember { mutableStateOf(PreferenceService.getTTSVoice().ifBlank { voices.firstOrNull()?.id.orEmpty() }) }
    var dialog by remember { mutableStateOf<String?>(null) }

    fun pageIndexFor(offset: Int): Int = pages.indexOfLast { offset >= it.startIndex }.coerceIn(0, (pages.size - 1).coerceAtLeast(0))

    fun visibleStart(): Int {
        val first = listState.layoutInfo.visibleItemsInfo.firstOrNull() ?: return doc.item.lastIndex
        val page = pages.getOrNull(first.index) ?: return 0
        val layout = layouts[first.index] ?: return page.startIndex
        val y = (-first.offset - (textTops[first.index] ?: 0) + 8).toFloat().coerceIn(0f, layout.size.height.toFloat())
        val line = layout.getLineForVerticalPosition(y)
        return reader.wordStart(page.startIndex + layout.getLineStart(line))
    }

    fun isVisible(index: Int): Boolean {
        if (index < 0) return false
        val pageIndex = pageIndexFor(index)
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == pageIndex } ?: return false
        val layout = layouts[pageIndex] ?: return false
        val page = pages[pageIndex]
        val line = layout.getLineForOffset((index - page.startIndex).coerceIn(0, page.text.length))
        val top = item.offset + (textTops[pageIndex] ?: 0) + layout.getLineTop(line)
        val bottom = item.offset + (textTops[pageIndex] ?: 0) + layout.getLineBottom(line)
        return bottom > 0 && top < info.viewportSize.height
    }

    suspend fun scrollToOffset(index: Int, animate: Boolean = true) {
        if (pages.isEmpty()) return
        val pageIndex = pageIndexFor(index)
        if (layouts[pageIndex] == null) {
            listState.scrollToItem(pageIndex)
            withFrameNanos { }
        }
        val layout = layouts[pageIndex] ?: return
        val page = pages[pageIndex]
        val line = layout.getLineForOffset((index - page.startIndex).coerceIn(0, page.text.length))
        val target = ((textTops[pageIndex] ?: 0) + layout.getLineTop(line) - listState.layoutInfo.viewportSize.height / 3f)
            .toInt().coerceAtLeast(0)
        if (animate) listState.animateScrollToItem(pageIndex, target) else listState.scrollToItem(pageIndex, target)
    }

    SideEffect {
        reader.visibleStart = ::visibleStart
        reader.isVisible = ::isVisible
        reader.scrollTo = { index -> scope.launch { scrollToOffset(index) } }
    }

    // Açılışta kaldığı yere git; kart üzerinden "devam" denildiyse okumayı başlat
    LaunchedEffect(doc) {
        val target = if (reader.pausedIndex >= 0) reader.pausedIndex else doc.item.lastIndex
        if (target > 0) scrollToOffset(target, animate = false)
        if (reader.resumeWhenReady) {
            reader.resumeWhenReady = false
            reader.speakFrom(if (reader.pausedIndex >= 0) reader.pausedIndex else visibleStart())
        }
    }
    // Okunan yeri kaydet
    LaunchedEffect(doc) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }.collect {
            delay(700)
            state.saveProgress(visibleStart())
        }
    }
    // Okunan cümleyi takip et (kullanıcı elle uzağa kaydırdıysa takip etme)
    LaunchedEffect(reader.activeRange) {
        val start = reader.activeRange.first
        if (reader.isReading && !reader.activeRange.isEmpty() && (reader.followReading || isVisible(start))) {
            reader.followReading = true
            if (!isVisible(start) || !isVisible(reader.activeRange.last)) scrollToOffset(start)
        }
    }
    LaunchedEffect(autoScroll) {
        while (autoScroll) {
            if (reader.isReading) { autoScroll = false; break }
            if (!listState.canScrollForward) { autoScroll = false; break }
            listState.scrollBy(with(density) { 0.8.dp.toPx() })
            delay(16)
        }
    }
    LaunchedEffect(query) {
        delay(250)
        results = if (query.trim().length < 2) emptyList() else {
            Regex(Regex.escape(query.trim()), RegexOption.IGNORE_CASE).findAll(doc.content.text).take(2000).map { it.range }.toList()
        }
        currentResult = if (results.isEmpty()) -1 else 0
        results.firstOrNull()?.let { scrollToOffset(it.first) }
    }

    fun goToResult(direction: Int) {
        if (results.isEmpty()) return
        currentResult = (currentResult + direction + results.size) % results.size
        reader.followReading = false
        scope.launch { scrollToOffset(results[currentResult].first) }
    }

    // Klavye: boşluk = oynat/duraklat, Esc = geri, Ctrl+F = ara, +/- = yazı boyutu
    DisposableEffect(reader) {
        state.readerKeyHandler = { event: KeyEvent ->
            if (event.type != KeyEventType.KeyDown) false else when {
                event.key == Key.Escape -> when {
                    selection != null -> { selection = null; clearSelectionToken++; true }
                    searchOpen -> { searchOpen = false; query = ""; true }
                    settingsOpen -> { settingsOpen = false; true }
                    autoScroll -> { autoScroll = false; true }
                    else -> { state.closeReader(); true }
                }
                textFieldFocused -> false
                event.key == Key.Spacebar -> { reader.toggle(); true }
                event.isCtrlPressed && event.key == Key.F -> { searchOpen = true; settingsOpen = false; true }
                event.key == Key.Plus || event.key == Key.Equals || event.key == Key.NumPadAdd -> {
                    fontSize = (fontSize + 1).coerceAtMost(36); PreferenceService.setFontSize(fontSize); true
                }
                event.key == Key.Minus || event.key == Key.NumPadSubtract -> {
                    fontSize = (fontSize - 1).coerceAtLeast(14); PreferenceService.setFontSize(fontSize); true
                }
                event.key == Key.PageDown -> { scope.launch { listState.animateScrollBy(listState.layoutInfo.viewportSize.height * 0.85f) }; true }
                event.key == Key.PageUp -> { scope.launch { listState.animateScrollBy(-listState.layoutInfo.viewportSize.height * 0.85f) }; true }
                else -> false
            }
        }
        onDispose { state.readerKeyHandler = null }
    }

    val family = when (fontFamily) {
        "serif" -> FontFamily.Serif
        "monospace" -> FontFamily.Monospace
        else -> FontFamily.SansSerif
    }
    val textStyle = TextStyle(
        color = c.readerText, fontSize = fontSize.sp, lineHeight = (fontSize * lineSpacing).sp,
        fontFamily = family, textDirection = TextDirection.Content
    )
    val docNotes = state.notes.filter { it.documentId == doc.item.id }
    // Yerleşim bilgisini doğrudan okumak her karede yeniden oluşturmaya yol açıyordu;
    // derivedStateOf yalnızca sonuç değiştiğinde ekranı günceller.
    val away by remember(reader) { derivedStateOf { reader.readingAway() } }

    Column(Modifier.fillMaxSize().background(c.readerBackground)) {
        // Üst çubuk
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            ToolbarIcon(Icons.AutoMirrored.Rounded.ArrowBack, s.library, state::closeReader)
            Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                Text(doc.title, color = c.readerText, fontWeight = FontWeight.Medium, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (away) s.readFromHere else reader.status,
                    color = c.muted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("readerStatus")
                )
            }
            ToolbarIcon(Icons.Rounded.StickyNote2, s.notes, { state.notesDialogOpen = true })
            ToolbarIcon(themeIcon(state.themeMode), s.theme, state::cycleTheme)
        }
        if (state.busyMessage != null) {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = c.primary, trackColor = c.readerBackground)
        }

        // Metin
        Box(
            Modifier.weight(1f).fillMaxWidth()
                .onPointerEvent(PointerEventType.Scroll) {
                    // Elle kaydırınca okuma ekranı geri çekmez; oynat düğmesi "buradan oku" olur
                    if (reader.isReading) reader.followReading = false
                    autoScroll = false
                }
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().testTag("readerText"),
                contentPadding = PaddingValues(top = 12.dp, bottom = 48.dp)
            ) {
                itemsIndexed(pages, key = { index, _ -> index }) { index, page ->
                    val pageEnd = page.startIndex + page.text.length
                    val highlights = buildList {
                        docNotes.forEach { note ->
                            local(note.startIndex until note.endIndex, page.startIndex, pageEnd)?.let {
                                add(it to SpanStyle(background = c.noteHighlight, textDecoration = TextDecoration.Underline))
                            }
                        }
                        results.forEachIndexed { i, range ->
                            if (range.first in page.startIndex until pageEnd) {
                                local(range, page.startIndex, pageEnd)?.let {
                                    add(it to SpanStyle(background = if (i == currentResult) c.primary.copy(alpha = 0.45f) else c.searchHighlight))
                                }
                            }
                        }
                        local(reader.activeRange, page.startIndex, pageEnd)?.let { add(it to SpanStyle(background = c.readingHighlight)) }
                    }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        Column(Modifier.widthIn(max = 780.dp).fillMaxWidth().padding(horizontal = 40.dp)) {
                            if (pages.size > 1) {
                                Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    HorizontalDivider(Modifier.weight(1f), color = c.outline)
                                    Text(
                                        s.pageLabel(unitId(doc.content.pageUnit), index + 1, pages.size),
                                        color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp)
                                    )
                                    HorizontalDivider(Modifier.weight(1f), color = c.outline)
                                }
                            }
                            var value by remember(page.text, clearSelectionToken) { mutableStateOf(TextFieldValue(page.text)) }
                            BasicTextField(
                                value = value,
                                onValueChange = { changed ->
                                    value = changed.copy(text = page.text, composition = null)
                                    val sel = changed.selection
                                    selection = if (sel.collapsed) null else (page.startIndex + sel.min) until (page.startIndex + sel.max)
                                },
                                readOnly = true,
                                textStyle = textStyle,
                                cursorBrush = SolidColor(Color.Transparent),
                                visualTransformation = HighlightTransformation(highlights),
                                onTextLayout = { layouts[index] = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onGloballyPositioned { textTops[index] = it.positionInParent().y.toInt() }
                                    .onFocusChanged { }
                                    .pointerInput(page.startIndex) {
                                        // Tek tıklama: o kelimeden okumaya başla (seçim yapılıyorsa değil)
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                            val up = waitForUpOrCancellation(PointerEventPass.Initial) ?: return@awaitEachGesture
                                            val moved = (up.position - down.position).getDistance()
                                            if (moved < 6f && up.uptimeMillis - down.uptimeMillis < 450) {
                                                val local = layouts[index]?.getOffsetForPosition(down.position) ?: return@awaitEachGesture
                                                tapJob?.cancel()
                                                tapJob = scope.launch {
                                                    delay(320)
                                                    if (selection == null) reader.speakFrom(page.startIndex + local)
                                                }
                                            }
                                        }
                                    }
                            )
                        }
                    }
                }
            }
        }

        // Alt panel
        Surface(
            color = c.surface,
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            border = BorderStroke(1.dp, c.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 900.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                    when {
                        selection != null -> NoteBar(
                            onCancel = { selection = null; clearSelectionToken++ },
                            onAdd = {
                                selection?.let(state::addNote)
                                selection = null
                                clearSelectionToken++
                            }
                        )
                        searchOpen -> SearchBar(
                            query = query,
                            onQuery = { query = it },
                            count = results.size,
                            current = currentResult,
                            onPrevious = { goToResult(-1) },
                            onNext = { goToResult(1) },
                            onClose = { searchOpen = false; query = "" },
                            onFocus = { textFieldFocused = it }
                        )
                        else -> {
                            if (settingsOpen) {
                                SettingsSection(
                                    voiceLabel = voices.firstOrNull { it.id == voiceId }?.name ?: voiceId,
                                    speed = speed,
                                    onSpeed = { speed = it },
                                    onSpeedDone = {
                                        state.speech.setSpeed(speed)
                                        PreferenceService.setTTSSpeed(speed)
                                        reader.restartIfReading()
                                    },
                                    sleepLabel = if (sleepMinutes == 0) s.sleepOff else s.minutes(sleepMinutes),
                                    fullScreen = state.fullScreen,
                                    onVoice = { dialog = "voice" },
                                    onAppearance = { dialog = "appearance" },
                                    onLanguage = { dialog = "language" },
                                    onSleep = { dialog = "sleep" },
                                    onFullScreen = { state.fullScreen = !state.fullScreen },
                                    onClose = { settingsOpen = false }
                                )
                                HorizontalDivider(Modifier.padding(top = 12.dp, bottom = 4.dp), color = c.outline)
                            }
                            ProgressRow(
                                label = s.pageLabel(unitId(doc.content.pageUnit), listState.firstVisibleItemIndex + 1, pages.size),
                                fraction = if (doc.content.text.isEmpty()) 0f else (pages.getOrNull(listState.firstVisibleItemIndex)?.startIndex ?: 0).toFloat() / doc.content.text.length,
                                onSeek = { fraction ->
                                    reader.followReading = false
                                    scope.launch { scrollToOffset((fraction * doc.content.text.length).toInt(), animate = false) }
                                }
                            )
                            val active = reader.isReading || reader.pausedIndex >= 0
                            Row(Modifier.fillMaxWidth().height(72.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                ControlIcon(Icons.Rounded.Tune, s.readingSettings, selected = settingsOpen) { settingsOpen = !settingsOpen }
                                ControlIcon(Icons.Rounded.Search, s.searchInText) { searchOpen = true; settingsOpen = false }
                                ControlIcon(Icons.Rounded.SkipPrevious, s.previousSentence, enabled = active) { reader.skip(-1) }
                                val playing = reader.isReading && !away
                                FilledIconButton(
                                    onClick = { autoScroll = false; reader.toggle() },
                                    modifier = Modifier.size(62.dp).semantics {
                                        contentDescription = when {
                                            playing -> s.pause
                                            away -> s.readFromHere
                                            reader.pausedIndex >= 0 -> s.resume
                                            else -> s.play
                                        }
                                    }.testTag("playButton"),
                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = c.primary, contentColor = c.onPrimary)
                                ) {
                                    Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, modifier = Modifier.size(32.dp))
                                }
                                ControlIcon(Icons.Rounded.SkipNext, s.nextSentence, enabled = active) { reader.skip(1) }
                                if (active) {
                                    ControlIcon(Icons.Rounded.Stop, s.stop) { reader.stop() }
                                } else {
                                    ControlIcon(Icons.Rounded.KeyboardDoubleArrowDown, s.autoScroll, selected = autoScroll) {
                                        autoScroll = !autoScroll
                                        if (autoScroll) state.showToast(s.autoScrollOn)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    when (dialog) {
        "voice" -> ChoiceDialog(
            title = s.voiceTitle,
            options = voices.map { it.name to "${s.voiceLanguage(it.language)} · ${s.builtIn}" },
            selected = voices.indexOfFirst { it.id == voiceId },
            onSelect = { i ->
                voiceId = voices[i].id
                state.speech.setVoice(voiceId)
                PreferenceService.setTTSVoice(voiceId)
                reader.restartIfReading()
            },
            onDismiss = { dialog = null }
        )
        "language" -> ChoiceDialog(
            title = s.languageTitle,
            options = UiLanguage.entries.map { it.label to null },
            selected = UiLanguage.entries.indexOf(state.language),
            onSelect = { state.changeLanguage(UiLanguage.entries[it]) },
            onDismiss = { dialog = null }
        )
        "sleep" -> ChoiceDialog(
            title = s.sleepTitle,
            options = SLEEP_OPTIONS.map { (if (it == 0) s.sleepOff else s.minutes(it)) to null },
            selected = SLEEP_OPTIONS.indexOf(sleepMinutes),
            onSelect = { i ->
                sleepMinutes = SLEEP_OPTIONS[i]
                PreferenceService.setSleepMinutes(sleepMinutes)
                state.speech.setDefaultSleepTimer(sleepMinutes.takeIf { it > 0 })
            },
            onDismiss = { dialog = null }
        )
        "appearance" -> AppearanceDialog(
            themeMode = state.themeMode,
            fontSize = fontSize,
            lineSpacing = lineSpacing,
            fontFamily = fontFamily,
            onTheme = state::setTheme,
            onFontSize = { fontSize = it; PreferenceService.setFontSize(it) },
            onLineSpacing = { lineSpacing = it; PreferenceService.setLineSpacing(it) },
            onFontFamily = { fontFamily = it; PreferenceService.setFontFamily(it) },
            onDismiss = { dialog = null }
        )
    }
}

private fun unitId(unit: PageUnit) = when (unit) {
    PageUnit.Chapter -> "chapter"
    PageUnit.Location -> "location"
    PageUnit.RealPage -> "page"
}

/** Genel aralığı sayfa içi aralığa çevirir; sayfayla kesişmiyorsa null. */
private fun local(range: IntRange, pageStart: Int, pageEnd: Int): IntRange? {
    if (range.isEmpty()) return null
    val start = maxOf(range.first, pageStart)
    val end = minOf(range.last + 1, pageEnd)
    return if (end > start) (start - pageStart) until (end - pageStart) else null
}

private class HighlightTransformation(private val highlights: List<Pair<IntRange, SpanStyle>>) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (highlights.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val builder = AnnotatedString.Builder(text)
        highlights.forEach { (range, style) ->
            val end = (range.last + 1).coerceAtMost(text.length)
            if (range.first < end) builder.addStyle(style, range.first, end)
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }

    override fun equals(other: Any?) = other is HighlightTransformation && other.highlights == highlights
    override fun hashCode() = highlights.hashCode()
}

@Composable
private fun ControlIcon(icon: ImageVector, description: String, enabled: Boolean = true, selected: Boolean = false, onClick: () -> Unit) {
    val c = Bideng.colors
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(50))
            .background(if (selected) c.primaryContainer else Color.Transparent)
            .semantics { contentDescription = description }
    ) {
        Icon(icon, null, tint = when {
            !enabled -> c.muted.copy(alpha = 0.45f)
            selected -> c.onPrimaryContainer
            else -> c.text
        })
    }
}

@Composable
private fun ProgressRow(label: String, fraction: Float, onSeek: (Float) -> Unit) {
    val c = Bideng.colors
    var dragging by remember { mutableStateOf<Float?>(null) }
    Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Slider(
            value = dragging ?: fraction.coerceIn(0f, 1f),
            onValueChange = { dragging = it },
            onValueChangeFinished = { dragging?.let(onSeek); dragging = null },
            colors = bidengSliderColors(),
            modifier = Modifier.weight(1f).padding(horizontal = 14.dp)
        )
        Text("%${((dragging ?: fraction) * 100).toInt().coerceIn(0, 100)}", color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SettingsSection(
    voiceLabel: String,
    speed: Float,
    onSpeed: (Float) -> Unit,
    onSpeedDone: () -> Unit,
    sleepLabel: String,
    fullScreen: Boolean,
    onVoice: () -> Unit,
    onAppearance: () -> Unit,
    onLanguage: () -> Unit,
    onSleep: () -> Unit,
    onFullScreen: () -> Unit,
    onClose: () -> Unit
) {
    val s = LocalStrings.current
    val c = Bideng.colors
    Column(Modifier.fillMaxWidth().testTag("settingsSection")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.readingSettings, color = c.text, fontWeight = FontWeight.Medium, fontSize = 15.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp).clip(RoundedCornerShape(50)).background(c.surfaceAlt).semantics { contentDescription = s.close }) {
                Icon(Icons.Rounded.KeyboardArrowDown, null, tint = c.text)
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            val wide = maxWidth > 640.dp
            val voiceAndSpeed: @Composable (Modifier) -> Unit = { modifier ->
                Column(modifier) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surfaceAlt).clickable(onClick = onVoice)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.GraphicEq, null, tint = c.primary)
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text(s.voice, color = c.muted, fontSize = 12.sp)
                            Text(voiceLabel, color = c.text, fontWeight = FontWeight.Medium, fontSize = 15.sp, maxLines = 1)
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.muted)
                    }
                    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 10.dp)) {
                        Text(s.speechSpeed, color = c.muted, fontWeight = FontWeight.Medium, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text("${"%.2f".format(java.util.Locale.US, speed)}x", color = c.primary, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    }
                    Slider(value = speed, onValueChange = onSpeed, onValueChangeFinished = onSpeedDone, valueRange = 0.5f..2.0f, colors = bidengSliderColors())
                }
            }
            val tiles: @Composable (Modifier) -> Unit = { modifier ->
                Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingTile(Icons.Rounded.TextFields, s.appearance, onClick = onAppearance, modifier = Modifier.weight(1f))
                    SettingTile(Icons.Rounded.Language, s.languageLabel, onClick = onLanguage, modifier = Modifier.weight(1f))
                    SettingTile(Icons.Rounded.Bedtime, sleepLabel, onClick = onSleep, modifier = Modifier.weight(1f))
                    SettingTile(if (fullScreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen, s.focusMode, selected = fullScreen, onClick = onFullScreen, modifier = Modifier.weight(1f))
                }
            }
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                    voiceAndSpeed(Modifier.weight(1f))
                    tiles(Modifier.weight(1f))
                }
            } else {
                Column {
                    voiceAndSpeed(Modifier.fillMaxWidth())
                    tiles(Modifier.fillMaxWidth().padding(top = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun SettingTile(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false) {
    val c = Bideng.colors
    val tint = if (selected) c.onPrimaryContainer else c.text
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(if (selected) c.primaryContainer else c.surfaceAlt)
            .clickable(onClick = onClick).padding(vertical = 12.dp).semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Text(label, color = tint, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQuery: (String) -> Unit,
    count: Int,
    current: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    onFocus: (Boolean) -> Unit
) {
    val s = LocalStrings.current
    val c = Bideng.colors
    val focus = remember { FocusRequester() }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            placeholder = { Text(s.searchInText) },
            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = c.muted) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = fieldColors(),
            modifier = Modifier.weight(1f).focusRequester(focus).onFocusChanged { onFocus(it.isFocused) }.testTag("readerSearch")
        )
        Text(
            when {
                query.trim().length < 2 -> ""
                count == 0 -> s.searchNone
                else -> s.searchFound(current + 1, count)
            },
            color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp).widthIn(min = 64.dp)
        )
        ControlIcon(Icons.Rounded.KeyboardArrowUp, s.previousResult, enabled = count > 0, onClick = onPrevious)
        ControlIcon(Icons.Rounded.KeyboardArrowDown, s.nextResult, enabled = count > 0, onClick = onNext)
        ControlIcon(Icons.Rounded.Close, s.close, onClick = onClose)
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
    DisposableEffect(Unit) { onDispose { onFocus(false) } }
}

@Composable
private fun NoteBar(onCancel: () -> Unit, onAdd: () -> Unit) {
    val s = LocalStrings.current
    val c = Bideng.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(s.selectionHint, color = c.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        QuietButton(s.cancel, onCancel)
        Spacer(Modifier.width(6.dp))
        PrimaryButton(s.addNote, onAdd)
    }
}
