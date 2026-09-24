package com.abbaskhanelectronics.quickguardpro.admin.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.abbaskhanelectronics.quickguardpro.admin.data.StaffUser
import com.abbaskhanelectronics.quickguardpro.admin.data.readable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface Route {
    data object Home : Route
    data object Customers : Route
    data object AddCustomer : Route
    data class CustomerDetail(val id: String) : Route
    data class NewAgreement(val customerId: String) : Route
    data class AgreementDetail(val id: String) : Route
    data class EnrollQr(val agreementId: String) : Route
    data class DeviceDetail(val id: String) : Route
    data object Staff : Route
}

class Nav {
    val stack = mutableStateListOf<Route>(Route.Home)
    var tab by mutableIntStateOf(0)
    val current: Route get() = stack.last()
    fun push(r: Route) { stack.add(r) }
    fun pop(): Boolean = if (stack.size > 1) { stack.removeAt(stack.lastIndex); true } else false
}

val LocalNav = staticCompositionLocalOf<Nav> { error("Nav missing") }
val LocalSnack = staticCompositionLocalOf<SnackbarHostState> { error("Snack missing") }
val LocalUser = staticCompositionLocalOf<StaffUser> { error("User missing") }

class Runner(private val scope: CoroutineScope, private val snack: SnackbarHostState) {
    var busy by mutableStateOf(false)
        private set

    fun run(success: String? = null, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                block()
                if (success != null) snack.showSnackbar(success)
            } catch (e: Exception) {
                snack.showSnackbar(e.readable())
            } finally {
                busy = false
            }
        }
    }
}

@Composable
fun rememberRunner(): Runner {
    val scope = rememberCoroutineScope()
    val snack = LocalSnack.current
    return remember { Runner(scope, snack) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScaffold(
    title: String,
    actions: @Composable RowScope.() -> Unit = {},
    floating: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val nav = LocalNav.current
    Scaffold(
        containerColor = QgColors.Bg,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { nav.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = QgColors.Bg),
            )
        },
        floatingActionButton = floating,
        snackbarHost = { SnackbarHost(LocalSnack.current) },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    number: Boolean = false,
    phone: Boolean = false,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(
            keyboardType = when {
                number -> KeyboardType.Number
                phone -> KeyboardType.Phone
                else -> KeyboardType.Text
            }
        ),
    )
}

@Composable
fun EmptyState(text: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp)) {
        Text(text, color = QgColors.Muted)
    }
}

private val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.US)
private val dateTimeFmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US)
fun Date?.fmtDate(): String = this?.let { dateFmt.format(it) } ?: "—"
fun Date?.fmtDateTime(): String = this?.let { dateTimeFmt.format(it) } ?: "—"
fun Date?.ago(): String {
    if (this == null) return "Never"
    val m = (System.currentTimeMillis() - time) / 60000
    return when {
        m < 1 -> "Just now"
        m < 60 -> "$m min ago"
        m < 1440 -> "${m / 60} h ago"
        else -> "${m / 1440} days ago"
    }
}

fun Modifier.horizontalScrollSafe(): Modifier = androidx.compose.ui.composed {
    this.then(Modifier.horizontalScroll(rememberScrollState()))
}
