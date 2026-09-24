package com.abbaskhanelectronics.quickguardpro.device

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

object Notifier {
    private const val CH_REMINDERS = "reminders"
    private const val CH_ALERTS = "alerts"

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_REMINDERS, "Installment reminders", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_ALERTS, "Device alerts", NotificationManager.IMPORTANCE_HIGH))
    }

    fun show(ctx: Context, title: String, body: String, id: Int = (System.currentTimeMillis() % 100000).toInt(), alert: Boolean = false) {
        val pi = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, if (alert) CH_ALERTS else CH_REMINDERS)
            .setSmallIcon(R.drawable.ic_qg_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            ctx.getSystemService(NotificationManager::class.java).notify(id, n)
        } catch (_: SecurityException) { }
    }

    fun simWarning(ctx: Context) = show(
        ctx,
        "Quick Guard Pro — ${Prefs.businessName}",
        "یہ موبائل عباس خان الیکٹرونکس سے قسطوں پر لیا گیا ہے\nرابطہ: ${Prefs.supportPhone}",
        id = 7001,
        alert = true,
    )
}
