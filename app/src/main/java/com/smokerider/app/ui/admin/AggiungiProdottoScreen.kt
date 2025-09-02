package com.smokerider.app.ui.admin

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smokerider.app.viewmodel.ProductViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AggiungiProdottoScreen(
    productViewModel: ProductViewModel = viewModel()
) {
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Sigarette") }
    var expanded by remember { mutableStateOf(false) }

    val categories = listOf("Sigarette", "E-Cig", "IQOS")

    val context = LocalContext.current
    val success by productViewModel.success.collectAsState()
    val error by productViewModel.error.collectAsState()

    LaunchedEffect(success, error) {
        success?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            productViewModel.clearMessages()
            name = ""
            price = ""
            category = "Sigarette"
        }
        error?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            productViewModel.clearMessages()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Aggiungi Prodotto", style = MaterialTheme.typography.headlineMedium)

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nome prodotto") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = price,
            onValueChange = { price = it },
            label = { Text("Prezzo (€)") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Spacer(Modifier.height(8.dp))

        // Dropdown categorie fisse
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = category,
                onValueChange = {},
                readOnly = true,
                label = { Text("Categoria") },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                categories.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            category = option
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                val priceValue = price.toDoubleOrNull()
                if (name.isBlank() || priceValue == null || category.isBlank()) {
                    Toast.makeText(context, "Compila tutti i campi", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                productViewModel.addProduct(name, priceValue, category)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Aggiungi")
        }
    }
}
