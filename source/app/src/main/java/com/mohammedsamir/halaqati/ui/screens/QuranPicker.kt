@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.mohammedsamir.halaqati.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohammedsamir.halaqati.quran.QuranCore
import com.mohammedsamir.halaqati.quran.QuranLocation
import com.mohammedsamir.halaqati.quran.QuranMetadata
import com.mohammedsamir.halaqati.quran.QuranRangeRequest
import com.mohammedsamir.halaqati.quran.QuranRangeResult
import com.mohammedsamir.halaqati.quran.QuranResolvedTraversal
import com.mohammedsamir.halaqati.quran.QuranSelection
import com.mohammedsamir.halaqati.quran.QuranTraversalDirection
import com.mohammedsamir.halaqati.ui.components.ProFilterChip
import com.mohammedsamir.halaqati.ui.components.HalaqatiLazySelectField
import java.util.Locale

private fun quranPagesLabel(value: Double): String {
    val raw = String.format(Locale.US, "%.2f", value)
    return raw.trimEnd('0').trimEnd('.').ifBlank { "0" }
}

private fun traversalLabel(value: QuranResolvedTraversal): String = when (value) {
    QuranResolvedTraversal.REVERSE_SURAH_ORDER -> "من السور الأعلى رقمًا إلى الأقل، والآيات داخل كل سورة تصاعدية"
    QuranResolvedTraversal.REVERSE_WITHIN_SURAH -> "تم حفظ ترتيب الآيات كما أدخلته"
    QuranResolvedTraversal.PAGE_ORDER_REVERSE -> "اتجاه النطاق من اليمين إلى اليسار"
    QuranResolvedTraversal.PAGE_ORDER_FORWARD -> "اتجاه النطاق حسب ترتيب المصحف"
    QuranResolvedTraversal.EXPLICIT_SEGMENTS -> "مقاطع مختارة حسب النشاط والمسار"
    QuranResolvedTraversal.FORWARD_QURAN -> "حسب ترتيب المصحف"
}

private fun surahsForJuz(juz: Int): List<Int> {
    val from = QuranMetadata.juzStart(juz).surah
    val to = QuranMetadata.juzEnd(juz).surah
    return (from..to).toList()
}

private fun pageAyahs(page: Int): List<QuranLocation> {
    if (page !in 1..QuranMetadata.PAGE_COUNT) return emptyList()
    val from = QuranMetadata.pageStarts[page]
    val to = QuranMetadata.pageStarts[page + 1] - 1
    return (from..to).map(QuranMetadata::location)
}

