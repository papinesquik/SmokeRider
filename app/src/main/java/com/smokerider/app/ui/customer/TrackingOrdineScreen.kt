package com.smokerider.app.ui.customer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.smokerider.app.data.model.Order
import com.smokerider.app.data.model.Position
import com.smokerider.app.data.repository.toOrderSafe
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun TrackingOrdineScreen(
    orderId: String,
    onGoHome: () -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    var order by remember { mutableStateOf<Order?>(null) }
    var position by remember { mutableStateOf<Position?>(null) }
    var orderListener by remember { mutableStateOf<ListenerRegistration?>(null) }
    val scope = rememberCoroutineScope()

    // Listener realtime ordine (safe)
    LaunchedEffect(orderId) {
        orderListener?.remove()
        orderListener = db.collection("orders").document(orderId)
            .addSnapshotListener { snapshot, _ ->
                order = snapshot?.toOrderSafe()
            }
    }

    // cleanup listener
    DisposableEffect(Unit) {
        onDispose { orderListener?.remove() }
    }

    //Recupera posizione cliente via uid
    LaunchedEffect(order?.clientId) {
        val cid = order?.clientId ?: return@LaunchedEffect
        val snap = db.collection("positions")
            .whereEqualTo("uid", cid)
            .limit(1)
            .get()
            .await()
        position = snap.documents.firstOrNull()?.toObject(Position::class.java)
    }

    fun euro(v: Double) = "€${"%.2f".format(v)}"

    // UI
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        val o = order
        if (o == null) {
            CircularProgressIndicator()
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("📦 Tracking ordine", style = MaterialTheme.typography.headlineMedium)

                when (o.status) {
                    "pending", "accepted", "on_the_way" -> {
                        Text(statusMessage(o.status))

                        LinearProgressIndicator(
                            progress = progressForStatus(o.status),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )

                        Text(
                            "Tempo stimato consegna: " +
                                    (o.estimatedDeliveryTime?.let { "${it.toInt()} min" } ?: "Calcolo…")
                        )
                    }

                    "delivered" -> {
                        Text("Ordine consegnato ✅")

                        Spacer(Modifier.height(16.dp))
                        Button(onClick = {
                            scope.launch {
                                db.collection("orders").document(orderId).delete().await()
                                onGoHome()
                            }
                        }) { Text("Torna alla home") }
                    }

                    "cancelled", "expired" -> {
                        Text(
                            if (o.status == "cancelled") "Ordine annullato ❌" else "Ordine scaduto ⏰"
                        )

                        Spacer(Modifier.height(16.dp))
                        Button(onClick = {
                            scope.launch {
                                db.collection("orders").document(orderId).delete().await()
                                onGoHome()
                            }
                        }) { Text("Torna alla home") }
                    }
                }

                HorizontalDivider()

                // Recap ordine (sempre visibile)
                Text("Dettagli ordine:", style = MaterialTheme.typography.titleMedium)
                o.items.forEach { item ->
                    Text("• ${item.name} x${item.quantity} = ${euro(item.price * item.quantity)}")
                }
                Text("Totale: ${euro(o.total)}", style = MaterialTheme.typography.titleLarge)

                position?.let { pos ->
                    Spacer(Modifier.height(12.dp))
                    Text("Consegna a: ${listOfNotNull(pos.city, pos.street).joinToString(", ")}")
                }
            }
        }
    }
}

// Messaggi user-friendly
fun statusMessage(status: String): String = when (status) {
    "pending" -> "In attesa che un rider accetti il tuo ordine..."
    "accepted" -> "Il tuo ordine è stato accettato, il rider si sta dirigendo verso la tabaccheria 🚴‍♂️"
    "on_the_way" -> "Il rider è in viaggio verso di te 📍"
    "delivered" -> "Ordine consegnato ✅"
    "cancelled" -> "Ordine annullato ❌"
    "expired" -> "Ordine scaduto ⏰"
    else -> status
}

// Valore progress bar per ogni stato
fun progressForStatus(status: String): Float = when (status) {
    "pending" -> 0.25f
    "accepted" -> 0.5f
    "on_the_way" -> 0.75f
    "delivered" -> 1f
    else -> 0f
}
