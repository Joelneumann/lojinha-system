package de.joelneumann.lojinha.ui.viewmodel.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.joelneumann.lojinha.domain.model.Product
import de.joelneumann.lojinha.domain.model.SystemSettings
import de.joelneumann.lojinha.domain.model.UnitType
import de.joelneumann.lojinha.domain.repository.ProductRepository
import de.joelneumann.lojinha.domain.repository.SettingsRepository
import de.joelneumann.lojinha.util.AppLogger
import de.joelneumann.lojinha.ui.components.admin.products.ProductSortOption
import de.joelneumann.lojinha.ui.utils.generateUuid
import de.joelneumann.lojinha.ui.utils.sortedByAccentInsensitive
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AdminProductsViewModel(
    private val productRepository: ProductRepository,
    private val settingsRepository: SettingsRepository? = null
) : ViewModel() {

    private val _products = MutableStateFlow<List<Product>>(emptyList())
    val products: StateFlow<List<Product>> = _products.asStateFlow()

    private val _settings = MutableStateFlow(SystemSettings())
    val settings: StateFlow<SystemSettings> = _settings.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOption = MutableStateFlow(ProductSortOption.NAME_ASC)
    val sortOption: StateFlow<ProductSortOption> = _sortOption.asStateFlow()

    private val _editProduct = MutableStateFlow<Product?>(null)
    val editProduct: StateFlow<Product?> = _editProduct.asStateFlow()

    private val _showProductModal = MutableStateFlow(false)
    val showProductModal: StateFlow<Boolean> = _showProductModal.asStateFlow()

    private var productsJob: Job? = null
    private var settingsJob: Job? = null

    init {
        loadData()
    }

    fun loadData() {
        productsJob?.cancel()
        productsJob = viewModelScope.launch {
            productRepository.getProductsFlow().collect { _products.value = it.sortedByAccentInsensitive { p -> p.name } }
        }
        settingsJob?.cancel()
        settingsRepository?.let { repo ->
            settingsJob = viewModelScope.launch {
                repo.getSettingsFlow().collect { _settings.value = it }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateSortOption(option: ProductSortOption) {
        _sortOption.value = option
    }

    fun openNewProductModal() {
        _editProduct.value = Product(
            id = generateUuid(),
            name = "",
            barcodes = emptyList(),
            basePrice = 0L,
            unitType = UnitType.PIECE,
            stockQuantity = 0L
        )
        _showProductModal.value = true
    }

    fun openEditProductModal(product: Product) {
        _editProduct.value = product
        _showProductModal.value = true
    }

    fun closeProductModal() {
        _showProductModal.value = false
        _editProduct.value = null
    }

    private suspend fun refreshProducts() {
        _products.value = productRepository.getAllProducts().sortedByAccentInsensitive { p -> p.name }
    }

    fun saveProduct(product: Product) {
        viewModelScope.launch {
            try {
                val original = _editProduct.value
                val isExisting = original != null && original.id.isNotBlank() && original.id == product.id
                if (isExisting) {
                    val stockDelta = product.stockQuantity - original.stockQuantity
                    productRepository.saveProduct(product)
                    if (stockDelta != 0L) {
                        productRepository.updateStock(product.id, stockDelta)
                    }
                } else {
                    productRepository.saveProduct(product)
                }
                refreshProducts()
                closeProductModal()
            } catch (e: Exception) {
                AppLogger.error("AdminProductsViewModel", "saveProduct error: ${e.message}", e)
            }
        }
    }

    fun toggleProductActive(product: Product) {
        viewModelScope.launch {
            try {
                productRepository.saveProduct(product.copy(isActive = !product.isActive))
                refreshProducts()
            } catch (e: Exception) {
                AppLogger.error("AdminProductsViewModel", "toggleProductActive error: ${e.message}", e)
            }
        }
    }

    fun deleteProduct(productId: String) {
        viewModelScope.launch {
            try {
                productRepository.hardDeleteProduct(productId)
                refreshProducts()
            } catch (e: Exception) {
                AppLogger.error("AdminProductsViewModel", "deleteProduct error: ${e.message}", e)
            }
        }
    }

    fun adjustProductStock(productId: String, deltaQuantity: Long) {
        viewModelScope.launch {
            try {
                val prod = _products.value.firstOrNull { it.id == productId } ?: return@launch
                val newStock = (prod.stockQuantity + deltaQuantity).coerceAtLeast(0L)
                val actualDelta = newStock - prod.stockQuantity
                if (actualDelta != 0L) {
                    productRepository.updateStock(productId, actualDelta)
                }
                refreshProducts()
            } catch (e: Exception) {
                AppLogger.error("AdminProductsViewModel", "adjustProductStock error: ${e.message}", e)
            }
        }
    }




    override fun onCleared() {
        super.onCleared()
        _products.value = emptyList()
        _searchQuery.value = ""
    }
}
