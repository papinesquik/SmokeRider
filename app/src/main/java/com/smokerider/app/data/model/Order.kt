package com.smokerider.app.data.model

import com.google.firebase.Timestamp

data class Order(
    val id: String = "",
    val clientId: String = "",                // uid cliente
    val items: List<OrderItem> = emptyList(), // prodotti ordinati
    val total: Double = 0.0,                  // totale € ordine

    /**
     * Stati possibili:
     * - "pending"   → in attesa che un rider accetti
     * - "accepted"  → accettato da un rider
     * - "on_the_way"→ rider in consegna
     * - "delivered" → ordine consegnato
     * - "cancelled" → annullato dal cliente
     * - "expired"   → scaduto (nessun rider entro la deadline)
     */
    val status: String = "pending",

    val createdAt: Timestamp? = null,   // quando creato
    val expiresAt: Timestamp? = null,   // scadenza (per pending)

    val acceptedBy: String? = null,     // uid rider che ha accettato
    val estimatedDeliveryTime: Double? = null // minuti stimati di consegna
)

data class OrderItem(
    val productId: String = "",
    val name: String = "",
    val quantity: Int = 1,
    val price: Double = 0.0
)
