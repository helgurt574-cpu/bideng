package com.example.filiz.desktop.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.filiz.desktop.model.LibraryItem
import com.example.filiz.desktop.service.PreferenceService
import com.example.filiz.desktop.ui.AppState
import com.example.filiz.desktop.ui.LocalStrings
import com.example.filiz.desktop.ui.components.PrimaryButton
import com.example.filiz.desktop.ui.components.TypeBadge
import com.example.filiz.desktop.ui.theme.Bideng
import com.example.filiz.desktop.ui.theme.ThemeMode
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun themeIcon(mode: ThemeMode): ImageVector = when (mode) {
    ThemeMode.Light -> Icons.Rounded.LightMode
    ThemeMode.Sepia -> Icons.Rounded.LocalCafe
    ThemeMode.Dark -> Icons.Rounded.DarkMode
}

@Composable
fun ToolbarIcon(icon: ImageVector, description: String, onClick: () -> Unit, tint: Color = Bideng.colors.text) {
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = description }) {
        Icon(icon, null, tint = tint)
    }
}

@Composable
fun LibraryScreen(state: AppState, onImport: () -> Unit) {
    val s = LocalStrings.current
    val c = Bideng.colors
    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxSize()) {
            // Üst çubuk
            Row(
                Modifier.fillMaxWidth().padding(start = 28.dp, end = 16.dp, top = 18.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.searchActive) {
                    ToolbarIcon(Icons.AutoMirrored.Rounded.ArrowBack, s.close, { state.searchActive = false; state.searchQuery = "" })
                    val focus = remember { FocusRequester() }
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = { state.searchQuery = it },
                        placeholder = { Text(s.searchLibraryHint) },
                        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = c.muted) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = fieldColors(),
                        keyboardOptions = KeyboardOptions.Default,
                        keyboardActions = KeyboardActions.Default,
                        modifier = Modifier.weight(1f).widthIn(max = 640.dp).focusRequester(focus).testTag("librarySearch")
                    )
                    LaunchedEffect(Unit) { focus.requestFocus() }
                    Spacer(Modifier.weight(0.001f))
                } else {
                    Column(Modifier.weight(1f)) {
                        Text(s.library, style = MaterialTheme.typography.headlineMedium, color = c.text)
                        Text(s.libraryCount(state.items.size), color = c.muted, fontSize = 13.sp)
                    }
                    ToolbarIcon(Icons.Rounded.Search, s.search, { state.searchActive = true })
                    ToolbarIcon(themeIcon(state.themeMode), s.theme, state::cycleTheme)
                    ToolbarIcon(Icons.Rounded.StickyNote2, s.notes, { state.notesDialogOpen = true })
                }
            }
            if (state.busyMessage != null) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 28.dp).height(3.dp), color = c.primary, trackColor = c.surfaceAlt)
            }

            if (state.items.isEmpty()) {
                EmptyLibrary(onImport)
            } else {
                val visible = state.visibleItems
                val searching = state.searchActive || state.searchQuery.isNotBlank()
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 380.dp),
                    modifier = Modifier.fillMaxSize().testTag("libraryGrid"),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 110.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val last = state.lastOpened
                    if (!searching && last != null) {
                        item(span = { GridItemSpan(maxLineSpan) }) { ContinueCard(state, last) }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            if (searching) s.resultsLabel(visible.size) else s.documentsLabel(state.items.size),
                            color = c.muted, fontWeight = FontWeight.Medium, fontSize = 13.sp,
                            modifier = Modifier.padding(start = 4.dp, top = 10.dp)
                        )
                    }
                    if (visible.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(s.noResults, color = c.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(40.dp))
                        }
                    }
                    items(visible, key = { it.id }) { item -> DocumentCard(state, item) }
                }
            }
        }

        if (state.items.isNotEmpty() && !state.searchActive) {
            ExtendedFloatingActionButton(
                onClick = onImport,
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text(s.addDocument, fontSize = 15.sp) },
                containerColor = c.primary,
                contentColor = c.onPrimary,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.align(Alignment.BottomEnd).padding(28.dp).testTag("addDocument")
            )
        }
    }
}

@Composable
private fun EmptyLibrary(onImport: () -> Unit) {
    val s = LocalStrings.current
    val c = Bideng.colors
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 460.dp).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(104.dp).clip(CircleShape).background(c.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.LibraryBooks, null, tint = c.onPrimaryContainer, modifier = Modifier.size(48.dp))
            }
            Spacer(Modifier.height(22.dp))
            Text(s.emptyTitle, style = MaterialTheme.typography.titleLarge, color = c.text)
            Spacer(Modifier.height(8.dp))
            Text(s.emptyBody, color = c.muted, textAlign = TextAlign.Center, lineHeight = 21.sp)
            Spacer(Modifier.height(24.dp))
            PrimaryButton("+  " + s.addDocument, onImport)
        }
    }
}

