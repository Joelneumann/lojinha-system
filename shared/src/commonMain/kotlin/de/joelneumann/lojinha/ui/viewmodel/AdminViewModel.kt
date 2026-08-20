package de.joelneumann.lojinha.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.joelneumann.lojinha.domain.model.*
import de.joelneumann.lojinha.domain.repository.ProductRepository
import de.joelneumann.lojinha.domain.repository.SettingsRepository
import de.joelneumann.lojinha.domain.repository.TransactionRepository
import de.joelneumann.lojinha.domain.repository.UserRepository
import de.joelneumann.lojinha.ui.utils.Formatting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class AdminTab {
    PRODUCTS,
    USERS,
    TRANSACTIONS,
    SETTINGS
}

class AdminViewModel(
    private val productRepository: ProductRepository,
    private val userRepository: UserRepository,
    private val transactionRepository: TransactionRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _currentTab = MutableStateFlow(AdminTab.PRODUCTS)
    val currentTab: StateFlow<AdminTab> = _currentTab.asStateFlow()

    private val _products = MutableStateFlow<List<Product>>(emptyList())
    val products: StateFlow<List<Product>> = _products.asStateFlow()

    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: StateFlow<List<User>> = _users.asStateFlow()

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()

    private val _settings = MutableStateFlow(SystemSettings())
    val settings: StateFlow<SystemSettings> = _settings.asStateFlow()

    private val _depositUser = MutableStateFlow<User?>(null)
    val depositUser: StateFlow<User?> = _depositUser.asStateFlow()

    private val _depositAmountInput = MutableStateFlow("")
    val depositAmountInput: StateFlow<String> = _depositAmountInput.asStateFlow()

    private val _depositNoteInput = MutableStateFlow("")
    val depositNoteInput: StateFlow<String> = _depositNoteInput.asStateFlow()

    private val _editProduct = MutableStateFlow<Product?>(null)
    val editProduct: StateFlow<Product?> = _editProduct.asStateFlow()

    private val _showProductModal = MutableStateFlow(false)
    val showProductModal: StateFlow<Boolean> = _showProductModal.asStateFlow()

    private val _editUser = MutableStateFlow<User?>(null)
    val editUser: StateFlow<User?> = _editUser.asStateFlow()

    private val _showUserModal = MutableStateFlow(false)
    val showUserModal: StateFlow<Boolean> = _showUserModal.asStateFlow()

    private val _userDeleteErrorMessage = MutableStateFlow<String?>(null)
    val userDeleteErrorMessage: StateFlow<String?> = _userDeleteErrorMessage.asStateFlow()

    init {
        loadData()
    }

    fun selectTab(tab: AdminTab) {
        _currentTab.value = tab
    }

    fun loadData() {
        viewModelScope.launch {
            productRepository.getProductsFlow().collect { _products.value = it }
        }
        viewModelScope.launch {
            userRepository.getUsersFlow().collect { _users.value = it }
        }
        viewModelScope.launch {
            transactionRepository.getTransactionsFlow().collect { _transactions.value = it }
        }
        viewModelScope.launch {
            settingsRepository.getSettingsFlow().collect { _settings.value = it }
        }
    }

    // Product CRUD
    fun openNewProductModal() {
        _editProduct.value = Product(
            id = "p-" + System.currentTimeMillis(),
            name = "",
            barcodes = emptyList(),
            basePrice = 0L,
            unitType = UnitType.PIECE,
            stockQuantity = 0L
        )
        _showProductModal.value = true
    }

    fun openEditProductModal(product: Product) {
        _editProduct.value = product
        _showProductModal.value = true
    }

    fun closeProductModal() {
        _showProductModal.value = false
        _editProduct.value = null
    }

    fun saveProduct(product: Product) {
        viewModelScope.launch {
            productRepository.saveProduct(product)
            closeProductModal()
        }
    }

    fun toggleProductActive(product: Product) {
        viewModelScope.launch {
            productRepository.saveProduct(product.copy(isActive = !product.isActive))
        }
    }

    fun deleteProduct(productId: String) {
        viewModelScope.launch {
            productRepository.hardDeleteProduct(productId)
        }
    }

    fun adjustProductStock(productId: String, deltaQuantity: Long) {
        viewModelScope.launch {
            val prod = _products.value.firstOrNull { it.id == productId } ?: return@launch
            val newStock = (prod.stockQuantity + deltaQuantity).coerceAtLeast(0L)
            productRepository.saveProduct(prod.copy(stockQuantity = newStock))
            loadData()
        }
    }

    // User CRUD
    fun openNewUserModal() {
        _editUser.value = User(
            id = "u-" + System.currentTimeMillis(),
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

    fun saveUser(user: User) {
        viewModelScope.launch {
            userRepository.saveUser(user)
            closeUserModal()
        }
    }

    fun toggleUserActive(user: User) {
        viewModelScope.launch {
            userRepository.saveUser(user.copy(isActive = !user.isActive))
        }
    }

    fun softDeleteUser(userId: String) {
        viewModelScope.launch {
            userRepository.softDeleteUser(userId)
            loadData()
        }
    }

    fun restoreUser(userId: String) {
        viewModelScope.launch {
            userRepository.restoreUser(userId)
            loadData()
        }
    }

    fun attemptDeleteUser(user: User) {
        viewModelScope.launch {
            userRepository.softDeleteUser(user.id)
            loadData()
        }
    }

    fun clearUserDeleteError() {
        _userDeleteErrorMessage.value = null
    }

    // Quick Deposit / Withdrawal
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

        val nowMillis = System.currentTimeMillis()
        val txId = "tx-admin-" + nowMillis + "-" + Random.nextInt(1000, 9999)

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
            closeDepositModal()
        }
    }

    fun adjustUserBalance(userId: String, userName: String, centsDelta: Long, note: String) {
        val isDeposit = centsDelta > 0
        val txType = if (isDeposit) TransactionType.ADMIN_DEPOSIT else TransactionType.ADMIN_WITHDRAWAL
        val nowMillis = System.currentTimeMillis()
        val txId = "tx-admin-" + nowMillis + "-" + kotlin.random.Random.nextInt(1000, 9999)

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
            loadData()
        }
    }

    // Strict Storno Workflow
    fun stornoPurchaseWithUpdatedItems(originalTx: Transaction, updatedItems: List<TransactionItem>) {
        val nowMillis = System.currentTimeMillis()
        val isAllZero = updatedItems.all { it.quantity == 0L }

        // Calculate original cost (positive) and updated cost (positive)
        val originalCost = kotlin.math.abs(originalTx.totalAmount)
        val updatedCost = updatedItems.sumOf { item ->
            when (item.unitType) {
                UnitType.PIECE -> item.unitPriceAtPurchase * item.quantity
                UnitType.WEIGHT -> kotlin.math.round((item.unitPriceAtPurchase * item.quantity) / 1000.0).toLong()
            }
        }
        val costDifference = updatedCost - originalCost // Negative = reduced cost (refund), Positive = increased cost (charge)
        val balanceDelta = -costDifference // Positive = credit/refund, Negative = debit/charge

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
            // Adjust user balance
            userRepository.updateBalance(originalTx.userId, balanceDelta)

            // Adjust stock levels for each product (qtyChange = updated - original)
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
            loadData()
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
            loadData()
        }
    }

    // System Settings Update
    fun updateSystemSettings(newSettings: SystemSettings) {
        viewModelScope.launch {
            settingsRepository.updateSettings(newSettings)
        }
    }
}
