package com.abbaskhanelectronics.quickguardpro.device.sync

import android.content.Context
import androidx.work.*
import com.abbaskhanelectronics.quickguardpro.device.Api
import com.abbaskhanelectronics.quickguardpro.device.DeviceInfo
import com.abbaskhanelectronics.quickguardpro.device.Notifier
import com.abbaskhanelectronics.quickguardpro.device.Prefs
import com.abbaskhanelectronics.quickguardpro.device.policy.Policy
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object SyncScheduler {
    private val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedule(ctx: Context) {
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            "qg-periodic", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setConstraints(net).build(),
        )
    }

    fun now(ctx: Context) {
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            "qg-now", ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(net)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build(),
        )
    }

    fun cancelAll(ctx: Context) = WorkManager.getInstance(ctx).cancelAllWork()
}

class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Prefs.init(applicationContext)
        return try {
            Sync.run(applicationContext)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }
}

object Sync {
    private val mutex = Mutex()

    /** Detects SIM changes locally (works offline) and warns the user immediately. */
    fun checkSim(ctx: Context): String {
        val (key, info) = DeviceInfo.simKey(ctx)
        val prev = Prefs.simKey
        if (prev == null) {
            Prefs.simKey = key
        } else if (prev != key) {
            Prefs.simKey = key
            Prefs.simChangePending = true
            Notifier.simWarning(ctx)
        }
        return info
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun run(ctx: Context) = mutex.withLock {
        val deviceId = Prefs.deviceId ?: return@withLock
        if (Prefs.released) return@withLock
        if (FirebaseAuth.getInstance().currentUser == null) return@withLock

        val simInfo = checkSim(ctx)
        val (battery, charging) = DeviceInfo.battery(ctx)
        val fcmToken = runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull()
        val status = mapOf(
            "battery" to battery,
            "charging" to charging,
            "simInfo" to simInfo,
            "simChanged" to Prefs.simChangePending,
            "isDeviceOwner" to Policy.isDeviceOwner(ctx),
            "lockState" to if (Prefs.locked) "LOCKED" else "UNLOCKED",
            "appVersion" to DeviceInfo.appVersion(ctx),
            "fcmToken" to fcmToken,
            "capabilities" to Policy.capabilities(ctx),
        )
        val res = Api.call("deviceSync", mapOf("deviceId" to deviceId, "status" to status))
        Prefs.simChangePending = false
        Prefs.lastSync = System.currentTimeMillis()
        (res["supportPhone"] as? String)?.takeIf { it.isNotBlank() }?.let { Prefs.supportPhone = it }
        (res["businessName"] as? String)?.takeIf { it.isNotBlank() }?.let { Prefs.businessName = it }
        (res["summary"] as? Map<String, Any?>)?.let { Prefs.summaryJson = JSONObject(it).toString() }

        val commands = (res["commands"] as? List<Map<String, Any?>>).orEmpty()
            .sortedBy { (it["createdAt"] as? Number)?.toLong() ?: 0L }
        for (cmd in commands) execute(ctx, deviceId, cmd)
    }

    private suspend fun execute(ctx: Context, deviceId: String, cmd: Map<String, Any?>) {
        val id = cmd["id"] as? String ?: return
        val action = cmd["action"] as? String ?: return
        if (Prefs.isProcessed(id)) {
            // Already executed; just make sure the backend knows (idempotent ack).
            runCatching { ack(deviceId, id, true, "Already executed") }
            return
        }
        when (action) {
            "LOCK" -> ackAndMark(deviceId, id, true, Policy.lock(ctx))
            "UNLOCK" -> ackAndMark(deviceId, id, true, Policy.unlock(ctx))
            "MESSAGE" -> {
                Notifier.show(ctx, Prefs.businessName, cmd["message"] as? String ?: "")
                ackAndMark(deviceId, id, true, "Shown")
            }
            "LOCATION" -> {
                val loc = DeviceInfo.location(ctx)
                if (loc == null) ackAndMark(deviceId, id, false, "Location unavailable (off or not permitted)")
                else ackAndMark(deviceId, id, true, "Location captured", loc)
            }
            "RELEASE" -> {
                // Confirm to the backend first: after release this app loses Device Owner.
                ackAndMark(deviceId, id, true, "Released")
                Policy.release(ctx)
                SyncScheduler.cancelAll(ctx)
                Notifier.show(ctx, Prefs.businessName, "مبارک ہو! آپ کی تمام اقساط مکمل ہو گئی ہیں۔ یہ فون اب مکمل طور پر آپ کا ہے۔")
            }
            else -> ackAndMark(deviceId, id, false, "Unknown action")
        }
    }

    private suspend fun ackAndMark(deviceId: String, id: String, ok: Boolean, result: String, location: Map<String, Any?>? = null) {
        Prefs.markProcessed(id)
        ack(deviceId, id, ok, result, location)
    }

    private suspend fun ack(deviceId: String, id: String, ok: Boolean, result: String, location: Map<String, Any?>? = null) {
        Api.call(
            "ackCommand",
            mapOf("deviceId" to deviceId, "commandId" to id, "success" to ok, "result" to result, "location" to location),
        )
    }
}
