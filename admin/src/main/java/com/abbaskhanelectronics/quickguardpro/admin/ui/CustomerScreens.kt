package com.abbaskhanelectronics.quickguardpro.admin.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.abbaskhanelectronics.quickguardpro.admin.data.Agreement
import com.abbaskhanelectronics.quickguardpro.admin.data.Repo
import java.time.LocalDate

@Composable
fun CustomersScreen() {
    val nav = LocalNav.current
    val customers by remember { Repo.customers() }.collectAsState(initial = emptyList())
    val agreements by remember { Repo.agreements() }.collectAsState(initial = emptyList())
    var q by remember { mutableStateOf("") }
    val filtered = customers
        .filter { c ->
            if (q.isBlank()) true else {
                val needle = q.lowercase()
                c.name.lowercase().contains(needle) || c.phone.contains(needle) || c.id.lowercase().contains(needle) ||
                    c.cnic.contains(needle) ||
                    agreements.any { a -> a.customerId == c.id && (a.agreementNo.lowercase().contains(needle) || a.deviceId.lowercase().contains(needle)) }
            }
        }
        .sortedByDescending { it.createdAt }

    DetailScaffold(
        "Customers",
        floating = {
            FloatingActionButton(onClick = { nav.push(Route.AddCustomer) }, containerColor = QgColors.Red) {
                Icon(Icons.Filled.Add, contentDescription = "Add customer")
            }
        },
    ) {
        Field("Search name, phone, CNIC, agreement no.", q, { q = it })
        if (filtered.isEmpty()) EmptyState(if (customers.isEmpty()) "No customers yet. Tap + to add one." else "No match.")
        filtered.forEach { c ->
            QgCard(Modifier.clickable { nav.push(Route.CustomerDetail(c.id)) }) {
                Text(c.name, fontWeight = FontWeight.Bold)
                Text(c.phone, color = QgColors.Muted)
                val count = agreements.count { it.customerId == c.id }
                if (count > 0) Text("$count agreement(s)", color = QgColors.Silver)
            }
        }
    }
}

@Composable
fun AddCustomerScreen() {
    val nav = LocalNav.current
    val runner = rememberRunner()
    var name by remember { mutableStateOf("") }
    var father by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var alt by remember { mutableStateOf("") }
    var cnic by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    DetailScaffold("New Customer") {
        Field("Full name *", name, { name = it })
        Field("Father name", father, { father = it })
        Field("Phone *", phone, { phone = it }, phone = true)
        Field("Alternate phone", alt, { alt = it }, phone = true)
        Field("CNIC", cnic, { cnic = it }, number = true)
        Field("Address", address, { address = it }, singleLine = false)
        Field("Reference / guarantor contact", reference, { reference = it })
        Field("Notes", notes, { notes = it }, singleLine = false)
        BigButton(
            if (runner.busy) "Saving…" else "SAVE CUSTOMER",
            enabled = !runner.busy && name.isNotBlank() && phone.length >= 10,
        ) {
            runner.run("Customer saved") {
                val res = Repo.call(
                    "createCustomer",
                    mapOf(
                        "name" to name, "fatherName" to father, "phone" to phone, "altPhone" to alt,
                        "cnic" to cnic, "address" to address, "reference" to reference, "notes" to notes,
                    ),
                )
                nav.pop()
                (res["id"] as? String)?.let { nav.push(Route.CustomerDetail(it)) }
            }
        }
    }
}

@Composable
fun CustomerDetailScreen(id: String) {
    val nav = LocalNav.current
    val customer by remember(id) { Repo.customer(id) }.collectAsState(initial = null)
    val agreements by remember(id) { Repo.agreementsOf(id) }.collectAsState(initial = emptyList())
    val c = customer
    DetailScaffold(c?.name ?: "Customer") {
        if (c == null) { EmptyState("Loading…"); return@DetailScaffold }
        QgCard {
            InfoRow("Customer ID", c.id.take(8).uppercase())
            InfoRow("Name", c.name)
            if (c.fatherName.isNotBlank()) InfoRow("Father", c.fatherName)
            InfoRow("Phone", c.phone)
            if (c.altPhone.isNotBlank()) InfoRow("Alt phone", c.altPhone)
            if (c.cnic.isNotBlank()) InfoRow("CNIC", c.cnic)
            if (c.address.isNotBlank()) InfoRow("Address", c.address)
            if (c.reference.isNotBlank()) InfoRow("Reference", c.reference)
            InfoRow("Registered", c.createdAt.fmtDate())
            if (c.notes.isNotBlank()) Text(c.notes, color = QgColors.Muted)
        }
        SectionTitle("Agreements")
        if (agreements.isEmpty()) EmptyState("No installment agreement yet.")
        agreements.sortedByDescending { it.createdAt }.forEach { a -> AgreementRow(a) }
        BigButton("+ NEW INSTALLMENT AGREEMENT") { nav.push(Route.NewAgreement(id)) }
    }
}

