package de.joelneumann.lojinha.server

import de.joelneumann.lojinha.domain.model.*
import de.joelneumann.lojinha.domain.repository.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.*

class LojinhaAdminServerTest {

    private lateinit var server: LojinhaAdminServer
    private val testPort = 18085
    private val client = HttpClient.newHttpClient()

    private val dummySettings = SystemSettings(
        adminPasswordHash = "secretPass123"
    )

    private val fakeSettingsRepo = object : SettingsRepository {
        override suspend fun getSettings(): SystemSettings = dummySettings
        override fun getSettingsFlow(): Flow<SystemSettings> = flowOf(dummySettings)
        override suspend fun updateSettings(settings: SystemSettings, notifyDataChanged: Boolean) {}
    }

    private val fakeProductRepo = object : ProductRepository {
        override fun getProductsFlow(): Flow<List<Product>> = flowOf(emptyList())
        override suspend fun getAllProducts(): List<Product> = emptyList()
        override suspend fun getProductById(id: String): Product? = null
        override suspend fun getProductByBarcode(barcode: String): Product? = null
        override suspend fun saveProduct(product: Product) {}
        override suspend fun deactivateProduct(id: String) {}
        override suspend fun hardDeleteProduct(id: String) {}
        override suspend fun updateStock(productId: String, delta: Long) {}
    }

    private val fakeUserRepo = object : UserRepository {
        override fun getUsersFlow(): Flow<List<User>> = flowOf(emptyList())
        override suspend fun getAllUsers(): List<User> = emptyList()
        override suspend fun getUserById(id: String): User? = null
        override suspend fun getUserByBarcode(barcode: String): User? = null
        override suspend fun saveUser(user: User) {}
        override suspend fun deactivateUser(id: String) {}
        override suspend fun softDeleteUser(id: String) {}
        override suspend fun restoreUser(id: String) {}
        override suspend fun canHardDeleteUser(id: String): Boolean = true
        override suspend fun hardDeleteUser(id: String): Boolean = true
        override suspend fun updateBalance(userId: String, amountDelta: Long) {}
    }

    private val fakeTransactionRepo = object : TransactionRepository {
        override fun getTransactionsFlow(): Flow<List<Transaction>> = flowOf(emptyList())
        override suspend fun getAllTransactions(): List<Transaction> = emptyList()
        override suspend fun getTransactionsByUserId(userId: String): List<Transaction> = emptyList()
        override suspend fun getTransactionById(id: String): Transaction? = null
        override suspend fun recordTransaction(transaction: Transaction) {}
        override suspend fun getTransactionCountForUser(userId: String): Int = 0
        override suspend fun getTransactionsPaged(page: Int, pageSize: Int, searchQuery: String?, typeFilter: TransactionType?): PagedResult<Transaction> =
            PagedResult(emptyList(), 0, page, pageSize, 0)
        override suspend fun getTransactionsByUserIdPaged(userId: String, page: Int, pageSize: Int, searchQuery: String?, typeFilter: TransactionType?): PagedResult<Transaction> =
            PagedResult(emptyList(), 0, page, pageSize, 0)
        override suspend fun getTransactionsByReferenceIds(referenceIds: List<String>): List<Transaction> = emptyList()
        override suspend fun getTransactionsByIds(ids: List<String>): List<Transaction> = emptyList()
        override suspend fun getCancellationCountForReference(refId: String): Int = 0
        override suspend fun executeAtomicTransaction(transaction: Transaction, balanceDelta: Long, stockDeltas: Map<String, Long>) {}
        override suspend fun applyPurchaseCorrection(originalTransactionId: String, newItems: List<TransactionItem>): Boolean = true
        override suspend fun stornoNonPurchase(transactionId: String): Boolean = true
        override suspend fun executeBatchTransactions(requests: List<AtomicTransactionRequest>): Boolean = true
    }

    private val fakeBillingListRepo = object : BillingListRepository {
        override fun getActiveBillingListsFlow(): Flow<List<BillingList>> = flowOf(emptyList())
        override suspend fun saveBillingList(list: BillingList) {}
        override suspend fun deleteBillingList(id: String) {}
        override suspend fun addUserToList(user: BillingListUser) {}
        override suspend fun removeUserFromList(listId: String, userId: String) {}
        override suspend fun removeAllUsersFromList(listId: String) {}
        override suspend fun updateUserQuantity(listId: String, userId: String, quantity: Int) {}
    }

    @BeforeTest
    fun setUp() {
        server = LojinhaAdminServer(
            productRepository = fakeProductRepo,
            userRepository = fakeUserRepo,
            transactionRepository = fakeTransactionRepo,
            billingListRepository = fakeBillingListRepo,
            settingsRepository = fakeSettingsRepo,
            port = testPort
        )
        server.start()
        Thread.sleep(500)
    }

    @AfterTest
    fun tearDown() {
        server.stop()
        Thread.sleep(300)
    }

