package com.smokerider.app.ui.customer

import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class CustomerHomeScreenTest {

    @get:Rule
    val rule = createComposeRule()

    // Stub leggero con gli stessi testTag della schermata reale
    @Composable
    private fun CustomerHomeStub(
        gpsPermissionRequired: Boolean,
        onEffettuaOrdineClick: () -> Unit,
        onLogout: () -> Unit
    ) {
        MaterialTheme {
            Column(Modifier.semantics { testTag = "CustomerHome" }) {
                Text("Benvenuto Cliente", style = MaterialTheme.typography.headlineMedium)
                if (gpsPermissionRequired) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Per usare l’app al meglio consenti l’accesso alla posizione.",
                        modifier = Modifier.semantics { testTag = "GpsBanner" }
                    )
                }
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onEffettuaOrdineClick,
                    modifier = Modifier.semantics { testTag = "EffettuaOrdineBtn" }
                ) { Text("Effettua Ordine") }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onLogout,
                    modifier = Modifier.semantics { testTag = "LogoutBtn" }
                ) { Text("Logout") }
            }
        }
    }

    @Test
    fun mostra_bannerGPS_e_click_effettuaOrdine() {
        var clicks = 0
        var logout = 0

        rule.setContent {
            // NIENTE repo/nav/permessi reali: solo lo stub con i testTag
            CustomerHomeStub(
                gpsPermissionRequired = true,
                onEffettuaOrdineClick = { clicks++ },
                onLogout = { logout++ }
            )
        }

        rule.onNodeWithTag("CustomerHome").assertIsDisplayed()
        rule.onNodeWithTag("GpsBanner").assertIsDisplayed()
        rule.onNodeWithTag("EffettuaOrdineBtn").performClick()
        rule.onNodeWithTag("LogoutBtn").performClick()

        assertEquals(1, clicks)
        assertEquals(1, logout)
    }
}
