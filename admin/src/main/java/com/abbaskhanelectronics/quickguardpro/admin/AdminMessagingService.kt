package com.abbaskhanelectronics.quickguardpro.admin

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.abbaskhanelectronics.quickguardpro.admin.data.Repo
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AdminMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        if (Repo.auth.currentUser == null) return
        CoroutineScope(Dispatchers.IO).launch {
            try { Repo.call("registerAdminToken", mapOf("token" to token)) } catch (_: Exception) { }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.data["title"] ?: message.notification?.title ?: return
        val body = message.data["body"] ?: message.notification?.body ?: ""
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("alerts", "Alerts", NotificationManager.IMPORTANCE_HIGH))
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(this, "alerts")
            .setSmallIcon(R.drawable.ic_qg_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try { nm.notify((System.currentTimeMillis() % 100000).toInt(), n) } catch (_: SecurityException) { }
    }
}
