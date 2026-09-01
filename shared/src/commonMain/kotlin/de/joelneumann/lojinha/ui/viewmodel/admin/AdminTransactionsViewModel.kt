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

    init {
        loadData()
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
                _transactions.value = paged.items
                _totalCount.value = paged.totalCount
                _totalPages.value = paged.totalPages
                _currentPage.value = paged.page
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Normal cancellation when filter or page changes rapidly
            }
        }
    }

    private fun refreshTransactions() {
        fetchPagedTransactions()
    }

    fun stornoPurchaseWithUpdatedItems(originalTx: Transaction, updatedItems: List<TransactionItem>) {
        val nowMillis = de.joelneumann.lojinha.ui.utils.currentTimeMillis()
        val isAllZero = updatedItems.all { it.quantity == 0L }

        val originalCost = kotlin.math.abs(originalTx.totalAmount)
        val updatedCost = updatedItems.sumOf { item ->
            when (item.unitType) {
                UnitType.PIECE -> item.unitPriceAtPurchase * item.quantity
                UnitType.WEIGHT -> kotlin.math.round((item.unitPriceAtPurchase * item.quantity) / 1000.0).toLong()
            }
        }
        val costDifference = updatedCost - originalCost
        val balanceDelta = -costDifference

        val dateStr = Formatting.formatTimestamp(originalTx.timestamp, Language.EN)
        val origAmountStr = Formatting.formatBrl(originalCost)
        val humanNote = if (isAllZero) {
            "Complete Storno of Purchase ($dateStr - $origAmountStr)"
        } else {
            "Item quantity correction for Purchase ($dateStr - Original $origAmountStr)"
        }

        val stornoId = generateUuid()
        val stornoTx = Transaction(
            id = stornoId,
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
            userRepository.updateBalance(originalTx.userId, balanceDelta)

            updatedItems.forEach { updatedItem ->
                val originalItem = originalTx.items.firstOrNull { it.productId == updatedItem.productId }
                val originalQty = originalItem?.quantity ?: 0L
                val qtyChange = updatedItem.quantity - originalQty
                if (qtyChange != 0L) {
                    val p = productRepository.getProductById(updatedItem.productId)
                    if (p != null) {
                        productRepository.updateStock(updatedItem.productId, -qtyChange)
                    }
                }
            }

            transactionRepository.recordTransaction(stornoTx)
            refreshTransactions()
        }
    }

    fun stornoNonPurchaseTransaction(tx: Transaction) {
        if (tx.type == TransactionType.CANCELLATION) return
        val nowMillis = de.joelneumann.lojinha.ui.utils.currentTimeMillis()
        val cancellationId = generateUuid()

        val refundAmount = -tx.totalAmount
        val dateStr = Formatting.formatTimestamp(tx.timestamp, Language.EN)
        val amountStr = Formatting.formatBrl(tx.totalAmount)

        val stornoTx = Transaction(
            id = cancellationId,
            userId = tx.userId,
            userNameSnapshot = tx.userNameSnapshot,
            timestamp = nowMillis,
            type = TransactionType.CANCELLATION,
            referenceTransactionId = tx.id,
            note = "Storno of ${tx.type.name} ($dateStr - $amountStr)",
            totalAmount = refundAmount,
            items = emptyList()
        )

        viewModelScope.launch {
            userRepository.updateBalance(tx.userId, refundAmount)
            transactionRepository.recordTransaction(stornoTx)
            refreshTransactions()
        }
    }

    override fun onCleared() {
        super.onCleared()
        _transactions.value = emptyList()
        _searchFilter.value = ""
        _selectedTypeFilter.value = null
    }
}
