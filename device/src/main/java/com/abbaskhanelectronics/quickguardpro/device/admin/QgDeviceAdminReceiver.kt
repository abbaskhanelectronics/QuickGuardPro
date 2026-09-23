package com.abbaskhanelectronics.quickguardpro.device.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.abbaskhanelectronics.quickguardpro.device.MainActivity
import com.abbaskhanelectronics.quickguardpro.device.Prefs

class QgDeviceAdminReceiver : DeviceAdminReceiver() {
    /** Legacy (Android 9 and below) provisioning completion. */
    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        Prefs.init(context)
        Prefs.captureToken(intent)
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
