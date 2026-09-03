package de.joelneumann.lojinha.ui.viewmodel.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.joelneumann.lojinha.domain.model.Product
import de.joelneumann.lojinha.domain.model.SystemSettings
import de.joelneumann.lojinha.domain.model.UnitType
import de.joelneumann.lojinha.domain.repository.ProductRepository
import de.joelneumann.lojinha.domain.repository.SettingsRepository
import de.joelneumann.lojinha.ui.components.admin.products.ProductSortOption
import de.joelneumann.lojinha.ui.utils.generateUuid
import de.joelneumann.lojinha.ui.utils.sortedByAccentInsensitive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AdminProductsViewModel(
    private val productRepository: ProductRepository,
    private val settingsRepository: SettingsRepository
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

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            productRepository.getProductsFlow().collect { _products.value = it.sortedByAccentInsensitive { p -> p.name } }
        }
        viewModelScope.launch {
            settingsRepository.getSettingsFlow().collect { _settings.value = it }
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
            productRepository.saveProduct(product)
            refreshProducts()
            closeProductModal()
        }
    }

    fun toggleProductActive(product: Product) {
        viewModelScope.launch {
            productRepository.saveProduct(product.copy(isActive = !product.isActive))
            refreshProducts()
        }
    }

    fun deleteProduct(productId: String) {
        viewModelScope.launch {
            productRepository.hardDeleteProduct(productId)
            refreshProducts()
        }
    }

    fun adjustProductStock(productId: String, deltaQuantity: Long) {
        viewModelScope.launch {
            val prod = _products.value.firstOrNull { it.id == productId } ?: return@launch
            val newStock = (prod.stockQuantity + deltaQuantity).coerceAtLeast(0L)
            productRepository.saveProduct(prod.copy(stockQuantity = newStock))
            refreshProducts()
        }
    }

    fun updateGlobalMarkup(markupPercent: Double) {
        viewModelScope.launch {
            val current = _settings.value
            settingsRepository.updateSettings(current.copy(globalMarkupPercent = markupPercent))
            _settings.value = settingsRepository.getSettings()
        }
    }

    override fun onCleared() {
        super.onCleared()
        _products.value = emptyList()
        _searchQuery.value = ""
    }
}
