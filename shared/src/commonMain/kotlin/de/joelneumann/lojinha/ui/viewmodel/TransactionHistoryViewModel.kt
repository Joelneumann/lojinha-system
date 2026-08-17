package de.joelneumann.lojinha.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.joelneumann.lojinha.domain.model.Language
import de.joelneumann.lojinha.domain.model.SecondaryCurrency
import de.joelneumann.lojinha.domain.model.Transaction
import de.joelneumann.lojinha.domain.model.TransactionType
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.domain.repository.TransactionRepository
import de.joelneumann.lojinha.domain.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TransactionHistoryViewModel(
    private val transactionRepository: TransactionRepository,
    private val userRepository: UserRepository
) : ViewModel() {

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

    fun loadUserTransactions(userId: String) {
        viewModelScope.launch {
            val list = transactionRepository.getTransactionsByUserId(userId)
            _transactions.value = list
        }
    }

    fun updateSearchFilter(query: String) {
        _searchFilter.value = query
    }

    fun selectTypeFilter(type: TransactionType?) {
        _selectedTypeFilter.value = type
    }

    fun openSettingsModal(user: User) {
        _pinInput.value = user.pin ?: ""
        _selectedLanguage.value = user.language
        _selectedSecondaryCurrency.value = user.secondaryCurrency
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

    fun saveUserSettings(user: User, onSaved: (User) -> Unit) {
        val updated = user.copy(
            pin = if (_pinInput.value.isBlank()) null else _pinInput.value.trim(),
            language = _selectedLanguage.value,
            secondaryCurrency = _selectedSecondaryCurrency.value
        )
        viewModelScope.launch {
            userRepository.saveUser(updated)
            _showSettingsModal.value = false
            onSaved(updated)
        }
    }
}
