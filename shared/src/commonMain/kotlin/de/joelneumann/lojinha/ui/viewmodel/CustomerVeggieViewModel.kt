package de.joelneumann.lojinha.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.joelneumann.lojinha.domain.model.*
import de.joelneumann.lojinha.domain.repository.CustomerVeggieRoundHistoryItem
import de.joelneumann.lojinha.ui.utils.containsIgnoreAccents
import de.joelneumann.lojinha.ui.utils.sortedByAccentInsensitive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

class CustomerVeggieViewModel(
    private val onFetchVeggieState: suspend () -> CustomerVeggieStateResponse,
    private val onSaveOrder: suspend (roundId: String, quantitiesScaled: Map<String, Long>) -> Boolean,
    private val onFetchHistory: suspend () -> List<CustomerVeggieRoundHistoryItem>,
    coroutineScope: CoroutineScope? = null
) : ViewModel() {

    private val activeScope = coroutineScope ?: viewModelScope

    private val _unitTypes = MutableStateFlow<List<VeggieUnitType>>(emptyList())
    val unitTypes: StateFlow<List<VeggieUnitType>> = _unitTypes.asStateFlow()

    private val _products = MutableStateFlow<List<VeggieProduct>>(emptyList())
    val products: StateFlow<List<VeggieProduct>> = _products.asStateFlow()

    private val _activeRound = MutableStateFlow<VeggieOrderRound?>(null)
    val activeRound: StateFlow<VeggieOrderRound?> = _activeRound.asStateFlow()

    private val _draftQuantitiesScaled = MutableStateFlow<Map<String, Long>>(emptyMap())
    val draftQuantitiesScaled: StateFlow<Map<String, Long>> = _draftQuantitiesScaled.asStateFlow()

    private val _savedQuantitiesScaled = MutableStateFlow<Map<String, Long>>(emptyMap())
    val savedQuantitiesScaled: StateFlow<Map<String, Long>> = _savedQuantitiesScaled.asStateFlow()

    private val _previousRoundQuantitiesScaled = MutableStateFlow<Map<String, Long>>(emptyMap())
    val previousRoundQuantitiesScaled: StateFlow<Map<String, Long>> = _previousRoundQuantitiesScaled.asStateFlow()

    private val _productSearchQuery = MutableStateFlow("")
    val productSearchQuery: StateFlow<String> = _productSearchQuery.asStateFlow()

    private val _selectedProductForDetail = MutableStateFlow<VeggieProduct?>(null)
    val selectedProductForDetail: StateFlow<VeggieProduct?> = _selectedProductForDetail.asStateFlow()

    private val _historyItems = MutableStateFlow<List<CustomerVeggieRoundHistoryItem>>(emptyList())
    val historyItems: StateFlow<List<CustomerVeggieRoundHistoryItem>> = _historyItems.asStateFlow()

    private val _historySearchQuery = MutableStateFlow("")
    val historySearchQuery: StateFlow<String> = _historySearchQuery.asStateFlow()

    private val _isSavingOrder = MutableStateFlow(false)
    val isSavingOrder: StateFlow<Boolean> = _isSavingOrder.asStateFlow()

    private val _orderSavedSuccessBanner = MutableStateFlow(false)
    val orderSavedSuccessBanner: StateFlow<Boolean> = _orderSavedSuccessBanner.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _hasUnsavedOrderChanges = MutableStateFlow(false)
    val hasUnsavedOrderChanges: StateFlow<Boolean> = _hasUnsavedOrderChanges.asStateFlow()

    private var loadedRoundId: String? = null
    private var loadJob: Job? = null
    private var bannerJob: Job? = null

    init {
        loadData()
    }

    fun loadData(preserveDraftIfSameRound: Boolean = true) {
        loadJob?.cancel()
        loadJob = activeScope.launch {
            try {
                val state = onFetchVeggieState()
                val history = onFetchHistory()

                _unitTypes.value = state.unitTypes.filter { it.isActive }.sortedByAccentInsensitive { it.name }
                _products.value = state.products.filter { it.isActive && !it.isDeleted }.sortedByAccentInsensitive { it.name }
                _activeRound.value = state.activeRound
                _previousRoundQuantitiesScaled.value = state.previousRoundQuantities
                _historyItems.value = history

                val serverQuantities = state.currentEntries
                    .filter { it.orderedQuantityScaled > 0L }
                    .associate { it.veggieProductId to it.orderedQuantityScaled }

                val currentRoundId = state.activeRound?.id
                val sameRound = currentRoundId != null && currentRoundId == loadedRoundId
                loadedRoundId = currentRoundId

                _savedQuantitiesScaled.value = serverQuantities
                if (!preserveDraftIfSameRound || !sameRound || !_hasUnsavedOrderChanges.value) {
                    _draftQuantitiesScaled.value = serverQuantities
                    _hasUnsavedOrderChanges.value = false
                } else {
                    recomputeUnsavedFlag()
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
        }
    }

    fun updateProductSearchQuery(query: String) {
        _productSearchQuery.value = query
    }

    fun updateHistorySearchQuery(query: String) {
        _historySearchQuery.value = query
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
    }

    fun incrementProductQuantity(product: VeggieProduct, unitType: VeggieUnitType?) {
        val step = if (unitType?.allowDecimals == true) VEGGIE_QTY_SCALE / 2L else VEGGIE_QTY_SCALE
        val current = _draftQuantitiesScaled.value[product.id] ?: 0L
        updateProductQuantity(product.id, current + step)
    }

    fun decrementProductQuantity(product: VeggieProduct, unitType: VeggieUnitType?) {
        val step = if (unitType?.allowDecimals == true) VEGGIE_QTY_SCALE / 2L else VEGGIE_QTY_SCALE
        val current = _draftQuantitiesScaled.value[product.id] ?: 0L
        updateProductQuantity(product.id, (current - step).coerceAtLeast(0L))
    }

    fun calculateEstimatedItemsSubtotalCents(): Long {
        val productsById = _products.value.associateBy { it.id }
        var total = 0L
        for ((productId, qtyScaled) in _draftQuantitiesScaled.value) {
            if (qtyScaled <= 0L) continue
            val price = productsById[productId]?.estimatedPriceCents ?: continue
            total += ((qtyScaled.toDouble() / VEGGIE_QTY_SCALE.toDouble()) * price.toDouble()).roundToLong()
        }
        return total
    }

    fun saveOrder(onOrderSaved: () -> Unit = {}) {
        val round = _activeRound.value ?: return
        if (round.status != VeggieRoundStatus.OPEN) return
        if (_isSavingOrder.value) return

        _isSavingOrder.value = true
        _errorMessage.value = null
        val snapshotToSave = _draftQuantitiesScaled.value.filterValues { it > 0L }

        activeScope.launch {
            try {
                val ok = onSaveOrder(round.id, snapshotToSave)
                if (ok) {
                    _savedQuantitiesScaled.value = snapshotToSave
                    _draftQuantitiesScaled.value = snapshotToSave
                    _hasUnsavedOrderChanges.value = false
                    loadData(preserveDraftIfSameRound = false)
                    showSavedBanner()
                    onOrderSaved()
                } else {
                    _errorMessage.value = "Failed to save veggie order"
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to save veggie order"
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

    private fun recomputeUnsavedFlag() {
        val cleanDraft = _draftQuantitiesScaled.value.filterValues { it > 0L }
        val cleanSaved = _savedQuantitiesScaled.value.filterValues { it > 0L }
        _hasUnsavedOrderChanges.value = cleanDraft != cleanSaved
    }

    private fun showSavedBanner() {
        bannerJob?.cancel()
        _orderSavedSuccessBanner.value = true
        bannerJob = activeScope.launch {
            delay(3500)
            _orderSavedSuccessBanner.value = false
        }
    }
}
