package com.mohammedsamir.halaqati.ui.screens

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.BuildConfig
import com.mohammedsamir.halaqati.data.AppGraph
import com.mohammedsamir.halaqati.model.Profile
import com.mohammedsamir.halaqati.ui.components.StateNotice
import com.mohammedsamir.halaqati.ui.components.StudentAvatar
import com.mohammedsamir.halaqati.ui.update.NativeAppUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AccountNameSettingsCard(profile: Profile) {
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var value by remember(profile.id) { mutableStateOf(profile.fullName) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    Card(Modifier.fillMaxWidth(), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Badge, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("اسم الحساب", fontWeight = FontWeight.Bold)
                    Text("الاسم الذي يظهر للمسؤولين وأولياء الأمور والمحفظين.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (editing) {
                OutlinedTextField(value, { value = it }, label = { Text("الاسم الكامل") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !saving && value.trim().length >= 3, onClick = {
                        saving = true; message = null
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { runCatching { AppGraph.repo.updateMyFullName(value) } }
                            saving = false
                            r.onSuccess { message = "تم تحديث اسم الحساب"; editing = false }
                                .onFailure { message = it.message ?: "تعذر تحديث الاسم" }
                        }
                    }) { Text(if (saving) "جارٍ الحفظ…" else "حفظ") }
                    OutlinedButton(onClick = { value = profile.fullName; editing = false }, enabled = !saving) { Text("إلغاء") }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(value.ifBlank { profile.fullName }, style = MaterialTheme.typography.titleMedium); Text(profile.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    OutlinedButton(onClick = { editing = true }) { Text("تعديل الاسم الكامل") }
                }
            }
            message?.let { StateNotice(it, if (it.startsWith("تم")) "success" else "warning") }
        }
    }
}