@Composable
fun AgreementRow(a: Agreement) {
    val nav = LocalNav.current
    QgCard(Modifier.clickable { nav.push(Route.AgreementDetail(a.id)) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(a.agreementNo, fontWeight = FontWeight.Bold)
                Text("${a.customerName} • ${a.productName}", color = QgColors.Muted)
            }
            StatusChip(statusLabel(a.status), statusColor(a.status))
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { if (a.installmentCount == 0) 0f else a.paidInstallments / a.installmentCount.toFloat() },
            modifier = Modifier.fillMaxWidth(),
            color = QgColors.Red,
            trackColor = QgColors.Outline,
        )
        Spacer(Modifier.height(6.dp))
        InfoRow("Remaining", formatPkr(a.remainingBalance))
        InfoRow("Next due", if (a.remainingBalance == 0L) "—" else a.nextDueDate.fmtDate())
    }
}

@Composable
fun NewAgreementScreen(customerId: String) {
    val nav = LocalNav.current
    val runner = rememberRunner()
    var product by remember { mutableStateOf("") }
    var imei by remember { mutableStateOf("") }
    var cash by remember { mutableStateOf("") }
    var total by remember { mutableStateOf("") }
    var down by remember { mutableStateOf("") }
    var count by remember { mutableStateOf("6") }
    var frequency by remember { mutableStateOf("MONTHLY") }
    var firstDue by remember { mutableStateOf(LocalDate.now().plusMonths(1).toString()) }
    var grace by remember { mutableStateOf("3") }

    val totalL = total.toLongOrNull() ?: 0
    val downL = down.toLongOrNull() ?: 0
    val countI = count.toIntOrNull() ?: 0
    val perInstallment = if (countI > 0 && totalL > downL) (totalL - downL + countI - 1) / countI else 0
    val dateOk = runCatching { LocalDate.parse(firstDue) }.isSuccess

    DetailScaffold("New Agreement") {
        Field("Product / phone model *", product, { product = it })
        Field("IMEI (optional)", imei, { imei = it }, number = true)
        Field("Cash price (Rs)", cash, { cash = it.filter(Char::isDigit) }, number = true)
        Field("Total installment price (Rs) *", total, { total = it.filter(Char::isDigit) }, number = true)
        Field("Down payment (Rs)", down, { down = it.filter(Char::isDigit) }, number = true)
        Field("Number of installments *", count, { count = it.filter(Char::isDigit) }, number = true)
        SectionTitle("Frequency")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("MONTHLY", "FORTNIGHTLY", "WEEKLY").forEach { f ->
                FilterChip(selected = frequency == f, onClick = { frequency = f }, label = { Text(f.lowercase()) })
            }
        }
        Field("First due date (YYYY-MM-DD) *", firstDue, { firstDue = it })
        if (!dateOk) Text("Date format: 2026-10-25", color = MaterialTheme.colorScheme.error)
        Field("Grace period (days)", grace, { grace = it.filter(Char::isDigit) }, number = true)
        QgCard {
            InfoRow("Financed amount", formatPkr((totalL - downL).coerceAtLeast(0)))
            InfoRow("Per installment", formatPkr(perInstallment))
        }
        BigButton(
            if (runner.busy) "Creating…" else "CREATE AGREEMENT",
            enabled = !runner.busy && product.isNotBlank() && totalL > 0 && downL < totalL && countI in 1..60 && dateOk,
        ) {
            runner.run("Agreement created") {
                val res = Repo.call(
                    "createAgreement",
                    mapOf(
                        "customerId" to customerId, "productName" to product, "imei" to imei,
                        "cashPrice" to (cash.toLongOrNull() ?: 0L), "totalPrice" to totalL, "downPayment" to downL,
                        "installmentCount" to countI, "frequency" to frequency, "firstDueDate" to firstDue,
                        "graceDays" to (grace.toIntOrNull() ?: 0),
                    ),
                )
                nav.pop()
                (res["id"] as? String)?.let { nav.push(Route.AgreementDetail(it)) }
            }
        }
    }
}
