package de.joelneumann.lojinha.ui.viewmodel

import de.joelneumann.lojinha.domain.model.*
import de.joelneumann.lojinha.domain.repository.TransactionRepository
import de.joelneumann.lojinha.domain.repository.UserRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private class FakeTransactionRepository : TransactionRepository {
    val transactions = mutableListOf<Transaction>()
    var lastSearchQuery: String? = null
    var lastTypeFilter: TransactionType? = null

    override fun getTransactionsFlow(): Flow<List<Transaction>> = emptyFlow()
    override suspend fun getAllTransactions(): List<Transaction> = transactions
    override suspend fun getTransactionsByUserId(userId: String): List<Transaction> =
        transactions.filter { it.userId == userId }
    override suspend fun getTransactionById(id: String): Transaction? =
        transactions.find { it.id == id }
    override suspend fun recordTransaction(transaction: Transaction) {
        transactions.add(0, transaction)
    }
    override suspend fun getTransactionCountForUser(userId: String): Int =
        transactions.count { it.userId == userId }

    override suspend fun getTransactionsPaged(
        page: Int,
        pageSize: Int,
        searchQuery: String?,
        typeFilter: TransactionType?
    ): PagedResult<Transaction> = PagedResult(transactions, transactions.size, 0, pageSize, 1)

    override suspend fun getTransactionsByUserIdPaged(
        userId: String,
        page: Int,
        pageSize: Int,
        searchQuery: String?,
        typeFilter: TransactionType?
    ): PagedResult<Transaction> {
        lastSearchQuery = searchQuery
        lastTypeFilter = typeFilter
        var filtered = transactions.filter { it.userId == userId }
        if (!searchQuery.isNullOrBlank()) {
            filtered = filtered.filter { it.note?.contains(searchQuery, ignoreCase = true) == true }
        }
        if (typeFilter != null) {
            filtered = filtered.filter { it.type == typeFilter }
        }
        return PagedResult(filtered, filtered.size, page, pageSize, 1)
    }

    override suspend fun getTransactionsByReferenceIds(referenceIds: List<String>): List<Transaction> = emptyList()
    override suspend fun getTransactionsByIds(ids: List<String>): List<Transaction> = emptyList()
    override suspend fun getCancellationCountForReference(refId: String): Int = 0
    override suspend fun executeAtomicTransaction(
        transaction: Transaction,
        balanceDelta: Long,
        stockDeltas: Map<String, Long>
    ) {
        transactions.add(0, transaction)
    }
    override suspend fun applyPurchaseCorrection(originalTransactionId: String, newItems: List<TransactionItem>): Boolean = true
    override suspend fun stornoNonPurchase(transactionId: String): Boolean = true
    override suspend fun executeBatchTransactions(requests: List<de.joelneumann.lojinha.domain.model.AtomicTransactionRequest>): Boolean = true
}

private class TxFakeUserRepository : UserRepository {
    override fun getUsersFlow(): Flow<List<User>> = emptyFlow()
    override suspend fun getAllUsers(): List<User> = emptyList()
    override suspend fun getUserById(id: String): User? = null
    override suspend fun getUserByBarcode(barcode: String): User? = null
    override suspend fun saveUser(user: User) {}
    override suspend fun deactivateUser(id: String) {}
    override suspend fun softDeleteUser(id: String) {}
    override suspend fun restoreUser(id: String) {}
    override suspend fun canHardDeleteUser(id: String): Boolean = false
    override suspend fun hardDeleteUser(id: String): Boolean = false
    override suspend fun updateBalance(userId: String, amountDelta: Long) {}
}

class TransactionHistoryViewModelTest {

    @Test
    fun testLoadUserTransactions_resetsFiltersByDefault() = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val txRepo = FakeTransactionRepository()
        val userRepo = TxFakeUserRepository()

        val vm = TransactionHistoryViewModel(txRepo, userRepo, testScope)

        try {
            // Apply initial filters
            vm.updateSearchFilter("existing-search")
            vm.selectTypeFilter(TransactionType.PURCHASE)
            delay(50)

            assertEquals("existing-search", vm.searchFilter.value)
            assertEquals(TransactionType.PURCHASE, vm.selectedTypeFilter.value)

            // Now call loadUserTransactions(resetFilters = true)
            vm.loadUserTransactions("user-1", resetFilters = true)
            delay(50)

            assertEquals("", vm.searchFilter.value, "Search filter should be cleared")
            assertNull(vm.selectedTypeFilter.value, "Type filter should be reset to null")
            assertEquals(0, vm.currentPage.value, "Page should be reset to 0")
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun testResetFilters_clearsFiltersAndFetchesPage() = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val txRepo = FakeTransactionRepository()
        val userRepo = TxFakeUserRepository()

        val vm = TransactionHistoryViewModel(txRepo, userRepo, testScope)

        try {
            vm.loadUserTransactions("user-1")
            delay(50)

            vm.updateSearchFilter("filtered")
            vm.selectTypeFilter(TransactionType.ADMIN_DEPOSIT)
            delay(50)

            assertEquals("filtered", vm.searchFilter.value)
            assertEquals(TransactionType.ADMIN_DEPOSIT, vm.selectedTypeFilter.value)

            vm.resetFilters()
            delay(50)

            assertEquals("", vm.searchFilter.value)
            assertNull(vm.selectedTypeFilter.value)
            assertEquals(0, vm.currentPage.value)
            assertEquals("", txRepo.lastSearchQuery)
            assertNull(txRepo.lastTypeFilter)
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun testSaveUserSettings_hashesPinAndInvokesCallback() = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val txRepo = FakeTransactionRepository()
        val userRepo = TxFakeUserRepository()

        val vm = TransactionHistoryViewModel(txRepo, userRepo, testScope)
        val user = User(
            id = "user-1",
            name = "Test User",
            pin = null,
            balance = 500,
            language = Language.DE
        )

        try {
            vm.openSettingsModal(user)
            assertEquals(true, vm.showSettingsModal.value)

            vm.updatePinInput("4321")
            vm.updateLanguage(Language.BR)

            var callbackInvoked = false
            var returnedUser: User? = null

            vm.saveUserSettings(user) { updated ->
                callbackInvoked = true
                returnedUser = updated
            }

            assertEquals(true, callbackInvoked)
            assertEquals(false, vm.showSettingsModal.value)
            assertEquals(Language.BR, returnedUser?.language)
            assertEquals(de.joelneumann.lojinha.security.PasswordHasher.hash("4321"), returnedUser?.pin)
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun testFetchPage_handlesExceptionGracefully() = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val failingRepo = object : FakeTransactionRepository() {
            override suspend fun getTransactionsByUserIdPaged(
                userId: String,
                page: Int,
                pageSize: Int,
                searchQuery: String?,
                typeFilter: TransactionType?
            ): PagedResult<Transaction> {
                throw IllegalStateException("Database query failed")
            }
        }
        val userRepo = TxFakeUserRepository()

        val vm = TransactionHistoryViewModel(failingRepo, userRepo, testScope)

        try {
            vm.loadUserTransactions("user-1")
            delay(50)

            // Should remain empty and not crash the scope
            assertEquals(emptyList(), vm.transactions.value)
            assertEquals(0, vm.totalCount.value)
        } finally {
            testScope.cancel()
        }
    }
}
