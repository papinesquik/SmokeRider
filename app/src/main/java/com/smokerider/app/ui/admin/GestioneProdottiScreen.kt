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
import com.smokerider.app.ui.theme.screenInsets
import com.smokerider.app.viewmodel.ProductViewModel

@Composable
fun GestioneProdottiScreen(
    productViewModel: ProductViewModel = viewModel()
) {
    val context = LocalContext.current
    val products by productViewModel.products.collectAsState()
    val success by productViewModel.success.collectAsState()
    val error by productViewModel.error.collectAsState()

    var search by remember { mutableStateOf(TextFieldValue("")) }
    var selectedCategory by remember { mutableStateOf("Tutte") }
    var selectedProduct by remember { mutableStateOf<Product?>(null) }

    // feedback toast
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .screenInsets(includeTop = true, includeBottom = true, extraTop = 16.dp) // ⬅️ niente sovrapposizione, più respiro
            .padding(horizontal = 16.dp)
    ) {
        Text("Gestione Prodotti", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        // ricerca
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text("Cerca prodotto") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
        )

        Spacer(Modifier.height(8.dp))

        // filtro categoria
        val categories = listOf("Tutte", "Sigarette", "E-Cig", "IQOS")
        var expanded by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { expanded = true }) { Text(selectedCategory) }
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

        // lista prodotti
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
                    Row(
                        Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${product.name} (${product.category})")
                        Text("${product.price} €")
                    }
                }
            }
        }
    }

    // dialog
    selectedProduct?.let { product ->
        ProductDialog(
            product = product,
            onDismiss = { selectedProduct = null },
            onDelete = {
                productViewModel.deleteProduct(product.id)
                selectedProduct = null
            },
            onUpdate = { newName, newPrice ->
                productViewModel.updateProduct(product.id, newName, newPrice)
                selectedProduct = null
            }
        )
    }
}
