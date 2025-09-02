package com.smokerider.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smokerider.app.data.model.OrderItem
import com.smokerider.app.data.model.Product
import com.smokerider.app.data.repository.FirestoreOrders
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlin.math.max

class OrderViewModel(
    private val repository: FirestoreOrders = FirestoreOrders()
) : ViewModel() {

    // Carrello corrente
    private val _items = MutableStateFlow<List<OrderItem>>(emptyList())
    val items: StateFlow<List<OrderItem>> = _items

    // Totale reattivo (€) con 2 decimali
    val total: StateFlow<Double> = _items
        .map { list -> list.sumOf { it.price * it.quantity } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    // Numero totale pezzi nel carrello
    val itemCount: StateFlow<Int> = _items
        .map { list -> list.sumOf { it.quantity } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Aggiungi N unità (>=1) di un prodotto. Se esiste, somma */
    fun addItem(product: Product, quantity: Int = 1) {
        if (quantity <= 0) return
        val current = _items.value.toMutableList()
        val idx = current.indexOfFirst { it.productId == product.id }
        if (idx >= 0) {
            val ex = current[idx]
            current[idx] = ex.copy(quantity = ex.quantity + quantity)
        } else {
            current.add(
                OrderItem(
                    productId = product.id,
                    name = product.name,
                    price = product.price,
                    quantity = quantity
                )
            )
        }
        _items.value = current
    }

    /** Imposta la quantità assoluta. Se qty <= 0 rimuove l'articolo */
    fun setQuantity(productId: String, qty: Int) {
        val current = _items.value.toMutableList()
        val idx = current.indexOfFirst { it.productId == productId }
        if (idx < 0) return
        if (qty <= 0) {
            current.removeAt(idx)
        } else {
            current[idx] = current[idx].copy(quantity = qty)
        }
        _items.value = current
    }

    /** +1 alla quantità (se non esiste, non fa nulla)*/
    fun increment(productId: String) {
        val current = _items.value.toMutableList()
        val idx = current.indexOfFirst { it.productId == productId }
        if (idx < 0) return
        val ex = current[idx]
        current[idx] = ex.copy(quantity = ex.quantity + 1)
        _items.value = current
    }

    /** -1 alla quantità. Se scende a 0 rimuove l'articolo*/
    fun decrement(productId: String) {
        val current = _items.value.toMutableList()
        val idx = current.indexOfFirst { it.productId == productId }
        if (idx < 0) return
        val ex = current[idx]
        val newQ = ex.quantity - 1
        if (newQ <= 0) current.removeAt(idx) else current[idx] = ex.copy(quantity = newQ)
        _items.value = current
    }

    /** Rimuove completamente un prodotto dal carrello*/
    fun removeItem(productId: String) {
        val filtered = _items.value.filterNot { it.productId == productId }
        _items.value = filtered
    }

    /** Svuota il carrello*/
    fun clearOrder() {
        _items.value = emptyList()
    }

    /** Ritorna una snapshot degli item */
    fun getItems(): List<OrderItem> = _items.value

    /** Crea l'ordine usando lo stato corrente e svuota se va a buon fine*/
    fun createOrder(
        clientId: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val snapshot = _items.value
        val tot = snapshot.sumOf { it.price * it.quantity }
        if (snapshot.isEmpty() || tot <= 0.0) {
            onComplete(false, null)
            return
        }
        viewModelScope.launch {
            repository.createOrder(clientId, snapshot, tot) { ok, id ->
                if (ok) clearOrder()
                onComplete(ok, id)
            }
        }
    }

    fun createOrder(
        clientId: String,
        items: List<OrderItem>,
        total: Double,
        onComplete: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            repository.createOrder(clientId, items, total) { ok, id ->
                if (ok) clearOrder()
                onComplete(ok, id)
            }
        }
    }
}
