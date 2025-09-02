package com.smokerider.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smokerider.app.data.model.User
import com.smokerider.app.data.repository.AuthRepository
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

//token FCM
import com.google.firebase.messaging.FirebaseMessaging
// per pulire il token al logout
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue

/** Stato UI centralizzato per l’autenticazione */
data class AuthUiState(
    val loading: Boolean = false,
    val user: User? = null,
    val error: String? = null,
    val signedIn: Boolean = false,
    val successMessage: String? = null
)

class AuthViewModel(
    private val repository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _ui = MutableStateFlow(AuthUiState())
    val ui: StateFlow<AuthUiState> = _ui

    /** Shortcut osservabili */
    val currentUser: StateFlow<User?> =
        ui.map { it.user }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val loading: StateFlow<Boolean> =
        ui.map { it.loading }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val errorMessage: StateFlow<String?> =
        ui.map { it.error }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Registrazione + salvataggio user (+ posizione se rider) */
    fun register(
        email: String,
        password: String,
        role: String,
        city: String? = null,
        street: String? = null,
        identityDocument: String? = null,
        latitude: Double? = null,
        longitude: Double? = null
    ) {
        if (_ui.value.loading) return
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null, signedIn = false) }
            try {
                requireNotBlank(email, "Email obbligatoria")
                requireNotBlank(password, "Password obbligatoria")
                requireNotBlank(role, "Ruolo obbligatorio")

                val result = repository.registerUser(
                    email = email.trim(),
                    password = password,
                    role = role,
                    city = city?.trim(),
                    street = street?.trim(),
                    identityDocument = identityDocument?.trim(),
                    latitude = latitude,
                    longitude = longitude
                )

                result.onSuccess {
                    _ui.update {
                        it.copy(
                            loading = false,
                            user = null,
                            error = null,
                            signedIn = false,
                            successMessage = "Registrazione avvenuta con successo. Effettua il login."
                        )
                    }
                }.onFailure { e ->
                    _ui.update { it.copy(loading = false, error = e.userMessage(), signedIn = false) }
                }
            } catch (t: Throwable) {
                _ui.update { it.copy(loading = false, error = t.userMessage(), signedIn = false) }
            }
        }
    }

    fun login(email: String, password: String) {
        if (_ui.value.loading) return
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null, signedIn = false) }
            try {
                requireNotBlank(email, "Email obbligatoria")
                requireNotBlank(password, "Password obbligatoria")

                val result = repository.loginUser(email.trim(), password)
                result.onSuccess { user ->
                    _ui.update { it.copy(loading = false, user = user, error = null, signedIn = true) }
                    setOnlineStatus(user.uid, true)

                    // SOLO rider: salva/aggiorna token
                    if (user.role.equals("rider", ignoreCase = true)) {
                        FirebaseMessaging.getInstance().token
                            .addOnSuccessListener { token -> updateFcmToken(user.uid, token) }
                    }
                    // clienti: nessuna azione sul token
                }.onFailure { e ->
                    _ui.update { it.copy(loading = false, error = e.userMessage(), signedIn = false) }
                }
            } catch (t: Throwable) {
                _ui.update { it.copy(loading = false, error = t.userMessage(), signedIn = false) }
            }
        }
    }

    fun logout() {
        val u = _ui.value.user
        viewModelScope.launch {
            try {
                // se era rider, togliamo il campo dal suo doc (ma NON cancelliamo il token del device)
                if (u?.role?.equals("rider", ignoreCase = true) == true) {
                    FirebaseFirestore.getInstance().collection("users").document(u.uid)
                        .update("fcmToken", FieldValue.delete())
                }
                if (u != null) setOnlineStatus(u.uid, false)
                repository.logout()
            } finally {
                _ui.update { AuthUiState() }
            }
        }
    }

    /** Aggiorna stato online/offline */
    fun setOnlineStatus(uid: String, online: Boolean) {
        viewModelScope.launch {
            try {
                repository.updateOnlineStatus(uid, online)
            } catch (t: Throwable) {
                _ui.update { it.copy(error = t.userMessage()) }
            }
        }
    }

    /** Aggiorna token FCM su Firestore */
    fun updateFcmToken(uid: String, token: String) {
        viewModelScope.launch {
            try {
                repository.updateFcmToken(uid, token)
            } catch (t: Throwable) {
                _ui.update { it.copy(error = t.userMessage()) }
            }
        }
    }

    /** Forza rilettura utente da Firebase */
    fun refreshAuth() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            try {
                val fb = Firebase.auth.currentUser
                val prev = _ui.value.user
                val user = fb?.let {
                    User(
                        uid = it.uid,
                        email = it.email ?: prev?.email.orEmpty(),
                        role = prev?.role ?: "",
                        identityDocument = prev?.identityDocument ?: "",
                        online = prev?.online ?: false,
                        active = prev?.active ?: true,
                        fcmToken = prev?.fcmToken
                    )
                }
                _ui.update { it.copy(loading = false, user = user, signedIn = user != null) }
            } catch (t: Throwable) {
                _ui.update { it.copy(loading = false, error = t.userMessage()) }
            }
        }
    }

    fun clearError() {
        _ui.update { it.copy(error = null) }
    }

    fun clearSuccessMessage() {
        _ui.update { it.copy(successMessage = null) }
    }
}

/* =========================
   Helpers
   ========================= */

private fun requireNotBlank(value: String?, message: String) {
    if (value.isNullOrBlank()) throw IllegalArgumentException(message)
}

/** Mappa le eccezioni in messaggi “umani” */
private fun Throwable.userMessage(): String {
    val raw = this.message ?: this.toString()
    return when {
        raw.contains("The email address is badly formatted", ignoreCase = true) ->
            "Email non valida"
        raw.contains("email address is already in use", ignoreCase = true) ->
            "Email già registrata"
        raw.contains("network", ignoreCase = true) ->
            "Problema di rete: controlla la connessione"
        else -> raw
    }
}
