package de.joelneumann.lojinha.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.joelneumann.lojinha.domain.model.*
import de.joelneumann.lojinha.domain.repository.ProductRepository
import de.joelneumann.lojinha.domain.repository.TransactionRepository
import de.joelneumann.lojinha.domain.repository.UserRepository
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.utils.Formatting
import de.joelneumann.lojinha.ui.utils.containsIgnoreAccents
import de.joelneumann.lojinha.ui.utils.filterAndRankProducts
import de.joelneumann.lojinha.ui.utils.generateUuid
import de.joelneumann.lojinha.ui.utils.removeAccents
import de.joelneumann.lojinha.ui.utils.sortedByAccentInsensitive
import de.joelneumann.lojinha.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

data class CartItem(
    val product: Product,
    val quantity: Long, // units for PIECE, grams for WEIGHT
    val unitPriceWithMarkup: Long
) {
    val lineTotal: Long
        get() = when (product.unitType) {
            UnitType.PIECE -> unitPriceWithMarkup * quantity
            UnitType.WEIGHT -> kotlin.math.round((unitPriceWithMarkup * quantity) / 1000.0).toLong()
        }
}

class ShoppingViewModel(
    private val productRepository: ProductRepository,
    private val userRepository: UserRepository,
    private val transactionRepository: TransactionRepository,
    coroutineScope: CoroutineScope? = null
) : ViewModel() {

    private val activeScope = coroutineScope ?: viewModelScope

    private val _products = MutableStateFlow<List<Product>>(emptyList())
    val products: StateFlow<List<Product>> = _products.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems: StateFlow<List<CartItem>> = _cartItems.asStateFlow()

    private val _isProcessingPurchase = MutableStateFlow(false)
    val isProcessingPurchase: StateFlow<Boolean> = _isProcessingPurchase.asStateFlow()

    private val _purchaseError = MutableStateFlow<String?>(null)
    val purchaseError: StateFlow<String?> = _purchaseError.asStateFlow()

    private val _weightProductDialog = MutableStateFlow<Product?>(null)
    val weightProductDialog: StateFlow<Product?> = _weightProductDialog.asStateFlow()

    private val _weightInput = MutableStateFlow("")
    val weightInput: StateFlow<String> = _weightInput.asStateFlow()

    private val _weightError = MutableStateFlow<String?>(null)
    val weightError: StateFlow<String?> = _weightError.asStateFlow()

    private val _completedPurchase = MutableStateFlow<Transaction?>(null)
    val completedPurchase: StateFlow<Transaction?> = _completedPurchase.asStateFlow()

    init {
        loadProducts()
    }

    fun loadProducts() {
        activeScope.launch {
            productRepository.getProductsFlow().collect { list ->
                _products.value = list.filter { it.isActive }.sortedByAccentInsensitive { it.name }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun submitBarcodeOrSearch(
        query: String,
        globalMarkup: Double,
        highlightedProduct: Product? = null
    ): Boolean {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return false

        // 1. Try exact barcode match first
        val exactBarcodeProduct = _products.value.firstOrNull { product ->
            product.barcodes.any { it.code.trim().equals(trimmed, ignoreCase = true) }
        }
        if (exactBarcodeProduct != null) {
            onProductSelected(exactBarcodeProduct, globalMarkup)
            _searchQuery.value = ""
            return true
        }

        // 2. Try exact name match (accent and case insensitive)
        val exactNameProduct = _products.value.firstOrNull { product ->
            product.name.trim().removeAccents().equals(trimmed.removeAccents(), ignoreCase = true)
        }
        if (exactNameProduct != null) {
            onProductSelected(exactNameProduct, globalMarkup)
            _searchQuery.value = ""
            return true
        }

        // 3. Safety Guard: If query is purely numeric and has barcode length (>= 4 digits),
        // and did NOT match an exact barcode, DO NOT add a random substring-matching product!
        val isNumericBarcode = trimmed.all { it.isDigit() } && trimmed.length >= 4
        if (isNumericBarcode) {
            _searchQuery.value = trimmed
            return false
        }

        // 4. Manual search submission: use highlighted product or first ranked search result
        if (highlightedProduct != null) {
            onProductSelected(highlightedProduct, globalMarkup)
            _searchQuery.value = ""
            return true
        }

        val ranked = _products.value.filterAndRankProducts(trimmed)
        if (ranked.isNotEmpty()) {
            onProductSelected(ranked.first(), globalMarkup)
            _searchQuery.value = ""
            return true
        }

        return false
    }

    fun onSearchSubmitted(globalMarkup: Double) {
        submitBarcodeOrSearch(_searchQuery.value, globalMarkup)
    }

    fun onProductSelected(product: Product, globalMarkup: Double) {
        if (product.unitType == UnitType.PIECE) {
            addPieceItemToCart(product, globalMarkup)
            _searchQuery.value = ""
        } else {
            _weightProductDialog.value = product
            _weightInput.value = ""
            _weightError.value = null
        }
    }

    private fun addPieceItemToCart(product: Product, globalMarkup: Double) {
        val currentList = _cartItems.value.toMutableList()
        val unitPrice = product.calculateEffectiveUnitPrice(globalMarkup)
        val existingIndex = currentList.indexOfFirst { it.product.id == product.id }
        if (existingIndex >= 0) {
            val item = currentList[existingIndex]
            currentList[existingIndex] = item.copy(
                product = product,
                quantity = item.quantity + 1,
                unitPriceWithMarkup = unitPrice
            )
        } else {
            currentList.add(CartItem(product = product, quantity = 1, unitPriceWithMarkup = unitPrice))
        }
        _cartItems.value = currentList
    }


    fun updateWeightInput(input: String) {
        _weightInput.value = input
        _weightError.value = null
    }

    fun submitWeightDialog(globalMarkup: Double) {
        val product = _weightProductDialog.value ?: return
        val grams = Formatting.parseWeightInputToGrams(_weightInput.value)
        if (grams != null && grams > 0) {
            val currentList = _cartItems.value.toMutableList()
            val unitPrice = product.calculateEffectiveUnitPrice(globalMarkup)
            val existingIndex = currentList.indexOfFirst { it.product.id == product.id }
            if (existingIndex >= 0) {
                val item = currentList[existingIndex]
                currentList[existingIndex] = item.copy(
                    product = product,
                    quantity = item.quantity + grams,
                    unitPriceWithMarkup = unitPrice
                )
            } else {
                currentList.add(CartItem(product = product, quantity = grams, unitPriceWithMarkup = unitPrice))
            }
            _cartItems.value = currentList
            _searchQuery.value = ""
            closeWeightDialog()
        } else {
            _weightError.value = I18n.get().invalidWeightFormat
        }
    }

    fun closeWeightDialog() {
        _weightProductDialog.value = null
        _weightInput.value = ""
        _weightError.value = null
    }

    fun updateCartItemQuantity(productId: String, newQty: Long) {
        if (newQty <= 0) {
            removeCartItem(productId)
            return
        }
        _cartItems.value = _cartItems.value.map { item ->
            if (item.product.id == productId) item.copy(quantity = newQty) else item
        }
    }

    fun removeCartItem(productId: String) {
        _cartItems.value = _cartItems.value.filterNot { it.product.id == productId }
    }

    fun clearCart() {
        _cartItems.value = emptyList()
        _searchQuery.value = ""
        closeWeightDialog()
    }

    fun dismissCompletedPurchase() {
        _completedPurchase.value = null
    }

    fun clearPurchaseError() {
        _purchaseError.value = null
    }

    fun completePurchase(user: User, onPurchaseFinalized: () -> Unit = {}) {
        if (_isProcessingPurchase.value) return
        val cart = _cartItems.value
        if (cart.isEmpty()) return

        _isProcessingPurchase.value = true
        _purchaseError.value = null

        val totalCents = cart.sumOf { it.lineTotal }
        val nowMillis = de.joelneumann.lojinha.ui.utils.currentTimeMillis()
        val txId = generateUuid()

        val txItems = cart.map { item ->
            TransactionItem(
                productId = item.product.id,
                productName = item.product.name,
                unitType = item.product.unitType,
                quantity = item.quantity,
                unitPriceAtPurchase = item.unitPriceWithMarkup
            )
        }

        val balBefore = user.balance
        val balAfter = user.balance - totalCents

        val tx = Transaction(
            id = txId,
            userId = user.id,
            userNameSnapshot = user.name,
            timestamp = nowMillis,
            type = TransactionType.PURCHASE,
            totalAmount = -totalCents, // Negative for purchase
            items = txItems,
            userBalanceBefore = balBefore,
            userBalanceAfter = balAfter
        )

        activeScope.launch {
            try {
                val stockDeltas = cart.associate { it.product.id to -it.quantity }
                transactionRepository.executeAtomicTransaction(
                    transaction = tx,
                    balanceDelta = -totalCents,
                    stockDeltas = stockDeltas
                )

                clearCart()
                _completedPurchase.value = tx
                onPurchaseFinalized()
            } catch (e: Exception) {
                AppLogger.error("ShoppingViewModel", "Failed to complete purchase for user ${user.id}: ${e.message}", e)
                _purchaseError.value = e.message ?: I18n.get().errFailedToCompletePurchase
            } finally {
                _isProcessingPurchase.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        clearCart()
        dismissCompletedPurchase()
        clearPurchaseError()
    }
}
