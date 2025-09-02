// File: app/src/main/java/com/smokerider/app/ui/auth/AuthScreen.kt
package com.smokerider.app.ui.auth

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.smokerider.app.viewmodel.AuthViewModel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

@Composable
fun AuthScreen(
    navController: androidx.navigation.NavHostController,
    authViewModel: AuthViewModel = viewModel()
) {
    val ui by authViewModel.ui.collectAsState()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var identityDocument by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("customer") }
    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var isLogin by remember { mutableStateOf(true) }
    var isAdultChecked by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val autocompleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                val place = Autocomplete.getPlaceFromIntent(data)
                val addressComponents = place.addressComponents?.asList() ?: emptyList()
                val cityComponent = addressComponents.firstOrNull { "locality" in it.types }
                city = cityComponent?.name ?: place.name.orEmpty()
                latitude = place.latLng?.latitude
                longitude = place.latLng?.longitude
            }
        }
    }

    LaunchedEffect(ui.signedIn) {
        val user = ui.user
        if (ui.signedIn && user != null) {
            Toast.makeText(context, "Accesso effettuato ✅", Toast.LENGTH_SHORT).show()

            val uid = user.uid
            val role = user.role
            val db = FirebaseFirestore.getInstance()

            when (role) {
                "customer" -> {
                    try {
                        val snap = db.collection("orders")
                            .whereEqualTo("clientId", uid)
                            .whereIn("status", listOf("pending", "accepted", "on_the_way", "delivered"))
                            .limit(1)
                            .get().await()

                        val doc = snap.documents.firstOrNull()
                        if (doc != null) {
                            when (doc.getString("status")) {
                                "pending" -> navController.navigate("customer/attesa/${doc.id}") {
                                    popUpTo("auth") { inclusive = true }
                                }
                                "accepted", "on_the_way", "delivered" -> navController.navigate("customer/tracking/${doc.id}") {
                                    popUpTo("auth") { inclusive = true }
                                }
                                else -> navController.navigate("customer/home") {
                                    popUpTo("auth") { inclusive = true }
                                }
                            }
                        } else {
                            navController.navigate("customer/home") {
                                popUpTo("auth") { inclusive = true }
                            }
                        }
                    } catch (e: Exception) {
                        navController.navigate("customer/home") {
                            popUpTo("auth") { inclusive = true }
                        }
                    }
                }

                "rider" -> {
                    try {
                        val snap = db.collection("orders")
                            .whereEqualTo("acceptedBy", uid)
                            .whereIn("status", listOf("accepted", "on_the_way"))
                            .limit(1)
                            .get().await()

                        val doc = snap.documents.firstOrNull()
                        if (doc != null) {
                            navController.navigate("rider/tracking/${doc.id}") {
                                popUpTo("auth") { inclusive = true }
                            }
                        } else {
                            navController.navigate("rider/home") {
                                popUpTo("auth") { inclusive = true }
                            }
                        }
                    } catch (e: Exception) {
                        navController.navigate("rider/home") {
                            popUpTo("auth") { inclusive = true }
                        }
                    }
                }

                "admin" -> {
                    navController.navigate("admin/home") {
                        popUpTo("auth") { inclusive = true }
                    }
                }

                else -> {
                    navController.navigate("auth") {
                        popUpTo("auth") { inclusive = true }
                    }
                }
            }
        }
    }

    LaunchedEffect(ui.error) {
        ui.error?.let {
            snackbarHostState.showSnackbar(it)
            authViewModel.clearError()
        }
    }

    LaunchedEffect(ui.successMessage) {
        ui.successMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            isLogin = true
            email = ""
            password = ""
            city = ""
            identityDocument = ""
            role = "customer"
            latitude = null
            longitude = null
            isAdultChecked = false
            authViewModel.clearSuccessMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (isLogin) "Login" else "Registrazione",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !ui.loading
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                enabled = !ui.loading
            )

            Spacer(Modifier.height(8.dp))

            if (!isLogin) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    listOf("customer", "rider").forEach { roleOption ->
                        Row(
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .clickable(enabled = !ui.loading) { role = roleOption },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = role == roleOption,
                                onClick = { role = roleOption },
                                enabled = !ui.loading
                            )
                            Text(roleOption)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                if (role == "rider") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !ui.loading) {
                                val fields = listOf(
                                    Place.Field.ID,
                                    Place.Field.NAME,
                                    Place.Field.LAT_LNG,
                                    Place.Field.ADDRESS_COMPONENTS
                                )
                                val intent = Autocomplete.IntentBuilder(
                                    AutocompleteActivityMode.FULLSCREEN,
                                    fields
                                ).build(context)
                                autocompleteLauncher.launch(intent)
                            }
                    ) {
                        OutlinedTextField(
                            value = city,
                            onValueChange = { },
                            label = { Text("Città in cui operi") },
                            modifier = Modifier.fillMaxWidth(),
                            readOnly = true,
                            enabled = false
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = identityDocument,
                        onValueChange = { identityDocument = it },
                        label = { Text("Documento di identità") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !ui.loading
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Checkbox(
                        checked = isAdultChecked,
                        onCheckedChange = { isAdultChecked = it }
                    )
                    Text("Dichiaro di avere almeno 18 anni")
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    if (isLogin) {
                        authViewModel.login(email, password)
                    } else {
                        if (!isAdultChecked) {
                            Toast.makeText(context, "Devi dichiarare di avere almeno 18 anni", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (role == "rider" && (city.isBlank() || identityDocument.isBlank())) {
                            Toast.makeText(context, "Compila tutti i campi richiesti per i rider", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        authViewModel.register(
                            email = email,
                            password = password,
                            role = role,
                            city = if (role == "rider") city else null,
                            identityDocument = if (role == "rider") identityDocument else null,
                            latitude = if (role == "rider") latitude else null,
                            longitude = if (role == "rider") longitude else null
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !ui.loading && (isLogin || isAdultChecked)
            ) {
                Text(if (ui.loading) "Attendere..." else if (isLogin) "Accedi" else "Registrati")
            }

            Spacer(Modifier.height(8.dp))

            TextButton(
                onClick = { isLogin = !isLogin },
                enabled = !ui.loading
            ) {
                Text(if (isLogin) "Non hai un account? Registrati" else "Hai già un account? Accedi")
            }

            if (ui.loading) {
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            val user = ui.user
            if (ui.signedIn && user != null) {
                Spacer(Modifier.height(12.dp))
                Text("Loggato come: ${user.email} (${user.role})")
            }
        }
    }
}
