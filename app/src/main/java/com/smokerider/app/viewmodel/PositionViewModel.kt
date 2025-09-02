package com.smokerider.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.smokerider.app.data.model.Position
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PositionViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val posRef = db.collection("positions")

    private val _position = MutableStateFlow<Position?>(null)
    val position: StateFlow<Position?> = _position

    fun loadPosition(uid: String) {
        viewModelScope.launch {
            posRef.document(uid).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        _position.value = doc.toObject(Position::class.java)
                    } else {
                        // Documento non esiste → cliente non ha ancora salvato posizione
                        _position.value = null
                    }
                }
                .addOnFailureListener {
                    _position.value = null // fallback in caso di errore di rete
                }
        }
    }

    fun saveOrUpdatePosition(uid: String, city: String, street: String, latitude: Double, longitude: Double) {
        viewModelScope.launch {
            val docRef = posRef.document(uid) // 🔹 useremo l’uid come documentId
            val newPos = Position(
                id = docRef.id,   // 🔹 salviamo anche l’id del documento
                uid = uid,
                city = city,
                street = street,  // 🔹 street deve includere anche numero civico
                latitude = latitude,
                longitude = longitude,
                timestamp = System.currentTimeMillis()
            )
            docRef.set(newPos)
        }
    }
}
