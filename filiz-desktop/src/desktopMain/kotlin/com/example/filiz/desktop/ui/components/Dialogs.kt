package com.example.filiz.desktop.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.filiz.desktop.model.Note
import com.example.filiz.desktop.ui.LocalStrings
import com.example.filiz.desktop.ui.theme.Bideng
import com.example.filiz.desktop.ui.theme.ThemeMode
import com.example.filiz.desktop.ui.theme.bidengColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Yuvarlak köşeli, iç boşluklu, tek tip diyalog. */
@Composable
fun BidengDialog(
    title: String,
    onDismiss: () -> Unit,
    confirm: (@Composable () -> Unit)? = null,
    dismiss: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(min = 360.dp, max = 520.dp),
        shape = RoundedCornerShape(28.dp),
        containerColor = Bideng.colors.surface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = Bideng.colors.text) },
        text = { content() },
        confirmButton = { confirm?.invoke() },
        dismissButton = { dismiss?.invoke() }
    )
}

@Composable
fun PrimaryButton(label: String, onClick: () -> Unit, danger: Boolean = false, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (danger) Bideng.colors.danger else Bideng.colors.primary,
            contentColor = if (danger) Color.White else Bideng.colors.onPrimary
        )
    ) { Text(label) }
}

@Composable
fun QuietButton(label: String, onClick: () -> Unit, color: Color = Bideng.colors.primary) {
    TextButton(onClick = onClick, shape = RoundedCornerShape(50)) { Text(label, color = color) }
}

@Composable
fun ConfirmDialog(title: String, message: String, confirmLabel: String, danger: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    BidengDialog(
        title = title,
        onDismiss = onDismiss,
        confirm = { PrimaryButton(confirmLabel, { onConfirm(); onDismiss() }, danger = danger) },
        dismiss = { QuietButton(s.cancel, onDismiss) }
    ) {
        Text(message, color = Bideng.colors.muted, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Seçeneklerden birini seçtiren liste (ses, dil, uyku zamanlayıcısı). */
@Composable
fun ChoiceDialog(
    title: String,
    options: List<Pair<String, String?>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val s = LocalStrings.current
    BidengDialog(title = title, onDismiss = onDismiss, confirm = { QuietButton(s.close, onDismiss) }) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEachIndexed { index, (label, subtitle) ->
                val isSelected = index == selected
                val textColor = if (isSelected) Bideng.colors.onPrimaryContainer else Bideng.colors.text
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) Bideng.colors.primaryContainer else Color.Transparent)
                        .clickable { onSelect(index); onDismiss() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(label, color = textColor, fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal, fontSize = 15.sp)
                        if (!subtitle.isNullOrBlank()) {
                            Text(subtitle, color = if (isSelected) textColor.copy(alpha = 0.75f) else Bideng.colors.muted, fontSize = 12.5.sp)
                        }
                    }
                    if (isSelected) Icon(Icons.Rounded.Check, null, tint = textColor)
                }
            }
        }
    }
}

