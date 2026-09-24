package com.abbaskhanelectronics.quickguardpro.device

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.abbaskhanelectronics.quickguardpro.device.policy.Policy
import com.abbaskhanelectronics.quickguardpro.device.sync.Sync
import com.abbaskhanelectronics.quickguardpro.device.sync.SyncScheduler
import com.abbaskhanelectronics.quickguardpro.device.ui.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val CONSENT_VERSION = "2026-09-v1"

class MainActivity : ComponentActivity() {
    private var refreshTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        Prefs.captureToken(intent)
        setContent { QgTheme { DeviceRoot(refreshTick) { refresh() } } }
    }

    override fun onResume() {
        super.onResume()
        if (Prefs.locked && !Prefs.released) Policy.enforce(this)
        refresh()
    }

    private fun refresh() {
        if (!Prefs.enrolled || Prefs.released) { refreshTick++; return }
        lifecycleScope.launch {
            runCatching { Sync.run(this@MainActivity) }
            refreshTick++
        }
    }
}

@Composable
private fun DeviceRoot(tick: Int, onRefresh: () -> Unit) {
    var enrolled by remember { mutableStateOf(Prefs.enrolled) }
    Surface(Modifier.fillMaxSize(), color = QgColors.Bg) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BrandHeader(Prefs.businessName)
            key(tick) {
                when {
                    Prefs.released -> ReleasedCard()
                    !enrolled -> EnrollCard { enrolled = true; onRefresh() }
                    else -> HomeCards(onRefresh)
                }
            }
        }
    }
}

@Composable
private fun EnrollCard(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf(Prefs.pendingToken ?: "") }
    var accepted by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onDone() }
    val owner = Policy.isDeviceOwner(ctx)

    QgCard {
        Text("Device setup / ڈیوائس سیٹ اپ", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        InfoRow("Device Owner", if (owner) "Active" else "Not active", if (owner) QgColors.Green else QgColors.Amber)
        if (!owner) Text(
            "Lock and uninstall protection need QR (Device Owner) enrollment on a factory-reset phone.",
            color = QgColors.Muted, fontSize = 13.sp,
        )
    }
    QgCard {
        Text("اہم معلومات — Management Disclosure", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "• یہ موبائل ${Prefs.businessName} سے قسطوں پر خریدا گیا ہے۔\n" +
                "• قسطیں مکمل ہونے تک ${Prefs.businessName} اس فون کے چند سیکیورٹی فنکشنز مینیج کرے گا۔\n" +
                "• آپ کو قسط کی یاد دہانی کے نوٹیفکیشن آئیں گے۔\n" +
                "• قسط ادا نہ ہونے پر معاہدے کے مطابق فون عارضی طور پر لاک کیا جا سکتا ہے۔ ایمرجنسی کال ہمیشہ دستیاب رہے گی۔\n" +
                "• بیٹری، کنکشن، سم کی حالت اور لاک اسٹیٹس کمپنی کو بھیجا جائے گا۔\n" +
                "• لوکیشن صرف کمپنی کی درخواست پر ایک بار لی جاتی ہے، مسلسل ٹریکنگ نہیں ہوتی۔\n" +
                "• آپ کے میسجز، کالز، تصاویر، کانٹیکٹس یا کیمرہ/مائیک تک رسائی نہیں لی جاتی۔\n" +
                "• تمام قسطیں مکمل ہونے پر یہ مینجمنٹ مکمل طور پر ختم کر دی جائے گی۔",
            style = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl),
            textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth(), color = QgColors.Silver,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "This phone is purchased on installments. Until the agreement is complete, the seller manages limited " +
                "security functions: payment reminders, possible restriction if payments are missed (emergency calls always " +
                "allowed), status sync (battery, connectivity, SIM state, lock state), and one-time location on request. " +
                "Messages, calls, photos, contacts, camera and microphone are never accessed. Management is removed after the final payment.",
            color = QgColors.Muted, fontSize = 13.sp,
        )
    }
    OutlinedTextField(
        value = token, onValueChange = { token = it.trim().uppercase() }, label = { Text("Enrollment code") },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = accepted, onCheckedChange = { accepted = it })
        Text("میں نے اوپر دی گئی شرائط پڑھ لی ہیں اور قبول کرتا/کرتی ہوں۔ I have read and accept.", fontSize = 14.sp)
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    BigButton(if (busy) "Please wait…" else "ACCEPT & ACTIVATE", enabled = accepted && token.length >= 6 && !busy) {
        busy = true; error = null
        scope.launch {
            try {
                val auth = FirebaseAuth.getInstance()
                if (auth.currentUser == null) auth.signInAnonymously().await()
                val res = Api.call(
                    "redeemEnrollment",
                    mapOf("token" to token, "info" to DeviceInfo.hardware(ctx), "consentVersion" to CONSENT_VERSION),
                )
                val id = res["deviceId"] as? String ?: throw IllegalStateException("Enrollment failed")
                Prefs.deviceId = id
                Prefs.pendingToken = null
                Policy.applyBaseline(ctx)
                Sync.checkSim(ctx)
                SyncScheduler.schedule(ctx)
                SyncScheduler.now(ctx)
                val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_PHONE_STATE)
                if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
                permLauncher.launch(perms.toTypedArray())
            } catch (e: Exception) {
                error = e.readable()
            } finally { busy = false }
        }
    }
}

