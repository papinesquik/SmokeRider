package com.smokerider.app.ui.rider

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.smokerider.app.data.model.Order
import com.smokerider.app.data.model.Position
import com.smokerider.app.data.repository.toOrderSafe
import com.smokerider.app.ui.theme.screenInsets
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun TrackingRiderScreen(
    orderId: String,
    onGoHome: () -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val context = LocalContext.current

    var order by remember { mutableStateOf<Order?>(null) }
    var clientPos by remember { mutableStateOf<Position?>(null) }
    var orderListener by remember { mutableStateOf<ListenerRegistration?>(null) }
    var isUpdating by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var deliveredLocally by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val cameraPositionState = rememberCameraPositionState()

    // Listener ordine realtime (safe)
    LaunchedEffect(orderId) {
        orderListener?.remove()
        orderListener = db.collection("orders").document(orderId)
            .addSnapshotListener { snapshot, _ ->
                val o = snapshot?.toOrderSafe()
                order = o
                if (o?.status == "delivered") {
                    deliveredLocally = true
                }
            }
    }

    // Cleanup
    DisposableEffect(Unit) { onDispose { orderListener?.remove() } }

    // Carica posizione cliente
    LaunchedEffect(order?.clientId) {
        val cid = order?.clientId ?: return@LaunchedEffect
        val snap = db.collection("positions")
            .whereEqualTo("uid", cid)
            .limit(1)
            .get()
            .await()
        clientPos = snap.documents.firstOrNull()?.toObject(Position::class.java)
    }

    // centra mappa quando ho la posizione
    LaunchedEffect(clientPos?.latitude, clientPos?.longitude) {
        val lat = clientPos?.latitude
        val lng = clientPos?.longitude
        if (lat != null && lng != null) {
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 15f),
                durationMs = 700
            )
        }
    }

    fun euro(v: Double) = "€${"%.2f".format(v)}"

    fun openMaps(pos: Position?) {
        val lat = pos?.latitude
        val lng = pos?.longitude
        val q = listOfNotNull(pos?.street, pos?.city).joinToString(", ")
        val uri = when {
            lat != null && lng != null -> Uri.parse("google.navigation:q=$lat,$lng")
            q.isNotBlank() -> Uri.parse("geo:0,0?q=${Uri.encode(q)}")
            else -> null
        } ?: return
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
        })
    }

    // Root con screenInsets → niente overlap con status/gesture bar
    Box(
        modifier = Modifier
            .fillMaxSize()
            .screenInsets(includeTop = true, includeBottom = true, extraTop = 16.dp)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        val o = order
        if (o == null) {
            CircularProgressIndicator()
        } else {
            // Se non è in una fase tracciabile
            if (o.status == "pending" || o.status == "cancelled" || o.status == "expired") {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Ordine non disponibile per il tracking", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onGoHome) { Text("Torna alla home") }
                }
                return@Box
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🚴 Tracking ordine (Rider)", style = MaterialTheme.typography.headlineMedium)

                // MAPPA con marker (tap = apri Google Maps)
                val lat = clientPos?.latitude
                val lng = clientPos?.longitude
                if (lat != null && lng != null) {
                    val target = LatLng(lat, lng)
                    GoogleMap(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clickable { openMaps(clientPos) },
                        cameraPositionState = cameraPositionState
                    ) {
                        Marker(
                            state = com.google.maps.android.compose.rememberMarkerState(position = target),
                            title = "Consegna",
                            snippet = listOfNotNull(clientPos?.street, clientPos?.city).joinToString(", ")
                        )
                    }
                } else {
                    Text("Recupero posizione cliente…")
                }

                // ETA
                o.estimatedDeliveryTime?.takeIf { it > 0 }?.let { eta ->
                    AssistChip(onClick = {}, label = { Text("ETA: ${eta.toInt()} min") })
                } ?: Text("Tempo stimato consegna: Calcolo…")

                HorizontalDivider()

                // CTA per avanzare stato
                when {
                    deliveredLocally || o.status == "delivered" -> {
                        Text("Ordine consegnato ✅")
                        LaunchedEffect(Unit) { onGoHome() }
                    }

                    o.status == "accepted" -> {
                        Button(
                            enabled = !isUpdating,
                            onClick = {
                                errorMsg = null
                                isUpdating = true
                                scope.launch {
                                    try {
                                        val currentEta = o.estimatedDeliveryTime ?: 0.0
                                        var newEta: Double? = null

                                        if (currentEta > 0) {
                                            newEta = when {
                                                currentEta < 10 -> currentEta - 5
                                                currentEta <= 15 -> currentEta - 7
                                                else -> currentEta - 8
                                            }
                                            if (newEta <= 0) newEta = 1.0
                                        }

                                        val updates = if (newEta != null) {
                                            mapOf(
                                                "status" to "on_the_way",
                                                "estimatedDeliveryTime" to newEta
                                            )
                                        } else {
                                            mapOf("status" to "on_the_way")
                                        }

                                        db.collection("orders").document(orderId).update(updates).await()
                                    } catch (e: Exception) {
                                        errorMsg = "Errore: impossibile impostare 'in viaggio'."
                                    } finally {
                                        isUpdating = false
                                    }
                                }
                            }
                        ) { Text(if (isUpdating) "Aggiorno…" else "In viaggio 🚗") }
                    }

                    o.status == "on_the_way" -> {
                        Button(
                            enabled = !isUpdating,
                            onClick = {
                                errorMsg = null
                                isUpdating = true
                                scope.launch {
                                    try {
                                        db.collection("orders").document(orderId)
                                            .update("status", "delivered")
                                            .await()
                                        deliveredLocally = true
                                        onGoHome()
                                    } catch (e: Exception) {
                                        errorMsg = "Errore: impossibile segnare 'consegnato'."
                                    } finally {
                                        isUpdating = false
                                    }
                                }
                            }
                        ) { Text(if (isUpdating) "Aggiorno…" else "Consegnato ✅") }
                    }
                }

                if (errorMsg != null) {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error)
                }

                HorizontalDivider()

                // Dettagli ordine
                Text("Dettagli ordine:", style = MaterialTheme.typography.titleMedium)
                o.items.forEach { item ->
                    Text("• ${item.name} ×${item.quantity} = ${euro(item.price * item.quantity)}")
                }
                Text("Totale: ${euro(o.total)}", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}
