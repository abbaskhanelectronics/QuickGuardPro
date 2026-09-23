package com.abbaskhanelectronics.quickguardpro.device.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.abbaskhanelectronics.quickguardpro.device.Notifier
import com.abbaskhanelectronics.quickguardpro.device.Prefs
import com.abbaskhanelectronics.quickguardpro.device.policy.Policy
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Prefs.init(ctx)
        if (!Prefs.enrolled || Prefs.released) return
        Policy.enforce(ctx)
        Sync.checkSim(ctx)
        SyncScheduler.schedule(ctx)
        SyncScheduler.now(ctx)
    }
}

/** FCM only wakes the app. Commands are always fetched and verified from the backend. */
class DeviceMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        Prefs.init(this)
        if (Prefs.enrolled) SyncScheduler.now(this)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Prefs.init(this)
        val type = message.data["type"]
        if (type == "REMINDER") {
            Notifier.show(this, message.data["title"] ?: Prefs.businessName, message.data["body"] ?: "")
        }
        if (Prefs.enrolled && !Prefs.released) SyncScheduler.now(this)
    }
}
