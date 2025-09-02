package com.smokerider.app.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.smokerider.app.data.model.Order
import com.smokerider.app.data.model.OrderItem
import com.smokerider.app.data.model.Position
import com.smokerider.app.data.repository.toOrderSafe   // mapper safe per leggere l'ordine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.UUID

// --- Redirect post-login / app start ---
sealed interface ClientRedirect {
    data class Tracking(val orderId: String) : ClientRedirect
    data class Waiting(val orderId: String)  : ClientRedirect
    data object None : ClientRedirect
}

class FirestoreOrders {
    private val db = FirebaseFirestore.getInstance()
    private val col = db.collection("orders")
    private val posCol = db.collection("positions") // posizioni utenti (client e rider)

    fun createOrder(
        clientId: String,
        items: List<OrderItem>,
        total: Double,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val id = UUID.randomUUID().toString()
        val nowMs = System.currentTimeMillis()
        val expiresAtMs = nowMs + 600_000L // 10 minuti in ms

        val order = Order(
            id = id,
            clientId = clientId,
            items = items,
            total = total,
            status = "pending",
            createdAt = Timestamp(Date(nowMs)),       // ✅ Timestamp
            expiresAt = Timestamp(Date(expiresAtMs)), // ✅ Timestamp
            acceptedBy = null,
            estimatedDeliveryTime = null             // sarà calcolata dopo l'accettazione
        )

        col.document(id).set(order)
            .addOnSuccessListener { onComplete(true, id) }
            .addOnFailureListener { onComplete(false, null) }
    }

    /**
     * Accetta un ordine lato Rider in modo atomico.
     * - Accetta solo se: status == "pending", acceptedBy == null, non scaduto.
     * - Dopo l'accettazione, calcola e salva l'ETA (best-effort) con sanificazione e correzione.
     */
    suspend fun acceptOrder(orderId: String, riderId: String, mapsRepo: MapsRepository): Boolean {
        val orderRef = col.document(orderId)

        // 1) TRANSACTION: prova ad accettare in modo atomico
        val accepted = try {
            db.runTransaction { tx ->
                val snap = tx.get(orderRef)
                val order = snap.toOrderSafe() ?: return@runTransaction false

                val nowMs = System.currentTimeMillis()
                val expiresAtMs = order.expiresAt?.toDate()?.time ?: Long.MAX_VALUE
                val canAccept = order.status == "pending" &&
                        order.acceptedBy.isNullOrEmpty() &&
                        nowMs < expiresAtMs

                if (!canAccept) return@runTransaction false

                tx.update(
                    orderRef,
                    mapOf(
                        "status" to "accepted",
                        "acceptedBy" to riderId
                    )
                )
                true
            }.await()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }

        if (!accepted) return false

        // 2) BEST-EFFORT: calcolo ETA e salvo (se fallisce, l'ordine resta accettato)
        try {
            val current = orderRef.get().await().toOrderSafe() ?: return true
            val clientId = current.clientId

            // Posizione cliente
            val clientPosSnap = posCol
                .whereEqualTo("uid", clientId)
                .limit(1)
                .get()
                .await()
            val clientPos = clientPosSnap.documents.firstOrNull()?.toObject(Position::class.java)

            // Posizione rider
            val riderPosSnap = posCol
                .whereEqualTo("uid", riderId)
                .limit(1)
                .get()
                .await()
            val riderPos = riderPosSnap.documents.firstOrNull()?.toObject(Position::class.java)

            if (clientPos != null && riderPos != null) {
                val raw = mapsRepo.calculateDeliveryTime(
                    clientPos.latitude ?: 0.0,
                    clientPos.longitude ?: 0.0,
                    riderPos.latitude ?: 0.0,
                    riderPos.longitude ?: 0.0
                )

                // Sanifica: null/NaN/∞/<=0 => nessuna ETA
                val sanitized: Double? = when {
                    raw == null -> null
                    raw.isNaN() || raw.isInfinite() -> null
                    raw <= 0.0 -> null
                    else -> raw.toDouble()
                }

                //   - t < 5       -> +11
                //   - 5 <= t <=10 -> +10
                //   - t > 10      -> +9
                var ruleApplied: String? = null
                val adjusted: Double? = sanitized?.let { t ->
                    when {
                        t < 5 -> { ruleApplied = "<5 => +11"; t + 11 }
                        t <= 10 -> { ruleApplied = "5..10 => +10"; t + 10 }
                        else -> { ruleApplied = ">10 => +9"; t + 9 }
                    }
                }

                val etaDebug = mapOf(
                    "calculatedAt" to Timestamp.now(),
                    "from" to mapOf(
                        "lat" to (riderPos.latitude ?: 0.0),
                        "lng" to (riderPos.longitude ?: 0.0)
                    ),
                    "to"   to mapOf(
                        "lat" to (clientPos.latitude ?: 0.0),
                        "lng" to (clientPos.longitude ?: 0.0)
                    ),
                    "rawMinutes" to raw,
                    "sanitizedMinutes" to sanitized,
                    "ruleApplied" to ruleApplied,
                    "adjustedMinutes" to adjusted
                )

                orderRef.update(
                    mapOf(
                        // Salviamo l'ETA già corretta
                        "estimatedDeliveryTime" to adjusted, // può essere null se Maps fallisce
                        "etaCalculatedAt" to Timestamp.now(),
                        "etaDebug" to etaDebug
                    )
                ).await()
            }
        } catch (e: Exception) {
            // Logga ma non bloccare l'accettazione
            e.printStackTrace()
        }

        return true
    }

    /**
     * Decide il redirect per il CLIENTE dopo login o all'avvio app:
     * 1) Se esiste un ordine (clientId=uid) con status in {accepted, on_the_way, delivered}
     *    prende il più "recente" (createdAt) e torna Tracking(orderId).
     * 2) Altrimenti, se esiste un ordine con status = pending (più recente), torna Waiting(orderId).
     * 3) Altrimenti None.
     *
     * NB: se in futuro gestirai updatedAt, puoi spostare l'orderBy sul campo aggiornato.
     */
    suspend fun findClientRedirect(uid: String): ClientRedirect = withContext(Dispatchers.IO) {
        val trackingStatuses = listOf("accepted", "on_the_way", "delivered")

        // Evitiamo whereIn + orderBy: 3 query singole, poi scegliamo il più recente
        val bestTracking = trackingStatuses.mapNotNull { st ->
            col.whereEqualTo("clientId", uid)
                .whereEqualTo("status", st)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()
                .documents
                .firstOrNull()
        }.maxByOrNull { it.getTimestamp("createdAt")?.toDate()?.time ?: 0L }

        if (bestTracking != null) {
            return@withContext ClientRedirect.Tracking(bestTracking.id)
        }

        // Pending più recente
        val pending = col.whereEqualTo("clientId", uid)
            .whereEqualTo("status", "pending")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()

        if (pending != null) ClientRedirect.Waiting(pending.id) else ClientRedirect.None
    }
}
