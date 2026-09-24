package com.abbaskhanelectronics.quickguardpro.admin.data

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

object Repo {
    val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    private val functions: FirebaseFunctions get() = FirebaseFunctions.getInstance()

    /** All writes go through Cloud Functions so the backend enforces roles and financial rules. */
    @Suppress("UNCHECKED_CAST")
    suspend fun call(name: String, data: Map<String, Any?> = emptyMap()): Map<String, Any?> {
        val res = functions.getHttpsCallable(name).call(data).await()
        return (res.data as? Map<String, Any?>) ?: emptyMap()
    }

    suspend fun loadProfile(): StaffUser? {
        val uid = auth.currentUser?.uid ?: return null
        val snap = db.collection("users").document(uid).get().await()
        if (snap.exists()) return snap.toStaff()
        // First login of the configured owner email creates the OWNER profile on the backend.
        return try {
            call("bootstrapOwner")
            db.collection("users").document(uid).get().await().takeIf { it.exists() }?.toStaff()
        } catch (e: Exception) {
            null
        }
    }

    fun <T> listen(query: Query, map: (DocumentSnapshot) -> T): Flow<List<T>> = callbackFlow {
        val reg = query.addSnapshotListener { snap, err ->
            if (err != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            trySend(snap?.documents?.map(map) ?: emptyList())
        }
        awaitClose { reg.remove() }
    }

    fun <T> listenDoc(path: String, map: (DocumentSnapshot) -> T): Flow<T?> = callbackFlow {
        val reg = db.document(path).addSnapshotListener { snap, _ ->
            trySend(if (snap != null && snap.exists()) map(snap) else null)
        }
        awaitClose { reg.remove() }
    }

    fun customers() = listen(db.collection("customers")) { it.toCustomer() }
    fun agreements() = listen(db.collection("agreements")) { it.toAgreement() }
    fun agreementsOf(customerId: String) =
        listen(db.collection("agreements").whereEqualTo("customerId", customerId)) { it.toAgreement() }
    fun agreement(id: String) = listenDoc("agreements/$id") { it.toAgreement() }
    fun customer(id: String) = listenDoc("customers/$id") { it.toCustomer() }
    fun payments() = listen(db.collection("payments")) { it.toPayment() }
    fun paymentsOf(agreementId: String) =
        listen(db.collection("payments").whereEqualTo("agreementId", agreementId)) { it.toPayment() }
    fun devices() = listen(db.collection("devices")) { it.toDevice() }
    fun device(id: String) = listenDoc("devices/$id") { it.toDevice() }
    fun commandsOf(deviceId: String) =
        listen(db.collection("commands").whereEqualTo("deviceId", deviceId)) { it.toCommand() }
    fun staff() = listen(db.collection("users")) { it.toStaff() }
    fun config(doc: String) = listenDoc("appConfig/$doc") { it.data ?: emptyMap<String, Any?>() }
}

fun Throwable.readable(): String = when (this) {
    is FirebaseFunctionsException -> when (code) {
        FirebaseFunctionsException.Code.UNAVAILABLE -> "Internet / server unavailable. Try again."
        FirebaseFunctionsException.Code.UNAUTHENTICATED -> "Session expired. Please log in again."
        FirebaseFunctionsException.Code.NOT_FOUND -> "Server function not found. Deploy the backend first."
        else -> message ?: code.name
    }
    is FirebaseNetworkException -> "No internet connection."
    is FirebaseAuthException -> message ?: "Authentication failed."
    is FirebaseFirestoreException -> message ?: "Database error."
    else -> message ?: "Something went wrong."
}
