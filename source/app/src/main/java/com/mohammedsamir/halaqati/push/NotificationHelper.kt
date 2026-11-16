package com.mohammedsamir.halaqati.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.mohammedsamir.halaqati.MainActivity

object NotificationHelper {
    /** Must match halaqati-notify Edge Function android.notification.channel_id. */
    const val CHANNEL_ID = "halaqati_general"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "حلقتي",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "إشعارات الحضور والتسميع والإعلانات والخطط"
                enableVibration(true)
            },
        )
    }

    fun show(
        context: Context,
        title: String,
        body: String,
        deepLink: String?,
        stableKey: String? = null,
    ) {
        ensureChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val normalizedDeepLink = deepLink?.takeIf { it.isNotBlank() }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            normalizedDeepLink?.let { data = Uri.parse(it) }
        }
        val key = stableKey ?: normalizedDeepLink ?: "$title|$body"
        val requestCode = key.hashCode() and Int.MAX_VALUE
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notificationId = (stableKey ?: "$key|${System.currentTimeMillis()}").hashCode() and Int.MAX_VALUE

        manager.notify(
            notificationId,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(com.mohammedsamir.halaqati.R.drawable.ic_stat_halaqati)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build(),
        )
    }
}
