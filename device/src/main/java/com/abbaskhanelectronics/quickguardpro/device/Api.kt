package com.abbaskhanelectronics.quickguardpro.device

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await

object Api {
    @Suppress("UNCHECKED_CAST")
    suspend fun call(name: String, data: Map<String, Any?>): Map<String, Any?> {
        val res = FirebaseFunctions.getInstance().getHttpsCallable(name).call(data).await()
        return (res.data as? Map<String, Any?>) ?: emptyMap()
    }
}

fun Throwable.readable(): String = when (this) {
    is FirebaseFunctionsException -> when (code) {
        FirebaseFunctionsException.Code.UNAVAILABLE -> "انٹرنیٹ دستیاب نہیں / No internet"
        else -> message ?: code.name
    }
    else -> message ?: "Error"
}
