package com.abbaskhanelectronics.quickguardpro.admin.ui

import android.graphics.Bitmap
import android.graphics.Color as AColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abbaskhanelectronics.quickguardpro.admin.data.Payment
import com.abbaskhanelectronics.quickguardpro.admin.data.Repo
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay
import java.util.UUID

@Composable
fun AgreementDetailScreen(id: String) {
    val nav = LocalNav.current
    val user = LocalUser.current
    val runner = rememberRunner()
    val agreement by remember(id) { Repo.agreement(id) }.collectAsState(initial = null)
    val payments by remember(id) { Repo.paymentsOf(id) }.collectAsState(initial = emptyList())
    var showPay by remember { mutableStateOf(false) }
    var reverse by remember { mutableStateOf<Payment?>(null) }
    val a = agreement

    DetailScaffold(a?.agreementNo ?: "Agreement") {
        if (a == null) { EmptyState("Loading…"); return@DetailScaffold }
        QgCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(a.customerName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(a.productName, color = QgColors.Muted)
                }
                StatusChip(statusLabel(a.status), statusColor(a.status))
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { if (a.installmentCount == 0) 0f else a.paidInstallments / a.installmentCount.toFloat() },
                modifier = Modifier.fillMaxWidth(), color = QgColors.Red, trackColor = QgColors.Outline,
            )
            Spacer(Modifier.height(10.dp))
            InfoRow("Total price", formatPkr(a.totalPrice))
            if (a.cashPrice > 0) InfoRow("Cash price", formatPkr(a.cashPrice))
            InfoRow("Down payment", formatPkr(a.downPayment))
            InfoRow("Installments", "${a.paidInstallments} / ${a.installmentCount} paid")
            InfoRow("Per installment", "${formatPkr(a.installmentAmount)} (${a.frequency.lowercase()})")
            InfoRow("Collected", formatPkr(a.totalCollected), QgColors.Green)
            InfoRow("Remaining", formatPkr(a.remainingBalance), if (a.remainingBalance > 0) QgColors.Amber else QgColors.Green)
            InfoRow("Next due", if (a.remainingBalance == 0L) "—" else a.nextDueDate.fmtDate())
            InfoRow("Grace period", "${a.graceDays} days")
        }

        if (a.status !in listOf("RELEASED", "CANCELLED", "FULLY_PAID")) {
            BigButton("RECORD PAYMENT") { showPay = true }
        }
        if (a.deviceId.isBlank() && a.status !in listOf("RELEASED", "CANCELLED")) {
            BigButton("ENROLL DEVICE (QR)", color = QgColors.SurfaceHigh) { nav.push(Route.EnrollQr(a.id)) }
        } else if (a.deviceId.isNotBlank()) {
            BigButton("OPEN DEVICE", color = QgColors.SurfaceHigh) { nav.push(Route.DeviceDetail(a.deviceId)) }
        }

        SectionTitle("Payment history")
        if (payments.isEmpty()) EmptyState("No payments recorded.")
        payments.sortedByDescending { it.createdAt }.forEach { p ->
            QgCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            (if (p.amount < 0) "− " else "") + formatPkr(kotlin.math.abs(p.amount)),
                            fontWeight = FontWeight.Bold,
                            color = if (p.amount < 0) QgColors.Red else QgColors.Green,
                        )
                        Text("${p.receiptNo} • ${p.kind.replace('_', ' ').lowercase()}", color = QgColors.Muted, fontSize = 12.sp)
                    }
                    if (p.reversed) StatusChip("REVERSED", QgColors.Red)
                }
                InfoRow("Date", p.createdAt.fmtDateTime())
                InfoRow("Method", p.method.ifBlank { "—" })
                if (p.reference.isNotBlank()) InfoRow("Reference", p.reference)
                InfoRow("Staff", p.staffName)
                InfoRow("Balance", "${formatPkr(p.previousBalance)} → ${formatPkr(p.newBalance)}")
                if (p.notes.isNotBlank()) Text(p.notes, color = QgColors.Muted, fontSize = 13.sp)
                if (user.isManagerOrOwner && !p.reversed && p.kind == "INSTALLMENT" && a.status != "RELEASED") {
                    TextButton(onClick = { reverse = p }) { Text("Reverse (mistake)", color = QgColors.Red) }
                }
            }
        }
    }

    if (showPay && a != null) {
        RecordPaymentDialog(a.remainingBalance, a.installmentAmount, onDismiss = { showPay = false }) { amount, method, ref, notes, requestId ->
            runner.run("Payment recorded") {
                Repo.call(
                    "recordPayment",
                    mapOf(
                        "agreementId" to id, "amount" to amount, "method" to method,
                        "reference" to ref, "notes" to notes, "requestId" to requestId,
                    ),
                )
                showPay = false
            }
        }
    }
    reverse?.let { p ->
        ConfirmDialog(
            "Reverse payment?",
            "${formatPkr(p.amount)} (${p.receiptNo}) will be reversed and the balance restored. The original record stays in history.",
            "REVERSE", danger = true,
            onConfirm = {
                runner.run("Payment reversed") { Repo.call("reversePayment", mapOf("paymentId" to p.id)) }
                reverse = null
            },
            onDismiss = { reverse = null },
        )
    }
}

