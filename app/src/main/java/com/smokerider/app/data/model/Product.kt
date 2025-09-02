package com.smokerider.app.data.model

data class Product(
    val id: String = "",       // ID documento Firestore
    val name: String = "",     // Nome prodotto
    val price: Double = 0.0,   // Prezzo
    val category: String = ""  // Categoria
)
