package de.joelneumann.lojinha.ui.viewmodel.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.joelneumann.lojinha.domain.model.*
import de.joelneumann.lojinha.domain.repository.ProductRepository
import de.joelneumann.lojinha.domain.repository.TransactionRepository
import de.joelneumann.lojinha.domain.repository.UserRepository
import de.joelneumann.lojinha.ui.utils.Formatting
import de.joelneumann.lojinha.ui.utils.generateUuid
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

class AdminTransactionsViewModel(
    private val transactionRepository: TransactionRepository,
    private val userRepository: UserRepository,
    private val productRepository: ProductRepository
) : ViewModel() {

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()

    private val _searchFilter = MutableStateFlow("")
    val searchFilter: StateFlow<String> = _searchFilter.asStateFlow()

    private val _selectedTypeFilter = MutableStateFlow<TransactionType?>(null)
    val selectedTypeFilter: StateFlow<TransactionType?> = _selectedTypeFilter.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _pageSize = MutableStateFlow(25)
    val pageSize: StateFlow<Int> = _pageSize.asStateFlow()

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()

    private val _totalPages = MutableStateFlow(1)
    val totalPages: StateFlow<Int> = _totalPages.asStateFlow()

    private val _relatedChildrenMap = MutableStateFlow<Map<String, List<Transaction>>>(emptyMap())
    val relatedChildrenMap: StateFlow<Map<String, List<Transaction>>> = _relatedChildrenMap.asStateFlow()

    private val _referencedParentsMap = MutableStateFlow<Map<String, Transaction>>(emptyMap())
    val referencedParentsMap: StateFlow<Map<String, Transaction>> = _referencedParentsMap.asStateFlow()

    private val _correctionTarget = MutableStateFlow<Pair<Transaction, List<TransactionItem>>?>(null)
    val correctionTarget: StateFlow<Pair<Transaction, List<TransactionItem>>?> = _correctionTarget.asStateFlow()

    init {
        loadData()
        observeTransactions()
    }

    private fun observeTransactions() {
        viewModelScope.launch {
            transactionRepository.getTransactionsFlow().collect {
                fetchPagedTransactions()
            }
        }
    }

    fun loadData() {
        fetchPagedTransactions()
    }

    fun updateSearchFilter(query: String) {
        _searchFilter.value = query
        _currentPage.value = 0
        fetchPagedTransactions()
    }

    fun updateTypeFilter(type: TransactionType?) {
        _selectedTypeFilter.value = type
        _currentPage.value = 0
        fetchPagedTransactions()
    }

    fun setPage(page: Int) {
        if (page >= 0 && page < _totalPages.value) {
            _currentPage.value = page
            fetchPagedTransactions()
        }
    }

    fun setPageSize(size: Int) {
        _pageSize.value = size
        _currentPage.value = 0
        fetchPagedTransactions()
    }

    fun openCorrectionModal(originalTx: Transaction, currentEffectiveItems: List<TransactionItem>) {
        _correctionTarget.value = originalTx to currentEffectiveItems
    }

    fun closeCorrectionModal() {
        _correctionTarget.value = null
    }

    private var fetchJob: Job? = null

    private fun fetchPagedTransactions() {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            try {
                val paged = transactionRepository.getTransactionsPaged(
                    page = _currentPage.value,
                    pageSize = _pageSize.value,
                    searchQuery = _searchFilter.value,
                    typeFilter = _selectedTypeFilter.value
                )
                val items = paged.items
                val parentIds = items.map { it.id }
                val refIds = items.mapNotNull { it.referenceTransactionId }
                val allParentIds = (parentIds + refIds).distinct()
                val children = transactionRepository.getTransactionsByReferenceIds(allParentIds)
                val parents = transactionRepository.getTransactionsByIds(refIds)

                _relatedChildrenMap.value = children.groupBy { it.referenceTransactionId!! }
                _referencedParentsMap.value = parents.associateBy { it.id }
                _transactions.value = items
                _totalCount.value = paged.totalCount
                _totalPages.value = paged.totalPages
                _currentPage.value = paged.page
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Normal cancellation when filter or page changes rapidly
            } catch (e: Exception) {
                // Ignore or log error
            }
        }
    }

    fun refreshTransactions() {
        fetchPagedTransactions()
    }

    fun applyPurchaseCorrection(
        originalTx: Transaction,
        currentItems: List<TransactionItem>,
        newItems: List<TransactionItem>
    ) {
        val nowMillis = de.joelneumann.lojinha.ui.utils.currentTimeMillis()
        val isAllZero = newItems.all { it.quantity == 0L }

        val currentCost = currentItems.sumOf { item ->
            when (item.unitType) {
                UnitType.PIECE -> item.unitPriceAtPurchase * item.quantity
                UnitType.WEIGHT -> kotlin.math.round((item.unitPriceAtPurchase * item.quantity) / 1000.0).toLong()
            }
        }
        val newCost = newItems.sumOf { item ->
            when (item.unitType) {
                UnitType.PIECE -> item.unitPriceAtPurchase * item.quantity
                UnitType.WEIGHT -> kotlin.math.round((item.unitPriceAtPurchase * item.quantity) / 1000.0).toLong()
            }
        }
        // Positive costDifference means newCost > currentCost (charge). balanceDelta is negative.
        // Negative costDifference means newCost < currentCost (refund). balanceDelta is positive.
        val costDifference = newCost - currentCost
        val balanceDelta = -costDifference

        val updatedItems = newItems.filter { newItem ->
            val currentItem = currentItems.firstOrNull { it.productId == newItem.productId }
            val currentQty = currentItem?.quantity ?: 0L
            newItem.quantity != currentQty
        }.map { newItem ->
            val currentItem = currentItems.firstOrNull { it.productId == newItem.productId }
            newItem.copy(previousQuantity = currentItem?.quantity ?: 0L)
        }

        if (updatedItems.isEmpty()) {
            closeCorrectionModal()
            return
        }

        val dateStr = Formatting.formatTimestamp(originalTx.timestamp, Language.EN)
        val origAmountStr = Formatting.formatBrl(kotlin.math.abs(originalTx.totalAmount))
        val humanNote = if (isAllZero) {
            "Complete Storno of Purchase ($dateStr - $origAmountStr)"
        } else {
            "Item quantity correction for Purchase ($dateStr - Original $origAmountStr)"
        }

        val correctionTx = Transaction(
            id = generateUuid(),
            userId = originalTx.userId,
            userNameSnapshot = originalTx.userNameSnapshot,
            timestamp = nowMillis,
            type = if (isAllZero) TransactionType.CANCELLATION else TransactionType.CORRECTION,
            referenceTransactionId = originalTx.id,
            note = humanNote,
            totalAmount = balanceDelta,
            items = updatedItems
        )

        viewModelScope.launch {
            try {
                if (balanceDelta != 0L) {
                    userRepository.updateBalance(originalTx.userId, balanceDelta)
                }

                newItems.forEach { newItem ->
                    val currentItem = currentItems.firstOrNull { it.productId == newItem.productId }
                    val currentQty = currentItem?.quantity ?: 0L
                    val qtyChange = newItem.quantity - currentQty
                    if (qtyChange != 0L) {
                        val p = productRepository.getProductById(newItem.productId)
                        if (p != null) {
                            productRepository.updateStock(newItem.productId, -qtyChange)
                        }
                    }
                }

                transactionRepository.recordTransaction(correctionTx)
                closeCorrectionModal()
                fetchPagedTransactions()
            } catch (e: Exception) {
                // Log or handle error
            }
        }
    }

    fun stornoNonPurchaseTransaction(tx: Transaction) {
        if (tx.type == TransactionType.CANCELLATION || tx.type == TransactionType.CORRECTION) return
        val nowMillis = de.joelneumann.lojinha.ui.utils.currentTimeMillis()
        val cancellationId = generateUuid()

        val refundAmount = -tx.totalAmount
        val dateStr = Formatting.formatTimestamp(tx.timestamp, Language.EN)
        val amountStr = Formatting.formatBrl(kotlin.math.abs(tx.totalAmount))
        val typeLabel = when (tx.type) {
            TransactionType.ADMIN_DEPOSIT -> "Deposit"
            TransactionType.ADMIN_WITHDRAWAL -> if (tx.items.isNotEmpty()) "Custom Expense" else "Debit"
            else -> tx.type.name
        }

        val stornoTx = Transaction(
            id = cancellationId,
            userId = tx.userId,
            userNameSnapshot = tx.userNameSnapshot,
            timestamp = nowMillis,
            type = TransactionType.CANCELLATION,
            referenceTransactionId = tx.id,
            note = "Storno of $typeLabel ($dateStr - $amountStr)",
            totalAmount = refundAmount,
            items = emptyList()
        )

        viewModelScope.launch {
            try {
                userRepository.updateBalance(tx.userId, refundAmount)
                transactionRepository.recordTransaction(stornoTx)
                fetchPagedTransactions()
            } catch (e: Exception) {
                // Log or handle error
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        _transactions.value = emptyList()
        _relatedChildrenMap.value = emptyMap()
        _referencedParentsMap.value = emptyMap()
        _correctionTarget.value = null
        _searchFilter.value = ""
        _selectedTypeFilter.value = null
    }

    companion object {
        fun computeEffectiveItems(
            originalItems: List<TransactionItem>,
            corrections: List<Transaction>,
            cancellation: Transaction? = null
        ): List<TransactionItem> {
            if (cancellation != null) {
                return originalItems.map { it.copy(quantity = 0L) }
            }
            if (corrections.isEmpty()) return originalItems

            val itemsMap = originalItems.associateBy { it.productId }.toMutableMap()
            val sortedCorrections = corrections.sortedBy { it.timestamp }
            for (corr in sortedCorrections) {
                for (item in corr.items) {
                    val existing = itemsMap[item.productId]
                    if (existing != null) {
                        itemsMap[item.productId] = existing.copy(quantity = item.quantity)
                    }
                }
            }
            return originalItems.map { itemsMap[it.productId] ?: it }
        }
    }
}
