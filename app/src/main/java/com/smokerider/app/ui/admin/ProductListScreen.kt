package com.smokerider.app.ui.admin

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smokerider.app.data.model.Product
import com.smokerider.app.viewmodel.ProductViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductListScreen(
    productViewModel: ProductViewModel = viewModel()
) {
    val context = LocalContext.current
    val products by productViewModel.products.collectAsState(initial = emptyList())

    var selectedCategory by remember { mutableStateOf("Tutte") }
    val categories = listOf("Tutte", "Sigarette", "E-Cig", "IQOS")

    var searchQuery by remember { mutableStateOf("") }

    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    // filtro dinamico
    val filteredProducts = products.filter {
        (selectedCategory == "Tutte" || it.category.equals(selectedCategory, ignoreCase = true)) &&
                it.name.contains(searchQuery, ignoreCase = true)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Gestione Prodotti", style = MaterialTheme.typography.headlineMedium)

        Spacer(Modifier.height(16.dp))

        // 🔽 Filtro categorie
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedCategory,
                onValueChange = {},
                readOnly = true,
                label = { Text("Categoria") },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                categories.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            selectedCategory = option
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // 🔎 search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Cerca prodotto") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
        )

        Spacer(Modifier.height(16.dp))

        LazyColumn {
            items(filteredProducts) { product ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable {
                            selectedProduct = product
                            showDialog = true
                        }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(product.name, style = MaterialTheme.typography.titleMedium)
                        Text("Prezzo: €${product.price}", style = MaterialTheme.typography.bodyMedium)
                        Text("Categoria: ${product.category}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    if (showDialog && selectedProduct != null) {
        ProductDialog(
            product = selectedProduct!!,
            onDismiss = { showDialog = false },
            onDelete = {
                productViewModel.deleteProduct(selectedProduct!!.id)
                Toast.makeText(context, "Prodotto eliminato", Toast.LENGTH_SHORT).show()
                showDialog = false
            },
            onUpdate = { newName, newPrice ->
                productViewModel.updateProduct(selectedProduct!!.id, newName, newPrice)
                Toast.makeText(context, "Prodotto modificato", Toast.LENGTH_SHORT).show()
                showDialog = false
            }
        )
    }
}

@Composable
fun ProductDialog(
    product: Product,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onUpdate: (String, Double) -> Unit
) {
    var newName by remember { mutableStateOf(product.name) }
    var newPrice by remember { mutableStateOf(product.price.toString()) }

    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text("Dettagli prodotto") },
        text = {
            Column {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Nome prodotto") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newPrice,
                    onValueChange = { newPrice = it },
                    label = { Text("Prezzo (€)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text("Categoria: ${product.category}")
            }
        },
        confirmButton = {
            Button(onClick = {
                val priceValue = newPrice.toDoubleOrNull()
                if (newName.isNotBlank() && priceValue != null) {
                    onUpdate(newName, priceValue)
                }
            }) {
                Text("Modifica")
            }
        },
        dismissButton = {
            Row {
                Button(onClick = { onDelete() }) {
                    Text("Elimina")
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onDismiss() }) {
                    Text("Chiudi")
                }
            }
        }
    )
}
