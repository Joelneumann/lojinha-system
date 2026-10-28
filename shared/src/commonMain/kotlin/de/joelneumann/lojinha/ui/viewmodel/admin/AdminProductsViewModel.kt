package de.joelneumann.lojinha.ui.viewmodel.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.joelneumann.lojinha.domain.model.PagedResult
import de.joelneumann.lojinha.domain.model.Product
import de.joelneumann.lojinha.domain.model.ProductHistoryEntry
import de.joelneumann.lojinha.domain.model.SystemSettings
import de.joelneumann.lojinha.domain.model.UnitType
import de.joelneumann.lojinha.domain.repository.ProductRepository
import de.joelneumann.lojinha.domain.repository.SettingsRepository
import de.joelneumann.lojinha.util.AppLogger
import de.joelneumann.lojinha.ui.components.admin.products.ProductSortOption
import de.joelneumann.lojinha.ui.i18n.I18n
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

    private val _isSavingProduct = MutableStateFlow(false)
    val isSavingProduct: StateFlow<Boolean> = _isSavingProduct.asStateFlow()

    private val _productErrorMessage = MutableStateFlow<String?>(null)
    val productErrorMessage: StateFlow<String?> = _productErrorMessage.asStateFlow()

    private val _historyProduct = MutableStateFlow<Product?>(null)
    val historyProduct: StateFlow<Product?> = _historyProduct.asStateFlow()

    private val _historyResult = MutableStateFlow<PagedResult<ProductHistoryEntry>?>(null)
    val historyResult: StateFlow<PagedResult<ProductHistoryEntry>?> = _historyResult.asStateFlow()

    private val _isLoadingHistory = MutableStateFlow(false)
    val isLoadingHistory: StateFlow<Boolean> = _isLoadingHistory.asStateFlow()

    private val _historyPage = MutableStateFlow(0)
    val historyPage: StateFlow<Int> = _historyPage.asStateFlow()

    private val _historyPageSize = MutableStateFlow(25)
    val historyPageSize: StateFlow<Int> = _historyPageSize.asStateFlow()

    private var productsJob: Job? = null
    private var settingsJob: Job? = null
    private var historyJob: Job? = null

    init {
        loadData()
    }

    fun loadData() {
        productsJob?.cancel()
        productsJob = viewModelScope.launch {
            productRepository.getProductsFlow().collect { list ->
                val sorted = list.sortedByAccentInsensitive { p -> p.name }
                _products.value = sorted
                _historyProduct.value?.let { currentHist ->
                    sorted.firstOrNull { p -> p.id == currentHist.id }?.let { updated ->
                        _historyProduct.value = updated
                    }
                }
            }
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

    fun clearProductError() {
        _productErrorMessage.value = null
    }

    fun openNewProductModal() {
        _editProduct.value = Product(
            id = "",
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
        if (_isSavingProduct.value) return
        viewModelScope.launch {
            _isSavingProduct.value = true
            try {
                val isExisting = product.id.isNotBlank() && _products.value.any { it.id == product.id }
                if (isExisting) {
                    val original = _products.value.find { it.id == product.id } ?: _editProduct.value
                    val stockDelta = if (original != null) product.stockQuantity - original.stockQuantity else 0L
                    productRepository.saveProduct(product)
                    if (stockDelta != 0L) {
                        productRepository.updateStock(product.id, stockDelta)
                    }
                } else {
                    val newProduct = if (product.id.isBlank()) product.copy(id = generateUuid()) else product
                    productRepository.saveProduct(newProduct)
                }
                refreshProducts()
                closeProductModal()
            } catch (e: Exception) {
                AppLogger.error("AdminProductsViewModel", "saveProduct error: ${e.message}", e)
                _productErrorMessage.value = e.message ?: I18n.get().errFailedToSaveProduct
            } finally {
                _isSavingProduct.value = false
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
                _productErrorMessage.value = e.message ?: I18n.get().errFailedToUpdateProductStatus
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
                _productErrorMessage.value = e.message ?: I18n.get().errFailedToDeleteProduct
            }
        }
    }

    fun adjustProductStock(productId: String, deltaQuantity: Long, newBasePrice: Long? = null) {
        viewModelScope.launch {
            try {
                val prod = _products.value.firstOrNull { it.id == productId } ?: return@launch
                val newStock = (prod.stockQuantity + deltaQuantity).coerceAtLeast(0L)
                val actualDelta = newStock - prod.stockQuantity
                if (actualDelta != 0L) {
                    productRepository.updateStock(productId, actualDelta)
                }
                if (newBasePrice != null && newBasePrice != prod.basePrice) {
                    productRepository.saveProduct(prod.copy(basePrice = newBasePrice))
                }
                refreshProducts()
                val updatedProd = _products.value.firstOrNull { it.id == productId }
                if (_historyProduct.value?.id == productId) {
                    if (updatedProd != null) {
                        _historyProduct.value = updatedProd
                    }
                    loadProductHistory()
                }
            } catch (e: Exception) {
                AppLogger.error("AdminProductsViewModel", "adjustProductStock error: ${e.message}", e)
                _productErrorMessage.value = e.message ?: I18n.get().errFailedToAdjustStock
            }
        }
    }

    fun openProductHistoryModal(product: Product) {
        _historyProduct.value = product
        _historyPage.value = 0
        loadProductHistory()
    }

    fun closeProductHistoryModal() {
        historyJob?.cancel()
        historyJob = null
        _historyProduct.value = null
        _historyResult.value = null
        _historyPage.value = 0
    }

    fun setHistoryPage(page: Int) {
        _historyPage.value = page
        loadProductHistory()
    }

    fun setHistoryPageSize(pageSize: Int) {
        _historyPageSize.value = pageSize
        _historyPage.value = 0
        loadProductHistory()
    }

    fun loadProductHistory() {
        val prod = _historyProduct.value ?: return
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            _isLoadingHistory.value = true
            try {
                val result = productRepository.getProductHistory(
                    productId = prod.id,
                    page = _historyPage.value,
                    pageSize = _historyPageSize.value
                )
                _historyResult.value = result
            } catch (e: Exception) {
                AppLogger.error("AdminProductsViewModel", "loadProductHistory error: ${e.message}", e)
                _productErrorMessage.value = e.message ?: I18n.get().errFailedToLoadProductHistory
            } finally {
                _isLoadingHistory.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        productsJob?.cancel()
        settingsJob?.cancel()
        historyJob?.cancel()
        _products.value = emptyList()
        _searchQuery.value = ""
    }
}
