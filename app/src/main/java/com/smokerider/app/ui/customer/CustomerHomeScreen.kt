package com.smokerider.app.ui.customer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.smokerider.app.data.repository.ClientRedirect
import com.smokerider.app.data.repository.FirestoreOrders

@Composable
fun CustomerHomeScreen(
    navController: NavController,
    ordersRepo: FirestoreOrders,
    onEffettuaOrdineClick: () -> Unit,
    onLogout: () -> Unit
) {
    // uid utente loggato
    val uid = FirebaseAuth.getInstance().currentUser?.uid

    // evita doppia navigazione se la screen rientra in composizione
    var didNavigate by rememberSaveable { mutableStateOf(false) }

    // appena entro nella screen → controllo redirect
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
                    // nessun redirect, resta in home
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Benvenuto Cliente", style = MaterialTheme.typography.headlineMedium)

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onEffettuaOrdineClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Effettua Ordine")
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.error)
        ) {
            Text("Logout")
        }
    }
}
