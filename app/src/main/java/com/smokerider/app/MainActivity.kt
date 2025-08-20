package com.smokerider.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import com.smokerider.app.ui.auth.AuthScreen
import com.smokerider.app.ui.theme.SmokeRiderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmokeRiderTheme {
                // Mostra la schermata di autenticazione
                AuthScreen()
            }
        }
    }
}
