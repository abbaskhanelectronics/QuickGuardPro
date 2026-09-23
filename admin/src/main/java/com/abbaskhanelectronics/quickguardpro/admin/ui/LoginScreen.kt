package com.abbaskhanelectronics.quickguardpro.admin.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.abbaskhanelectronics.quickguardpro.admin.data.Repo
import com.abbaskhanelectronics.quickguardpro.admin.data.readable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun LoginScreen(initialError: String?, onSignedIn: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf(initialError) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        BrandHeader("Admin • Abbas Khan Electronics")
        Text("SMART SECURITY | TOTAL CONTROL | COMPLETE PEACE OF MIND", color = QgColors.Muted,
            style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = email, onValueChange = { email = it.trim() }, label = { Text("Email") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        OutlinedTextField(
            value = password, onValueChange = { password = it }, label = { Text("Password") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        BigButton(if (busy) "Signing in…" else "LOGIN", enabled = !busy && email.isNotBlank() && password.isNotBlank()) {
            busy = true
            scope.launch {
                try {
                    Repo.auth.signInWithEmailAndPassword(email, password).await()
                    onSignedIn()
                } catch (e: Exception) {
                    message = e.readable()
                } finally { busy = false }
            }
        }
        TextButton(onClick = {
            if (email.isBlank()) { message = "Enter your email first."; return@TextButton }
            scope.launch {
                message = try {
                    Repo.auth.sendPasswordResetEmail(email).await()
                    "Password reset email sent."
                } catch (e: Exception) { e.readable() }
            }
        }) { Text("Forgot password?") }
    }
}
