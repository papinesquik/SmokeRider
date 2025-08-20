package com.smokerider.app.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smokerider.app.viewmodel.AuthViewModel

@Composable
fun AuthScreen(authViewModel: AuthViewModel = viewModel()) {
    // 🔹 Stati locali per i campi input
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var identityDocument by remember { mutableStateOf("") } // nuovo campo documento
    var role by remember { mutableStateOf("customer") }

    // 🔹 Toggle: login o registrazione
    var isLogin by remember { mutableStateOf(true) }

    // 🔹 Stato dal ViewModel
    val currentUser by authViewModel.currentUser.collectAsState()
    val loading by authViewModel.loading.collectAsState()
    val errorMessage by authViewModel.errorMessage.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        // Titolo
        Text(
            text = if (isLogin) "Login" else "Registrazione",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Campo email
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Campo password
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Solo in registrazione → Ruolo, città e documento
        if (!isLogin) {
            // 🔹 Scelta ruolo
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("customer", "rider", "admin").forEach {
                    Row(modifier = Modifier.padding(end = 8.dp)) {
                        RadioButton(
                            selected = role == it,
                            onClick = { role = it }
                        )
                        Text(it)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 🔹 Mostra campi aggiuntivi solo se rider
            if (role == "rider") {
                // Campo città
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text("Città in cui operi") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Campo documento di identità
                OutlinedTextField(
                    value = identityDocument,
                    onValueChange = { identityDocument = it },
                    label = { Text("Documento di identità") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 🔹 Pulsante principale
        Button(
            onClick = {
                if (isLogin) {
                    authViewModel.login(email, password)
                } else {
                    authViewModel.register(
                        email = email,
                        password = password,
                        role = role,
                        city = if (role == "rider") city else null,
                        identityDocument = if (role == "rider") identityDocument else null
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading
        ) {
            Text(if (isLogin) "Accedi" else "Registrati")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 🔹 Toggle login/registrazione
        TextButton(onClick = { isLogin = !isLogin }) {
            Text(if (isLogin) "Non hai un account? Registrati" else "Hai già un account? Accedi")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 🔹 Mostra errori
        if (errorMessage != null) {
            Text(
                text = errorMessage ?: "",
                color = MaterialTheme.colorScheme.error
            )
        }

        // 🔹 Mostra utente loggato
        if (currentUser != null) {
            Text("Benvenuto ${currentUser?.email} (${currentUser?.role})")
        }
    }
}
