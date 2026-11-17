package de.joelneumann.lojinha.data.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.joelneumann.lojinha.data.database.AppDatabase
import de.joelneumann.lojinha.data.database.AppDatabase_Impl
import de.joelneumann.lojinha.data.database.DatabaseFactory
import de.joelneumann.lojinha.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class RoomVeggieOrderRepositoryTest {

    private lateinit var tempDir: File
    private lateinit var dbFile: File
    private lateinit var db: AppDatabase
    private lateinit var veggieRepo: RoomVeggieOrderRepositoryImpl
    private lateinit var userRepo: RoomUserRepositoryImpl
    private lateinit var settingsRepo: RoomSettingsRepositoryImpl
    private lateinit var txRepo: RoomTransactionRepositoryImpl

    @BeforeTest
    fun setup() = runTest {
        tempDir = Files.createTempDirectory("veggie_repo_test").toFile()
        dbFile = File(tempDir, "test_veggie.db")

        val builder = Room.databaseBuilder<AppDatabase>(
            name = dbFile.absolutePath,
            factory = { AppDatabase_Impl() }
        )
        builder.setDriver(BundledSQLiteDriver())
        builder.setQueryCoroutineContext(Dispatchers.IO)
        db = builder.build()

        // Seed default unit types just like DatabaseFactory.seedInitialData
        db.veggieOrderDao().insertOrUpdateUnitTypes(DatabaseFactory.defaultVeggieUnitTypes(1000L))

        veggieRepo = RoomVeggieOrderRepositoryImpl(
            veggieOrderDao = db.veggieOrderDao(),
            userDao = db.userDao(),
            transactionDao = db.transactionDao(),
            runInTransaction = { block -> DatabaseFactory.runImmediateTransaction(db, block) }
        )
        userRepo = RoomUserRepositoryImpl(
            userDao = db.userDao(),
            transactionDao = db.transactionDao(),
            veggieOrderDao = db.veggieOrderDao()
        )
        settingsRepo = RoomSettingsRepositoryImpl(
            settingsDao = db.settingsDao()
        )
        txRepo = RoomTransactionRepositoryImpl(
            transactionDao = db.transactionDao()
        )
    }

    @AfterTest
    fun tearDown() {
        db.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun testDefaultUnitTypesAndProductCatalogLifecycle() = runTest {
        val units = veggieRepo.getAllUnitTypes()
        assertEquals(4, units.size)
        val kgUnit = units.first { it.id == "unit-kg" }
        assertTrue(kgUnit.allowDecimals)
        val pieceUnit = units.first { it.id == "unit-piece" }
        assertFalse(pieceUnit.allowDecimals)

        // Create custom unit type
        val crateUnit = VeggieUnitType(id = "unit-crate", name = "Crate", allowDecimals = false)
        veggieRepo.saveUnitType(crateUnit)
        assertEquals(5, veggieRepo.getAllUnitTypes().size)

        // Create two products (one with price, one without price yet)
        val tomato = VeggieProduct(
            id = "vp-tomato",
            name = "Tomato",
            description = "Organic red tomatoes",
            estimatedPriceCents = 850L,
            unitTypeId = "unit-kg"
        )
        val kale = VeggieProduct(
            id = "vp-kale",
            name = "Kale",
            description = null,
            estimatedPriceCents = null, // Price unknown initially
            unitTypeId = "unit-bunch"
        )
        veggieRepo.saveVeggieProduct(tomato)
        veggieRepo.saveVeggieProduct(kale)

        val products = veggieRepo.getAllVeggieProducts()
        assertEquals(2, products.size)
        assertEquals("Kale", products[0].name)
        assertNull(products[0].estimatedPriceCents)
        assertEquals("Tomato", products[1].name)
        assertEquals(850L, products[1].estimatedPriceCents)

        // Hard delete product not in any order -> removed completely
        veggieRepo.hardDeleteVeggieProduct("vp-kale")
        assertNull(db.veggieOrderDao().getVeggieProductById("vp-kale"))
    }

    @Test
    fun testRoundLifecycle_customerOrders_andPreviousRoundQuantities() = runTest {
        val alice = User(id = "u-alice", name = "Alice", pin = "hash", requirePinOnKiosk = false)
        userRepo.saveUser(alice)
        val loadedAlice = userRepo.getUserById("u-alice")!!
        assertFalse(loadedAlice.requirePinOnKiosk)

        val tomato = VeggieProduct(id = "vp-tomato", name = "Tomato", estimatedPriceCents = 1000L, unitTypeId = "unit-kg")
        val avocado = VeggieProduct(id = "vp-avocado", name = "Avocado", estimatedPriceCents = 600L, unitTypeId = "unit-piece")
        veggieRepo.saveVeggieProduct(tomato)
        veggieRepo.saveVeggieProduct(avocado)

        // Round 1
        val round1 = veggieRepo.openNewRound(
            buyerOperationFeeCents = 1000L,
            transportFeeCents = 500L,
            openedAt = 1_700_000_000_000L
        )
        assertEquals(VeggieRoundStatus.OPEN, round1.status)

        // Alice orders 1.5 kg Tomato (1500) and 2 Avocados (2000)
        val saved = veggieRepo.saveCustomerOrder(
            roundId = round1.id,
            user = loadedAlice,
            productQuantitiesScaled = mapOf(
                "vp-tomato" to 1500L,
                "vp-avocado" to 2000L
            )
        )
        assertTrue(saved)

        // Because Alice has a veggie order entry, she cannot be hard-deleted
        assertFalse(userRepo.canHardDeleteUser("u-alice"))

        // Close Round 1, test that customer can no longer edit while closed, then reopen and edit, then settle
        assertTrue(veggieRepo.closeRound(round1.id, closedAt = 1_700_010_000_000L))
        assertFalse(
            veggieRepo.saveCustomerOrder(
                roundId = round1.id,
                user = loadedAlice,
                productQuantitiesScaled = mapOf("vp-tomato" to 3000L)
            )
        )
        assertTrue(veggieRepo.reopenRound(round1.id))
        assertTrue(
            veggieRepo.saveCustomerOrder(
                roundId = round1.id,
                user = loadedAlice,
                productQuantitiesScaled = mapOf(
                    "vp-tomato" to 1750L,
                    "vp-avocado" to 2000L
                )
            )
        )
        assertTrue(veggieRepo.closeRound(round1.id, closedAt = 1_700_020_000_000L))

        // Settle Round 1
        val r1Entries = veggieRepo.getEntriesForRound(round1.id)
        assertNotNull(
            veggieRepo.executeRoundSettlement(
                roundId = round1.id,
                buyerOperationFeeCents = 1000L,
                transportFeeCents = 500L,
                transporterUserId = null,
                productSettlements = listOf(
                    VeggieRoundProductSettlement(round1.id, "vp-tomato", marketUnitPriceCents = 1000L, totalSpentCents = 1750L),
                    VeggieRoundProductSettlement(round1.id, "vp-avocado", marketUnitPriceCents = 600L, totalSpentCents = 1200L)
                ),
                actualQuantitiesByEntryId = r1Entries.associate { it.id to it.orderedQuantityScaled },
                settledAt = 1_700_030_000_000L
            )
        )

        // Open Round 2 and verify "Last order: X" badge data from Round 1
        val round2 = veggieRepo.openNewRound(
            buyerOperationFeeCents = 1000L,
            transportFeeCents = 500L,
            openedAt = 1_700_600_000_000L
        )
        val prevQuantities = veggieRepo.getPreviousRoundQuantitiesForUser(round2.id, "u-alice")
        assertEquals(1750L, prevQuantities["vp-tomato"])
        assertEquals(2000L, prevQuantities["vp-avocado"])

        // Soft-delete protection on ordered product
        veggieRepo.hardDeleteVeggieProduct("vp-tomato")
        val softDeletedTomato = db.veggieOrderDao().getVeggieProductById("vp-tomato")
        assertNotNull(softDeletedTomato)
        assertTrue(softDeletedTomato.isDeleted)
    }

    @Test
    fun testFullSettlement_ledgerIntegration_transporterDeposit_andPostSettlementCorrection() = runTest {
        // Users: Alice (initial balance R$ 100,00 = 10000), Bob (also Transporter, initial balance R$ 50,00 = 5000)
        userRepo.saveUser(User(id = "u-alice", name = "Alice"))
        userRepo.saveUser(User(id = "u-bob", name = "Bob"))
        userRepo.updateBalance("u-alice", 100_00L)
        userRepo.updateBalance("u-bob", 50_00L)

        // Products: Tomato (kg, initially no price), Lettuce (Unit, R$ 4,00)
        veggieRepo.saveVeggieProduct(VeggieProduct(id = "vp-tomato", name = "Tomato", estimatedPriceCents = null, unitTypeId = "unit-kg"))
        veggieRepo.saveVeggieProduct(VeggieProduct(id = "vp-lettuce", name = "Lettuce", estimatedPriceCents = 400L, unitTypeId = "unit-piece"))

        val round = veggieRepo.openNewRound(
            buyerOperationFeeCents = 600L, // R$ 6,00
            transportFeeCents = 400L,      // R$ 4,00 (Combined fee = R$ 10,00 -> R$ 5,00 per customer)
            openedAt = 1_776_672_000_000L  // 2026-04-20 (Week 17 of 2026)
        )

        val alice = userRepo.getUserById("u-alice")!!
        val bob = userRepo.getUserById("u-bob")!!

        // Alice orders 2.0 kg Tomato
        veggieRepo.saveCustomerOrder(round.id, alice, mapOf("vp-tomato" to 2000L))
        // Bob orders 2.0 kg Tomato + 2 Lettuce
        veggieRepo.saveCustomerOrder(round.id, bob, mapOf("vp-tomato" to 2000L, "vp-lettuce" to 2000L))

        veggieRepo.closeRound(round.id, closedAt = 1_776_680_000_000L)

        val entries = veggieRepo.getEntriesForRound(round.id)
        val aliceTomatoEntry = entries.first { it.userId == "u-alice" && it.veggieProductId == "vp-tomato" }
        val bobTomatoEntry = entries.first { it.userId == "u-bob" && it.veggieProductId == "vp-tomato" }
        val bobLettuceEntry = entries.first { it.userId == "u-bob" && it.veggieProductId == "vp-lettuce" }

        // Market reality:
        // Buyer bought 4 kg box of Tomatoes at R$ 10,00/kg = R$ 40,00 (4000 cents) Total Spent.
        // Due to weight loss, Alice received 1.800 kg and Bob received 1.800 kg (Total delivered = 3.600 kg).
        // Proportional cost recovery: Alice pays 2000 cents (R$ 20,00), Bob pays 2000 cents (R$ 20,00).
        // Lettuce: Market unit price R$ 5,00 (500 cents), Bob received 2 units -> Total Spent 1000 cents (R$ 10,00).
        // Combined fee: R$ 10,00 split equally -> Alice pays 500 cents, Bob pays 500 cents.
        // Expected charges:
        // - Alice: 2000 + 500 = 2500 cents (R$ 25,00) -> new balance = 10000 - 2500 = 7500 cents
        // - Bob (order): 2000 + 1000 + 500 = 3500 cents (R$ 35,00)
        // - Bob (transporter credit): +400 cents (R$ 4,00) in a separate transaction
        // -> Bob's new balance = 5000 - 3500 + 400 = 1900 cents
        val settlementResult = veggieRepo.executeRoundSettlement(
            roundId = round.id,
            buyerOperationFeeCents = 600L,
            transportFeeCents = 400L,
            transporterUserId = "u-bob",
            productSettlements = listOf(
                VeggieRoundProductSettlement(
                    roundId = round.id,
                    veggieProductId = "vp-tomato",
                    marketUnitPriceCents = 1000L,
                    totalSpentCents = 4000L
                ),
                VeggieRoundProductSettlement(
                    roundId = round.id,
                    veggieProductId = "vp-lettuce",
                    marketUnitPriceCents = 500L,
                    totalSpentCents = null // Auto-calculated as 500 * 2 = 1000
                )
            ),
            actualQuantitiesByEntryId = mapOf(
                aliceTomatoEntry.id to 1800L,
                bobTomatoEntry.id to 1800L,
                bobLettuceEntry.id to 2000L
            ),
            settledAt = 1_776_690_000_000L
        )
        assertNotNull(settlementResult)

        // Verify balances
        assertEquals(75_00L, userRepo.getUserById("u-alice")!!.balance)
        assertEquals(19_00L, userRepo.getUserById("u-bob")!!.balance)

        // Verify transactions in Lojinha ledger
        val aliceTxs = txRepo.getTransactionsByUserId("u-alice")
        assertEquals(1, aliceTxs.size)
        assertEquals(TransactionType.VEGGIE, aliceTxs[0].type)
        assertEquals(-25_00L, aliceTxs[0].totalAmount)
        assertTrue(aliceTxs[0].note!!.startsWith("SYSNOTE|VEGGIE_ORDER|"))

        val bobTxs = txRepo.getTransactionsByUserId("u-bob")
        assertEquals(2, bobTxs.size)
        val bobOrderTx = bobTxs.first { it.type == TransactionType.VEGGIE }
        val bobTransportTx = bobTxs.first { it.type == TransactionType.ADMIN_DEPOSIT }
        assertEquals(-35_00L, bobOrderTx.totalAmount)
        assertTrue(bobOrderTx.note!!.startsWith("SYSNOTE|VEGGIE_ORDER|"))
        assertEquals(4_00L, bobTransportTx.totalAmount)
        assertTrue(bobTransportTx.note!!.startsWith("SYSNOTE|VEGGIE_TRANSPORT|"))

        // Verify catalog reference prices updated automatically
        assertEquals(1000L, veggieRepo.getVeggieProductById("vp-tomato")!!.estimatedPriceCents)
        assertEquals(500L, veggieRepo.getVeggieProductById("vp-lettuce")!!.estimatedPriceCents)

        // Verify customer order history
        val aliceHistory = veggieRepo.getCustomerOrderHistory("u-alice")
        assertEquals(1, aliceHistory.size)
        assertEquals(20_00L, aliceHistory[0].itemsSubtotalCents)
        assertEquals(5_00L, aliceHistory[0].combinedFeeShareCents)
        assertEquals(25_00L, aliceHistory[0].grandTotalCents)

        // Post-Settlement Correction:
        // Suppose Tomato Total Spent was actually R$ 36,00 (3600 cents) instead of R$ 40,00 (4000 cents).
        // Each buyer's tomato cost drops by R$ 2,00 (200 cents) -> Alice and Bob each receive a +200 cent correction!
        val correctedResult = veggieRepo.executeRoundSettlement(
            roundId = round.id,
            buyerOperationFeeCents = 600L,
            transportFeeCents = 400L,
            transporterUserId = "u-bob",
            productSettlements = listOf(
                VeggieRoundProductSettlement(
                    roundId = round.id,
                    veggieProductId = "vp-tomato",
                    marketUnitPriceCents = 1000L,
                    totalSpentCents = 3600L
                ),
                VeggieRoundProductSettlement(
                    roundId = round.id,
                    veggieProductId = "vp-lettuce",
                    marketUnitPriceCents = 500L,
                    totalSpentCents = 1000L
                )
            ),
            actualQuantitiesByEntryId = mapOf(
                aliceTomatoEntry.id to 1800L,
                bobTomatoEntry.id to 1800L,
                bobLettuceEntry.id to 2000L
            ),
            settledAt = 1_776_700_000_000L
        )
        assertNotNull(correctedResult)

        // Alice balance: 7500 + 200 = 7700; Bob balance: 1900 + 200 = 2100
        assertEquals(77_00L, userRepo.getUserById("u-alice")!!.balance)
        assertEquals(21_00L, userRepo.getUserById("u-bob")!!.balance)

        val aliceTxsAfter = txRepo.getTransactionsByUserId("u-alice")
        assertEquals(2, aliceTxsAfter.size)
        val aliceCorrTx = aliceTxsAfter.first { it.note!!.startsWith("SYSNOTE|VEGGIE_CORRECTION|") }
        assertEquals(2_00L, aliceCorrTx.totalAmount)
        assertEquals(TransactionType.VEGGIE, aliceCorrTx.type)
    }

    @Test
    fun testVeggieSettingsPersistence() = runTest {
        val initial = settingsRepo.getSettings()
        assertFalse(initial.veggieAutoOpenEnabled)
        assertEquals(1, initial.veggieAutoOpenDayOfWeek)
        assertEquals("08:00", initial.veggieAutoOpenTime)
        assertEquals(0L, initial.defaultVeggieBuyerFeeCents)
        assertEquals(0L, initial.defaultVeggieTransportFeeCents)

        val updated = initial.copy(
            veggieAutoOpenEnabled = true,
            veggieAutoOpenDayOfWeek = 2,
            veggieAutoOpenTime = "09:30",
            defaultVeggieBuyerFeeCents = 15_00L,
            defaultVeggieTransportFeeCents = 20_00L
        )
        settingsRepo.updateSettings(updated)

        val loaded = settingsRepo.getSettings()
        assertTrue(loaded.veggieAutoOpenEnabled)
        assertEquals(2, loaded.veggieAutoOpenDayOfWeek)
        assertEquals("09:30", loaded.veggieAutoOpenTime)
        assertEquals(15_00L, loaded.defaultVeggieBuyerFeeCents)
        assertEquals(20_00L, loaded.defaultVeggieTransportFeeCents)
    }

    @Test
    fun testCustomerOrderHistory_calculatesEstimatedPricesForOpenRound() = runTest {
        veggieRepo.saveVeggieProduct(
            VeggieProduct(
                id = "vp-carrot",
                name = "Carrots",
                unitTypeId = "unit-kg",
                estimatedPriceCents = 600L
            )
        )
        userRepo.saveUser(User(id = "u-charlie", name = "Charlie", balance = 50_00L))

        val round = veggieRepo.openNewRound(
            buyerOperationFeeCents = 200L,
            transportFeeCents = 300L,
            openedAt = 1_776_672_000_000L
        )

        val charlie = userRepo.getUserById("u-charlie")!!
        veggieRepo.saveCustomerOrder(round.id, charlie, mapOf("vp-carrot" to 2500L))

        val history = veggieRepo.getCustomerOrderHistory("u-charlie")
        assertEquals(1, history.size)
        val item = history[0]
        assertEquals(round.id, item.round.id)
        assertEquals(VeggieRoundStatus.OPEN, item.round.status)
        assertEquals(1, item.entries.size)

        val entry = item.entries[0]
        assertEquals("vp-carrot", entry.veggieProductId)
        assertEquals(600L, entry.effectiveUnitPriceCents)
        assertEquals(1500L, entry.finalItemTotalCents)
        assertEquals(1500L, item.itemsSubtotalCents)
        assertEquals(500L, item.combinedFeeShareCents)
        assertEquals(2000L, item.grandTotalCents)
    }

    @Test
    fun testEmptyRound_settleArchivedToHistoryAndAllowsNewRound() = runTest {
        val round = veggieRepo.openNewRound(
            buyerOperationFeeCents = 10_00L,
            transportFeeCents = 5_00L,
            openedAt = 1_776_700_000_000L
        )

        // Close round without any customer orders
        assertTrue(veggieRepo.closeRound(round.id, 1_776_701_000_000L))

        // Settle empty round
        val result = veggieRepo.executeRoundSettlement(
            roundId = round.id,
            buyerOperationFeeCents = 0L,
            transportFeeCents = 0L,
            transporterUserId = null,
            productSettlements = emptyList(),
            actualQuantitiesByEntryId = emptyMap(),
            settledAt = 1_776_702_000_000L
        )
        assertNotNull(result)
        assertEquals(0, result.customerSummaries.size)

        val updated = veggieRepo.getRoundById(round.id)!!
        assertEquals(VeggieRoundStatus.SETTLED, updated.status)

        // Verify zero transactions were created
        val allTxs = txRepo.getAllTransactions()
        assertEquals(0, allTxs.size)

        // Verify a new round can now be opened without conflict
        val newRound = veggieRepo.openNewRound(
            buyerOperationFeeCents = 10_00L,
            transportFeeCents = 5_00L,
            openedAt = 1_776_800_000_000L
        )
        assertEquals(VeggieRoundStatus.OPEN, newRound.status)
    }

    @Test
    fun testEmptyRound_deletePermanently() = runTest {
        val round = veggieRepo.openNewRound(
            buyerOperationFeeCents = 10_00L,
            transportFeeCents = 5_00L,
            openedAt = 1_776_700_000_000L
        )

        assertTrue(veggieRepo.deleteRound(round.id))
        assertNull(veggieRepo.getRoundById(round.id))
    }

    @Test
    fun testPostSettlement_addUserAfterwardsUpdatesOperationCostForEveryone() = runTest {
        // Setup 4 users
        userRepo.saveUser(User(id = "u-1", name = "User 1"))
        userRepo.saveUser(User(id = "u-2", name = "User 2"))
        userRepo.saveUser(User(id = "u-3", name = "User 3"))
        userRepo.saveUser(User(id = "u-4", name = "User 4"))
        userRepo.updateBalance("u-1", 100_00L)
        userRepo.updateBalance("u-2", 100_00L)
        userRepo.updateBalance("u-3", 100_00L)
        userRepo.updateBalance("u-4", 100_00L)

        // Product
        veggieRepo.saveVeggieProduct(VeggieProduct(id = "vp-item", name = "Item", estimatedPriceCents = 10_00L, unitTypeId = "unit-piece"))

        // Round with R$ 30,00 fee (3000 cents)
        val round = veggieRepo.openNewRound(
            buyerOperationFeeCents = 30_00L,
            transportFeeCents = 0L,
            openedAt = 1_776_700_000_000L
        )

        // Users 1, 2, 3 order 1 item each
        val u1 = userRepo.getUserById("u-1")!!
        val u2 = userRepo.getUserById("u-2")!!
        val u3 = userRepo.getUserById("u-3")!!
        val u4 = userRepo.getUserById("u-4")!!

        veggieRepo.saveCustomerOrder(round.id, u1, mapOf("vp-item" to 1000L))
        veggieRepo.saveCustomerOrder(round.id, u2, mapOf("vp-item" to 1000L))
        veggieRepo.saveCustomerOrder(round.id, u3, mapOf("vp-item" to 1000L))

        veggieRepo.closeRound(round.id, 1_776_701_000_000L)

        // Initial settlement with 3 users:
        // Market price: R$ 10,00 per item -> item cost = 1000 cents each.
        // Fee share: R$ 30,00 / 3 = R$ 10,00 each.
        // Grand total per user: R$ 20,00 (2000 cents).
        val initialEntries = veggieRepo.getEntriesForRound(round.id)
        val initialSettlement = veggieRepo.executeRoundSettlement(
            roundId = round.id,
            buyerOperationFeeCents = 30_00L,
            transportFeeCents = 0L,
            transporterUserId = null,
            productSettlements = listOf(
                VeggieRoundProductSettlement(
                    roundId = round.id,
                    veggieProductId = "vp-item",
                    marketUnitPriceCents = 10_00L,
                    totalSpentCents = 30_00L
                )
            ),
            actualQuantitiesByEntryId = initialEntries.associate { it.id to 1000L },
            settledAt = 1_776_702_000_000L
        )
        assertNotNull(initialSettlement)

        // Check balances after initial settlement: 10000 - 2000 = 8000 cents (R$ 80,00)
        assertEquals(80_00L, userRepo.getUserById("u-1")!!.balance)
        assertEquals(80_00L, userRepo.getUserById("u-2")!!.balance)
        assertEquals(80_00L, userRepo.getUserById("u-3")!!.balance)
        assertEquals(100_00L, userRepo.getUserById("u-4")!!.balance)

        // Now, User 4 is added afterwards to this billed round!
        val itemProd = veggieRepo.getVeggieProductById("vp-item")!!
        veggieRepo.upsertAdminOrderEntry(
            VeggieOrderEntry(
                id = "entry-u4",
                roundId = round.id,
                userId = u4.id,
                userNameSnapshot = u4.name,
                veggieProductId = itemProd.id,
                veggieNameSnapshot = itemProd.name,
                unitNameSnapshot = "Unit",
                allowDecimalsSnapshot = false,
                orderedQuantityScaled = 1000L,
                actualQuantityScaled = 1000L
            )
        )

        // Execute settlement update (4 users now! Fee share drops to R$ 30,00 / 4 = R$ 7,50 = 750 cents):
        // New grand total for u-1, u-2, u-3: 1000 (item) + 750 (fee) = 1750 cents (R$ 17,50).
        // Each previously paid 2000 cents -> Each gets a +250 cent (R$ 2,50) refund!
        // New grand total for u-4: 1000 (item) + 750 (fee) = 1750 cents (R$ 17,50).
        // u-4 previously paid 0 cents -> Charged -1750 cents (R$ 17,50).
        val allEntriesNow = veggieRepo.getEntriesForRound(round.id)
        val updateSettlement = veggieRepo.executeRoundSettlement(
            roundId = round.id,
            buyerOperationFeeCents = 30_00L,
            transportFeeCents = 0L,
            transporterUserId = null,
            productSettlements = listOf(
                VeggieRoundProductSettlement(
                    roundId = round.id,
                    veggieProductId = "vp-item",
                    marketUnitPriceCents = 10_00L,
                    totalSpentCents = 40_00L
                )
            ),
            actualQuantitiesByEntryId = allEntriesNow.associate { it.id to 1000L },
            settledAt = 1_776_703_000_000L
        )
        assertNotNull(updateSettlement)

        // Verify balances after post-settlement addition:
        // u-1, u-2, u-3: 8000 + 250 = 8250 cents (R$ 82,50)
        // u-4: 10000 - 1750 = 8250 cents (R$ 82,50)
        assertEquals(82_50L, userRepo.getUserById("u-1")!!.balance)
        assertEquals(82_50L, userRepo.getUserById("u-2")!!.balance)
        assertEquals(82_50L, userRepo.getUserById("u-3")!!.balance)
        assertEquals(82_50L, userRepo.getUserById("u-4")!!.balance)

        // Verify delta transactions for u-1, u-2, u-3 have type VEGGIE and amount +250 cents
        val u1Txs = txRepo.getTransactionsByUserId("u-1")
        assertEquals(2, u1Txs.size)
        val u1Refund = u1Txs.find { it.note!!.startsWith("SYSNOTE|VEGGIE_CORRECTION|") }!!
        assertEquals(2_50L, u1Refund.totalAmount)
        assertEquals(TransactionType.VEGGIE, u1Refund.type)

        // Verify charge transaction for u-4 has type VEGGIE and amount -1750 cents
        val u4Txs = txRepo.getTransactionsByUserId("u-4")
        assertEquals(1, u4Txs.size)
        val u4Charge = u4Txs[0]
        assertEquals(-17_50L, u4Charge.totalAmount)
        assertEquals(TransactionType.VEGGIE, u4Charge.type)
    }

    @Test
    fun testPdfExportMarking() = runTest {
        val round = veggieRepo.openNewRound(
            buyerOperationFeeCents = 5_00L,
            transportFeeCents = 5_00L,
            openedAt = 1_776_700_000_000L
        )
        assertNull(round.buyerPdfExportedAt)
        assertNull(round.workerPdfExportedAt)

        val buyerExportTime = 1_776_710_000_000L
        val workerExportTime = 1_776_720_000_000L

        assertTrue(veggieRepo.markBuyerPdfExported(round.id, buyerExportTime))
        val afterBuyer = veggieRepo.getRoundById(round.id)!!
        assertEquals(buyerExportTime, afterBuyer.buyerPdfExportedAt)
        assertNull(afterBuyer.workerPdfExportedAt)

        assertTrue(veggieRepo.markWorkerPdfExported(round.id, workerExportTime))
        val afterWorker = veggieRepo.getRoundById(round.id)!!
        assertEquals(buyerExportTime, afterWorker.buyerPdfExportedAt)
        assertEquals(workerExportTime, afterWorker.workerPdfExportedAt)
    }

    @Test
    fun testSaveCustomerOrder_deactivatedProduct_clampsIncreaseAndAllowsReduceOrRemove() = runTest {
        val unitKg = VeggieUnitType(id = "u-kg", name = "kg", allowDecimals = true, isActive = true)
        veggieRepo.saveUnitType(unitKg)
        val prodA = VeggieProduct(id = "vp-a", name = "Batata", unitTypeId = "u-kg", isActive = true)
        val prodB = VeggieProduct(id = "vp-b", name = "Cenoura", unitTypeId = "u-kg", isActive = true)
        veggieRepo.saveVeggieProduct(prodA)
        veggieRepo.saveVeggieProduct(prodB)

        val user = User(id = "u-1", name = "Alice", balance = 100_00L)
        userRepo.saveUser(user)

        val round = veggieRepo.openNewRound(0L, 0L, 1_776_700_000_000L)
        assertTrue(veggieRepo.saveCustomerOrder(round.id, user, mapOf("vp-a" to 2000L)))

        // Admin deactivates prodA
        veggieRepo.deactivateVeggieProduct("vp-a")

        // Customer tries to increase prodA to 5000L and also add prodB (1000L)
        assertTrue(veggieRepo.saveCustomerOrder(round.id, user, mapOf("vp-a" to 5000L, "vp-b" to 1000L)))
        val entriesAfterIncreaseAttempt = veggieRepo.getEntriesForUserInRound(round.id, user.id)
            .associateBy { it.veggieProductId }
        assertEquals(2000L, entriesAfterIncreaseAttempt["vp-a"]?.orderedQuantityScaled)
        assertEquals(1000L, entriesAfterIncreaseAttempt["vp-b"]?.orderedQuantityScaled)

        // Customer reduces prodA to 1500L -> allowed
        assertTrue(veggieRepo.saveCustomerOrder(round.id, user, mapOf("vp-a" to 1500L, "vp-b" to 1000L)))
        val entriesAfterReduce = veggieRepo.getEntriesForUserInRound(round.id, user.id)
            .associateBy { it.veggieProductId }
        assertEquals(1500L, entriesAfterReduce["vp-a"]?.orderedQuantityScaled)

        // Customer removes prodA -> allowed
        assertTrue(veggieRepo.saveCustomerOrder(round.id, user, mapOf("vp-b" to 1000L)))
        val entriesAfterRemove = veggieRepo.getEntriesForUserInRound(round.id, user.id)
            .associateBy { it.veggieProductId }
        assertNull(entriesAfterRemove["vp-a"])
        assertEquals(1000L, entriesAfterRemove["vp-b"]?.orderedQuantityScaled)
    }

    @Test
    fun testCancelProductEntriesInOpenRound_removesProductEntriesOnlyInOpenRound() = runTest {
        val unitKg = VeggieUnitType(id = "u-kg", name = "kg", allowDecimals = true, isActive = true)
        veggieRepo.saveUnitType(unitKg)
        val prodA = VeggieProduct(id = "vp-a", name = "Batata", unitTypeId = "u-kg", isActive = true)
        val prodB = VeggieProduct(id = "vp-b", name = "Cenoura", unitTypeId = "u-kg", isActive = true)
        veggieRepo.saveVeggieProduct(prodA)
        veggieRepo.saveVeggieProduct(prodB)

        val user = User(id = "u-1", name = "Alice", balance = 100_00L)
        userRepo.saveUser(user)

        val round = veggieRepo.openNewRound(0L, 0L, 1_776_700_000_000L)
        assertTrue(veggieRepo.saveCustomerOrder(round.id, user, mapOf("vp-a" to 2000L, "vp-b" to 1000L)))

        assertTrue(veggieRepo.cancelProductEntriesInOpenRound(round.id, "vp-a"))
        val remaining = veggieRepo.getEntriesForRound(round.id)
        assertEquals(1, remaining.size)
        assertEquals("vp-b", remaining[0].veggieProductId)

        // Cannot cancel entries once round is closed
        assertTrue(veggieRepo.closeRound(round.id, 1_776_705_000_000L))
        assertFalse(veggieRepo.cancelProductEntriesInOpenRound(round.id, "vp-b"))
        assertEquals(1, veggieRepo.getEntriesForRound(round.id).size)
    }

    @Test
    fun testCustomerVeggieFavorites_persistAndCleanupOnHardDelete() = runTest {
        val unitKg = VeggieUnitType(id = "u-kg", name = "kg", allowDecimals = true, isActive = true)
        veggieRepo.saveUnitType(unitKg)
        veggieRepo.saveVeggieProduct(VeggieProduct(id = "vp-a", name = "Tomate", unitTypeId = "u-kg", isActive = true))
        veggieRepo.saveVeggieProduct(VeggieProduct(id = "vp-b", name = "Alface", unitTypeId = "u-kg", isActive = true))

        assertEquals(emptySet<String>(), veggieRepo.getFavoriteProductIdsForUser("u-alice"))

        veggieRepo.setFavoriteProductIdsForUser("u-alice", setOf("vp-a", "vp-b"))
        veggieRepo.setFavoriteProductIdsForUser("u-bob", setOf("vp-b"))

        assertEquals(setOf("vp-a", "vp-b"), veggieRepo.getFavoriteProductIdsForUser("u-alice"))
        assertEquals(setOf("vp-b"), veggieRepo.getFavoriteProductIdsForUser("u-bob"))

        // Replacing favorites updates only that user
        veggieRepo.setFavoriteProductIdsForUser("u-alice", setOf("vp-a"))
        assertEquals(setOf("vp-a"), veggieRepo.getFavoriteProductIdsForUser("u-alice"))
        assertEquals(setOf("vp-b"), veggieRepo.getFavoriteProductIdsForUser("u-bob"))

        // Hard deleting a product removes its favorite references
        veggieRepo.hardDeleteVeggieProduct("vp-a")
        assertEquals(emptySet<String>(), veggieRepo.getFavoriteProductIdsForUser("u-alice"))
        assertEquals(setOf("vp-b"), veggieRepo.getFavoriteProductIdsForUser("u-bob"))
    }

    @Test
    fun testPostSettlementCorrection_transporterReductionUsesAdminWithdrawalAndBlocksStorno() = runTest {
        userRepo.saveUser(User(id = "u-alice", name = "Alice", balance = 100_00L))
        userRepo.saveUser(User(id = "u-driver", name = "Driver", balance = 10_00L))
        veggieRepo.saveUnitType(VeggieUnitType(id = "unit-kg", name = "kg", allowDecimals = true, isActive = true))
        veggieRepo.saveVeggieProduct(VeggieProduct(id = "vp-tom", name = "Tomato", unitTypeId = "unit-kg", isActive = true))

        val round = veggieRepo.openNewRound(0L, 20_00L, 1_776_700_000_000L)
        val alice = userRepo.getUserById("u-alice")!!
        assertTrue(veggieRepo.saveCustomerOrder(round.id, alice, mapOf("vp-tom" to 1000L)))
        assertTrue(veggieRepo.closeRound(round.id, 1_776_701_000_000L))

        val entries = veggieRepo.getEntriesForRound(round.id)
        val settlements = listOf(VeggieRoundProductSettlement(round.id, "vp-tom", 10_00L, 10_00L))

        // Initial settlement: Driver gets +20.00 ADMIN_DEPOSIT
        assertNotNull(
            veggieRepo.executeRoundSettlement(
                roundId = round.id,
                buyerOperationFeeCents = 0L,
                transportFeeCents = 20_00L,
                transporterUserId = "u-driver",
                productSettlements = settlements,
                actualQuantitiesByEntryId = mapOf(entries[0].id to 1000L),
                settledAt = 1_776_702_000_000L
            )
        )
        assertEquals(20_00L, userRepo.getUserById("u-driver")!!.balance)

        val initialDriverTx = txRepo.getAllTransactions().first { it.userId == "u-driver" }
        assertEquals(TransactionType.ADMIN_DEPOSIT, initialDriverTx.type)
        // Stornoing a VEGGIE_TRANSPORT deposit from Transactions tab must be blocked
        assertFalse(txRepo.stornoNonPurchase(initialDriverTx.id))

        // Correction: reduce transport fee from 20.00 to 5.00 -> Driver should get -15.00 ADMIN_WITHDRAWAL
        assertNotNull(
            veggieRepo.executeRoundSettlement(
                roundId = round.id,
                buyerOperationFeeCents = 0L,
                transportFeeCents = 5_00L,
                transporterUserId = "u-driver",
                productSettlements = settlements,
                actualQuantitiesByEntryId = mapOf(entries[0].id to 1000L),
                settledAt = 1_776_703_000_000L
            )
        )
        assertEquals(5_00L, userRepo.getUserById("u-driver")!!.balance)
        val driverTxs = txRepo.getAllTransactions().filter { it.userId == "u-driver" }.sortedBy { it.timestamp }
        assertEquals(2, driverTxs.size)
        assertEquals(TransactionType.ADMIN_WITHDRAWAL, driverTxs[1].type)
        assertEquals(-15_00L, driverTxs[1].totalAmount)
    }

    @Test
    fun testCustomerOrderUpdateInOpenRound_resetsStaleActualQuantityScaled() = runTest {
        userRepo.saveUser(User(id = "u-alice", name = "Alice", balance = 100_00L))
        veggieRepo.saveUnitType(VeggieUnitType(id = "unit-kg", name = "kg", allowDecimals = true, isActive = true))
        veggieRepo.saveVeggieProduct(
            VeggieProduct(id = "vp-tom", name = "Tomato", unitTypeId = "unit-kg", estimatedPriceCents = 10_00L, imageFileName = "tomate", isActive = true)
        )

        val round = veggieRepo.openNewRound(0L, 0L, 1_776_700_000_000L)
        val alice = userRepo.getUserById("u-alice")!!

        // Alice orders 1 kg, and admin saves draft settlement (setting actualQuantityScaled = 1000L)
        assertTrue(veggieRepo.saveCustomerOrder(round.id, alice, mapOf("vp-tom" to 1000L)))
        val initialEntry = veggieRepo.getEntriesForRound(round.id).single()
        assertTrue(
            veggieRepo.saveDraftSettlement(
                roundId = round.id,
                buyerOperationFeeCents = 0L,
                transportFeeCents = 0L,
                transporterUserId = null,
                productSettlements = emptyList(),
                actualQuantitiesByEntryId = mapOf(initialEntry.id to 1000L)
            )
        )
        assertEquals(1000L, veggieRepo.getEntriesForRound(round.id).single().actualQuantityScaled)

        // Alice updates her order in the still-open round to 3.5 kg -> actualQuantityScaled must reset to null so deliveredQuantityScaled is 3500L
        assertTrue(veggieRepo.saveCustomerOrder(round.id, alice, mapOf("vp-tom" to 3500L)))
        val updatedEntry = veggieRepo.getEntriesForRound(round.id).single()
        assertEquals(3500L, updatedEntry.orderedQuantityScaled)
        assertNull(updatedEntry.actualQuantityScaled)
        assertEquals(3500L, updatedEntry.deliveredQuantityScaled)

        // Soft-deleting an ordered product clears its imageFileName
        veggieRepo.hardDeleteVeggieProduct("vp-tom")
        val softDeleted = db.veggieOrderDao().getVeggieProductById("vp-tom")
        assertNotNull(softDeleted)
        assertTrue(softDeleted.isDeleted)
        assertNull(softDeleted.imageFileName)
    }
}