@Composable
private fun HomeCards(onRefresh: () -> Unit) {
    val ctx = LocalContext.current
    val s = Prefs.summary()
    if (s == null) {
        QgCard { Text("Loading your installment details… Connect to the internet.", color = QgColors.Muted) }
    } else {
        val status = s.optString("status")
        QgCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.optString("customerName"), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(s.optString("productName"), color = QgColors.Muted)
                }
                StatusChip(statusLabel(status), statusColor(status))
            }
            Spacer(Modifier.height(8.dp))
            val total = s.optInt("installmentCount")
            val paid = s.optInt("paidInstallments")
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else paid / total.toFloat() },
                modifier = Modifier.fillMaxWidth(), color = QgColors.Red, trackColor = QgColors.Outline,
            )
            Spacer(Modifier.height(8.dp))
            InfoRow("Agreement / معاہدہ", s.optString("agreementNo"))
            InfoRow("Total installments / کل اقساط", total.toString())
            InfoRow("Paid / ادا شدہ", paid.toString(), QgColors.Green)
            InfoRow("Remaining / باقی اقساط", (total - paid).coerceAtLeast(0).toString())
            InfoRow("Amount paid / ادا شدہ رقم", formatPkr(s.optLong("totalCollected")), QgColors.Green)
            InfoRow("Balance / بقایا", formatPkr(s.optLong("remainingBalance")), QgColors.Amber)
            if (s.optLong("remainingBalance") > 0) {
                InfoRow("Next installment / اگلی قسط", formatPkr(s.optLong("installmentAmount")))
                InfoRow("Due date / آخری تاریخ", fmtDate(s.optLong("nextDueDate")))
            }
        }
    }
    QgCard {
        InfoRow("Last updated", if (Prefs.lastSync == 0L) "—" else fmtDateTime(Prefs.lastSync))
        InfoRow("Support", Prefs.supportPhone)
    }
    BigButton("CONTACT SUPPORT / رابطہ کریں") {
        ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Prefs.supportPhone}")))
    }
    BigButton("REFRESH", color = QgColors.SurfaceHigh) { onRefresh() }
}

@Composable
private fun ReleasedCard() {
    QgCard {
        Text("✓ Installments complete", color = QgColors.Green, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("مبارک ہو! آپ کی تمام اقساط مکمل ہو گئی ہیں۔ یہ فون اب مکمل طور پر آپ کا ہے۔ آپ یہ ایپ ان انسٹال کر سکتے ہیں۔",
            color = QgColors.Silver, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth())
        Text("Management has been removed from this phone. You may uninstall this app.", color = QgColors.Muted)
    }
}

private val dFmt = SimpleDateFormat("dd MMM yyyy", Locale.US)
private val dtFmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US)
fun fmtDate(ms: Long) = if (ms <= 0) "—" else dFmt.format(Date(ms))
fun fmtDateTime(ms: Long) = if (ms <= 0) "—" else dtFmt.format(Date(ms))
