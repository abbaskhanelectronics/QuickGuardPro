package com.abbaskhanelectronics.quickguardpro.admin.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abbaskhanelectronics.quickguardpro.admin.BuildConfig
import com.abbaskhanelectronics.quickguardpro.admin.data.Repo
import com.abbaskhanelectronics.quickguardpro.admin.data.StaffUser

@Composable
fun SettingsTab(onLogout: () -> Unit) {
    val user = LocalUser.current
    val nav = LocalNav.current
    val runner = rememberRunner()
    val business by remember { Repo.config("business") }.collectAsState(initial = null)
    val provisioning by remember { Repo.config("provisioning") }.collectAsState(initial = null)

    var name by remember(business) { mutableStateOf(business?.get("name") as? String ?: "Abbas Khan Electronics") }
    var phone by remember(business) { mutableStateOf(business?.get("supportPhone") as? String ?: "03098026981") }
    var address by remember(business) { mutableStateOf(business?.get("address") as? String ?: "") }
    var apkUrl by remember(provisioning) { mutableStateOf(provisioning?.get("apkUrl") as? String ?: "") }
    var checksum by remember(provisioning) { mutableStateOf(provisioning?.get("checksum") as? String ?: "") }
    var logout by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Settings", fontWeight = FontWeight.Bold, fontSize = 22.sp)
        QgCard {
            InfoRow("Logged in as", user.name.ifBlank { user.email })
            InfoRow("Email", user.email)
            InfoRow("Role", user.role, QgColors.Red)
        }

        SectionTitle("Business")
        if (user.isOwner) {
            Field("Business name", name, { name = it })
            Field("Support phone", phone, { phone = it }, phone = true)
            Field("Address", address, { address = it }, singleLine = false)
            BigButton("SAVE BUSINESS INFO", enabled = !runner.busy, color = QgColors.SurfaceHigh) {
                runner.run("Saved") {
                    Repo.call("updateSettings", mapOf("business" to mapOf("name" to name, "supportPhone" to phone, "address" to address)))
                }
            }
        } else {
            QgCard { InfoRow("Name", name); InfoRow("Support", phone) }
        }

        if (user.isOwner) {
            SectionTitle("QR enrollment setup")
            Text("Device APK download link and signature checksum (see SETUP guide).", color = QgColors.Muted, fontSize = 13.sp)
            Field("Device APK URL (https://…)", apkUrl, { apkUrl = it.trim() })
            Field("Signature checksum", checksum, { checksum = it.trim() })
            BigButton("SAVE QR SETUP", enabled = !runner.busy, color = QgColors.SurfaceHigh) {
                runner.run("Saved") {
                    Repo.call("updateSettings", mapOf("provisioning" to mapOf("apkUrl" to apkUrl, "checksum" to checksum)))
                }
            }
            SectionTitle("Team")
            BigButton("STAFF & ROLES", color = QgColors.SurfaceHigh) { nav.push(Route.Staff) }
        }

        SectionTitle("App")
        QgCard {
            InfoRow("Version", BuildConfig.VERSION_NAME)
            InfoRow("Support", "03098026981")
        }
        BigButton("LOGOUT") { logout = true }
    }
    if (logout) ConfirmDialog("Logout?", "You will need to sign in again.", "LOGOUT", true,
        onConfirm = { logout = false; onLogout() }, onDismiss = { logout = false })
}

@Composable
fun StaffScreen() {
    val me = LocalUser.current
    val runner = rememberRunner()
    val staff by remember { Repo.staff() }.collectAsState(initial = emptyList())
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<StaffUser?>(null) }

    DetailScaffold(
        "Staff & Roles",
        floating = {
            FloatingActionButton(onClick = { adding = true }, containerColor = QgColors.Red) {
                Icon(Icons.Filled.Add, contentDescription = "Add staff")
            }
        },
    ) {
        Text("OWNER: full access • MANAGER: lock/unlock/location/release/reversals • STAFF: customers, agreements, payments, enrollment",
            color = QgColors.Muted, fontSize = 13.sp)
        staff.sortedBy { it.role }.forEach { s ->
            QgCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.name.ifBlank { s.email }, fontWeight = FontWeight.Bold)
                        Text(s.email, color = QgColors.Muted, fontSize = 12.sp)
                    }
                    StatusChip(if (s.active) s.role else "DISABLED", if (s.active) QgColors.Silver else QgColors.Red)
                }
                if (s.uid != me.uid) TextButton(onClick = { editing = s }) { Text("Change") }
            }
        }
    }

    if (adding) {
        var name by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var role by remember { mutableStateOf("STAFF") }
        AlertDialog(
            onDismissRequest = { adding = false },
            containerColor = QgColors.SurfaceHigh,
            title = { Text("Add staff account") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("Name", name, { name = it })
                    Field("Email", email, { email = it.trim() })
                    OutlinedTextField(password, { password = it }, label = { Text("Password (min 8)") },
                        visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                    RolePicker(role) { role = it }
                }
            },
            confirmButton = {
                Button(enabled = name.isNotBlank() && email.contains("@") && password.length >= 8, onClick = {
                    runner.run("Staff account created") {
                        Repo.call("createStaff", mapOf("name" to name, "email" to email, "password" to password, "role" to role))
                    }
                    adding = false
                }) { Text("CREATE") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } },
        )
    }
    editing?.let { s ->
        var role by remember(s.uid) { mutableStateOf(s.role) }
        var active by remember(s.uid) { mutableStateOf(s.active) }
        AlertDialog(
            onDismissRequest = { editing = null },
            containerColor = QgColors.SurfaceHigh,
            title = { Text(s.name.ifBlank { s.email }) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RolePicker(role) { role = it }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Account active", modifier = Modifier.weight(1f))
                        Switch(checked = active, onCheckedChange = { active = it })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    runner.run("Updated") { Repo.call("updateStaff", mapOf("uid" to s.uid, "role" to role, "active" to active)) }
                    editing = null
                }) { Text("SAVE") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RolePicker(role: String, onPick: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("STAFF", "MANAGER", "OWNER").forEach { r ->
            FilterChip(selected = role == r, onClick = { onPick(r) }, label = { Text(r.lowercase()) })
        }
    }
}
