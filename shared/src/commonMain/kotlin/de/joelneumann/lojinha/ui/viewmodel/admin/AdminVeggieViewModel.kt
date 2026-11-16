package de.joelneumann.lojinha.ui.viewmodel.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.joelneumann.lojinha.domain.model.*
import de.joelneumann.lojinha.domain.repository.SettingsRepository
import de.joelneumann.lojinha.domain.repository.UserRepository
import de.joelneumann.lojinha.domain.repository.VeggieOrderRepository
import de.joelneumann.lojinha.domain.service.NextAutoRoundInfo
import de.joelneumann.lojinha.domain.service.VeggieAutoScheduleCalculator
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
import kotlinx.serialization.Serializable

@Serializable
data class AdminVeggieMatrixDraft(
    val actualQuantities: Map<String, String> = emptyMap(),
    val marketPrices: Map<String, String> = emptyMap(),
    val totalSpent: Map<String, String> = emptyMap(),
    val manuallyEditedTotalSpentProductIds: Set<String> = emptySet(),
    val buyerFeeInput: String = "0",
    val transportFeeInput: String = "0",
    val transporterUserId: String? = null
)

enum class AdminVeggieSubTab {
    ACTIVE_ROUND,
    CATALOG,
    HISTORY
}

enum class AddOrderEntryMode {
    OVERWRITE,
    ADD
}

