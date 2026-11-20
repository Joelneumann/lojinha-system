package de.joelneumann.lojinha.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.joelneumann.lojinha.domain.model.*
import de.joelneumann.lojinha.domain.repository.CustomerVeggieRoundHistoryItem
import de.joelneumann.lojinha.domain.service.VeggieSettlementCalculator
import de.joelneumann.lojinha.ui.utils.containsIgnoreAccents
import de.joelneumann.lojinha.ui.utils.sortedByAccentInsensitive
import de.joelneumann.lojinha.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CustomerVeggieViewModel(
    private val onFetchVeggieState: suspend () -> CustomerVeggieStateResponse,
    private val onSaveOrder: suspend (roundId: String, quantitiesScaled: Map<String, Long>) -> Boolean,
    private val onFetchHistory: suspend () -> List<CustomerVeggieRoundHistoryItem>,
    initialFavoriteProductIds: Set<String> = emptySet(),
    private val onFavoritesChanged: ((Set<String>) -> Unit)? = null,
    private val onSaveFavorites: (suspend (Set<String>) -> Boolean)? = null,
    private val onLoadDraft: ((roundId: String) -> Map<String, Long>?)? = null,
    private val onSaveDraft: ((roundId: String, quantitiesScaled: Map<String, Long>) -> Unit)? = null,
    private val onClearDraft: ((roundId: String) -> Unit)? = null,
    coroutineScope: CoroutineScope? = null
) : ViewModel() {

    private val activeScope = coroutineScope ?: viewModelScope

    private val _unitTypes = MutableStateFlow<List<VeggieUnitType>>(emptyList())
    val unitTypes: StateFlow<List<VeggieUnitType>> = _unitTypes.asStateFlow()

    private val _products = MutableStateFlow<List<VeggieProduct>>(emptyList())
    val products: StateFlow<List<VeggieProduct>> = _products.asStateFlow()

    private val _inactiveOrderedProducts = MutableStateFlow<List<VeggieProduct>>(emptyList())
    val inactiveOrderedProducts: StateFlow<List<VeggieProduct>> = _inactiveOrderedProducts.asStateFlow()

    private val _activeRound = MutableStateFlow<VeggieOrderRound?>(null)
    val activeRound: StateFlow<VeggieOrderRound?> = _activeRound.asStateFlow()

    private val _draftQuantitiesScaled = MutableStateFlow<Map<String, Long>>(emptyMap())
    val draftQuantitiesScaled: StateFlow<Map<String, Long>> = _draftQuantitiesScaled.asStateFlow()

    private val _savedQuantitiesScaled = MutableStateFlow<Map<String, Long>>(emptyMap())
    val savedQuantitiesScaled: StateFlow<Map<String, Long>> = _savedQuantitiesScaled.asStateFlow()

    private val _previousRoundQuantitiesScaled = MutableStateFlow<Map<String, Long>>(emptyMap())
    val previousRoundQuantitiesScaled: StateFlow<Map<String, Long>> = _previousRoundQuantitiesScaled.asStateFlow()

    private val _favoriteProductIds = MutableStateFlow<Set<String>>(initialFavoriteProductIds)
    val favoriteProductIds: StateFlow<Set<String>> = _favoriteProductIds.asStateFlow()

    private val _filterFavoritesOnly = MutableStateFlow(false)
    val filterFavoritesOnly: StateFlow<Boolean> = _filterFavoritesOnly.asStateFlow()

    private val _filterInOrderOnly = MutableStateFlow(false)
    val filterInOrderOnly: StateFlow<Boolean> = _filterInOrderOnly.asStateFlow()

    private val _productSearchQuery = MutableStateFlow("")
    val productSearchQuery: StateFlow<String> = _productSearchQuery.asStateFlow()

    private val _selectedProductForDetail = MutableStateFlow<VeggieProduct?>(null)
    val selectedProductForDetail: StateFlow<VeggieProduct?> = _selectedProductForDetail.asStateFlow()

    private val _historyItems = MutableStateFlow<List<CustomerVeggieRoundHistoryItem>>(emptyList())
    val historyItems: StateFlow<List<CustomerVeggieRoundHistoryItem>> = _historyItems.asStateFlow()

    private val _historySearchQuery = MutableStateFlow("")
    val historySearchQuery: StateFlow<String> = _historySearchQuery.asStateFlow()

    private val _historyPage = MutableStateFlow(0)
    val historyPage: StateFlow<Int> = _historyPage.asStateFlow()

    private val _historyPageSize = MutableStateFlow(10)
    val historyPageSize: StateFlow<Int> = _historyPageSize.asStateFlow()

    private val _isSavingOrder = MutableStateFlow(false)
    val isSavingOrder: StateFlow<Boolean> = _isSavingOrder.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _hasUnsavedOrderChanges = MutableStateFlow(false)
    val hasUnsavedOrderChanges: StateFlow<Boolean> = _hasUnsavedOrderChanges.asStateFlow()

    private val _displaySettings = MutableStateFlow<CustomerDisplaySettings?>(null)
    val displaySettings: StateFlow<CustomerDisplaySettings?> = _displaySettings.asStateFlow()

    private var loadedRoundId: String? = null
    private var loadJob: Job? = null

    init {
        loadData()
    }

    fun loadData(preserveDraftIfSameRound: Boolean = true) {
        loadJob?.cancel()
        loadJob = activeScope.launch {
            try {
                val (state, history) = coroutineScope {
                    val stateDeferred = async { onFetchVeggieState() }
                    val historyDeferred = async { onFetchHistory() }
                    stateDeferred.await() to historyDeferred.await()
                }

                val serverQuantities = state.currentEntries
                    .filter { it.orderedQuantityScaled > 0L }
                    .associate { it.veggieProductId to it.orderedQuantityScaled }

                _unitTypes.value = state.unitTypes.filter { it.isActive }.sortedByAccentInsensitive { it.name }
                _products.value = state.products.filter { it.isActive && !it.isDeleted }.sortedByAccentInsensitive { it.name }
                _inactiveOrderedProducts.value = state.products
                    .filter { (!it.isActive || it.isDeleted) && it.id in serverQuantities }
                    .sortedByAccentInsensitive { it.name }
                _activeRound.value = state.activeRound
                _previousRoundQuantitiesScaled.value = state.previousRoundQuantities
                _favoriteProductIds.value = state.favoriteProductIds
                if (state.displaySettings != null) {
                    _displaySettings.value = state.displaySettings
                }
                _historyItems.value = history

                val currentRoundId = state.activeRound?.id
                val sameRound = currentRoundId != null && currentRoundId == loadedRoundId
                loadedRoundId = currentRoundId

                val visibleProductIds = (_products.value + _inactiveOrderedProducts.value).map { it.id }.toSet()
                _savedQuantitiesScaled.value = serverQuantities
                val isRoundOpen = state.activeRound?.status == VeggieRoundStatus.OPEN
                if (state.activeRound?.status == VeggieRoundStatus.SETTLED && currentRoundId != null) {
                    onClearDraft?.invoke(currentRoundId)
                }
                if (!isRoundOpen || !preserveDraftIfSameRound || !sameRound || !_hasUnsavedOrderChanges.value) {
                    val localDraft = if (currentRoundId != null && isRoundOpen) {
                        onLoadDraft?.invoke(currentRoundId)?.filter { (pid, qty) -> qty > 0L && pid in visibleProductIds }
                    } else {
                        null
                    }
                    if (localDraft != null && localDraft != serverQuantities) {
                        _draftQuantitiesScaled.value = localDraft
                        _hasUnsavedOrderChanges.value = true
                    } else {
                        _draftQuantitiesScaled.value = serverQuantities
                        _hasUnsavedOrderChanges.value = false
                    }
                } else {
                    // Prune any draft entries for products that were deactivated before saving
                    val prunedDraft = _draftQuantitiesScaled.value.filterKeys { it in visibleProductIds }
                    if (prunedDraft != _draftQuantitiesScaled.value) {
                        _draftQuantitiesScaled.value = prunedDraft
                    }
                    recomputeUnsavedFlag()
                }
                _errorMessage.value = null
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AppLogger.error("CustomerVeggieViewModel", "Failed to load veggie state: ${e.message}", e)
                _errorMessage.value = e.message
            }
        }
    }

    fun updateProductSearchQuery(query: String) {
        _productSearchQuery.value = query
    }

    fun toggleFavorite(productId: String) {
        val current = _favoriteProductIds.value
        val updated = if (productId in current) {
            current - productId
        } else {
            current + productId
        }
        persistFavoritesUpdate(previous = current, updated = updated)
    }

    fun isFavorite(productId: String): Boolean {
        return productId in _favoriteProductIds.value
    }

    fun toggleFilterFavoritesOnly() {
        val next = !_filterFavoritesOnly.value
        _filterFavoritesOnly.value = next
        if (next) {
            _filterInOrderOnly.value = false
        }
    }

    fun setFilterFavoritesOnly(enabled: Boolean) {
        _filterFavoritesOnly.value = enabled
        if (enabled) {
            _filterInOrderOnly.value = false
        }
    }

    fun setFavoriteProductIds(ids: Set<String>) {
        val current = _favoriteProductIds.value
        persistFavoritesUpdate(previous = current, updated = ids)
    }

    private fun persistFavoritesUpdate(previous: Set<String>, updated: Set<String>) {
        _favoriteProductIds.value = updated
        onFavoritesChanged?.invoke(updated)
        onSaveFavorites?.let { save ->
            activeScope.launch {
                val ok = try {
                    save(updated)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    AppLogger.error("CustomerVeggieViewModel", "Failed to save veggie favorites: ${e.message}", e)
                    false
                }
                if (!ok && _favoriteProductIds.value == updated) {
                    _favoriteProductIds.value = previous
                    onFavoritesChanged?.invoke(previous)
                    _errorMessage.value = "Failed to save favorites"
                }
            }
        }
    }

    fun setFilterInOrderOnly(enabled: Boolean) {
        _filterInOrderOnly.value = enabled
        if (enabled) {
            _filterFavoritesOnly.value = false
        }
    }

    fun toggleFilterInOrderOnly() {
        val next = !_filterInOrderOnly.value
        _filterInOrderOnly.value = next
        if (next) {
            _filterFavoritesOnly.value = false
        }
    }

    fun updateHistorySearchQuery(query: String) {
        _historySearchQuery.value = query
        _historyPage.value = 0
    }

    fun setHistoryPage(page: Int) {
        _historyPage.value = page.coerceAtLeast(0)
    }

    fun setHistoryPageSize(pageSize: Int) {
        _historyPageSize.value = pageSize
        _historyPage.value = 0
    }

    fun openProductDetail(product: VeggieProduct) {
        _selectedProductForDetail.value = product
    }

    fun closeProductDetail() {
        _selectedProductForDetail.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun discardUnsavedChanges() {
        loadedRoundId?.let { onClearDraft?.invoke(it) }
        _draftQuantitiesScaled.value = _savedQuantitiesScaled.value
        _hasUnsavedOrderChanges.value = false
        loadData(preserveDraftIfSameRound = false)
    }

    fun updateProductQuantity(productId: String, quantityScaled: Long) {
        val round = _activeRound.value ?: return
        if (round.status != VeggieRoundStatus.OPEN) return

        val clamped = quantityScaled.coerceAtLeast(0L)
        val updated = _draftQuantitiesScaled.value.toMutableMap()
        if (clamped == 0L) {
            updated.remove(productId)
        } else {
            updated[productId] = clamped
        }
        _draftQuantitiesScaled.value = updated
        recomputeUnsavedFlag()
        persistDraftIfChanged(round.id)
    }

    fun copyPreviousRoundOrder() {
        val round = _activeRound.value ?: return
        if (round.status != VeggieRoundStatus.OPEN) return
        val prev = _previousRoundQuantitiesScaled.value.filterValues { it > 0L }
        if (prev.isEmpty()) return

        val activeProductIds = _products.value.map { it.id }.toSet()
        val filteredPrev = prev.filterKeys { it in activeProductIds }
        if (filteredPrev.isEmpty()) return

        _draftQuantitiesScaled.value = filteredPrev
        recomputeUnsavedFlag()
        persistDraftIfChanged(round.id)
    }

    fun calculateEstimatedItemsSubtotalCents(): Long {
        val productsById = (_products.value + _inactiveOrderedProducts.value).associateBy { it.id }
        var total = 0L
        for ((productId, qtyScaled) in _draftQuantitiesScaled.value) {
            if (qtyScaled <= 0L) continue
            val product = productsById[productId] ?: continue
            // Skip dual-unit products because estimatedPriceCents is per delivery unit (e.g. R$/kg)
            // whereas qtyScaled is in ordering units (e.g. pieces)
            if (product.isDualUnit) continue
            val price = product.estimatedPriceCents ?: continue
            total += VeggieSettlementCalculator.calculateTotalSpentFromUnitPrice(price, qtyScaled)
        }
        return total
    }

    fun saveOrder(
        onOrderSaved: () -> Unit = {},
        onOrderFailed: () -> Unit = {}
    ) {
        val round = _activeRound.value
        if (round == null || round.status != VeggieRoundStatus.OPEN) {
            onOrderFailed()
            return
        }
        if (_isSavingOrder.value) return

        _isSavingOrder.value = true
        _errorMessage.value = null
        val snapshotToSave = _draftQuantitiesScaled.value.filterValues { it > 0L }

        activeScope.launch {
            try {
                val ok = onSaveOrder(round.id, snapshotToSave)
                if (ok) {
                    _savedQuantitiesScaled.value = snapshotToSave
                    val currentCleanDraft = _draftQuantitiesScaled.value.filterValues { it > 0L }
                    if (currentCleanDraft == snapshotToSave) {
                        _draftQuantitiesScaled.value = snapshotToSave
                        _hasUnsavedOrderChanges.value = false
                        onClearDraft?.invoke(round.id)
                    } else {
                        recomputeUnsavedFlag()
                    }
                    loadData(preserveDraftIfSameRound = true)
                    onOrderSaved()
                } else {
                    _errorMessage.value = "Failed to save veggie order"
                    loadData(preserveDraftIfSameRound = true)
                    onOrderFailed()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AppLogger.error("CustomerVeggieViewModel", "Failed to save veggie order for round '${round.id}': ${e.message}", e)
                _errorMessage.value = e.message ?: "Failed to save veggie order"
                onOrderFailed()
            } finally {
                _isSavingOrder.value = false
            }
        }
    }

    fun filteredHistoryItems(): List<CustomerVeggieRoundHistoryItem> {
        val query = _historySearchQuery.value.trim()
        val all = _historyItems.value
        if (query.isBlank()) return all
        return all.filter { item ->
            item.entries.any { entry ->
                entry.veggieNameSnapshot.containsIgnoreAccents(query)
            }
        }
    }

    fun pagedHistoryItems(): List<CustomerVeggieRoundHistoryItem> {
        val filtered = filteredHistoryItems()
        val size = _historyPageSize.value
        val totalPages = if (filtered.isEmpty()) 1 else (filtered.size + size - 1) / size
        val page = _historyPage.value.coerceIn(0, totalPages - 1)
        val fromIndex = (page * size).coerceAtMost(filtered.size)
        val toIndex = (fromIndex + size).coerceAtMost(filtered.size)
        return filtered.subList(fromIndex, toIndex)
    }

    fun historyTotalPages(): Int {
        val filtered = filteredHistoryItems()
        val size = _historyPageSize.value
        if (filtered.isEmpty()) return 1
        return (filtered.size + size - 1) / size
    }

    private fun recomputeUnsavedFlag() {
        val cleanDraft = _draftQuantitiesScaled.value.filterValues { it > 0L }
        val cleanSaved = _savedQuantitiesScaled.value.filterValues { it > 0L }
        _hasUnsavedOrderChanges.value = cleanDraft != cleanSaved
    }

    private fun persistDraftIfChanged(roundId: String) {
        if (_hasUnsavedOrderChanges.value) {
            onSaveDraft?.invoke(roundId, _draftQuantitiesScaled.value.filterValues { it > 0L })
        } else {
            onClearDraft?.invoke(roundId)
        }
    }
}
