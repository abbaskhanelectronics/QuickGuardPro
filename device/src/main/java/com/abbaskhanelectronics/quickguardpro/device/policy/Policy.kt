package com.abbaskhanelectronics.quickguardpro.device.policy

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import com.abbaskhanelectronics.quickguardpro.device.Prefs
import com.abbaskhanelectronics.quickguardpro.device.admin.QgDeviceAdminReceiver
import com.abbaskhanelectronics.quickguardpro.device.lock.LockActivity

/**
 * All device controls use documented DevicePolicyManager APIs available to a Device Owner.
 * Nothing here is hidden from the user or bypasses Android security.
 */
object Policy {
    private val RESTRICTIONS = listOf(
        UserManager.DISALLOW_FACTORY_RESET,
        UserManager.DISALLOW_SAFE_BOOT,
        UserManager.DISALLOW_ADD_USER,
    )

    fun admin(ctx: Context) = ComponentName(ctx, QgDeviceAdminReceiver::class.java)
    private fun dpm(ctx: Context) = ctx.getSystemService(DevicePolicyManager::class.java)
    fun isDeviceOwner(ctx: Context): Boolean = runCatching { dpm(ctx).isDeviceOwnerApp(ctx.packageName) }.getOrDefault(false)

    /** Applied once after the customer accepts the disclosure. */
    fun applyBaseline(ctx: Context) {
        if (!isDeviceOwner(ctx)) return
        val dpm = dpm(ctx)
        val admin = admin(ctx)
        runCatching { dpm.setUninstallBlocked(admin, ctx.packageName, true) }
        RESTRICTIONS.forEach { r -> runCatching { dpm.addUserRestriction(admin, r) } }
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE,
        )
        if (Build.VERSION.SDK_INT >= 29) perms += Manifest.permission.ACCESS_BACKGROUND_LOCATION
        if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
        perms.forEach { p ->
            runCatching {
                dpm.setPermissionGrantState(admin, ctx.packageName, p, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
            }
        }
    }

    fun lock(ctx: Context): String {
        Prefs.locked = true
        if (!isDeviceOwner(ctx)) {
            launchLockScreen(ctx)
            return "Restriction screen shown, but Device Owner is not active (limited lock)"
        }
        allowLockTask(ctx)
        launchLockScreen(ctx)
        return "Locked (lock task mode)"
    }

    /** Called on boot / app start to make sure a locked phone stays locked. */
    fun enforce(ctx: Context) {
        if (Prefs.released || !Prefs.locked) return
        if (isDeviceOwner(ctx)) allowLockTask(ctx)
        launchLockScreen(ctx)
    }

    fun unlock(ctx: Context): String {
        Prefs.locked = false
        if (isDeviceOwner(ctx)) runCatching { dpm(ctx).setLockTaskPackages(admin(ctx), emptyArray()) }
        return "Unlocked"
    }

    fun release(ctx: Context) {
        Prefs.locked = false
        Prefs.released = true
        if (!isDeviceOwner(ctx)) return
        val dpm = dpm(ctx)
        val admin = admin(ctx)
        runCatching { dpm.setLockTaskPackages(admin, emptyArray()) }
        RESTRICTIONS.forEach { r -> runCatching { dpm.clearUserRestriction(admin, r) } }
        runCatching { dpm.setUninstallBlocked(admin, ctx.packageName, false) }
        @Suppress("DEPRECATION")
        runCatching { dpm.clearDeviceOwnerApp(ctx.packageName) }
    }

    private fun allowLockTask(ctx: Context) {
        val dpm = dpm(ctx)
        val admin = admin(ctx)
        val dialer = runCatching { ctx.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }.getOrNull()
        val packages = listOfNotNull(ctx.packageName, dialer).distinct().toTypedArray()
        runCatching { dpm.setLockTaskPackages(admin, packages) }
        if (Build.VERSION.SDK_INT >= 28) {
            runCatching {
                dpm.setLockTaskFeatures(
                    admin,
                    DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS or DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO,
                )
            }
        }
    }

    private fun launchLockScreen(ctx: Context) {
        val i = Intent(ctx, LockActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        runCatching { ctx.startActivity(i) }
    }

    fun capabilities(ctx: Context): Map<String, String> {
        val owner = isDeviceOwner(ctx)
        val um = ctx.getSystemService(UserManager::class.java)
        fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
        val ownerOnly = { ok: Boolean -> if (!owner) "REQUIRES_DEVICE_OWNER" else if (ok) "SUPPORTED" else "LIMITED_BY_OEM" }
        return mapOf(
            "Device owner" to if (owner) "SUPPORTED" else "REQUIRES_DEVICE_OWNER",
            "Remote lock" to ownerOnly(true),
            "Uninstall protection" to ownerOnly(runCatching { dpm(ctx).isUninstallBlocked(admin(ctx), ctx.packageName) }.getOrDefault(false)),
            "Factory reset block" to ownerOnly(um.hasUserRestriction(UserManager.DISALLOW_FACTORY_RESET)),
            "Location" to if (granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)) "SUPPORTED" else "PERMISSION_MISSING",
            "SIM monitoring" to if (granted(Manifest.permission.READ_PHONE_STATE)) "SUPPORTED" else "LIMITED",
        )
    }
}
