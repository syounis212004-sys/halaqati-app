@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mohammedsamir.halaqati.ui.screens

import com.mohammedsamir.halaqati.ui.components.HalaqatiAlertDialog

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.documents.DocumentImageEncoder
import com.mohammedsamir.halaqati.ui.components.ProFilterChip
import org.json.JSONObject
import java.util.Locale

/** RC49: currencies, payment details and the transfer-proof dialog shared by the admin and donor support screens. */
internal val CURRENCIES = listOf("ILS" to "شيكل ₪", "USD" to "دولار أمريكي $", "JOD" to "دينار أردني")
internal val PAY_METHODS = listOf("bank" to "تحويل بنكي", "wallet" to "محفظة إلكترونية")

internal fun currencySymbol(code: String) = when (code) { "USD" -> "$"; "JOD" -> "د.أ"; else -> "₪" }

internal fun amountLabel(amount: Double, currency: String): String =
    (if (amount == Math.rint(amount)) amount.toLong().toString() else String.format(Locale.US, "%.2f", amount)) + " " + currencySymbol(currency)

internal fun JSONObject.amountOrNull(): String? {
    val a = optDouble("amount", Double.NaN)
    return if (a.isNaN()) null else amountLabel(a, optString("currency").ifBlank { "ILS" })
}

internal fun payMethodLabel(m: String) = PAY_METHODS.firstOrNull { it.first == m }?.second ?: ""

internal class PaymentForm(initial: JSONObject?) {
    var method by mutableStateOf(initial?.optString("method").orEmpty().takeIf { it != "null" }.orEmpty())
    var provider by mutableStateOf(initial?.optString("provider").orEmpty().takeIf { it != "null" }.orEmpty())
    var account by mutableStateOf(initial?.optString("account").orEmpty().takeIf { it != "null" }.orEmpty())
    var holder by mutableStateOf(initial?.optString("holder").orEmpty().takeIf { it != "null" }.orEmpty())
    var note by mutableStateOf(initial?.optString("note").orEmpty().takeIf { it != "null" }.orEmpty())

    fun putInto(json: JSONObject): JSONObject = json.put("pay_method", method).put("pay_provider", provider).put("pay_account", account)
        .put("pay_holder", holder).put("pay_note", note)
}

@Composable
internal fun PaymentFormFields(form: PaymentForm) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PAY_METHODS.forEach { (k, l) -> ProFilterChip(form.method == k, { form.method = k }, l, showCheckIcon = false) }
        }
        OutlinedTextField(form.provider, { form.provider = it }, label = { Text("اسم البنك أو المحفظة") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(form.account, { form.account = it }, label = { Text("رقم الحساب أو المحفظة") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(form.holder, { form.holder = it }, label = { Text("اسم صاحب الحساب") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(form.note, { form.note = it }, label = { Text("ملاحظة للمانح (اختياري)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
    }
}

@Composable
internal fun DefaultsDialog(initial: JSONObject?, onDismiss: () -> Unit, onSave: (JSONObject) -> Unit) {
    var currency by remember { mutableStateOf(initial?.optString("currency")?.takeIf { it.isNotBlank() && it != "null" } ?: "ILS") }
    val form = remember { PaymentForm(initial) }
    HalaqatiAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("حساب الدعم الافتراضي") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("يظهر للمانح في كل بطاقة لا تحدّد حسابًا خاصًا بها.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CURRENCIES.forEach { (k, l) -> ProFilterChip(currency == k, { currency = k }, l, showCheckIcon = false) }
                }
                PaymentFormFields(form)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(form.putInto(JSONObject().put("currency", currency))) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

/** What the donor needs in order to transfer: where to, how much, and a copy button for the account number. */
@Composable
internal fun PaymentInfoCard(pay: JSONObject?, amount: String?) {
    val clipboard = LocalClipboardManager.current
    val account = pay?.optString("account").orEmpty().takeIf { it != "null" }.orEmpty()
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("بيانات التحويل", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            if (account.isBlank()) {
                Text("لم يحدّد المدير بيانات الدفع بعد. تواصل معه لأخذ رقم الحساب.", style = MaterialTheme.typography.bodySmall)
            } else {
                amount?.let { Text("المبلغ: $it", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) }
                val method = payMethodLabel(pay?.optString("method").orEmpty())
                val provider = pay?.optString("provider").orEmpty().takeIf { it != "null" }.orEmpty()
                Text(listOf(method, provider).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(account, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { clipboard.setText(AnnotatedString(account)) }) { Text("نسخ") }
                }
                pay?.optString("holder")?.takeIf { it.isNotBlank() && it != "null" }?.let { Text("باسم: $it", style = MaterialTheme.typography.bodySmall) }
                pay?.optString("note")?.takeIf { it.isNotBlank() && it != "null" }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
internal fun TransferDialog(onDismiss: () -> Unit, onSubmit: (String, String?) -> Unit) {
    val context = LocalContext.current
    var ref by remember { mutableStateOf("") }
    var image by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) image = DocumentImageEncoder.uriJpeg(context, uri, maxDimension = 1024, quality = 70)
    }
    HalaqatiAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إثبات التحويل") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("أرسل رقم الحوالة أو صورة الإشعار (يكفي أحدهما).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(ref, { ref = it }, label = { Text("رقم الحوالة") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedButton(onClick = { picker.launch("image/*") }) { Text(if (image == null) "إرفاق صورة الإشعار" else "تغيير الصورة") }
                if (image != null) Text("أُرفقت صورة", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = { TextButton(enabled = ref.trim().length >= 2 || image != null, onClick = { onSubmit(ref.trim(), image) }) { Text("إرسال") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
