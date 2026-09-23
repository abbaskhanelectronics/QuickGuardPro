package com.abbaskhanelectronics.quickguardpro.device

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.PersistableBundle
import org.json.JSONObject

/** Small local cache of the last verified state. Authority always stays on the backend. */
object Prefs {
    lateinit var sp: SharedPreferences
        private set

    fun init(ctx: Context) {
        if (!::sp.isInitialized) sp = ctx.applicationContext.getSharedPreferences("qg_state", Context.MODE_PRIVATE)
    }

    var pendingToken: String?
        get() = sp.getString("pendingToken", null)
        set(v) = sp.edit().putString("pendingToken", v).apply()
    var deviceId: String?
        get() = sp.getString("deviceId", null)
        set(v) = sp.edit().putString("deviceId", v).apply()
    val enrolled: Boolean get() = deviceId != null
    var locked: Boolean
        get() = sp.getBoolean("locked", false)
        set(v) { sp.edit().putBoolean("locked", v).commit() }
    var released: Boolean
        get() = sp.getBoolean("released", false)
        set(v) { sp.edit().putBoolean("released", v).commit() }
    var summaryJson: String?
        get() = sp.getString("summary", null)
        set(v) = sp.edit().putString("summary", v).apply()
    var supportPhone: String
        get() = sp.getString("supportPhone", "03098026981") ?: "03098026981"
        set(v) = sp.edit().putString("supportPhone", v).apply()
    var businessName: String
        get() = sp.getString("businessName", "Abbas Khan Electronics") ?: "Abbas Khan Electronics"
        set(v) = sp.edit().putString("businessName", v).apply()
    var simKey: String?
        get() = sp.getString("simKey", null)
        set(v) = sp.edit().putString("simKey", v).apply()
    var simChangePending: Boolean
        get() = sp.getBoolean("simChangePending", false)
        set(v) = sp.edit().putBoolean("simChangePending", v).apply()
    var lastSync: Long
        get() = sp.getLong("lastSync", 0L)
        set(v) = sp.edit().putLong("lastSync", v).apply()

    fun isProcessed(commandId: String) = sp.getStringSet("processed", emptySet())!!.contains(commandId)
    fun markProcessed(commandId: String) {
        val set = sp.getStringSet("processed", emptySet())!!.toMutableSet()
        set.add(commandId)
        // keep the set small
        val trimmed = if (set.size > 200) set.toList().takeLast(150).toSet() else set
        sp.edit().putStringSet("processed", trimmed).commit()
    }

    fun summary(): JSONObject? = summaryJson?.let { runCatching { JSONObject(it) }.getOrNull() }

    /** Reads the single-use enrollment code passed through the QR code's admin extras bundle. */
    fun captureToken(intent: Intent?) {
        val bundle: PersistableBundle? = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, PersistableBundle::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE)
        }
        bundle?.getString("qg_token")?.takeIf { it.isNotBlank() }?.let { pendingToken = it }
    }
}
