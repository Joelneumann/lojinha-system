package de.joelneumann.lojinha.server

import de.joelneumann.lojinha.data.service.VeggieImageService
import de.joelneumann.lojinha.domain.model.*
import de.joelneumann.lojinha.domain.repository.*
import de.joelneumann.lojinha.domain.service.VeggieRoundSettlementResult
import de.joelneumann.lojinha.domain.service.VeggieSettlementCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.*

class LojinhaAdminServerTest {

    private lateinit var server: LojinhaAdminServer
    private val testPort = 18085
    private val client = HttpClient.newHttpClient()

    private val dummySettings = SystemSettings(
        adminPasswordHash = de.joelneumann.lojinha.security.PasswordHasher.hash("secretPass123")
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
        override suspend fun getProductHistory(productId: String, page: Int, pageSize: Int): PagedResult<ProductHistoryEntry> =
            PagedResult(emptyList(), 0, page, pageSize, 0)
        override suspend fun recordStockAdjustment(adjustment: ProductStockAdjustment) {}
    }

    private val fakeUsers = mutableListOf(
        User(
            id = "u-alice",
            name = "Alice",
            balance = 2500L,
            pin = de.joelneumann.lojinha.security.PasswordHasher.hash("1234"),
            requirePinOnKiosk = false,
            language = Language.EN,
            isActive = true,
            isDeleted = false
        ),
        User(
            id = "u-nopin",
            name = "Bob NoPin",
            balance = 1000L,
            pin = null,
            requirePinOnKiosk = true,
            language = Language.BR,
            isActive = true,
            isDeleted = false
        )
    )

    private val fakeUserRepo = object : UserRepository {
        override fun getUsersFlow(): Flow<List<User>> = flowOf(fakeUsers)
        override suspend fun getAllUsers(): List<User> = fakeUsers.toList()
        override suspend fun getUserById(id: String): User? = fakeUsers.find { it.id == id }
        override suspend fun getUserByBarcode(barcode: String): User? = null
        override suspend fun saveUser(user: User) {
            fakeUsers.removeIf { it.id == user.id }
            fakeUsers.add(user)
        }
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
        override suspend fun updateLastExecutionTime(id: String, lastExecutionTime: Long?) {}
        override suspend fun deleteBillingList(id: String) {}
        override suspend fun addUserToList(user: BillingListUser) {}
        override suspend fun removeUserFromList(listId: String, userId: String) {}
        override suspend fun removeAllUsersFromList(listId: String) {}
        override suspend fun updateUserQuantity(listId: String, userId: String, quantity: Int) {}
    }

    private val fakeAdminNoteRepo = object : AdminNoteRepository {
        val notes = mutableListOf(
            AdminNote("note-init", "Need more sugar", "u1", "Alice", 1700000000000L)
        )
        override fun getNotesFlow(): Flow<List<AdminNote>> = flowOf(notes)
        override suspend fun getAllNotes(): List<AdminNote> = notes
        override suspend fun addNote(note: AdminNote) {
            notes.add(note)
        }
        override suspend fun deleteNote(id: String) {
            notes.removeIf { it.id == id }
        }
        override suspend fun deleteAllNotes() {
            notes.clear()
        }
    }

    private lateinit var tempImageDir: File

    private val fakeVeggieUnitTypes = mutableListOf(
        VeggieUnitType("unit-kg", "kg", allowDecimals = true, isActive = true),
        VeggieUnitType("unit-un", "Unit", allowDecimals = false, isActive = true)
    )
    private val fakeVeggieProducts = mutableListOf(
        VeggieProduct(
            id = "vp-tomato",
            name = "Organic Tomato",
            description = "Fresh vine tomatoes",
            estimatedPriceCents = 950L,
            unitTypeId = "unit-kg",
            isActive = true,
            isDeleted = false
        )
    )
    private val fakeVeggieRounds = mutableListOf<VeggieOrderRound>()
    private val fakeVeggieEntries = mutableListOf<VeggieOrderEntry>()
    private val fakeVeggieSettlements = mutableListOf<VeggieRoundProductSettlement>()

