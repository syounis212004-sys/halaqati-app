package com.mohammedsamir.halaqati.ui.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import com.mohammedsamir.halaqati.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object NativeAppUpdater {
    const val UPDATE_MANIFEST_URL = "https://syounis212004-sys.github.io/halaqati-web/version.json"

    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String,
        val notes: List<String>,
        val mandatory: Boolean = true,
        /** Optional: only devices older than this versionCode are blocked (a newer version stays an optional update for the rest). */
        val minVersionCode: Int = 0,
        val sha256: String? = null,
    ) {
        val available: Boolean get() = versionCode > BuildConfig.VERSION_CODE
        /** RC27.35: a newer published version blocks the app until installed (manifest "mandatory": false lifts it; "minVersionCode" narrows it). */
        val blocking: Boolean get() = available && mandatory && (minVersionCode <= 0 || BuildConfig.VERSION_CODE < minVersionCode)
    }

    data class DownloadState(
        val status: Int,
        val progress: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val localUri: String? = null,
        val reason: Int = 0,
    ) {
        val running: Boolean get() = status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_PAUSED
        val complete: Boolean get() = status == DownloadManager.STATUS_SUCCESSFUL
        val failed: Boolean get() = status == DownloadManager.STATUS_FAILED
    }

    suspend fun check(): UpdateInfo = withContext(Dispatchers.IO) {
        val conn = URL("$UPDATE_MANIFEST_URL?t=${System.currentTimeMillis()}").openConnection() as HttpURLConnection
        conn.connectTimeout = 12_000
        conn.readTimeout = 12_000
        conn.setRequestProperty("Cache-Control", "no-cache")
        try {
            if (conn.responseCode !in 200..299) error("تعذر الوصول إلى ملف التحديث (${conn.responseCode})")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val code = json.optInt("versionCode", 0)
            val url = json.optString("apkUrl")
            require(code > 0 && url.startsWith("https://")) { "ملف التحديث غير مكتمل" }
            require(UpdateTrustRules.trustedApkUrl(url, UPDATE_MANIFEST_URL)) { "رابط التحديث ليس من مصدر النشر المعتمد" }
            val rawNotes = json.opt("notes")
            val notes = when (rawNotes) {
                is org.json.JSONArray -> (0 until rawNotes.length()).mapNotNull { rawNotes.optString(it).takeIf(String::isNotBlank) }
                is String -> rawNotes.lines().map(String::trim).filter(String::isNotBlank)
                else -> emptyList()
            }
            UpdateInfo(
                code, json.optString("versionName", code.toString()), url, notes,
                json.optBoolean("mandatory", true), json.optInt("minVersionCode", 0),
                json.optString("sha256").takeIf { UpdateTrustRules.validSha256(it) },
            )
        } finally { conn.disconnect() }
    }

    fun downloadAndInstall(context: Context, info: UpdateInfo): Long {
        val request = DownloadManager.Request(Uri.parse(info.apkUrl))
            .setTitle("تحديث حلقتي ${info.versionName}")
            .setDescription("تنزيل إصدار حلقتي الجديد")
            .setMimeType("application/vnd.android.package-archive")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Halaqati-${info.versionName}.apk")
        return context.getSystemService(DownloadManager::class.java).enqueue(request)
    }

    fun queryDownload(context: Context, downloadId: Long): DownloadState {
        val manager = context.getSystemService(DownloadManager::class.java)
        manager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
            if (!cursor.moveToFirst()) return DownloadState(DownloadManager.STATUS_FAILED, 0, 0, 0, reason = -1)
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)).coerceAtLeast(0)
            val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)).coerceAtLeast(0)
            val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            val uri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
            val progress = if (total > 0) ((downloaded * 100L) / total).toInt().coerceIn(0, 100) else if (status == DownloadManager.STATUS_SUCCESSFUL) 100 else 0
            return DownloadState(status, progress, downloaded, total, uri, reason)
        }
    }

    fun canRequestPackageInstalls(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun installDownloaded(context: Context, downloadId: Long) {
        if (!canRequestPackageInstalls(context)) {
            openInstallPermission(context)
            return
        }
        val manager = context.getSystemService(DownloadManager::class.java)
        val uri = manager.getUriForDownloadedFile(downloadId) ?: error("ملف التحديث غير متاح للتثبيت")
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    fun openDownloads(context: Context) {
        context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private const val PREFS = "halaqati_forced_update"

    /** Last blocking update seen online, so the block also holds when the phone is offline at launch. */
    fun cachedBlocking(context: Context): UpdateInfo? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val code = p.getInt("code", 0)
        val url = p.getString("url", null)
        if (code <= BuildConfig.VERSION_CODE || url.isNullOrBlank()) return null
        val notes = p.getString("notes", "").orEmpty().lines().filter(String::isNotBlank)
        return UpdateInfo(code, p.getString("name", code.toString()).orEmpty(), url, notes, true, 0, p.getString("sha", null))
    }

    fun rememberBlocking(context: Context, info: UpdateInfo?) {
        val e = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (info != null && info.blocking) {
            e.putInt("code", info.versionCode).putString("name", info.versionName).putString("url", info.apkUrl).putString("notes", info.notes.joinToString("\n")).putString("sha", info.sha256)
        } else e.clear()
        e.apply()
    }

    /** True only when the downloaded file matches the published SHA-256. A manifest without a digest is not trusted. */
    suspend fun verifyDownload(context: Context, downloadId: Long, expected: String?): Boolean = withContext(Dispatchers.IO) {
        if (!UpdateTrustRules.validSha256(expected)) return@withContext false
        runCatching {
            val manager = context.getSystemService(DownloadManager::class.java)
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            manager.openDownloadedFile(downloadId).use { pfd ->
                java.io.FileInputStream(pfd.fileDescriptor).use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        digest.update(buffer, 0, n)
                    }
                }
            }
            digest.digest().joinToString("") { "%02x".format(java.util.Locale.US, it) }.equals(expected, ignoreCase = true)
        }.getOrDefault(false)
    }

    /** Last resort when the in-app download keeps failing: open the APK link in the browser. */
    fun openInBrowser(context: Context, info: UpdateInfo) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.apkUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