@Composable
private fun ContinueCard(state: AppState, item: LibraryItem) {
    val s = LocalStrings.current
    val c = Bideng.colors
    val paused = PreferenceService.getPausedIndex(item.id) >= 0
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).clickable { state.open(item) }.testTag("continueCard"),
        color = c.primaryContainer,
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(Modifier.padding(horizontal = 22.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (paused) s.pausedReading else s.continueReading, color = c.onPrimaryContainer.copy(alpha = 0.75f), fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                Text(item.name, color = c.onPrimaryContainer, fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { progressOf(item) / 100f },
                        modifier = Modifier.weight(1f).height(5.dp),
                        color = c.primary, trackColor = c.onPrimaryContainer.copy(alpha = 0.15f), strokeCap = StrokeCap.Round
                    )
                    Text("%${progressOf(item)}", color = c.onPrimaryContainer.copy(alpha = 0.8f), fontSize = 12.sp, modifier = Modifier.padding(start = 10.dp))
                }
            }
            Spacer(Modifier.width(16.dp))
            FilledIconButton(
                onClick = { state.open(item, autoResume = paused) },
                modifier = Modifier.size(54.dp).semantics { contentDescription = if (paused) s.resume else s.continueReading },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = c.primary, contentColor = c.onPrimary)
            ) {
                Icon(if (paused) Icons.Rounded.PlayArrow else Icons.AutoMirrored.Rounded.ArrowForward, null)
            }
        }
    }
}

@Composable
private fun DocumentCard(state: AppState, item: LibraryItem) {
    val s = LocalStrings.current
    val c = Bideng.colors
    val type = typeLabel(item.name)
    val color = typeColor(type)
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { state.open(item) }.testTag("doc:${item.name}"),
        color = c.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (item.id == state.document?.item?.id) c.primary.copy(alpha = 0.55f) else c.outline)
    ) {
        Row(Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TypeBadge(type, color)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(item.name, color = c.text, fontWeight = FontWeight.Medium, fontSize = 15.5.sp, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${s.readPercent(progressOf(item))} · ${lastReadLabel(item, s.notOpened)}",
                    color = c.muted, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(top = 3.dp)
                )
                LinearProgressIndicator(
                    progress = { progressOf(item) / 100f },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(4.dp),
                    color = color, trackColor = c.surfaceAlt, strokeCap = StrokeCap.Round
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.semantics { contentDescription = "${s.more}: ${item.name}" }) {
                    Icon(Icons.Rounded.MoreVert, null, tint = c.muted)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, modifier = Modifier.background(c.surface)) {
                    DropdownMenuItem(
                        text = { Text(s.rename, color = c.text) },
                        leadingIcon = { Icon(Icons.Rounded.Edit, null, tint = c.text) },
                        onClick = { menuOpen = false; state.renameTarget = item }
                    )
                    DropdownMenuItem(
                        text = { Text(s.delete, color = c.danger) },
                        leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = c.danger) },
                        onClick = { menuOpen = false; state.deleteTarget = item }
                    )
                }
            }
        }
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Bideng.colors.primary,
    unfocusedBorderColor = Bideng.colors.outline,
    focusedContainerColor = Bideng.colors.surfaceAlt,
    unfocusedContainerColor = Bideng.colors.surfaceAlt,
    cursorColor = Bideng.colors.primary,
    focusedTextColor = Bideng.colors.text,
    unfocusedTextColor = Bideng.colors.text
)

fun progressOf(item: LibraryItem): Int =
    if (item.textLength <= 0) 0 else ((item.lastIndex.toLong() * 100) / item.textLength).toInt().coerceIn(0, 100)

private fun lastReadLabel(item: LibraryItem, notOpened: String): String =
    if (item.lastReadAt <= 0) notOpened else SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(item.lastReadAt))

fun typeLabel(name: String): String = when (File(name).extension.lowercase()) {
    "pdf" -> "PDF"
    "epub" -> "EPUB"
    "doc", "docx" -> "WORD"
    "odt" -> "ODT"
    "rtf" -> "RTF"
    "html", "htm" -> "HTML"
    "txt" -> "TXT"
    else -> "DOC"
}

fun typeColor(type: String): Color = when (type) {
    "PDF" -> Color(0xFFE15555)
    "WORD" -> Color(0xFF4285E6)
    "EPUB" -> Color(0xFF20A578)
    "TXT" -> Color(0xFFD6911E)
    "HTML" -> Color(0xFF14A0BE)
    "ODT" -> Color(0xFF8264DC)
    else -> Color(0xFF6E788C)
}