    private val fakeVeggieRepo = object : VeggieOrderRepository {
        override fun getUnitTypesFlow(): Flow<List<VeggieUnitType>> = flowOf(fakeVeggieUnitTypes)
        override suspend fun getAllUnitTypes(): List<VeggieUnitType> = fakeVeggieUnitTypes.toList()
        override suspend fun saveUnitType(unitType: VeggieUnitType) {
            fakeVeggieUnitTypes.removeIf { it.id == unitType.id }
            fakeVeggieUnitTypes.add(unitType)
        }
        override fun getVeggieProductsFlow(): Flow<List<VeggieProduct>> = flowOf(fakeVeggieProducts)
        override suspend fun getAllVeggieProducts(): List<VeggieProduct> = fakeVeggieProducts.toList()
        override suspend fun getVeggieProductById(id: String): VeggieProduct? = fakeVeggieProducts.find { it.id == id }
        override suspend fun saveVeggieProduct(product: VeggieProduct) {
            fakeVeggieProducts.removeIf { it.id == product.id }
            fakeVeggieProducts.add(product)
        }
        override suspend fun deactivateVeggieProduct(id: String) {
            val idx = fakeVeggieProducts.indexOfFirst { it.id == id }
            if (idx >= 0) fakeVeggieProducts[idx] = fakeVeggieProducts[idx].copy(isActive = false)
        }
        override suspend fun hardDeleteVeggieProduct(id: String) {
            fakeVeggieProducts.removeIf { it.id == id }
        }
        override fun getRoundsFlow(): Flow<List<VeggieOrderRound>> = flowOf(fakeVeggieRounds)
        override suspend fun getAllRounds(): List<VeggieOrderRound> = fakeVeggieRounds.toList()
        override suspend fun getActiveOrLatestRound(): VeggieOrderRound? =
            fakeVeggieRounds.firstOrNull { it.status == VeggieRoundStatus.OPEN } ?: fakeVeggieRounds.maxByOrNull { it.openedAt }
        override suspend fun getRoundById(roundId: String): VeggieOrderRound? = fakeVeggieRounds.find { it.id == roundId }
        override suspend fun openNewRound(buyerOperationFeeCents: Long, transportFeeCents: Long, openedAt: Long): VeggieOrderRound {
            val round = VeggieOrderRound(
                id = "round-${fakeVeggieRounds.size + 1}",
                openedAt = openedAt,
                status = VeggieRoundStatus.OPEN,
                buyerOperationFeeCents = buyerOperationFeeCents,
                transportFeeCents = transportFeeCents,
                updatedAt = openedAt
            )
            fakeVeggieRounds.add(round)
            return round
        }
        override suspend fun closeRound(roundId: String, closedAt: Long): Boolean {
            val idx = fakeVeggieRounds.indexOfFirst { it.id == roundId }
            if (idx < 0) return false
            fakeVeggieRounds[idx] = fakeVeggieRounds[idx].copy(status = VeggieRoundStatus.CLOSED, closedAt = closedAt)
            return true
        }
        override suspend fun reopenRound(roundId: String): Boolean {
            val idx = fakeVeggieRounds.indexOfFirst { it.id == roundId }
            if (idx < 0) return false
            fakeVeggieRounds[idx] = fakeVeggieRounds[idx].copy(status = VeggieRoundStatus.OPEN, closedAt = null)
            return true
        }
        override suspend fun deleteRound(roundId: String): Boolean {
            fakeVeggieEntries.removeIf { it.roundId == roundId }
            fakeVeggieSettlements.removeIf { it.roundId == roundId }
            return fakeVeggieRounds.removeIf { it.id == roundId }
        }
        override suspend fun markBuyerPdfExported(roundId: String, timestamp: Long): Boolean {
            val idx = fakeVeggieRounds.indexOfFirst { it.id == roundId }
            if (idx < 0) return false
            fakeVeggieRounds[idx] = fakeVeggieRounds[idx].copy(buyerPdfExportedAt = timestamp)
            return true
        }
        override suspend fun markWorkerPdfExported(roundId: String, timestamp: Long): Boolean {
            val idx = fakeVeggieRounds.indexOfFirst { it.id == roundId }
            if (idx < 0) return false
            fakeVeggieRounds[idx] = fakeVeggieRounds[idx].copy(workerPdfExportedAt = timestamp)
            return true
        }
        override suspend fun getEntriesForRound(roundId: String): List<VeggieOrderEntry> =
            fakeVeggieEntries.filter { it.roundId == roundId }
        override suspend fun getEntriesForUserInRound(roundId: String, userId: String): List<VeggieOrderEntry> =
            fakeVeggieEntries.filter { it.roundId == roundId && it.userId == userId }
        override suspend fun getPreviousRoundQuantitiesForUser(currentRoundId: String, userId: String): Map<String, Long> =
            mapOf("vp-tomato" to 1500L)
        override suspend fun saveCustomerOrder(roundId: String, user: User, productQuantitiesScaled: Map<String, Long>): Boolean {
            val round = getRoundById(roundId) ?: return false
            if (round.status != VeggieRoundStatus.OPEN) return false
            fakeVeggieEntries.removeIf { it.roundId == roundId && it.userId == user.id }
            for ((prodId, qty) in productQuantitiesScaled) {
                if (qty <= 0L) continue
                val prod = getVeggieProductById(prodId) ?: continue
                val unit = fakeVeggieUnitTypes.find { it.id == prod.unitTypeId }
                fakeVeggieEntries.add(
                    VeggieOrderEntry(
                        id = "entry-${user.id}-$prodId",
                        roundId = roundId,
                        userId = user.id,
                        userNameSnapshot = user.name,
                        veggieProductId = prod.id,
                        veggieNameSnapshot = prod.name,
                        unitNameSnapshot = unit?.name ?: "kg",
                        allowDecimalsSnapshot = unit?.allowDecimals ?: true,
                        orderedQuantityScaled = qty
                    )
                )
            }
            return true
        }
        override suspend fun getCustomerOrderHistory(userId: String): List<CustomerVeggieRoundHistoryItem> {
            return fakeVeggieRounds.mapNotNull { round ->
                val entries = getEntriesForUserInRound(round.id, userId)
                if (entries.isEmpty()) return@mapNotNull null
                CustomerVeggieRoundHistoryItem(
                    round = round,
                    entries = entries,
                    itemsSubtotalCents = entries.sumOf { it.finalItemTotalCents ?: 0L },
                    combinedFeeShareCents = round.combinedFeeCents,
                    grandTotalCents = entries.sumOf { it.finalItemTotalCents ?: 0L } + round.combinedFeeCents
                )
            }
        }
        override suspend fun getProductSettlementsForRound(roundId: String): List<VeggieRoundProductSettlement> =
            fakeVeggieSettlements.filter { it.roundId == roundId }
        override suspend fun saveDraftSettlement(
            roundId: String,
            buyerOperationFeeCents: Long,
            transportFeeCents: Long,
            transporterUserId: String?,
            productSettlements: List<VeggieRoundProductSettlement>,
            actualQuantitiesByEntryId: Map<String, Long>
        ): Boolean = true
        override suspend fun executeRoundSettlement(
            roundId: String,
            buyerOperationFeeCents: Long,
            transportFeeCents: Long,
            transporterUserId: String?,
            productSettlements: List<VeggieRoundProductSettlement>,
            actualQuantitiesByEntryId: Map<String, Long>,
            settledAt: Long
        ): VeggieRoundSettlementResult? {
            val roundIdx = fakeVeggieRounds.indexOfFirst { it.id == roundId }
            if (roundIdx < 0) return null
            val entries = getEntriesForRound(roundId).map { e ->
                e.copy(actualQuantityScaled = actualQuantitiesByEntryId[e.id] ?: e.orderedQuantityScaled)
            }
            val res = VeggieSettlementCalculator.calculateRoundSettlement(
                roundId = roundId,
                entries = entries,
                productSettlementInputs = productSettlements.associateBy { it.veggieProductId },
                buyerOperationFeeCents = buyerOperationFeeCents,
                transportFeeCents = transportFeeCents,
                transporterUserId = transporterUserId,
                timestamp = settledAt
            )
            fakeVeggieRounds[roundIdx] = fakeVeggieRounds[roundIdx].copy(
                status = VeggieRoundStatus.SETTLED,
                settledAt = settledAt,
                buyerOperationFeeCents = buyerOperationFeeCents,
                transportFeeCents = transportFeeCents,
                transporterUserId = transporterUserId
            )
            fakeVeggieEntries.removeIf { it.roundId == roundId }
            fakeVeggieEntries.addAll(res.updatedEntries)
            return res
        }
    }

