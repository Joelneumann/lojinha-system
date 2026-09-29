package de.joelneumann.lojinha.ui.viewmodel

import de.joelneumann.lojinha.domain.model.*
import de.joelneumann.lojinha.domain.repository.ProductRepository
import de.joelneumann.lojinha.domain.repository.TransactionRepository
import de.joelneumann.lojinha.domain.repository.UserRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.*

private class FakeShoppingProductRepository(initialProducts: List<Product> = emptyList()) : ProductRepository {
    val productsFlow = MutableStateFlow(initialProducts)

    override fun getProductsFlow(): Flow<List<Product>> = productsFlow
    override suspend fun getAllProducts(): List<Product> = productsFlow.value
    override suspend fun getProductById(id: String): Product? = productsFlow.value.firstOrNull { it.id == id }
    override suspend fun getProductByBarcode(barcode: String): Product? {
        val clean = barcode.trim()
        return productsFlow.value.firstOrNull { entity ->
            entity.barcodes.any { it.code.equals(clean, ignoreCase = true) }
        }
    }
    override suspend fun saveProduct(product: Product) {
        val list = productsFlow.value.toMutableList()
        val idx = list.indexOfFirst { it.id == product.id }
        if (idx != -1) list[idx] = product else list.add(product)
        productsFlow.value = list
    }
    override suspend fun deactivateProduct(id: String) {}
    override suspend fun hardDeleteProduct(id: String) {}
    override suspend fun updateStock(productId: String, delta: Long) {}
}

private class FakeShoppingTransactionRepository : TransactionRepository {
    val transactions = mutableListOf<Transaction>()

    override fun getTransactionsFlow(): Flow<List<Transaction>> = MutableStateFlow(transactions)
    override suspend fun getAllTransactions(): List<Transaction> = transactions
    override suspend fun getTransactionsByUserId(userId: String): List<Transaction> = emptyList()
    override suspend fun getTransactionById(id: String): Transaction? = null
    override suspend fun recordTransaction(transaction: Transaction) { transactions.add(transaction) }
    override suspend fun getTransactionCountForUser(userId: String): Int = 0
    override suspend fun getTransactionsPaged(page: Int, pageSize: Int, searchQuery: String?, typeFilter: TransactionType?): PagedResult<Transaction> = PagedResult(emptyList<Transaction>(), 0, 0, pageSize, 0)
    override suspend fun getTransactionsByUserIdPaged(userId: String, page: Int, pageSize: Int, searchQuery: String?, typeFilter: TransactionType?): PagedResult<Transaction> = PagedResult(emptyList<Transaction>(), 0, 0, pageSize, 0)
    override suspend fun getTransactionsByReferenceIds(referenceIds: List<String>): List<Transaction> = emptyList()
    override suspend fun getTransactionsByIds(ids: List<String>): List<Transaction> = emptyList()
    override suspend fun getCancellationCountForReference(refId: String): Int = 0
    override suspend fun executeAtomicTransaction(transaction: Transaction, balanceDelta: Long, stockDeltas: Map<String, Long>) { transactions.add(transaction) }
    override suspend fun applyPurchaseCorrection(originalTransactionId: String, newItems: List<TransactionItem>): Boolean = true
    override suspend fun stornoNonPurchase(transactionId: String): Boolean = true
    override suspend fun executeBatchTransactions(requests: List<AtomicTransactionRequest>): Boolean = true
}

class ShoppingViewModelTest {

    private val prodClubMate = Product(
        id = "1",
        name = "Club Mate (330ml)",
        barcodes = listOf(Barcode("4029764001807", "Single Bottle")),
        basePrice = 800,
        unitType = UnitType.PIECE,
        stockQuantity = 50,
        isActive = true
    )

    private val prodGuarana = Product(
        id = "2",
        name = "Guaraná Antarctica 1.75L",
        barcodes = listOf(Barcode("7891234564821", "Large Bottle")),
        basePrice = 650,
        unitType = UnitType.PIECE,
        stockQuantity = 20,
        isActive = true
    )

    private val prodCoca = Product(
        id = "3",
        name = "Coca-Cola Zero",
        barcodes = listOf(Barcode("5449000000996", "Can")),
        basePrice = 500,
        unitType = UnitType.PIECE,
        stockQuantity = 30,
        isActive = true
    )

    private val prodApple = Product(
        id = "4",
        name = "Maçã Gala",
        barcodes = listOf(Barcode("9001", "Bulk")),
        basePrice = 1200, // per kg
        unitType = UnitType.WEIGHT,
        stockQuantity = 10000,
        isActive = true
    )

    private val prodAlphanumeric = Product(
        id = "5",
        name = "Special Item",
        barcodes = listOf(Barcode("BAR-SPEC-01")),
        basePrice = 1500,
        unitType = UnitType.PIECE,
        stockQuantity = 10,
        isActive = true
    )

