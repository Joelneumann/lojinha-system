package de.joelneumann.lojinha.ui.viewmodel.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.joelneumann.lojinha.domain.model.*
import de.joelneumann.lojinha.domain.repository.SettingsRepository
import de.joelneumann.lojinha.domain.repository.UserRepository
import de.joelneumann.lojinha.domain.repository.VeggieOrderRepository
import de.joelneumann.lojinha.domain.service.VeggieRoundSettlementResult
import de.joelneumann.lojinha.domain.service.VeggieSettlementCalculator
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.utils.Formatting
import de.joelneumann.lojinha.ui.utils.currentTimeMillis
import de.joelneumann.lojinha.ui.utils.generateUuid
import de.joelneumann.lojinha.ui.utils.invalidateVeggieImageCache
import de.joelneumann.lojinha.ui.utils.sortedByAccentInsensitive
import de.joelneumann.lojinha.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AdminVeggieSubTab {
    ACTIVE_ROUND,
    CATALOG,
    HISTORY
}

class AdminVeggieViewModel(
    private val veggieOrderRepository: VeggieOrderRepository,
    private val userRepository: UserRepository,
    private val settingsRepository: SettingsRepository? = null,
    private val onSaveProductImage: (suspend (productId: String, bytes: ByteArray, previousFileName: String?) -> String?)? = null,
    private val onDeleteProductImage: (suspend (productId: String, previousFileName: String?) -> Unit)? = null,
    coroutineScope: CoroutineScope? = null
) : ViewModel() {

    private val activeScope = coroutineScope ?: viewModelScope

    private val _currentSubTab = MutableStateFlow(AdminVeggieSubTab.ACTIVE_ROUND)
    val currentSubTab: StateFlow<AdminVeggieSubTab> = _currentSubTab.asStateFlow()

    private val _products = MutableStateFlow<List<VeggieProduct>>(emptyList())
    val products: StateFlow<List<VeggieProduct>> = _products.asStateFlow()

    private val _unitTypes = MutableStateFlow<List<VeggieUnitType>>(emptyList())
    val unitTypes: StateFlow<List<VeggieUnitType>> = _unitTypes.asStateFlow()

    private val _catalogSearchQuery = MutableStateFlow("")
    val catalogSearchQuery: StateFlow<String> = _catalogSearchQuery.asStateFlow()

    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: StateFlow<List<User>> = _users.asStateFlow()

    private val _rounds = MutableStateFlow<List<VeggieOrderRound>>(emptyList())
    val rounds: StateFlow<List<VeggieOrderRound>> = _rounds.asStateFlow()

    private val _selectedRound = MutableStateFlow<VeggieOrderRound?>(null)
    val selectedRound: StateFlow<VeggieOrderRound?> = _selectedRound.asStateFlow()

    private val _selectedRoundEntries = MutableStateFlow<List<VeggieOrderEntry>>(emptyList())
    val selectedRoundEntries: StateFlow<List<VeggieOrderEntry>> = _selectedRoundEntries.asStateFlow()

    private val _selectedRoundSettlements = MutableStateFlow<Map<String, VeggieRoundProductSettlement>>(emptyMap())
    val selectedRoundSettlements: StateFlow<Map<String, VeggieRoundProductSettlement>> = _selectedRoundSettlements.asStateFlow()

    // Draft inputs for 2D settlement matrix
    private val _draftActualQuantities = MutableStateFlow<Map<String, String>>(emptyMap())
    val draftActualQuantities: StateFlow<Map<String, String>> = _draftActualQuantities.asStateFlow()

    private val _draftMarketPrices = MutableStateFlow<Map<String, String>>(emptyMap())
    val draftMarketPrices: StateFlow<Map<String, String>> = _draftMarketPrices.asStateFlow()

    private val _draftTotalSpent = MutableStateFlow<Map<String, String>>(emptyMap())
    val draftTotalSpent: StateFlow<Map<String, String>> = _draftTotalSpent.asStateFlow()

    private val _manuallyEditedTotalSpentProductIds = MutableStateFlow<Set<String>>(emptySet())

    private val _draftBuyerFeeInput = MutableStateFlow("0")
    val draftBuyerFeeInput: StateFlow<String> = _draftBuyerFeeInput.asStateFlow()

    private val _draftTransportFeeInput = MutableStateFlow("0")
    val draftTransportFeeInput: StateFlow<String> = _draftTransportFeeInput.asStateFlow()

    private val _draftTransporterUserId = MutableStateFlow<String?>(null)
    val draftTransporterUserId: StateFlow<String?> = _draftTransporterUserId.asStateFlow()

    private val _hasUnsavedSettlementChanges = MutableStateFlow(false)
    val hasUnsavedSettlementChanges: StateFlow<Boolean> = _hasUnsavedSettlementChanges.asStateFlow()

    private val _liveSettlementPreview = MutableStateFlow<VeggieRoundSettlementResult?>(null)
    val liveSettlementPreview: StateFlow<VeggieRoundSettlementResult?> = _liveSettlementPreview.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var loadJob: Job? = null

    init {
        loadData()
    }

    fun selectSubTab(subTab: AdminVeggieSubTab) {
        _currentSubTab.value = subTab
    }

    fun updateCatalogSearchQuery(query: String) {
        _catalogSearchQuery.value = query
    }

    fun clearMessages() {
        _statusMessage.value = null
        _errorMessage.value = null
    }

    fun loadData(preserveDraftEdits: Boolean = true) {
        loadJob?.cancel()
        loadJob = activeScope.launch {
            try {
                val allUnits = veggieOrderRepository.getAllUnitTypes()
                val allProducts = veggieOrderRepository.getAllVeggieProducts()
                val allUsers = userRepository.getAllUsers().filter { it.isActive && !it.isDeleted }.sortedByAccentInsensitive { it.name }
                val allRounds = veggieOrderRepository.getAllRounds().sortedByDescending { it.openedAt }

                _unitTypes.value = allUnits
                _products.value = allProducts
                _users.value = allUsers
                _rounds.value = allRounds

                val currentSelectedId = _selectedRound.value?.id
                val targetRound = if (currentSelectedId != null) {
                    allRounds.find { it.id == currentSelectedId } ?: allRounds.firstOrNull()
                } else {
                    allRounds.firstOrNull { it.status != VeggieRoundStatus.SETTLED } ?: allRounds.firstOrNull()
                }

                if (targetRound != null) {
                    val isSameRound = currentSelectedId == targetRound.id
                    loadRoundDetailsInternal(targetRound, resetDraft = !(isSameRound && preserveDraftEdits && _hasUnsavedSettlementChanges.value))
                } else {
                    _selectedRound.value = null
                    _selectedRoundEntries.value = emptyList()
                    _selectedRoundSettlements.value = emptyMap()
                    _liveSettlementPreview.value = null
                    _hasUnsavedSettlementChanges.value = false
                }
            } catch (e: Exception) {
                AppLogger.error("AdminVeggieViewModel", "Failed to load veggie data: ${e.message}", e)
            }
        }
    }

    fun selectRound(roundId: String, switchToRoundTab: Boolean = true) {
        activeScope.launch {
            val round = _rounds.value.find { it.id == roundId } ?: veggieOrderRepository.getRoundById(roundId) ?: return@launch
            loadRoundDetailsInternal(round, resetDraft = true)
            if (switchToRoundTab) {
                _currentSubTab.value = AdminVeggieSubTab.ACTIVE_ROUND
            }
        }
    }

    private suspend fun loadRoundDetailsInternal(round: VeggieOrderRound, resetDraft: Boolean) {
        _selectedRound.value = round
        val entries = veggieOrderRepository.getEntriesForRound(round.id)
        val settlements = veggieOrderRepository.getProductSettlementsForRound(round.id).associateBy { it.veggieProductId }
        _selectedRoundEntries.value = entries
        _selectedRoundSettlements.value = settlements

        if (resetDraft) {
            _draftActualQuantities.value = entries.associate { entry ->
                entry.id to VeggieQuantityScaling.formatScaledQuantity(entry.deliveredQuantityScaled, entry.allowDecimalsSnapshot)
            }
            val productIds = entries.map { it.veggieProductId }.distinct()
            val productCatalogMap = _products.value.associateBy { it.id }

            _draftMarketPrices.value = productIds.associateWith { pid ->
                val savedPrice = settlements[pid]?.marketUnitPriceCents ?: productCatalogMap[pid]?.estimatedPriceCents
                savedPrice?.let { formatCentsToInput(it) } ?: ""
            }
            _draftTotalSpent.value = productIds.associateWith { pid ->
                val savedSpent = settlements[pid]?.totalSpentCents
                if (savedSpent != null && savedSpent > 0L) {
                    formatCentsToInput(savedSpent)
                } else {
                    val savedPrice = settlements[pid]?.marketUnitPriceCents ?: productCatalogMap[pid]?.estimatedPriceCents
                    val totalDelivered = entries.filter { it.veggieProductId == pid }.sumOf { it.deliveredQuantityScaled }
                    if (savedPrice != null && savedPrice > 0L && totalDelivered > 0L) {
                        val autoSpent = VeggieSettlementCalculator.calculateTotalSpentFromUnitPrice(savedPrice, totalDelivered)
                        formatCentsToInput(autoSpent)
                    } else {
                        ""
                    }
                }
            }
            _manuallyEditedTotalSpentProductIds.value = settlements.filterValues { (it.totalSpentCents ?: 0L) > 0L }.keys
            _draftBuyerFeeInput.value = formatCentsToInput(round.buyerOperationFeeCents)
            _draftTransportFeeInput.value = formatCentsToInput(round.transportFeeCents)
            _draftTransporterUserId.value = round.transporterUserId
            _hasUnsavedSettlementChanges.value = false
        }
        recomputeLivePreview()
    }

    fun updateEntryActualQuantity(entryId: String, input: String) {
        val entry = _selectedRoundEntries.value.find { it.id == entryId } ?: return
        val newQuantities = _draftActualQuantities.value.toMutableMap()
        newQuantities[entryId] = input
        _draftActualQuantities.value = newQuantities
        _hasUnsavedSettlementChanges.value = true

        val productId = entry.veggieProductId
        if (productId !in _manuallyEditedTotalSpentProductIds.value) {
            val marketPriceInput = _draftMarketPrices.value[productId] ?: ""
            val unitPriceCents = parseBrlInputToCents(marketPriceInput)
            if (unitPriceCents != null && unitPriceCents > 0L) {
                val totalDelivered = calculateDraftTotalDeliveredForProduct(productId, newQuantities)
                val autoSpent = VeggieSettlementCalculator.calculateTotalSpentFromUnitPrice(unitPriceCents, totalDelivered)
                val newSpentMap = _draftTotalSpent.value.toMutableMap()
                newSpentMap[productId] = if (autoSpent > 0L) formatCentsToInput(autoSpent) else "0"
                _draftTotalSpent.value = newSpentMap
            }
        }
        recomputeLivePreview()
    }

    fun updateProductMarketUnitPrice(productId: String, input: String) {
        val newPrices = _draftMarketPrices.value.toMutableMap()
        newPrices[productId] = input
        _draftMarketPrices.value = newPrices
        _hasUnsavedSettlementChanges.value = true

        val unitPriceCents = parseBrlInputToCents(input)
        if (unitPriceCents != null && unitPriceCents >= 0L) {
            val totalDelivered = calculateDraftTotalDeliveredForProduct(productId, _draftActualQuantities.value)
            val autoSpent = VeggieSettlementCalculator.calculateTotalSpentFromUnitPrice(unitPriceCents, totalDelivered)
            val newSpentMap = _draftTotalSpent.value.toMutableMap()
            newSpentMap[productId] = formatCentsToInput(autoSpent)
            _draftTotalSpent.value = newSpentMap
            _manuallyEditedTotalSpentProductIds.value = _manuallyEditedTotalSpentProductIds.value - productId
        }
        recomputeLivePreview()
    }

    fun updateProductTotalSpent(productId: String, input: String) {
        val newSpentMap = _draftTotalSpent.value.toMutableMap()
        newSpentMap[productId] = input
        _draftTotalSpent.value = newSpentMap
        _manuallyEditedTotalSpentProductIds.value = _manuallyEditedTotalSpentProductIds.value + productId
        _hasUnsavedSettlementChanges.value = true
        recomputeLivePreview()
    }

    fun updateBuyerFeeInput(input: String) {
        _draftBuyerFeeInput.value = input
        _hasUnsavedSettlementChanges.value = true
        recomputeLivePreview()
    }

    fun updateTransportFeeInput(input: String) {
        _draftTransportFeeInput.value = input
        _hasUnsavedSettlementChanges.value = true
        recomputeLivePreview()
    }

    fun updateTransporterUserId(userId: String?) {
        _draftTransporterUserId.value = userId
        _hasUnsavedSettlementChanges.value = true
        recomputeLivePreview()
    }

    private fun calculateDraftTotalDeliveredForProduct(
        productId: String,
        quantitiesMap: Map<String, String>
    ): Long {
        return _selectedRoundEntries.value
            .filter { it.veggieProductId == productId }
            .sumOf { entry ->
                val raw = quantitiesMap[entry.id]
                if (raw != null) {
                    VeggieQuantityScaling.parseQuantityScaled(raw, entry.allowDecimalsSnapshot) ?: entry.deliveredQuantityScaled
                } else {
                    entry.deliveredQuantityScaled
                }
            }
    }

    private fun buildCurrentDraftPayload(): DraftSettlementPayload? {
        val round = _selectedRound.value ?: return null
        val entries = _selectedRoundEntries.value
        val actualByEntryId = entries.associate { entry ->
            val raw = _draftActualQuantities.value[entry.id]
            val parsed = if (raw != null) {
                VeggieQuantityScaling.parseQuantityScaled(raw, entry.allowDecimalsSnapshot) ?: entry.deliveredQuantityScaled
            } else {
                entry.deliveredQuantityScaled
            }
            entry.id to parsed.coerceAtLeast(0L)
        }

        val entriesWithDraftQty = entries.map { entry ->
            entry.copy(actualQuantityScaled = actualByEntryId[entry.id] ?: entry.deliveredQuantityScaled)
        }

        val productIds = entries.map { it.veggieProductId }.distinct()
        val settlementInputs = productIds.associateWith { pid ->
            val marketCents = parseBrlInputToCents(_draftMarketPrices.value[pid] ?: "")
            val spentCents = parseBrlInputToCents(_draftTotalSpent.value[pid] ?: "")
            VeggieRoundProductSettlement(
                roundId = round.id,
                veggieProductId = pid,
                marketUnitPriceCents = marketCents,
                totalSpentCents = spentCents,
                updatedAt = currentTimeMillis()
            )
        }

        val buyerFeeCents = (parseBrlInputToCents(_draftBuyerFeeInput.value) ?: round.buyerOperationFeeCents).coerceAtLeast(0L)
        val transportFeeCents = (parseBrlInputToCents(_draftTransportFeeInput.value) ?: round.transportFeeCents).coerceAtLeast(0L)

        return DraftSettlementPayload(
            round = round,
            entriesWithDraftQty = entriesWithDraftQty,
            actualQuantitiesByEntryId = actualByEntryId,
            productSettlementsMap = settlementInputs,
            buyerFeeCents = buyerFeeCents,
            transportFeeCents = transportFeeCents,
            transporterUserId = _draftTransporterUserId.value
        )
    }

    private fun recomputeLivePreview() {
        val payload = buildCurrentDraftPayload()
        if (payload == null) {
            _liveSettlementPreview.value = null
            return
        }
        _liveSettlementPreview.value = VeggieSettlementCalculator.calculateRoundSettlement(
            roundId = payload.round.id,
            entries = payload.entriesWithDraftQty,
            productSettlementInputs = payload.productSettlementsMap,
            buyerOperationFeeCents = payload.buyerFeeCents,
            transportFeeCents = payload.transportFeeCents,
            transporterUserId = payload.transporterUserId,
            timestamp = currentTimeMillis()
        )
    }

    fun openNewRound() {
        if (_isSaving.value) return
        _isSaving.value = true
        clearMessages()
        activeScope.launch {
            try {
                val settings = settingsRepository?.getSettings() ?: SystemSettings()
                val newRound = veggieOrderRepository.openNewRound(
                    buyerOperationFeeCents = settings.defaultVeggieBuyerFeeCents,
                    transportFeeCents = settings.defaultVeggieTransportFeeCents,
                    openedAt = currentTimeMillis()
                )
                _selectedRound.value = newRound
                loadData(preserveDraftEdits = false)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to open round"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun closeSelectedRound() {
        val round = _selectedRound.value ?: return
        if (_isSaving.value) return
        _isSaving.value = true
        clearMessages()
        activeScope.launch {
            try {
                val ok = veggieOrderRepository.closeRound(round.id, currentTimeMillis())
                if (ok) {
                    loadData(preserveDraftEdits = false)
                } else {
                    _errorMessage.value = "Could not close round"
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to close round"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun reopenSelectedRound() {
        val round = _selectedRound.value ?: return
        if (_isSaving.value) return
        _isSaving.value = true
        clearMessages()
        activeScope.launch {
            try {
                val ok = veggieOrderRepository.reopenRound(round.id)
                if (ok) {
                    loadData(preserveDraftEdits = false)
                } else {
                    _errorMessage.value = "Could not reopen round"
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to reopen round"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun saveDraftSettlement() {
        val payload = buildCurrentDraftPayload() ?: return
        if (_isSaving.value) return
        _isSaving.value = true
        clearMessages()
        activeScope.launch {
            try {
                val ok = veggieOrderRepository.saveDraftSettlement(
                    roundId = payload.round.id,
                    buyerOperationFeeCents = payload.buyerFeeCents,
                    transportFeeCents = payload.transportFeeCents,
                    transporterUserId = payload.transporterUserId,
                    productSettlements = payload.productSettlementsMap.values.toList(),
                    actualQuantitiesByEntryId = payload.actualQuantitiesByEntryId
                )
                if (ok) {
                    _hasUnsavedSettlementChanges.value = false
                    _statusMessage.value = I18n.get().saved
                    loadData(preserveDraftEdits = false)
                } else {
                    _errorMessage.value = "Failed to save draft settlement"
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to save draft settlement"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun executeSettlement() {
        val payload = buildCurrentDraftPayload() ?: return
        if (_isSaving.value) return
        _isSaving.value = true
        clearMessages()
        activeScope.launch {
            try {
                val result = veggieOrderRepository.executeRoundSettlement(
                    roundId = payload.round.id,
                    buyerOperationFeeCents = payload.buyerFeeCents,
                    transportFeeCents = payload.transportFeeCents,
                    transporterUserId = payload.transporterUserId,
                    productSettlements = payload.productSettlementsMap.values.toList(),
                    actualQuantitiesByEntryId = payload.actualQuantitiesByEntryId,
                    settledAt = currentTimeMillis()
                )
                if (result != null) {
                    _hasUnsavedSettlementChanges.value = false
                    _statusMessage.value = I18n.get().saved
                    loadData(preserveDraftEdits = false)
                } else {
                    _errorMessage.value = "Failed to execute round settlement"
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to execute round settlement"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun addOrUpdateCustomerEntry(user: User, product: VeggieProduct, quantityInput: String) {
        val round = _selectedRound.value ?: return
        val unit = _unitTypes.value.find { it.id == product.unitTypeId }
        val allowDecimals = unit?.allowDecimals ?: false
        val qtyScaled = VeggieQuantityScaling.parseQuantityScaled(quantityInput, allowDecimals) ?: return

        activeScope.launch {
            try {
                val existing = _selectedRoundEntries.value.find { it.userId == user.id && it.veggieProductId == product.id }
                val now = currentTimeMillis()
                val entry = existing?.copy(
                    orderedQuantityScaled = if (round.status == VeggieRoundStatus.OPEN) qtyScaled else existing.orderedQuantityScaled,
                    actualQuantityScaled = qtyScaled,
                    updatedAt = now
                ) ?: VeggieOrderEntry(
                    id = generateUuid(),
                    roundId = round.id,
                    userId = user.id,
                    userNameSnapshot = user.name,
                    veggieProductId = product.id,
                    veggieNameSnapshot = product.name,
                    unitNameSnapshot = unit?.name ?: product.unitTypeId,
                    allowDecimalsSnapshot = allowDecimals,
                    orderedQuantityScaled = qtyScaled,
                    actualQuantityScaled = qtyScaled,
                    updatedAt = now
                )
                veggieOrderRepository.upsertAdminOrderEntry(entry)
                loadData(preserveDraftEdits = true)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to add order entry"
            }
        }
    }

    // --- Catalog Management ---

    fun saveVeggieProduct(
        product: VeggieProduct,
        newImageBytes: ByteArray?,
        removeExistingImage: Boolean
    ) {
        if (_isSaving.value) return
        _isSaving.value = true
        clearMessages()
        activeScope.launch {
            try {
                var finalImageFileName = product.imageFileName
                if (removeExistingImage && newImageBytes == null) {
                    onDeleteProductImage?.invoke(product.id, product.imageFileName)
                    invalidateVeggieImageCache(product.id)
                    finalImageFileName = null
                }
                val initialProduct = product.copy(imageFileName = finalImageFileName, updatedAt = currentTimeMillis())
                veggieOrderRepository.saveVeggieProduct(initialProduct)

                if (newImageBytes != null && newImageBytes.isNotEmpty()) {
                    val savedFileName = onSaveProductImage?.invoke(product.id, newImageBytes, product.imageFileName)
                    if (!savedFileName.isNullOrBlank()) {
                        veggieOrderRepository.saveVeggieProduct(
                            initialProduct.copy(
                                imageFileName = savedFileName,
                                updatedAt = currentTimeMillis()
                            )
                        )
                    }
                    invalidateVeggieImageCache(product.id)
                }
                loadData()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: I18n.get().errFailedToSaveProduct
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun toggleVeggieProductActive(product: VeggieProduct) {
        activeScope.launch {
            try {
                if (product.isActive) {
                    veggieOrderRepository.deactivateVeggieProduct(product.id)
                } else {
                    veggieOrderRepository.saveVeggieProduct(
                        product.copy(isActive = true, updatedAt = currentTimeMillis())
                    )
                }
                loadData()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: I18n.get().errFailedToUpdateProductStatus
            }
        }
    }

    fun deleteVeggieProduct(product: VeggieProduct) {
        activeScope.launch {
            try {
                onDeleteProductImage?.invoke(product.id, product.imageFileName)
                invalidateVeggieImageCache(product.id)
                veggieOrderRepository.hardDeleteVeggieProduct(product.id)
                loadData()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: I18n.get().errFailedToDeleteProduct
            }
        }
    }

    fun saveUnitType(unitType: VeggieUnitType) {
        activeScope.launch {
            try {
                veggieOrderRepository.saveUnitType(unitType)
                loadData()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to save unit type"
            }
        }
    }

    fun deleteUnitType(unitTypeId: String) {
        activeScope.launch {
            try {
                val existing = _unitTypes.value.find { it.id == unitTypeId } ?: return@launch
                veggieOrderRepository.saveUnitType(
                    existing.copy(isActive = false, updatedAt = currentTimeMillis())
                )
                loadData()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to delete unit type"
            }
        }
    }

    private data class DraftSettlementPayload(
        val round: VeggieOrderRound,
        val entriesWithDraftQty: List<VeggieOrderEntry>,
        val actualQuantitiesByEntryId: Map<String, Long>,
        val productSettlementsMap: Map<String, VeggieRoundProductSettlement>,
        val buyerFeeCents: Long,
        val transportFeeCents: Long,
        val transporterUserId: String?
    )

    companion object {
        fun formatCentsToInput(cents: Long): String {
            val isWhole = (cents % 100L) == 0L
            return if (isWhole) {
                (cents / 100L).toString()
            } else {
                val reais = cents / 100L
                val remainder = kotlin.math.abs(cents % 100L).toString().padStart(2, '0')
                "$reais,$remainder"
            }
        }

        fun parseBrlInputToCents(input: String): Long? {
            val cleaned = input.trim().removePrefix("R$").trim().replace(',', '.')
            if (cleaned.isBlank()) return null
            val valueDouble = cleaned.toDoubleOrNull() ?: return null
            if (!valueDouble.isFinite() || valueDouble < 0.0) return null
            return kotlin.math.round(valueDouble * 100.0).toLong()
        }
    }
}
