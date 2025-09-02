// File: ApprovaRiderScreen.kt
package com.smokerider.app.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smokerider.app.viewmodel.ApprovaRiderViewModel
import com.smokerider.app.viewmodel.RiderWithPosition

@Composable
fun ApprovaRiderScreen(
    vm: ApprovaRiderViewModel = viewModel()
) {
    val riders by vm.riders.collectAsState()
    var selected by remember { mutableStateOf<RiderWithPosition?>(null) }

    LaunchedEffect(Unit) {
        vm.loadPendingRiders()
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Rider da approvare", style = MaterialTheme.typography.headlineMedium)

        Spacer(Modifier.height(16.dp))

        if (riders.isEmpty()) {
            Text("Nessun rider in attesa")
        } else {
            riders.forEach { rider ->
                Text(
                    text = rider.user.email,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selected = rider }
                        .padding(12.dp)
                )
                Divider()
            }
        }
    }

    // Dialog dettagli rider
    if (selected != null) {
        val rider = selected!!
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("Dettagli Rider") },
            text = {
                Column {
                    Text("Email: ${rider.user.email}")
                    Text("Documento: ${rider.user.identityDocument ?: "-"}")
                    Text("Città: ${rider.position?.city ?: "-"}")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.approveRider(rider.user.uid)
                    selected = null
                }) { Text("Approva") }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.rejectRider(rider)
                    selected = null
                }) { Text("Rifiuta") }
            }
        )
    }
}
