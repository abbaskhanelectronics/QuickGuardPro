package com.abbaskhanelectronics.quickguardpro.device.provisioning

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Bundle
import com.abbaskhanelectronics.quickguardpro.device.Prefs

/** Android 10+ QR provisioning: tells setup that we are a fully managed (Device Owner) app. */
class GetProvisioningModeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        Prefs.captureToken(intent)
        val result = Intent().putExtra(
            DevicePolicyManager.EXTRA_PROVISIONING_MODE,
            DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE,
        )
        setResult(RESULT_OK, result)
        finish()
    }
}

/** Android 10+ QR provisioning: final compliance step during setup. */
class PolicyComplianceActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        Prefs.captureToken(intent)
        setResult(RESULT_OK)
        finish()
    }
}
