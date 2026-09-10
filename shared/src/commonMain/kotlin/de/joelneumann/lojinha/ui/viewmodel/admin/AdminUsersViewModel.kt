package de.joelneumann.lojinha.ui.viewmodel.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.joelneumann.lojinha.domain.model.Language
import de.joelneumann.lojinha.domain.model.Transaction
import de.joelneumann.lojinha.domain.model.TransactionItem
import de.joelneumann.lojinha.domain.model.TransactionType
import de.joelneumann.lojinha.domain.model.UnitType
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.domain.repository.TransactionRepository
import de.joelneumann.lojinha.domain.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import de.joelneumann.lojinha.ui.utils.generateUuid
import de.joelneumann.lojinha.ui.utils.sortedByAccentInsensitive
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

    private val _customExpenseUser = MutableStateFlow<User?>(null)
    val customExpenseUser: StateFlow<User?> = _customExpenseUser.asStateFlow()

    private val _customIncomeUser = MutableStateFlow<User?>(null)
    val customIncomeUser: StateFlow<User?> = _customIncomeUser.asStateFlow()

    private val _userDeleteErrorMessage = MutableStateFlow<String?>(null)
    val userDeleteErrorMessage: StateFlow<String?> = _userDeleteErrorMessage.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            userRepository.getUsersFlow().collect { _users.value = it.sortedByAccentInsensitive { u -> u.name } }
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

    fun openCustomExpenseModal(user: User) {
        _customExpenseUser.value = user
    }

    fun closeCustomExpenseModal() {
        _customExpenseUser.value = null
    }

    fun submitCustomExpense(user: User, amountCents: Long, description: String) {
        val absCents = kotlin.math.abs(amountCents)
        val deltaCents = -absCents
        val txType = TransactionType.ADMIN_WITHDRAWAL
        val nowMillis = de.joelneumann.lojinha.ui.utils.currentTimeMillis()
        val txId = generateUuid()

        val items = listOf(
            TransactionItem(
                productId = "custom",
                productName = description,
                unitType = UnitType.PIECE,
                quantity = 1L,
                unitPriceAtPurchase = absCents
            )
        )

        val balBefore = user.balance
        val balAfter = user.balance + deltaCents

        val tx = Transaction(
            id = txId,
            userId = user.id,
            userNameSnapshot = user.name,
            timestamp = nowMillis,
            type = txType,
            note = description,
            totalAmount = deltaCents,
            items = items,
            userBalanceBefore = balBefore,
            userBalanceAfter = balAfter
        )

        viewModelScope.launch {
            transactionRepository.executeAtomicTransaction(tx, deltaCents, emptyMap())
            refreshUsers()
            closeCustomExpenseModal()
        }
    }

    fun openCustomIncomeModal(user: User) {
        _customIncomeUser.value = user
    }

    fun closeCustomIncomeModal() {
        _customIncomeUser.value = null
    }

    fun submitCustomIncome(user: User, amountCents: Long, comment: String) {
        val absCents = kotlin.math.abs(amountCents)
        val deltaCents = absCents
        val txType = TransactionType.ADMIN_DEPOSIT
        val nowMillis = de.joelneumann.lojinha.ui.utils.currentTimeMillis()
        val txId = generateUuid()

        val items = listOf(
            TransactionItem(
                productId = "custom",
                productName = comment,
                unitType = UnitType.PIECE,
                quantity = 1L,
                unitPriceAtPurchase = absCents
            )
        )

        val balBefore = user.balance
        val balAfter = user.balance + deltaCents

        val tx = Transaction(
            id = txId,
            userId = user.id,
            userNameSnapshot = user.name,
            timestamp = nowMillis,
            type = txType,
            note = comment,
            totalAmount = deltaCents,
            items = items,
            userBalanceBefore = balBefore,
            userBalanceAfter = balAfter
        )

        viewModelScope.launch {
            transactionRepository.executeAtomicTransaction(tx, deltaCents, emptyMap())
            refreshUsers()
            closeCustomIncomeModal()
        }
    }

    fun adjustUserBalance(userId: String, userName: String, centsDelta: Long, note: String) {
        val isDeposit = centsDelta > 0
        val txType = if (isDeposit) TransactionType.ADMIN_DEPOSIT else TransactionType.ADMIN_WITHDRAWAL
        val nowMillis = de.joelneumann.lojinha.ui.utils.currentTimeMillis()
        val txId = generateUuid()

        viewModelScope.launch {
            val user = userRepository.getUserById(userId)
            val balBefore = user?.balance ?: 0L
            val balAfter = balBefore + centsDelta

            val tx = Transaction(
                id = txId,
                userId = userId,
                userNameSnapshot = userName,
                timestamp = nowMillis,
                type = txType,
                note = note.ifBlank { if (isDeposit) "Deposit via Admin" else "Debit via Admin" },
                totalAmount = centsDelta,
                items = emptyList(),
                userBalanceBefore = balBefore,
                userBalanceAfter = balAfter
            )

            transactionRepository.executeAtomicTransaction(tx, centsDelta, emptyMap())
            refreshUsers()
        }
    }

    override fun onCleared() {
        super.onCleared()
        _users.value = emptyList()
        _searchQuery.value = ""
        closeCustomExpenseModal()
        closeCustomIncomeModal()
    }
}
