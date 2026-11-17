package de.joelneumann.lojinha.ui.viewmodel.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.joelneumann.lojinha.domain.model.Product
import de.joelneumann.lojinha.domain.model.ShoppingListItem
import de.joelneumann.lojinha.domain.model.UnitType
import de.joelneumann.lojinha.domain.repository.AdminNoteRepository
import de.joelneumann.lojinha.domain.repository.ProductRepository
import de.joelneumann.lojinha.domain.repository.ShoppingListRepository
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.utils.Formatting
import de.joelneumann.lojinha.ui.utils.containsIgnoreAccents
import de.joelneumann.lojinha.ui.utils.currentTimeMillis
import de.joelneumann.lojinha.ui.utils.generateUuid
import de.joelneumann.lojinha.ui.utils.sortedByAccentInsensitive
import de.joelneumann.lojinha.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ShoppingListModalRequest(
    val product: Product? = null,
    val customName: String = "",
    val sourceNoteId: String? = null,
    val existingItem: ShoppingListItem? = null
)

class AdminShoppingListViewModel(
    private val shoppingListRepository: ShoppingListRepository,
    private val productRepository: ProductRepository,
    private val adminNoteRepository: AdminNoteRepository? = null
) : ViewModel() {

    private val _items = MutableStateFlow<List<ShoppingListItem>>(emptyList())
    val items: StateFlow<List<ShoppingListItem>> = _items.asStateFlow()

    private val _products = MutableStateFlow<List<Product>>(emptyList())
    val products: StateFlow<List<Product>> = _products.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _pendingModalRequest = MutableStateFlow<ShoppingListModalRequest?>(null)
    val pendingModalRequest: StateFlow<ShoppingListModalRequest?> = _pendingModalRequest.asStateFlow()

    private val _showExportApprovalDialog = MutableStateFlow(false)
    val showExportApprovalDialog: StateFlow<Boolean> = _showExportApprovalDialog.asStateFlow()

    private val _exportResultPath = MutableStateFlow<String?>(null)
    val exportResultPath: StateFlow<String?> = _exportResultPath.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var itemsJob: Job? = null
    private var productsJob: Job? = null

    init {
        loadData()
    }

    fun loadData() {
        itemsJob?.cancel()
        itemsJob = viewModelScope.launch {
            try {
                shoppingListRepository.getItemsFlow().collect { list ->
                    _items.value = list.sortedWith(compareBy<ShoppingListItem> { it.sortOrder }.thenBy { it.createdAt })
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.error("AdminShoppingListViewModel", "Failed to load shopping list: ${e.message}", e)
                _errorMessage.value = e.message
            }
        }

        productsJob?.cancel()
        productsJob = viewModelScope.launch {
            try {
                productRepository.getProductsFlow().collect { list ->
                    _products.value = list.filter { it.isActive }.sortedByAccentInsensitive { it.name }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.error("AdminShoppingListViewModel", "Failed to load products: ${e.message}", e)
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun getMatchingProducts(query: String): List<Product> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()
        return _products.value.filter { p ->
            p.name.containsIgnoreAccents(trimmed) ||
                p.barcodes.any { it.code.contains(trimmed, ignoreCase = true) }
        }.take(8)
    }

    fun requestAddProduct(product: Product, sourceNoteId: String? = null) {
        val existing = _items.value.find { it.productId == product.id }
            ?: _items.value.find { it.name.equals(product.name, ignoreCase = true) }
        _pendingModalRequest.value = ShoppingListModalRequest(
            product = product,
            customName = product.name,
            sourceNoteId = sourceNoteId,
            existingItem = existing
        )
    }

    fun requestAddFromStockZeroNote(productName: String, noteId: String) {
        val trimmed = productName.trim()
        val matchedProduct = _products.value.find { it.name.equals(trimmed, ignoreCase = true) }
            ?: _products.value.find { it.name.containsIgnoreAccents(trimmed) && trimmed.containsIgnoreAccents(it.name) }
        if (matchedProduct != null) {
            requestAddProduct(matchedProduct, sourceNoteId = noteId)
        } else {
            val existing = _items.value.find { it.name.equals(trimmed, ignoreCase = true) }
            _pendingModalRequest.value = ShoppingListModalRequest(
                product = null,
                customName = trimmed,
                sourceNoteId = noteId,
                existingItem = existing
            )
        }
    }

    fun requestAddCustomItem(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        val existing = _items.value.find { it.name.equals(trimmed, ignoreCase = true) }
        val matchedProduct = _products.value.find { it.name.equals(trimmed, ignoreCase = true) }
        _pendingModalRequest.value = ShoppingListModalRequest(
            product = matchedProduct,
            customName = matchedProduct?.name ?: trimmed,
            sourceNoteId = null,
            existingItem = existing
        )
    }

    fun dismissModal() {
        _pendingModalRequest.value = null
    }

    fun confirmModalAdd(
        name: String,
        rawQuantityInput: String,
        product: Product?,
        sourceNoteId: String?,
        existingItem: ShoppingListItem?,
        removeNote: Boolean,
        clearSearchQuery: Boolean = false
    ): Boolean {
        val strings = I18n.get()
        val formattedQuantity = formatQuantityInput(rawQuantityInput, product)
        if (formattedQuantity == null) {
            _errorMessage.value = strings.shoppingListInvalidQuantityError
            return false
        }

        val cleanName = name.trim()
        if (cleanName.isBlank()) return false

        _pendingModalRequest.value = null
        if (clearSearchQuery) {
            _searchQuery.value = ""
        }

        viewModelScope.launch {
            try {
                val itemToSave = if (existingItem != null) {
                    existingItem.copy(
                        name = cleanName,
                        quantity = formattedQuantity,
                        isConfirmedForCurrentExport = true
                    )
                } else {
                    val nextOrder = (_items.value.maxOfOrNull { it.sortOrder } ?: -1) + 1
                    ShoppingListItem(
                        id = generateUuid(),
                        productId = product?.id,
                        name = cleanName,
                        quantity = formattedQuantity,
                        sortOrder = nextOrder,
                        createdAt = currentTimeMillis(),
                        lastExportedName = null,
                        lastExportedQuantity = null,
                        isConfirmedForCurrentExport = true
                    )
                }
                shoppingListRepository.addOrUpdateItem(itemToSave)

                if (existingItem != null) {
                    _items.value = _items.value.map { if (it.id == existingItem.id) itemToSave else it }
                } else {
                    _items.value = _items.value + itemToSave
                }

                if (removeNote && sourceNoteId != null) {
                    adminNoteRepository?.deleteNote(sourceNoteId)
                }

                _statusMessage.value = strings.shoppingListAddedToast(cleanName)
            } catch (e: Exception) {
                AppLogger.error("AdminShoppingListViewModel", "Failed to add item: ${e.message}", e)
                _errorMessage.value = e.message
            }
        }
        return true
    }

    fun updateItemInPlace(id: String, newName: String, newQuantity: String, confirm: Boolean = false) {
        val current = _items.value.find { it.id == id } ?: return
        val cleanName = newName.trim().ifBlank { current.name }
        val cleanQty = newQuantity.trim().ifBlank { current.quantity }

        val contentChanged = cleanName != current.name || cleanQty != current.quantity
        val shouldConfirm = confirm || contentChanged
        if (!contentChanged && (!shouldConfirm || current.isConfirmedForCurrentExport)) {
            return
        }

        val updated = current.copy(
            name = cleanName,
            quantity = cleanQty,
            isConfirmedForCurrentExport = current.isConfirmedForCurrentExport || shouldConfirm
        )
        _items.value = _items.value.map { if (it.id == id) updated else it }

        viewModelScope.launch {
            try {
                shoppingListRepository.updateItem(updated)
            } catch (e: Exception) {
                AppLogger.error("AdminShoppingListViewModel", "Failed to update item: ${e.message}", e)
                _errorMessage.value = e.message
            }
        }
    }

    fun confirmItem(id: String) {
        val current = _items.value.find { it.id == id } ?: return
        if (current.isReviewedForExport) return

        val updated = current.copy(isConfirmedForCurrentExport = true)
        _items.value = _items.value.map { if (it.id == id) updated else it }

        viewModelScope.launch {
            try {
                shoppingListRepository.confirmItem(id)
            } catch (e: Exception) {
                AppLogger.error("AdminShoppingListViewModel", "Failed to confirm item: ${e.message}", e)
                _errorMessage.value = e.message
            }
        }
    }

    fun moveItem(fromIndex: Int, toIndex: Int) {
        val current = _items.value.toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices || fromIndex == toIndex) return
        val moved = current.removeAt(fromIndex)
        current.add(toIndex, moved)
        val reindexed = current.mapIndexed { idx, item -> item.copy(sortOrder = idx) }
        _items.value = reindexed
    }

    fun persistCurrentOrder() {
        val snapshot = _items.value
        viewModelScope.launch {
            try {
                shoppingListRepository.updateItemsOrder(snapshot)
            } catch (e: Exception) {
                AppLogger.error("AdminShoppingListViewModel", "Failed to persist order: ${e.message}", e)
                _errorMessage.value = e.message
            }
        }
    }

    fun deleteItem(id: String) {
        _items.value = _items.value.filter { it.id != id }
        viewModelScope.launch {
            try {
                shoppingListRepository.deleteItem(id)
            } catch (e: Exception) {
                AppLogger.error("AdminShoppingListViewModel", "Failed to delete item: ${e.message}", e)
                _errorMessage.value = e.message
            }
        }
    }

    fun clearAllItems() {
        _items.value = emptyList()
        viewModelScope.launch {
            try {
                shoppingListRepository.clearAllItems()
            } catch (e: Exception) {
                AppLogger.error("AdminShoppingListViewModel", "Failed to clear items: ${e.message}", e)
                _errorMessage.value = e.message
            }
        }
    }

    fun requestExportPdf() {
        _showExportApprovalDialog.value = true
    }

    fun dismissExportApprovalDialog() {
        _showExportApprovalDialog.value = false
    }

    fun markExportedLocally() {
        _items.value = _items.value.map {
            it.copy(
                lastExportedName = it.name,
                lastExportedQuantity = it.quantity,
                isConfirmedForCurrentExport = false
            )
        }
    }

    fun setExportResultPath(path: String?) {
        _exportResultPath.value = path
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }

    companion object {
        fun formatQuantityInput(rawInput: String, product: Product?): String? {
            val trimmed = rawInput.trim()
            if (trimmed.isBlank()) return null

            if (product == null) {
                return trimmed
            }

            val strings = I18n.get()
            return when (product.unitType) {
                UnitType.PIECE -> {
                    val qty = trimmed.toLongOrNull() ?: return null
                    if (qty <= 0L) return null
                    strings.pieceUnitSuffix(qty)
                }
                UnitType.WEIGHT -> {
                    val grams = Formatting.parseWeightInputToGrams(trimmed) ?: return null
                    if (grams <= 0L) return null
                    if (grams < 1000L) {
                        "$grams g"
                    } else if (grams % 1000L == 0L) {
                        "${grams / 1000L} kg"
                    } else {
                        val kg = grams.toDouble() / 1000.0
                        val formatted = kg.toString().trimEnd('0').trimEnd('.').replace('.', ',')
                        "$formatted kg"
                    }
                }
            }
        }
    }
}
