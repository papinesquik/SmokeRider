package com.smokerider.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.smokerider.app.data.model.Product
import kotlinx.coroutines.tasks.await

class ProductRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val productsRef = firestore.collection("products")

    // aggiungi prodotto
    suspend fun addProduct(product: Product) {
        val docRef = productsRef.document()
        val newProduct = product.copy(id = docRef.id)
        docRef.set(newProduct).await()
    }

    //ottieni prodotti in tempo reale
    fun getProducts(callback: (List<Product>) -> Unit): ListenerRegistration {
        return productsRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                callback(emptyList())
                return@addSnapshotListener
            }
            val products = snapshot?.documents?.mapNotNull { it.toObject(Product::class.java) } ?: emptyList()
            callback(products)
        }
    }

    // aggiorna nome e prezzo
    suspend fun updateProduct(id: String, newName: String, newPrice: Double) {
        productsRef.document(id)
            .update(mapOf("name" to newName, "price" to newPrice))
            .await()
    }

    // elimina prodotto
    suspend fun deleteProduct(id: String) {
        productsRef.document(id).delete().await()
    }
}
