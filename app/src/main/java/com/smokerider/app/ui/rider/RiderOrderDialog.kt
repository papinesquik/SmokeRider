package com.smokerider.app.ui.rider

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.smokerider.app.data.model.Order
import com.smokerider.app.data.model.Position
import com.smokerider.app.data.repository.FirestoreOrders
import com.smokerider.app.data.repository.MapsRepository
import com.smokerider.app.data.repository.toOrderSafe
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun RiderOrderDialog(
    orderId: String,
    riderId: String,
    ordersRepo: FirestoreOrders,
    mapsRepo: MapsRepository,      // usa quello che hai in progetto
    onDismiss: () -> Unit,
    onAccepted: (String) -> Unit   // es. vai al tracking dopo “Accetta”
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var order by remember { mutableStateOf<Order?>(null) }
    var clientPos by remember { mutableStateOf<Position?>(null) }
    var listener by remember { mutableStateOf<ListenerRegistration?>(null) }

    var isAccepting by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // carica ordine (senza mostrare lo stato nell’UI del dialog)
    LaunchedEffect(orderId) {
        listener?.remove()
        listener = db.collection("orders").document(orderId)
            .addSnapshotListener { snap, _ ->
                order = snap?.toOrderSafe()
            }
    }
    DisposableEffect(Unit) { onDispose { listener?.remove() } }

    // posizione cliente (per “Apri in Mappe”)
    LaunchedEffect(order?.clientId) {
        val cid = order?.clientId ?: return@LaunchedEffect
        val snap = db.collection("positions")
            .whereEqualTo("uid", cid)
            .limit(1)
            .get()
            .await()
        clientPos = snap.documents.firstOrNull()?.toObject(Position::class.java)
    }

    fun openMaps() {
        val pos = clientPos ?: return
        val q = listOfNotNull(pos.street, pos.city).joinToString(", ")
        val uri = when {
            pos.latitude != null && pos.longitude != null ->
                Uri.parse("geo:${pos.latitude},${pos.longitude}?q=${pos.latitude},${pos.longitude}(${Uri.encode(q)})")
            q.isNotBlank() -> Uri.parse("geo:0,0?q=${Uri.encode(q)}")
            else -> null
        } ?: return
        ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
        })
    }

    fun euro(v: Double) = "€${"%.2f".format(v)}"
    val canAccept = order?.status == "pending"

    AlertDialog(
        onDismissRequest = { if (!isAccepting) onDismiss() },
        confirmButton = {
            Button(
                enabled = order != null && canAccept && !isAccepting,
                onClick = {
                    errorMsg = null
                    isAccepting = true
                    scope.launch {
                        val ok = ordersRepo.acceptOrder(orderId, riderId, mapsRepo)
                        isAccepting = false
                        if (ok) onAccepted(orderId)
                        else errorMsg = "Impossibile accettare l’ordine. Potrebbe essere già stato accettato."
                    }
                }
            ) {
                if (isAccepting) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (canAccept) "Accetta" else "Non disponibile")
            }
        },
        dismissButton = {
            TextButton(enabled = !isAccepting, onClick = onDismiss) { Text("Chiudi") }
        },
        title = { Text("Dettagli ordine") },
        text = {
            val o = order
            if (o == null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val addr = listOfNotNull(clientPos?.city, clientPos?.street).joinToString(", ")
                    Text(
                        text = "Consegna a: ${if (addr.isBlank()) "-" else addr}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = if (addr.isNotBlank()) Modifier.clickable { openMaps() } else Modifier
                    )

                    HorizontalDivider()

                    Text("Dettagli ordine:", style = MaterialTheme.typography.titleMedium)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        o.items.forEach { item ->
                            Text("• ${item.name} ×${item.quantity} = ${euro(item.price * item.quantity)}")
                        }
                    }
                    Text("Totale: ${euro(o.total)}", style = MaterialTheme.typography.titleLarge)

                    if (!canAccept) {
                        Text(
                            "Questo ordine non è più disponibile per l’accettazione.",
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }

                    if (errorMsg != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(errorMsg!!, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    )
}
