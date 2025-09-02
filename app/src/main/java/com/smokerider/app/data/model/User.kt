package com.smokerider.app.data.model

data class User(
    val uid: String = "",                // ID univoco Firebase
    val email: String = "",              // Email utente
    val role: String = "customer",       // customer | rider | admin

    // 🔹 Campi specifici per i rider
    val identityDocument: String? = null, // documento di identità (solo rider)

    // 🔹 Campi comuni
    val online: Boolean = false,       // stato online/offline (solo rider)
    val fcmToken: String? = null,        // token per notifiche push
    val active: Boolean = true         // true = attivo, false = da approvare (solo rider)
)
