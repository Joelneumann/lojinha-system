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
    val buyerFeeInput: String = ""
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

                val statusChanged = previousRound?.id != newState.round?.id ||
                    previousRound?.status != newState.round?.status

                if (forceSyncDrafts || statusChanged || !_hasUnsavedChanges.value) {
                    applyServerDrafts(newState)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // Keep existing state on transient network errors; SSE/retry will recover
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun applyServerDrafts(state: BuyerVeggieRoundStateResponse) {
        val round = state.round
        if (round == null || round.status != VeggieRoundStatus.CLOSED) {
            if (round != null) {
                onClearDraft?.invoke(round.id)
            }
            _marketPrices.value = emptyMap()
            _totalSpent.value = emptyMap()
            _manuallyEditedTotalSpentIds.value = emptySet()
            _buyerFeeInput.value = ""
            _hasUnsavedChanges.value = false
            return
        }

        val priceMap = mutableMapOf<String, String>()
        val spentMap = mutableMapOf<String, String>()
        val manualSpent = mutableSetOf<String>()

        state.items.forEach { item ->
            item.marketUnitPriceCents?.let { priceCents ->
                priceMap[item.productId] = AdminVeggieViewModel.formatCentsToInput(priceCents)
            }
            item.totalSpentCents?.let { spentCents ->
                spentMap[item.productId] = AdminVeggieViewModel.formatCentsToInput(spentCents)
                val autoSpent = item.marketUnitPriceCents?.let { priceCents ->
                    VeggieSettlementCalculator.calculateTotalSpentFromUnitPrice(priceCents, item.totalOrderedScaled)
                }
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
            _marketPrices.value = (priceMap + localDraft.marketPrices).filterKeys { it in validIds }
            _totalSpent.value = (spentMap + localDraft.totalSpent).filterKeys { it in validIds }
            _manuallyEditedTotalSpentIds.value = (manualSpent + localDraft.manuallyEditedTotalSpentIds)
                .filter { it in validIds && !_totalSpent.value[it].isNullOrBlank() }
                .toSet()
            _buyerFeeInput.value = localDraft.buyerFeeInput
            _hasUnsavedChanges.value = true
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
        onSaveDraft?.invoke(
            round.id,
            BuyerVeggiePriceDraft(
                marketPrices = _marketPrices.value,
                totalSpent = _totalSpent.value,
                manuallyEditedTotalSpentIds = _manuallyEditedTotalSpentIds.value,
                buyerFeeInput = _buyerFeeInput.value
            )
        )
    }

    fun updateMarketUnitPrice(productId: String, input: String) {
        val newPrices = _marketPrices.value.toMutableMap()
        if (input.isEmpty()) {
            newPrices.remove(productId)
        } else {
            newPrices[productId] = input
        }
        _marketPrices.value = newPrices
        _hasUnsavedChanges.value = true

        if (productId !in _manuallyEditedTotalSpentIds.value) {
            val unitPriceCents = AdminVeggieViewModel.parseBrlInputToCents(input)
            val item = _roundState.value?.items?.firstOrNull { it.productId == productId }
            val newSpent = _totalSpent.value.toMutableMap()
            if (unitPriceCents != null && unitPriceCents >= 0L && item != null) {
                val autoSpent = VeggieSettlementCalculator.calculateTotalSpentFromUnitPrice(
                    unitPriceCents,
                    item.totalOrderedScaled
                )
                newSpent[productId] = AdminVeggieViewModel.formatCentsToInput(autoSpent)
            } else if (input.isBlank()) {
                newSpent.remove(productId)
            }
            _totalSpent.value = newSpent
        }
        persistLocalDraft()
    }

    fun updateTotalSpent(productId: String, input: String) {
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
        _buyerFeeInput.value = input
        _hasUnsavedChanges.value = true
        persistLocalDraft()
    }

    fun saveDraft(successMessage: String, errorMessage: String) {
        val currentState = _roundState.value ?: return
        val round = currentState.round ?: return
        if (round.status != VeggieRoundStatus.CLOSED || _isSaving.value) return

        _isSaving.value = true
        _feedbackMessage.value = null
        activeScope.launch {
            try {
                val buyerFeeCents = if (_buyerFeeInput.value.isBlank()) {
                    0L
                } else {
                    AdminVeggieViewModel.parseBrlInputToCents(_buyerFeeInput.value) ?: 0L
                }
                val settlements = currentState.items.map { item ->
                    val priceInput = _marketPrices.value[item.productId].orEmpty()
                    val spentInput = _totalSpent.value[item.productId].orEmpty()
                    val unitPriceCents = if (priceInput.isBlank()) null else AdminVeggieViewModel.parseBrlInputToCents(priceInput)
                    val totalSpentCents = if (spentInput.isBlank()) null else AdminVeggieViewModel.parseBrlInputToCents(spentInput)
                    BuyerProductPriceDraft(
                        productId = item.productId,
                        marketUnitPriceCents = unitPriceCents,
                        totalSpentCents = totalSpentCents
                    )
                }

                val ok = repository.saveDraftSettlement(
                    BuyerSaveDraftSettlementRequest(
                        roundId = round.id,
                        buyerOperationFeeCents = buyerFeeCents.coerceAtLeast(0L),
                        productSettlements = settlements
                    )
                )
                if (ok) {
                    onClearDraft?.invoke(round.id)
                    _hasUnsavedChanges.value = false
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
