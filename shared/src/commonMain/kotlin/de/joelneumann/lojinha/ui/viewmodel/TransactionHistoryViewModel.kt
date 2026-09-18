package de.joelneumann.lojinha.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.joelneumann.lojinha.domain.model.AvatarType
import de.joelneumann.lojinha.domain.model.Language
import de.joelneumann.lojinha.domain.model.SecondaryCurrency
import de.joelneumann.lojinha.domain.model.Transaction
import de.joelneumann.lojinha.domain.model.TransactionType
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.domain.model.UserAvatarConfig
import de.joelneumann.lojinha.domain.repository.TransactionRepository
import de.joelneumann.lojinha.domain.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TransactionHistoryViewModel(
    private val transactionRepository: TransactionRepository,
    private val userRepository: UserRepository,
    coroutineScope: CoroutineScope? = null
) : ViewModel() {

    private val activeScope = coroutineScope ?: viewModelScope

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()

    private val _searchFilter = MutableStateFlow("")
    val searchFilter: StateFlow<String> = _searchFilter.asStateFlow()

    private val _selectedTypeFilter = MutableStateFlow<TransactionType?>(null)
    val selectedTypeFilter: StateFlow<TransactionType?> = _selectedTypeFilter.asStateFlow()

    private val _showSettingsModal = MutableStateFlow(false)
    val showSettingsModal: StateFlow<Boolean> = _showSettingsModal.asStateFlow()

    private val _pinInput = MutableStateFlow("")
    val pinInput: StateFlow<String> = _pinInput.asStateFlow()

    private val _selectedLanguage = MutableStateFlow(Language.DE)
    val selectedLanguage: StateFlow<Language> = _selectedLanguage.asStateFlow()

    private val _selectedSecondaryCurrency = MutableStateFlow(SecondaryCurrency.NONE)
    val selectedSecondaryCurrency: StateFlow<SecondaryCurrency> = _selectedSecondaryCurrency.asStateFlow()

    private val _selectedAvatar = MutableStateFlow(UserAvatarConfig())
    val selectedAvatar: StateFlow<UserAvatarConfig> = _selectedAvatar.asStateFlow()

    private var currentUserId: String? = null

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _pageSize = MutableStateFlow(25)
    val pageSize: StateFlow<Int> = _pageSize.asStateFlow()

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()

    private val _totalPages = MutableStateFlow(1)
    val totalPages: StateFlow<Int> = _totalPages.asStateFlow()

    private val _relatedTransactionsMap = MutableStateFlow<Map<String, Transaction>>(emptyMap())
    val relatedTransactionsMap: StateFlow<Map<String, Transaction>> = _relatedTransactionsMap.asStateFlow()

    fun loadUserTransactions(userId: String, resetFilters: Boolean = true) {
        currentUserId = userId
        if (resetFilters) {
            _searchFilter.value = ""
            _selectedTypeFilter.value = null
            _showSettingsModal.value = false
        }
        _currentPage.value = 0
        fetchPage()
    }

    fun updateSearchFilter(query: String) {
        _searchFilter.value = query
        _currentPage.value = 0
        fetchPage()
    }

    fun selectTypeFilter(type: TransactionType?) {
        _selectedTypeFilter.value = type
        _currentPage.value = 0
        fetchPage()
    }

    fun setPage(page: Int) {
        if (page in 0 until _totalPages.value) {
            _currentPage.value = page
            fetchPage()
        }
    }

    fun setPageSize(size: Int) {
        _pageSize.value = size
        _currentPage.value = 0
        fetchPage()
    }

    private var fetchJob: Job? = null

    private fun fetchPage() {
        val userId = currentUserId ?: return
        fetchJob?.cancel()
        fetchJob = activeScope.launch {
            try {
                val paged = transactionRepository.getTransactionsByUserIdPaged(
                    userId = userId,
                    page = _currentPage.value,
                    pageSize = _pageSize.value,
                    searchQuery = _searchFilter.value,
                    typeFilter = _selectedTypeFilter.value
                )
                val items = paged.items
                _transactions.value = items
                _totalCount.value = paged.totalCount
                _totalPages.value = paged.totalPages
                _currentPage.value = paged.page

                val refIds = items.mapNotNull { it.referenceTransactionId }.toSet()
                val parentTxs = if (refIds.isNotEmpty()) transactionRepository.getTransactionsByIds(refIds.toList()) else emptyList()

                val txIds = items.map { it.id }
                val childTxs = if (txIds.isNotEmpty()) transactionRepository.getTransactionsByReferenceIds(txIds) else emptyList()

                _relatedTransactionsMap.value = (parentTxs + childTxs).associateBy { it.id }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Normal cancellation when user updates filter/page rapidly
            }
        }
    }

    fun openSettingsModal(user: User) {
        _pinInput.value = ""
        _selectedLanguage.value = user.language
        _selectedSecondaryCurrency.value = user.secondaryCurrency
        _selectedAvatar.value = user.avatar
        _showSettingsModal.value = true
    }

    fun closeSettingsModal() {
        _showSettingsModal.value = false
    }

    fun updatePinInput(pin: String) {
        _pinInput.value = pin
    }

    fun updateLanguage(language: Language) {
        _selectedLanguage.value = language
    }

    fun updateSecondaryCurrency(currency: SecondaryCurrency) {
        _selectedSecondaryCurrency.value = currency
    }

    fun updateAvatar(avatar: UserAvatarConfig) {
        _selectedAvatar.value = avatar
    }

    fun saveUserSettings(user: User, onSaved: (User) -> Unit) {
        val updatedPin = if (_pinInput.value.isNotBlank()) {
            de.joelneumann.lojinha.security.PasswordHasher.hash(_pinInput.value.trim())
        } else {
            user.pin
        }
        val updated = user.copy(
            pin = updatedPin,
            language = _selectedLanguage.value,
            secondaryCurrency = _selectedSecondaryCurrency.value,
            avatar = _selectedAvatar.value
        )
        activeScope.launch {
            userRepository.saveUser(updated)
            _showSettingsModal.value = false
            onSaved(updated)
        }
    }

    fun resetFilters() {
        _searchFilter.value = ""
        _selectedTypeFilter.value = null
        _currentPage.value = 0
        _showSettingsModal.value = false
        fetchPage()
    }

    override fun onCleared() {
        super.onCleared()
        fetchJob?.cancel()
        _transactions.value = emptyList()
        _searchFilter.value = ""
        _selectedTypeFilter.value = null
        _currentPage.value = 0
        _showSettingsModal.value = false
    }
}
