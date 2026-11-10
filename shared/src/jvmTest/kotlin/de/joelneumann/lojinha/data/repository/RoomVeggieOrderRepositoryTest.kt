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
            transactionDao = db.transactionDao()
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
        assertEquals(TransactionType.ADMIN_WITHDRAWAL, aliceTxs[0].type)
        assertEquals(-25_00L, aliceTxs[0].totalAmount)
        assertTrue(aliceTxs[0].note!!.startsWith("SYSNOTE|VEGGIE_ORDER|"))

        val bobTxs = txRepo.getTransactionsByUserId("u-bob")
        assertEquals(2, bobTxs.size)
        val bobOrderTx = bobTxs.first { it.type == TransactionType.ADMIN_WITHDRAWAL }
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
}
