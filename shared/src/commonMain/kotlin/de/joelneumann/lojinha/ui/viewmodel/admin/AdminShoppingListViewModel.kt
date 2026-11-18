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
import de.joelneumann.lojinha.ui.utils.containsIgnoreAccents
import de.joelneumann.lojinha.ui.utils.currentTimeMillis
import de.joelneumann.lojinha.ui.utils.filterAndRankProducts
import de.joelneumann.lojinha.ui.utils.generateUuid
import de.joelneumann.lojinha.ui.utils.sortedByAccentInsensitive
import de.joelneumann.lojinha.util.AppLogger
import kotlin.math.round
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

    private val _productsById = MutableStateFlow<Map<String, Product>>(emptyMap())
    val productsById: StateFlow<Map<String, Product>> = _productsById.asStateFlow()

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
                AppLogger.error(TAG, "Failed to load shopping list: ${e.message}", e)
                _errorMessage.value = e.message
            }
        }

        productsJob?.cancel()
        productsJob = viewModelScope.launch {
            try {
                productRepository.getProductsFlow().collect { list ->
                    _productsById.value = list.associateBy { it.id }
                    _products.value = list.filter { it.isActive }.sortedByAccentInsensitive { it.name }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.error(TAG, "Failed to load products: ${e.message}", e)
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun getMatchingProducts(query: String): List<Product> {
        return _products.value.filterAndRankProducts(query).take(8)
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

    fun requestAddCustomItem(name: String, forceCustom: Boolean = true) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        val matchedProduct = if (forceCustom) null else _products.value.find { it.name.equals(trimmed, ignoreCase = true) }
        val existing = if (matchedProduct != null) {
            _items.value.find { it.productId == matchedProduct.id }
                ?: _items.value.find { it.name.equals(matchedProduct.name, ignoreCase = true) }
        } else {
            _items.value.find { it.productId == null && it.name.equals(trimmed, ignoreCase = true) }
        }
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
                        productId = existingItem.productId ?: product?.id,
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

                _statusMessage.value = strings.shoppingListAddedToast(cleanName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.error(TAG, "Failed to add item: ${e.message}", e)
                _errorMessage.value = e.message
                return@launch
            }

            if (removeNote && sourceNoteId != null) {
                try {
                    adminNoteRepository?.deleteNote(sourceNoteId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.warn(TAG, "Added '$cleanName', but failed to delete source note $sourceNoteId: ${e.message}", e)
                    _errorMessage.value = e.message
                }
            }
        }
        return true
    }

    fun updateItemInPlace(id: String, newName: String, newQuantity: String, confirm: Boolean = false) {
        val current = _items.value.find { it.id == id } ?: return
        val unitType = current.productId?.let { _productsById.value[it]?.unitType }
        val cleanName = newName.trim().ifBlank { current.name }
        val normalizedNewQty = normalizeInlineQuantity(newQuantity, unitType) ?: current.quantity
        val normalizedCurrentQty = normalizeInlineQuantity(current.quantity, unitType) ?: current.quantity

        val qtyTextActuallyChanged = normalizedNewQty != normalizedCurrentQty
        val nameChanged = cleanName != current.name
        val contentChanged = nameChanged || qtyTextActuallyChanged
        val shouldConfirm = confirm || contentChanged
        if (!contentChanged && (!shouldConfirm || current.isConfirmedForCurrentExport)) {
            return
        }

        val finalQty = if (qtyTextActuallyChanged) normalizedNewQty else current.quantity
        val updated = current.copy(
            name = cleanName,
            quantity = finalQty,
            isConfirmedForCurrentExport = current.isConfirmedForCurrentExport || shouldConfirm
        )
        val previousItems = _items.value
        _items.value = _items.value.map { if (it.id == id) updated else it }

        runOptimisticMutation("update item", previousItems) {
            shoppingListRepository.updateItem(updated)
        }
    }

    fun confirmItem(id: String) {
        val current = _items.value.find { it.id == id } ?: return
        if (current.isReviewedForExport) return

        val previousItems = _items.value
        val updated = current.copy(isConfirmedForCurrentExport = true)
        _items.value = _items.value.map { if (it.id == id) updated else it }

        runOptimisticMutation("confirm item", previousItems) {
            shoppingListRepository.confirmItem(id)
        }
    }

    fun unconfirmItem(id: String) {
        val current = _items.value.find { it.id == id } ?: return
        if (current.isNewlyAdded || current.isPendingReview) return

        val previousItems = _items.value
        val updated = current.copy(isConfirmedForCurrentExport = false)
        _items.value = _items.value.map { if (it.id == id) updated else it }

        runOptimisticMutation("unconfirm item", previousItems) {
            shoppingListRepository.unconfirmItem(id)
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.error(TAG, "Failed to persist order: ${e.message}", e)
                _errorMessage.value = e.message
                loadData()
            }
        }
    }

    fun deleteItem(id: String) {
        val previousItems = _items.value
        _items.value = _items.value.filter { it.id != id }
        runOptimisticMutation("delete item", previousItems) {
            shoppingListRepository.deleteItem(id)
        }
    }

    fun clearAllItems() {
        val previousItems = _items.value
        _items.value = emptyList()
        runOptimisticMutation("clear items", previousItems) {
            shoppingListRepository.clearAllItems()
        }
    }

    private fun runOptimisticMutation(
        actionName: String,
        previousItems: List<ShoppingListItem>,
        block: suspend () -> Unit
    ) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.error(TAG, "Failed to $actionName: ${e.message}", e)
                _items.value = previousItems
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

    fun reportError(message: String) {
        _errorMessage.value = message
    }

    companion object {
        private const val TAG = "AdminShoppingListViewModel"

        fun isAllowedQuantityText(text: String, unitType: UnitType?): Boolean {
            return when (unitType) {
                UnitType.PIECE -> text.all { it.isDigit() }
                UnitType.WEIGHT -> {
                    var sepCount = 0
                    text.all { ch ->
                        if (ch.isDigit()) {
                            true
                        } else if (ch == '.' || ch == ',') {
                            sepCount++
                            sepCount <= 1
                        } else {
                            false
                        }
                    }
                }
                null -> true
            }
        }

        fun extractEditableNumericPart(rawQuantity: String, unitType: UnitType?): String {
            val trimmed = rawQuantity.trim()
            return when (unitType) {
                UnitType.PIECE -> trimmed.takeWhile { it.isDigit() }.ifBlank { trimmed }
                UnitType.WEIGHT -> {
                    val lower = trimmed.lowercase()
                    when {
                        lower.endsWith("kg") -> trimmed.dropLast(2).trim()
                        lower.endsWith("g") -> {
                            val grams = lower.dropLast(1).trim().replace(',', '.').toDoubleOrNull()
                            if (grams != null) {
                                (grams / 1000.0).toString().trimEnd('0').trimEnd('.').replace('.', ',')
                            } else {
                                trimmed
                            }
                        }
                        else -> trimmed
                    }
                }
                null -> trimmed
            }
        }

        fun formatQuantityInput(rawInput: String, product: Product?): String? {
            return normalizeInlineQuantity(rawInput, product?.unitType)
        }

        fun normalizeInlineQuantity(rawInput: String, unitType: UnitType?): String? {
            val trimmed = rawInput.trim()
            if (trimmed.isBlank()) return null

            return when (unitType) {
                UnitType.PIECE -> {
                    val digits = trimmed.takeWhile { it.isDigit() }
                    val qty = digits.toLongOrNull() ?: return null
                    if (qty <= 0L) return null
                    qty.toString()
                }
                UnitType.WEIGHT -> {
                    val lower = trimmed.lowercase()
                    val kgValue = when {
                        lower.endsWith("kg") -> lower.dropLast(2).trim().replace(',', '.').toDoubleOrNull()
                        lower.endsWith("g") -> lower.dropLast(1).trim().replace(',', '.').toDoubleOrNull()?.div(1000.0)
                        else -> trimmed.replace(',', '.').toDoubleOrNull()
                    } ?: return null
                    if (!kgValue.isFinite() || kgValue <= 0.0) return null
                    val millis = round(kgValue * 1000.0).toLong()
                    if (millis <= 0L) return null
                    val whole = millis / 1000L
                    val rem = millis % 1000L
                    if (rem == 0L) {
                        whole.toString()
                    } else {
                        "$whole,${rem.toString().padStart(3, '0').trimEnd('0')}"
                    }
                }
                null -> trimmed
            }
        }
    }
}
