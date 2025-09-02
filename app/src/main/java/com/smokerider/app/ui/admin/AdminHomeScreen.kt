package com.smokerider.app.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

@Composable
fun AdminHomeScreen(
    onApprovaRiderClick: () -> Unit,
    onAggiungiProdottoClick: () -> Unit,
    onGestioneProdottiClick: () -> Unit,
    onLogout: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var loading by remember { mutableStateOf(false) }
    var askConfirm by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Benvenuto Admin", style = MaterialTheme.typography.headlineSmall)

            Spacer(Modifier.height(24.dp))

            Button(onClick = onApprovaRiderClick, enabled = !loading) { Text("Approva Rider") }
            Spacer(Modifier.height(16.dp))

            Button(onClick = onAggiungiProdottoClick, enabled = !loading) { Text("Aggiungi Prodotto") }
            Spacer(Modifier.height(16.dp))

            Button(onClick = onGestioneProdottiClick, enabled = !loading) { Text("Gestione Prodotti") }
            Spacer(Modifier.height(24.dp))

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text("Manutenzione ordini", style = MaterialTheme.typography.titleMedium)

            Button(
                onClick = { askConfirm = true },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Text(if (loading) "Pulizia in corso…" else "Elimina ordini ANNULLATI/SCADUTI")
            }

            if (loading) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.85f))
            }

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = onLogout,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                enabled = !loading
            ) {
                Text("Logout")
            }
        }

        if (askConfirm) {
            AlertDialog(
                onDismissRequest = { askConfirm = false },
                title = { Text("Confermi la pulizia?") },
                text = {
                    Text("Verranno eliminati TUTTI gli ordini con stato \"cancelled\" o \"expired\". Operazione irreversibile.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        askConfirm = false
                        scope.launch {
                            loading = true
                            try {
                                val deleted = purgeOrdersByStatuses(listOf("cancelled", "expired"))
                                snackbarHostState.showSnackbar("Eliminati $deleted ordini")
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar("Errore: ${e.message ?: "sconosciuto"}")
                            } finally {
                                loading = false
                            }
                        }
                    }) { Text("Conferma") }
                },
                dismissButton = {
                    TextButton(onClick = { askConfirm = false }) { Text("Annulla") }
                }
            )
        }
    }
}

/** Cancella TUTTI i documenti in 'orders' con uno status tra quelli indicati.
 *  Ritorna il numero totale di documenti eliminati. */
suspend fun purgeOrdersByStatuses(statuses: List<String>): Int {
    val db = FirebaseFirestore.getInstance()
    var totalDeleted = 0
    for (status in statuses) {
        val snap = db.collection("orders")
            .whereEqualTo("status", status)
            .get()
            .await()

        val docs = snap.documents
        if (docs.isEmpty()) continue

        var batch = db.batch()
        var ops = 0
        for (doc in docs) {
            batch.delete(doc.reference)
            ops++
            if (ops == 450) { // sicurezza sotto il limite 500 op/batch
                batch.commit().await()
                totalDeleted += ops
                batch = db.batch()
                ops = 0
            }
        }
        if (ops > 0) {
            batch.commit().await()
            totalDeleted += ops
        }
    }
    return totalDeleted
}
