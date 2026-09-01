package de.joelneumann.lojinha.ui.viewmodel.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.joelneumann.lojinha.domain.model.Language
import de.joelneumann.lojinha.domain.model.Transaction
import de.joelneumann.lojinha.domain.model.TransactionType
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.domain.repository.TransactionRepository
import de.joelneumann.lojinha.domain.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import de.joelneumann.lojinha.ui.utils.generateUuid
import kotlin.random.Random

class AdminUsersViewModel(
    private val userRepository: UserRepository,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: StateFlow<List<User>> = _users.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _editUser = MutableStateFlow<User?>(null)
    val editUser: StateFlow<User?> = _editUser.asStateFlow()

    private val _showUserModal = MutableStateFlow(false)
    val showUserModal: StateFlow<Boolean> = _showUserModal.asStateFlow()

    private val _depositUser = MutableStateFlow<User?>(null)
    val depositUser: StateFlow<User?> = _depositUser.asStateFlow()

    private val _depositAmountInput = MutableStateFlow("")
    val depositAmountInput: StateFlow<String> = _depositAmountInput.asStateFlow()

    private val _depositNoteInput = MutableStateFlow("")
    val depositNoteInput: StateFlow<String> = _depositNoteInput.asStateFlow()

    private val _userDeleteErrorMessage = MutableStateFlow<String?>(null)
    val userDeleteErrorMessage: StateFlow<String?> = _userDeleteErrorMessage.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            userRepository.getUsersFlow().collect { _users.value = it }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun openNewUserModal() {
        _editUser.value = User(
            id = generateUuid(),
            name = "",
            balance = 0L,
            language = Language.DE
        )
        _showUserModal.value = true
    }

    fun openEditUserModal(user: User) {
        _editUser.value = user
        _showUserModal.value = true
    }

    fun closeUserModal() {
        _showUserModal.value = false
        _editUser.value = null
    }

    private suspend fun refreshUsers() {
        _users.value = userRepository.getAllUsers()
    }

    fun saveUser(user: User) {
        viewModelScope.launch {
            userRepository.saveUser(user)
            refreshUsers()
            closeUserModal()
        }
    }

    fun toggleUserActive(user: User) {
        viewModelScope.launch {
            userRepository.saveUser(user.copy(isActive = !user.isActive))
            refreshUsers()
        }
    }

    fun softDeleteUser(userId: String) {
        viewModelScope.launch {
            userRepository.softDeleteUser(userId)
            refreshUsers()
        }
    }

    fun restoreUser(userId: String) {
        viewModelScope.launch {
            userRepository.restoreUser(userId)
            refreshUsers()
        }
    }

    fun clearUserDeleteError() {
        _userDeleteErrorMessage.value = null
    }

    fun openDepositModal(user: User) {
        _depositUser.value = user
        _depositAmountInput.value = ""
        _depositNoteInput.value = ""
    }

    fun closeDepositModal() {
        _depositUser.value = null
        _depositAmountInput.value = ""
        _depositNoteInput.value = ""
    }

    fun updateDepositAmount(amountStr: String) {
        _depositAmountInput.value = amountStr
    }

    fun updateDepositNote(note: String) {
        _depositNoteInput.value = note
    }

    fun submitDeposit(isDeposit: Boolean) {
        val user = _depositUser.value ?: return
        val rawInput = _depositAmountInput.value.replace(',', '.')
        val valDouble = rawInput.toDoubleOrNull() ?: return
        if (valDouble <= 0.0) return

        val cents = kotlin.math.round(valDouble * 100.0).toLong()
        val delta = if (isDeposit) cents else -cents
        val txType = if (isDeposit) TransactionType.ADMIN_DEPOSIT else TransactionType.ADMIN_WITHDRAWAL

        val nowMillis = de.joelneumann.lojinha.ui.utils.currentTimeMillis()
        val txId = generateUuid()

        val tx = Transaction(
            id = txId,
            userId = user.id,
            userNameSnapshot = user.name,
            timestamp = nowMillis,
            type = txType,
            note = _depositNoteInput.value.ifBlank { if (isDeposit) "Deposit via Admin" else "Withdrawal via Admin" },
            totalAmount = delta,
            items = emptyList()
        )

        viewModelScope.launch {
            userRepository.updateBalance(user.id, delta)
            transactionRepository.recordTransaction(tx)
            refreshUsers()
            closeDepositModal()
        }
    }

    fun adjustUserBalance(userId: String, userName: String, centsDelta: Long, note: String) {
        val isDeposit = centsDelta > 0
        val txType = if (isDeposit) TransactionType.ADMIN_DEPOSIT else TransactionType.ADMIN_WITHDRAWAL
        val nowMillis = de.joelneumann.lojinha.ui.utils.currentTimeMillis()
        val txId = generateUuid()

        val tx = Transaction(
            id = txId,
            userId = userId,
            userNameSnapshot = userName,
            timestamp = nowMillis,
            type = txType,
            note = note.ifBlank { if (isDeposit) "Deposit via Admin" else "Withdrawal via Admin" },
            totalAmount = centsDelta,
            items = emptyList()
        )

        viewModelScope.launch {
            userRepository.updateBalance(userId, centsDelta)
            transactionRepository.recordTransaction(tx)
            refreshUsers()
        }
    }
}
