package com.smokerider.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smokerider.app.data.model.User
import com.smokerider.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val repository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    // 🔹 Registrazione nuovo utente (ora con city + identityDocument + posizione rider)
    fun register(
        email: String,
        password: String,
        role: String,
        city: String? = null,
        identityDocument: String? = null,
        latitude: Double? = null,
        longitude: Double? = null
    ) {
        viewModelScope.launch {
            _loading.value = true
            val result = repository.registerUser(
                email = email,
                password = password,
                role = role,
                city = city,
                identityDocument = identityDocument,
                latitude = latitude,
                longitude = longitude
            )
            _loading.value = false

            result.onSuccess { user ->
                _currentUser.value = user
                _errorMessage.value = null
                // appena registrato → settiamo online
                setOnlineStatus(user.uid, true)
            }.onFailure { e ->
                _errorMessage.value = e.message
            }
        }
    }

    // 🔹 Login
    fun login(email: String, password: String) {
        viewModelScope.launch {
            _loading.value = true
            val result = repository.loginUser(email, password)
            _loading.value = false

            result.onSuccess { user ->
                _currentUser.value = user
                _errorMessage.value = null
                // login → set online
                setOnlineStatus(user.uid, true)
            }.onFailure { e ->
                _errorMessage.value = e.message
            }
        }
    }

    // 🔹 Logout
    fun logout() {
        val uid = _currentUser.value?.uid
        if (uid != null) {
            viewModelScope.launch {
                setOnlineStatus(uid, false) // logout → set offline
                repository.logout()
                _currentUser.value = null
            }
        } else {
            repository.logout()
            _currentUser.value = null
        }
    }

    // 🔹 Aggiorna stato online/offline
    fun setOnlineStatus(uid: String, isOnline: Boolean) {
        viewModelScope.launch {
            try {
                repository.updateOnlineStatus(uid, isOnline)
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
        }
    }

    // 🔹 Aggiorna token FCM
    fun updateFcmToken(uid: String, token: String) {
        viewModelScope.launch {
            try {
                repository.updateFcmToken(uid, token)
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
        }
    }
}
