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
    private val testJson = Json { ignoreUnknownKeys = true }

    private val dummySettings = SystemSettings(
        adminPasswordHash = de.joelneumann.lojinha.security.PasswordHasher.hash("secretPass123")
    )

    private val fakeSettingsRepo = object : SettingsRepository {
        override suspend fun getSettings(): SystemSettings = dummySettings
        override fun getSettingsFlow(): Flow<SystemSettings> = flowOf(dummySettings)
        override suspend fun updateSettings(settings: SystemSettings, notifyDataChanged: Boolean) {}
    }

    private val fakeProducts = mutableListOf<Product>()
    private val fakeProductRepo = object : ProductRepository {
        override fun getProductsFlow(): Flow<List<Product>> = flowOf(fakeProducts.toList())
        override suspend fun getAllProducts(): List<Product> = fakeProducts.toList()
        override suspend fun getProductById(id: String): Product? = fakeProducts.find { it.id == id }
        override suspend fun getProductByBarcode(barcode: String): Product? = null
        override suspend fun saveProduct(product: Product) {
            fakeProducts.removeAll { it.id == product.id }
            fakeProducts.add(product)
        }
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

    private val fakeTransactions = mutableListOf<Transaction>()
    private val fakeTransactionRepo = object : TransactionRepository {
        override fun getTransactionsFlow(): Flow<List<Transaction>> = flowOf(fakeTransactions.toList())
        override suspend fun getAllTransactions(): List<Transaction> = fakeTransactions.toList()
        override suspend fun getTransactionsByUserId(userId: String): List<Transaction> = fakeTransactions.filter { it.userId == userId }
        override suspend fun getTransactionById(id: String): Transaction? = fakeTransactions.find { it.id == id }
        override suspend fun recordTransaction(transaction: Transaction) {
            fakeTransactions.add(transaction)
        }
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
    private val fakeVeggieFavorites = mutableMapOf<String, Set<String>>()

    private val fakeVeggieRepo = object : VeggieOrderRepository {
        override suspend fun getFavoriteProductIdsForUser(userId: String): Set<String> =
            fakeVeggieFavorites[userId] ?: emptySet()

        override suspend fun setFavoriteProductIdsForUser(userId: String, productIds: Set<String>) {
            fakeVeggieFavorites[userId] = productIds
        }
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
        override suspend fun cancelProductEntriesInOpenRound(roundId: String, productId: String): Boolean {
            val round = getRoundById(roundId) ?: return false
            if (round.status != VeggieRoundStatus.OPEN) return false
            fakeVeggieEntries.removeIf { it.roundId == roundId && it.veggieProductId == productId }
            return true
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
        override suspend fun markBuyerPortalPdfExported(roundId: String, timestamp: Long): Boolean {
            val idx = fakeVeggieRounds.indexOfFirst { it.id == roundId }
            if (idx < 0) return false
            fakeVeggieRounds[idx] = fakeVeggieRounds[idx].copy(buyerPortalPdfExportedAt = timestamp)
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
        ): Boolean {
            val idx = fakeVeggieRounds.indexOfFirst { it.id == roundId }
            if (idx < 0) return false
            fakeVeggieRounds[idx] = fakeVeggieRounds[idx].copy(
                buyerOperationFeeCents = buyerOperationFeeCents,
                transportFeeCents = transportFeeCents,
                transporterUserId = transporterUserId
            )
            fakeVeggieSettlements.removeAll { it.roundId == roundId }
            fakeVeggieSettlements.addAll(productSettlements)
            return true
        }
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

    private val fakeShoppingListItems = mutableListOf<ShoppingListItem>()
    private val fakeShoppingListRepo = object : ShoppingListRepository {
        override fun getItemsFlow(): Flow<List<ShoppingListItem>> = flowOf(fakeShoppingListItems.toList())
        override suspend fun getAllItems(): List<ShoppingListItem> = fakeShoppingListItems.sortedBy { it.sortOrder }
        override suspend fun addOrUpdateItem(item: ShoppingListItem) {
            val id = item.id.ifBlank { "sl-${fakeShoppingListItems.size + 1}" }
            val existingIdx = fakeShoppingListItems.indexOfFirst { it.id == id }
            if (existingIdx >= 0) {
                fakeShoppingListItems[existingIdx] = fakeShoppingListItems[existingIdx].copy(
                    name = item.name,
                    quantity = item.quantity,
                    isConfirmedForCurrentExport = true
                )
            } else {
                fakeShoppingListItems.add(item.copy(id = id, sortOrder = fakeShoppingListItems.size))
            }
        }
        override suspend fun updateItem(item: ShoppingListItem) {
            val idx = fakeShoppingListItems.indexOfFirst { it.id == item.id }
            if (idx >= 0) fakeShoppingListItems[idx] = item
        }
        override suspend fun confirmItem(id: String) {
            val idx = fakeShoppingListItems.indexOfFirst { it.id == id }
            if (idx >= 0) fakeShoppingListItems[idx] = fakeShoppingListItems[idx].copy(isConfirmedForCurrentExport = true)
        }
        override suspend fun unconfirmItem(id: String) {
            val idx = fakeShoppingListItems.indexOfFirst { it.id == id }
            if (idx >= 0) fakeShoppingListItems[idx] = fakeShoppingListItems[idx].copy(isConfirmedForCurrentExport = false)
        }
        override suspend fun updateItemsOrder(items: List<ShoppingListItem>) {
            items.forEachIndexed { idx, item ->
                val pos = fakeShoppingListItems.indexOfFirst { it.id == item.id }
                if (pos >= 0) fakeShoppingListItems[pos] = fakeShoppingListItems[pos].copy(sortOrder = idx)
            }
        }
        override suspend fun deleteItem(id: String) {
            fakeShoppingListItems.removeIf { it.id == id }
        }
        override suspend fun clearAllItems() {
            fakeShoppingListItems.clear()
        }
        override suspend fun commitExportBaseline() {
            for (i in fakeShoppingListItems.indices) {
                val cur = fakeShoppingListItems[i]
                fakeShoppingListItems[i] = cur.copy(
                    lastExportedName = cur.name,
                    lastExportedQuantity = cur.quantity,
                    isConfirmedForCurrentExport = false
                )
            }
        }
    }

    @BeforeTest
    fun setUp() {
        fakeShoppingListItems.clear()
        tempImageDir = Files.createTempDirectory("lojinha_server_veggie_imgs").toFile()
        server = LojinhaAdminServer(
            productRepository = fakeProductRepo,
            userRepository = fakeUserRepo,
            transactionRepository = fakeTransactionRepo,
            billingListRepository = fakeBillingListRepo,
            settingsRepository = fakeSettingsRepo,
            adminNoteRepository = fakeAdminNoteRepo,
            shoppingListRepository = fakeShoppingListRepo,
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

        // 2. Login with wrong password -> 401 generic Invalid credentials
        val wrongLoginReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/login"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("""{"identifier":"Admin","password":"wrongPassword"}"""))
            .build()
        val wrongLoginRes = client.send(wrongLoginReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(401, wrongLoginRes.statusCode())
        assertTrue(wrongLoginRes.body().contains("Invalid credentials"))

        // 3. Login with correct Admin password -> 200 OK + HttpOnly cookie
        val okLoginReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/login"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("""{"identifier":"Admin","password":"secretPass123"}"""))
            .build()
        val okLoginRes = client.send(okLoginReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, okLoginRes.statusCode())
        assertTrue(okLoginRes.body().contains("\"success\":true") || okLoginRes.body().contains("\"success\": true"))
        assertTrue(okLoginRes.body().contains("ADMIN"))

        val setCookieHeader = okLoginRes.headers().allValues("Set-Cookie")
            .firstOrNull { it.startsWith("lojinha_admin_session=") }
        assertNotNull(setCookieHeader, "Admin session cookie must be set")
        assertTrue(setCookieHeader.contains("HttpOnly", ignoreCase = true))
        assertTrue(setCookieHeader.contains("SameSite=Lax", ignoreCase = true))
        assertFalse(setCookieHeader.contains("Secure", ignoreCase = true), "Plain HTTP should not set Secure flag")
        val adminCookie = setCookieHeader.substringBefore(";")

        // 4. Authenticated request with Cookie should succeed (200 OK)
        val authReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/users"))
            .header("Cookie", adminCookie)
            .GET()
            .build()
        val authRes = client.send(authReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, authRes.statusCode())

        // 5. Authenticated request to bulk billing lists with Cookie
        val billingReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/billing-lists"))
            .header("Cookie", adminCookie)
            .GET()
            .build()
        val billingRes = client.send(billingReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, billingRes.statusCode())

        // 6. Logout
        val logoutReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/logout"))
            .header("Cookie", adminCookie)
            .POST(HttpRequest.BodyPublishers.noBody())
            .build()
        val logoutRes = client.send(logoutReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, logoutRes.statusCode())

        // 7. Former cookie is now invalid -> 401
        val revokedReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/users"))
            .header("Cookie", adminCookie)
            .GET()
            .build()
        val revokedRes = client.send(revokedReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(401, revokedRes.statusCode())
    }

    @Test
    fun testSettingsEndpointsAreRemoved() {
        val adminCookie = getValidAdminCookie()

        // GET /api/admin/settings should return 404 (endpoint removed)
        val getSettingsReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/settings"))
            .header("Cookie", adminCookie)
            .GET()
            .build()
        val getSettingsRes = client.send(getSettingsReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(404, getSettingsRes.statusCode())

        // POST /api/admin/settings should return 404
        val postSettingsReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/settings"))
            .header("Cookie", adminCookie)
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
    fun testRateLimitingWithCfConnectingIpAndAccountBackoff() {
        val wrongLoginPayload = """{"identifier":"Admin","password":"wrongPassword"}"""

        // 5 failed attempts from CF-Connecting-IP 1.2.3.4
        repeat(5) {
            val req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$testPort/api/login"))
                .header("Content-Type", "application/json")
                .header("CF-Connecting-IP", "1.2.3.4")
                .POST(HttpRequest.BodyPublishers.ofString(wrongLoginPayload))
                .build()
            val res = client.send(req, HttpResponse.BodyHandlers.ofString())
            assertEquals(401, res.statusCode())
        }

        // 6th attempt from 1.2.3.4 must be blocked with 429 Too Many Requests + Retry-After
        val rateLimitedReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/login"))
            .header("Content-Type", "application/json")
            .header("CF-Connecting-IP", "1.2.3.4")
            .POST(HttpRequest.BodyPublishers.ofString(wrongLoginPayload))
            .build()
        val rateLimitedRes = client.send(rateLimitedReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(429, rateLimitedRes.statusCode())
        assertNotNull(rateLimitedRes.headers().firstValue("Retry-After").orElse(null))
        assertTrue(rateLimitedRes.body().contains("Too many failed login attempts"))

        // A different client IP trying a DIFFERENT account (Alice) must NOT be locked out!
        val aliceFromOtherIpReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/login"))
            .header("Content-Type", "application/json")
            .header("CF-Connecting-IP", "5.6.7.8")
            .POST(HttpRequest.BodyPublishers.ofString("""{"identifier":"Alice","password":"1234"}"""))
            .build()
        val aliceOtherIpRes = client.send(aliceFromOtherIpReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, aliceOtherIpRes.statusCode())

        // Rotating IPs against the SAME account ("Admin") must still trigger per-account lockout!
        val adminFromOtherIpReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/login"))
            .header("Content-Type", "application/json")
            .header("CF-Connecting-IP", "9.9.9.9")
            .POST(HttpRequest.BodyPublishers.ofString(wrongLoginPayload))
            .build()
        val adminLockedRes = client.send(adminFromOtherIpReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(429, adminLockedRes.statusCode())
    }

    private fun extractCookie(res: HttpResponse<*>, cookieName: String): String {
        val setCookie = res.headers().allValues("Set-Cookie")
            .firstOrNull { it.startsWith("$cookieName=") }
            ?: error("Cookie $cookieName not found in Set-Cookie headers: ${res.headers().allValues("Set-Cookie")}")
        return setCookie.substringBefore(";")
    }

    private fun getValidAdminCookie(): String {
        val loginReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/login"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("""{"identifier":"Admin","password":"secretPass123"}"""))
            .build()
        val loginRes = client.send(loginReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, loginRes.statusCode())
        return extractCookie(loginRes, "lojinha_admin_session")
    }

    @Test
    fun testCanDeleteUserEndpoint() {
        val adminCookie = getValidAdminCookie()
        val req = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/users/can-delete/user123"))
            .header("Cookie", adminCookie)
            .GET()
            .build()
        val res = client.send(req, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, res.statusCode())
        assertTrue(res.body().contains("\"canDelete\":true") || res.body().contains("\"canDelete\": true"))
    }

    @Test
    fun testBatchTransactionsEndpoint() {
        val adminCookie = getValidAdminCookie()
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
            .header("Cookie", adminCookie)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build()
        val res = client.send(req, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, res.statusCode())
    }

    @Test
    fun testRootRouteServesLoginWhenUnauthenticatedAndAppWhenAuthenticated() {
        // 1. Unauthenticated GET / serves login.html with security headers and no-store cache
        val unauthReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/"))
            .GET()
            .build()
        val unauthRes = client.send(unauthReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, unauthRes.statusCode())
        val contentType = unauthRes.headers().firstValue("Content-Type").orElse("")
        assertTrue(contentType.contains("text/html"), "Content-Type must be text/html, was: $contentType")
        assertTrue(unauthRes.body().contains("id=\"loginForm\""), "Unauthenticated GET / must serve login.html")
        assertTrue(unauthRes.headers().firstValue("Cache-Control").orElse("").contains("no-store"))
        assertEquals("nosniff", unauthRes.headers().firstValue("X-Content-Type-Options").orElse(null))
        assertEquals("DENY", unauthRes.headers().firstValue("X-Frame-Options").orElse(null))
        assertEquals("no-referrer", unauthRes.headers().firstValue("Referrer-Policy").orElse(null))

        // 2. Authenticated GET / serves index.html (ComposeTarget)
        val adminCookie = getValidAdminCookie()
        val authReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/"))
            .header("Cookie", adminCookie)
            .GET()
            .build()
        val authRes = client.send(authReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, authRes.statusCode())
        assertTrue(authRes.body().contains("id=\"ComposeTarget\""), "Authenticated GET / must serve index.html")
        assertFalse(authRes.body().contains("037f170986f544477491.wasm\n"), "Body must not be a directory listing")
    }

    @Test
    fun testAdminNotesEndpoints() {
        val adminCookie = getValidAdminCookie()

        // 1. GET /api/admin/notes without cookie -> 401
        val unauthReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/notes"))
            .GET()
            .build()
        val unauthRes = client.send(unauthReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(401, unauthRes.statusCode())

        // 2. GET /api/admin/notes with cookie -> 200 and returns note list
        val getReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/notes"))
            .header("Cookie", adminCookie)
            .GET()
            .build()
        val getRes = client.send(getReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, getRes.statusCode())
        assertTrue(getRes.body().contains("Need more sugar"))

        // 3a. POST /api/admin/notes without cookie -> 401
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
            .header("Cookie", adminCookie)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(blankPayload))
            .build()
        val blankPostRes = client.send(blankPostReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(400, blankPostRes.statusCode())

        // 3c. POST /api/admin/notes with valid payload -> 201 Created
        val postReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/notes"))
            .header("Cookie", adminCookie)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(postPayload))
            .build()
        val postRes = client.send(postReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(201, postRes.statusCode())
        assertTrue(postRes.body().contains("Ice tea ran out"))

        // 4. DELETE /api/admin/notes/{id} -> 200
        val delReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/notes/note-init"))
            .header("Cookie", adminCookie)
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

        // 1. GET /api/customer/users must be removed (404) so user names are never leaked
        val usersReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/customer/users"))
            .GET()
            .build()
        val usersRes = client.send(usersReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(404, usersRes.statusCode())

        // 2. Customer without PIN & unknown user both return uniform 401 "Invalid credentials"
        val noPinLoginReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/login"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(UnifiedLoginRequest("Bob NoPin", "1234"))))
            .build()
        val noPinLoginRes = client.send(noPinLoginReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(401, noPinLoginRes.statusCode())
        val noPinBody = json.decodeFromString<UnifiedLoginResponse>(noPinLoginRes.body())
        assertFalse(noPinBody.success)
        assertEquals("Invalid credentials", noPinBody.message)

        val unknownUserReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/login"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(UnifiedLoginRequest("NonExistent", "1234"))))
            .build()
        val unknownUserRes = client.send(unknownUserReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(401, unknownUserRes.statusCode())
        assertEquals(noPinLoginRes.body(), unknownUserRes.body())

        // 3. Customer with valid PIN logs in case-insensitively and receives HttpOnly cookie (Secure when X-Forwarded-Proto: https)
        val aliceLoginReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/login"))
            .header("Content-Type", "application/json")
            .header("X-Forwarded-Proto", "https")
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(UnifiedLoginRequest("  alice ", "1234", keepLoggedIn = true))))
            .build()
        val aliceLoginRes = client.send(aliceLoginReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, aliceLoginRes.statusCode())
        val aliceLoginBody = json.decodeFromString<UnifiedLoginResponse>(aliceLoginRes.body())
        assertTrue(aliceLoginBody.success)
        assertEquals("CUSTOMER", aliceLoginBody.role)
        val customerSetCookie = aliceLoginRes.headers().allValues("Set-Cookie")
            .firstOrNull { it.startsWith("lojinha_customer_session=") }
        assertNotNull(customerSetCookie)
        assertTrue(customerSetCookie.contains("HttpOnly", ignoreCase = true))
        assertTrue(customerSetCookie.contains("Secure", ignoreCase = true))
        assertTrue(customerSetCookie.contains("Max-Age=7776000", ignoreCase = true), "Expected 90-day Max-Age on persistent session")
        val customerCookie = customerSetCookie.substringBefore(";")

        // Verify /api/customer/me returns Alice's actual balance and masked PIN
        val meReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/customer/me"))
            .header("Cookie", customerCookie)
            .GET()
            .build()
        val meRes = client.send(meReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, meRes.statusCode())
        val meUser = json.decodeFromString<User>(meRes.body())
        assertEquals(2500L, meUser.balance)
        assertEquals("PROTECTED", meUser.pin)

        // 4. Open a veggie round as Admin first
        val adminCookie = getValidAdminCookie()
        val openRoundReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/veggie/rounds/open"))
            .header("Cookie", adminCookie)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(OpenVeggieRoundRequest(300L, 600L, 1731150000000L))))
            .build()
        val openRoundRes = client.send(openRoundReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, openRoundRes.statusCode())
        val openedRound = json.decodeFromString<VeggieOrderRound>(openRoundRes.body())

        // 5. Customer fetches /api/customer/veggie/state
        val stateReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/customer/veggie/state"))
            .header("Cookie", customerCookie)
            .GET()
            .build()
        val stateRes = client.send(stateReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, stateRes.statusCode())
        val veggieState = json.decodeFromString<CustomerVeggieStateResponse>(stateRes.body())
        assertEquals(openedRound.id, veggieState.activeRound?.id)
        assertEquals(1500L, veggieState.previousRoundQuantities["vp-tomato"])
        assertNotNull(veggieState.displaySettings)

        // 6. Customer submits an order in the open round
        val saveOrderPayload = SaveCustomerVeggieOrderRequest(
            roundId = openedRound.id,
            productQuantitiesScaled = mapOf("vp-tomato" to 2000L)
        )
        val saveOrderReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/customer/veggie/order"))
            .header("Cookie", customerCookie)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(saveOrderPayload)))
            .build()
        val saveOrderRes = client.send(saveOrderReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, saveOrderRes.statusCode())

        // 7. Customer fetches /api/customer/veggie/history
        val historyReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/customer/veggie/history"))
            .header("Cookie", customerCookie)
            .GET()
            .build()
        val historyRes = client.send(historyReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, historyRes.statusCode())
        val history = json.decodeFromString<List<CustomerVeggieRoundHistoryItem>>(historyRes.body())
        assertEquals(1, history.size)
        assertEquals(2000L, history.first().entries.first().orderedQuantityScaled)

        // 8. Customer updates favorites via POST /api/customer/veggie/favorites and sees them in /api/customer/veggie/state
        val saveFavReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/customer/veggie/favorites"))
            .header("Cookie", customerCookie)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(UpdateCustomerVeggieFavoritesRequest(setOf("vp-tomato")))))
            .build()
        val saveFavRes = client.send(saveFavReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, saveFavRes.statusCode())

        val stateAfterFavReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/customer/veggie/state"))
            .header("Cookie", customerCookie)
            .GET()
            .build()
        val stateAfterFavRes = client.send(stateAfterFavReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, stateAfterFavRes.statusCode())
        val stateAfterFav = json.decodeFromString<CustomerVeggieStateResponse>(stateAfterFavRes.body())
        assertEquals(setOf("vp-tomato"), stateAfterFav.favoriteProductIds)
    }

    @Test
    fun testAdminVeggieEndpointsImagesAndPdfs() {
        val json = Json { ignoreUnknownKeys = true }
        val adminCookie = getValidAdminCookie()

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
            .header("Cookie", adminCookie)
            .header("Content-Type", "application/octet-stream")
            .POST(HttpRequest.BodyPublishers.ofByteArray(imgBytes))
            .build()
        val uploadImgRes = client.send(uploadImgReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, uploadImgRes.statusCode())
        val uploadBody = json.decodeFromString<Map<String, String>>(uploadImgRes.body())
        val imageKey = uploadBody["imageFileName"]
        assertEquals("organic_tomato", imageKey)

        // 2. Fetch thumbnail via /api/veggie/images/<imageKey>?thumb=true and verify ETag 304
        val getThumbReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/veggie/images/$imageKey?thumb=true"))
            .GET()
            .build()
        val getThumbRes = client.send(getThumbReq, HttpResponse.BodyHandlers.ofByteArray())
        assertEquals(200, getThumbRes.statusCode())
        assertTrue(getThumbRes.headers().firstValue("Content-Type").orElse("").contains("image/jpeg"))
        val imgEtag = getThumbRes.headers().firstValue("ETag").orElse(null)
        assertNotNull(imgEtag)

        val condThumbReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/veggie/images/$imageKey?thumb=true"))
            .header("If-None-Match", imgEtag)
            .GET()
            .build()
        val condThumbRes = client.send(condThumbReq, HttpResponse.BodyHandlers.ofByteArray())
        assertEquals(304, condThumbRes.statusCode())

        // 3. Open round, add entry, close round, download Buyer & Worker PDFs, and settle
        val openRoundReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/veggie/rounds/open"))
            .header("Cookie", adminCookie)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(OpenVeggieRoundRequest(200L, 400L, 1731150000000L))))
            .build()
        val round = json.decodeFromString<VeggieOrderRound>(
            client.send(openRoundReq, HttpResponse.BodyHandlers.ofString()).body()
        )

        val buyerPdfReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/veggie/rounds/${round.id}/buyer-pdf?lang=EN"))
            .header("Cookie", adminCookie)
            .GET()
            .build()
        val buyerPdfRes = client.send(buyerPdfReq, HttpResponse.BodyHandlers.ofByteArray())
        assertEquals(200, buyerPdfRes.statusCode())
        assertEquals("%PDF-", String(buyerPdfRes.body(), 0, 5, Charsets.US_ASCII))

        val workerPdfReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/veggie/rounds/${round.id}/worker-pdf?lang=DE"))
            .header("Cookie", adminCookie)
            .GET()
            .build()
        val workerPdfRes = client.send(workerPdfReq, HttpResponse.BodyHandlers.ofByteArray())
        assertEquals(200, workerPdfRes.statusCode())
        assertEquals("%PDF-", String(workerPdfRes.body(), 0, 5, Charsets.US_ASCII))

        // Close and settle round
        val closeReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/veggie/rounds/${round.id}/close"))
            .header("Cookie", adminCookie)
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
            .header("Cookie", adminCookie)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(settlePayload)))
            .build()
        val settleRes = client.send(settleReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, settleRes.statusCode())
        val settlementResult = json.decodeFromString<VeggieRoundSettlementResult>(settleRes.body())
        assertEquals(600L, settlementResult.combinedFeeCents)
        assertEquals("u-alice", settlementResult.transporterUserId)

        // 4. Cancel-product endpoint succeeds on OPEN round and returns 409 on SETTLED round
        val cancelClosedReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/veggie/rounds/${round.id}/cancel-product/vp-tomato"))
            .header("Cookie", adminCookie)
            .POST(HttpRequest.BodyPublishers.noBody())
            .build()
        assertEquals(409, client.send(cancelClosedReq, HttpResponse.BodyHandlers.ofString()).statusCode())

        val openRound2Req = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/veggie/rounds/open"))
            .header("Cookie", adminCookie)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(OpenVeggieRoundRequest(200L, 400L, 1731200000000L))))
            .build()
        val round2 = json.decodeFromString<VeggieOrderRound>(
            client.send(openRound2Req, HttpResponse.BodyHandlers.ofString()).body()
        )
        val cancelOpenReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/veggie/rounds/${round2.id}/cancel-product/vp-tomato"))
            .header("Cookie", adminCookie)
            .POST(HttpRequest.BodyPublishers.noBody())
            .build()
        assertEquals(200, client.send(cancelOpenReq, HttpResponse.BodyHandlers.ofString()).statusCode())
    }

    @Test
    fun testUiSettingsAndHeaderAuthRemoval() {
        val adminCookie = getValidAdminCookie()

        // 1. Legacy X-Admin-Password header without session cookie must be rejected
        val legacyHeaderReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/ui-settings"))
            .header("X-Admin-Password", "secretPass123")
            .GET()
            .build()
        val legacyHeaderRes = client.send(legacyHeaderReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(401, legacyHeaderRes.statusCode())

        // 2. GET /api/admin/ui-settings with cookie returns settings without adminPasswordHash
        val uiSettingsReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/ui-settings"))
            .header("Cookie", adminCookie)
            .GET()
            .build()
        val uiSettingsRes = client.send(uiSettingsReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, uiSettingsRes.statusCode())
        val settings = testJson.decodeFromString<SystemSettings>(uiSettingsRes.body())
        assertEquals("", settings.adminPasswordHash)

        // 3. GET /api/admin/transactions?userId=... succeeds
        val userTxReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/transactions?userId=u-alice"))
            .header("Cookie", adminCookie)
            .GET()
            .build()
        val userTxRes = client.send(userTxReq, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, userTxRes.statusCode())
    }

    @Test
    fun testUniqueCustomerNamesAccentInsensitiveLoginAndSessionRevocation() {
        val json = Json { ignoreUnknownKeys = true }
        val adminCookie = getValidAdminCookie()

        // 1. Admin creates user "João" with PIN "9876"
        val joao = User(
            id = "u-joao",
            name = "João",
            pin = de.joelneumann.lojinha.security.PasswordHasher.hash("9876"),
            language = Language.BR
        )
        val createJoaoReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$testPort/api/admin/users"))
            .header("Cookie", adminCookie)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(joao)))
            .build()
        assertEquals(200, client.send(createJoaoReq, HttpResponse.BodyHandlers.ofString()).statusCode())

        // 2. Duplicate normalized name ("joao") and reserved name ("Admin") must be rejected with 400
        val dupJoao = joao.copy(id = "u-joao-dup", name = "joao")
        val dupRes = client.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$testPort/api/admin/users"))
                .header("Cookie", adminCookie)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(dupJoao)))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )
        assertEquals(400, dupRes.statusCode())

        val reservedAdminUser = joao.copy(id = "u-admin-dup", name = " admin ")
        val reservedRes = client.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$testPort/api/admin/users"))
                .header("Cookie", adminCookie)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(reservedAdminUser)))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )
        assertEquals(400, reservedRes.statusCode())

        // 3. Customer logs in with accent-stripped "joao"
        val joaoLoginRes = client.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$testPort/api/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(UnifiedLoginRequest("joao", "9876"))))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )
        assertEquals(200, joaoLoginRes.statusCode())
        val joaoCookie = extractCookie(joaoLoginRes, "lojinha_customer_session")

        // Verify /api/customer/me succeeds
        assertEquals(
            200,
            client.send(
                HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:$testPort/api/customer/me"))
                    .header("Cookie", joaoCookie)
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            ).statusCode()
        )

        // 4. Admin deactivates João -> existing customer session is immediately revoked (401)
        assertEquals(
            200,
            client.send(
                HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:$testPort/api/admin/users/deactivate/u-joao"))
                    .header("Cookie", adminCookie)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            ).statusCode()
        )
        assertEquals(
            401,
            client.send(
                HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:$testPort/api/customer/me"))
                    .header("Cookie", joaoCookie)
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            ).statusCode()
        )
    }

    @Test
    fun testBlankAdminPasswordFallbackIsDisabledOnWebLogin() {
        val noPassSettings = SystemSettings(adminPasswordHash = "")
        val noPassSettingsRepo = object : SettingsRepository {
            override suspend fun getSettings(): SystemSettings = noPassSettings
            override fun getSettingsFlow(): Flow<SystemSettings> = flowOf(noPassSettings)
            override suspend fun updateSettings(settings: SystemSettings, notifyDataChanged: Boolean) {}
        }
        val isolatedPort = 18086
        val isolatedServer = LojinhaAdminServer(
            productRepository = fakeProductRepo,
            userRepository = fakeUserRepo,
            transactionRepository = fakeTransactionRepo,
            billingListRepository = fakeBillingListRepo,
            settingsRepository = noPassSettingsRepo,
            port = isolatedPort
        )
        isolatedServer.start()
        Thread.sleep(300)
        try {
            val res = client.send(
                HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:$isolatedPort/api/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("""{"identifier":"Admin","password":"admin"}"""))
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            )
            assertEquals(401, res.statusCode())
        } finally {
            isolatedServer.stop()
            Thread.sleep(200)
        }
    }

    @Test
    fun testDefaultAdminPasswordHashIsRejectedOnWebLogin() {
        val defaultAdminSettings = SystemSettings(
            adminPasswordHash = de.joelneumann.lojinha.security.PasswordHasher.hash("admin")
        )
        val defaultSettingsRepo = object : SettingsRepository {
            override suspend fun getSettings(): SystemSettings = defaultAdminSettings
            override fun getSettingsFlow(): Flow<SystemSettings> = flowOf(defaultAdminSettings)
            override suspend fun updateSettings(settings: SystemSettings, notifyDataChanged: Boolean) {}
        }
        val isolatedPort = 18087
        val isolatedServer = LojinhaAdminServer(
            productRepository = fakeProductRepo,
            userRepository = fakeUserRepo,
            transactionRepository = fakeTransactionRepo,
            billingListRepository = fakeBillingListRepo,
            settingsRepository = defaultSettingsRepo,
            port = isolatedPort
        )
        isolatedServer.start()
        Thread.sleep(300)
        try {
            // Admin login with default "admin" password must be rejected
            val adminRes = client.send(
                HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:$isolatedPort/api/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("""{"identifier":"Admin","password":"admin"}"""))
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            )
            assertEquals(401, adminRes.statusCode())

            // Customer admin-bypass with default "admin" password must also be rejected
            val customerBypassRes = client.send(
                HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:$isolatedPort/api/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("""{"identifier":"Alice","password":"admin"}"""))
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            )
            assertEquals(401, customerBypassRes.statusCode())
        } finally {
            isolatedServer.stop()
            Thread.sleep(200)
        }
    }

    @Test
    fun testSecurityHeadersCspHstsOptionsAndSessionDiscovery() {
        // 1. Plain HTTP has CSP, but NO HSTS
        val httpRes = client.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$testPort/api/session"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )
        assertEquals(200, httpRes.statusCode())
        assertTrue(httpRes.headers().firstValue("Content-Security-Policy").orElse("").contains("'wasm-unsafe-eval'"))
        assertFalse(httpRes.headers().firstValue("Strict-Transport-Security").isPresent)
        assertNull(testJson.decodeFromString<SessionStatusResponse>(httpRes.body()).role)

        // 2. HTTPS proxy header adds HSTS
        val httpsRes = client.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$testPort/api/health"))
                .header("X-Forwarded-Proto", "https")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )
        assertEquals(200, httpsRes.statusCode())
        assertEquals("max-age=31536000", httpsRes.headers().firstValue("Strict-Transport-Security").orElse(null))

        // 3. OPTIONS requests are rejected (405 Method Not Allowed) without CORS
        val adminOptionsRes = client.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$testPort/api/admin/users"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )
        assertEquals(405, adminOptionsRes.statusCode())

        val customerOptionsRes = client.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$testPort/api/customer/me"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )
        assertEquals(405, customerOptionsRes.statusCode())

        // 4. Malformed JSON on POST /api/login returns 400 Bad Request
        val badJsonRes = client.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$testPort/api/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{bad"))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )
        assertEquals(400, badJsonRes.statusCode())

        // 5. /api/session reports ADMIN and CUSTOMER roles accurately
        val adminCookie = getValidAdminCookie()
        val adminSessionRes = client.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$testPort/api/session"))
                .header("Cookie", adminCookie)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )
        assertEquals("ADMIN", testJson.decodeFromString<SessionStatusResponse>(adminSessionRes.body()).role)

        val aliceLoginRes = client.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$testPort/api/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""{"identifier":"Alice","password":"1234"}"""))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )
        val customerCookie = extractCookie(aliceLoginRes, "lojinha_customer_session")
        val customerSessionRes = client.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$testPort/api/session"))
                .header("Cookie", customerCookie)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )
        assertEquals("CUSTOMER", testJson.decodeFromString<SessionStatusResponse>(customerSessionRes.body()).role)
    }

    @Test
    fun testAdminShoppingListEndpointsAndPdfExportWithCookieAuth() {
        // 1. Unauthenticated requests must be rejected with 401
        val unauthGet = client.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$testPort/api/admin/shopping-list"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )
        assertEquals(401, unauthGet.statusCode())

        val unauthPdf = client.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$testPort/api/admin/shopping-list/pdf?lang=EN"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofByteArray()
        )
        assertEquals(401, unauthPdf.statusCode())

        // 2. Cookie-authenticated CRUD + PDF download
        val adminCookie = getValidAdminCookie()
        val addRes = client.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$testPort/api/admin/shopping-list"))
                .header("Cookie", adminCookie)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(testJson.encodeToString(ShoppingListItem(id = "sl-test-1", name = "Oat Milk", quantity = "6 L"))))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )
        assertEquals(200, addRes.statusCode())

        val listRes = client.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$testPort/api/admin/shopping-list"))
                .header("Cookie", adminCookie)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )
        assertEquals(200, listRes.statusCode())
        val items = testJson.decodeFromString<List<ShoppingListItem>>(listRes.body())
        assertEquals(1, items.size)
        assertEquals("Oat Milk", items.first().name)

        val pdfRes = client.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$testPort/api/admin/shopping-list/pdf?lang=EN"))
                .header("Cookie", adminCookie)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofByteArray()
        )
        assertEquals(200, pdfRes.statusCode())
        assertEquals("%PDF-", String(pdfRes.body(), 0, 5, Charsets.US_ASCII))
        assertTrue(
            pdfRes.headers().firstValue("Content-Disposition").orElse("").contains("Shopping_List_"),
            "Content-Disposition should use localized Shopping_List_ filename"
        )
    }

    @Test
    fun testUserNicknameValidationAndWebLogin() {
        val adminCookie = getValidAdminCookie()

        val u1 = User(
            id = "u-nick-1",
            name = "Robert Johnson",
            nickname = "Bobby",
            pin = de.joelneumann.lojinha.security.PasswordHasher.hash("4321"),
            isActive = true
        )
        val saveU1Res = client.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$testPort/api/admin/users"))
                .header("Cookie", adminCookie)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(testJson.encodeToString(u1)))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )
        assertEquals(200, saveU1Res.statusCode())

        // > 10 chars rejected
        val tooLongRes = client.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$testPort/api/admin/users"))
                .header("Cookie", adminCookie)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(testJson.encodeToString(u1.copy(nickname = "TooLongNick11"))))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )
        assertEquals(400, tooLongRes.statusCode())

        // "Admin" nickname rejected
        val adminNickRes = client.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$testPort/api/admin/users"))
                .header("Cookie", adminCookie)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(testJson.encodeToString(u1.copy(nickname = "Admin"))))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )
        assertEquals(400, adminNickRes.statusCode())

        // Another user's nickname colliding with u1's nickname (case-insensitive) rejected
        val u2 = User(id = "u-nick-2", name = "Clara", nickname = "bobby", isActive = true)
        val dupNickRes = client.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$testPort/api/admin/users"))
                .header("Cookie", adminCookie)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(testJson.encodeToString(u2)))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )
        assertEquals(400, dupNickRes.statusCode())

        // Another user's name colliding with u1's nickname rejected
        val nameCollidesNickRes = client.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$testPort/api/admin/users"))
                .header("Cookie", adminCookie)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(testJson.encodeToString(User(id = "u-nick-3", name = "bobby", isActive = true))))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )
        assertEquals(400, nameCollidesNickRes.statusCode())

        // Customer login with nickname (case-insensitive) succeeds
        val loginRes = client.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$testPort/api/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""{"identifier":"bobby","password":"4321"}"""))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )
        assertEquals(200, loginRes.statusCode())
        assertTrue(loginRes.body().contains("CUSTOMER"))
    }

    @Test
    fun testCsvExportEndpoints_excludeDeletedAndRequireAuth() {
        val client = HttpClient.newHttpClient()

        // 1. Unauthenticated request is rejected
        val unauthRes = client.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$testPort/api/admin/export/users.csv"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )
        assertEquals(401, unauthRes.statusCode())

        val adminCookie = getValidAdminCookie()

        // Seed data including soft-deleted user & soft-deleted veggie product
        fakeUsers.add(User(id = "csv-u-active", name = "CSV Active User", isActive = true, isDeleted = false))
        fakeUsers.add(User(id = "csv-u-deleted", name = "CSV Deleted User", isActive = false, isDeleted = true))
        kotlinx.coroutines.runBlocking {
            fakeProductRepo.saveProduct(Product(id = "csv-p-1", name = "CSV Soda", basePrice = 500, stockQuantity = 10))
            fakeAdminNoteRepo.addNote(AdminNote(id = "csv-n-1", userId = "csv-u-active", userNameSnapshot = "CSV Active User", timestamp = 1000L, message = "Hello CSV"))
            fakeTransactionRepo.recordTransaction(
                Transaction(
                    id = "csv-tx-1",
                    userId = "csv-u-active",
                    userNameSnapshot = "CSV Active User",
                    timestamp = 2000L,
                    type = TransactionType.PURCHASE,
                    totalAmount = -500,
                    items = listOf(TransactionItem("csv-p-1", "CSV Soda", UnitType.PIECE, 1, 500))
                )
            )
        }
        fakeVeggieUnitTypes.add(VeggieUnitType(id = "csv-vu-1", name = "kg", allowDecimals = true))
        fakeVeggieProducts.add(VeggieProduct(id = "csv-vp-active", name = "CSV Carrots", unitTypeId = "csv-vu-1", isActive = true, isDeleted = false))
        fakeVeggieProducts.add(VeggieProduct(id = "csv-vp-deleted", name = "CSV Deleted Kale", unitTypeId = "csv-vu-1", isActive = false, isDeleted = true))
        fakeVeggieRounds.add(VeggieOrderRound(id = "csv-vr-1", openedAt = 3000L, status = VeggieRoundStatus.OPEN))

        fun getCsv(path: String): String {
            val res = client.send(
                HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:$testPort$path"))
                    .header("Cookie", adminCookie)
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            )
            assertEquals(200, res.statusCode(), "Expected 200 for $path")
            val contentType = res.headers().firstValue("Content-Type").orElse("")
            assertTrue(contentType.contains("text/csv", ignoreCase = true), "Expected text/csv for $path, got $contentType")
            val disposition = res.headers().firstValue("Content-Disposition").orElse("")
            assertTrue(disposition.contains("attachment", ignoreCase = true), "Expected attachment for $path, got $disposition")
            assertTrue(res.body().startsWith("\uFEFF"), "Expected UTF-8 BOM for $path")
            return res.body()
        }

        val notesCsv = getCsv("/api/admin/export/notes.csv")
        assertTrue(notesCsv.contains("Hello CSV"))

        val productsCsv = getCsv("/api/admin/export/products.csv")
        assertTrue(productsCsv.contains("CSV Soda"))

        val productHistoryCsv = getCsv("/api/admin/export/products/csv-p-1/history.csv")
        assertTrue(productHistoryCsv.contains("id;productId;timestamp;type"))

        val usersCsv = getCsv("/api/admin/export/users.csv")
        assertTrue(usersCsv.contains("CSV Active User"))
        assertFalse(usersCsv.contains("CSV Deleted User"))

        val userTxCsv = getCsv("/api/admin/export/users/csv-u-active/transactions.csv")
        assertTrue(userTxCsv.contains("csv-tx-1"))

        val allTxCsv = getCsv("/api/admin/export/transactions.csv")
        assertTrue(allTxCsv.contains("csv-tx-1"))

        val veggieProductsCsv = getCsv("/api/admin/export/veggie-products.csv")
        assertTrue(veggieProductsCsv.contains("CSV Carrots"))
        assertFalse(veggieProductsCsv.contains("CSV Deleted Kale"))

        val veggieOrdersCsv = getCsv("/api/admin/export/veggie-orders.csv")
        assertTrue(veggieOrdersCsv.contains("csv-vr-1"))
    }

    @Test
    fun testBuyerLoginWithPasswordAndAdminBypass() {
        var buyerSettings = SystemSettings(
            adminPasswordHash = de.joelneumann.lojinha.security.PasswordHasher.hash("adminSecret99"),
            buyerPasswordHash = de.joelneumann.lojinha.security.PasswordHasher.hash("buyerSecret42")
        )
        val buyerSettingsRepo = object : SettingsRepository {
            override suspend fun getSettings(): SystemSettings = buyerSettings
            override fun getSettingsFlow(): Flow<SystemSettings> = flowOf(buyerSettings)
            override suspend fun updateSettings(settings: SystemSettings, notifyDataChanged: Boolean) {
                buyerSettings = settings
            }
        }
        val isolatedPort = 18088
        val isolatedServer = LojinhaAdminServer(
            productRepository = fakeProductRepo,
            userRepository = fakeUserRepo,
            transactionRepository = fakeTransactionRepo,
            billingListRepository = fakeBillingListRepo,
            settingsRepository = buyerSettingsRepo,
            veggieOrderRepository = fakeVeggieRepo,
            port = isolatedPort
        )
        isolatedServer.start()
        Thread.sleep(300)
        try {
            // 1. Wrong password is rejected
            val badRes = client.send(
                HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:$isolatedPort/api/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("""{"identifier":"buyer","password":"wrong"}"""))
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            )
            assertEquals(401, badRes.statusCode())

            // 2. Buyer password succeeds and sets lojinha_buyer_session cookie
            val buyerRes = client.send(
                HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:$isolatedPort/api/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("""{"identifier":"buyer","password":"buyerSecret42"}"""))
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            )
            assertEquals(200, buyerRes.statusCode())
            val loginBody = testJson.decodeFromString<UnifiedLoginResponse>(buyerRes.body())
            assertTrue(loginBody.success)
            assertEquals("BUYER", loginBody.role)
            val buyerCookie = buyerRes.headers().allValues("Set-Cookie")
                .firstOrNull { it.startsWith("lojinha_buyer_session=") }
                ?.substringBefore(";")
            assertNotNull(buyerCookie)

            // 3. /api/session reports BUYER
            val sessionRes = client.send(
                HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:$isolatedPort/api/session"))
                    .header("Cookie", buyerCookie!!)
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            )
            assertEquals(200, sessionRes.statusCode())
            assertEquals("BUYER", testJson.decodeFromString<SessionStatusResponse>(sessionRes.body()).role)

            // 4. Buyer cannot access admin or customer endpoints
            val adminForbidden = client.send(
                HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:$isolatedPort/api/admin/users"))
                    .header("Cookie", buyerCookie)
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            )
            assertEquals(401, adminForbidden.statusCode())

            val customerForbidden = client.send(
                HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:$isolatedPort/api/customer/me"))
                    .header("Cookie", buyerCookie)
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            )
            assertEquals(401, customerForbidden.statusCode())

            // 5. Admin password bypasses Buyer password
            val adminBypassRes = client.send(
                HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:$isolatedPort/api/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("""{"identifier":"buyer","password":"adminSecret99"}"""))
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            )
            assertEquals(200, adminBypassRes.statusCode())
            assertEquals("BUYER", testJson.decodeFromString<UnifiedLoginResponse>(adminBypassRes.body()).role)

            // 5b. Rotating or disabling buyer password immediately revokes active buyer token
            buyerSettings = buyerSettings.copy(buyerPasswordHash = "")
            val revokedSession = client.send(
                HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:$isolatedPort/api/session"))
                    .header("Cookie", buyerCookie)
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            )
            assertNull(testJson.decodeFromString<SessionStatusResponse>(revokedSession.body()).role)

            val revokedApi = client.send(
                HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:$isolatedPort/api/buyer/veggie/state"))
                    .header("Cookie", buyerCookie)
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            )
            assertEquals(401, revokedApi.statusCode())

            // 6. Logout clears BUYER session
            val adminBuyerCookie = adminBypassRes.headers().allValues("Set-Cookie")
                .firstOrNull { it.startsWith("lojinha_buyer_session=") }
                ?.substringBefore(";")
            assertNotNull(adminBuyerCookie)
            val logoutRes = client.send(
                HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:$isolatedPort/api/buyer/logout"))
                    .header("Cookie", adminBuyerCookie!!)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            )
            assertEquals(200, logoutRes.statusCode())

            val afterLogoutSession = client.send(
                HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:$isolatedPort/api/session"))
                    .header("Cookie", adminBuyerCookie)
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            )
            assertNull(testJson.decodeFromString<SessionStatusResponse>(afterLogoutSession.body()).role)
        } finally {
            isolatedServer.stop()
            Thread.sleep(200)
        }
    }

    @Test
    fun testBuyerVeggieRoundLifecycleAndReopenGuard() {
        var buyerSettings = SystemSettings(
            adminPasswordHash = de.joelneumann.lojinha.security.PasswordHasher.hash("adminSecret99"),
            buyerPasswordHash = de.joelneumann.lojinha.security.PasswordHasher.hash("buyerSecret42")
        )
        val buyerSettingsRepo = object : SettingsRepository {
            override suspend fun getSettings(): SystemSettings = buyerSettings
            override fun getSettingsFlow(): Flow<SystemSettings> = flowOf(buyerSettings)
            override suspend fun updateSettings(settings: SystemSettings, notifyDataChanged: Boolean) {
                buyerSettings = settings
            }
        }
        val isolatedPort = 18089
        val isolatedServer = LojinhaAdminServer(
            productRepository = fakeProductRepo,
            userRepository = fakeUserRepo,
            transactionRepository = fakeTransactionRepo,
            billingListRepository = fakeBillingListRepo,
            settingsRepository = buyerSettingsRepo,
            veggieOrderRepository = fakeVeggieRepo,
            port = isolatedPort
        )
        isolatedServer.start()
        Thread.sleep(300)
        try {
            fakeVeggieProducts.clear()
            fakeVeggieProducts.add(
                VeggieProduct(
                    id = "vp-buyer-1",
                    name = "Buyer Tomatoes",
                    unitTypeId = "unit-kg",
                    estimatedPriceCents = 800L,
                    excludeFromBuyerPdf = false
                )
            )
            fakeVeggieProducts.add(
                VeggieProduct(
                    id = "vp-buyer-excluded",
                    name = "Local Honey",
                    unitTypeId = "unit-piece",
                    estimatedPriceCents = 1500L,
                    excludeFromBuyerPdf = true
                )
            )
            fakeVeggieRounds.clear()
            fakeVeggieEntries.clear()
            fakeVeggieSettlements.clear()

            val round = VeggieOrderRound(
                id = "vr-buyer-1",
                openedAt = 1000L,
                status = VeggieRoundStatus.OPEN,
                buyerOperationFeeCents = 500L,
                transportFeeCents = 1200L,
                transporterUserId = "u1",
                updatedAt = 1000L
            )
            fakeVeggieRounds.add(round)
            fakeVeggieEntries.add(
                VeggieOrderEntry(
                    id = "ve-buyer-1",
                    roundId = round.id,
                    userId = "u1",
                    userNameSnapshot = "Alice",
                    veggieProductId = "vp-buyer-1",
                    veggieNameSnapshot = "Buyer Tomatoes",
                    unitNameSnapshot = "kg",
                    allowDecimalsSnapshot = true,
                    orderedQuantityScaled = 2500L,
                    updatedAt = 1000L
                )
            )
            fakeVeggieEntries.add(
                VeggieOrderEntry(
                    id = "ve-buyer-2",
                    roundId = round.id,
                    userId = "u1",
                    userNameSnapshot = "Alice",
                    veggieProductId = "vp-buyer-excluded",
                    veggieNameSnapshot = "Local Honey",
                    unitNameSnapshot = "Unit",
                    allowDecimalsSnapshot = false,
                    orderedQuantityScaled = 1000L,
                    updatedAt = 1000L
                )
            )

            val loginRes = client.send(
                HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:$isolatedPort/api/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("""{"identifier":"buyer","password":"buyerSecret42"}"""))
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            )
            val buyerCookie = loginRes.headers().allValues("Set-Cookie")
                .first { it.startsWith("lojinha_buyer_session=") }
                .substringBefore(";")

            // 1. While OPEN, state has empty items and PDF / draft save return 409 Conflict
            val openStateRes = client.send(
                HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:$isolatedPort/api/buyer/veggie/state"))
                    .header("Cookie", buyerCookie)
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            )
            assertEquals(200, openStateRes.statusCode())
            val openState = testJson.decodeFromString<BuyerVeggieRoundStateResponse>(openStateRes.body())
            assertEquals(VeggieRoundStatus.OPEN, openState.round?.status)
            assertTrue(openState.items.isEmpty())

            val openPdfRes = client.send(
                HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:$isolatedPort/api/buyer/veggie/round/buyer-pdf"))
                    .header("Cookie", buyerCookie)
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofByteArray()
            )
            assertEquals(409, openPdfRes.statusCode())

            // 2. Close round -> Buyer sees aggregated items (excluding excludeFromBuyerPdf), exports PDF, saves draft
            fakeVeggieRounds[0] = round.copy(status = VeggieRoundStatus.CLOSED, closedAt = 2000L, updatedAt = 2000L)

            val closedStateRes = client.send(
                HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:$isolatedPort/api/buyer/veggie/state"))
                    .header("Cookie", buyerCookie)
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            )
            assertEquals(200, closedStateRes.statusCode())
            val closedState = testJson.decodeFromString<BuyerVeggieRoundStateResponse>(closedStateRes.body())
            assertEquals(1, closedState.items.size)
            assertEquals("vp-buyer-1", closedState.items.first().productId)
            assertEquals(2500L, closedState.items.first().totalOrderedScaled)
            assertNull(closedState.items.first().marketUnitPriceCents)

            val closedPdfRes = client.send(
                HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:$isolatedPort/api/buyer/veggie/round/buyer-pdf"))
                    .header("Cookie", buyerCookie)
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofByteArray()
            )
            assertEquals(200, closedPdfRes.statusCode())
            assertTrue(closedPdfRes.body().isNotEmpty())
            assertNotNull(fakeVeggieRounds[0].buyerPortalPdfExportedAt)
            assertNull(fakeVeggieRounds[0].buyerPdfExportedAt)

            val saveReqJson = testJson.encodeToString(
                BuyerSaveDraftSettlementRequest.serializer(),
                BuyerSaveDraftSettlementRequest(
                    roundId = round.id,
                    buyerOperationFeeCents = 750L,
                    productSettlements = listOf(
                        BuyerProductPriceDraft(
                            productId = "vp-buyer-1",
                            marketUnitPriceCents = 900L,
                            totalSpentCents = 2250L
                        )
                    )
                )
            )
            val saveRes = client.send(
                HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:$isolatedPort/api/buyer/veggie/round/draft-settlement"))
                    .header("Cookie", buyerCookie)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(saveReqJson))
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            )
            assertEquals(200, saveRes.statusCode())
            // Transport fee & transporter preserved, buyer fee updated
            assertEquals(750L, fakeVeggieRounds[0].buyerOperationFeeCents)
            assertEquals(1200L, fakeVeggieRounds[0].transportFeeCents)
            assertEquals("u1", fakeVeggieRounds[0].transporterUserId)

            // 3. Reopen round -> Buyer is locked out again (409 on draft save, empty items on state)
            fakeVeggieRounds[0] = fakeVeggieRounds[0].copy(status = VeggieRoundStatus.OPEN, closedAt = null, updatedAt = 3000L)
            val reopenedSaveRes = client.send(
                HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:$isolatedPort/api/buyer/veggie/round/draft-settlement"))
                    .header("Cookie", buyerCookie)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(saveReqJson))
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            )
            assertEquals(409, reopenedSaveRes.statusCode())
        } finally {
            isolatedServer.stop()
            Thread.sleep(200)
        }
    }
}

