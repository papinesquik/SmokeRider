package com.smokerider.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.smokerider.app.data.model.User
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    // 🔹 Registrazione nuovo utente
    suspend fun registerUser(
        email: String,
        password: String,
        role: String,
        city: String? = null,
        identityDocument: String? = null,
        latitude: Double? = null,
        longitude: Double? = null
    ): Result<User> {
        return try {
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val uid = authResult.user?.uid ?: throw Exception("UID nullo")

            // 🔹 Se rider → obbligatori city + documento
            val user = if (role == "rider") {
                if (city.isNullOrBlank()) throw Exception("La città è obbligatoria per i rider")
                if (identityDocument.isNullOrBlank()) throw Exception("Il documento è obbligatorio per i rider")

                User(
                    uid = uid,
                    email = email,
                    role = role,
                    city = city,
                    identityDocument = identityDocument,
                    latitude = latitude,
                    longitude = longitude
                )
            } else {
                User(
                    uid = uid,
                    email = email,
                    role = role
                )
            }

            // 🔹 salvo su Firestore
            firestore.collection("users").document(uid).set(user).await()

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 🔹 Login utente esistente
    suspend fun loginUser(email: String, password: String): Result<User> {
        return try {
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            val uid = authResult.user?.uid ?: throw Exception("UID nullo")

            // 🔹 prendo i dati da Firestore
            val snapshot = firestore.collection("users").document(uid).get().await()
            val user = snapshot.toObject(User::class.java) ?: throw Exception("Utente non trovato")

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 🔹 Aggiorna stato online/offline
    suspend fun updateOnlineStatus(uid: String, isOnline: Boolean) {
        firestore.collection("users").document(uid)
            .update("isOnline", isOnline)
            .await()
    }

    // 🔹 Aggiorna token FCM del dispositivo
    suspend fun updateFcmToken(uid: String, token: String) {
        firestore.collection("users").document(uid)
            .update("fcmToken", token)
            .await()
    }

    // 🔹 Logout
    fun logout() {
        auth.signOut()
    }

    // 🔹 Controllo se c'è un utente già loggato
    fun getCurrentUserId(): String? = auth.currentUser?.uid
}
