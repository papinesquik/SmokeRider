package com.smokerider.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.smokerider.app.data.model.User
import com.smokerider.app.data.model.Position
import kotlinx.coroutines.tasks.await
import android.util.Log

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    // Registrazione nuovo utente
    suspend fun registerUser(
        email: String,
        password: String,
        role: String,
        city: String? = null,
        street: String? = null,
        identityDocument: String? = null,
        latitude: Double? = null,
        longitude: Double? = null
    ): Result<User> {
        return try {
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val uid = authResult.user?.uid ?: throw Exception("UID nullo")

            // Creo l’oggetto User (senza city/lat/lon → sono in Position)
            val user = if (role == "rider") {
                if (city.isNullOrBlank()) throw Exception("La città è obbligatoria per i rider")
                if (identityDocument.isNullOrBlank()) throw Exception("Il documento è obbligatorio per i rider")

                User(
                    uid = uid,
                    email = email,
                    role = role,
                    identityDocument = identityDocument,
                    active = false // rider va approvato da admin
                )
            } else {
                User(
                    uid = uid,
                    email = email,
                    role = role,
                    active = true // customer e admin attivi subito
                )
            }

            // Salvo utente in Firestore
            firestore.collection("users").document(uid).set(user).await()

            // Salvo posizione separata se ho dati
            if (city != null || street != null || latitude != null || longitude != null) {
                val positionId = firestore.collection("positions").document().id
                val position = Position(
                    id = positionId,
                    uid = uid,
                    city = city ?: "",
                    street = street,
                    latitude = latitude,
                    longitude = longitude
                )
                firestore.collection("positions").document(positionId).set(position).await()
            }

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Login utente esistente
    suspend fun loginUser(email: String, password: String): Result<User> {
        return try {
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            val uid = authResult.user?.uid ?: throw Exception("UID nullo")

            // Recupero dati utente
            val user = getUserById(uid) ?: throw Exception("Utente non trovato")

            Log.d(
                "AuthRepository",
                "DEBUG LOGIN → role=${user.role}, active=${user.active}, online=${user.online}"
            )

            if (user.role == "rider" && !user.active) {
                throw Exception("Il tuo account è in attesa di approvazione da parte dell'admin")
            }

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Aggiorna stato online/offline
    suspend fun updateOnlineStatus(uid: String, online: Boolean) {
        firestore.collection("users").document(uid)
            .update("online", online)
            .await()
    }

    // Aggiorna token FCM
    suspend fun updateFcmToken(uid: String, token: String) {
        firestore.collection("users").document(uid)
            .update("fcmToken", token)
            .await()
    }

    // Logout
    fun logout() {
        auth.signOut()
    }

    // Ottieni solo UID
    fun getCurrentUserId(): String? = auth.currentUser?.uid

    // Ottieni utente loggato con dati da Firestore
    suspend fun getCurrentUser(): User? {
        val uid = auth.currentUser?.uid ?: return null
        return getUserById(uid)
    }

    // Recupera un utente da Firestore
    suspend fun getUserById(uid: String): User? {
        val snapshot = firestore.collection("users").document(uid).get().await()
        if (!snapshot.exists()) return null

        val data = snapshot.data ?: return null

        return User(
            uid = data["uid"] as? String ?: "",
            email = data["email"] as? String ?: "",
            role = data["role"] as? String ?: "customer",
            identityDocument = data["identityDocument"] as? String,
            online = data["online"] as? Boolean ?: false,
            fcmToken = data["fcmToken"] as? String,
            active = data["active"] as? Boolean ?: false
        )
    }
}
