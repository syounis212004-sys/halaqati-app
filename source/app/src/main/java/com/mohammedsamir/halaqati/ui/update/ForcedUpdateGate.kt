package com.mohammedsamir.halaqati.ui.update

import android.app.DownloadManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.mohammedsamir.halaqati.BuildConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * RC27.35: when a newer version is published the app does not open until it is installed.
 * The block is remembered on the phone, so it also holds when the phone is offline at launch.
 * If the update manifest cannot be read (no internet) and nothing is remembered, the app opens normally.
 * Set "mandatory": false in version.json to make a release optional again.
 */
@Composable
fun ForcedUpdateGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var required by remember { mutableStateOf(NativeAppUpdater.cachedBlocking(context)) }
    var lastCheck by remember { mutableLongStateOf(0L) }
    var resumeTick by remember { mutableIntStateOf(0) }

    LifecycleResumeEffect(Unit) {
        resumeTick++
        val now = System.currentTimeMillis()
        // Re-check on every return to the app while blocked, otherwise at most every 5 minutes.
        if (required != null || now - lastCheck > 5 * 60_000L) {
            lastCheck = now
            scope.launch {
                runCatching { NativeAppUpdater.check() }.onSuccess { info ->
                    NativeAppUpdater.rememberBlocking(context, info)
                    required = info.takeIf { it.blocking }
                }
            }
        }
        onPauseOrDispose { }
    }

    val info = required
    if (info == null) {
        content()
    } else {
        ForcedUpdateScreen(info, resumeTick)
    }
}

@Composable
private fun ForcedUpdateScreen(info: NativeAppUpdater.UpdateInfo, resumeTick: Int) {
    val context = LocalContext.current
    var downloadId by remember { mutableStateOf<Long?>(null) }
    var state by remember { mutableStateOf<NativeAppUpdater.DownloadState?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var verified by remember { mutableStateOf<Boolean?>(null) }
    BackHandler(enabled = true) { /* the app cannot be skipped */ }

    LaunchedEffect(downloadId) {
        val id = downloadId ?: return@LaunchedEffect
        while (true) {
            val s = runCatching { NativeAppUpdater.queryDownload(context, id) }.getOrNull()
            state = s
            if (s == null || s.complete || s.failed) {
                if (s?.complete == true) {
                    verified = NativeAppUpdater.verifyDownload(context, id, info.sha256)
                    if (verified == false) message = "الملف المنزَّل غير مطابق للإصدار المنشور (ربما ما زال التحديث يُنشر). أعد التنزيل بعد دقائق."
                }
                break
            }
            delay(700)
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(84.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.SystemUpdateAlt, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(42.dp))
                }
            }
            Text("تحديث مطلوب", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Text(
                "يتوفر إصدار جديد من حلقاتي. يجب تحديث التطبيق للمتابعة.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Text(
                "${BuildConfig.VERSION_NAME}  ←  ${info.versionName}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            if (info.notes.isNotEmpty()) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("ما الجديد", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        info.notes.take(5).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            val s = state
            when {
                s?.running == true -> {
                    Text("جارٍ التنزيل… ${s.progress}%", fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(drawStopIndicator = {}, progress = { s.progress / 100f }, modifier = Modifier.fillMaxWidth().height(8.dp))
                }
                s?.failed == true -> Text("فشل التنزيل (رمز ${s.reason}). تأكد من الإنترنت وأعد المحاولة.", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }

            // resumeTick makes this re-evaluate after the user returns from the "install unknown apps" settings page.
            val canInstall = remember(resumeTick) { NativeAppUpdater.canRequestPackageInstalls(context) }
            when {
                s?.complete == true && verified == true && canInstall -> Button(
                    onClick = {
                        downloadId?.let { id ->
                            runCatching { NativeAppUpdater.installDownloaded(context, id) }.onFailure { message = "تعذر فتح التثبيت: ${it.message ?: "خطأ"}" }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Icon(Icons.Default.InstallMobile, null); Spacer(Modifier.width(8.dp)); Text("تثبيت الآن") }
                s?.complete == true && verified == true -> Button(
                    onClick = { NativeAppUpdater.openInstallPermission(context) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Icon(Icons.Default.Security, null); Spacer(Modifier.width(8.dp)); Text("السماح بتثبيت التحديث") }
                s?.complete == true && verified == null -> Text("جارٍ التحقق من الملف…", fontWeight = FontWeight.Bold)
                s?.running != true -> Button(
                    onClick = {
                        message = null
                        runCatching { NativeAppUpdater.downloadAndInstall(context, info) }
                            .onSuccess { id -> downloadId = id; state = null; verified = null }
                            .onFailure { message = "تعذر بدء التنزيل: ${it.message ?: "تحقق من الإنترنت"}" }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text(if (s?.failed == true) "إعادة التنزيل" else "تنزيل التحديث") }
            }
            if (s?.failed == true || message != null) {
                OutlinedButton(
                    onClick = { runCatching { NativeAppUpdater.openInBrowser(context, info) } },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("فتح رابط التنزيل في المتصفح") }
            }
            Text("بياناتك محفوظة ولن تتأثر بالتحديث.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}
