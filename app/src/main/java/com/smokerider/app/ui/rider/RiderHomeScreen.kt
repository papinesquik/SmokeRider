package com.smokerider.app.ui.rider

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.smokerider.app.R
import com.smokerider.app.data.model.Order
import com.smokerider.app.data.model.Position
import com.smokerider.app.data.repository.FirestoreOrders
import com.smokerider.app.data.repository.MapsRepository
import com.smokerider.app.data.repository.toOrdersSafe
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun RiderHomeScreen(
    onOrderClick: (String) -> Unit,
    onLogout: () -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val riderId = remember { FirebaseAuth.getInstance().currentUser?.uid.orEmpty() }

    val context = LocalContext.current
    val ordersRepo = remember { FirestoreOrders() }
    val mapsRepo = remember { MapsRepository(context.getString(R.string.google_maps_key)) }

    var isOnline by remember { mutableStateOf(false) }
    var riderCity by remember { mutableStateOf<String?>(null) }

    var orders by remember { mutableStateOf<List<Order>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    var dialogOrderId by remember { mutableStateOf<String?>(null) }

    var userListener by remember { mutableStateOf<ListenerRegistration?>(null) }
    var posListener by remember { mutableStateOf<ListenerRegistration?>(null) }
    var ordersListener by remember { mutableStateOf<ListenerRegistration?>(null) }

    val scope = rememberCoroutineScope()

    // --- Permesso notifiche Android 13+ (una volta) ---
    RequestPostNotificationsPermissionOnce()

    // --- Rebind del token FCM ---
    LaunchedEffect(riderId) {
        if (riderId.isNotEmpty()) {
            val role = try {
                db.collection("users").document(riderId).get().await()
                    .getString("role")?.lowercase()
            } catch (_: Exception) { null }

            if (role == "rider") {
                try {
                    val token = FirebaseMessaging.getInstance().token.await()
                    db.collection("users").document(riderId)
                        .set(mapOf("fcmToken" to token), SetOptions.merge())
                } catch (_: Exception) {
                    // ignora errori
                }
            }
        }
    }

    suspend fun loadClientsCityMap(clientIds: Set<String>): Map<String, String?> {
        if (clientIds.isEmpty()) return emptyMap()
        val chunks = clientIds.chunked(10)
        val results = mutableMapOf<String, String?>()
        for (chunk in chunks) {
            val snap = db.collection("positions")
                .whereIn("uid", chunk)
                .get()
                .await()
            for (doc in snap.documents) {
                val p = doc.toObject(Position::class.java)
                if (p != null) results[p.uid] = p.city
            }
        }
        return results
    }

    fun attachOrdersListener(currentCity: String?) {
        ordersListener?.remove()
        if (currentCity == null || !isOnline) {
            orders = emptyList()
            isLoading = false
            return
        }
        isLoading = true
        errorMsg = null

        ordersListener = db.collection("orders")
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot: QuerySnapshot?, e ->
                if (e != null) {
                    errorMsg = "Errore nel caricare gli ordini."
                    orders = emptyList()
                    isLoading = false
                    return@addSnapshotListener
                }

                scope.launch {
                    val allOrders = snapshot?.toOrdersSafe() ?: emptyList()
                    val clientIds = allOrders.map { it.clientId }.toSet()
                    val cityMap = loadClientsCityMap(clientIds)

                    val filtered = allOrders.filter { o ->
                        cityMap[o.clientId] == currentCity
                    }

                    orders = filtered.sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                    isLoading = false
                }
            }
    }

    fun attachUserListener() {
        userListener?.remove()
        if (riderId.isEmpty()) return
        userListener = db.collection("users").document(riderId)
            .addSnapshotListener { doc, _ ->
                val online = doc?.getBoolean("online") ?: false
                isOnline = online
                attachOrdersListener(riderCity)
            }
    }

    fun attachRiderCityListener() {
        posListener?.remove()
        if (riderId.isEmpty()) return
        posListener = db.collection("positions")
            .whereEqualTo("uid", riderId)
            .limit(1)
            .addSnapshotListener { snapshot, _ ->
                val pos = snapshot?.documents?.firstOrNull()?.toObject(Position::class.java)
                val newCity = pos?.city
                if (newCity != riderCity) {
                    riderCity = newCity
                    attachOrdersListener(newCity)
                }
            }
    }

    // 🔀 Auto-redirect se rider ha già ordine in corso
    LaunchedEffect(riderId) {
        if (riderId.isNotEmpty()) {
            try {
                val snap = db.collection("orders")
                    .whereEqualTo("acceptedBy", riderId)
                    .whereIn("status", listOf("accepted", "on_the_way"))
                    .limit(1)
                    .get()
                    .await()

                val doc = snap.documents.firstOrNull()
                if (doc != null) {
                    onOrderClick(doc.id)
                    return@LaunchedEffect
                }
            } catch (_: Exception) {
                // ignora errori
            }

            attachUserListener()
            attachRiderCityListener()
        } else {
            isLoading = false
            errorMsg = "Devi effettuare l’accesso come rider."
        }
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            userListener?.remove()
            posListener?.remove()
            ordersListener?.remove()
        }
    }

    // UI
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("🚴 Rider Dashboard", style = MaterialTheme.typography.headlineMedium)

        Button(
            onClick = onLogout,
            colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.error)
        ) {
            Text("Logout")
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Stato: ${if (isOnline) "Online ✅" else "Offline ⛔"}")

            Switch(
                checked = isOnline,
                onCheckedChange = { checked ->
                    val prev = isOnline
                    isOnline = checked
                    if (riderId.isNotEmpty()) {
                        db.collection("users").document(riderId)
                            .update("online", checked)
                            .addOnFailureListener { isOnline = prev }
                            .addOnSuccessListener {
                                attachOrdersListener(riderCity)
                            }
                    }
                }
            )
        }

        HorizontalDivider()

        if (!isOnline) {
            Text("Sei offline. Attiva lo stato online per ricevere ordini.")
            return@Column
        }

        Text(
            "Ordini disponibili a ${riderCity ?: "-"}:",
            style = MaterialTheme.typography.titleMedium
        )

        when {
            errorMsg != null -> Text(errorMsg!!, color = MaterialTheme.colorScheme.error)
            isLoading -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) { CircularProgressIndicator() }
            }
            orders.isEmpty() -> Text("Nessun ordine disponibile al momento.")
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = orders,
                        key = { it.id }
                    ) { order ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (order.status == "pending") {
                                        dialogOrderId = order.id
                                    }
                                },
                            elevation = CardDefaults.cardElevation(4.dp)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    "Ordine #${order.id.take(6)}",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text("Totale: €${"%.2f".format(order.total)}")
                                Text("Stato: ${order.status}")
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog per accettare ordini
    if (dialogOrderId != null && riderId.isNotEmpty()) {
        RiderOrderDialog(
            orderId = dialogOrderId!!,
            riderId = riderId,
            ordersRepo = ordersRepo,
            mapsRepo = mapsRepo,
            onDismiss = { dialogOrderId = null },
            onAccepted = { acceptedId ->
                dialogOrderId = null
                onOrderClick(acceptedId)
            }
        )
    }
}

@Composable
private fun RequestPostNotificationsPermissionOnce() {
    if (Build.VERSION.SDK_INT < 33) return
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* ignore */ }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
