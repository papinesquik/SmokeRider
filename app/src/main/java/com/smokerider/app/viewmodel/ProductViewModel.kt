package com.smokerider.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smokerider.app.data.model.Product
import com.smokerider.app.data.repository.ProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProductViewModel(
    private val repository: ProductRepository = ProductRepository()
) : ViewModel() {

    private val _success = MutableStateFlow<String?>(null)
    val success = _success.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _products = MutableStateFlow<List<Product>>(emptyList())
    val products = _products.asStateFlow()

    init {
        getProducts()
    }

    fun getProducts() {
        viewModelScope.launch {
            try {
                repository.getProducts { productList ->
                    _products.value = productList
                }
            } catch (e: Exception) {
                _error.value = "Errore caricamento prodotti: ${e.message}"
            }
        }
    }

    fun addProduct(name: String, price: Double, category: String) {
        viewModelScope.launch {
            try {
                val product = Product(name = name, price = price, category = category)
                repository.addProduct(product)
                _success.value = "Prodotto aggiunto con successo ✅"
                getProducts()
            } catch (e: Exception) {
                _error.value = "Errore: ${e.message}"
            }
        }
    }

    fun updateProduct(id: String, newName: String, newPrice: Double) {
        viewModelScope.launch {
            try {
                repository.updateProduct(id, newName, newPrice)
                _success.value = "Prodotto aggiornato ✅"
                getProducts()
            } catch (e: Exception) {
                _error.value = "Errore aggiornamento: ${e.message}"
            }
        }
    }

    fun deleteProduct(id: String) {
        viewModelScope.launch {
            try {
                repository.deleteProduct(id)
                _success.value = "Prodotto eliminato ✅"
                getProducts()
            } catch (e: Exception) {
                _error.value = "Errore eliminazione: ${e.message}"
            }
        }
    }

    fun clearMessages() {
        _success.value = null
        _error.value = null
    }
}
