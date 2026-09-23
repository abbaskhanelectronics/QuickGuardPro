package com.abbaskhanelectronics.quickguardpro.device

import android.app.Application
import com.abbaskhanelectronics.quickguardpro.device.sync.SyncScheduler

class QgApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Notifier.createChannels(this)
        if (Prefs.enrolled && !Prefs.released) SyncScheduler.schedule(this)
    }
}