    @Test
    fun testAuthenticationFlowAndTokenProtection() {
        // 1. Unauthenticated request to protected endpoint should return 401
        val unauthReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/users"))
            .GET()
            .build()
        val unauthRes = client.send(unauthReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(401, unauthRes.statusCode())

        // 2. Login with wrong password
        val wrongLoginReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/login"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("""{"password":"wrongPassword"}"""))
            .build()
        val wrongLoginRes = client.send(wrongLoginReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, wrongLoginRes.statusCode())
        assertTrue(wrongLoginRes.body().contains("\"success\":false") || wrongLoginRes.body().contains("\"success\": false"))

        // 3. Login with correct password
        val okLoginReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/login"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("""{"password":"secretPass123"}"""))
            .build()
        val okLoginRes = client.send(okLoginReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, okLoginRes.statusCode())
        assertTrue(okLoginRes.body().contains("\"success\":true") || okLoginRes.body().contains("\"success\": true"))

        // Extract token from response json
        val body = okLoginRes.body()
        val tokenRegex = "\"token\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        val matchResult = tokenRegex.find(body)
        assertNotNull(matchResult, "Token should be present in login response")
        val token = matchResult.groupValues[1]
        assertTrue(token.isNotBlank())

        // 4. Authenticated request with Bearer token should succeed (200 OK)
        val authReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/users"))
            .header("Authorization", "Bearer $token")
            .GET()
            .build()
        val authRes = client.send(authReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, authRes.statusCode())

        // 5. Authenticated request to bulk billing lists with Bearer token
        val billingReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/billing-lists"))
            .header("Authorization", "Bearer $token")
            .GET()
            .build()
        val billingRes = client.send(billingReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, billingRes.statusCode())

        // 6. Logout
        val logoutReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/logout"))
            .header("Authorization", "Bearer $token")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build()
        val logoutRes = client.send(logoutReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, logoutRes.statusCode())

        // 7. Former token is now invalid -> 401
        val revokedReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/users"))
            .header("Authorization", "Bearer $token")
            .GET()
            .build()
        val revokedRes = client.send(revokedReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(401, revokedRes.statusCode())
    }

    @Test
    fun testSettingsEndpointsAreRemoved() {
        // Log in to obtain valid token
        val loginReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/login"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("""{"password":"secretPass123"}"""))
            .build()
        val loginRes = client.send(loginReq, HttpResponse.BodyHandlers.ofString())
        val tokenRegex = "\"token\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        val token = tokenRegex.find(loginRes.body())!!.groupValues[1]

        // GET /api/admin/settings should return 404 (endpoint removed)
        val getSettingsReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/settings"))
            .header("Authorization", "Bearer $token")
            .GET()
            .build()
        val getSettingsRes = client.send(getSettingsReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(404, getSettingsRes.statusCode())

        // POST /api/admin/settings should return 404
        val postSettingsReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/settings"))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{}"))
            .build()
        val postSettingsRes = client.send(postSettingsReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(404, postSettingsRes.statusCode())
    }

    @Test
    fun testDirectoryTraversalBlocked() {
        // Attempt path traversal via static asset route
        val traversalReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/../../../../etc/passwd"))
            .GET()
            .build()
        val traversalRes = client.send(traversalReq, HttpResponse.BodyHandlers.ofString())
        assertTrue(traversalRes.statusCode() == 404 || traversalRes.statusCode() == 400)
        assertFalse(traversalRes.body().contains("root:"))
    }

    @Test
    fun testRateLimitingOnRepeatedFailedLogins() {
        val wrongLoginReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/login"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("""{"password":"wrongPassword"}"""))
            .build()

        // Send 5 failed attempts
        repeat(5) {
            val res = client.send(wrongLoginReq, HttpResponse.BodyHandlers.ofString())
            assertEquals(200, res.statusCode())
            assertTrue(res.body().contains("\"success\":false") || res.body().contains("\"success\": false"))
        }

        // 6th attempt should be blocked with 429 Too Many Requests
        val rateLimitedRes = client.send(wrongLoginReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(429, rateLimitedRes.statusCode())
        assertTrue(rateLimitedRes.body().contains("Too many failed login attempts"))
    }

    private fun getValidToken(): String {
        val loginReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/login"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("""{"password":"secretPass123"}"""))
            .build()
        val loginRes = client.send(loginReq, HttpResponse.BodyHandlers.ofString())
        val tokenRegex = "\"token\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        return tokenRegex.find(loginRes.body())!!.groupValues[1]
    }

    @Test
    fun testCanDeleteUserEndpoint() {
        val token = getValidToken()
        val req = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/users/can-delete/user123"))
            .header("Authorization", "Bearer $token")
            .GET()
            .build()
        val res = client.send(req, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, res.statusCode())
        assertTrue(res.body().contains("\"canDelete\":true") || res.body().contains("\"canDelete\": true"))
    }

    @Test
    fun testBatchTransactionsEndpoint() {
        val token = getValidToken()
        val reqObj = listOf(
            AtomicTransactionRequest(
                transaction = Transaction(
                    id = "tx1",
                    userId = "u1",
                    userNameSnapshot = "User 1",
                    timestamp = 123456789L,
                    type = TransactionType.PURCHASE,
                    totalAmount = 1500L,
                    items = listOf(
                        TransactionItem(
                            productId = "p1",
                            productName = "Item 1",
                            quantity = 1L,
                            unitPriceAtPurchase = 1500L
                        )
                    )
                ),
                balanceDelta = -1500L,
                stockDeltas = emptyMap()
            )
        )
        val payload = Json.encodeToString(reqObj)

        val req = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/transactions/batch"))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build()
        val res = client.send(req, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, res.statusCode())
    }

    @Test
    fun testRootRouteServesHtmlNotDirectoryListing() {
        val req = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/"))
            .GET()
            .build()
        val res = client.send(req, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, res.statusCode())
        val contentType = res.headers().firstValue("Content-Type").orElse("")
        assertTrue(contentType.contains("text/html"), "Content-Type must be text/html, was: $contentType")
        assertTrue(res.body().contains("<html") || res.body().contains("<!DOCTYPE html>"), "Body must contain HTML")
        assertFalse(res.body().contains("037f170986f544477491.wasm\n"), "Body must not be a directory listing")
    }
}

