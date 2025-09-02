package com.smokerider.app.ui.customer

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.smokerider.app.data.repository.ClientRedirect
import com.smokerider.app.data.repository.FirestoreOrders
import com.smokerider.app.ui.utils.rememberLocationPermissionState

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag


@Composable
fun CustomerHomeScreen(
    navController: NavController,
    ordersRepo: FirestoreOrders,
    onEffettuaOrdineClick: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current

    // 1) Richiesta permesso posizione appena si entra in Home (customer)
    var locationGranted by rememberSaveable { mutableStateOf(false) }
    val hasPermission = rememberLocationPermissionState(
        context = context,
        onPermissionGranted = { locationGranted = true }
    )

    // 2) Logica redirect ordini esistente
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    var didNavigate by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uid) {
        if (uid != null && !didNavigate) {
            when (val r = ordersRepo.findClientRedirect(uid)) {
                is ClientRedirect.Tracking -> {
                    didNavigate = true
                    navController.navigate("customer/tracking/${r.orderId}") {
                        popUpTo("customer/home") { inclusive = true }
                        launchSingleTop = true
                    }
                }
                is ClientRedirect.Waiting -> {
                    didNavigate = true
                    navController.navigate("customer/attesa/${r.orderId}") {
                        popUpTo("customer/home") { inclusive = true }
                        launchSingleTop = true
                    }
                }
                ClientRedirect.None -> {
                    // resta in home
                }
            }
        }
    }

    // 3) UI
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .semantics { testTag = "CustomerHome" },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Benvenuto Cliente", style = MaterialTheme.typography.headlineMedium)

        // Stato permessi: se non concesso, informo l’utente
        if (!hasPermission) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Per usare l’app al meglio consenti l’accesso alla posizione.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { testTag = "GpsBanner" }
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    }
                ) {
                    Text("Apri impostazioni")
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onEffettuaOrdineClick,
            modifier = Modifier.fillMaxWidth().semantics { testTag = "EffettuaOrdineBtn" },
            enabled = hasPermission
        ) {
            Text("Effettua Ordine")
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth().semantics { testTag = "LogoutBtn" },
            colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.error)
        ) {
            Text("Logout")
        }
    }
}
