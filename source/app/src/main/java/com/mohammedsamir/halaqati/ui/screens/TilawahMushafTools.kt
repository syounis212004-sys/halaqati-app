package com.mohammedsamir.halaqati.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.quran.TilawahCategory
import com.mohammedsamir.halaqati.quran.TilawahMark

@Composable
internal fun TilawahMushafTools(
    side: Boolean, tool: Tool, busy: Boolean, canUndo: Boolean, canRedo: Boolean,
    onTool: (Tool) -> Unit, onUndo: () -> Unit, onRedo: () -> Unit,
) {
    val content: @Composable () -> Unit = {
        TextButton(onClick = { onTool(Tool.Tap) }, enabled = !busy) { Text(if (tool == Tool.Tap) "✓ كلمة" else "كلمة") }
        TextButton(onClick = { onTool(Tool.Highlight) }, enabled = !busy) { Text(if (tool == Tool.Highlight) "✓ نطاق" else "نطاق") }
        TextButton(onClick = { onTool(Tool.Erase) }, enabled = !busy) { Text(if (tool == Tool.Erase) "✓ محو" else "محو") }
        TextButton(onClick = onUndo, enabled = !busy && canUndo) { Text("تراجع") }
        TextButton(onClick = onRedo, enabled = !busy && canRedo) { Text("إعادة") }
    }
    if (side) Column(Modifier.width(92.dp).verticalScroll(rememberScrollState())) { content() }
    else Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = { onTool(Tool.Tap) }, enabled = !busy) { Text(if (tool == Tool.Tap) "✓ كلمة" else "كلمة") }
            TextButton(onClick = { onTool(Tool.Highlight) }, enabled = !busy) { Text(if (tool == Tool.Highlight) "✓ نطاق" else "نطاق") }
            TextButton(onClick = { onTool(Tool.Erase) }, enabled = !busy) { Text(if (tool == Tool.Erase) "✓ محو" else "محو") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = onUndo, enabled = !busy && canUndo) { Text("تراجع") }
            TextButton(onClick = onRedo, enabled = !busy && canRedo) { Text("إعادة") }
        }
    }
}

@Composable
internal fun TilawahWordEditor(
    ids: List<String>, marks: List<TilawahMark>, busy: Boolean, error: String?,
    onAdd: (TilawahCategory, String, String) -> Unit, onCorrect: (String) -> Unit,
    onRemove: (String) -> Unit, onDismiss: () -> Unit,
) {
    var note by remember(ids) { mutableStateOf("") }
    var subtype by remember(ids) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (ids.size == 1) "تحديد موضع الخطأ" else "تحديد ${ids.size} كلمة") },
        text = {
            Column(Modifier.heightIn(max = 410.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("كل ضغطة تسجّل تكرارًا واحدًا لكل كلمة محددة.", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedTextField(value = subtype, onValueChange = { subtype = it.take(80) }, label = { Text("التصنيف الفرعي (اختياري)") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
                OutlinedTextField(value = note, onValueChange = { note = it.take(500) }, label = { Text("ملاحظة (اختياري)") }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                listOf(true, false).forEach { major ->
                    Text(if (major) "لحن جلي" else "لحن خفي", fontWeight = FontWeight.Bold)
                    TilawahCategory.entries.filter { it.major == major }.forEach { category ->
                        TextButton(onClick = { onAdd(category, note, subtype) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Text("+ ${category.label}")
                        }
                    }
                }
                val selected = marks.filter { it.wordId in ids }
                if (selected.isNotEmpty()) {
                    Text("العلامات المسجلة", fontWeight = FontWeight.Bold)
                    selected.forEach { mark ->
                        Text("${mark.wordId} · ${mark.category.label} × ${mark.occurrences} · ${if (mark.corrected) "صُحح" else "مفتوح"}")
                        Row {
                            TextButton(onClick = { onCorrect(mark.markId) }, enabled = !busy) { Text(if (mark.corrected) "إعادة فتح" else "تم التصحيح") }
                            TextButton(onClick = { onRemove(mark.markId) }, enabled = !busy) { Text("حذف") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(if (busy) "جارٍ الحفظ…" else "تم") } },
    )
}
