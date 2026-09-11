package de.joelneumann.lojinha.ui.viewmodel.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.joelneumann.lojinha.domain.model.*
import de.joelneumann.lojinha.domain.repository.BillingListRepository
import de.joelneumann.lojinha.domain.repository.TransactionRepository
import de.joelneumann.lojinha.domain.repository.UserRepository
import de.joelneumann.lojinha.ui.utils.currentTimeMillis
import de.joelneumann.lojinha.ui.utils.generateUuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AdminBulkBillingViewModel(
    private val billingListRepository: BillingListRepository,
    private val userRepository: UserRepository,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    val billingLists: StateFlow<List<BillingList>> = billingListRepository.getActiveBillingListsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeUsers: StateFlow<List<User>> = userRepository.getUsersFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedListId = MutableStateFlow<String?>(null)
    val selectedListId: StateFlow<String?> = _selectedListId

    fun selectList(id: String?) {
        _selectedListId.value = id
    }

    fun saveBillingList(list: BillingList) {
        viewModelScope.launch {
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
        }
    }

    fun deleteList(id: String) {
        viewModelScope.launch {
            billingListRepository.deleteBillingList(id)
            if (_selectedListId.value == id) {
                _selectedListId.value = null
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

    fun executeCharges(list: BillingList) {
        viewModelScope.launch {
            val nowMillis = currentTimeMillis()
            val allUsers = activeUsers.value.associateBy { it.id }

            for (listUser in list.users) {
                val user = allUsers[listUser.userId] ?: continue
                if (!user.isActive || user.isDeleted) continue

                val amountCents = if (list.type == BillingListType.FIXED) {
                    (list.basePrice ?: 0L) * listUser.quantity
                } else {
                    _variableAmounts.value[listUser.userId] ?: 0L
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
                        quantity = listUser.quantity.toLong(),
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
                    type = TransactionType.ADMIN_WITHDRAWAL,
                    note = list.comment?.takeIf { it.isNotBlank() } ?: list.name,
                    totalAmount = deltaCents,
                    items = items,
                    userBalanceBefore = balBefore,
                    userBalanceAfter = balAfter
                )

                transactionRepository.executeAtomicTransaction(tx, deltaCents, emptyMap())
            }

            // Clear variable amounts after execution
            _variableAmounts.value = emptyMap()
        }
    }
}
