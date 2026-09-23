package com.abbaskhanelectronics.quickguardpro.admin.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abbaskhanelectronics.quickguardpro.admin.data.Repo
import java.util.Calendar

@Composable
fun HomeShell(onLogout: () -> Unit) {
    val nav = LocalNav.current
    val tabs = listOf<Pair<String, ImageVector>>(
        "Dashboard" to Icons.Filled.Home,
        "Devices" to Icons.Filled.Phone,
        "Payments" to Icons.Filled.ShoppingCart,
        "Settings" to Icons.Filled.Settings,
    )
    Scaffold(
        containerColor = QgColors.Bg,
        snackbarHost = { SnackbarHost(LocalSnack.current) },
        bottomBar = {
            NavigationBar(containerColor = QgColors.Surface) {
                tabs.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = nav.tab == i,
                        onClick = { nav.tab = i },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            indicatorColor = QgColors.RedDark,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (nav.tab) {
                0 -> DashboardTab()
                1 -> DevicesTab()
                2 -> PaymentsTab()
                else -> SettingsTab(onLogout)
            }
        }
    }
}

@Composable
fun DashboardTab() {
    val nav = LocalNav.current
    val agreements by remember { Repo.agreements() }.collectAsState(initial = emptyList())
    val devices by remember { Repo.devices() }.collectAsState(initial = emptyList())
    val payments by remember { Repo.payments() }.collectAsState(initial = emptyList())
    val customers by remember { Repo.customers() }.collectAsState(initial = emptyList())

    val startOfDay = remember {
        Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
    }
    val startOfMonth = remember {
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }.timeInMillis
    }
    val live = agreements.filter { it.status !in listOf("RELEASED", "CANCELLED") }
    val todayCollected = payments.filter { (it.createdAt?.time ?: 0) >= startOfDay }.sumOf { it.amount }
    val monthCollected = payments.filter { (it.createdAt?.time ?: 0) >= startOfMonth }.sumOf { it.amount }
    val managed = devices.filter { it.managementStatus == "ACTIVE" }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BrandHeader()
        SectionTitle("Collections")
        StatRow("Today", formatPkr(todayCollected), QgColors.Green, "This month", formatPkr(monthCollected), QgColors.Green)
        StatRow(
            "Outstanding", formatPkr(live.sumOf { it.remainingBalance }), QgColors.Amber,
            "Customers", customers.size.toString(), QgColors.Silver,
        )
        SectionTitle("Installments")
        StatRow(
            "Due today", live.count { it.status == "DUE_TODAY" }.toString(), QgColors.Amber,
            "Due soon", live.count { it.status == "DUE_SOON" }.toString(), QgColors.Amber,
        )
        StatRow(
            "Overdue", live.count { it.status == "OVERDUE" || it.status == "TEMPORARILY_RESTRICTED" }.toString(), QgColors.Red,
            "Fully paid", agreements.count { it.status == "FULLY_PAID" }.toString(), QgColors.Blue,
        )
        SectionTitle("Devices")
        StatRow(
            "Managed", managed.size.toString(), QgColors.Silver,
            "Released", devices.count { it.managementStatus == "RELEASED" }.toString(), QgColors.Blue,
        )
        StatRow(
            "Locked", managed.count { it.lockState == "LOCKED" }.toString(), QgColors.Red,
            "Unlocked", managed.count { it.lockState != "LOCKED" }.toString(), QgColors.Green,
        )
        StatRow(
            "Online", managed.count { it.online }.toString(), QgColors.Green,
            "Offline", managed.count { !it.online }.toString(), QgColors.Muted,
        )
        Spacer(Modifier.height(4.dp))
        BigButton("CUSTOMERS", color = QgColors.SurfaceHigh) { nav.push(Route.Customers) }
        BigButton("+ NEW CUSTOMER") { nav.push(Route.AddCustomer) }
    }
}

@Composable
private fun StatRow(l1: String, v1: String, c1: Color, l2: String, v2: String, c2: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCard(l1, v1, c1, Modifier.weight(1f))
        StatCard(l2, v2, c2, Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(label: String, value: String, color: Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = QgColors.Surface),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(label, color = QgColors.Muted, fontSize = 12.sp)
            Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
    }
}
