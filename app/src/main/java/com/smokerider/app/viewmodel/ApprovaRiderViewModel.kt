// File: ApprovaRiderViewModel.kt
package com.smokerider.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.smokerider.app.data.model.User
import com.smokerider.app.data.model.Position
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class RiderWithPosition(
    val user: User,
    val position: Position?
)

class ApprovaRiderViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()

    private val _riders = MutableStateFlow<List<RiderWithPosition>>(emptyList())
    val riders: StateFlow<List<RiderWithPosition>> = _riders

    fun loadPendingRiders() {
        viewModelScope.launch {
            val snapshot = db.collection("users")
                .whereEqualTo("role", "rider")
                .whereEqualTo("active", false)
                .get().await()

            val ridersList = mutableListOf<RiderWithPosition>()
            for (doc in snapshot.documents) {
                val user = doc.toObject(User::class.java)?.copy(uid = doc.id)
                if (user != null) {
                    val posSnap = db.collection("positions")
                        .whereEqualTo("uid", user.uid)
                        .get().await()
                        .documents
                        .firstOrNull()

                    val pos = posSnap?.toObject(Position::class.java)?.copy(id = posSnap.id)
                    ridersList.add(RiderWithPosition(user, pos))
                }
            }
            _riders.value = ridersList
        }
    }

    fun approveRider(uid: String) {
        viewModelScope.launch {
            db.collection("users").document(uid)
                .update("active", true).await()
            loadPendingRiders()
        }
    }

    fun rejectRider(rider: RiderWithPosition) {
        viewModelScope.launch {
            db.collection("users").document(rider.user.uid).delete().await()
            rider.position?.let {
                db.collection("positions").document(it.id).delete().await()
            }
            loadPendingRiders()
        }
    }
}
