package com.smokerider.app.ui.rider

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.smokerider.app.data.model.Order
import com.smokerider.app.data.model.Position
import kotlinx.coroutines.tasks.await

@Composable
fun RiderOrdersScreen(
    onOrderClick: (String) -> Unit // callback quando rider clicca un ordine
) {
    val db = FirebaseFirestore.getInstance()
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    var riderCity by remember { mutableStateOf<String?>(null) }
    var orders by remember { mutableStateOf<List<Order>>(emptyList()) }

    // 🔹 Recupera la città del rider
    LaunchedEffect(uid) {
        if (uid.isNotEmpty()) {
            val posDoc = db.collection("positions").document(uid).get().await()
            val pos = posDoc.toObject(Position::class.java)
            riderCity = pos?.city
        }
    }

    // 🔹 Recupera ordini pending e filtra per città cliente
    LaunchedEffect(riderCity) {
        riderCity?.let { city ->
            db.collection("orders")
                .whereEqualTo("status", "pending")
                .addSnapshotListener { snapshot, _ ->
                    snapshot?.let { snap ->
                        val allOrders = snap.documents.mapNotNull { it.toObject(Order::class.java) }

                        // 🔹 Recupera posizioni dei clienti e filtra
                        val filteredOrders = mutableListOf<Order>()

                        for (order in allOrders) {
                            val clientPosDoc = db.collection("positions").document(order.clientId)
                            clientPosDoc.get().addOnSuccessListener { posDoc ->
                                val clientPos = posDoc.toObject(Position::class.java)
                                if (clientPos?.city == city) {
                                    filteredOrders.add(order)
                                }
                                orders = filteredOrders.toList()
                            }
                        }
                    }
                }
        }
    }

    // 🔹 UI
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Ordini disponibili", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        if (orders.isEmpty()) {
            Text("Nessun ordine disponibile nella tua città.")
        } else {
            LazyColumn {
                items(orders) { order ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        onClick = { onOrderClick(order.id) }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Ordine #${order.id}", style = MaterialTheme.typography.titleMedium)
                            Text("Totale: €${order.total}", style = MaterialTheme.typography.bodyMedium)
                            Text("Stato: ${order.status}")
                        }
                    }
                }
            }
        }
    }
}
