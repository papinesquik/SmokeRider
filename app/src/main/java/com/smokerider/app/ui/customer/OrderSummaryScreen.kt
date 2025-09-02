package com.smokerider.app.ui.customer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smokerider.app.data.model.OrderItem
import com.smokerider.app.viewmodel.PositionViewModel
import com.smokerider.app.viewmodel.OrderViewModel

@Composable
fun OrderSummaryScreen(
    navController: NavController,
    orderViewModel: OrderViewModel,
    items: List<OrderItem>,
    onOrderConfirmed: () -> Unit,
    positionViewModel: PositionViewModel = viewModel()
) {
    val cart by orderViewModel.items.collectAsState()
    val total by orderViewModel.total.collectAsState(initial = 0.0)

    fun euro(v: Double) = "€${"%.2f".format(v)}"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Riepilogo ordine", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        if (cart.isEmpty()) {
            Text("Il carrello è vuoto.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Torna alla scelta prodotti")
            }
            return@Column
        }

        cart.forEach { item ->
            Text("${item.name} × ${item.quantity} = ${euro(item.price * item.quantity)}")
        }

        Spacer(Modifier.height(16.dp))
        Text("Totale: ${euro(total)}", style = MaterialTheme.typography.titleLarge)

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                navController.navigate("customer/position")
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Conferma posizione e ordina")
        }
    }
}
