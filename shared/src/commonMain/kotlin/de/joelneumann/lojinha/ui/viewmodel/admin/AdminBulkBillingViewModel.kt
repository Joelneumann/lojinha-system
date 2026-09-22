package de.joelneumann.lojinha.ui.viewmodel.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.joelneumann.lojinha.domain.model.*
import de.joelneumann.lojinha.domain.repository.BillingListRepository
import de.joelneumann.lojinha.domain.repository.TransactionRepository
import de.joelneumann.lojinha.domain.repository.UserRepository
import de.joelneumann.lojinha.ui.utils.currentTimeMillis
import de.joelneumann.lojinha.ui.utils.generateUuid
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AdminBulkBillingViewModel(
    private val billingListRepository: BillingListRepository,
    private val userRepository: UserRepository,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val _billingLists = MutableStateFlow<List<BillingList>>(emptyList())
    val billingLists: StateFlow<List<BillingList>> = _billingLists.asStateFlow()

    private val _activeUsers = MutableStateFlow<List<User>>(emptyList())
    val activeUsers: StateFlow<List<User>> = _activeUsers.asStateFlow()

    private val _selectedListId = MutableStateFlow<String?>(null)
    val selectedListId: StateFlow<String?> = _selectedListId

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isExecutingCharges = MutableStateFlow(false)
    val isExecutingCharges: StateFlow<Boolean> = _isExecutingCharges.asStateFlow()

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    private var listsJob: Job? = null
    private var usersJob: Job? = null

    init {
        loadData()
    }

    fun loadData() {
        listsJob?.cancel()
        listsJob = viewModelScope.launch {
            billingListRepository.getActiveBillingListsFlow().collect { lists ->
                _billingLists.value = lists
            }
        }
        usersJob?.cancel()
        usersJob = viewModelScope.launch {
            userRepository.getUsersFlow().collect { users ->
                _activeUsers.value = users
            }
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            _billingLists.value = billingListRepository.getActiveBillingListsFlow().first()
            _activeUsers.value = userRepository.getAllUsers()
        }
    }

    fun selectList(id: String?) {
        _selectedListId.value = id
    }

    fun saveBillingList(list: BillingList) {
        viewModelScope.launch {
            try {
                val isNew = list.id.isBlank()
                val idToSave = if (isNew) generateUuid() else list.id
                val listToSave = list.copy(
                    id = idToSave,
                    users = list.users.map { u ->
                        u.copy(
                            id = if (u.id.isBlank()) generateUuid() else u.id,
                            listId = idToSave
                        )
                    }
                )
                
                billingListRepository.saveBillingList(listToSave)
                _selectedListId.value = idToSave
                _errorMessage.value = null
                refreshData()
            } catch (e: Exception) {
                println("[AdminBulkBillingViewModel] saveBillingList error: ${e.message}")
                _errorMessage.value = "Failed to save billing list: ${e.message}"
            }
        }
    }

    fun deleteList(id: String) {
        viewModelScope.launch {
            try {
                billingListRepository.deleteBillingList(id)
                if (_selectedListId.value == id) {
                    _selectedListId.value = null
                }
                _errorMessage.value = null
                refreshData()
            } catch (e: Exception) {
                println("[AdminBulkBillingViewModel] deleteList error: ${e.message}")
                _errorMessage.value = "Failed to delete billing list: ${e.message}"
            }
        }
    }

    // Temporary state to hold variable amounts while editing in the UI (keyed by "listId:userId" or "userId")
    private val _variableAmounts = MutableStateFlow<Map<String, Long>>(emptyMap())
    val variableAmounts: StateFlow<Map<String, Long>> = _variableAmounts

    fun setVariableAmount(listId: String, userId: String, amountCents: Long) {
        val clamped = amountCents.coerceAtLeast(0L)
        val key = if (listId.isBlank()) userId else "${listId}:${userId}"
        _variableAmounts.value = _variableAmounts.value.toMutableMap().apply {
            if (clamped <= 0L) {
                remove(key)
            } else {
                put(key, clamped)
            }
        }
    }

    fun setVariableAmount(userId: String, amountCents: Long) {
        val currentListId = _selectedListId.value ?: ""
        setVariableAmount(currentListId, userId, amountCents)
    }

    fun getVariableAmount(listId: String, userId: String): Long {
        if (listId.isNotBlank()) {
            val scoped = _variableAmounts.value["${listId}:${userId}"]
            if (scoped != null) return scoped
        }
        return _variableAmounts.value[userId] ?: 0L
    }

    fun executeCharges(list: BillingList, onComplete: ((Boolean) -> Unit)? = null) {
        if (_isExecutingCharges.value) {
            println("[AdminBulkBillingViewModel] executeCharges rejected: execution already in progress")
            return
        }
        _isExecutingCharges.value = true

        viewModelScope.launch {
            try {
                val nowMillis = currentTimeMillis()
                val allUsers = activeUsers.value.associateBy { it.id }
                val batchRequests = mutableListOf<de.joelneumann.lojinha.domain.model.AtomicTransactionRequest>()

                for (listUser in list.users) {
                    val user = allUsers[listUser.userId] ?: continue
                    if (!user.isActive || user.isDeleted) continue

                    val unitPrice: Long
                    val quantity: Long
                    val amountCents: Long

                    if (list.type == BillingListType.FIXED) {
                        unitPrice = list.basePrice ?: 0L
                        quantity = listUser.quantity.toLong()
                        if (quantity <= 0L) continue
                        amountCents = unitPrice * quantity
                    } else {
                        val customAmount = getVariableAmount(list.id, listUser.userId)
                        if (customAmount <= 0L) continue
                        unitPrice = customAmount
                        quantity = 1L
                        amountCents = customAmount
                    }

                    if (amountCents <= 0) continue

                    val absCents = kotlin.math.abs(amountCents)
                    val deltaCents = -absCents
                    val txId = generateUuid()

                    val items = listOf(
                        TransactionItem(
                            productId = "custom_bulk",
                            productName = list.name,
                            unitType = UnitType.PIECE,
                            quantity = quantity,
                            unitPriceAtPurchase = unitPrice
                        )
                    )

                    val balBefore = user.balance
                    val balAfter = user.balance + deltaCents

                    val tx = Transaction(
                        id = txId,
                        userId = user.id,
                        userNameSnapshot = user.name,
                        timestamp = nowMillis,
                        type = TransactionType.ADMIN_WITHDRAWAL,
                        note = list.comment?.takeIf { it.isNotBlank() } ?: list.name,
                        totalAmount = deltaCents,
                        items = items,
                        userBalanceBefore = balBefore,
                        userBalanceAfter = balAfter
                    )

                    batchRequests.add(de.joelneumann.lojinha.domain.model.AtomicTransactionRequest(tx, deltaCents, emptyMap()))
                }

                if (batchRequests.isEmpty()) {
                    _errorMessage.value = "No valid charges to execute. Please ensure users are active and amounts are greater than zero."
                    onComplete?.invoke(false)
                    return@launch
                }

                val success = transactionRepository.executeBatchTransactions(batchRequests)

                if (success) {
                    billingListRepository.updateLastExecutionTime(list.id, nowMillis)
                    val chargedUserIds = list.users.map { it.userId }.toSet()
                    _variableAmounts.value = _variableAmounts.value.filterKeys { key ->
                        !key.startsWith("${list.id}:") && !chargedUserIds.contains(key)
                    }
                    _errorMessage.value = null
                    refreshData()
                    onComplete?.invoke(true)
                } else {
                    _errorMessage.value = "Failed to execute batch charges. Please check network connection and retry."
                    onComplete?.invoke(false)
                }
            } catch (e: Exception) {
                println("[AdminBulkBillingViewModel] executeCharges error: ${e.message}")
                _errorMessage.value = "Error executing charges: ${e.message}"
                onComplete?.invoke(false)
            } finally {
                _isExecutingCharges.value = false
            }
        }
    }
}
