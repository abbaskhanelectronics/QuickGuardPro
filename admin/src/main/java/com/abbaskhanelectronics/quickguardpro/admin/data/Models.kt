package com.abbaskhanelectronics.quickguardpro.admin.data

import com.google.firebase.firestore.DocumentSnapshot
import java.util.Date

data class StaffUser(val uid: String, val name: String, val email: String, val role: String, val active: Boolean) {
    val isOwner get() = role == "OWNER"
    val isManagerOrOwner get() = role == "OWNER" || role == "MANAGER"
}

data class Customer(
    val id: String, val name: String, val fatherName: String, val phone: String, val altPhone: String,
    val cnic: String, val address: String, val reference: String, val notes: String, val createdAt: Date?,
)

data class Agreement(
    val id: String, val agreementNo: String, val customerId: String, val customerName: String,
    val customerPhone: String, val productName: String, val deviceId: String,
    val cashPrice: Long, val totalPrice: Long, val downPayment: Long,
    val installmentCount: Int, val installmentAmount: Long, val frequency: String,
    val nextDueDate: Date?, val graceDays: Int, val paidInstallments: Int,
    val totalCollected: Long, val remainingBalance: Long, val status: String, val createdAt: Date?,
)

data class Payment(
    val id: String, val agreementId: String, val amount: Long, val method: String, val reference: String,
    val notes: String, val receiptNo: String, val previousBalance: Long, val newBalance: Long,
    val staffName: String, val kind: String, val reversed: Boolean, val createdAt: Date?,
)

data class Loc(val lat: Double, val lng: Double, val accuracy: Double, val time: Date?, val lastKnown: Boolean)

data class ManagedDevice(
    val id: String, val agreementId: String, val customerName: String, val brand: String, val model: String,
    val androidVersion: String, val appVersion: String, val isDeviceOwner: Boolean, val lockState: String,
    val managementStatus: String, val battery: Int, val charging: Boolean, val lastSeen: Date?,
    val simAlert: Boolean, val simInfo: String, val imei: String, val location: Loc?,
    val capabilities: Map<String, String>,
) {
    val online: Boolean get() = lastSeen?.let { System.currentTimeMillis() - it.time < 30 * 60 * 1000 } ?: false
}

data class Command(
    val id: String, val deviceId: String, val action: String, val status: String, val createdByName: String,
    val createdAt: Date?, val executedAt: Date?, val result: String,
)

private fun DocumentSnapshot.s(k: String) = getString(k) ?: ""
private fun DocumentSnapshot.l(k: String) = (get(k) as? Number)?.toLong() ?: 0L
private fun DocumentSnapshot.i(k: String) = (get(k) as? Number)?.toInt() ?: 0
private fun DocumentSnapshot.d(k: String) = getTimestamp(k)?.toDate()
private fun DocumentSnapshot.b(k: String) = getBoolean(k) ?: false

fun DocumentSnapshot.toStaff() = StaffUser(id, s("name"), s("email"), s("role"), b("active"))

fun DocumentSnapshot.toCustomer() = Customer(
    id, s("name"), s("fatherName"), s("phone"), s("altPhone"), s("cnic"), s("address"), s("reference"),
    s("notes"), d("createdAt"),
)

fun DocumentSnapshot.toAgreement() = Agreement(
    id, s("agreementNo"), s("customerId"), s("customerName"), s("customerPhone"), s("productName"),
    s("deviceId"), l("cashPrice"), l("totalPrice"), l("downPayment"), i("installmentCount"),
    l("installmentAmount"), s("frequency"), d("nextDueDate"), i("graceDays"), i("paidInstallments"),
    l("totalCollected"), l("remainingBalance"), s("status"), d("createdAt"),
)

fun DocumentSnapshot.toPayment() = Payment(
    id, s("agreementId"), l("amount"), s("method"), s("reference"), s("notes"), s("receiptNo"),
    l("previousBalance"), l("newBalance"), s("staffName"), s("kind"), b("reversed"), d("createdAt"),
)

@Suppress("UNCHECKED_CAST")
fun DocumentSnapshot.toDevice(): ManagedDevice {
    val locMap = get("lastLocation") as? Map<String, Any?>
    val loc = locMap?.let {
        Loc(
            (it["lat"] as? Number)?.toDouble() ?: 0.0,
            (it["lng"] as? Number)?.toDouble() ?: 0.0,
            (it["accuracy"] as? Number)?.toDouble() ?: 0.0,
            (it["time"] as? com.google.firebase.Timestamp)?.toDate(),
            it["lastKnown"] as? Boolean ?: false,
        )
    }
    val caps = (get("capabilities") as? Map<String, Any?>)?.mapValues { it.value?.toString() ?: "" } ?: emptyMap()
    return ManagedDevice(
        id, s("agreementId"), s("customerName"), s("brand"), s("model"), s("androidVersion"), s("appVersion"),
        b("isDeviceOwner"), s("lockState"), s("managementStatus"), i("battery"), b("charging"), d("lastSeen"),
        b("simAlert"), s("simInfo"), s("imei"), loc, caps,
    )
}

fun DocumentSnapshot.toCommand() = Command(
    id, s("deviceId"), s("action"), s("status"), s("createdByName"), d("createdAt"), d("executedAt"), s("result"),
)
