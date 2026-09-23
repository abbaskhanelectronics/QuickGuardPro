package com.abbaskhanelectronics.quickguardpro.admin.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abbaskhanelectronics.quickguardpro.admin.data.ManagedDevice
import com.abbaskhanelectronics.quickguardpro.admin.data.Repo
import java.util.Locale

@Composable
fun DevicesTab() {
    val nav = LocalNav.current
    val devices by remember { Repo.devices() }.collectAsState(initial = emptyList())
    val agreements by remember { Repo.agreements() }.collectAsState(initial = emptyList())
    var filter by remember { mutableStateOf("ALL") }
    var q by remember { mutableStateOf("") }
    val statusOf = agreements.associate { it.id to it.status }
    val filters = listOf("ALL", "LOCKED", "OFFLINE", "OVERDUE", "SIM ALERT", "RELEASED")
    val list = devices.filter { d ->
        when (filter) {
            "LOCKED" -> d.lockState == "LOCKED"
            "OFFLINE" -> !d.online && d.managementStatus == "ACTIVE"
            "OVERDUE" -> statusOf[d.agreementId] in listOf("OVERDUE", "TEMPORARILY_RESTRICTED")
            "SIM ALERT" -> d.simAlert
            "RELEASED" -> d.managementStatus == "RELEASED"
            else -> true
        }
    }.filter { q.isBlank() || it.customerName.contains(q, true) || it.model.contains(q, true) || it.id.contains(q, true) || it.imei.contains(q) }
        .sortedByDescending { it.lastSeen }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("Devices", fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Field("Search customer / model / IMEI", q, { q = it })
        Row(Modifier.padding(vertical = 6.dp).horizontalScrollSafe(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            filters.forEach { f -> FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.lowercase()) }) }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (list.isEmpty()) item { EmptyState(if (devices.isEmpty()) "No enrolled devices yet. Enroll one from an agreement." else "Nothing here.") }
            items(list, key = { it.id }) { d ->
                QgCard(Modifier.clickable { nav.push(Route.DeviceDetail(d.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(d.customerName, fontWeight = FontWeight.Bold)
                            Text("${d.brand} ${d.model}".trim(), color = QgColors.Muted)
                        }
                        StatusChip(if (d.managementStatus == "RELEASED") "RELEASED" else d.lockState.ifBlank { "UNLOCKED" },
                            statusColor(if (d.managementStatus == "RELEASED") "RELEASED" else d.lockState))
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusChip(if (d.online) "ONLINE" else "OFFLINE", statusColor(if (d.online) "ONLINE" else "OFFLINE"))
                        StatusChip("🔋 ${d.battery}%", QgColors.Silver)
                        if (d.simAlert) StatusChip("SIM ALERT", QgColors.Red)
                    }
                    Text("Last seen: ${d.lastSeen.ago()}", color = QgColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

@Composable
fun DeviceDetailScreen(id: String) {
    val user = LocalUser.current
    val context = LocalContext.current
    val runner = rememberRunner()
    val device by remember(id) { Repo.device(id) }.collectAsState(initial = null)
    val commands by remember(id) { Repo.commandsOf(id) }.collectAsState(initial = emptyList())
    val d = device
    val agreementFlow = remember(d?.agreementId) { d?.agreementId?.takeIf { it.isNotBlank() }?.let { Repo.agreement(it) } }
    val agreement = agreementFlow?.collectAsState(initial = null)?.value
    var confirm by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    var showMessage by remember { mutableStateOf(false) }

    DetailScaffold(d?.customerName ?: "Device") {
        if (d == null) { EmptyState("Loading…"); return@DetailScaffold }
        val released = d.managementStatus == "RELEASED"
        QgCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${d.brand} ${d.model}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Android ${d.androidVersion} • App ${d.appVersion}", color = QgColors.Muted, fontSize = 12.sp)
                }
                StatusChip(if (released) "RELEASED" else d.lockState.ifBlank { "UNLOCKED" },
                    statusColor(if (released) "RELEASED" else d.lockState))
            }
            Spacer(Modifier.height(8.dp))
            InfoRow("Status", if (d.online) "Online" else "Offline", if (d.online) QgColors.Green else QgColors.Muted)
            InfoRow("Last seen", d.lastSeen.fmtDateTime())
            InfoRow("Battery", "${d.battery}%" + if (d.charging) " (charging)" else "")
            InfoRow("Device Owner", if (d.isDeviceOwner) "Yes" else "No", if (d.isDeviceOwner) QgColors.Green else QgColors.Red)
            if (d.imei.isNotBlank()) InfoRow("IMEI", d.imei)
            InfoRow("SIM", d.simInfo.ifBlank { "—" }, if (d.simAlert) QgColors.Red else QgColors.Silver)
            InfoRow("Device ID", d.id.take(8).uppercase())
        }
        agreement?.let { a ->
            QgCard {
                InfoRow("Agreement", a.agreementNo)
                InfoRow("Remaining", formatPkr(a.remainingBalance), QgColors.Amber)
                InfoRow("Next due", if (a.remainingBalance == 0L) "—" else a.nextDueDate.fmtDate())
                InfoRow("Installments", "${a.paidInstallments} / ${a.installmentCount}")
                InfoRow("Status", statusLabel(a.status), statusColor(a.status))
            }
        }
        d.location?.let { loc ->
            QgCard {
                Text(if (loc.lastKnown) "Last known location" else "Current location (at capture time)", fontWeight = FontWeight.Bold)
                InfoRow("Coordinates", String.format(Locale.US, "%.5f, %.5f", loc.lat, loc.lng))
                InfoRow("Accuracy", "±${loc.accuracy.toInt()} m")
                InfoRow("Captured", loc.time.fmtDateTime())
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=${loc.lat},${loc.lng}")))
                }) { Text("Open in Maps") }
            }
        }

        if (!released) {
            SectionTitle("Actions")
            if (!d.isDeviceOwner) Text("Lock/Release require Device Owner enrollment.", color = QgColors.Amber, fontSize = 13.sp)
            if (user.isManagerOrOwner) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigButton("LOCK", Modifier.weight(1f), enabled = !runner.busy) { confirm = "LOCK" }
                    BigButton("UNLOCK", Modifier.weight(1f), enabled = !runner.busy, color = QgColors.Green) { confirm = "UNLOCK" }
                }
                BigButton("REQUEST LOCATION", enabled = !runner.busy, color = QgColors.SurfaceHigh) { confirm = "LOCATION" }
            }
            BigButton("SEND MESSAGE / REMINDER", enabled = !runner.busy, color = QgColors.SurfaceHigh) { showMessage = true }
            if (d.simAlert && user.isManagerOrOwner) {
                TextButton(onClick = { runner.run("SIM alert cleared") { Repo.call("clearSimAlert", mapOf("deviceId" to id)) } }) {
                    Text("Clear SIM alert")
                }
            }
            if (user.isManagerOrOwner && agreement?.status == "FULLY_PAID") {
                BigButton("RELEASE DEVICE", enabled = !runner.busy, color = QgColors.Blue) { confirm = "RELEASE" }
            }
        }

        SectionTitle("Capabilities")
        QgCard {
            if (d.capabilities.isEmpty()) Text("Not reported yet.", color = QgColors.Muted)
            d.capabilities.toSortedMap().forEach { (k, v) ->
                InfoRow(k, v.replace('_', ' ').lowercase(), when (v) {
                    "SUPPORTED" -> QgColors.Green
                    "UNSUPPORTED" -> QgColors.Red
                    else -> QgColors.Amber
                })
            }
        }

        SectionTitle("Command history")
        if (commands.isEmpty()) EmptyState("No commands yet.")
        commands.sortedByDescending { it.createdAt }.take(30).forEach { c ->
            QgCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(c.action, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    StatusChip(c.status, statusColor(c.status))
                }
                InfoRow("Requested", c.createdAt.fmtDateTime())
                InfoRow("By", c.createdByName)
                if (c.executedAt != null) InfoRow("Executed", c.executedAt.fmtDateTime())
                if (c.result.isNotBlank()) Text(c.result, color = QgColors.Muted, fontSize = 12.sp)
            }
        }
    }

    confirm?.let { action ->
        val (title, msg) = when (action) {
            "LOCK" -> "Lock this device?" to "The customer's phone will show the payment restriction screen until you unlock it. Emergency calls stay available."
            "UNLOCK" -> "Unlock this device?" to "Restriction will be removed as soon as the phone is online."
            "RELEASE" -> "Release this device?" to "This permanently removes Quick Guard Pro management. It cannot be undone."
            else -> "Request location?" to "The phone will send its location once, if location is on and permitted."
        }
        ConfirmDialog(title, msg, action, danger = action != "UNLOCK",
            onConfirm = {
                runner.run("Command sent. It runs when the phone is online.") {
                    Repo.call("sendCommand", mapOf("deviceId" to id, "action" to action))
                }
                confirm = null
            },
            onDismiss = { confirm = null })
    }
    if (showMessage) {
        AlertDialog(
            onDismissRequest = { showMessage = false },
            containerColor = QgColors.SurfaceHigh,
            title = { Text("Message to customer") },
            text = { Field("Message", message, { message = it.take(300) }, singleLine = false) },
            confirmButton = {
                Button(onClick = {
                    runner.run("Message sent") {
                        Repo.call("sendCommand", mapOf("deviceId" to id, "action" to "MESSAGE", "message" to message))
                        message = ""
                    }
                    showMessage = false
                }, enabled = message.isNotBlank()) { Text("SEND") }
            },
            dismissButton = { TextButton(onClick = { showMessage = false }) { Text("Cancel") } },
        )
    }
}
