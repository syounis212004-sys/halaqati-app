package com.mohammedsamir.halaqati.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mohammedsamir.halaqati.quran.MushafEngine
import com.mohammedsamir.halaqati.quran.MushafOfflinePlan
import com.mohammedsamir.halaqati.quran.QuranMetadata
import kotlin.math.max
import kotlin.math.min

/** Go to a page, to the assigned range ("ضمن المقرر"), a bookmark, a juz or a surah (with an optional ayah). */
@Composable
internal fun MushafJumpDialog(page: Int, rangeStart: Int?, rangeEnd: Int?, onGo: (Int) -> Unit, onDismiss: () -> Unit, bookmarks: List<Int> = emptyList()) {
    var input by remember { mutableStateOf(page.toString()) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        ) {
            Column(Modifier.padding(top = 18.dp, bottom = 8.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("الانتقال", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(jumpHere(page), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onDismiss) { Text("إغلاق") }
                }
                Spacer(Modifier.size(8.dp))
                MushafJumpBody(page, rangeStart, rangeEnd, input, { input = it }, onGo, bookmarks)
            }
        }
    }
}

internal fun jumpHere(page: Int): String {
    val juz = (1..30).firstOrNull { page in MushafOfflinePlan.juzPages(it) } ?: 1
    val sura = QuranMetadata.pageStart(page.coerceIn(1, 604)).surah
    return "أنت في صفحة ${digits(page)} · الجزء ${digits(juz)} · سورة ${QuranMetadata.surahName(sura)}"
}

private val jumpTabs = listOf("الصفحة", "السور", "الأجزاء")

@Composable
internal fun MushafJumpBody(page: Int, rangeStart: Int?, rangeEnd: Int?, input: String, onInput: (String) -> Unit, onGo: (Int) -> Unit, bookmarks: List<Int> = emptyList()) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val rangePages = remember(rangeStart, rangeEnd) {
        if (rangeStart == null) emptyList() else (min(rangeStart, rangeEnd ?: rangeStart)..max(rangeStart, rangeEnd ?: rangeStart)).toList()
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (rangePages.isNotEmpty()) RangeCard(rangePages, page, onGo)
        TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface) {
            jumpTabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal) }) }
        }
        Box(Modifier.fillMaxWidth().heightIn(min = 280.dp, max = 400.dp).padding(horizontal = 18.dp)) {
            when (tab) {
                0 -> PageTab(page, input, onInput, onGo, bookmarks)
                1 -> SurahTab(onGo)
                else -> JuzTab(page, onGo)
            }
        }
    }
}

/** The assigned range as one card: where it starts and ends, and a chip per page. */
@Composable
private fun RangeCard(pages: List<Int>, page: Int, onGo: (Int) -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ضمن المقرر", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                Text(
                    if (pages.size == 1) "صفحة ${digits(pages.first())}" else "ص ${digits(pages.first())} ← ${digits(pages.last())} (${digits(pages.size)} صفحة)",
                    style = MaterialTheme.typography.bodySmall, maxLines = 1,
                )
            }
            if (pages.size <= 12) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pages.forEach { p -> PageChip(digits(p), selected = p == page) { onGo(p) } }
            } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PageChip("أول المقرر", selected = false) { onGo(pages.first()) }
                PageChip("آخر المقرر", selected = false) { onGo(pages.last()) }
            }
        }
    }
}

@Composable
private fun PageChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) { Text(label, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), maxLines = 1, softWrap = false) }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PageTab(page: Int, input: String, onInput: (String) -> Unit, onGo: (Int) -> Unit, bookmarks: List<Int>) {
    val typed = input.toIntOrNull()
    val valid = typed != null && typed in 1..MushafEngine.TOTAL_PAGES
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.size(2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { onInput(it.filter(Char::isDigit).take(3)) }, singleLine = true, modifier = Modifier.weight(1f),
                label = { Text("رقم الصفحة (١–٦٠٤)") }, isError = input.isNotEmpty() && !valid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (valid) onGo(typed!!) }),
            )
            Button(enabled = valid, onClick = { onGo(typed!!) }) { Text("انتقال") }
        }
        Slider(
            value = (typed ?: page).coerceIn(1, MushafEngine.TOTAL_PAGES).toFloat(), onValueChange = { onInput(it.toInt().toString()) },
            valueRange = 1f..MushafEngine.TOTAL_PAGES.toFloat(),
        )
        if (bookmarks.isNotEmpty()) {
            Text("العلامات المرجعية", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                bookmarks.forEach { b -> PageChip("ص ${digits(b)}", selected = b == page) { onGo(b) } }
            }
        }
    }
}

@Composable
private fun SurahTab(onGo: (Int) -> Unit) {
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<Int?>(null) }
    var ayahInput by remember { mutableStateOf("") }
    val list = remember(query) {
        val q = query.trim()
        (1..114).filter { q.isEmpty() || QuranMetadata.surahName(it).contains(q) || it.toString() == q }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Spacer(Modifier.size(2.dp))
        OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text("ابحث عن سورة") })
        LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(list, key = { it }) { sn ->
                val open = picked == sn
                Surface(
                    onClick = { picked = if (open) null else sn; ayahInput = "" }, shape = RoundedCornerShape(16.dp),
                    color = if (open) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(34.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                                Text(digits(sn), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                            }
                            Text(QuranMetadata.surahName(sn), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
                            Text("${digits(QuranMetadata.maxAyah(sn))} آية · ص ${digits(QuranMetadata.pageFor(sn, 1))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                        }
                        if (open) {
                            val max = QuranMetadata.maxAyah(sn)
                            val ayah = ayahInput.toIntOrNull()
                            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = ayahInput, onValueChange = { ayahInput = it.filter(Char::isDigit).take(3) }, singleLine = true, modifier = Modifier.weight(1f),
                                    label = { Text("رقم الآية (١–${digits(max)})") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    isError = ayahInput.isNotEmpty() && (ayah == null || ayah !in 1..max),
                                )
                                Button(enabled = ayah != null && ayah in 1..max, onClick = { onGo(QuranMetadata.pageFor(sn, ayah!!)) }) { Text("اذهب إلى الآية") }
                            }
                            TextButton(onClick = { onGo(QuranMetadata.pageFor(sn, 1)) }) { Text("بداية السورة") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JuzTab(page: Int, onGo: (Int) -> Unit) {
    val cur = (1..30).firstOrNull { page in MushafOfflinePlan.juzPages(it) } ?: 1
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Spacer(Modifier.size(2.dp))
        (1..30).chunked(5).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { j ->
                    val sel = j == cur
                    Surface(
                        onClick = { onGo(MushafOfflinePlan.juzPages(j).first) }, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f),
                        color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    ) {
                        Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(digits(j), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            Text("ص ${digits(MushafOfflinePlan.juzPages(j).first)}", style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
                        }
                    }
                }
                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Text("اضغط الجزء للانتقال إلى أول صفحة فيه", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
