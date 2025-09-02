package com.smokerider.app.ui

import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

/**
 * NavHost minimale per i test:
 * - "customer/home" con bottone (testTag: EffettuaOrdineBtn)
 * - "attesaOrdine/{orderId}" che mostra il testo Attesa Ordine (testTag: AttesaOrdineScreen)
 */
@Composable
fun TestAppNavHost(navController: NavHostController) {
    MaterialTheme {
        NavHost(navController = navController, startDestination = "customer/home") {
            composable("customer/home") {
                Button(
                    onClick = { navController.navigate("attesaOrdine/order-123") },
                    modifier = Modifier.semantics { testTag = "EffettuaOrdineBtn" }
                ) { Text("Effettua Ordine") }
            }
            composable("attesaOrdine/{orderId}") {
                Text(
                    "Attesa Ordine",
                    modifier = Modifier.semantics { testTag = "AttesaOrdineScreen" }
                )
            }
        }
    }
}
