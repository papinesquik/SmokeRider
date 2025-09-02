package com.smokerider.app.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
import com.smokerider.app.data.model.Order
import com.smokerider.app.data.model.OrderItem
import java.util.Date

private fun anyToTimestamp(any: Any?): Timestamp? = when (any) {
    is Timestamp -> any
    is Long -> Timestamp(Date(any))
    is Int -> Timestamp(Date(any.toLong()))
    is Double -> Timestamp(Date(any.toLong()))
    is Map<*, *> -> {
        val seconds = (any["_seconds"] as? Number)?.toLong()
        val nanos = (any["_nanoseconds"] as? Number)?.toInt() ?: 0
        if (seconds != null) Timestamp(seconds, nanos) else null
    }
    else -> null
}

private fun anyToDouble(any: Any?): Double? = when (any) {
    is Number -> any.toDouble()
    is String -> any.toDoubleOrNull()
    else -> null
}

@Suppress("UNCHECKED_CAST")
private fun anyToItems(any: Any?): List<OrderItem> {
    if (any !is List<*>) return emptyList()
    return any.mapNotNull { el ->
        val m = el as? Map<String, Any?> ?: return@mapNotNull null
        OrderItem(
            productId = m["productId"] as? String ?: "",
            name = m["name"] as? String ?: "",
            quantity = (m["quantity"] as? Number)?.toInt() ?: 1,
            price = anyToDouble(m["price"]) ?: 0.0
        )
    }
}

fun DocumentSnapshot.toOrderSafe(): Order? {
    val d = data ?: return null
    return Order(
        id = d["id"] as? String ?: id,
        clientId = d["clientId"] as? String ?: "",
        items = anyToItems(d["items"]),
        total = anyToDouble(d["total"]) ?: 0.0,
        status = d["status"] as? String ?: "pending",
        createdAt = anyToTimestamp(d["createdAt"]),
        expiresAt = anyToTimestamp(d["expiresAt"]),
        acceptedBy = d["acceptedBy"] as? String,
        estimatedDeliveryTime = anyToDouble(d["estimatedDeliveryTime"])
    )
}

fun QuerySnapshot.toOrdersSafe(): List<Order> =
    documents.mapNotNull { it.toOrderSafe() }
