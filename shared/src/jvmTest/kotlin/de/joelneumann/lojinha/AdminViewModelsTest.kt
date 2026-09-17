package de.joelneumann.lojinha

import de.joelneumann.lojinha.domain.model.*
import de.joelneumann.lojinha.domain.repository.BillingListRepository
import de.joelneumann.lojinha.domain.repository.ProductRepository
import de.joelneumann.lojinha.domain.repository.TransactionRepository
import de.joelneumann.lojinha.domain.repository.UserRepository
import de.joelneumann.lojinha.ui.viewmodel.admin.AdminBulkBillingViewModel
import de.joelneumann.lojinha.ui.viewmodel.admin.AdminProductsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AdminViewModelsTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- Fake Repositories ---

    private class TestProductRepository(
        var products: List<Product> = emptyList()
    ) : ProductRepository {
        var recordedStockDeltas = mutableListOf<Pair<String, Long>>()
        var savedProducts = mutableListOf<Product>()

        override fun getProductsFlow(): Flow<List<Product>> = flowOf(products)
        override suspend fun getAllProducts(): List<Product> = products
        override suspend fun getProductById(id: String): Product? = products.find { it.id == id }
        override suspend fun getProductByBarcode(barcode: String): Product? = null
        override suspend fun saveProduct(product: Product) {
            savedProducts.add(product)
        }
        override suspend fun deactivateProduct(id: String) {}
        override suspend fun hardDeleteProduct(id: String) {}
        override suspend fun updateStock(productId: String, delta: Long) {
            recordedStockDeltas.add(productId to delta)
            products = products.map {
                if (it.id == productId) it.copy(stockQuantity = it.stockQuantity + delta) else it
            }
        }
    }

    private class TestBillingListRepository(
        var lists: List<BillingList> = emptyList()
    ) : BillingListRepository {
        override fun getActiveBillingListsFlow(): Flow<List<BillingList>> = flowOf(lists)
        override suspend fun saveBillingList(list: BillingList) {}
        override suspend fun deleteBillingList(id: String) {}
        override suspend fun addUserToList(user: BillingListUser) {}
        override suspend fun removeUserFromList(listId: String, userId: String) {}
        override suspend fun removeAllUsersFromList(listId: String) {}
        override suspend fun updateUserQuantity(listId: String, userId: String, quantity: Int) {}
    }

    private class TestUserRepository(
        var users: List<User> = emptyList()
    ) : UserRepository {
        override fun getUsersFlow(): Flow<List<User>> = flowOf(users)
        override suspend fun getAllUsers(): List<User> = users
        override suspend fun getUserById(id: String): User? = users.find { it.id == id }
        override suspend fun getUserByBarcode(barcode: String): User? = null
        override suspend fun saveUser(user: User) {}
        override suspend fun deactivateUser(id: String) {}
        override suspend fun softDeleteUser(id: String) {}
        override suspend fun restoreUser(id: String) {}
        override suspend fun canHardDeleteUser(id: String): Boolean = true
        override suspend fun hardDeleteUser(id: String): Boolean = true
        override suspend fun updateBalance(userId: String, amountDelta: Long) {}
    }

    private class TestTransactionRepository : TransactionRepository {
        var batchResult: Boolean = true
        var recordedBatchRequests = mutableListOf<List<AtomicTransactionRequest>>()

        override fun getTransactionsFlow(): Flow<List<Transaction>> = flowOf(emptyList())
        override suspend fun getAllTransactions(): List<Transaction> = emptyList()
        override suspend fun getTransactionsByUserId(userId: String): List<Transaction> = emptyList()
        override suspend fun getTransactionById(id: String): Transaction? = null
        override suspend fun recordTransaction(transaction: Transaction) {}
        override suspend fun getTransactionCountForUser(userId: String): Int = 0
        override suspend fun getTransactionsPaged(page: Int, pageSize: Int, searchQuery: String?, typeFilter: TransactionType?): PagedResult<Transaction> =
            PagedResult(emptyList(), 0, page, pageSize, 1)
        override suspend fun getTransactionsByUserIdPaged(userId: String, page: Int, pageSize: Int, searchQuery: String?, typeFilter: TransactionType?): PagedResult<Transaction> =
            PagedResult(emptyList(), 0, page, pageSize, 1)
        override suspend fun getTransactionsByReferenceIds(referenceIds: List<String>): List<Transaction> = emptyList()
        override suspend fun getTransactionsByIds(ids: List<String>): List<Transaction> = emptyList()
        override suspend fun getCancellationCountForReference(refId: String): Int = 0
        override suspend fun executeAtomicTransaction(transaction: Transaction, balanceDelta: Long, stockDeltas: Map<String, Long>) {}
        override suspend fun applyPurchaseCorrection(originalTransactionId: String, newItems: List<TransactionItem>): Boolean = true
        override suspend fun stornoNonPurchase(transactionId: String): Boolean = true
        override suspend fun executeBatchTransactions(requests: List<AtomicTransactionRequest>): Boolean {
            recordedBatchRequests.add(requests)
            return batchResult
        }
    }

    // --- Tests for AdminProductsViewModel (QA-01 & QA-08) ---

    @Test
    fun testAdjustProductStock_callsUpdateStockWithCorrectDelta() = runTest {
        val initialProduct = Product(
            id = "prod-1",
            name = "Test Product",
            barcodes = emptyList(),
            basePrice = 1000,
            unitType = UnitType.PIECE,
            stockQuantity = 10
        )
        val productRepo = TestProductRepository(listOf(initialProduct))
        val viewModel = AdminProductsViewModel(productRepo)

        // Increment stock by +5
        viewModel.adjustProductStock("prod-1", 5)

        assertEquals(1, productRepo.recordedStockDeltas.size)
        assertEquals("prod-1" to 5L, productRepo.recordedStockDeltas.first())
        // Ensure saveProduct was NOT called for quick stock adjustments
        assertTrue(productRepo.savedProducts.isEmpty())
    }

    @Test
    fun testAdjustProductStock_clampedAtZero() = runTest {
        val initialProduct = Product(
            id = "prod-2",
            name = "Limited Stock Item",
            barcodes = emptyList(),
            basePrice = 500,
            unitType = UnitType.PIECE,
            stockQuantity = 3
        )
        val productRepo = TestProductRepository(listOf(initialProduct))
        val viewModel = AdminProductsViewModel(productRepo)

        // Attempting to decrement by -10 when stock is 3 should clamp to -3
        viewModel.adjustProductStock("prod-2", -10)

        assertEquals(1, productRepo.recordedStockDeltas.size)
        assertEquals("prod-2" to -3L, productRepo.recordedStockDeltas.first())
    }

    // --- Tests for AdminBulkBillingViewModel (QA-02 & QA-08) ---

    @Test
    fun testBulkBilling_retainsVariableAmountsAndSetsErrorOnBatchFailure() = runTest {
        val user1 = User(id = "user-1", name = "Alice", balance = 5000, isActive = true)
        val billingList = BillingList(
            id = "list-1",
            name = "Monthly Contributions",
            type = BillingListType.VARIABLE,
            users = listOf(BillingListUser(id = "blu-1", listId = "list-1", userId = "user-1", quantity = 1))
        )
        val userRepo = TestUserRepository(listOf(user1))
        val listRepo = TestBillingListRepository(listOf(billingList))
        val txRepo = TestTransactionRepository().apply {
            batchResult = false // Simulate server error or network drop
        }

        val viewModel = AdminBulkBillingViewModel(listRepo, userRepo, txRepo)
        viewModel.setVariableAmount("user-1", 2500)

        var completedSuccess: Boolean? = null
        viewModel.executeCharges(billingList) { success ->
            completedSuccess = success
        }

        assertFalse(completedSuccess ?: true, "onComplete should receive false on batch failure")
        assertEquals(2500L, viewModel.variableAmounts.value["user-1"], "Variable amounts must NOT be erased on failure")
        assertNotNull(viewModel.errorMessage.value, "Error message must be set when batch fails")
    }

    @Test
    fun testBulkBilling_clearsAmountsOnSuccess() = runTest {
        val user1 = User(id = "user-1", name = "Alice", balance = 5000, isActive = true)
        val billingList = BillingList(
            id = "list-1",
            name = "Fixed Subscriptions",
            type = BillingListType.FIXED,
            basePrice = 1500,
            users = listOf(BillingListUser(id = "blu-1", listId = "list-1", userId = "user-1", quantity = 2))
        )
        val userRepo = TestUserRepository(listOf(user1))
        val listRepo = TestBillingListRepository(listOf(billingList))
        val txRepo = TestTransactionRepository().apply {
            batchResult = true
        }

        val viewModel = AdminBulkBillingViewModel(listRepo, userRepo, txRepo)

        var completedSuccess: Boolean? = null
        viewModel.executeCharges(billingList) { success ->
            completedSuccess = success
        }

        assertTrue(completedSuccess == true, "onComplete should receive true on batch success")
        assertNull(viewModel.errorMessage.value, "Error message must be null on success")
        assertEquals(1, txRepo.recordedBatchRequests.size)

        val batch = txRepo.recordedBatchRequests.first()
        assertEquals(1, batch.size)
        val req = batch.first()
        // 2 units * 1500 cents = 3000 cents (R$ 30,00)
        assertEquals(-3000L, req.balanceDelta)
        assertEquals(1, req.transaction.items.size)
        assertEquals(2L, req.transaction.items.first().quantity)
        assertEquals(1500L, req.transaction.items.first().unitPriceAtPurchase)
    }
}
