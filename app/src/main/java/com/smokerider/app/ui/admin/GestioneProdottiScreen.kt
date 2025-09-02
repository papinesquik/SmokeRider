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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smokerider.app.data.model.Product
import com.smokerider.app.viewmodel.ProductViewModel

@Composable
fun GestioneProdottiScreen(
    productViewModel: ProductViewModel = viewModel()
) {
    val context = LocalContext.current
    val products by productViewModel.products.collectAsState() // lista aggiornata dal VM
    val success by productViewModel.success.collectAsState()
    val error by productViewModel.error.collectAsState()

    var search by remember { mutableStateOf(TextFieldValue("")) }
    var selectedCategory by remember { mutableStateOf("Tutte") }
    var selectedProduct by remember { mutableStateOf<Product?>(null) }

    // Toast feedback
    LaunchedEffect(success, error) {
        success?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            productViewModel.clearMessages()
        }
        error?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            productViewModel.clearMessages()
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Gestione Prodotti", style = MaterialTheme.typography.headlineMedium)

        Spacer(Modifier.height(16.dp))

        // 🔍 Barra di ricerca
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text("Cerca prodotto") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
        )

        Spacer(Modifier.height(8.dp))

        // 📂 Filtro categoria
        val categories = listOf("Tutte", "Sigarette", "E-Cig", "IQOS")
        var expanded by remember { mutableStateOf(false) }

        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(selectedCategory)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                categories.forEach { cat ->
                    DropdownMenuItem(
                        text = { Text(cat) },
                        onClick = {
                            selectedCategory = cat
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 📜 Lista prodotti
        LazyColumn {
            val filtered = products.filter {
                (selectedCategory == "Tutte" || it.category == selectedCategory) &&
                        (search.text.isBlank() || it.name.contains(search.text, ignoreCase = true))
            }
            items(filtered) { product ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { selectedProduct = product }
                ) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${product.name} (${product.category})")
                        Text("${product.price} €")
                    }
                }
            }
        }
    }

    // 🔔 Dialog Dettagli/Modifica/Elimina
    selectedProduct?.let { product ->
        ProdottoDialog(
            product = product,
            onDismiss = { selectedProduct = null },
            onUpdate = { name, price ->
                productViewModel.updateProduct(product.id, name, price)
                selectedProduct = null
            },
            onDelete = {
                productViewModel.deleteProduct(product.id)
                selectedProduct = null
            }
        )
    }
}

@Composable
fun ProdottoDialog(
    product: Product,
    onDismiss: () -> Unit,
    onUpdate: (String, Double) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(product.name) }
    var price by remember { mutableStateOf(product.price.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gestisci Prodotto") },
        text = {
            Column {
                Text("Categoria: ${product.category}")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Prezzo (€)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                price.toDoubleOrNull()?.let { onUpdate(name, it) }
            }) {
                Text("Modifica")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Elimina") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDismiss) { Text("Chiudi") }
            }
        }
    )
}
