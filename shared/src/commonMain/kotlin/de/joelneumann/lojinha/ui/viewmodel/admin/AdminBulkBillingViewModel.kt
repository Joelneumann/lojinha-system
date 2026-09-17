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
                val listToSave = list.copy(id = idToSave)
                
                billingListRepository.saveBillingList(listToSave)
                
                // Delete old users from list
                billingListRepository.removeAllUsersFromList(idToSave)
                
                // Add new users
                for (u in listToSave.users) {
                    billingListRepository.addUserToList(
                        u.copy(id = generateUuid(), listId = idToSave)
                    )
                }
                _selectedListId.value = idToSave
                refreshData()
            } catch (e: Exception) {
                println("[AdminBulkBillingViewModel] saveBillingList error: ${e.message}")
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
                refreshData()
            } catch (e: Exception) {
                println("[AdminBulkBillingViewModel] deleteList error: ${e.message}")
            }
        }
    }

    // Temporary state to hold variable amounts while editing in the UI (since they are not saved in the DB between sessions)
    private val _variableAmounts = MutableStateFlow<Map<String, Long>>(emptyMap())
    val variableAmounts: StateFlow<Map<String, Long>> = _variableAmounts

    fun setVariableAmount(userId: String, amountCents: Long) {
        _variableAmounts.value = _variableAmounts.value.toMutableMap().apply {
            put(userId, amountCents)
        }
    }

    fun executeCharges(list: BillingList, onComplete: ((Boolean) -> Unit)? = null) {
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
                        quantity = listUser.quantity.toLong().coerceAtLeast(1L)
                        amountCents = unitPrice * quantity
                    } else {
                        val customAmount = _variableAmounts.value[listUser.userId] ?: 0L
                        unitPrice = kotlin.math.abs(customAmount)
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

                val success = if (batchRequests.isNotEmpty()) {
                    transactionRepository.executeBatchTransactions(batchRequests)
                } else true

                if (success) {
                    _variableAmounts.value = emptyMap()
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
            }
        }
    }
}