    private val allProducts = listOf(prodClubMate, prodGuarana, prodCoca, prodApple, prodAlphanumeric)

    private fun withViewModel(
        products: List<Product> = allProducts,
        block: suspend (ShoppingViewModel, FakeShoppingProductRepository) -> Unit
    ) = runTest {
        val productRepo = FakeShoppingProductRepository(products)
        val userRepo = FakeUserRepository()
        val txRepo = FakeShoppingTransactionRepository()
        val vm = ShoppingViewModel(productRepo, userRepo, txRepo, backgroundScope)

        withTimeout(2000) {
            while (vm.products.value.isEmpty() && products.isNotEmpty()) {
                delay(10)
            }
        }
        block(vm, productRepo)
    }

    @Test
    fun testExactBarcodeMatch_addsCorrectProductToCart() = withViewModel { vm, _ ->
        val handled = vm.submitBarcodeOrSearch("4029764001807", 0.0)
        assertTrue(handled, "Barcode scan should succeed")

        val cart = vm.cartItems.value
        assertEquals(1, cart.size)
        assertEquals("1", cart[0].product.id)
        assertEquals("Club Mate (330ml)", cart[0].product.name)
        assertEquals(1L, cart[0].quantity)
        assertEquals("", vm.searchQuery.value)
    }

    @Test
    fun testRepeatedBarcodeScan_incrementsQuantityOnSameProduct() = withViewModel { vm, _ ->
        // Scan 1
        assertTrue(vm.submitBarcodeOrSearch("4029764001807", 0.0))
        // Scan 2
        assertTrue(vm.submitBarcodeOrSearch("4029764001807", 0.0))
        // Scan 3
        assertTrue(vm.submitBarcodeOrSearch("4029764001807", 0.0))

        val cart = vm.cartItems.value
        assertEquals(1, cart.size, "Cart must still contain only 1 distinct product")
        assertEquals("1", cart[0].product.id)
        assertEquals(3L, cart[0].quantity, "Quantity should have incremented to 3")
    }

    @Test
    fun testUnregisteredNumericBarcode_doesNotAddAnyProduct() = withViewModel { vm, _ ->
        // Scan a 13-digit barcode that contains digits like '1', '7', '8', but is NOT registered
        val handled = vm.submitBarcodeOrSearch("7890000000000", 0.0)
        assertFalse(handled, "Unregistered numeric barcode must not be accepted")

        val cart = vm.cartItems.value
        assertTrue(cart.isEmpty(), "Cart must remain completely empty when an unknown barcode is scanned")
        assertEquals("7890000000000", vm.searchQuery.value, "Query should be retained so UI shows not found")
    }

    @Test
    fun testExactNameMatch_addsProduct() = withViewModel { vm, _ ->
        val handled = vm.submitBarcodeOrSearch("Coca-Cola Zero", 0.0)
        assertTrue(handled)

        val cart = vm.cartItems.value
        assertEquals(1, cart.size)
        assertEquals("3", cart[0].product.id)
    }

    @Test
    fun testFuzzyTextSearch_addsRankedProduct() = withViewModel { vm, _ ->
        // "mate" matches "Club Mate (330ml)"
        val handled = vm.submitBarcodeOrSearch("mate", 0.0)
        assertTrue(handled)

        val cart = vm.cartItems.value
        assertEquals(1, cart.size)
        assertEquals("1", cart[0].product.id)
    }

    @Test
    fun testBarcodeWithWhitespaceAndCase_matchesCleanly() = withViewModel { vm, _ ->
        val handled = vm.submitBarcodeOrSearch("   bar-spec-01   ", 0.0)
        assertTrue(handled)

        val cart = vm.cartItems.value
        assertEquals(1, cart.size)
        assertEquals("5", cart[0].product.id)
    }

    @Test
    fun testWeightProduct_opensWeightDialogInsteadOfDirectCart() = withViewModel { vm, _ ->
        val handled = vm.submitBarcodeOrSearch("9001", 0.0)
        assertTrue(handled)

        val cart = vm.cartItems.value
        assertTrue(cart.isEmpty(), "Weight product should not go straight to cart with quantity 1")
        assertNotNull(vm.weightProductDialog.value, "Weight dialog should be open")
        assertEquals("4", vm.weightProductDialog.value?.id)
    }

    @Test
    fun testBlankQuery_returnsFalseAndDoesNothing() = withViewModel { vm, _ ->
        assertFalse(vm.submitBarcodeOrSearch("", 0.0))
        assertFalse(vm.submitBarcodeOrSearch("   ", 0.0))
        assertTrue(vm.cartItems.value.isEmpty())
    }
}
