package com.smokerider.app.data.model

data class User(
    val uid: String = "",                // ID univoco Firebase
    val email: String = "",              // Email utente
    val role: String = "customer",       // customer | rider | admin

    // 🔹 Campi specifici per i rider
    val city: String? = null,            // città dove opera il rider
    val identityDocument: String? = null,// documento di identità (solo rider)
    val latitude: Double? = null,        // latitudine posizione base rider
    val longitude: Double? = null,       // longitudine posizione base rider

    // 🔹 Campi comuni
    val isOnline: Boolean = false,       // stato online/offline
    val fcmToken: String? = null         // token per notifiche push
)
