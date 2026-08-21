package de.joelneumann.lojinha.ui.viewmodel.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.joelneumann.lojinha.domain.model.*
import de.joelneumann.lojinha.domain.repository.ProductRepository
import de.joelneumann.lojinha.domain.repository.TransactionRepository
import de.joelneumann.lojinha.domain.repository.UserRepository
import de.joelneumann.lojinha.ui.utils.Formatting
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

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            transactionRepository.getTransactionsFlow().collect { _transactions.value = it }
        }
    }

    fun updateSearchFilter(query: String) {
        _searchFilter.value = query
    }

    fun updateTypeFilter(type: TransactionType?) {
        _selectedTypeFilter.value = type
    }

    fun stornoPurchaseWithUpdatedItems(originalTx: Transaction, updatedItems: List<TransactionItem>) {
        val nowMillis = System.currentTimeMillis()
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

        val stornoId = "tx-storno-" + nowMillis + "-" + Random.nextInt(1000, 9999)
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
        }
    }

    fun stornoNonPurchaseTransaction(tx: Transaction) {
        if (tx.type == TransactionType.CANCELLATION) return
        val nowMillis = System.currentTimeMillis()
        val cancellationId = "tx-storno-" + nowMillis + "-" + Random.nextInt(1000, 9999)

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
        }
    }
}
