package de.joelneumann.lojinha.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.joelneumann.lojinha.domain.model.*
import de.joelneumann.lojinha.domain.repository.ProductRepository
import de.joelneumann.lojinha.domain.repository.SettingsRepository
import de.joelneumann.lojinha.domain.repository.TransactionRepository
import de.joelneumann.lojinha.domain.repository.UserRepository
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

    fun attemptDeleteUser(user: User) {
        viewModelScope.launch {
            val success = userRepository.hardDeleteUser(user.id)
            if (!success) {
                _userDeleteErrorMessage.value = "User has transaction history! User has been soft-deleted (deactivated) to preserve the audit trail."
            }
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

    // Strict Reversal Workflow
    fun reverseTransaction(tx: Transaction) {
        if (tx.type == TransactionType.CANCELLATION) return // Already a cancellation

        val nowMillis = System.currentTimeMillis()
        val cancellationId = "tx-rev-" + nowMillis + "-" + Random.nextInt(1000, 9999)

        val refundAmount = -tx.totalAmount // Negate original transaction total amount

        val reversalTx = Transaction(
            id = cancellationId,
            userId = tx.userId,
            userNameSnapshot = tx.userNameSnapshot,
            timestamp = nowMillis,
            type = TransactionType.CANCELLATION,
            referenceTransactionId = tx.id,
            note = "Reversal of ${tx.type.name} from ${java.text.SimpleDateFormat("dd MMM yyyy, HH:mm").format(java.util.Date(tx.timestamp))}",
            totalAmount = refundAmount,
            items = tx.items
        )

        viewModelScope.launch {
            // Refund user balance
            userRepository.updateBalance(tx.userId, refundAmount)
            // Restore inventory for items if products exist
            tx.items.forEach { item ->
                val p = productRepository.getProductById(item.productId)
                if (p != null) {
                    productRepository.updateStock(item.productId, item.quantity)
                }
            }
            // Record cancellation transaction
            transactionRepository.recordTransaction(reversalTx)
        }
    }

    // System Settings Update
    fun updateSystemSettings(newSettings: SystemSettings) {
        viewModelScope.launch {
            settingsRepository.updateSettings(newSettings)
        }
    }
}