/** RC90: every person sets their own photo (round, cropped square, compressed); nobody has to ask the system administrator. */
@Composable
internal fun MyPhotoSettingsCard(profile: Profile) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var photo by remember(profile.id, profile.avatarPath) { mutableStateOf(profile.avatarPath.takeIf { it.isNotBlank() && it != "null" }) }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            busy = true; message = null
            scope.launch {
                val data = withContext(Dispatchers.IO) {
                    com.mohammedsamir.halaqati.documents.DocumentImageEncoder.uriJpeg(context, uri, maxDimension = 384, quality = 85, squareCrop = true)
                }
                if (data == null) {
                    busy = false; message = "تعذر قراءة الصورة. اختر صورة أخرى."
                } else {
                    val r = withContext(Dispatchers.IO) { runCatching { AppGraph.repo.updateMyAvatar(data) } }
                    busy = false
                    r.onSuccess { photo = data; message = "تم حفظ صورتك" }.onFailure { message = it.message ?: "تعذر حفظ الصورة، تأكد من الاتصال بالإنترنت" }
                }
            }
        }
    }
    Card(Modifier.fillMaxWidth(), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                StudentAvatar(photo, profile.fullName, 76.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("صورتي", fontWeight = FontWeight.Bold)
                    Text("تظهر في بطاقة الترحيب والقائمة الجانبية وقوائم الكادر.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { picker.launch("image/*") }, enabled = !busy) { Text(if (busy) "جارٍ الحفظ…" else if (photo == null) "إضافة صورتي" else "تغيير صورتي") }
                        if (photo != null) TextButton(enabled = !busy, onClick = {
                            busy = true; message = null
                            scope.launch {
                                val r = withContext(Dispatchers.IO) { runCatching { AppGraph.repo.updateMyAvatar(null) } }
                                busy = false
                                r.onSuccess { photo = null; message = "أُزيلت صورتك" }.onFailure { message = it.message ?: "تعذر الإزالة" }
                            }
                        }) { Text("إزالة") }
                    }
                }
            }
            message?.let { StateNotice(it, if (it.startsWith("تم") || it.startsWith("أُزيلت")) "success" else "warning") }
        }
    }
}

@Composable
internal fun AppUpdateSettingsCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<NativeAppUpdater.UpdateInfo?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var downloadId by remember { mutableStateOf<Long?>(null) }
    var downloadState by remember { mutableStateOf<NativeAppUpdater.DownloadState?>(null) }

    LaunchedEffect(downloadId) {
        val id = downloadId ?: return@LaunchedEffect
        while (true) {
            val state = runCatching { NativeAppUpdater.queryDownload(context, id) }.getOrNull()
            downloadState = state
            if (state == null || state.complete || state.failed) break
            delay(700)
        }
    }

    ElevatedCard(Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(47.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        when {
                            checking -> CircularProgressIndicator(modifier = Modifier.size(23.dp), strokeWidth = 2.5.dp, color = MaterialTheme.colorScheme.onPrimary)
                            downloadState?.complete == true -> Icon(Icons.Default.DownloadDone, null, tint = MaterialTheme.colorScheme.onPrimary)
                            info?.available == false -> Icon(Icons.Default.Verified, null, tint = MaterialTheme.colorScheme.onPrimary)
                            info?.available == true -> Icon(Icons.Default.SystemUpdateAlt, null, tint = MaterialTheme.colorScheme.onPrimary)
                            else -> Icon(Icons.Default.SystemUpdate, null, tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            checking -> "جاري فحص التحديثات…"
                            downloadState?.complete == true -> "اكتمل تنزيل التحديث"
                            info?.available == true -> "يتوفر تحديث ${info?.versionName.orEmpty()}"
                            info?.available == false -> "أنت على أحدث إصدار"
                            else -> "تحديث التطبيق"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        if (info?.available == true) "${BuildConfig.VERSION_NAME}  ←  ${info?.versionName}" else "الإصدار الحالي ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            info?.takeIf { it.available }?.notes?.take(5)?.let { notes ->
                if (notes.isNotEmpty()) {
                    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .48f)) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("ما الجديد", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            notes.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }

            downloadState?.let { state ->
                when {
                    state.running -> {
                        Text("جارٍ التنزيل… ${state.progress}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        LinearProgressIndicator(drawStopIndicator = {}, progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth().height(8.dp))
                        if (state.totalBytes > 0) {
                            val downloadedMb = String.format(java.util.Locale.US, "%.1f", state.downloadedBytes / 1_048_576.0)
                            val totalMb = String.format(java.util.Locale.US, "%.1f", state.totalBytes / 1_048_576.0)
                            Text("$downloadedMb / $totalMb MB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    state.complete -> StateNotice("اكتمل التنزيل. اضغط تثبيت الآن لإكمال التحديث.", "success")
                    state.failed -> StateNotice("فشل تنزيل التحديث (رمز ${state.reason}). يمكنك إعادة المحاولة.", "warning")
                }
            }

            message?.let { StateNotice(it, if (it.startsWith("تعذر") || it.startsWith("فشل")) "warning" else "info") }

            when {
                downloadState?.complete == true -> {
                    if (NativeAppUpdater.canRequestPackageInstalls(context)) {
                        Button(
                            onClick = {
                                downloadId?.let { id ->
                                    runCatching { NativeAppUpdater.installDownloaded(context, id) }
                                        .onFailure { message = "تعذر فتح التثبيت: ${it.message ?: "خطأ"}" }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                        ) { Icon(Icons.Default.InstallMobile, null); Spacer(Modifier.width(7.dp)); Text("تثبيت الآن") }
                    } else {
                        Button(onClick = { NativeAppUpdater.openInstallPermission(context) }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                            Icon(Icons.Default.Security, null); Spacer(Modifier.width(7.dp)); Text("السماح بتثبيت التحديث")
                        }
                    }
                }
                info?.available == true && downloadState?.running != true -> {
                    Button(onClick = {
                        runCatching { NativeAppUpdater.downloadAndInstall(context, info!!) }
                            .onSuccess { id -> downloadId = id; downloadState = null; message = "بدأ تنزيل التحديث" }
                            .onFailure { message = "تعذر بدء التنزيل: ${it.message ?: "خطأ"}" }
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                        Icon(Icons.Default.Download, null); Spacer(Modifier.width(7.dp)); Text(if (downloadState?.failed == true) "إعادة تنزيل التحديث" else "تنزيل التحديث")
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = !checking,
                    onClick = {
                        checking = true; message = null
                        scope.launch {
                            val r = runCatching { NativeAppUpdater.check() }
                            checking = false
                            r.onSuccess { info = it; if (!it.available) downloadId = null }
                                .onFailure { message = "تعذر فحص التحديثات: ${it.message ?: "خطأ"}" }
                        }
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text(if (checking) "جارٍ الفحص…" else if (info == null) "فحص الآن" else "إعادة الفحص") }
                OutlinedButton(onClick = { NativeAppUpdater.openDownloads(context) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text("التنزيلات")
                }
            }
        }
    }
}

@Composable
internal fun V6ScoringSettingsCard() {
    Card(Modifier.fillMaxWidth(), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("معيار جودة التسميع V6", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("الخطأ = 1 · التلقين = 0.5 · تطبيع على 15 سطرًا حسب السطور المسمعة فعليًا.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                V6Band("ممتاز", "90–100%", Modifier.weight(1f))
                V6Band("جيد جدًا", "80–<90%", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                V6Band("جيد", "70–<80%", Modifier.weight(1f))
                V6Band("يحتاج إعادة", "<70%", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun V6Band(label: String, range: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            Text(range, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** RC27: admin-only full backup straight from the app (zip saved wherever the admin chooses: phone, Drive...). */
@Composable
internal fun BackupSettingsCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var kind by remember { mutableStateOf("info") }
    val create = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            running = true
            message = null
            try {
                val summary = withContext(Dispatchers.IO) {
                    val out = context.contentResolver.openOutputStream(uri) ?: error("تعذر فتح الملف للحفظ")
                    out.use { AppGraph.repo.exportBackup(it) { step -> progress = step } }
                }
                if (summary.failed.isEmpty()) {
                    message = "تم حفظ النسخة: ${summary.tables} جدولًا و${summary.rows} سجلًا."
                    kind = "success"
                } else {
                    message = "تم حفظ نسخة ناقصة؛ تعذّر: " + summary.failed.joinToString("، ") { it.substringBefore(":") } + ". أعد المحاولة."
                    kind = "error"
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                message = e.message ?: "تعذر إنشاء النسخة الاحتياطية"
                kind = "error"
            } finally { running = false; progress = "" }
        }
    }
    ElevatedCard(Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("نسخة احتياطية كاملة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "تحفظ كل بيانات الحلقات (الطلاب، الجلسات، التسميع، الخطط، الشهادات…) في ملف zip تختار مكانه. يحتوي بيانات طلاب وأولياء أمور فاحفظه في مكان آمن ولا ترسله لأحد. يُنصح بنسخة أسبوعية.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            message?.let { StateNotice(it, kind = kind) }
            Button(
                enabled = !running,
                onClick = { create.launch(com.mohammedsamir.halaqati.data.BackupRules.fileName(java.time.LocalDate.now().toString())) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (running) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text(if (progress.isBlank()) "جارٍ التصدير…" else "جارٍ التصدير: $progress")
                } else Text("إنشاء نسخة احتياطية الآن")
            }
        }
    }
}

/**
 * RC30: one-tap problem report. Nothing is sent by the app itself: it opens the system share sheet with a short,
 * non-personal diagnostic text (app version, Android version, device model, time) so the user can send it to the
 * administrator through any app (WhatsApp, e-mail, ...). No student names, ids or account data are included.
 */
@Composable
internal fun SupportSettingsCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("المساعدة والدعم", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "إذا ظهر خطأ أو رقم غير صحيح، اضغط الزر وأرسل التقرير لمسؤول النظام مع وصف بسيط لما حدث وفي أي شاشة.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { scope.launch {
                    // Room reads must stay off the main thread.
                    val diag = withContext(Dispatchers.IO) {
                        runCatching {
                            val r = AppGraph.repo
                            "عمليات لم تصل الخادم: ${r.pendingQueueSize()} · محجوبة: ${r.blockedQueueSize()} · تعارض: ${r.conflictQueueSize()} · أقدمها منذ ${r.oldestPendingMinutes()} دقيقة"
                        }.getOrDefault("تعذّر قراءة حالة المزامنة")
                    }
                    val report = buildString {
                        appendLine("تقرير مشكلة · حلقاتي")
                        appendLine("المزامنة: $diag")
                        appendLine("الإصدار: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                        appendLine("أندرويد: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                        appendLine("الجهاز: ${Build.MANUFACTURER} ${Build.MODEL}")
                        appendLine("الوقت: ${java.time.LocalDateTime.now()}")
                        appendLine()
                        appendLine("ماذا حدث؟ (اكتب هنا)")
                        appendLine("في أي شاشة؟")
                    }
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "تقرير مشكلة · حلقاتي ${BuildConfig.VERSION_NAME}")
                        putExtra(Intent.EXTRA_TEXT, report)
                    }
                    context.startActivity(Intent.createChooser(send, "إرسال تقرير المشكلة").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("إرسال تقرير مشكلة")
            }
        }
    }
}