@Composable
private fun QuranModeTab(
    selected: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f),
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(14.dp),
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.heightIn(min = 58.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(label, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun QuranSurahChoice(
    surah: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(12.dp),
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(surah.toString(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(QuranMetadata.surahName(surah), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun PickerDropdown(
    label: String,
    value: Int,
    options: List<Pair<Int, String>>,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    HalaqatiLazySelectField(
        label = label,
        selectedKey = value.toString(),
        options = options.map { it.first.toString() to it.second },
        onSelected = { key -> key.toIntOrNull()?.let(onSelected) },
        modifier = modifier.fillMaxWidth(),
        searchable = options.size > 18,
    )
}

@Composable
private fun QuranResultCard(range: QuranRangeResult) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(range.displayLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${quranPagesLabel(range.exactPages)} صفحة", style = MaterialTheme.typography.bodyMedium)
                Text("${range.ayahCount} آية", style = MaterialTheme.typography.bodyMedium)
                Text("${range.surahCount} سورة", style = MaterialTheme.typography.bodyMedium)
            }
            Text(traversalLabel(range.resolvedTraversal), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("QCF4 · مصحف المدينة · 604 صفحة · 15 سطرًا", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * RC8.3 Quran picker. The visible language is always Surah/Ayah-first.
 * Page mode is only a convenient locator: after choosing each page the user chooses
 * the exact ayah on that page, and the engine stores/calculates the exact QCF4 range.
 */
@Composable
fun QuranRangePickerDialog(
    initialStartPage: Int? = null,
    initialEndPage: Int? = null,
    initialStartSurah: Int? = null,
    initialStartAyah: Int? = null,
    initialEndSurah: Int? = null,
    initialEndAyah: Int? = null,
    track: String,
    activityType: String,
    direction: QuranTraversalDirection = QuranTraversalDirection.AUTO_BY_ACTIVITY,
    onDismiss: () -> Unit,
    onConfirm: (QuranRangeResult) -> Unit,
) {
    val hasExactInitial = initialStartSurah != null && initialStartAyah != null && initialEndSurah != null && initialEndAyah != null
    var mode by remember { mutableStateOf(if (hasExactInitial) "ayahs" else if (initialStartPage != null || initialEndPage != null) "pages" else "ayahs") }
    val defaultDirection = remember(track, activityType, direction) {
        when {
            direction != QuranTraversalDirection.AUTO_BY_ACTIVITY -> direction
            track == "general_hifz" && activityType in setOf("new_hifz", "hifz") -> QuranTraversalDirection.REVERSE_SURAH_ORDER
            track == "tathbit" -> QuranTraversalDirection.FORWARD_QURAN
            else -> QuranTraversalDirection.AUTO_BY_ACTIVITY
        }
    }
    var selectedDirection by remember(track, activityType, direction) { mutableStateOf(defaultDirection) }
    var surahQuery by remember { mutableStateOf("") }

    var selectedJuzFilter by remember { mutableIntStateOf(initialStartSurah?.let { QuranMetadata.juzFor(it, initialStartAyah ?: 1) } ?: if (selectedDirection == QuranTraversalDirection.REVERSE_SURAH_ORDER) 30 else 1) }
    var startSurah by remember { mutableIntStateOf(initialStartSurah ?: (surahsForJuz(selectedJuzFilter).firstOrNull() ?: 78)) }
    var startAyah by remember { mutableIntStateOf((initialStartAyah ?: 1).coerceIn(1, QuranMetadata.maxAyah(startSurah))) }
    var endSurah by remember { mutableIntStateOf(initialEndSurah ?: startSurah) }
    var endAyah by remember { mutableIntStateOf((initialEndAyah ?: QuranMetadata.maxAyah(endSurah)).coerceIn(1, QuranMetadata.maxAyah(endSurah))) }

    var startPageText by remember(initialStartPage) { mutableStateOf((initialStartPage ?: QuranMetadata.pageFor(startSurah, startAyah)).toString()) }
    var endPageText by remember(initialEndPage) { mutableStateOf((initialEndPage ?: QuranMetadata.pageFor(endSurah, endAyah)).toString()) }
    var startPageLocation by remember { mutableStateOf<QuranLocation?>(null) }
    var endPageLocation by remember { mutableStateOf<QuranLocation?>(null) }

    val selectedSurahs = remember { mutableStateListOf<Int>() }
    val selectedJuz = remember { mutableStateListOf<Int>() }

    LaunchedEffect(startSurah) { startAyah = startAyah.coerceIn(1, QuranMetadata.maxAyah(startSurah)) }
    LaunchedEffect(endSurah) { endAyah = endAyah.coerceIn(1, QuranMetadata.maxAyah(endSurah)) }
    LaunchedEffect(startPageText) {
        val page = startPageText.toIntOrNull()?.takeIf { it in 1..604 }
        val options = page?.let(::pageAyahs).orEmpty()
        startPageLocation = options.firstOrNull { it.surah == initialStartSurah && it.ayah == initialStartAyah } ?: options.firstOrNull()
    }
    LaunchedEffect(endPageText) {
        val page = endPageText.toIntOrNull()?.takeIf { it in 1..604 }
        val options = page?.let(::pageAyahs).orEmpty()
        endPageLocation = options.firstOrNull { it.surah == initialEndSurah && it.ayah == initialEndAyah } ?: options.lastOrNull()
    }

    val selectionAndInput = remember(
        mode, startSurah, startAyah, endSurah, endAyah, startPageText, endPageText,
        startPageLocation, endPageLocation, selectedSurahs.toList(), selectedJuz.toList(),
    ) {
        when (mode) {
            "pages" -> {
                val sp = startPageText.toIntOrNull()
                val ep = endPageText.toIntOrNull()
                val a = startPageLocation
                val b = endPageLocation
                if (sp != null && ep != null && sp in 1..604 && ep in 1..604 && a != null && b != null) {
                    QuranSelection.Ayahs(a, b) to "pages"
                } else null
            }
            "surahs" -> selectedSurahs.takeIf { it.isNotEmpty() }?.let { QuranSelection.Surahs(it.toList()) to "surahs" }
            "juz" -> selectedJuz.takeIf { it.isNotEmpty() }?.let { QuranSelection.Juz(it.toList()) to "juz" }
            else -> QuranSelection.Ayahs(QuranLocation(startSurah, startAyah), QuranLocation(endSurah, endAyah)) to "ayahs"
        }
    }

    val attempt = remember(selectionAndInput, track, activityType, selectedDirection) {
        runCatching {
            selectionAndInput?.let { (selection, inputMethod) ->
                QuranCore.engine().calculate(QuranRangeRequest(track, activityType, selection, selectedDirection)).copy(inputMethod = inputMethod)
            }
        }
    }
    val range = attempt.getOrNull()
    val rangeError = attempt.exceptionOrNull()?.message

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اختيار المقرر من المصحف", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 660.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    QuranModeTab(mode == "ayahs", "آية", Icons.Default.MenuBook, { mode = "ayahs" }, Modifier.weight(1f))
                    QuranModeTab(mode == "pages", "صفحات", Icons.Default.Description, { mode = "pages" }, Modifier.weight(1f))
                    QuranModeTab(mode == "surahs", "سور", Icons.Default.FormatListNumbered, { mode = "surahs" }, Modifier.weight(1f))
                    QuranModeTab(mode == "juz", "أجزاء", Icons.Default.Apps, { mode = "juz" }, Modifier.weight(1f))
                }

                if (track in setOf("general_hifz", "tathbit") || activityType in setOf("new_hifz", "hifz", "tathbit", "review_near", "review_far")) {
                    Text("اتجاه الحفظ / التقدم", style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ProFilterChip(
                            selectedDirection == QuranTraversalDirection.REVERSE_SURAH_ORDER,
                            { selectedDirection = QuranTraversalDirection.REVERSE_SURAH_ORDER },
                            "من الناس إلى البقرة",
                            Modifier.weight(1f),
                        )
                        ProFilterChip(
                            selectedDirection == QuranTraversalDirection.FORWARD_QURAN,
                            { selectedDirection = QuranTraversalDirection.FORWARD_QURAN },
                            "من البقرة إلى الناس",
                            Modifier.weight(1f),
                        )
                    }
                    Text(
                        if (selectedDirection == QuranTraversalDirection.REVERSE_SURAH_ORDER)
                            "بعد نهاية السورة ننتقل إلى السورة السابقة رقمًا، مع بقاء الآيات داخل السورة تصاعدية."
                        else "نتقدم حسب ترتيب المصحف من السورة الأقل رقمًا إلى الأعلى، والآيات داخل السورة تصاعدية.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                when (mode) {
                    "pages" -> {
                        Text("اختر الصفحة، ثم اختر الآية الدقيقة داخلها. لن تُحسب الصفحة كاملة إلا إذا اخترت أول آية وآخر آية فيها.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = startPageText,
                                onValueChange = { startPageText = it.filter(Char::isDigit).take(3) },
                                label = { Text("صفحة البداية") },
                                modifier = Modifier.weight(1f), singleLine = true,
                            )
                            OutlinedTextField(
                                value = endPageText,
                                onValueChange = { endPageText = it.filter(Char::isDigit).take(3) },
                                label = { Text("صفحة النهاية") },
                                modifier = Modifier.weight(1f), singleLine = true,
                            )
                        }
                        val startOptions = startPageText.toIntOrNull()?.takeIf { it in 1..604 }?.let(::pageAyahs).orEmpty()
                        val endOptions = endPageText.toIntOrNull()?.takeIf { it in 1..604 }?.let(::pageAyahs).orEmpty()
                        if (startOptions.isNotEmpty()) {
                            PickerDropdown(
                                "آية البداية في الصفحة",
                                value = startOptions.indexOf(startPageLocation).takeIf { it >= 0 } ?: 0,
                                options = startOptions.mapIndexed { index, loc -> index to "${QuranMetadata.surahName(loc.surah)} · آية ${loc.ayah}" },
                                onSelected = { startPageLocation = startOptions[it] },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (endOptions.isNotEmpty()) {
                            PickerDropdown(
                                "آية النهاية في الصفحة",
                                value = endOptions.indexOf(endPageLocation).takeIf { it >= 0 } ?: (endOptions.lastIndex.coerceAtLeast(0)),
                                options = endOptions.mapIndexed { index, loc -> index to "${QuranMetadata.surahName(loc.surah)} · آية ${loc.ayah}" },
                                onSelected = { endPageLocation = endOptions[it] },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    "surahs" -> {
                        Text("يمكن اختيار سور غير متجاورة؛ اضغط السورة لإضافتها أو إزالتها.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = surahQuery,
                            onValueChange = { surahQuery = it },
                            label = { Text("بحث عن سورة") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val visibleSurahs = (1..114).filter { s ->
                            surahQuery.isBlank() || s.toString().contains(surahQuery.trim()) || QuranMetadata.surahName(s).contains(surahQuery.trim(), ignoreCase = true)
                        }
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            visibleSurahs.chunked(2).forEach { pair ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    pair.forEach { s ->
                                        val selected = s in selectedSurahs
                                        QuranSurahChoice(
                                            surah = s,
                                            selected = selected,
                                            onClick = { if (selected) selectedSurahs.remove(s) else selectedSurahs.add(s) },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                        Text(if (selectedSurahs.isEmpty()) "لم يتم اختيار سور" else "المحدد: ${selectedSurahs.joinToString { QuranMetadata.surahName(it) }}")
                    }
                    "juz" -> {
                        Text("يمكن اختيار جزء أو أكثر، حتى لو كانت الأجزاء غير متجاورة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(Modifier.fillMaxWidth(), maxItemsInEachRow = 5, horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            (1..30).forEach { j ->
                                val selected = j in selectedJuz
                                ProFilterChip(selected, { if (selected) selectedJuz.remove(j) else selectedJuz.add(j) }, j.toString(), Modifier.widthIn(min = 54.dp))
                            }
                        }
                        Text(if (selectedJuz.isEmpty()) "لم يتم اختيار أجزاء" else "الأجزاء المحددة: ${selectedJuz.joinToString()}")
                    }
                    else -> {
                        Text("فلترة السور حسب الجزء", style = MaterialTheme.typography.labelLarge)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (1..30).forEach { j ->
                                ProFilterChip(selectedJuzFilter == j, {
                                    selectedJuzFilter = j
                                    val juzSurahs = surahsForJuz(j)
                                    val first = juzSurahs.firstOrNull()
                                    if (first != null) {
                                        startSurah = first
                                        startAyah = 1
                                        if (endSurah !in juzSurahs) {
                                            endSurah = first
                                            endAyah = QuranMetadata.maxAyah(first)
                                        }
                                    }
                                }, j.toString())
                            }
                        }
                        val juzSurahs = remember(selectedJuzFilter) { surahsForJuz(selectedJuzFilter) }
                        val surahOptions = remember(selectedJuzFilter) { juzSurahs.map { it to "${it}. ${QuranMetadata.surahName(it)}" } }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PickerDropdown("سورة البداية", startSurah, surahOptions, { startSurah = it; startAyah = 1 }, Modifier.weight(1f))
                            PickerDropdown("سورة النهاية", endSurah, surahOptions, { endSurah = it; endAyah = QuranMetadata.maxAyah(it) }, Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PickerDropdown(
                                "من آية", startAyah,
                                (1..QuranMetadata.maxAyah(startSurah)).map { it to it.toString() },
                                { startAyah = it }, Modifier.weight(1f),
                            )
                            PickerDropdown(
                                "إلى آية", endAyah,
                                (1..QuranMetadata.maxAyah(endSurah)).map { it to it.toString() },
                                { endAyah = it }, Modifier.weight(1f),
                            )
                        }
                        if (startSurah == endSurah) {
                            val max = QuranMetadata.maxAyah(startSurah)
                            var slider by remember(startSurah, startAyah, endAyah) { mutableStateOf(startAyah.toFloat()..endAyah.coerceAtLeast(startAyah).toFloat()) }
                            RangeSlider(
                                value = slider,
                                onValueChange = {
                                    val a = it.start.toInt().coerceIn(1, max)
                                    val b = it.endInclusive.toInt().coerceIn(a, max)
                                    slider = a.toFloat()..b.toFloat(); startAyah = a; endAyah = b
                                },
                                valueRange = 1f..max.toFloat(),
                                steps = (max - 2).coerceAtLeast(0),
                            )
                        }
                    }
                }

                HorizontalDivider()
                range?.let { QuranResultCard(it) } ?: Text(
                    rangeError ?: "اختر نطاقًا صالحًا لحساب المقدار",
                    color = if (rangeError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { Button(onClick = { range?.let(onConfirm) }, enabled = range != null) { Text("اعتماد الاختيار") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
