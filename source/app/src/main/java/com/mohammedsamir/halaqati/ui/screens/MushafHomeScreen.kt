package com.mohammedsamir.halaqati.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.quran.MushafOfflinePlan
import com.mohammedsamir.halaqati.quran.QuranMetadata
import com.mohammedsamir.halaqati.ui.components.ProFilterChip

/** Credit lines kept visible for the fonts and page data the mushaf uses. */
internal const val MUSHAF_ATTRIBUTION =
    "خط المصحف Madina: © 2020-2025 Amine Anane و Tarteel Inc.، رخصة SIL Open Font License 1.1.\n" +
        "Quran fonts provided by Quran Foundation (quran.foundation)؛ خطوط صفحات المصحف الكلاسيكية: مجمع الملك فهد لطباعة المصحف الشريف."

/** The mushaf entry in the main menu: continue where you stopped, jump to a juz or surah, download for offline use. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MushafHomeScreen() {
    val context = LocalContext.current
    var last by remember { mutableIntStateOf(MushafReaderPrefs.lastPage(context)) }
    var openAt by remember { mutableStateOf<Int?>(null) }
    var offline by remember { mutableStateOf(false) }
    var surahMenu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("المصحف", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("تابع القراءة من صفحة ${digits(last)}", color = MaterialTheme.colorScheme.onPrimaryContainer)
                Button(onClick = { openAt = last }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("متابعة القراءة", fontWeight = FontWeight.Bold) }
                OutlinedButton(onClick = { openAt = 1 }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("من أول المصحف") }
            }
        }
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("الانتقال إلى جزء", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (j in 1..30) ProFilterChip(selected = false, onClick = { openAt = MushafOfflinePlan.juzPages(j).first }, label = digits(j), showCheckIcon = false)
                }
                Text("الانتقال إلى سورة", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
                Box {
                    OutlinedButton(onClick = { surahMenu = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("اختر السورة") }
                    DropdownMenu(expanded = surahMenu, onDismissRequest = { surahMenu = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                        for (s in 1..114) DropdownMenuItem(text = { Text("${digits(s)}. ${QuranMetadata.surahName(s)}") }, onClick = { surahMenu = false; openAt = QuranMetadata.pageFor(s, 1) })
                    }
                }
            }
        }
        OutlinedButton(onClick = { offline = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("المصحف بدون إنترنت") }
        Text(MUSHAF_ATTRIBUTION, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    openAt?.let { page -> MushafReaderDialog(startPage = page, onDismiss = { openAt = null; last = MushafReaderPrefs.lastPage(context) }) }
    if (offline) MushafOfflineDialog(currentPage = last, onDismiss = { offline = false })
}
