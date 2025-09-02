package com.smokerider.app.ui.customer

import android.os.CountDownTimer
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.smokerider.app.data.repository.toOrderSafe
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

@Composable
fun AttesaOrdineScreen(
    orderId: String,
    onExpired: () -> Unit,
    onCancelled: () -> Unit,
    onAccepted: (String) -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val scope = rememberCoroutineScope()

    var remainingTime by remember { mutableStateOf(0L) }       // secondi
    var status by remember { mutableStateOf("pending") }

    var timer: CountDownTimer? by remember { mutableStateOf(null) }
    var listener: ListenerRegistration? by remember { mutableStateOf(null) }
    var navigated by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }

    fun startTimer(nowMs: Long, expiresAt: Timestamp?) {
        timer?.cancel()
        val expiresAtMs = expiresAt?.toDate()?.time ?: 0L
        val diffMs = (expiresAtMs - nowMs).coerceAtLeast(0)
        val diffSec = diffMs / 1000L
        remainingTime = diffSec
        if (diffSec == 0L) {
            status = "expired"
            return
        }
        timer = object : CountDownTimer(diffMs, 1_000L) {
            override fun onTick(millisUntilFinished: Long) {
                remainingTime = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished)
            }
            override fun onFinish() {
                remainingTime = 0
                status = "expired"
            }
        }.start()
    }

    suspend fun loadAndListen() {
        // Caricamento iniziale
        val snap = db.collection("orders").document(orderId).get().await()
        val order = snap.toOrderSafe()
        if (order != null) {
            status = order.status
            startTimer(System.currentTimeMillis(), order.expiresAt)
            // Se entriamo su attesa ma lo stato è già oltre "pending", navighiamo subito
            when (order.status) {
                "accepted", "on_the_way", "delivered" -> if (!navigated) {
                    navigated = true
                    timer?.cancel()
                    onAccepted(orderId)
                }
                "expired" -> if (!navigated) {
                    navigated = true
                    timer?.cancel()
                    onExpired()
                }
                "cancelled" -> if (!navigated) {
                    navigated = true
                    timer?.cancel()
                    onCancelled()
                }
            }
        } else {
            // Ordine non trovato: torna alla home come se fosse cancellato
            if (!navigated) {
                navigated = true
                onCancelled()
            }
        }

        // Listener realtime
        listener?.remove()
        listener = db.collection("orders").document(orderId)
            .addSnapshotListener { snapshot, _ ->
                val o = snapshot?.toOrderSafe() ?: return@addSnapshotListener
                status = o.status
                startTimer(System.currentTimeMillis(), o.expiresAt)

                when (o.status) {
                    "accepted", "on_the_way", "delivered" -> if (!navigated) {
                        navigated = true
                        timer?.cancel()
                        onAccepted(orderId)
                    }
                    "expired" -> if (!navigated) {
                        navigated = true
                        timer?.cancel()
                        onExpired()
                    }
                    "cancelled" -> if (!navigated) {
                        navigated = true
                        timer?.cancel()
                        onCancelled()
                    }
                }
            }
    }

    LaunchedEffect(orderId) {
        navigated = false
        isBusy = false
        loadAndListen()
    }

    DisposableEffect(Unit) {
        onDispose {
            timer?.cancel()
            listener?.remove()
        }
    }

    fun formatTime(seconds: Long): String {
        val minutes = seconds / 60
        val sec = seconds % 60
        return String.format("%02d:%02d", minutes, sec)
    }

    // UI
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (status) {
            "pending" -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Attendi che un rider accetti il tuo ordine")
                Spacer(Modifier.height(16.dp))
                Text("Tempo rimanente: ${formatTime(remainingTime)}")
                CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
                Spacer(Modifier.height(24.dp))
                Button(
                    enabled = !isBusy,
                    onClick = {
                        scope.launch {
                            try {
                                isBusy = true
                                db.collection("orders").document(orderId)
                                    .update("status", "cancelled")
                                    .await()
                                // Il listener intercetterà "cancelled" e farà il redirect
                            } finally {
                                isBusy = false
                            }
                        }
                    }
                ) { Text(if (isBusy) "Annullamento…" else "Annulla ordine") }
            }

            "expired" -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("L’ordine è scaduto ⏰")
                Spacer(Modifier.height(16.dp))
                Button(
                    enabled = !isBusy,
                    onClick = {
                        scope.launch {
                            try {
                                isBusy = true
                                db.collection("orders").document(orderId).delete().await()
                                if (!navigated) {
                                    navigated = true
                                    onExpired()
                                }
                            } finally {
                                isBusy = false
                            }
                        }
                    }
                ) { Text(if (isBusy) "Sto tornando…" else "Torna alla home") }
            }

            "cancelled" -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Hai annullato l’ordine ❌")
                Spacer(Modifier.height(16.dp))
                Button(
                    enabled = !isBusy,
                    onClick = {
                        scope.launch {
                            try {
                                isBusy = true
                                db.collection("orders").document(orderId).delete().await()
                                if (!navigated) {
                                    navigated = true
                                    onCancelled()
                                }
                            } finally {
                                isBusy = false
                            }
                        }
                    }
                ) { Text(if (isBusy) "Sto tornando…" else "Torna alla home") }
            }

            // Stati “ponte”: mostrati per un attimo, poi il listener fa redirect
            "accepted", "on_the_way", "delivered" -> CircularProgressIndicator()

            else -> CircularProgressIndicator()
        }
    }
}
