package com.smokerider.app.data.model

data class Position(
    val id: String = "",          // ID documento Firestore
    val uid: String = "",         // ID utente collegato
    val city: String = "",        // città generica
    val street: String? = null,   // opzionale: via o indirizzo
    val latitude: Double? = null, // latitudine
    val longitude: Double? = null,// longitudine
    val timestamp: Long = System.currentTimeMillis() // quando è stata aggiornata
)
