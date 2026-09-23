package com.abbaskhanelectronics.quickguardpro.device

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.abbaskhanelectronics.quickguardpro.device.policy.Policy
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

object DeviceInfo {
    private fun granted(ctx: Context, p: String) =
        ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    fun appVersion(ctx: Context): String =
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" }.getOrDefault("")

    /** Identifiers are read only where Android allows it (Device Owner + READ_PHONE_STATE). */
    @SuppressLint("MissingPermission", "HardwareIds")
    fun hardware(ctx: Context): Map<String, Any?> {
        val tm = ctx.getSystemService(TelephonyManager::class.java)
        val imei = if (granted(ctx, Manifest.permission.READ_PHONE_STATE)) runCatching { tm.getImei(0) }.getOrNull() else null
        val serial = if (granted(ctx, Manifest.permission.READ_PHONE_STATE)) runCatching { Build.getSerial() }.getOrNull() else null
        return mapOf(
            "brand" to Build.BRAND,
            "manufacturer" to Build.MANUFACTURER,
            "model" to Build.MODEL,
            "androidVersion" to Build.VERSION.RELEASE,
            "sdk" to Build.VERSION.SDK_INT,
            "appVersion" to appVersion(ctx),
            "isDeviceOwner" to Policy.isDeviceOwner(ctx),
            "imei" to (imei ?: ""),
            "serial" to (serial?.takeIf { it != Build.UNKNOWN } ?: ""),
        )
    }

    fun battery(ctx: Context): Pair<Int, Boolean> {
        val bm = ctx.getSystemService(BatteryManager::class.java)
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) to bm.isCharging
    }

    /**
     * A key describing the SIM(s) currently in the phone. Subscription IDs change whenever a
     * different SIM card is inserted, so a change in this key means the SIM was removed or swapped.
     */
    @SuppressLint("MissingPermission")
    fun simKey(ctx: Context): Pair<String, String> {
        val tm = ctx.getSystemService(TelephonyManager::class.java)
        val state = when (tm.simState) {
            TelephonyManager.SIM_STATE_READY -> "READY"
            TelephonyManager.SIM_STATE_ABSENT -> "ABSENT"
            else -> "OTHER"
        }
        val subs = if (granted(ctx, Manifest.permission.READ_PHONE_STATE)) runCatching {
            ctx.getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList
                ?.map { it.subscriptionId }?.sorted()?.joinToString(",") ?: ""
        }.getOrDefault("") else ""
        val operator = tm.simOperatorName ?: ""
        return "$state|$subs|$operator" to "$state • ${operator.ifBlank { "No operator" }}"
    }

    @SuppressLint("MissingPermission")
    suspend fun location(ctx: Context): Map<String, Any?>? {
        if (!granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION) && !granted(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)) return null
        val client = LocationServices.getFusedLocationProviderClient(ctx)
        val cts = CancellationTokenSource()
        val current = withTimeoutOrNull(25_000) {
            runCatching { client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await() }.getOrNull()
        }
        cts.cancel()
        val loc = current ?: runCatching { client.lastLocation.await() }.getOrNull() ?: return null
        return mapOf(
            "lat" to loc.latitude,
            "lng" to loc.longitude,
            "accuracy" to loc.accuracy.toDouble(),
            "time" to loc.time,
            "lastKnown" to (current == null),
        )
    }
}
