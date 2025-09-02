package com.smokerider.app.ui.customer

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.smokerider.app.data.model.Product
import com.smokerider.app.viewmodel.ProductViewModel
import com.smokerider.app.viewmodel.OrderViewModel
import androidx.compose.foundation.shape.RoundedCornerShape
import com.smokerider.app.ui.theme.screenInsets   // ⬅️ import utility

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

    // ---- LAYOUT GENERALE CON CARRELLO ANCORATO IN BASSO ----
    Box(
        modifier = Modifier
            .fillMaxSize()
            .screenInsets(includeTop = true, includeBottom = true, extraTop = 16.dp) // ⬅️ evita overlap + respiro
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Text("Effettua Ordine", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))

            // Filtro categorie
            var expandedCat by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expandedCat,
                onExpandedChange = { expandedCat = !expandedCat }
            ) {
                OutlinedTextField(
                    value = selectedCategory,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Categoria") },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedCat) }
                )
                ExposedDropdownMenu(expanded = expandedCat, onDismissRequest = { expandedCat = false }) {
                    categories.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                selectedCategory = option
                                expandedCat = false
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
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 120.dp) // spazio per la card del carrello
            ) {
                items(filteredProducts, key = { it.id }) { product ->
                    var quantity by remember(product.id) { mutableStateOf(1) }

                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(product.name, style = MaterialTheme.typography.titleMedium)
                            Text(euro(product.price), style = MaterialTheme.typography.bodyMedium)

                            Spacer(Modifier.height(8.dp))

                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { if (quantity > 1) quantity-- }) {
                                        Icon(Icons.Default.Remove, contentDescription = "Diminuisci")
                                    }
                                    Text(quantity.toString(), textAlign = TextAlign.Center)
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
                                    quantity = 1
                                }) {
                                    Text("Aggiungi")
                                }
                            }
                        }
                    }
                }
            }
        }

        // -------- CARRELLO COMPRESSO/ESTESO --------
        if (ordine.isNotEmpty()) {
            CollapsibleCart(
                totalFormatted = euro(totale),
                items = ordine.map {
                    CartUi(
                        id = it.productId,
                        name = it.name,
                        priceEach = euro(it.price),
                        qty = it.quantity,
                        lineTotal = euro(it.price * it.quantity)
                    )
                },
                onIncrease = { id -> orderViewModel.increment(id) },
                onDecrease = { id -> orderViewModel.decrement(id) },
                onRemove   = { id -> orderViewModel.removeItem(id) },
                onProceed  = { navController.navigate("customer/position") },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 0.dp, vertical = 8.dp)
            )
        }
    }
}

/* -------------------- UI Support -------------------- */

private data class CartUi(
    val id: String,
    val name: String,
    val priceEach: String,
    val qty: Int,
    val lineTotal: String
)

@Composable
private fun CollapsibleCart(
    totalFormatted: String,
    items: List<CartUi>,
    onIncrease: (String) -> Unit,
    onDecrease: (String) -> Unit,
    onRemove: (String) -> Unit,
    onProceed: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        // HEADER
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(if (expanded) "Carrello" else "Totale", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(totalFormatted, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                    contentDescription = null
                )
            }
        }

        // CONTENUTO
        AnimatedVisibility(visible = expanded) {
            Column {
                Divider()
                if (items.isEmpty()) {
                    Text(
                        "Il carrello è vuoto",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(items, key = { it.id }) { it ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(it.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "${it.priceEach} × ${it.qty} = ${it.lineTotal}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { onDecrease(it.id) }) {
                                        Icon(Icons.Default.Remove, contentDescription = "Diminuisci")
                                    }
                                    Text("${it.qty}", modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
                                    IconButton(onClick = { onIncrease(it.id) }) {
                                        Icon(Icons.Default.Add, contentDescription = "Aumenta")
                                    }
                                    IconButton(onClick = { onRemove(it.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Rimuovi")
                                    }
                                }
                            }
                            Divider()
                        }
                    }

                    // Riepilogo
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Totale", style = MaterialTheme.typography.titleMedium)
                            Text(totalFormatted, style = MaterialTheme.typography.titleLarge)
                        }
                        Button(onClick = onProceed) {
                            Text("Prosegui")
                        }
                    }
                }
            }
        }
    }
}