    @BeforeTest
    fun setUp() {
        tempImageDir = Files.createTempDirectory("lojinha_server_veggie_imgs").toFile()
        server = LojinhaAdminServer(
            productRepository = fakeProductRepo,
            userRepository = fakeUserRepo,
            transactionRepository = fakeTransactionRepo,
            billingListRepository = fakeBillingListRepo,
            settingsRepository = fakeSettingsRepo,
            adminNoteRepository = fakeAdminNoteRepo,
            veggieOrderRepository = fakeVeggieRepo,
            veggieImageService = VeggieImageService(tempImageDir),
            port = testPort
        )
        server.start()
        Thread.sleep(500)
    }

    @AfterTest
    fun tearDown() {
        server.stop()
        tempImageDir.deleteRecursively()
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

    @Test
    fun testAdminNotesEndpoints() {
        val token = getValidToken()

        // 1. GET /api/admin/notes without token -> 401
        val unauthReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/notes"))
            .GET()
            .build()
        val unauthRes = client.send(unauthReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(401, unauthRes.statusCode())

        // 2. GET /api/admin/notes with token -> 200 and returns note list
        val getReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/notes"))
            .header("Authorization", "Bearer $token")
            .GET()
            .build()
        val getRes = client.send(getReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, getRes.statusCode())
        assertTrue(getRes.body().contains("Need more sugar"))

        // 3a. POST /api/admin/notes without token -> 401
        val postPayload = """{"message":"Ice tea ran out","userId":"u2","userNameSnapshot":"Bob"}"""
        val unauthPostReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/notes"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(postPayload))
            .build()
        val unauthPostRes = client.send(unauthPostReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(401, unauthPostRes.statusCode())

        // 3b. POST /api/admin/notes with blank message -> 400
        val blankPayload = """{"message":"   ","userId":"u2","userNameSnapshot":"Bob"}"""
        val blankPostReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/notes"))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(blankPayload))
            .build()
        val blankPostRes = client.send(blankPostReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(400, blankPostRes.statusCode())

        // 3c. POST /api/admin/notes with valid payload -> 201 Created
        val postReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/notes"))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(postPayload))
            .build()
        val postRes = client.send(postReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(201, postRes.statusCode())
        assertTrue(postRes.body().contains("Ice tea ran out"))

        // 4. DELETE /api/admin/notes/{id} -> 200
        val delReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/notes/note-init"))
            .header("Authorization", "Bearer $token")
            .DELETE()
            .build()
        val delRes = client.send(delReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, delRes.statusCode())
        assertFalse(fakeAdminNoteRepo.notes.any { it.id == "note-init" })
    }

    @Test
    fun testWasmAssetServingCompressionAndCaching() {
        // 1. GET /index.html with Accept-Encoding: gzip
        val htmlReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/index.html"))
            .header("Accept-Encoding", "gzip")
            .GET()
            .build()
        val htmlRes = client.send(htmlReq, HttpResponse.BodyHandlers.ofByteArray())
        assertEquals(200, htmlRes.statusCode())
        assertEquals("gzip", htmlRes.headers().firstValue("Content-Encoding").orElse(null))
        assertEquals("Accept-Encoding", htmlRes.headers().firstValue("Vary").orElse(null))
        assertTrue(htmlRes.headers().firstValue("Content-Type").orElse("").contains("text/html"))
        assertTrue(htmlRes.headers().firstValue("Cache-Control").orElse("").contains("no-cache"))
        val htmlEtag = htmlRes.headers().firstValue("ETag").orElse(null)
        assertNotNull(htmlEtag)
        assertTrue(htmlEtag.startsWith("W/\""), "ETag should be weak ETag")

        // Verify conditional request returns 304 Not Modified (weak match, exact)
        val conditionalHtmlReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/index.html"))
            .header("If-None-Match", htmlEtag)
            .GET()
            .build()
        val conditionalHtmlRes = client.send(conditionalHtmlReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(304, conditionalHtmlRes.statusCode())
        assertEquals("Accept-Encoding", conditionalHtmlRes.headers().firstValue("Vary").orElse(null))

        // RFC 9110 weak comparison: Normalized strong format from client (stripped W/) must match weak server ETag
        val strongHtmlEtag = htmlEtag.removePrefix("W/")
        val conditionalStrongReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/index.html"))
            .header("If-None-Match", strongHtmlEtag)
            .GET()
            .build()
        val conditionalStrongRes = client.send(conditionalStrongReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(304, conditionalStrongRes.statusCode(), "Client sending strong opaque ETag should match weak ETag with 304")

        // RFC 9110: Comma-separated If-None-Match header must match if any tag matches
        val commaSeparatedReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/index.html"))
            .header("If-None-Match", "\"unrelated-tag-123\", $htmlEtag")
            .GET()
            .build()
        val commaSeparatedRes = client.send(commaSeparatedReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(304, commaSeparatedRes.statusCode(), "Comma-separated If-None-Match should match with 304")

        // RFC 9110: Wildcard * matches any existing representation
        val wildcardReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/index.html"))
            .header("If-None-Match", "*")
            .GET()
            .build()
        val wildcardRes = client.send(wildcardReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(304, wildcardRes.statusCode(), "Wildcard If-None-Match should match with 304")

        // 2. GET /lojinha.js - MUST NOT be immutable (must be no-cache with ETag for safe updates)
        val jsReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/lojinha.js"))
            .header("Accept-Encoding", "gzip")
            .GET()
            .build()
        val jsRes = client.send(jsReq, HttpResponse.BodyHandlers.ofByteArray())
        if (jsRes.statusCode() == 200) {
            assertEquals("gzip", jsRes.headers().firstValue("Content-Encoding").orElse(null))
            assertEquals("Accept-Encoding", jsRes.headers().firstValue("Vary").orElse(null))
            assertTrue(jsRes.headers().firstValue("Cache-Control").orElse("").contains("no-cache"),
                "lojinha.js must use no-cache, not immutable, to prevent stale cache lock-in")
            val jsEtag = jsRes.headers().firstValue("ETag").orElse(null)
            assertNotNull(jsEtag)

            // Test 304 for lojinha.js
            val conditionalJsReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$testPort/lojinha.js"))
                .header("If-None-Match", jsEtag)
                .GET()
                .build()
            val conditionalJsRes = client.send(conditionalJsReq, HttpResponse.BodyHandlers.ofString())
            assertEquals(304, conditionalJsRes.statusCode())
        }

        // 3. GET wasm asset with gzip
        val wasmFile = java.io.File("shared/build/dist/wasmJs/productionExecutable")
            .listFiles { file -> file.extension == "wasm" }
            ?.firstOrNull()
        if (wasmFile != null && wasmFile.exists()) {
            val wasmName = wasmFile.name
            val wasmGzipReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$testPort/$wasmName"))
                .header("Accept-Encoding", "gzip")
                .GET()
                .build()
            val wasmGzipRes = client.send(wasmGzipReq, HttpResponse.BodyHandlers.ofByteArray())
            assertEquals(200, wasmGzipRes.statusCode())
            assertEquals("gzip", wasmGzipRes.headers().firstValue("Content-Encoding").orElse(null))
            assertEquals("application/wasm", wasmGzipRes.headers().firstValue("Content-Type").orElse(null))
            assertEquals("Accept-Encoding", wasmGzipRes.headers().firstValue("Vary").orElse(null))
            assertEquals("public, max-age=31536000, immutable", wasmGzipRes.headers().firstValue("Cache-Control").orElse(null))
            val wasmEtag = wasmGzipRes.headers().firstValue("ETag").orElse(null)
            assertNotNull(wasmEtag)
            assertTrue(wasmEtag.startsWith("W/\""), "ETag should be weak ETag")

            // Verify compressed payload is significantly smaller than raw file
            val compressedBytes = wasmGzipRes.body()
            assertTrue(compressedBytes.size < wasmFile.length(), "Compressed size ${compressedBytes.size} should be smaller than raw size ${wasmFile.length()}")

            // Decompress and verify content integrity
            val decompressedBytes = java.util.zip.GZIPInputStream(java.io.ByteArrayInputStream(compressedBytes)).use { it.readBytes() }
            assertEquals(wasmFile.length(), decompressedBytes.size.toLong())

            // Test conditional 304 Not Modified for wasm asset
            val conditionalWasmReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$testPort/$wasmName"))
                .header("If-None-Match", wasmEtag)
                .GET()
                .build()
            val conditionalWasmRes = client.send(conditionalWasmReq, HttpResponse.BodyHandlers.ofByteArray())
            assertEquals(304, conditionalWasmRes.statusCode())

            // Test non-gzip request receives uncompressed content but still includes Vary header
            val wasmRawReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$testPort/$wasmName"))
                .GET()
                .build()
            val wasmRawRes = client.send(wasmRawReq, HttpResponse.BodyHandlers.ofByteArray())
            assertEquals(200, wasmRawRes.statusCode())
            assertFalse(wasmRawRes.headers().firstValue("Content-Encoding").isPresent)
            assertEquals("application/wasm", wasmRawRes.headers().firstValue("Content-Type").orElse(null))
            assertEquals("Accept-Encoding", wasmRawRes.headers().firstValue("Vary").orElse(null))
            assertEquals("public, max-age=31536000, immutable", wasmRawRes.headers().firstValue("Cache-Control").orElse(null))
            assertEquals(wasmFile.length(), wasmRawRes.body().size.toLong())
        }
    }

    @Test
    fun testCustomerPortalLoginAndVeggieOrderFlow() {
        val json = Json { ignoreUnknownKeys = true }

        // 1. GET /api/customer/users sanitizes balance and PIN hash
        val usersReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/customer/users"))
            .GET()
            .build()
        val usersRes = client.send(usersReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, usersRes.statusCode())
        val publicUsers = json.decodeFromString<List<User>>(usersRes.body())
        val alicePublic = publicUsers.first { it.id == "u-alice" }
        val bobPublic = publicUsers.first { it.id == "u-nopin" }
        assertEquals(0L, alicePublic.balance, "Public user list must zero out balance")
        assertEquals("PROTECTED", alicePublic.pin, "Public user list must mask PIN hash")
        assertNull(bobPublic.pin, "User without PIN must have null pin on public list")

        // 2. Customer without PIN cannot log in on network portal
        val noPinLoginReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/customer/login"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(CustomerLoginRequest("u-nopin", "1234"))))
            .build()
        val noPinLoginRes = client.send(noPinLoginReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, noPinLoginRes.statusCode())
        val noPinBody = json.decodeFromString<CustomerLoginResponse>(noPinLoginRes.body())
        assertFalse(noPinBody.success)
        assertTrue(noPinBody.message.contains("PIN required"))

        // 3. Customer with valid PIN logs in and receives scoped token + actual balance
        val aliceLoginReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/customer/login"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(CustomerLoginRequest("u-alice", "1234"))))
            .build()
        val aliceLoginRes = client.send(aliceLoginReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, aliceLoginRes.statusCode())
        val aliceLoginBody = json.decodeFromString<CustomerLoginResponse>(aliceLoginRes.body())
        assertTrue(aliceLoginBody.success)
        assertNotNull(aliceLoginBody.token)
        assertEquals(2500L, aliceLoginBody.user?.balance)
        val customerToken = aliceLoginBody.token!!

        // 4. Open a veggie round as Admin first
        val adminToken = getValidToken()
        val openRoundReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/veggie/rounds/open"))
            .header("Authorization", "Bearer $adminToken")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(OpenVeggieRoundRequest(300L, 600L, 1731150000000L))))
            .build()
        val openRoundRes = client.send(openRoundReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, openRoundRes.statusCode())
        val openedRound = json.decodeFromString<VeggieOrderRound>(openRoundRes.body())

        // 5. Customer fetches /api/customer/veggie/state
        val stateReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/customer/veggie/state"))
            .header("Authorization", "Bearer $customerToken")
            .GET()
            .build()
        val stateRes = client.send(stateReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, stateRes.statusCode())
        val veggieState = json.decodeFromString<CustomerVeggieStateResponse>(stateRes.body())
        assertEquals(openedRound.id, veggieState.activeRound?.id)
        assertEquals(1500L, veggieState.previousRoundQuantities["vp-tomato"])

        // 6. Customer submits an order in the open round
        val saveOrderPayload = SaveCustomerVeggieOrderRequest(
            roundId = openedRound.id,
            productQuantitiesScaled = mapOf("vp-tomato" to 2000L)
        )
        val saveOrderReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/customer/veggie/order"))
            .header("Authorization", "Bearer $customerToken")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(saveOrderPayload)))
            .build()
        val saveOrderRes = client.send(saveOrderReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, saveOrderRes.statusCode())

        // 7. Customer fetches /api/customer/veggie/history
        val historyReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/customer/veggie/history"))
            .header("Authorization", "Bearer $customerToken")
            .GET()
            .build()
        val historyRes = client.send(historyReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, historyRes.statusCode())
        val history = json.decodeFromString<List<CustomerVeggieRoundHistoryItem>>(historyRes.body())
        assertEquals(1, history.size)
        assertEquals(2000L, history.first().entries.first().orderedQuantityScaled)
    }

    @Test
    fun testAdminVeggieEndpointsImagesAndPdfs() {
        val json = Json { ignoreUnknownKeys = true }
        val adminToken = getValidToken()

        // 1. Upload image for vp-tomato
        val sampleImg = BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB).apply {
            val g = createGraphics()
            g.color = Color.RED
            g.fillRect(0, 0, 400, 300)
            g.dispose()
        }
        val imgBytes = ByteArrayOutputStream().also { ImageIO.write(sampleImg, "png", it) }.toByteArray()

        val uploadImgReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/veggie/products/vp-tomato/image"))
            .header("Authorization", "Bearer $adminToken")
            .header("Content-Type", "application/octet-stream")
            .POST(HttpRequest.BodyPublishers.ofByteArray(imgBytes))
            .build()
        val uploadImgRes = client.send(uploadImgReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, uploadImgRes.statusCode())
        assertTrue(uploadImgRes.body().contains("vp-tomato"))

        // 2. Fetch thumbnail via /api/veggie/images/vp-tomato?thumb=true and verify ETag 304
        val getThumbReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/veggie/images/vp-tomato?thumb=true"))
            .GET()
            .build()
        val getThumbRes = client.send(getThumbReq, HttpResponse.BodyHandlers.ofByteArray())
        assertEquals(200, getThumbRes.statusCode())
        assertTrue(getThumbRes.headers().firstValue("Content-Type").orElse("").contains("image/jpeg"))
        val imgEtag = getThumbRes.headers().firstValue("ETag").orElse(null)
        assertNotNull(imgEtag)

        val condThumbReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/veggie/images/vp-tomato?thumb=true"))
            .header("If-None-Match", imgEtag)
            .GET()
            .build()
        val condThumbRes = client.send(condThumbReq, HttpResponse.BodyHandlers.ofByteArray())
        assertEquals(304, condThumbRes.statusCode())

        // 3. Open round, add entry, close round, download Buyer & Worker PDFs, and settle
        val openRoundReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/veggie/rounds/open"))
            .header("Authorization", "Bearer $adminToken")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(OpenVeggieRoundRequest(200L, 400L, 1731150000000L))))
            .build()
        val round = json.decodeFromString<VeggieOrderRound>(
            client.send(openRoundReq, HttpResponse.BodyHandlers.ofString()).body()
        )

        val buyerPdfReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/veggie/rounds/${round.id}/buyer-pdf?lang=EN"))
            .header("Authorization", "Bearer $adminToken")
            .GET()
            .build()
        val buyerPdfRes = client.send(buyerPdfReq, HttpResponse.BodyHandlers.ofByteArray())
        assertEquals(200, buyerPdfRes.statusCode())
        assertEquals("%PDF-", String(buyerPdfRes.body(), 0, 5, Charsets.US_ASCII))

        val workerPdfReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/veggie/rounds/${round.id}/worker-pdf?lang=DE"))
            .header("Authorization", "Bearer $adminToken")
            .GET()
            .build()
        val workerPdfRes = client.send(workerPdfReq, HttpResponse.BodyHandlers.ofByteArray())
        assertEquals(200, workerPdfRes.statusCode())
        assertEquals("%PDF-", String(workerPdfRes.body(), 0, 5, Charsets.US_ASCII))

        // Close and settle round
        val closeReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/veggie/rounds/${round.id}/close"))
            .header("Authorization", "Bearer $adminToken")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(CloseVeggieRoundRequest(1731155000000L))))
            .build()
        assertEquals(200, client.send(closeReq, HttpResponse.BodyHandlers.ofString()).statusCode())

        val settlePayload = VeggieSettlementRequest(
            buyerOperationFeeCents = 200L,
            transportFeeCents = 400L,
            transporterUserId = "u-alice",
            productSettlements = listOf(
                VeggieRoundProductSettlement(
                    roundId = round.id,
                    veggieProductId = "vp-tomato",
                    marketUnitPriceCents = 1000L,
                    totalSpentCents = 2000L
                )
            ),
            actualQuantitiesByEntryId = emptyMap(),
            settledAt = 1731160000000L
        )
        val settleReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/veggie/rounds/${round.id}/settle"))
            .header("Authorization", "Bearer $adminToken")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(settlePayload)))
            .build()
        val settleRes = client.send(settleReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, settleRes.statusCode())
        val settlementResult = json.decodeFromString<VeggieRoundSettlementResult>(settleRes.body())
        assertEquals(600L, settlementResult.combinedFeeCents)
        assertEquals("u-alice", settlementResult.transporterUserId)
    }
}

