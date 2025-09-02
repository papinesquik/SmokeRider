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
    // Manteniamo il parametro items per compatibilità, ma useremo lo stato del ViewModel per essere sempre allineati.
    items: List<OrderItem>,
    onOrderConfirmed: () -> Unit,
    positionViewModel: PositionViewModel = viewModel()
) {
    // Stato reattivo dal ViewModel (coerente con EffettuaOrdineScreen)
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

        // Lista articoli (sola lettura qui: le modifiche si fanno nella screen precedente)
        cart.forEach { item ->
            Text("${item.name} × ${item.quantity} = ${euro(item.price * item.quantity)}")
        }

        Spacer(Modifier.height(16.dp))
        Text("Totale: ${euro(total)}", style = MaterialTheme.typography.titleLarge)

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                // Prosegui alla conferma della posizione; l'ordine verrà creato nella screen successiva
                navController.navigate("customer/position")
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Conferma posizione e ordina")
        }
    }
}
