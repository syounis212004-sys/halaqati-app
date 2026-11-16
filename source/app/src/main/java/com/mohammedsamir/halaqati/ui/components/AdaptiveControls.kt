@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mohammedsamir.halaqati.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.floor

/**
 * BUILD FIX 5 — one lazy, searchable selector for the whole app.
 *
 * Material ExposedDropdownMenu eagerly composes its children. That becomes visible as a pause for
 * Quran ayah lists (up to 286 rows), students and other long datasets. This selector opens a sheet
 * immediately and renders the list with LazyColumn, so only visible rows are composed.
 */
@Composable
fun HalaqatiLazySelectField(
    label: String,
    selectedKey: String?,
    options: List<Pair<String, String>>,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emptyLabel: String = "اختر $label",
    allowEmpty: Boolean = false,
    emptyKey: String = "",
    emptyOptionLabel: String = "بدون $label",
    searchable: Boolean = options.size > 12,
    leadingIcon: ImageVector? = null,
) {
    var open by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selectedKey }?.second
        ?: if (allowEmpty && selectedKey.orEmpty().isBlank()) emptyOptionLabel else emptyLabel

    OutlinedButton(
        onClick = { open = true },
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp),
        // RC95: same corner and outline as the text fields next to it (was a full pill beside square fields).
        shape = androidx.compose.material3.OutlinedTextFieldDefaults.shape,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(selectedLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
    }

    if (open) {
        HalaqatiLazySelectSheet(
            title = "اختيار $label",
            selectedKey = selectedKey,
            options = options,
            searchable = searchable,
            allowEmpty = allowEmpty,
            emptyKey = emptyKey,
            emptyOptionLabel = emptyOptionLabel,
            onDismiss = { open = false },
            onSelected = {
                onSelected(it)
                open = false
            },
        )
    }
}

@Composable
fun HalaqatiLazySelectSheet(
    title: String,
    selectedKey: String?,
    options: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit,
    searchable: Boolean = options.size > 12,
    allowEmpty: Boolean = false,
    emptyKey: String = "",
    emptyOptionLabel: String = "بدون اختيار",
) {
    var query by remember { mutableStateOf("") }
    val allOptions = remember(options, allowEmpty, emptyKey, emptyOptionLabel) {
        if (allowEmpty) listOf(emptyKey to emptyOptionLabel) + options.filterNot { it.first == emptyKey } else options
    }
    val filtered = remember(allOptions, query) {
        if (query.isBlank()) allOptions else allOptions.filter { (_, text) -> text.contains(query.trim(), ignoreCase = true) }
    }
    val listState = rememberLazyListState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(selectedKey, query, filtered.size) {
        if (query.isBlank()) {
            val index = filtered.indexOfFirst { it.first == selectedKey }
            if (index >= 0) listState.scrollToItem(index)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "إغلاق") }
            }
            if (searchable) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("بحث سريع") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                )
            }
            if (filtered.isEmpty()) {
                Text("لا توجد نتيجة مطابقة", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp))
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(filtered, key = { it.first }) { (key, text) ->
                        val selected = key == selectedKey
                        ListItem(
                            headlineContent = {
                                Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                            },
                            trailingContent = {
                                if (selected) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .42f) else MaterialTheme.colorScheme.surface,
                            ),
                            modifier = Modifier.fillMaxWidth().clickable { onSelected(key) },
                        )
                    }
                }
            }
        }
    }
}

/** A responsive chip grid that never squeezes Arabic labels into broken multi-line fragments. */
@Composable
fun <T> ResponsiveChipGrid(
    items: List<T>,
    modifier: Modifier = Modifier,
    minItemWidth: Dp = 104.dp,
    maxColumns: Int = 4,
    spacing: Dp = 6.dp,
    itemContent: @Composable (T, Modifier) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val available = maxWidth.value.coerceAtLeast(1f)
        val unit = (minItemWidth + spacing).value.coerceAtLeast(1f)
        val columns = floor((available + spacing.value) / unit).toInt().coerceIn(2, maxColumns.coerceAtLeast(2))
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            items.chunked(columns).forEach { rowItems ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    rowItems.forEach { item -> itemContent(item, Modifier.weight(1f)) }
                    repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
fun HalaqatiDateField(
    label: String,
    value: java.time.LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 54.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value.toString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        Icon(androidx.compose.material.icons.Icons.Default.CalendarMonth, contentDescription = null)
    }
}

@Composable
fun HalaqatiDatePickerDialog(
    initial: java.time.LocalDate,
    title: String = "اختر التاريخ",
    onDismiss: () -> Unit,
    onConfirm: (java.time.LocalDate) -> Unit,
) {
    val initialMillis = initial.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis ?: initialMillis
                onConfirm(java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC).toLocalDate())
            }) { Text("اعتماد") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    ) {
        DatePicker(state = state, title = { Text(title, Modifier.padding(start = 24.dp, top = 16.dp)) })
    }
}

@Composable
fun HalaqatiDateRangePickerDialog(
    initialStart: java.time.LocalDate,
    initialEnd: java.time.LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (java.time.LocalDate, java.time.LocalDate) -> Unit,
) {
    val startMillis = initialStart.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
    val endMillis = initialEnd.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDateRangePickerState(initialSelectedStartDateMillis = startMillis, initialSelectedEndDateMillis = endMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null,
                onClick = {
                    val s = java.time.Instant.ofEpochMilli(state.selectedStartDateMillis ?: startMillis).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                    val e = java.time.Instant.ofEpochMilli(state.selectedEndDateMillis ?: endMillis).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                    onConfirm(minOf(s, e), maxOf(s, e))
                },
            ) { Text("اعتماد الفترة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    ) {
        DateRangePicker(
            state = state,
            title = { Text("اختر بداية ونهاية الفترة", Modifier.padding(start = 24.dp, top = 16.dp)) },
            modifier = Modifier.heightIn(max = 560.dp),
        )
    }
}

@Composable
fun HalaqatiMonthPickerDialog(
    initial: java.time.YearMonth,
    onDismiss: () -> Unit,
    onConfirm: (java.time.YearMonth) -> Unit,
) {
    var year by remember(initial) { mutableIntStateOf(initial.year) }
    var month by remember(initial) { mutableIntStateOf(initial.monthValue) }
    val monthNames = listOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اختر الشهر") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { year-- }) { Text("‹", style = MaterialTheme.typography.headlineSmall) }
                    Text(year.toString(), style = MaterialTheme.typography.titleLarge.copy(fontFamily = com.mohammedsamir.halaqati.ui.theme.CairoFamily), fontWeight = FontWeight.Bold)
                    IconButton(onClick = { year++ }) { Text("›", style = MaterialTheme.typography.headlineSmall) }
                }
                ResponsiveChipGrid(items = monthNames.mapIndexed { index, name -> (index + 1) to name }, minItemWidth = 92.dp, maxColumns = 3) { pair, itemModifier ->
                    ProFilterChip(month == pair.first, { month = pair.first }, pair.second, itemModifier.fillMaxWidth(), showCheckIcon = false)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(java.time.YearMonth.of(year, month)) }) { Text("اعتماد") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
