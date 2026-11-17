package com.mohammedsamir.halaqati.ui.screens

import com.mohammedsamir.halaqati.ui.components.HalaqatiAlertDialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.data.AppGraph
import com.mohammedsamir.halaqati.model.Student
import com.mohammedsamir.halaqati.ui.components.StateNotice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private fun saveIdentity(studentId: String, nationalId: String, birthDate: String) {
    AppGraph.repo.patch(
        "students", "id=eq.$studentId",
        JSONObject().put("national_id", nationalId).put("date_of_birth", birthDate),
        dedupe = "student-identity:$studentId",
    )
}

/**
 * RC27.47: fill the national id + birth date that guardian linking needs.
 * Students are collapsed one-line rows (tap to open one form), searchable, and the whole page scrolls.
 * A pasted list (name, id, date) can fill many students at once.
 */
@Composable
fun StudentIdentitySection(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var missing by remember { mutableStateOf<List<Student>>(emptyList()) }
    var open by remember { mutableStateOf(false) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }
    var showImport by remember { mutableStateOf(false) }

    suspend fun reload() {
        missing = withContext(Dispatchers.IO) {
            runCatching { AppGraph.repo.students() }.getOrDefault(emptyList())
                .filter { it.status == "active" && (it.nationalId.isNullOrBlank() || it.dateOfBirth.isNullOrBlank()) }
                .sortedBy { it.fullName }
        }
    }
    LaunchedEffect(Unit) { reload() }
    if (missing.isEmpty()) return

    ElevatedCard(modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("بيانات هوية الطلاب (${missing.size} ناقصة)", fontWeight = FontWeight.Bold)
                    Text(
                        "ربط أولياء الأمور برقم الهوية وتاريخ الميلاد يحتاج هذه البيانات.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null)
            }
            note?.let { StateNotice(it, "success") }
            if (open) {
                OutlinedButton(onClick = { showImport = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("لصق قائمة من Excel")
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("بحث باسم الطالب") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val needle = IdentityImport.normName(query)
                val shown = missing.filter { needle.isBlank() || IdentityImport.normName(it.fullName).contains(needle) }
                shown.take(200).forEach { st ->
                    key(st.id) {
                        val isOpen = expandedId == st.id
                        var nid by remember(st.id) { mutableStateOf(st.nationalId.orEmpty()) }
                        var dob by remember(st.id) { mutableStateOf(st.dateOfBirth.orEmpty()) }
                        var err by remember(st.id) { mutableStateOf<String?>(null) }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            HorizontalDivider()
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { expandedId = if (isOpen) null else st.id },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(st.fullName, style = MaterialTheme.typography.titleSmall)
                                    val lacks = listOfNotNull(
                                        "الهوية".takeIf { st.nationalId.isNullOrBlank() },
                                        "تاريخ الميلاد".takeIf { st.dateOfBirth.isNullOrBlank() },
                                    ).joinToString(" و")
                                    Text("ناقص: $lacks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(if (isOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null)
                            }
                            if (isOpen) {
                                OutlinedTextField(
                                    nid, { nid = it.filter { c -> c.isDigit() }; err = null },
                                    label = { Text("رقم الهوية") }, singleLine = true,
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                BirthDateField(label = "تاريخ الميلاد", value = dob, onChange = { dob = it; err = null })
                                err?.let { StateNotice(it, "error") }
                                Button(
                                    onClick = {
                                        when {
                                            nid.count { it.isDigit() } < 5 -> err = "رقم الهوية غير صالح"
                                            dob.isBlank() -> err = "اختر تاريخ الميلاد"
                                            else -> scope.launch {
                                                withContext(Dispatchers.IO) { saveIdentity(st.id, nid, dob) }
                                                expandedId = null
                                                reload()
                                                note = "تم حفظ ${st.fullName} · المتبقي ${missing.size}"
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                ) { Text("حفظ") }
                            }
                        }
                    }
                }
                if (shown.isEmpty()) Text("لا نتائج مطابقة.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (showImport) {
        var text by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        val parsed = remember(text) { IdentityImport.parse(text) }
        val byName = remember(missing) { missing.groupBy { IdentityImport.normName(it.fullName) } }
        val matched = parsed.first.filter { byName[IdentityImport.normName(it.name)]?.size == 1 }
        val notMatched = parsed.first.filter { byName[IdentityImport.normName(it.name)]?.size != 1 }
        HalaqatiAlertDialog(
            onDismissRequest = { if (!busy) showImport = false },
            title = { Text("لصق قائمة الهويات") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "انسخ من Excel ثلاثة أعمدة: الاسم، رقم الهوية، تاريخ الميلاد، والصقها هنا. يُطابَق الاسم مع الطلاب الناقصين فقط.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(text, { text = it }, minLines = 5, maxLines = 8, modifier = Modifier.fillMaxWidth(), label = { Text("الصق هنا") })
                    if (text.isNotBlank()) {
                        Text("جاهز للحفظ: ${matched.size} · غير مطابق: ${notMatched.size} · أسطر غير مقروءة: ${parsed.second}", style = MaterialTheme.typography.bodyMedium)
                        if (notMatched.isNotEmpty()) {
                            Text("غير مطابق: " + notMatched.take(6).joinToString("، ") { it.name }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && matched.isNotEmpty(),
                    onClick = {
                        scope.launch {
                            busy = true
                            withContext(Dispatchers.IO) {
                                matched.forEach { row ->
                                    byName[IdentityImport.normName(row.name)]?.singleOrNull()?.let { saveIdentity(it.id, row.nationalId, row.birthDate) }
                                }
                            }
                            busy = false
                            showImport = false
                            reload()
                            note = "تم حفظ ${matched.size} طالبًا · المتبقي ${missing.size}"
                        }
                    },
                ) { Text(if (busy) "جارٍ الحفظ…" else "حفظ ${matched.size}") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { showImport = false }) { Text("إلغاء") } },
        )
    }
}
