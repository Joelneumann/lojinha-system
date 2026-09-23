package de.joelneumann.lojinha.ui.viewmodel

import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.domain.repository.UserRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.*

class FakeUserRepository(initialUsers: List<User> = emptyList()) : UserRepository {
    val usersFlow = MutableStateFlow(initialUsers)

    override fun getUsersFlow(): Flow<List<User>> = usersFlow
    override suspend fun getAllUsers(): List<User> = usersFlow.value
    override suspend fun getUserById(id: String): User? = usersFlow.value.firstOrNull { it.id == id }
    override suspend fun getUserByBarcode(barcode: String): User? {
        val clean = barcode.trim()
        return usersFlow.value
            .sortedWith(compareBy<User> { it.isDeleted }.thenByDescending { it.isActive })
            .firstOrNull {
                (it.userBarcode != null && it.userBarcode.equals(clean, ignoreCase = true)) ||
                (it.userBarcodeNumber != null && it.userBarcodeNumber.equals(clean, ignoreCase = true))
            }
    }
    override suspend fun saveUser(user: User) {
        val list = usersFlow.value.toMutableList()
        val idx = list.indexOfFirst { it.id == user.id }
        if (idx != -1) list[idx] = user else list.add(user)
        usersFlow.value = list
    }
    override suspend fun deactivateUser(id: String) {}
    override suspend fun softDeleteUser(id: String) {}
    override suspend fun restoreUser(id: String) {}
    override suspend fun canHardDeleteUser(id: String): Boolean = true
    override suspend fun hardDeleteUser(id: String): Boolean = true
    override suspend fun updateBalance(userId: String, amountDelta: Long) {}
}

class UserSelectionViewModelTest {

    private val userAlice = User(id = "1", name = "Alice", pin = null)
    private val userBobWithPin = User(id = "2", name = "Bob", pin = de.joelneumann.lojinha.security.PasswordHasher.hash("1234"))
    private val userCarlos = User(id = "3", name = "Carlos", pin = null)
    private val userCarlosEduardo = User(id = "4", name = "Carlos Eduardo", pin = null)
    private val userJoao = User(id = "5", name = "João", pin = null)
    private val userBarcode = User(id = "6", name = "Barcode User", userBarcode = "BAR-100", userBarcodeNumber = "BAR-100", pin = null)
    private val userInactive = User(id = "7", name = "Inactive User", userBarcode = "INACTIVE", userBarcodeNumber = "INACTIVE", isActive = false)
    private val userDeleted = User(id = "8", name = "Deleted User", userBarcode = "DELETED", userBarcodeNumber = "DELETED", isDeleted = true)

    private val allTestUsers = listOf(
        userAlice, userBobWithPin, userCarlos, userCarlosEduardo,
        userJoao, userBarcode, userInactive, userDeleted
    )

