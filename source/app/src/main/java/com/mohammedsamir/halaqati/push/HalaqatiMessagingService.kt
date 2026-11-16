package com.mohammedsamir.halaqati.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.mohammedsamir.halaqati.data.AppGraph

class HalaqatiMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        runCatching { AppGraph.repo.registerPushToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        // Data messages received while signed out must never expose another account's data.
        val currentUser = AppGraph.repo.currentUserId() ?: return
        // halaqati-notify v6+ includes recipient_id in Native data-only pushes. Drop any
        // mismatched message so a stale token from a previous login can never surface content.
        val intendedUser = message.data["recipient_id"]?.trim().orEmpty()
        if (intendedUser.isNotBlank() && intendedUser != currentUser) return
        NotificationHelper.show(
            context = this,
            title = message.notification?.title ?: message.data["title"] ?: "حلقتي",
            body = message.notification?.body ?: message.data["body"] ?: "لديك إشعار جديد",
            deepLink = message.data["deep_link"],
            stableKey = message.messageId ?: message.data["source_key"],
        )
    }
}
