package de.joelneumann.lojinha

import de.joelneumann.lojinha.domain.model.*
import de.joelneumann.lojinha.domain.repository.BackupRepository
import de.joelneumann.lojinha.domain.repository.BillingListRepository
import de.joelneumann.lojinha.domain.repository.ProductRepository
import de.joelneumann.lojinha.domain.repository.SettingsRepository
import de.joelneumann.lojinha.domain.repository.TransactionRepository
import de.joelneumann.lojinha.domain.repository.UserRepository
import de.joelneumann.lojinha.ui.utils.PlatformFile
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
        assertEquals("System settings updated successfully.", viewModel.statusMessage.value)
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
        assertEquals("Diagnostic bundle exported successfully.", viewModel.statusMessage.value)

        viewModel.exportSupportBundle(prepareEmail = true, recipientEmail = "support@lojinha.local")
        assertEquals(true, exportedWithEmail)
        assertEquals("support@lojinha.local", recipientUsed)
        assertEquals("Diagnostic bundle exported & email draft opened.", viewModel.statusMessage.value)
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
        assertEquals("Successfully imported 5 Products (3 added, 2 updated).", viewModel.statusMessage.value)
    }
}
