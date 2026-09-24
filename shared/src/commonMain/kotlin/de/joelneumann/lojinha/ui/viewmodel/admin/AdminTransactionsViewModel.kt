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

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

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

                _relatedChildrenMap.value = children
                    .filter { !it.referenceTransactionId.isNullOrBlank() }
                    .groupBy { it.referenceTransactionId!! }
                _referencedParentsMap.value = parents.associateBy { it.id }
                _transactions.value = items
                _totalCount.value = paged.totalCount
                _totalPages.value = paged.totalPages
                _currentPage.value = paged.page
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Normal cancellation when filter or page changes rapidly
            } catch (e: Exception) {
                de.joelneumann.lojinha.util.AppLogger.error("AdminTransactionsViewModel", "fetchPagedTransactions error: ${e.message}", e)
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
        if (_isSubmitting.value) return
        _isSubmitting.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val success = transactionRepository.applyPurchaseCorrection(originalTx.id, newItems)
                if (success) {
                    closeCorrectionModal()
                    fetchPagedTransactions()
                } else {
                    _errorMessage.value = "Failed to apply correction. Transaction may have already been canceled or quantities are unchanged."
                }
            } catch (e: Exception) {
                de.joelneumann.lojinha.util.AppLogger.error("AdminTransactionsViewModel", "applyPurchaseCorrection error: ${e.message}", e)
                _errorMessage.value = e.message ?: "An unexpected error occurred while applying correction."
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    fun stornoNonPurchaseTransaction(tx: Transaction) {
        if (tx.type != TransactionType.ADMIN_DEPOSIT && tx.type != TransactionType.ADMIN_WITHDRAWAL) return
        if (_isSubmitting.value) return
        _isSubmitting.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                if (transactionRepository.getCancellationCountForReference(tx.id) > 0) {
                    _errorMessage.value = "This transaction has already been canceled."
                    return@launch
                }
                val success = transactionRepository.stornoNonPurchase(tx.id)
                if (success) {
                    fetchPagedTransactions()
                } else {
                    _errorMessage.value = "Failed to storno transaction. It may have already been canceled."
                }
            } catch (e: Exception) {
                de.joelneumann.lojinha.util.AppLogger.error("AdminTransactionsViewModel", "stornoNonPurchaseTransaction error: ${e.message}", e)
                _errorMessage.value = e.message ?: "An unexpected error occurred while canceling transaction."
            } finally {
                _isSubmitting.value = false
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
        _errorMessage.value = null
    }

    companion object {
        fun computeEffectiveItems(
            originalItems: List<TransactionItem>,
            corrections: List<Transaction>,
            cancellation: Transaction? = null
        ): List<TransactionItem> = Transaction.computeEffectiveItems(originalItems, corrections, cancellation)
    }
}