class AdminVeggieViewModel(
    private val veggieOrderRepository: VeggieOrderRepository,
    private val userRepository: UserRepository,
    private val settingsRepository: SettingsRepository? = null,
    private val onSaveProductImage: (suspend (productId: String, productName: String, bytes: ByteArray, previousFileName: String?) -> String?)? = null,
    private val onDeleteProductImage: (suspend (productId: String, previousFileName: String?) -> Unit)? = null,
    private val onLoadMatrixDraft: ((roundId: String) -> AdminVeggieMatrixDraft?)? = null,
    private val onSaveMatrixDraft: ((roundId: String, draft: AdminVeggieMatrixDraft) -> Unit)? = null,
    private val onClearMatrixDraft: ((roundId: String) -> Unit)? = null,
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

    // Pagination for past settled rounds (history tab)
    private val _historyCurrentPage = MutableStateFlow(0)
    val historyCurrentPage: StateFlow<Int> = _historyCurrentPage.asStateFlow()

    private val _historyPageSize = MutableStateFlow(10)
    val historyPageSize: StateFlow<Int> = _historyPageSize.asStateFlow()

    private val _historyTotalCount = MutableStateFlow(0)
    val historyTotalCount: StateFlow<Int> = _historyTotalCount.asStateFlow()

    private val _historyTotalPages = MutableStateFlow(1)
    val historyTotalPages: StateFlow<Int> = _historyTotalPages.asStateFlow()

    private val _pagedSettledRounds = MutableStateFlow<List<VeggieOrderRound>>(emptyList())
    val pagedSettledRounds: StateFlow<List<VeggieOrderRound>> = _pagedSettledRounds.asStateFlow()

    private val _activeRound = MutableStateFlow<VeggieOrderRound?>(null)
    val activeRound: StateFlow<VeggieOrderRound?> = _activeRound.asStateFlow()

    private val _editingPastRound = MutableStateFlow<VeggieOrderRound?>(null)
    val editingPastRound: StateFlow<VeggieOrderRound?> = _editingPastRound.asStateFlow()

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

    private val _pdfExportResultPath = MutableStateFlow<String?>(null)
    val pdfExportResultPath: StateFlow<String?> = _pdfExportResultPath.asStateFlow()

    private val _systemSettings = MutableStateFlow(SystemSettings())
    val systemSettings: StateFlow<SystemSettings> = _systemSettings.asStateFlow()

    private val _nextAutoRoundSchedule = MutableStateFlow<NextAutoRoundInfo?>(null)
    val nextAutoRoundSchedule: StateFlow<NextAutoRoundInfo?> = _nextAutoRoundSchedule.asStateFlow()

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
                var allUnits = veggieOrderRepository.getAllUnitTypes()
                val allProducts = veggieOrderRepository.getAllVeggieProducts()
                val usedUnitIds = allProducts.filter { !it.isDeleted }.map { it.unitTypeId }.toSet()
                val unitsToReactivate = allUnits.filter { it.id in usedUnitIds && !it.isActive }
                if (unitsToReactivate.isNotEmpty()) {
                    unitsToReactivate.forEach { unit ->
                        veggieOrderRepository.saveUnitType(unit.copy(isActive = true, updatedAt = currentTimeMillis()))
                    }
                    allUnits = veggieOrderRepository.getAllUnitTypes()
                }
                val allUsers = userRepository.getAllUsers().filter { it.isActive && !it.isDeleted }.sortedByAccentInsensitive { it.name }
                val allRounds = veggieOrderRepository.getAllRounds().sortedByDescending { it.openedAt }
                val settings = settingsRepository?.getSettings() ?: SystemSettings()

                _unitTypes.value = allUnits
                _products.value = allProducts
                _users.value = allUsers
                _rounds.value = allRounds
                updatePagedSettledRounds()
                _systemSettings.value = settings
                _nextAutoRoundSchedule.value = VeggieAutoScheduleCalculator.getNextAutoRoundSchedule(
                    settings = settings,
                    nowMillis = currentTimeMillis()
                )

                val currentActive = allRounds.firstOrNull { it.status != VeggieRoundStatus.SETTLED }
                _activeRound.value = currentActive

                val editingId = _editingPastRound.value?.id
                if (editingId != null) {
                    val updatedEditingRound = allRounds.find { it.id == editingId }
                    if (updatedEditingRound != null) {
                        _editingPastRound.value = updatedEditingRound
                        loadRoundDetailsInternal(updatedEditingRound, resetDraft = !(preserveDraftEdits && _hasUnsavedSettlementChanges.value))
                    } else {
                        _editingPastRound.value = null
                        if (currentActive != null) {
                            loadRoundDetailsInternal(currentActive, resetDraft = true)
                        } else {
                            clearSelectedRoundState()
                        }
                    }
                } else {
                    if (currentActive != null) {
                        val isSame = _selectedRound.value?.id == currentActive.id
                        loadRoundDetailsInternal(currentActive, resetDraft = !(isSame && preserveDraftEdits && _hasUnsavedSettlementChanges.value))
                    } else {
                        clearSelectedRoundState()
                    }
                }
            } catch (e: Exception) {
                AppLogger.error("AdminVeggieViewModel", "Failed to load veggie data: ${e.message}", e)
            }
        }
    }

    private fun clearSelectedRoundState() {
        _selectedRound.value = null
        _selectedRoundEntries.value = emptyList()
        _selectedRoundSettlements.value = emptyMap()
        _liveSettlementPreview.value = null
        _hasUnsavedSettlementChanges.value = false
        _draftActualQuantities.value = emptyMap()
        _draftMarketPrices.value = emptyMap()
        _draftTotalSpent.value = emptyMap()
        _manuallyEditedTotalSpentProductIds.value = emptySet()
        _draftBuyerFeeInput.value = "0"
        _draftTransportFeeInput.value = "0"
        _draftTransporterUserId.value = null
    }

    fun openPastRoundForEditing(roundId: String) {
        activeScope.launch {
            val round = _rounds.value.find { it.id == roundId } ?: veggieOrderRepository.getRoundById(roundId) ?: return@launch
            _editingPastRound.value = round
            loadRoundDetailsInternal(round, resetDraft = true)
        }
    }

    fun closePastRoundEditor() {
        _editingPastRound.value = null
        _hasUnsavedSettlementChanges.value = false
        val active = _activeRound.value
        if (active != null) {
            activeScope.launch {
                loadRoundDetailsInternal(active, resetDraft = true)
            }
        } else {
            clearSelectedRoundState()
        }
    }

    fun setHistoryPage(page: Int) {
        val total = _historyTotalPages.value
        if (page in 0 until total) {
            _historyCurrentPage.value = page
            updatePagedSettledRounds()
        }
    }

    fun setHistoryPageSize(size: Int) {
        if (size > 0) {
            _historyPageSize.value = size
            _historyCurrentPage.value = 0
            updatePagedSettledRounds()
        }
    }

    private fun updatePagedSettledRounds() {
        val settled = _rounds.value.filter { it.status == VeggieRoundStatus.SETTLED }
        val count = settled.size
        _historyTotalCount.value = count
        val size = _historyPageSize.value
        val pages = if (count == 0) 1 else kotlin.math.ceil(count.toDouble() / size).toInt()
        _historyTotalPages.value = pages

        if (_historyCurrentPage.value >= pages) {
            _historyCurrentPage.value = maxOf(0, pages - 1)
        }

        val start = _historyCurrentPage.value * size
        _pagedSettledRounds.value = if (start < count) {
            settled.drop(start).take(size)
        } else {
            emptyList()
        }
    }

    fun discardDraft() {
        _hasUnsavedSettlementChanges.value = false
        val editing = _editingPastRound.value
        if (editing != null) {
            activeScope.launch {
                loadRoundDetailsInternal(editing, resetDraft = true)
            }
            return
        }
        val active = _activeRound.value
        if (active != null) {
            activeScope.launch {
                loadRoundDetailsInternal(active, resetDraft = true)
            }
        } else {
            clearSelectedRoundState()
        }
    }

    fun selectRound(roundId: String, switchToRoundTab: Boolean = true) {
        openPastRoundForEditing(roundId)
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

            val localDraft = onLoadMatrixDraft?.invoke(round.id)
            if (localDraft != null) {
                if (localDraft.actualQuantities.isNotEmpty()) {
                    _draftActualQuantities.value = _draftActualQuantities.value + localDraft.actualQuantities
                }
                if (localDraft.marketPrices.isNotEmpty()) {
                    _draftMarketPrices.value = _draftMarketPrices.value + localDraft.marketPrices
                }
                if (localDraft.totalSpent.isNotEmpty()) {
                    _draftTotalSpent.value = _draftTotalSpent.value + localDraft.totalSpent
                }
                if (localDraft.manuallyEditedTotalSpentProductIds.isNotEmpty()) {
                    _manuallyEditedTotalSpentProductIds.value =
                        _manuallyEditedTotalSpentProductIds.value + localDraft.manuallyEditedTotalSpentProductIds
                }
                _draftBuyerFeeInput.value = localDraft.buyerFeeInput
                _draftTransportFeeInput.value = localDraft.transportFeeInput
                _draftTransporterUserId.value = localDraft.transporterUserId
                _hasUnsavedSettlementChanges.value = true
            } else {
                _hasUnsavedSettlementChanges.value = false
            }
        }
        recomputeLivePreview()
    }

    private fun persistCurrentMatrixDraft() {
        val roundId = _selectedRound.value?.id ?: return
        onSaveMatrixDraft?.invoke(
            roundId,
            AdminVeggieMatrixDraft(
                actualQuantities = _draftActualQuantities.value,
                marketPrices = _draftMarketPrices.value,
                totalSpent = _draftTotalSpent.value,
                manuallyEditedTotalSpentProductIds = _manuallyEditedTotalSpentProductIds.value,
                buyerFeeInput = _draftBuyerFeeInput.value,
                transportFeeInput = _draftTransportFeeInput.value,
                transporterUserId = _draftTransporterUserId.value
            )
        )
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
        persistCurrentMatrixDraft()
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
        persistCurrentMatrixDraft()
    }

    fun updateProductTotalSpent(productId: String, input: String) {
        val newSpentMap = _draftTotalSpent.value.toMutableMap()
        newSpentMap[productId] = input
        _draftTotalSpent.value = newSpentMap
        _manuallyEditedTotalSpentProductIds.value = _manuallyEditedTotalSpentProductIds.value + productId
        _hasUnsavedSettlementChanges.value = true
        recomputeLivePreview()
        persistCurrentMatrixDraft()
    }

    fun updateBuyerFeeInput(input: String) {
        _draftBuyerFeeInput.value = input
        _hasUnsavedSettlementChanges.value = true
        recomputeLivePreview()
        persistCurrentMatrixDraft()
    }

    fun updateTransportFeeInput(input: String) {
        _draftTransportFeeInput.value = input
        _hasUnsavedSettlementChanges.value = true
        recomputeLivePreview()
        persistCurrentMatrixDraft()
    }

    fun updateTransporterUserId(userId: String?) {
        _draftTransporterUserId.value = userId
        _hasUnsavedSettlementChanges.value = true
        recomputeLivePreview()
        persistCurrentMatrixDraft()
    }

    private fun calculateDraftTotalDeliveredForProduct(
        productId: String,
        quantitiesMap: Map<String, String>
    ): Long {
        val product = _products.value.find { it.id == productId }
        val deliveryUnit = product?.deliveryUnitTypeId?.let { did -> _unitTypes.value.find { it.id == did } }
        return _selectedRoundEntries.value
            .filter { it.veggieProductId == productId }
            .sumOf { entry ->
                val raw = quantitiesMap[entry.id]
                val allowDecimals = deliveryUnit?.allowDecimals
                    ?: entry.deliveryAllowDecimalsSnapshot
                    ?: entry.allowDecimalsSnapshot
                if (raw != null) {
                    VeggieQuantityScaling.parseQuantityScaled(raw, allowDecimals) ?: entry.deliveredQuantityScaled
                } else {
                    entry.deliveredQuantityScaled
                }
            }
    }

    private fun buildCurrentDraftPayload(): DraftSettlementPayload? {
        val round = _selectedRound.value ?: return null
        val entries = _selectedRoundEntries.value
        val productsById = _products.value.associateBy { it.id }
        val unitTypesById = _unitTypes.value.associateBy { it.id }

        val actualByEntryId = entries.associate { entry ->
            val raw = _draftActualQuantities.value[entry.id]
            val product = productsById[entry.veggieProductId]
            val deliveryUnit = product?.deliveryUnitTypeId?.let { unitTypesById[it] }
            val allowDecimals = deliveryUnit?.allowDecimals
                ?: entry.deliveryAllowDecimalsSnapshot
                ?: entry.allowDecimalsSnapshot
            val parsed = if (raw != null) {
                VeggieQuantityScaling.parseQuantityScaled(raw, allowDecimals) ?: entry.deliveredQuantityScaled
            } else {
                entry.deliveredQuantityScaled
            }
            entry.id to parsed.coerceAtLeast(0L)
        }

        val entriesWithDraftQty = entries.map { entry ->
            val product = productsById[entry.veggieProductId]
            val deliveryUnit = product?.deliveryUnitTypeId?.let { unitTypesById[it] }
            entry.copy(
                deliveryUnitNameSnapshot = deliveryUnit?.name ?: entry.deliveryUnitNameSnapshot,
                deliveryAllowDecimalsSnapshot = deliveryUnit?.allowDecimals ?: entry.deliveryAllowDecimalsSnapshot,
                actualQuantityScaled = actualByEntryId[entry.id] ?: entry.deliveredQuantityScaled
            )
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
                _editingPastRound.value = null
                val settings = settingsRepository?.getSettings() ?: SystemSettings()
                val newRound = veggieOrderRepository.openNewRound(
                    buyerOperationFeeCents = settings.defaultVeggieBuyerFeeCents,
                    transportFeeCents = settings.defaultVeggieTransportFeeCents,
                    openedAt = currentTimeMillis()
                )
                _currentSubTab.value = AdminVeggieSubTab.ACTIVE_ROUND
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
        if (_selectedRoundEntries.value.isEmpty()) return
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

    fun saveDraftSettlement(onSuccess: (() -> Unit)? = null) {
        val payload = buildCurrentDraftPayload() ?: return
        if (payload.entriesWithDraftQty.isEmpty()) return
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
                    onClearMatrixDraft?.invoke(payload.round.id)
                    _statusMessage.value = I18n.get().saved
                    loadData(preserveDraftEdits = false)
                    onSuccess?.invoke()
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

    fun setExportResultPath(path: String?) {
        _pdfExportResultPath.value = path
    }

    fun dismissExportResultDialog() {
        _pdfExportResultPath.value = null
    }

    fun deleteCurrentRound() {
        val round = _selectedRound.value ?: return
        if (_isSaving.value) return
        _isSaving.value = true
        clearMessages()
        activeScope.launch {
            try {
                val ok = veggieOrderRepository.deleteRound(round.id)
                if (ok) {
                    _editingPastRound.value = null
                    _statusMessage.value = I18n.get().deleted
                    loadData(preserveDraftEdits = false)
                } else {
                    _errorMessage.value = "Could not delete round (may have existing transactions)"
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to delete round"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun markPdfExported(roundId: String, isBuyer: Boolean) {
        activeScope.launch {
            try {
                if (isBuyer) {
                    veggieOrderRepository.markBuyerPdfExported(roundId, currentTimeMillis())
                } else {
                    veggieOrderRepository.markWorkerPdfExported(roundId, currentTimeMillis())
                }
                loadData(preserveDraftEdits = true)
            } catch (e: Exception) {
                AppLogger.error("AdminVeggieViewModel", "Failed to mark PDF exported: ${e.message}", e)
            }
        }
    }

    fun executeSettlement() {
        val payload = buildCurrentDraftPayload() ?: return
        if (payload.entriesWithDraftQty.isEmpty()) return
        if (_isSaving.value) return
        _isSaving.value = true
        clearMessages()
        activeScope.launch {
            try {
                val isEditingPast = _editingPastRound.value != null
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
                    if (isEditingPast) {
                        _editingPastRound.value = null
                    }
                    _hasUnsavedSettlementChanges.value = false
                    onClearMatrixDraft?.invoke(payload.round.id)
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

    fun addOrUpdateCustomerEntry(
        user: User,
        product: VeggieProduct,
        quantityInput: String,
        mode: AddOrderEntryMode = AddOrderEntryMode.OVERWRITE
    ) {
        val round = _selectedRound.value ?: return
        val unit = _unitTypes.value.find { it.id == product.unitTypeId }
        val deliveryUnit = product.deliveryUnitTypeId?.let { did -> _unitTypes.value.find { it.id == did } }
        val allowDecimals = unit?.allowDecimals ?: false
        val qtyScaled = VeggieQuantityScaling.parseQuantityScaled(quantityInput, allowDecimals) ?: return
        if (qtyScaled <= 0L) return

        activeScope.launch {
            try {
                val existing = _selectedRoundEntries.value.find { it.userId == user.id && it.veggieProductId == product.id }
                val now = currentTimeMillis()

                val targetOrderedQty = when {
                    existing == null -> qtyScaled
                    round.status == VeggieRoundStatus.OPEN -> {
                        if (mode == AddOrderEntryMode.ADD) existing.orderedQuantityScaled + qtyScaled else qtyScaled
                    }
                    else -> existing.orderedQuantityScaled
                }

                val currentActual = existing?.actualQuantityScaled ?: existing?.orderedQuantityScaled ?: 0L
                val targetActualQty = when {
                    existing == null -> qtyScaled
                    mode == AddOrderEntryMode.ADD -> currentActual + qtyScaled
                    else -> qtyScaled
                }

                val entry = existing?.copy(
                    deliveryUnitNameSnapshot = deliveryUnit?.name ?: existing.deliveryUnitNameSnapshot,
                    deliveryAllowDecimalsSnapshot = deliveryUnit?.allowDecimals ?: existing.deliveryAllowDecimalsSnapshot,
                    orderedQuantityScaled = targetOrderedQty,
                    actualQuantityScaled = targetActualQty,
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
                    deliveryUnitNameSnapshot = deliveryUnit?.name,
                    deliveryAllowDecimalsSnapshot = deliveryUnit?.allowDecimals,
                    orderedQuantityScaled = targetOrderedQty,
                    actualQuantityScaled = targetActualQty,
                    updatedAt = now
                )
                veggieOrderRepository.upsertAdminOrderEntry(entry)

                if (existing != null && _draftActualQuantities.value.containsKey(existing.id)) {
                    _draftActualQuantities.value = _draftActualQuantities.value - existing.id
                }

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
                    val savedFileName = onSaveProductImage?.invoke(
                        product.id,
                        product.name,
                        newImageBytes,
                        product.imageFileName
                    )
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

    fun toggleVeggieProductActive(product: VeggieProduct, cancelOpenRoundOrders: Boolean = false) {
        activeScope.launch {
            try {
                if (product.isActive) {
                    veggieOrderRepository.deactivateVeggieProduct(product.id)
                    if (cancelOpenRoundOrders) {
                        val openRound = _activeRound.value
                        if (openRound != null && openRound.status == VeggieRoundStatus.OPEN) {
                            veggieOrderRepository.cancelProductEntriesInOpenRound(openRound.id, product.id)
                        }
                    }
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
                val isExisting = _unitTypes.value.any { it.id == unitType.id }
                val toSave = if (!isExisting) {
                    // Only for NEW units: reactivate matching inactive unit if one exists
                    val existingInactive = _unitTypes.value.find {
                        !it.isActive && it.name.equals(unitType.name.trim(), ignoreCase = true)
                    }
                    if (existingInactive != null) {
                        existingInactive.copy(
                            name = unitType.name.trim(),
                            allowDecimals = unitType.allowDecimals,
                            isActive = true,
                            updatedAt = currentTimeMillis()
                        )
                    } else {
                        unitType
                    }
                } else {
                    // For EXISTING units: keep the unit's ID and update its properties directly
                    unitType
                }
                veggieOrderRepository.saveUnitType(toSave)
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
