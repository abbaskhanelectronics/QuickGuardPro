package com.abbaskhanelectronics.quickguardpro.admin

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.abbaskhanelectronics.quickguardpro.admin.data.Repo
import com.abbaskhanelectronics.quickguardpro.admin.data.StaffUser
import com.abbaskhanelectronics.quickguardpro.admin.ui.*
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { QgTheme { AdminRoot() } }
    }
}

@Composable
fun AdminRoot() {
    var user by remember { mutableStateOf<StaffUser?>(null) }
    var checking by remember { mutableStateOf(Repo.auth.currentUser != null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(checking) {
        if (checking) {
            val profile = try { Repo.loadProfile() } catch (e: Exception) { null }
            if (profile == null || !profile.active) {
                if (Repo.auth.currentUser != null) error = "This account is not authorized for Quick Guard Pro."
                Repo.auth.signOut()
            }
            user = profile?.takeIf { it.active }
            checking = false
        }
    }

    when {
        checking -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = QgColors.Red)
        }
        user == null -> LoginScreen(error) { error = null; checking = true }
        else -> Signed(user!!) { Repo.auth.signOut(); user = null }
    }
}

@Composable
private fun Signed(user: StaffUser, onLogout: () -> Unit) {
    val nav = remember { Nav() }
    val snack = remember { SnackbarHostState() }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(user.uid) {
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            Repo.call("registerAdminToken", mapOf("token" to token))
        } catch (_: Exception) { }
    }

    BackHandler(enabled = nav.stack.size > 1) { nav.pop() }

    CompositionLocalProvider(LocalNav provides nav, LocalSnack provides snack, LocalUser provides user) {
        when (val r = nav.current) {
            Route.Home -> HomeShell(onLogout)
            Route.Customers -> CustomersScreen()
            Route.AddCustomer -> AddCustomerScreen()
            is Route.CustomerDetail -> CustomerDetailScreen(r.id)
            is Route.NewAgreement -> NewAgreementScreen(r.customerId)
            is Route.AgreementDetail -> AgreementDetailScreen(r.id)
            is Route.EnrollQr -> EnrollQrScreen(r.agreementId)
            is Route.DeviceDetail -> DeviceDetailScreen(r.id)
            Route.Staff -> StaffScreen()
        }
    }
}
