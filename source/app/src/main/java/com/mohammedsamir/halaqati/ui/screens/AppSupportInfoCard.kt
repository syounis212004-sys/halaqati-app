package com.mohammedsamir.halaqati.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.data.AppGraph
import com.mohammedsamir.halaqati.model.Profile
import com.mohammedsamir.halaqati.ui.components.StateNotice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * RC53: "ادعم التطبيق". Everyone sees where support for the app's creator can be sent (phone number and the kind of
 * destination: bank or wallet). Only the administrator edits it; nothing is hard-coded in the app.
 */
@Composable
internal fun AppSupportInfoCard(profile: Profile) {
    val scope = rememberCoroutineScope()
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val isAdmin = profile.role == com.mohammedsamir.halaqati.model.UserRole.ADMIN
    var phone by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("bank") }
    var name by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val row = withContext(Dispatchers.IO) { AppGraph.repo.supportInfo() }
        if (row != null) {
            phone = row.optString("phone").takeIf { it != "null" }.orEmpty()
            type = row.optString("entity_type").takeIf { it.isNotBlank() && it != "null" } ?: "bank"
            name = row.optString("entity_name").takeIf { it != "null" }.orEmpty()
            note = row.optString("note").takeIf { it != "null" }.orEmpty()
        }
        loaded = true
    }
    val hasInfo = phone.isNotBlank()
    if (!loaded || (!hasInfo && !isAdmin)) return
    val typeLabel = when (type) { "wallet" -> "محفظة إلكترونية"; "other" -> "جهة أخرى"; else -> "بنك" }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ادعم التطبيق", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "إن نفعك التطبيق وأحببت دعم استمراره وتطويره، يمكنك التحويل على البيانات التالية، جزاك الله خيرًا.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (hasInfo && !editing) {
                Text("رقم الهاتف: $phone", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text("نوع الجهة: $typeLabel" + if (name.isNotBlank()) " · $name" else "", style = MaterialTheme.typography.bodyMedium)
                if (note.isNotBlank()) Text(note, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(
                    onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(phone)); message = "تم نسخ الرقم" },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("نسخ الرقم") }
            }
            if (isAdmin) {
                if (!editing) {
                    TextButton(onClick = { editing = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (hasInfo) "تعديل بيانات الدعم" else "إضافة بيانات الدعم") }
                } else {
                    OutlinedTextField(phone, { phone = it }, label = { Text("رقم الهاتف") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("bank" to "بنك", "wallet" to "محفظة", "other" to "أخرى").forEach { (k, l) ->
                            com.mohammedsamir.halaqati.ui.components.ProFilterChip(type == k, { type = k }, l, showCheckIcon = false)
                        }
                    }
                    OutlinedTextField(name, { name = it }, label = { Text("اسم الجهة (مثلًا: اسم البنك أو المحفظة)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(note, { note = it }, label = { Text("ملاحظة (اختياري)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) { AppGraph.repo.saveSupportInfo(phone.trim(), type, name.trim(), note.trim()) }
                                        editing = false; message = "تم حفظ بيانات الدعم"
                                    } catch (e: kotlinx.coroutines.CancellationException) {
                                        throw e
                                    } catch (e: Exception) { message = e.message ?: "تعذر الحفظ. تأكد من الاتصال وأن تحديث الخادم مطبّق." }
                                }
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) { Text("حفظ") }
                        TextButton(onClick = { editing = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("إلغاء") }
                    }
                }
            }
            message?.let { StateNotice(it, kind = if (it.startsWith("تعذر")) "error" else "success") }
        }
    }
}
