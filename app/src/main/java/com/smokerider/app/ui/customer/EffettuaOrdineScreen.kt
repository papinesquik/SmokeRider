package com.smokerider.app.ui.customer

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.smokerider.app.data.model.Product
import com.smokerider.app.viewmodel.ProductViewModel
import com.smokerider.app.viewmodel.OrderViewModel
import androidx.compose.ui.Alignment


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EffettuaOrdineScreen(
    navController: NavController,
    orderViewModel: OrderViewModel,   // viene passato da MainActivity
    productViewModel: ProductViewModel = viewModel()
) {
    val context = LocalContext.current

    // Prodotti disponibili
    val products by productViewModel.products.collectAsState(initial = emptyList())

    // Stato carrello condiviso
    val ordine by orderViewModel.items.collectAsState()
    val totale by orderViewModel.total.collectAsState(initial = 0.0)

    // Filtri
    var selectedCategory by remember { mutableStateOf("Tutte") }
    val categories = listOf("Tutte", "Sigarette", "E-Cig", "IQOS")

    var searchQuery by remember { mutableStateOf("") }

    val filteredProducts = products.filter {
        (selectedCategory == "Tutte" || it.category == selectedCategory) &&
                it.name.contains(searchQuery, ignoreCase = true)
    }

    fun euro(v: Double) = "€${"%.2f".format(v)}"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Effettua Ordine", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        // Filtro categorie
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

        // Barra ricerca
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Cerca prodotto") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
        )

        Spacer(Modifier.height(16.dp))

        // Lista prodotti
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredProducts, key = { it.id }) { product ->
                // Quantità da aggiungere per questo prodotto (selezione veloce)
                var quantity by remember(product.id) { mutableStateOf(1) }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(product.name, style = MaterialTheme.typography.titleMedium)
                        Text(euro(product.price), style = MaterialTheme.typography.bodyMedium)

                        Spacer(Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { if (quantity > 1) quantity-- }) {
                                    Icon(Icons.Default.Remove, contentDescription = "Diminuisci")
                                }
                                Text(quantity.toString())
                                IconButton(onClick = { quantity++ }) {
                                    Icon(Icons.Default.Add, contentDescription = "Aumenta")
                                }
                            }

                            Button(onClick = {
                                orderViewModel.addItem(product, quantity)
                                Toast.makeText(
                                    context,
                                    "${product.name} aggiunto all'ordine",
                                    Toast.LENGTH_SHORT
                                ).show()
                                quantity = 1 // reset selezione locale
                            }) {
                                Text("Aggiungi")
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Riepilogo ordine (modificabile)
        if (ordine.isNotEmpty()) {
            Text("Riepilogo ordine:", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            // Lista articoli nel carrello con + / − / elimina
            ordine.forEach { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.bodyLarge)
                            Text("${euro(item.price)} × ${item.quantity} = ${euro(item.price * item.quantity)}")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { orderViewModel.decrement(item.productId) }) {
                                Icon(Icons.Default.Remove, contentDescription = "Diminuisci quantità")
                            }
                            Text(item.quantity.toString())
                            IconButton(onClick = { orderViewModel.increment(item.productId) }) {
                                Icon(Icons.Default.Add, contentDescription = "Aumenta quantità")
                            }
                            IconButton(onClick = { orderViewModel.removeItem(item.productId) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Rimuovi")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("Totale: ${euro(totale)}", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    if (ordine.isNotEmpty()) {
                        navController.navigate("customer/position")
                    } else {
                        Toast.makeText(context, "Aggiungi almeno un prodotto", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Prosegui")
            }
        } else {
            // Carrello vuoto
            Text("Il carrello è vuoto.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
