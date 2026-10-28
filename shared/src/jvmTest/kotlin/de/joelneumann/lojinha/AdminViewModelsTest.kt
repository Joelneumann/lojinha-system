package de.joelneumann.lojinha

import de.joelneumann.lojinha.domain.model.*
import de.joelneumann.lojinha.domain.repository.BackupRepository
import de.joelneumann.lojinha.domain.repository.BillingListRepository
import de.joelneumann.lojinha.domain.repository.ProductRepository
import de.joelneumann.lojinha.domain.repository.SettingsRepository
import de.joelneumann.lojinha.domain.repository.TransactionRepository
import de.joelneumann.lojinha.domain.repository.UserRepository
import de.joelneumann.lojinha.ui.utils.PlatformFile
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.data.service.CurrencyRateService
import de.joelneumann.lojinha.data.service.CurrencyRates
import de.joelneumann.lojinha.ui.viewmodel.admin.AdminBulkBillingViewModel
import de.joelneumann.lojinha.ui.viewmodel.admin.AdminProductsViewModel
import de.joelneumann.lojinha.ui.viewmodel.admin.AdminSettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
        var shouldThrowOnSave: Boolean = false
        var historyResult: PagedResult<ProductHistoryEntry> = PagedResult(emptyList<ProductHistoryEntry>(), 0, 1, 20, 1)
        var recordedAdjustments = mutableListOf<ProductStockAdjustment>()
        var requestedHistoryArgs = mutableListOf<Triple<String, Int, Int>>()
        var shouldThrowOnHistory: Boolean = false

        override fun getProductsFlow(): Flow<List<Product>> = flowOf(products)
        override suspend fun getAllProducts(): List<Product> = products
        override suspend fun getProductById(id: String): Product? = products.find { it.id == id }
        override suspend fun getProductByBarcode(barcode: String): Product? = null
        override suspend fun saveProduct(product: Product) {
            if (shouldThrowOnSave) {
                throw RuntimeException("Database error saving product")
            }
            savedProducts.add(product)
            products = if (products.any { it.id == product.id }) {
                products.map { if (it.id == product.id) product else it }
            } else {
                products + product
            }
        }
        override suspend fun deactivateProduct(id: String) {}
        override suspend fun hardDeleteProduct(id: String) {}
        override suspend fun updateStock(productId: String, delta: Long) {
            recordedStockDeltas.add(productId to delta)
            products = products.map {
                if (it.id == productId) it.copy(stockQuantity = it.stockQuantity + delta) else it
            }
        }
        override suspend fun getProductHistory(productId: String, page: Int, pageSize: Int): PagedResult<ProductHistoryEntry> {
            requestedHistoryArgs.add(Triple(productId, page, pageSize))
            if (shouldThrowOnHistory) {
                throw RuntimeException("Database error loading history")
            }
            return historyResult
        }
        override suspend fun recordStockAdjustment(adjustment: ProductStockAdjustment) {
            recordedAdjustments.add(adjustment)
        }
    }

    private class TestBillingListRepository(
        var lists: List<BillingList> = emptyList()
    ) : BillingListRepository {
        var updatedExecutionTimes = mutableMapOf<String, Long?>()

        override fun getActiveBillingListsFlow(): Flow<List<BillingList>> = flowOf(lists)
        override suspend fun saveBillingList(list: BillingList) {}
        override suspend fun updateLastExecutionTime(id: String, lastExecutionTime: Long?) {
            updatedExecutionTimes[id] = lastExecutionTime
            lists = lists.map {
                if (it.id == id) it.copy(lastExecutionTime = lastExecutionTime) else it
            }
        }
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

    private class TestSettingsRepository(
        initialSettings: SystemSettings = SystemSettings()
    ) : SettingsRepository {
        val flow = MutableStateFlow(initialSettings)
        var updatedSettings: SystemSettings? = null

        override fun getSettingsFlow(): Flow<SystemSettings> = flow
        override suspend fun getSettings(): SystemSettings = flow.value
        override suspend fun updateSettings(settings: SystemSettings, notifyDataChanged: Boolean) {
            updatedSettings = settings
            flow.value = settings
        }
    }

    private class TestBackupRepository(
        initialRoutines: List<BackupRoutine> = emptyList()
    ) : BackupRepository {
        val flow = MutableStateFlow(initialRoutines)
        var savedRoutines = mutableListOf<BackupRoutine>()
        var deletedRoutineIds = mutableListOf<String>()

        override fun getBackupsFlow(): Flow<List<BackupRoutine>> = flow
        override suspend fun getAllBackups(): List<BackupRoutine> = flow.value
        override suspend fun getBackupById(id: String): BackupRoutine? = flow.value.find { it.id == id }
        override suspend fun saveBackupRoutine(routine: BackupRoutine) {
            savedRoutines.add(routine)
            val current = flow.value.toMutableList()
            val idx = current.indexOfFirst { it.id == routine.id }
            if (idx >= 0) {
                current[idx] = routine
            } else {
                current.add(routine)
            }
            flow.value = current
        }
        override suspend fun deleteBackupRoutine(id: String) {
            deletedRoutineIds.add(id)
            flow.value = flow.value.filter { it.id != id }
        }
        override suspend fun deleteAllBackupRoutines() {
            flow.value = emptyList()
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

    @Test
    fun testAdjustProductStock_withNewBasePrice_updatesStockAndSavesProduct() = runTest {
        val initialProduct = Product(
            id = "prod-wac",
            name = "Coffee Beans",
            barcodes = emptyList(),
            basePrice = 200,
            unitType = UnitType.PIECE,
            stockQuantity = 10
        )
        val productRepo = TestProductRepository(listOf(initialProduct))
        val viewModel = AdminProductsViewModel(productRepo)

        // Adjust stock by +10 with calculated new base price 250
        viewModel.adjustProductStock("prod-wac", 10, newBasePrice = 250L)

        // Verify stock delta was updated
        assertEquals(1, productRepo.recordedStockDeltas.size)
        assertEquals("prod-wac" to 10L, productRepo.recordedStockDeltas.first())

        // Verify saveProduct was called to update basePrice
        assertEquals(1, productRepo.savedProducts.size)
        assertEquals(250L, productRepo.savedProducts.first().basePrice)
    }

    @Test
    fun testAdjustProductStock_withSameBasePrice_doesNotCallSaveProduct() = runTest {
        val initialProduct = Product(
            id = "prod-same-price",
            name = "Sugar",
            barcodes = emptyList(),
            basePrice = 200,
            unitType = UnitType.PIECE,
            stockQuantity = 10
        )
        val productRepo = TestProductRepository(listOf(initialProduct))
        val viewModel = AdminProductsViewModel(productRepo)

        // Adjust stock by +5 but newBasePrice is identical to existing (200L)
        viewModel.adjustProductStock("prod-same-price", 5, newBasePrice = 200L)

        assertEquals(1, productRepo.recordedStockDeltas.size)
        assertEquals("prod-same-price" to 5L, productRepo.recordedStockDeltas.first())
        assertTrue(productRepo.savedProducts.isEmpty())
    }

    @Test
    fun testSaveProduct_newProduct_doesNotCallUpdateStockAndSetsInitialStockCorrectly() = runTest {

        val productRepo = TestProductRepository(emptyList())
        val viewModel = AdminProductsViewModel(productRepo)

        viewModel.openNewProductModal()
        assertEquals(true, viewModel.showProductModal.value)
        assertEquals("", viewModel.editProduct.value?.id)

        val newProduct = Product(
            id = "",
            name = "Fresh Apples",
            barcodes = emptyList(),
            basePrice = 450,
            unitType = UnitType.WEIGHT,
            stockQuantity = 25000 // 25 kg in grams
        )

        viewModel.saveProduct(newProduct)

        // Must save the product directly with the initial stock
        assertEquals(1, productRepo.savedProducts.size)
        val saved = productRepo.savedProducts.first()
        assertEquals("Fresh Apples", saved.name)
        assertEquals(25000L, saved.stockQuantity)
        assertTrue(saved.id.isNotBlank())

        // P0 FIX: updateStock must NOT be called for new products (preventing doubled stock)
        assertTrue(productRepo.recordedStockDeltas.isEmpty())

        // Modal closed and saving state reset
        assertFalse(viewModel.showProductModal.value)
        assertNull(viewModel.editProduct.value)
        assertFalse(viewModel.isSavingProduct.value)
    }

    @Test
    fun testSaveProduct_existingProduct_calculatesDeltaAndCallsUpdateStock() = runTest {
        val existingProduct = Product(
            id = "prod-existing",
            name = "Juice Box",
            barcodes = emptyList(),
            basePrice = 300,
            unitType = UnitType.PIECE,
            stockQuantity = 10
        )
        val productRepo = TestProductRepository(listOf(existingProduct))
        val viewModel = AdminProductsViewModel(productRepo)

        viewModel.openEditProductModal(existingProduct)
        assertEquals(true, viewModel.showProductModal.value)
        assertEquals("prod-existing", viewModel.editProduct.value?.id)

        // Admin updates name and increases stock from 10 to 14 (+4)
        val modifiedProduct = existingProduct.copy(
            name = "Juice Box Orange",
            stockQuantity = 14
        )
        viewModel.saveProduct(modifiedProduct)

        assertEquals(1, productRepo.savedProducts.size)
        assertEquals("Juice Box Orange", productRepo.savedProducts.first().name)

        // Delta (+4) must be applied via updateStock
        assertEquals(1, productRepo.recordedStockDeltas.size)
        assertEquals("prod-existing" to 4L, productRepo.recordedStockDeltas.first())

        // Modal closed and saving state reset
        assertFalse(viewModel.showProductModal.value)
        assertNull(viewModel.editProduct.value)
        assertFalse(viewModel.isSavingProduct.value)
    }

    @Test
    fun testSaveProduct_handlesExceptionAndSetsProductErrorMessage() = runTest {
        val productRepo = TestProductRepository().apply {
            shouldThrowOnSave = true
        }
        val viewModel = AdminProductsViewModel(productRepo)

        viewModel.openNewProductModal()
        val product = Product(
            id = "",
            name = "Failure Product",
            barcodes = emptyList(),
            basePrice = 100,
            unitType = UnitType.PIECE,
            stockQuantity = 5
        )

        viewModel.saveProduct(product)

        // Error message set and isSavingProduct reset
        assertNotNull(viewModel.productErrorMessage.value)
        assertFalse(viewModel.isSavingProduct.value)

        // Dismiss error
        viewModel.clearProductError()
        assertNull(viewModel.productErrorMessage.value)
    }

    @Test
    fun testOpenProductHistoryModal_loadsHistoryAndClearsOnClose() = runTest {
        val testProduct = Product(
            id = "prod-history-1",
            name = "Mate Tea",
            barcodes = emptyList(),
            basePrice = 250,
            unitType = UnitType.PIECE,
            stockQuantity = 20
        )
        val historyEntries = listOf(
            ProductHistoryEntry(
                id = "h-1",
                productId = testProduct.id,
                timestamp = 1000L,
                type = ProductHistoryType.STOCK_ADJUSTMENT,
                quantity = 20,
                unitType = UnitType.PIECE,
                userName = null,
                note = "Initial stock",
                stockAfter = 20L
            ),
            ProductHistoryEntry(
                id = "h-2",
                productId = testProduct.id,
                timestamp = 2000L,
                type = ProductHistoryType.PURCHASE,
                quantity = 2,
                unitType = UnitType.PIECE,
                userName = "Alice",
                note = null,
                stockAfter = 18L
            )
        )
        val productRepo = TestProductRepository(listOf(testProduct)).apply {
            historyResult = PagedResult(historyEntries, 2, 1, 20, 1)
        }
        val viewModel = AdminProductsViewModel(productRepo)

        viewModel.openProductHistoryModal(testProduct)

        assertEquals(testProduct, viewModel.historyProduct.value)
        assertEquals(1, productRepo.requestedHistoryArgs.size)
        assertEquals(Triple("prod-history-1", 0, 25), productRepo.requestedHistoryArgs.first())
        assertEquals(2, viewModel.historyResult.value?.items?.size)
        assertEquals(2, viewModel.historyResult.value?.totalCount)
        assertEquals(20L, viewModel.historyResult.value?.items?.get(0)?.stockAfter)
        assertEquals(18L, viewModel.historyResult.value?.items?.get(1)?.stockAfter)
        assertFalse(viewModel.isLoadingHistory.value)

        viewModel.closeProductHistoryModal()
        assertNull(viewModel.historyProduct.value)
        assertNull(viewModel.historyResult.value)
    }

    @Test
    fun testProductHistoryPagination_updatesPageAndPageSize() = runTest {
        val testProduct = Product(
            id = "prod-history-2",
            name = "Coffee",
            barcodes = emptyList(),
            basePrice = 150,
            unitType = UnitType.PIECE,
            stockQuantity = 50
        )
        val productRepo = TestProductRepository(listOf(testProduct))
        val viewModel = AdminProductsViewModel(productRepo)

        viewModel.openProductHistoryModal(testProduct)
        assertEquals(0, viewModel.historyPage.value)
        assertEquals(25, viewModel.historyPageSize.value)

        viewModel.setHistoryPage(2)
        assertEquals(2, viewModel.historyPage.value)
        assertTrue(productRepo.requestedHistoryArgs.contains(Triple("prod-history-2", 2, 25)))

        viewModel.setHistoryPageSize(50)
        assertEquals(0, viewModel.historyPage.value) // resets to page 0
        assertEquals(50, viewModel.historyPageSize.value)
        assertTrue(productRepo.requestedHistoryArgs.contains(Triple("prod-history-2", 0, 50)))
    }

    @Test
    fun testAdjustProductStock_refreshesHistoryIfModalIsOpen() = runTest {
        val testProduct = Product(
            id = "prod-history-3",
            name = "Snack Bar",
            barcodes = emptyList(),
            basePrice = 120,
            unitType = UnitType.PIECE,
            stockQuantity = 10
        )
        val productRepo = TestProductRepository(listOf(testProduct))
        val viewModel = AdminProductsViewModel(productRepo)

        viewModel.openProductHistoryModal(testProduct)
        assertEquals(1, productRepo.requestedHistoryArgs.size)

        viewModel.adjustProductStock(testProduct.id, 5)
        assertEquals(2, productRepo.requestedHistoryArgs.size)
        assertEquals(Triple("prod-history-3", 0, 25), productRepo.requestedHistoryArgs.last())
        assertEquals(15L, viewModel.historyProduct.value?.stockQuantity)
    }

    @Test
    fun testOpenProductHistoryModal_handlesExceptionGracefully() = runTest {
        val testProduct = Product(
            id = "prod-history-4",
            name = "Sparkling Water",
            barcodes = emptyList(),
            basePrice = 80,
            unitType = UnitType.PIECE,
            stockQuantity = 30
        )
        val productRepo = TestProductRepository(listOf(testProduct)).apply {
            shouldThrowOnHistory = true
        }
        val viewModel = AdminProductsViewModel(productRepo)

        viewModel.openProductHistoryModal(testProduct)

        assertNotNull(viewModel.productErrorMessage.value)
        assertFalse(viewModel.isLoadingHistory.value)
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
        assertNotNull(listRepo.updatedExecutionTimes["list-1"], "lastExecutionTime must be recorded on batch success")
        assertEquals(1, listRepo.lists.first().users.size, "Users must NOT be removed from the list upon charge execution")

        val batch = txRepo.recordedBatchRequests.first()
        assertEquals(1, batch.size)
        val req = batch.first()
        // 2 units * 1500 cents = 3000 cents (R$ 30,00)
        assertEquals(-3000L, req.balanceDelta)
        assertEquals(1, req.transaction.items.size)
        assertEquals(2L, req.transaction.items.first().quantity)
        assertEquals(1500L, req.transaction.items.first().unitPriceAtPurchase)
    }

    @Test
    fun testBulkBilling_variableAmountsScopedToListId() = runTest {
        val user1 = User(id = "user-1", name = "Alice", balance = 5000, isActive = true)
        val listA = BillingList(
            id = "list-A",
            name = "Variable A",
            type = BillingListType.VARIABLE,
            users = listOf(BillingListUser(id = "blu-1", listId = "list-A", userId = "user-1", quantity = 1))
        )
        val listB = BillingList(
            id = "list-B",
            name = "Variable B",
            type = BillingListType.VARIABLE,
            users = listOf(BillingListUser(id = "blu-2", listId = "list-B", userId = "user-1", quantity = 1))
        )
        val userRepo = TestUserRepository(listOf(user1))
        val listRepo = TestBillingListRepository(listOf(listA, listB))
        val txRepo = TestTransactionRepository()

        val viewModel = AdminBulkBillingViewModel(listRepo, userRepo, txRepo)

        // Set variable amount for user-1 in list-A
        viewModel.setVariableAmount("list-A", "user-1", 1200L)
        assertEquals(1200L, viewModel.getVariableAmount("list-A", "user-1"))
        // list-B should NOT have any amount for user-1
        assertEquals(0L, viewModel.getVariableAmount("list-B", "user-1"))

        // Set variable amount for list-B
        viewModel.setVariableAmount("list-B", "user-1", 3400L)
        assertEquals(1200L, viewModel.getVariableAmount("list-A", "user-1"))
        assertEquals(3400L, viewModel.getVariableAmount("list-B", "user-1"))

        // Execute list-A: only list-A's amount should be cleared, list-B retained
        viewModel.executeCharges(listA)
        assertEquals(0L, viewModel.getVariableAmount("list-A", "user-1"))
        assertEquals(3400L, viewModel.getVariableAmount("list-B", "user-1"))
    }

    @Test
    fun testBulkBilling_zeroChargesAbortsWithoutUpdatingExecutionTime() = runTest {
        val user1 = User(id = "user-1", name = "Alice", balance = 5000, isActive = true)
        val billingList = BillingList(
            id = "list-zero",
            name = "Variable Zero",
            type = BillingListType.VARIABLE,
            lastExecutionTime = 1000L,
            users = listOf(BillingListUser(id = "blu-1", listId = "list-zero", userId = "user-1", quantity = 1))
        )
        val userRepo = TestUserRepository(listOf(user1))
        val listRepo = TestBillingListRepository(listOf(billingList))
        val txRepo = TestTransactionRepository()

        val viewModel = AdminBulkBillingViewModel(listRepo, userRepo, txRepo)

        var completedSuccess: Boolean? = null
        // No variable amounts entered -> zero charges
        viewModel.executeCharges(billingList) { success ->
            completedSuccess = success
        }

        assertFalse(completedSuccess ?: true, "Execution should fail when batch is empty")
        assertNotNull(viewModel.errorMessage.value)
        assertEquals(0, txRepo.recordedBatchRequests.size, "No transactions should be recorded")
        assertNull(listRepo.updatedExecutionTimes["list-zero"], "lastExecutionTime must not be updated")
    }

    @Test
    fun testBulkBilling_skipsInactiveAndDeletedUsers() = runTest {
        val userActive = User(id = "u-active", name = "Active", balance = 5000, isActive = true, isDeleted = false)
        val userInactive = User(id = "u-inactive", name = "Inactive", balance = 5000, isActive = false, isDeleted = false)
        val userDeleted = User(id = "u-deleted", name = "Deleted", balance = 5000, isActive = true, isDeleted = true)

        val billingList = BillingList(
            id = "list-mixed",
            name = "Mixed Status List",
            type = BillingListType.FIXED,
            basePrice = 2000L,
            users = listOf(
                BillingListUser(id = "blu-1", listId = "list-mixed", userId = "u-active", quantity = 1),
                BillingListUser(id = "blu-2", listId = "list-mixed", userId = "u-inactive", quantity = 1),
                BillingListUser(id = "blu-3", listId = "list-mixed", userId = "u-deleted", quantity = 1)
            )
        )

        val userRepo = TestUserRepository(listOf(userActive, userInactive, userDeleted))
        val listRepo = TestBillingListRepository(listOf(billingList))
        val txRepo = TestTransactionRepository()

        val viewModel = AdminBulkBillingViewModel(listRepo, userRepo, txRepo)

        var completedSuccess: Boolean? = null
        viewModel.executeCharges(billingList) { success ->
            completedSuccess = success
        }

        assertTrue(completedSuccess == true)
        assertEquals(1, txRepo.recordedBatchRequests.size)
        val batch = txRepo.recordedBatchRequests.first()
        // Only the active user should be charged
        assertEquals(1, batch.size)
        assertEquals("u-active", batch.first().transaction.userId)
        assertEquals(-2000L, batch.first().balanceDelta)
    }

    @Test
    fun testBulkBilling_skipsUsersWithZeroOrNegativeQuantity() = runTest {
        val user1 = User(id = "user-1", name = "Alice", balance = 5000, isActive = true)
        val user2 = User(id = "user-2", name = "Bob", balance = 5000, isActive = true)

        val billingList = BillingList(
            id = "list-qty-test",
            name = "Qty Test List",
            type = BillingListType.FIXED,
            basePrice = 1000L,
            users = listOf(
                BillingListUser(id = "blu-1", listId = "list-qty-test", userId = "user-1", quantity = 0),
                BillingListUser(id = "blu-2", listId = "list-qty-test", userId = "user-2", quantity = 2)
            )
        )

        val userRepo = TestUserRepository(listOf(user1, user2))
        val listRepo = TestBillingListRepository(listOf(billingList))
        val txRepo = TestTransactionRepository()

        val viewModel = AdminBulkBillingViewModel(listRepo, userRepo, txRepo)

        var completedSuccess: Boolean? = null
        viewModel.executeCharges(billingList) { success ->
            completedSuccess = success
        }

        assertTrue(completedSuccess == true)
        assertEquals(1, txRepo.recordedBatchRequests.size)
        val batch = txRepo.recordedBatchRequests.first()
        // user-1 with quantity=0 must be skipped, only user-2 charged
        assertEquals(1, batch.size)
        assertEquals("user-2", batch.first().transaction.userId)
        assertEquals(-2000L, batch.first().balanceDelta)
    }

    @Test
    fun testBulkBilling_clampsNegativeVariableAmounts() = runTest {
        val user1 = User(id = "user-1", name = "Alice", balance = 5000, isActive = true)
        val billingList = BillingList(
            id = "list-neg-var",
            name = "Negative Var List",
            type = BillingListType.VARIABLE,
            users = listOf(
                BillingListUser(id = "blu-1", listId = "list-neg-var", userId = "user-1", quantity = 1)
            )
        )

        val userRepo = TestUserRepository(listOf(user1))
        val listRepo = TestBillingListRepository(listOf(billingList))
        val txRepo = TestTransactionRepository()

        val viewModel = AdminBulkBillingViewModel(listRepo, userRepo, txRepo)

        // Attempt to set a negative amount
        viewModel.setVariableAmount("list-neg-var", "user-1", -5000L)
        assertEquals(0L, viewModel.getVariableAmount("list-neg-var", "user-1"))

        // Attempt to execute charges with zero amount should fail without recording transactions
        var completedSuccess: Boolean? = null
        viewModel.executeCharges(billingList) { success ->
            completedSuccess = success
        }

        assertFalse(completedSuccess ?: true)
        assertEquals(0, txRepo.recordedBatchRequests.size)
    }

    @Test
    fun testBulkBilling_deduplicatesUsersInBatchExecution() = runTest {
        val user1 = User(id = "user-1", name = "Alice", balance = 5000, isActive = true)
        val billingList = BillingList(
            id = "list-dedup",
            name = "Duplicate Members List",
            type = BillingListType.FIXED,
            basePrice = 1000L,
            users = listOf(
                BillingListUser(id = "blu-1", listId = "list-dedup", userId = "user-1", quantity = 1),
                BillingListUser(id = "blu-2", listId = "list-dedup", userId = "user-1", quantity = 1)
            )
        )

        val userRepo = TestUserRepository(listOf(user1))
        val listRepo = TestBillingListRepository(listOf(billingList))
        val txRepo = TestTransactionRepository()

        val viewModel = AdminBulkBillingViewModel(listRepo, userRepo, txRepo)

        var completedSuccess: Boolean? = null
        viewModel.executeCharges(billingList) { success ->
            completedSuccess = success
        }

        assertTrue(completedSuccess == true)
        assertEquals(1, txRepo.recordedBatchRequests.size)
        val batch = txRepo.recordedBatchRequests.first()
        // Deduplication must ensure only 1 transaction request is produced for user-1
        assertEquals(1, batch.size)
        assertEquals("user-1", batch.first().transaction.userId)
        assertEquals(-1000L, batch.first().balanceDelta)
    }

    @Test
    fun testBulkBilling_reactivatingUserMakesThemEligibleForCharges() = runTest {
        val user1 = User(id = "user-1", name = "Alice", balance = 5000, isActive = false)
        val billingList = BillingList(
            id = "list-reactivate",
            name = "Reactivate List",
            type = BillingListType.FIXED,
            basePrice = 1500L,
            users = listOf(
                BillingListUser(id = "blu-1", listId = "list-reactivate", userId = "user-1", quantity = 1)
            )
        )

        val userRepo = TestUserRepository(listOf(user1))
        val listRepo = TestBillingListRepository(listOf(billingList))
        val txRepo = TestTransactionRepository()

        val viewModel = AdminBulkBillingViewModel(listRepo, userRepo, txRepo)

        // While deactivated, charges abort with no valid charges
        var completedDeactivated: Boolean? = null
        viewModel.executeCharges(billingList) { success ->
            completedDeactivated = success
        }
        assertFalse(completedDeactivated ?: true)
        assertEquals(0, txRepo.recordedBatchRequests.size)

        // Reactivate user in repository
        userRepo.users = listOf(user1.copy(isActive = true))
        viewModel.refreshData()

        // Now charges succeed and charge the user
        var completedActive: Boolean? = null
        viewModel.executeCharges(billingList) { success ->
            completedActive = success
        }
        assertTrue(completedActive == true)
        assertEquals(1, txRepo.recordedBatchRequests.size)
        assertEquals("user-1", txRepo.recordedBatchRequests.first().first().transaction.userId)
    }

    @Test
    fun testBulkBilling_deleteListPurgesVariableAmounts() = runTest {
        val billingList = BillingList(
            id = "list-to-delete",
            name = "Delete Me",
            type = BillingListType.VARIABLE
        )
        val listRepo = TestBillingListRepository(listOf(billingList))
        val userRepo = TestUserRepository()
        val txRepo = TestTransactionRepository()

        val viewModel = AdminBulkBillingViewModel(listRepo, userRepo, txRepo)
        viewModel.setVariableAmount("list-to-delete", "user-1", 5000L)
        assertEquals(5000L, viewModel.getVariableAmount("list-to-delete", "user-1"))

        viewModel.deleteList("list-to-delete")
        assertEquals(0L, viewModel.getVariableAmount("list-to-delete", "user-1"))
    }

    // --- Tests for AdminSettingsViewModel ---

    @Test
    fun testAdminSettingsViewModel_updateSystemSettings_updatesRepositoryAndSetsStatusMessage() = runTest {
        val initialSettings = SystemSettings(supportEmail = "old@support.org", inactivityTimeoutMinutes = 15)
        val settingsRepo = TestSettingsRepository(initialSettings)
        val viewModel = AdminSettingsViewModel(settingsRepository = settingsRepo)

        val updated = initialSettings.copy(supportEmail = "new@support.org", inactivityTimeoutMinutes = 30)
        viewModel.updateSystemSettings(updated)

        assertEquals("new@support.org", settingsRepo.updatedSettings?.supportEmail)
        assertEquals(30, settingsRepo.updatedSettings?.inactivityTimeoutMinutes)
        assertEquals(I18n.get().statusSystemSettingsUpdated, viewModel.statusMessage.value)
    }

    @Test
    fun testAdminSettingsViewModel_updateSystemSettings_updatesBalanceWarningThresholds() = runTest {
        val initialSettings = SystemSettings(
            yellowBalanceWarningThresholdCents = 300_00L,
            redBalanceWarningThresholdCents = 0L
        )
        val settingsRepo = TestSettingsRepository(initialSettings)
        val viewModel = AdminSettingsViewModel(settingsRepository = settingsRepo)

        val updated = initialSettings.copy(
            yellowBalanceWarningThresholdCents = 150_00L,
            redBalanceWarningThresholdCents = -20_00L
        )
        viewModel.updateSystemSettings(updated)

        assertEquals(150_00L, settingsRepo.updatedSettings?.yellowBalanceWarningThresholdCents)
        assertEquals(-20_00L, settingsRepo.updatedSettings?.redBalanceWarningThresholdCents)
        assertEquals(I18n.get().statusSystemSettingsUpdated, viewModel.statusMessage.value)
    }

    @Test
    fun testAdminSettingsViewModel_routineLifecycle_createsEditsAndDeletes() = runTest {
        val backupRepo = TestBackupRepository()
        val settingsRepo = TestSettingsRepository()
        val viewModel = AdminSettingsViewModel(settingsRepository = settingsRepo, backupRepository = backupRepo)

        viewModel.openCreateRoutineDialog()
        assertTrue(viewModel.showRoutineDialog.value)
        assertNull(viewModel.editingRoutine.value)

        val newRoutine = BackupRoutine(id = "routine-1", name = "Daily DB Routine", isEnabled = true)
        viewModel.saveBackupRoutine(newRoutine)

        assertFalse(viewModel.showRoutineDialog.value)
        assertEquals(1, backupRepo.savedRoutines.size)
        assertEquals("Daily DB Routine", backupRepo.savedRoutines.first().name)
        assertEquals("Backup routine saved: 'Daily DB Routine'", viewModel.statusMessage.value)

        viewModel.openEditRoutineDialog(newRoutine)
        assertTrue(viewModel.showRoutineDialog.value)
        assertEquals(newRoutine, viewModel.editingRoutine.value)
        viewModel.closeRoutineDialog()
        assertFalse(viewModel.showRoutineDialog.value)

        viewModel.requestDeleteRoutine(newRoutine)
        assertEquals(newRoutine, viewModel.routineToDelete.value)
        viewModel.confirmDeleteRoutine()
        assertNull(viewModel.routineToDelete.value)
        assertTrue(backupRepo.deletedRoutineIds.contains("routine-1"))
        assertEquals("Backup routine 'Daily DB Routine' removed.", viewModel.statusMessage.value)
    }

    @Test
    fun testAdminSettingsViewModel_toggleBackupRoutine_updatesEnabledState() = runTest {
        val initialRoutine = BackupRoutine(id = "routine-toggle", name = "Hourly Interval", isEnabled = true)
        val backupRepo = TestBackupRepository(listOf(initialRoutine))
        val settingsRepo = TestSettingsRepository()
        val viewModel = AdminSettingsViewModel(settingsRepository = settingsRepo, backupRepository = backupRepo)

        viewModel.requestToggleRoutine(initialRoutine, false)
        assertEquals(initialRoutine to false, viewModel.routineToToggle.value)

        viewModel.confirmToggleRoutine()
        assertNull(viewModel.routineToToggle.value)
        assertEquals(false, backupRepo.savedRoutines.last().isEnabled)
        assertEquals("Backup routine 'Hourly Interval' disabled.", viewModel.statusMessage.value)
    }

    @Test
    fun testAdminSettingsViewModel_runRoutineNow_invokesCallbackAndHandlesErrors() = runTest {
        val routine = BackupRoutine(id = "routine-now", name = "Manual Run", isEnabled = true)
        val settingsRepo = TestSettingsRepository()
        var runCount = 0
        var shouldThrow = false

        val viewModel = AdminSettingsViewModel(
            settingsRepository = settingsRepo,
            onRunRoutineNow = {
                if (shouldThrow) throw IllegalStateException("Backup disk full")
                runCount++
            }
        )

        viewModel.runRoutineNow(routine)
        assertEquals(1, runCount)
        assertEquals("Triggered routine 'Manual Run'.", viewModel.statusMessage.value)
        assertNull(viewModel.errorMessage.value)

        shouldThrow = true
        viewModel.runRoutineNow(routine)
        assertEquals("Failed to run routine 'Manual Run': Backup disk full", viewModel.errorMessage.value)
    }

    @Test
    fun testAdminSettingsViewModel_exportSupportBundle_setsStatusMessageOnSuccess() = runTest {
        val settingsRepo = TestSettingsRepository()
        var exportedWithEmail: Boolean? = null
        var recipientUsed: String? = null

        val viewModel = AdminSettingsViewModel(
            settingsRepository = settingsRepo,
            onExportSupportBundle = { prepareEmail, recipient ->
                exportedWithEmail = prepareEmail
                recipientUsed = recipient
            }
        )

        viewModel.exportSupportBundle(prepareEmail = false, recipientEmail = "")
        assertEquals(false, exportedWithEmail)
        assertEquals(I18n.get().statusDiagnosticBundleExported, viewModel.statusMessage.value)

        viewModel.exportSupportBundle(prepareEmail = true, recipientEmail = "support@lojinha.local")
        assertEquals(true, exportedWithEmail)
        assertEquals("support@lojinha.local", recipientUsed)
        assertEquals(I18n.get().statusDiagnosticBundleExportedWithEmail, viewModel.statusMessage.value)
    }

    @Test
    fun testAdminSettingsViewModel_csvImportPreviewAndExecution_handlesErrorsAndSuccess() = runTest {
        val settingsRepo = TestSettingsRepository()
        val dummyFile = PlatformFile("products.csv", "/tmp/products.csv")

        var previewCalled = false
        var executeCalled = false

        val viewModel = AdminSettingsViewModel(
            settingsRepository = settingsRepo,
            onPreviewCsvImport = { file, type ->
                previewCalled = true
                CsvImportResult(
                    totalProcessed = 5,
                    addedCount = 3,
                    updatedCount = 2,
                    strippedBarcodesCount = 1,
                    errors = emptyList(),
                    warnings = listOf("Line 2: Barcode stripped")
                )
            },
            onExecuteCsvImport = { file, type ->
                executeCalled = true
                CsvImportResult(
                    totalProcessed = 5,
                    addedCount = 3,
                    updatedCount = 2,
                    strippedBarcodesCount = 1
                )
            }
        )

        viewModel.prepareCsvImport(dummyFile, "Products")
        assertTrue(previewCalled)
        assertEquals(dummyFile, viewModel.csvImportPreview.value?.first)
        assertEquals(5, viewModel.csvImportPreview.value?.second?.totalProcessed)

        viewModel.executeCsvImport()
        assertTrue(executeCalled)
        assertNull(viewModel.csvImportPreview.value)
        assertEquals(
            I18n.get().statusCsvImportSuccess(5, I18n.get().csvTypeProducts, 3, 2),
            viewModel.statusMessage.value
        )
    }

    @Test
    fun testAdminSettingsViewModel_backupNowLocal_dialogState() = runTest {
        val settingsRepo = TestSettingsRepository()
        val viewModel = AdminSettingsViewModel(settingsRepository = settingsRepo)

        assertFalse(viewModel.showBackupNowLocalDialog.value)
        viewModel.openBackupNowLocalDialog()
        assertTrue(viewModel.showBackupNowLocalDialog.value)
        viewModel.closeBackupNowLocalDialog()
        assertFalse(viewModel.showBackupNowLocalDialog.value)
    }

    @Test
    fun testAdminSettingsViewModel_backupNowLocal_successExecution() = runTest {
        val settingsRepo = TestSettingsRepository()
        var receivedPath: String? = null
        var receivedType: BackupFileType? = null
        var receivedMode: BackupWriteMode? = null

        val viewModel = AdminSettingsViewModel(
            settingsRepository = settingsRepo,
            onExecuteBackupNowLocal = { path, type, mode ->
                receivedPath = path
                receivedType = type
                receivedMode = mode
                listOf("lojinha_backup_20261008.db")
            }
        )

        viewModel.openBackupNowLocalDialog()
        assertTrue(viewModel.showBackupNowLocalDialog.value)

        viewModel.executeBackupNowLocal(
            destinationPath = "/custom/backups",
            fileType = BackupFileType.BOTH,
            writeMode = BackupWriteMode.OVERWRITE_LATEST
        )

        assertEquals("/custom/backups", receivedPath)
        assertEquals(BackupFileType.BOTH, receivedType)
        assertEquals(BackupWriteMode.OVERWRITE_LATEST, receivedMode)
        assertFalse(viewModel.showBackupNowLocalDialog.value)
        assertFalse(viewModel.isExecutingBackupNow.value)
        assertEquals("/custom/backups", viewModel.lastLocalBackupPath.value)
        assertEquals(
            I18n.get().statusBackupNowSuccess(1, "/custom/backups"),
            viewModel.statusMessage.value
        )
        assertNull(viewModel.errorMessage.value)
    }

    @Test
    fun testAdminSettingsViewModel_backupNowLocal_blankPathValidation() = runTest {
        val settingsRepo = TestSettingsRepository()
        var callbackCalled = false

        val viewModel = AdminSettingsViewModel(
            settingsRepository = settingsRepo,
            onExecuteBackupNowLocal = { _, _, _ ->
                callbackCalled = true
                emptyList()
            }
        )

        viewModel.openBackupNowLocalDialog()
        viewModel.executeBackupNowLocal(
            destinationPath = "   ",
            fileType = BackupFileType.DB
        )

        assertFalse(callbackCalled)
        assertTrue(viewModel.showBackupNowLocalDialog.value)
        assertEquals(I18n.get().pleaseSelectDestinationFolder, viewModel.errorMessage.value)
        assertNull(viewModel.statusMessage.value)
    }

    @Test
    fun testAdminSettingsViewModel_backupNowLocal_handlesException() = runTest {
        val settingsRepo = TestSettingsRepository()

        val viewModel = AdminSettingsViewModel(
            settingsRepository = settingsRepo,
            onExecuteBackupNowLocal = { _, _, _ ->
                throw IllegalStateException("Read-only filesystem")
            }
        )

        viewModel.openBackupNowLocalDialog()
        viewModel.executeBackupNowLocal(
            destinationPath = "/protected",
            fileType = BackupFileType.DB
        )

        assertFalse(viewModel.isExecutingBackupNow.value)
        assertEquals("Failed to create local backup: Read-only filesystem", viewModel.errorMessage.value)
        assertNull(viewModel.statusMessage.value)
    }

    private class FakeCurrencyRateService(
        var result: Result<CurrencyRates> = Result.success(CurrencyRates(usdRate = 0.1993, eurRate = 0.1782, timestamp = 1700000000000L, provider = "Frankfurter API"))
    ) : CurrencyRateService {
        var fetchCount = 0
        override suspend fun fetchRates(): Result<CurrencyRates> {
            fetchCount++
            return result
        }
    }

    @Test
    fun testAdminSettingsViewModel_fetchExchangeRates_success_updatesSettingsAndSetsStatusMessage() = runTest {
        val initialSettings = SystemSettings(usdExchangeRate = 0.18, eurExchangeRate = 0.16)
        val settingsRepo = TestSettingsRepository(initialSettings)
        val fakeService = FakeCurrencyRateService(
            Result.success(CurrencyRates(usdRate = 0.205, eurRate = 0.185, timestamp = 1720000000000L, provider = "Frankfurter API"))
        )
        val viewModel = AdminSettingsViewModel(
            settingsRepository = settingsRepo,
            currencyRateService = fakeService
        )

        var callbackUsd = 0.0
        var callbackEur = 0.0
        viewModel.fetchExchangeRates { u, e ->
            callbackUsd = u
            callbackEur = e
        }

        assertEquals(1, fakeService.fetchCount)
        assertEquals(0.205, callbackUsd)
        assertEquals(0.185, callbackEur)
        assertEquals(0.205, settingsRepo.updatedSettings?.usdExchangeRate)
        assertEquals(0.185, settingsRepo.updatedSettings?.eurExchangeRate)
        assertEquals(1720000000000L, settingsRepo.updatedSettings?.lastExchangeRateUpdateTimestamp)
        assertEquals(I18n.get().statusRatesUpdatedSuccess(0.205, 0.185, "Frankfurter API"), viewModel.statusMessage.value)
        assertNull(viewModel.errorMessage.value)
        assertFalse(viewModel.isFetchingRates.value)
    }

    @Test
    fun testAdminSettingsViewModel_fetchExchangeRates_failure_setsErrorMessageAndPreservesSettings() = runTest {
        val initialSettings = SystemSettings(usdExchangeRate = 0.18, eurExchangeRate = 0.16)
        val settingsRepo = TestSettingsRepository(initialSettings)
        val fakeService = FakeCurrencyRateService(
            Result.failure(IllegalStateException("No internet connection"))
        )
        val viewModel = AdminSettingsViewModel(
            settingsRepository = settingsRepo,
            currencyRateService = fakeService
        )

        viewModel.fetchExchangeRates()

        assertEquals(1, fakeService.fetchCount)
        assertNull(settingsRepo.updatedSettings)
        assertTrue(viewModel.errorMessage.value?.contains("No internet connection") == true)
        assertNull(viewModel.statusMessage.value)
        assertFalse(viewModel.isFetchingRates.value)
    }

    @Test
    fun testAdminSettingsViewModel_fetchExchangeRates_serviceNotConfigured_setsErrorMessage() = runTest {
        val settingsRepo = TestSettingsRepository()
        val viewModel = AdminSettingsViewModel(
            settingsRepository = settingsRepo,
            currencyRateService = null
        )

        viewModel.fetchExchangeRates()

        assertNull(settingsRepo.updatedSettings)
        assertTrue(viewModel.errorMessage.value?.contains("Service not available") == true)
        assertNull(viewModel.statusMessage.value)
        assertFalse(viewModel.isFetchingRates.value)
    }

    @Test
    fun testAdminSettingsViewModel_fetchExchangeRates_reentranceGuarded() = runTest {
        val settingsRepo = TestSettingsRepository()
        val fakeService = object : CurrencyRateService {
            var fetchCount = 0
            override suspend fun fetchRates(): Result<CurrencyRates> {
                fetchCount++
                kotlinx.coroutines.delay(100)
                return Result.success(CurrencyRates(usdRate = 0.20, eurRate = 0.18))
            }
        }
        val viewModel = AdminSettingsViewModel(
            settingsRepository = settingsRepo,
            currencyRateService = fakeService
        )

        viewModel.fetchExchangeRates()
        assertTrue(viewModel.isFetchingRates.value)

        // Call again while fetching
        viewModel.fetchExchangeRates()
        assertEquals(1, fakeService.fetchCount)
    }

    @Test
    fun testAdminSettingsViewModel_executeDbRestore_invokesCallbackWithPreserveParams() = runTest {
        val settingsRepo = TestSettingsRepository(SystemSettings())
        var restoredFile: de.joelneumann.lojinha.ui.utils.PlatformFile? = null
        var preservedRoutines = false
        var preservedPassword = false
        val viewModel = AdminSettingsViewModel(
            settingsRepository = settingsRepo,
            onExecuteDbRestore = { file, keepRoutines, keepPassword ->
                restoredFile = file
                preservedRoutines = keepRoutines
                preservedPassword = keepPassword
            }
        )
        val fakeFile = de.joelneumann.lojinha.ui.utils.PlatformFile("backup.db", "/path/to/backup.db")
        viewModel.executeDbRestore(fakeFile, preserveExistingRoutines = true, preserveCurrentAdminPassword = true)
        assertEquals(fakeFile, restoredFile)
        assertTrue(preservedRoutines)
        assertTrue(preservedPassword)
        assertEquals("Database restored successfully from backup.db.", viewModel.statusMessage.value)
    }

    @Test
    fun testAdminSettingsViewModel_executeWipeData_invokesCallbackWithPreserveParams() = runTest {
        val settingsRepo = TestSettingsRepository(SystemSettings())
        var preservedRoutines = true
        var preservedPassword = true
        val viewModel = AdminSettingsViewModel(
            settingsRepository = settingsRepo,
            onExecuteWipeData = { keepRoutines, keepPassword ->
                preservedRoutines = keepRoutines
                preservedPassword = keepPassword
            }
        )
        viewModel.executeWipeData(preserveBackupRoutines = false, preserveAdminPassword = false)
        assertFalse(preservedRoutines)
        assertFalse(preservedPassword)
        assertEquals("Factory Reset completed.", viewModel.statusMessage.value)
    }

    @Test
    fun testAdminSettingsViewModel_loadSettingsAndRoutines_reloadsLatestData() = runTest {
        val settingsRepo = TestSettingsRepository(SystemSettings(globalMarkupPercent = 10.0))
        val backupRepo = TestBackupRepository(listOf(
            BackupRoutine(id = "r1", name = "Daily", isEnabled = true, type = BackupType.LOCAL, fileType = BackupFileType.DB, writeMode = BackupWriteMode.CREATE_NEW_FILE, scheduleConfig = BackupScheduleConfig.Timed("02:00"), backupLocationPath = "/tmp")
        ))
        val viewModel = AdminSettingsViewModel(
            settingsRepository = settingsRepo,
            backupRepository = backupRepo
        )
        assertEquals(10.0, viewModel.settings.value.globalMarkupPercent)
        assertEquals(1, viewModel.routines.value.size)

        // Simulate restore or wipe changing the backing repository
        settingsRepo.flow.value = SystemSettings(globalMarkupPercent = 25.0)
        backupRepo.flow.value = emptyList()

        viewModel.loadSettings()
        viewModel.loadRoutines()

        assertEquals(25.0, viewModel.settings.value.globalMarkupPercent)
        assertEquals(0, viewModel.routines.value.size)
    }

    @Test
    fun testAppViewModel_reactiveReloadSettingsAndUser() = runTest {
        val settingsRepo = TestSettingsRepository(SystemSettings(inactivityTimeoutMinutes = 5))
        val alice = User(id = "u1", name = "Alice", balance = 1000)
        val userRepo = TestUserRepository(listOf(alice))
        val appViewModel = de.joelneumann.lojinha.ui.viewmodel.AppViewModel(userRepo, settingsRepo)

        assertEquals(5, appViewModel.settings.value.inactivityTimeoutMinutes)
        appViewModel.loginUser(alice)
        assertEquals("u1", appViewModel.currentUser.value?.id)

        // 1. Settings update
        settingsRepo.flow.value = SystemSettings(inactivityTimeoutMinutes = 15)
        appViewModel.loadSettings()
        assertEquals(15, appViewModel.settings.value.inactivityTimeoutMinutes)

        // 2. User update
        val updatedAlice = alice.copy(name = "Alice Updated", balance = 2000)
        userRepo.users = listOf(updatedAlice)
        appViewModel.refreshCurrentUser()
        assertEquals("Alice Updated", appViewModel.currentUser.value?.name)
        assertEquals(2000, appViewModel.currentUser.value?.balance)

        // 3. User wiped/deleted triggers logout
        userRepo.users = emptyList()
        appViewModel.refreshCurrentUser()
        assertNull(appViewModel.currentUser.value)
        assertEquals(de.joelneumann.lojinha.ui.viewmodel.AppScreen.MAIN_USER_SELECT, appViewModel.currentScreen.value)
    }
}

