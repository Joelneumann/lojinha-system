package de.joelneumann.lojinha.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.joelneumann.lojinha.domain.model.*
import de.joelneumann.lojinha.domain.repository.BuyerVeggieRepository
import de.joelneumann.lojinha.domain.service.VeggieSettlementCalculator
import de.joelneumann.lojinha.ui.viewmodel.admin.AdminVeggieViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class BuyerVeggiePriceDraft(
    val marketPrices: Map<String, String> = emptyMap(),
    val totalSpent: Map<String, String> = emptyMap(),
    val manuallyEditedTotalSpentIds: Set<String> = emptySet(),
    val buyerFeeInput: String? = null
)

class BuyerVeggieViewModel(
    private val repository: BuyerVeggieRepository,
    coroutineScope: CoroutineScope? = null,
    private val onLoadDraft: ((roundId: String) -> BuyerVeggiePriceDraft?)? = null,
    private val onSaveDraft: ((roundId: String, draft: BuyerVeggiePriceDraft) -> Unit)? = null,
    private val onClearDraft: ((roundId: String) -> Unit)? = null
) : ViewModel() {
    private val activeScope = coroutineScope ?: viewModelScope
    private var loadJob: Job? = null

    private val _roundState = MutableStateFlow<BuyerVeggieRoundStateResponse?>(null)
    val roundState: StateFlow<BuyerVeggieRoundStateResponse?> = _roundState.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _isExportingPdf = MutableStateFlow(false)
    val isExportingPdf: StateFlow<Boolean> = _isExportingPdf.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _marketPrices = MutableStateFlow<Map<String, String>>(emptyMap())
    val marketPrices: StateFlow<Map<String, String>> = _marketPrices.asStateFlow()

    private val _totalSpent = MutableStateFlow<Map<String, String>>(emptyMap())
    val totalSpent: StateFlow<Map<String, String>> = _totalSpent.asStateFlow()

    private val _manuallyEditedTotalSpentIds = MutableStateFlow<Set<String>>(emptySet())

    private val _buyerFeeInput = MutableStateFlow("")
    val buyerFeeInput: StateFlow<String> = _buyerFeeInput.asStateFlow()

    private val _hasUnsavedChanges = MutableStateFlow(false)
    val hasUnsavedChanges: StateFlow<Boolean> = _hasUnsavedChanges.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    private val _isErrorFeedback = MutableStateFlow(false)
    val isErrorFeedback: StateFlow<Boolean> = _isErrorFeedback.asStateFlow()

    // Track only fields explicitly touched by the Buyer so SSE updates from Admin merge cleanly
    // and saving never overwrites untouched Admin-entered prices with null.
    private val dirtyMarketPriceIds = mutableSetOf<String>()
    private val dirtyTotalSpentIds = mutableSetOf<String>()
    private var dirtyBuyerFee = false
    private var lastClosedRoundId: String? = null

    private fun clearDirtyTracking() {
        dirtyMarketPriceIds.clear()
        dirtyTotalSpentIds.clear()
        dirtyBuyerFee = false
    }

    init {
        loadState(forceSyncDrafts = true)
        activeScope.launch {
            repository.observeDataChanges().collect {
                loadState(forceSyncDrafts = false)
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    fun setIsExportingPdf(exporting: Boolean) {
        _isExportingPdf.value = exporting
    }

    fun reportError(message: String) {
        _isErrorFeedback.value = true
        _feedbackMessage.value = message
    }

    fun loadState(forceSyncDrafts: Boolean = false) {
        loadJob?.cancel()
        loadJob = activeScope.launch {
            if (_roundState.value == null) {
                _isLoading.value = true
            }
            try {
                val previousRound = _roundState.value?.round
                val newState = repository.getRoundState()
                _roundState.value = newState
                if (_isErrorFeedback.value && _feedbackMessage.value != null && previousRound == null) {
                    _feedbackMessage.value = null
                    _isErrorFeedback.value = false
                }

                val statusChanged = previousRound?.id != newState.round?.id ||
                    previousRound?.status != newState.round?.status

                if (forceSyncDrafts || statusChanged || !_hasUnsavedChanges.value) {
                    applyServerDrafts(newState)
                } else {
                    mergeServerDraftsPreservingDirty(newState)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (_roundState.value == null) {
                    _isErrorFeedback.value = true
                    _feedbackMessage.value = e.message ?: "Failed to load round state"
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun mergeServerDraftsPreservingDirty(state: BuyerVeggieRoundStateResponse) {
        val round = state.round
        if (round == null || round.status != VeggieRoundStatus.CLOSED) {
            applyServerDrafts(state)
            return
        }
        val validIds = state.items.map { it.productId }.toSet()
        dirtyMarketPriceIds.retainAll(validIds)
        dirtyTotalSpentIds.retainAll(validIds)

        val mergedPrices = _marketPrices.value.filterKeys { it in validIds }.toMutableMap()
        val mergedSpent = _totalSpent.value.filterKeys { it in validIds }.toMutableMap()
        val mergedManual = _manuallyEditedTotalSpentIds.value.filter { it in validIds }.toMutableSet()

        state.items.forEach { item ->
            if (item.productId !in dirtyMarketPriceIds) {
                val priceCents = item.marketUnitPriceCents
                if (priceCents != null) {
                    mergedPrices[item.productId] = AdminVeggieViewModel.formatCentsToInput(priceCents)
                } else {
                    mergedPrices.remove(item.productId)
                }
            }
            if (item.productId !in dirtyTotalSpentIds) {
                val spentCents = item.totalSpentCents
                if (spentCents != null) {
                    mergedSpent[item.productId] = AdminVeggieViewModel.formatCentsToInput(spentCents)
                    val autoSpent = if (!item.isDualUnit) {
                        item.marketUnitPriceCents?.let { priceCents ->
                            VeggieSettlementCalculator.calculateTotalSpentFromUnitPrice(priceCents, item.totalOrderedScaled)
                        }
                    } else null
                    if (autoSpent == null || spentCents != autoSpent) {
                        mergedManual.add(item.productId)
                    } else {
                        mergedManual.remove(item.productId)
                    }
                } else {
                    mergedSpent.remove(item.productId)
                    mergedManual.remove(item.productId)
                }
            } else if (!item.isDualUnit && item.productId !in mergedManual && item.productId in dirtyMarketPriceIds) {
                val unitPriceCents = AdminVeggieViewModel.parseBrlInputToCents(mergedPrices[item.productId].orEmpty())
                if (unitPriceCents != null && unitPriceCents >= 0L) {
                    val autoSpent = VeggieSettlementCalculator.calculateTotalSpentFromUnitPrice(unitPriceCents, item.totalOrderedScaled)
                    mergedSpent[item.productId] = AdminVeggieViewModel.formatCentsToInput(autoSpent)
                }
            }
        }
        _marketPrices.value = mergedPrices
        _totalSpent.value = mergedSpent
        _manuallyEditedTotalSpentIds.value = mergedManual
        if (!dirtyBuyerFee) {
            _buyerFeeInput.value = if (state.buyerOperationFeeCents > 0L) {
                AdminVeggieViewModel.formatCentsToInput(state.buyerOperationFeeCents)
            } else {
                ""
            }
        }
    }

    private fun applyServerDrafts(state: BuyerVeggieRoundStateResponse) {
        val round = state.round
        if (round == null || round.status != VeggieRoundStatus.CLOSED) {
            // Only clear localStorage draft when the round is settled/gone (round == null),
            // NOT when an Admin temporarily reopens a CLOSED round to OPEN.
            if (round == null) {
                lastClosedRoundId?.let { onClearDraft?.invoke(it) }
                lastClosedRoundId = null
            }
            clearDirtyTracking()
            _marketPrices.value = emptyMap()
            _totalSpent.value = emptyMap()
            _manuallyEditedTotalSpentIds.value = emptySet()
            _buyerFeeInput.value = ""
            _hasUnsavedChanges.value = false
            return
        }

        lastClosedRoundId = round.id
        clearDirtyTracking()
        val priceMap = mutableMapOf<String, String>()
        val spentMap = mutableMapOf<String, String>()
        val manualSpent = mutableSetOf<String>()

        state.items.forEach { item ->
            item.marketUnitPriceCents?.let { priceCents ->
                priceMap[item.productId] = AdminVeggieViewModel.formatCentsToInput(priceCents)
            }
            item.totalSpentCents?.let { spentCents ->
                spentMap[item.productId] = AdminVeggieViewModel.formatCentsToInput(spentCents)
                val autoSpent = if (!item.isDualUnit) {
                    item.marketUnitPriceCents?.let { priceCents ->
                        VeggieSettlementCalculator.calculateTotalSpentFromUnitPrice(priceCents, item.totalOrderedScaled)
                    }
                } else null
                if (autoSpent == null || spentCents != autoSpent) {
                    manualSpent.add(item.productId)
                }
            }
        }

        val serverBuyerFee = if (state.buyerOperationFeeCents > 0L) {
            AdminVeggieViewModel.formatCentsToInput(state.buyerOperationFeeCents)
        } else {
            ""
        }

        val localDraft = onLoadDraft?.invoke(round.id)
        if (localDraft != null) {
            val validIds = state.items.map { it.productId }.toSet()
            val mergedPrices = priceMap.toMutableMap()
            localDraft.marketPrices.forEach { (pid, value) ->
                if (pid in validIds) {
                    dirtyMarketPriceIds.add(pid)
                    if (value.isBlank()) mergedPrices.remove(pid) else mergedPrices[pid] = value
                }
            }
            val mergedSpent = spentMap.toMutableMap()
            localDraft.totalSpent.forEach { (pid, value) ->
                if (pid in validIds) {
                    dirtyTotalSpentIds.add(pid)
                    if (value.isBlank()) {
                        mergedSpent.remove(pid)
                        manualSpent.remove(pid)
                    } else {
                        mergedSpent[pid] = value
                    }
                }
            }
            _marketPrices.value = mergedPrices
            _totalSpent.value = mergedSpent
            _manuallyEditedTotalSpentIds.value = (manualSpent + localDraft.manuallyEditedTotalSpentIds)
                .filter { it in validIds && !mergedSpent[it].isNullOrBlank() }
                .toSet()
            if (localDraft.buyerFeeInput != null) {
                _buyerFeeInput.value = localDraft.buyerFeeInput
                dirtyBuyerFee = true
            } else {
                _buyerFeeInput.value = serverBuyerFee
            }
            _hasUnsavedChanges.value = dirtyMarketPriceIds.isNotEmpty() || dirtyTotalSpentIds.isNotEmpty() || dirtyBuyerFee
        } else {
            _marketPrices.value = priceMap
            _totalSpent.value = spentMap
            _manuallyEditedTotalSpentIds.value = manualSpent
            _buyerFeeInput.value = serverBuyerFee
            _hasUnsavedChanges.value = false
        }
    }

    private fun persistLocalDraft() {
        val round = _roundState.value?.round ?: return
        if (round.status != VeggieRoundStatus.CLOSED) return
        val dirtyPrices = dirtyMarketPriceIds.associateWith { pid -> _marketPrices.value[pid].orEmpty() }
        val dirtySpent = dirtyTotalSpentIds.associateWith { pid -> _totalSpent.value[pid].orEmpty() }
        onSaveDraft?.invoke(
            round.id,
            BuyerVeggiePriceDraft(
                marketPrices = dirtyPrices,
                totalSpent = dirtySpent,
                manuallyEditedTotalSpentIds = _manuallyEditedTotalSpentIds.value.filter { it in dirtyTotalSpentIds }.toSet(),
                buyerFeeInput = if (dirtyBuyerFee) _buyerFeeInput.value else null
            )
        )
    }

    fun updateMarketUnitPrice(productId: String, input: String) {
        dirtyMarketPriceIds.add(productId)
        val newPrices = _marketPrices.value.toMutableMap()
        if (input.isEmpty()) {
            newPrices.remove(productId)
        } else {
            newPrices[productId] = input
        }
        _marketPrices.value = newPrices
        _hasUnsavedChanges.value = true

        val item = _roundState.value?.items?.firstOrNull { it.productId == productId }
        if (productId !in _manuallyEditedTotalSpentIds.value && item?.isDualUnit != true) {
            val unitPriceCents = AdminVeggieViewModel.parseBrlInputToCents(input)
            val newSpent = _totalSpent.value.toMutableMap()
            if (unitPriceCents != null && unitPriceCents >= 0L && item != null) {
                val autoSpent = VeggieSettlementCalculator.calculateTotalSpentFromUnitPrice(
                    unitPriceCents,
                    item.totalOrderedScaled
                )
                newSpent[productId] = AdminVeggieViewModel.formatCentsToInput(autoSpent)
                dirtyTotalSpentIds.add(productId)
            } else if (input.isBlank()) {
                newSpent.remove(productId)
                dirtyTotalSpentIds.add(productId)
            }
            _totalSpent.value = newSpent
        }
        persistLocalDraft()
    }

    fun updateTotalSpent(productId: String, input: String) {
        dirtyTotalSpentIds.add(productId)
        val newSpent = _totalSpent.value.toMutableMap()
        if (input.isEmpty()) {
            newSpent.remove(productId)
            _manuallyEditedTotalSpentIds.value = _manuallyEditedTotalSpentIds.value - productId
        } else {
            newSpent[productId] = input
            _manuallyEditedTotalSpentIds.value = _manuallyEditedTotalSpentIds.value + productId
        }
        _totalSpent.value = newSpent
        _hasUnsavedChanges.value = true
        persistLocalDraft()
    }

    fun updateBuyerFee(input: String) {
        dirtyBuyerFee = true
        _buyerFeeInput.value = input
        _hasUnsavedChanges.value = true
        persistLocalDraft()
    }

    fun saveDraft(successMessage: String, errorMessage: String) {
        val currentState = _roundState.value ?: return
        val round = currentState.round ?: return
        if (round.status != VeggieRoundStatus.CLOSED || _isSaving.value) return

        // Validate BRL inputs before saving so malformed entries like "." or overflow don't silently coerce to null/0
        val rawFee = _buyerFeeInput.value
        val parsedFee = if (rawFee.isBlank()) 0L else AdminVeggieViewModel.parseBrlInputToCents(rawFee)
        if (parsedFee == null) {
            _isErrorFeedback.value = true
            _feedbackMessage.value = errorMessage
            return
        }
        val touchedIds = (dirtyMarketPriceIds + dirtyTotalSpentIds).toSet()
        for (item in currentState.items) {
            if (touchedIds.isNotEmpty() && item.productId !in touchedIds) continue
            val priceInput = _marketPrices.value[item.productId].orEmpty()
            val spentInput = _totalSpent.value[item.productId].orEmpty()
            if (priceInput.isNotBlank() && AdminVeggieViewModel.parseBrlInputToCents(priceInput) == null) {
                _isErrorFeedback.value = true
                _feedbackMessage.value = errorMessage
                return
            }
            if (spentInput.isNotBlank() && AdminVeggieViewModel.parseBrlInputToCents(spentInput) == null) {
                _isErrorFeedback.value = true
                _feedbackMessage.value = errorMessage
                return
            }
        }

        val snapshotPrices = _marketPrices.value
        val snapshotSpent = _totalSpent.value
        val snapshotFee = _buyerFeeInput.value

        _isSaving.value = true
        _feedbackMessage.value = null
        activeScope.launch {
            try {
                val buyerFeeCents = if (dirtyBuyerFee) {
                    parsedFee.coerceAtLeast(0L)
                } else {
                    currentState.buyerOperationFeeCents.coerceAtLeast(0L)
                }
                // Only send products that the Buyer actually touched so untouched Admin-entered prices are preserved
                val itemsToSubmit = if (touchedIds.isNotEmpty()) {
                    currentState.items.filter { it.productId in touchedIds }
                } else {
                    emptyList()
                }
                val settlements = itemsToSubmit.map { item ->
                    val priceInput = snapshotPrices[item.productId].orEmpty()
                    val spentInput = snapshotSpent[item.productId].orEmpty()
                    val unitPriceCents = if (item.productId in dirtyMarketPriceIds) {
                        if (priceInput.isBlank()) null else AdminVeggieViewModel.parseBrlInputToCents(priceInput)
                    } else {
                        item.marketUnitPriceCents
                    }
                    val totalSpentCents = if (item.productId in dirtyTotalSpentIds) {
                        if (spentInput.isBlank()) null else AdminVeggieViewModel.parseBrlInputToCents(spentInput)
                    } else {
                        item.totalSpentCents
                    }
                    BuyerProductPriceDraft(
                        productId = item.productId,
                        marketUnitPriceCents = unitPriceCents,
                        totalSpentCents = totalSpentCents
                    )
                }

                val ok = repository.saveDraftSettlement(
                    BuyerSaveDraftSettlementRequest(
                        roundId = round.id,
                        buyerOperationFeeCents = buyerFeeCents,
                        productSettlements = settlements
                    )
                )
                if (ok) {
                    val editedWhileInFlight = _marketPrices.value != snapshotPrices ||
                        _totalSpent.value != snapshotSpent ||
                        _buyerFeeInput.value != snapshotFee
                    if (!editedWhileInFlight) {
                        onClearDraft?.invoke(round.id)
                        clearDirtyTracking()
                        _hasUnsavedChanges.value = false
                    }
                    _isErrorFeedback.value = false
                    _feedbackMessage.value = successMessage
                    loadState(forceSyncDrafts = false)
                } else {
                    _isErrorFeedback.value = true
                    _feedbackMessage.value = errorMessage
                    loadState(forceSyncDrafts = false)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _isErrorFeedback.value = true
                _feedbackMessage.value = errorMessage
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun logout(onComplete: () -> Unit) {
        activeScope.launch {
            _roundState.value?.round?.id?.let { onClearDraft?.invoke(it) }
            repository.logout()
            onComplete()
        }
    }
}