@Composable
fun NotesDialog(
    notes: List<Note>,
    showDocumentNames: Boolean,
    documentName: (String) -> String,
    onOpen: (Note) -> Unit,
    onDelete: (Note) -> Unit,
    onDeleteAll: () -> Unit,
    onExport: () -> Unit,
    onDismiss: () -> Unit
) {
    val s = LocalStrings.current
    val format = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
    BidengDialog(
        title = s.notes,
        onDismiss = onDismiss,
        confirm = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (notes.isNotEmpty()) {
                    QuietButton(s.deleteAll, onDeleteAll, color = Bideng.colors.danger)
                    Spacer(Modifier.width(4.dp))
                    PrimaryButton(s.exportNotes, onExport)
                    Spacer(Modifier.width(4.dp))
                }
                QuietButton(s.close, onDismiss)
            }
        }
    ) {
        if (notes.isEmpty()) {
            Text(s.notesEmpty, color = Bideng.colors.muted, style = MaterialTheme.typography.bodyLarge)
        } else {
            LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val grouped = if (showDocumentNames) notes.groupBy { it.documentId } else mapOf("" to notes)
                grouped.forEach { (docId, group) ->
                    if (showDocumentNames) {
                        item(key = "h$docId") {
                            Text(documentName(docId), color = Bideng.colors.primary, fontWeight = FontWeight.Medium, fontSize = 13.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
                        }
                    }
                    items(group, key = { it.id }) { note ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Bideng.colors.surfaceAlt)
                                .clickable { onOpen(note) }
                                .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("“${note.text.trim()}”", color = Bideng.colors.text, maxLines = 4, overflow = TextOverflow.Ellipsis, fontSize = 14.5.sp)
                                Text(format.format(Date(note.createdAt)), color = Bideng.colors.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                            IconButton(onClick = { onDelete(note) }, modifier = Modifier.semantics { contentDescription = s.delete }) {
                                Icon(Icons.Rounded.DeleteOutline, null, tint = Bideng.colors.muted)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Tema, yazı boyutu, satır aralığı ve yazı tipi tek pencerede; değişiklikler anında metne yansır. */
@Composable
fun AppearanceDialog(
    themeMode: ThemeMode,
    fontSize: Int,
    lineSpacing: Float,
    fontFamily: String,
    onTheme: (ThemeMode) -> Unit,
    onFontSize: (Int) -> Unit,
    onLineSpacing: (Float) -> Unit,
    onFontFamily: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val s = LocalStrings.current
    BidengDialog(title = s.appearance, onDismiss = onDismiss, confirm = { PrimaryButton(s.ok, onDismiss) }) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionLabel(s.theme)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemeMode.entries.forEach { mode ->
                    val c = bidengColors(mode)
                    val selected = mode == themeMode
                    Surface(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).clickable { onTheme(mode) },
                        color = c.readerBackground,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Bideng.colors.primary else Bideng.colors.outline)
                    ) {
                        Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Aa", color = c.readerText, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                            Text(s.themeName(mode.id), color = c.muted, fontSize = 12.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            SectionLabel(s.fontSize, "$fontSize")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("A", color = Bideng.colors.muted, fontSize = 13.sp)
                Slider(
                    value = fontSize.toFloat(), onValueChange = { onFontSize(it.toInt()) }, valueRange = 14f..36f, steps = 21,
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp), colors = bidengSliderColors()
                )
                Text("A", color = Bideng.colors.muted, fontSize = 21.sp)
            }
            SectionLabel(s.lineSpacing, "${(lineSpacing * 100).toInt()}%")
            Slider(
                value = lineSpacing, onValueChange = onLineSpacing, valueRange = 1.2f..2.2f,
                colors = bidengSliderColors()
            )
            SectionLabel(s.fontFamily)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("sans-serif" to "Sans", "serif" to "Serif", "monospace" to "Mono").forEach { (id, label) ->
                    val selected = id == fontFamily
                    Surface(
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onFontFamily(id) },
                        color = if (selected) Bideng.colors.primaryContainer else Bideng.colors.surfaceAlt,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            label,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
                            color = if (selected) Bideng.colors.onPrimaryContainer else Bideng.colors.text,
                            fontFamily = when (id) { "serif" -> FontFamily.Serif; "monospace" -> FontFamily.Monospace; else -> FontFamily.SansSerif }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, value: String? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = Bideng.colors.muted, fontWeight = FontWeight.Medium, fontSize = 13.sp, modifier = Modifier.weight(1f))
        if (value != null) Text(value, color = Bideng.colors.primary, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    }
}

@Composable
fun bidengSliderColors() = SliderDefaults.colors(
    thumbColor = Bideng.colors.primary,
    activeTrackColor = Bideng.colors.primary,
    inactiveTrackColor = Bideng.colors.outline,
    activeTickColor = Color.Transparent,
    inactiveTickColor = Color.Transparent
)

@Composable
fun CloseIcon(onClick: () -> Unit) {
    val s = LocalStrings.current
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = s.close }) {
        Icon(Icons.Rounded.Close, null, tint = Bideng.colors.text)
    }
}

@Composable
fun TypeBadge(type: String, color: Color, size: Int = 48) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = if (Bideng.colors.isDark) 0.18f else 0.11f)),
        contentAlignment = Alignment.Center
    ) {
        Text(type, color = color, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.4.sp)
    }
}