@Composable
private fun RecordPaymentDialog(
    remaining: Long,
    suggested: Long,
    onDismiss: () -> Unit,
    onSubmit: (Long, String, String, String, String) -> Unit,
) {
    val requestId = remember { UUID.randomUUID().toString() }
    var amount by remember { mutableStateOf(minOf(suggested, remaining).toString()) }
    var method by remember { mutableStateOf("CASH") }
    var ref by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val amt = amount.toLongOrNull() ?: 0
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = QgColors.SurfaceHigh,
        title = { Text("Record payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Remaining: ${formatPkr(remaining)}", color = QgColors.Muted)
                Field("Amount (Rs)", amount, { amount = it.filter(Char::isDigit) }, number = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("CASH", "JAZZCASH", "EASYPAISA", "BANK").forEach { m ->
                        FilterChip(selected = method == m, onClick = { method = m },
                            label = { Text(m.lowercase(), fontSize = 11.sp) })
                    }
                }
                Field("Reference no. (optional)", ref, { ref = it })
                Field("Notes (optional)", notes, { notes = it })
                if (amt > remaining) Text("Amount is more than remaining balance.", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(amt, method, ref, notes, requestId) },
                enabled = amt in 1..remaining,
                colors = ButtonDefaults.buttonColors(containerColor = QgColors.Red),
            ) { Text("SAVE") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun EnrollQrScreen(agreementId: String) {
    val runner = rememberRunner()
    var token by remember { mutableStateOf<String?>(null) }
    var payload by remember { mutableStateOf<String?>(null) }
    var expiresAt by remember { mutableLongStateOf(0L) }
    var warning by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val agreement by remember(agreementId) { Repo.agreement(agreementId) }.collectAsState(initial = null)

    LaunchedEffect(Unit) {
        while (true) { now = System.currentTimeMillis(); delay(1000) }
    }
    fun generate() = runner.run {
        val res = Repo.call("createEnrollmentToken", mapOf("agreementId" to agreementId))
        token = res["token"] as? String
        payload = res["qrPayload"] as? String
        expiresAt = (res["expiresAt"] as? Number)?.toLong() ?: 0L
        warning = res["warning"] as? String
    }
    LaunchedEffect(Unit) { generate() }

    val qr = remember(payload) { payload?.let { makeQr(it) } }
    val left = ((expiresAt - now) / 1000).coerceAtLeast(0)
    val enrolled = agreement?.deviceId?.isNotBlank() == true

    DetailScaffold("Enroll Device") {
        if (enrolled) {
            QgCard {
                Text("✓ Device enrolled successfully", color = QgColors.Green, fontWeight = FontWeight.Bold)
                Text("The phone is now linked to ${agreement?.customerName}.", color = QgColors.Muted)
            }
            return@DetailScaffold
        }
        QgCard {
            Text("Steps", fontWeight = FontWeight.Bold)
            Text(
                "1. Factory reset the new phone (or use a brand-new phone).\n" +
                    "2. On the first Welcome screen, tap the screen 6 times in the same spot.\n" +
                    "3. Connect Wi-Fi if asked, then scan this QR code.\n" +
                    "4. Hand the phone to the customer to read and accept the disclosure.",
                color = QgColors.Silver, fontSize = 14.sp,
            )
        }
        warning?.let { QgCard { Text(it, color = QgColors.Amber) } }
        if (qr != null && left > 0) {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(qr.asImageBitmap(), contentDescription = "Enrollment QR", modifier = Modifier.fillMaxWidth().aspectRatio(1f))
            }
        }
        token?.let {
            QgCard {
                InfoRow("Enrollment code", it)
                Text("Use this code for manual enrollment (testing via ADB).", color = QgColors.Muted, fontSize = 12.sp)
                InfoRow("Expires in", if (left > 0) "${left / 60}:${"%02d".format(left % 60)}" else "EXPIRED",
                    if (left > 0) QgColors.Green else QgColors.Red)
            }
        }
        BigButton(if (runner.busy) "Please wait…" else "GENERATE NEW CODE", enabled = !runner.busy) { generate() }
        token?.let { t ->
            TextButton(onClick = {
                runner.run("Code revoked") { Repo.call("revokeEnrollmentToken", mapOf("token" to t)); token = null; payload = null }
            }) { Text("Revoke this code", color = QgColors.Red) }
        }
    }
}

private fun makeQr(text: String): Bitmap {
    val size = 720
    val matrix = QRCodeWriter().encode(
        text, BarcodeFormat.QR_CODE, size, size,
        mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.CHARACTER_SET to "UTF-8"),
    )
    val pixels = IntArray(size * size) { i -> if (matrix.get(i % size, i / size)) AColor.BLACK else AColor.WHITE }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}

@Composable
fun PaymentsTab() {
    val agreements by remember { Repo.agreements() }.collectAsState(initial = emptyList())
    var filter by remember { mutableStateOf("ALL") }
    var q by remember { mutableStateOf("") }
    val filters = listOf("ALL", "DUE_TODAY", "DUE_SOON", "OVERDUE", "ACTIVE", "FULLY_PAID", "RELEASED")
    val list = agreements
        .filter { filter == "ALL" || it.status == filter || (filter == "OVERDUE" && it.status == "TEMPORARILY_RESTRICTED") }
        .filter { q.isBlank() || it.customerName.contains(q, true) || it.agreementNo.contains(q, true) || it.customerPhone.contains(q) }
        .sortedBy { it.nextDueDate }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("Installments", fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Field("Search customer / agreement", q, { q = it })
        Row(Modifier.padding(vertical = 6.dp).horizontalScrollSafe(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            filters.forEach { f -> FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(statusLabel(f).lowercase()) }) }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (list.isEmpty()) item { EmptyState("Nothing here.") }
            items(list, key = { it.id }) { AgreementRow(it) }
        }
    }
}