    private fun withViewModel(
        users: List<User> = allTestUsers,
        block: suspend (UserSelectionViewModel, FakeUserRepository) -> Unit
    ) = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repo = FakeUserRepository(users)
        val vm = UserSelectionViewModel(repo, testScope)
        try {
            block(vm, repo)
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun testLoadUsers_collectsOnlyActiveAndNonDeletedUsersSorted() = withViewModel { vm, _ ->
        delay(50)
        val users = vm.users.value
        assertEquals(6, users.size)
        assertFalse(users.any { it.id == "7" || it.id == "8" })
        assertEquals("Alice", users[0].name)
    }

    @Test
    fun testLoadUsers_multipleInvocationsDoNotFailOrLeak() = withViewModel { vm, _ ->
        delay(50)
        assertEquals(6, vm.users.value.size)

        vm.loadUsers()
        vm.loadUsers()
        vm.loadUsers()
        delay(50)

        assertEquals(6, vm.users.value.size)
    }

    @Test
    fun testResetState_clearsAllInputsAndFlags() = withViewModel { vm, _ ->
        vm.updateSearchQuery("test query")
        vm.onUserCardClicked(userBobWithPin) {}
        vm.updatePinInput("9999")
        vm.openAdminAuthDialog()
        vm.updateAdminPassword("pass")

        assertEquals("test query", vm.searchQuery.value)
        assertEquals(userBobWithPin, vm.selectedUserForPin.value)
        assertEquals("9999", vm.pinInput.value)
        assertTrue(vm.showAdminAuthDialog.value)
        assertEquals("pass", vm.adminPasswordInput.value)

        vm.resetState()

        assertEquals("", vm.searchQuery.value)
        assertNull(vm.selectedUserForPin.value)
        assertEquals("", vm.pinInput.value)
        assertNull(vm.pinError.value)
        assertFalse(vm.showAdminAuthDialog.value)
        assertEquals("", vm.adminPasswordInput.value)
        assertNull(vm.adminPasswordError.value)
    }

    @Test
    fun testSearchSubmitted_exactNameMatchTakesPrecedenceOverSubstring() = withViewModel(listOf(userCarlos, userCarlosEduardo)) { vm, _ ->
        delay(50)

        var loggedInUser: User? = null
        vm.updateSearchQuery("Carlos")
        vm.onSearchSubmitted { loggedInUser = it }

        delay(50)
        assertNotNull(loggedInUser)
        assertEquals("3", loggedInUser?.id)
        assertEquals("Carlos", loggedInUser?.name)
        assertEquals("", vm.searchQuery.value)
    }

    @Test
    fun testSearchSubmitted_accentInsensitiveExactMatch() = withViewModel(listOf(userJoao)) { vm, _ ->
        delay(50)

        var loggedInUser: User? = null
        vm.updateSearchQuery("Joao")
        vm.onSearchSubmitted { loggedInUser = it }

        delay(50)
        assertNotNull(loggedInUser)
        assertEquals("5", loggedInUser?.id)
        assertEquals("João", loggedInUser?.name)
        assertEquals("", vm.searchQuery.value)
    }

    @Test
    fun testSearchSubmitted_barcodeResolutionCaseInsensitive() = withViewModel(listOf(userBarcode)) { vm, _ ->
        delay(50)

        var loggedInUser: User? = null
        vm.updateSearchQuery("bar-100")
        vm.onSearchSubmitted { loggedInUser = it }

        delay(50)
        assertNotNull(loggedInUser)
        assertEquals("6", loggedInUser?.id)
        assertEquals("", vm.searchQuery.value)
    }

    @Test
    fun testSearchSubmitted_deactivatedOrDeletedBarcodeIgnored() = withViewModel(listOf(userInactive, userDeleted)) { vm, _ ->
        delay(50)

        var loggedInUser: User? = null
        vm.updateSearchQuery("INACTIVE")
        vm.onSearchSubmitted { loggedInUser = it }

        delay(50)
        assertNull(loggedInUser)

        vm.updateSearchQuery("DELETED")
        vm.onSearchSubmitted { loggedInUser = it }

        delay(50)
        assertNull(loggedInUser)
    }

    @Test
    fun testSearchQueryClearedOnDirectCardClickWithoutPin() = withViewModel(listOf(userAlice)) { vm, _ ->
        delay(50)

        vm.updateSearchQuery("Al")
        assertEquals("Al", vm.searchQuery.value)

        var loggedInUser: User? = null
        vm.onUserCardClicked(userAlice) { loggedInUser = it }

        assertEquals(userAlice, loggedInUser)
        assertEquals("", vm.searchQuery.value)
    }

    @Test
    fun testSearchQueryClearedOnSuccessfulPinSubmit() = withViewModel(listOf(userBobWithPin)) { vm, _ ->
        delay(50)

        vm.updateSearchQuery("Bo")
        vm.onUserCardClicked(userBobWithPin) {}
        assertEquals(userBobWithPin, vm.selectedUserForPin.value)
        assertEquals("Bo", vm.searchQuery.value)

        var loggedInUser: User? = null
        vm.updatePinInput("1234")
        vm.submitPin("admin") { loggedInUser = it }

        assertEquals(userBobWithPin, loggedInUser)
        assertNull(vm.selectedUserForPin.value)
        assertEquals("", vm.searchQuery.value)
        assertEquals("", vm.pinInput.value)
    }

    @Test
    fun testPinSubmission_wrongPinSetsErrorAndDoesNotLogIn() = withViewModel(listOf(userBobWithPin)) { vm, _ ->
        delay(50)

        vm.onUserCardClicked(userBobWithPin) {}
        var loggedInUser: User? = null

        vm.updatePinInput("wrongPin")
        vm.submitPin("adminSecret") { loggedInUser = it }

        assertNull(loggedInUser)
        assertEquals(userBobWithPin, vm.selectedUserForPin.value)
        assertEquals("pin_incorrect", vm.pinError.value)
        assertEquals("", vm.pinInput.value)
    }

    @Test
    fun testPinSubmission_acceptsTrailingAndLeadingWhitespace() = withViewModel(listOf(userBobWithPin)) { vm, _ ->
        delay(50)

        vm.onUserCardClicked(userBobWithPin) {}
        var loggedInUser: User? = null

        vm.updatePinInput("  1234  ")
        vm.submitPin("adminSecret") { loggedInUser = it }

        assertEquals(userBobWithPin, loggedInUser)
        assertNull(vm.selectedUserForPin.value)
        assertEquals("", vm.pinInput.value)
    }

    @Test
    fun testCancelPinDialogPreservesSearchQueryFromSearchSubmitted() = withViewModel(listOf(userBobWithPin)) { vm, _ ->
        delay(50)

        vm.updateSearchQuery("Bob")
        vm.onSearchSubmitted {}
        delay(50)

        assertEquals(userBobWithPin, vm.selectedUserForPin.value)
        assertEquals("Bob", vm.searchQuery.value)

        vm.cancelPinDialog()
        assertNull(vm.selectedUserForPin.value)
        assertEquals("Bob", vm.searchQuery.value)
    }

    @Test
    fun testSearchSubmitted_prioritizesActiveUserOverSoftDeletedWithSameBarcode() = runBlocking {
        val activeUser = User(id = "10", name = "Active With Barcode", userBarcode = "BAR-SAME", userBarcodeNumber = "BAR-SAME", isActive = true, isDeleted = false)
        val deletedUser = User(id = "11", name = "Deleted With Barcode", userBarcode = "BAR-SAME", userBarcodeNumber = "BAR-SAME", isActive = false, isDeleted = true)

        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        // Pass deleted user first in list to simulate older DB insertion order
        val repo = FakeUserRepository(listOf(deletedUser, activeUser))
        val vm = UserSelectionViewModel(repo, testScope)
        try {
            delay(50)
            var loggedIn: User? = null
            vm.updateSearchQuery("BAR-SAME")
            vm.onSearchSubmitted { loggedIn = it }
            delay(50)

            assertNotNull(loggedIn)
            assertEquals("10", loggedIn?.id)
            assertEquals("Active With Barcode", loggedIn?.name)
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun testSearchSubmitted_reentrancyGuardWhenLoggingIn() = withViewModel(listOf(userAlice, userCarlos)) { vm, _ ->
        delay(50)

        var loginCount = 0
        vm.onUserCardClicked(userAlice) { loginCount++ }
        assertEquals(1, loginCount)

        vm.updateSearchQuery("Carlos")
        vm.onSearchSubmitted { loginCount++ }
        delay(50)

        assertEquals(1, loginCount)
    }

    @Test
    fun testReentrantLoginPreventedByGuard() = withViewModel(listOf(userAlice, userCarlos)) { vm, _ ->
        delay(50)

        var loginCount = 0
        val onLogin: (User) -> Unit = { loginCount++ }

        vm.onUserCardClicked(userAlice, onLogin)
        assertEquals(1, loginCount)

        vm.onUserCardClicked(userCarlos, onLogin)
        assertEquals(1, loginCount)

        vm.resetState()
        vm.onUserCardClicked(userCarlos, onLogin)
        assertEquals(2, loginCount)
    }

    @Test
    fun testAdminPasswordDialogLifecycle() = withViewModel { vm, _ ->
        assertFalse(vm.showAdminAuthDialog.value)
        vm.openAdminAuthDialog()
        assertTrue(vm.showAdminAuthDialog.value)

        vm.updateAdminPassword("secret")
        assertEquals("secret", vm.adminPasswordInput.value)

        val expectedHash = de.joelneumann.lojinha.security.PasswordHasher.hash("secret")
        var adminNavigated = false
        vm.submitAdminPassword("wrongPass") { adminNavigated = true }
        assertFalse(adminNavigated)
        assertEquals("admin_password_incorrect", vm.adminPasswordError.value)
        assertEquals("", vm.adminPasswordInput.value)
        assertTrue(vm.showAdminAuthDialog.value)

        vm.submitAdminPassword(expectedHash) { adminNavigated = true }
        assertTrue(adminNavigated)
        assertFalse(vm.showAdminAuthDialog.value)
        assertEquals("", vm.adminPasswordInput.value)
        assertNull(vm.adminPasswordError.value)
    }
}
