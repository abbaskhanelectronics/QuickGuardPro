package com.abbaskhanelectronics.quickguardpro.device.lock

import android.app.ActivityManager
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abbaskhanelectronics.quickguardpro.device.MainActivity
import com.abbaskhanelectronics.quickguardpro.device.Prefs
import com.abbaskhanelectronics.quickguardpro.device.fmtDate
import com.abbaskhanelectronics.quickguardpro.device.policy.Policy
import com.abbaskhanelectronics.quickguardpro.device.sync.SyncScheduler
import com.abbaskhanelectronics.quickguardpro.device.ui.*

/**
 * Payment restriction screen. Runs in Android lock task mode (Device Owner),
 * with the phone dialer allowed so support and emergency calls always work.
 */
class LockActivity : ComponentActivity() {
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "locked" && !Prefs.locked) runOnUiThread { exitLock() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        if (!Prefs.locked || Prefs.released) { finish(); return }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* blocked while restricted */ }
        })
        Prefs.sp.registerOnSharedPreferenceChangeListener(listener)
        setContent { QgTheme { LockScreen() } }
    }

    override fun onResume() {
        super.onResume()
        if (!Prefs.locked) { exitLock(); return }
        val am = getSystemService(ActivityManager::class.java)
        if (Policy.isDeviceOwner(this) && am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) {
            runCatching { startLockTask() }
        }
    }

    override fun onDestroy() {
        runCatching { Prefs.sp.unregisterOnSharedPreferenceChangeListener(listener) }
        super.onDestroy()
    }

    private fun exitLock() {
        runCatching { stopLockTask() }
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    @Composable
    private fun LockScreen() {
        val s = Prefs.summary()
        Surface(Modifier.fillMaxSize(), color = QgColors.Bg) {
            Column(
                Modifier.fillMaxSize().systemBarsPadding().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BrandHeader(Prefs.businessName)
                QgCard {
                    Text("Device temporarily restricted", color = QgColors.Red, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "قسط کی ادائیگی باقی ہونے کی وجہ سے یہ فون عارضی طور پر محدود کر دیا گیا ہے۔ براہ کرم ادائیگی کے لیے رابطہ کریں۔",
                        color = QgColors.Silver, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    if (s != null) {
                        InfoRow("Customer", s.optString("customerName"))
                        InfoRow("Agreement", s.optString("agreementNo"))
                        InfoRow("Due amount / واجب الادا", formatPkr(s.optLong("dueAmount")), QgColors.Amber)
                        InfoRow("Due date", fmtDate(s.optLong("nextDueDate")))
                        InfoRow("Balance", formatPkr(s.optLong("remainingBalance")))
                    }
                    InfoRow("Support", Prefs.supportPhone)
                }
                BigButton("CONTACT SUPPORT / رابطہ کریں") {
                    runCatching { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Prefs.supportPhone}"))) }
                }
                BigButton("EMERGENCY CALL / ایمرجنسی کال", color = QgColors.SurfaceHigh) {
                    runCatching { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:1122"))) }
                }
                BigButton("I HAVE PAID — CHECK AGAIN", color = QgColors.SurfaceHigh) { SyncScheduler.now(this@LockActivity) }
                Text("Quick Guard Pro • ${Prefs.businessName}", color = QgColors.Muted, fontSize = 12.sp)
            }
        }
    }
}
