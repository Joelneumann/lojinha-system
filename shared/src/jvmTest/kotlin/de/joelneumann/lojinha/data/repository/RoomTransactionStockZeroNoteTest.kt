package de.joelneumann.lojinha.data.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.joelneumann.lojinha.data.database.AppDatabase
import de.joelneumann.lojinha.data.database.AppDatabase_Impl
import de.joelneumann.lojinha.data.entity.ProductEntity
import de.joelneumann.lojinha.data.entity.UserEntity
import de.joelneumann.lojinha.domain.model.*
import de.joelneumann.lojinha.ui.i18n.EnglishStrings
import de.joelneumann.lojinha.ui.i18n.GermanStrings
import de.joelneumann.lojinha.ui.i18n.PortugueseStrings
import de.joelneumann.lojinha.ui.utils.SystemNoteHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class RoomTransactionStockZeroNoteTest {

    private lateinit var tempDir: File
    private lateinit var dbFile: File
    private lateinit var db: AppDatabase
    private var dataChangedCallCount = 0
    private lateinit var txRepository: RoomTransactionRepositoryImpl
    private lateinit var noteRepository: RoomAdminNoteRepositoryImpl

    @BeforeTest
    fun setup() {
        tempDir = Files.createTempDirectory("stock_zero_test").toFile()
        dbFile = File(tempDir, "test_stock_zero.db")

        val builder = Room.databaseBuilder<AppDatabase>(
            name = dbFile.absolutePath,
            factory = { AppDatabase_Impl() }
        )
        builder.setDriver(BundledSQLiteDriver())
        builder.setQueryCoroutineContext(Dispatchers.IO)
        db = builder.build()

        dataChangedCallCount = 0
        txRepository = RoomTransactionRepositoryImpl(
            transactionDao = db.transactionDao(),
            onDataChanged = { dataChangedCallCount++ }
        )
        noteRepository = RoomAdminNoteRepositoryImpl(
            adminNoteDao = db.adminNoteDao()
        )
    }

    @AfterTest
    fun tearDown() {
        db.close()
        tempDir.deleteRecursively()
    }

    private suspend fun insertUser(id: String, name: String, balance: Long = 100_00L) {
        db.userDao().insertOrUpdateUser(
            UserEntity.fromDomain(
                User(
                    id = id,
                    name = name,
                    balance = balance,
                    isActive = true
                )
            )
        )
    }

    private suspend fun insertProduct(id: String, name: String, stockQuantity: Long) {
        db.productDao().insertOrUpdateProduct(
            ProductEntity.fromDomain(
                Product(
                    id = id,
                    name = name,
                    basePrice = 250L,
                    unitType = UnitType.PIECE,
                    stockQuantity = stockQuantity,
                    isActive = true
                )
            )
        )
    }

    @Test
    fun testPurchase_depletingStockToZero_createsSystemNote() = runTest {
        insertUser("u1", "Alice")
        insertProduct("p1", "Club Mate", stockQuantity = 2L)

        val tx = Transaction(
            id = "tx1",
            userId = "u1",
            userNameSnapshot = "Alice",
            timestamp = 1710000000000L,
            type = TransactionType.PURCHASE,
            totalAmount = -500L,
            items = listOf(
                TransactionItem(
                    productId = "p1",
                    productName = "Club Mate",
                    unitType = UnitType.PIECE,
                    quantity = 2L,
                    unitPriceAtPurchase = 250L
                )
            ),
            userBalanceBefore = 100_00L,
            userBalanceAfter = 95_00L
        )

        txRepository.executeAtomicTransaction(
            transaction = tx,
            balanceDelta = -500L,
            stockDeltas = mapOf("p1" to -2L)
        )

        // Verify product stock is 0
        val finalStock = db.transactionDao().getProductStock("p1")
        assertEquals(0L, finalStock)

        // Verify system note was created
        val notes = noteRepository.getAllNotes()
        assertEquals(1, notes.size, "Should have created exactly one system note")

        val note = notes.first()
        assertTrue(note.isSystem, "Note must be identified as system note")
        assertEquals(AdminNote.SYSTEM_USER_ID, note.userId)
        assertEquals(AdminNote.SYSTEM_USER_NAME, note.userNameSnapshot)
        assertEquals(1710000000000L, note.timestamp)
        assertEquals("SYSNOTE|STOCK_ZERO|Club Mate|Alice", note.message)

        // Verify translations in all languages
        assertEquals("Alice bought the last item of Club Mate", SystemNoteHelper.decodeNote(note.message, EnglishStrings))
        assertEquals("Alice hat den letzten Artikel von Club Mate gekauft", SystemNoteHelper.decodeNote(note.message, GermanStrings))
        assertEquals("Alice comprou o último item de Club Mate", SystemNoteHelper.decodeNote(note.message, PortugueseStrings))
    }

    @Test
    fun testPurchase_partialStockReduction_doesNotCreateNote() = runTest {
        insertUser("u1", "Bob")
        insertProduct("p1", "Club Mate", stockQuantity = 5L)

        val tx = Transaction(
            id = "tx2",
            userId = "u1",
            userNameSnapshot = "Bob",
            timestamp = 1710000000000L,
            type = TransactionType.PURCHASE,
            totalAmount = -500L,
            items = listOf(
                TransactionItem(
                    productId = "p1",
                    productName = "Club Mate",
                    unitType = UnitType.PIECE,
                    quantity = 2L,
                    unitPriceAtPurchase = 250L
                )
            ),
            userBalanceBefore = 100_00L,
            userBalanceAfter = 95_00L
        )

        txRepository.executeAtomicTransaction(
            transaction = tx,
            balanceDelta = -500L,
            stockDeltas = mapOf("p1" to -2L)
        )

        val finalStock = db.transactionDao().getProductStock("p1")
        assertEquals(3L, finalStock)

        val notes = noteRepository.getAllNotes()
        assertTrue(notes.isEmpty(), "No system note should be created when stock is still > 0")
    }

    @Test
    fun testPurchase_multiProduct_onlyDepletedProductsCreateNotes() = runTest {
        insertUser("u1", "Charlie")
        insertProduct("p1", "Mate", stockQuantity = 1L)
        insertProduct("p2", "Cookie", stockQuantity = 5L)
        insertProduct("p3", "Apple", stockQuantity = 3L)

        val tx = Transaction(
            id = "tx3",
            userId = "u1",
            userNameSnapshot = "Charlie",
            timestamp = 1710000001000L,
            type = TransactionType.PURCHASE,
            totalAmount = -1000L,
            items = emptyList(),
            userBalanceBefore = 100_00L,
            userBalanceAfter = 90_00L
        )

        // Deplete p1 (1 -> 0) and p3 (3 -> 0), but not p2 (5 -> 3)
        txRepository.executeAtomicTransaction(
            transaction = tx,
            balanceDelta = -1000L,
            stockDeltas = mapOf(
                "p1" to -1L,
                "p2" to -2L,
                "p3" to -3L
            )
        )

        val notes = noteRepository.getAllNotes()
        assertEquals(2, notes.size, "Should have created exactly 2 notes for the 2 depleted products")

        val messages = notes.map { it.message }.toSet()
        assertTrue(messages.contains("SYSNOTE|STOCK_ZERO|Mate|Charlie"))
        assertTrue(messages.contains("SYSNOTE|STOCK_ZERO|Apple|Charlie"))
        assertFalse(messages.any { it.contains("Cookie") })
    }

    @Test
    fun testNonPurchase_doesNotCreateSystemNote() = runTest {
        insertUser("u1", "David")
        insertProduct("p1", "Chocolate", stockQuantity = 2L)

        val tx = Transaction(
            id = "tx4",
            userId = "u1",
            userNameSnapshot = "David",
            timestamp = 1710000002000L,
            type = TransactionType.ADMIN_WITHDRAWAL,
            totalAmount = -500L,
            items = emptyList(),
            userBalanceBefore = 100_00L,
            userBalanceAfter = 95_00L
        )

        txRepository.executeAtomicTransaction(
            transaction = tx,
            balanceDelta = -500L,
            stockDeltas = mapOf("p1" to -2L)
        )

        assertEquals(0L, db.transactionDao().getProductStock("p1"))
        val notes = noteRepository.getAllNotes()
        assertTrue(notes.isEmpty(), "Non-purchase transactions must not trigger stock-zero notes")
    }

    @Test
    fun testPipesInNames_sanitizedCorrectly() = runTest {
        insertUser("u1", "User | Special")
        insertProduct("p1", "Product | Special", stockQuantity = 1L)

        val tx = Transaction(
            id = "tx5",
            userId = "u1",
            userNameSnapshot = "User | Special",
            timestamp = 1710000003000L,
            type = TransactionType.PURCHASE,
            totalAmount = -250L,
            items = emptyList(),
            userBalanceBefore = 100_00L,
            userBalanceAfter = 97_50L
        )

        txRepository.executeAtomicTransaction(
            transaction = tx,
            balanceDelta = -250L,
            stockDeltas = mapOf("p1" to -1L)
        )

        val notes = noteRepository.getAllNotes()
        assertEquals(1, notes.size)

        val note = notes.first()
        assertEquals("SYSNOTE|STOCK_ZERO|Product / Special|User / Special", note.message)

        val decoded = SystemNoteHelper.decodeNote(note.message, EnglishStrings)
        assertEquals("User / Special bought the last item of Product / Special", decoded)
    }

    @Test
    fun testBlankNamesAndNewlines_handledGracefully() = runTest {
        insertUser("u-99", "Fallback User")
        insertProduct("p-newline", "Cold\nDrink\r\nSpecial", stockQuantity = 1L)

        val tx = Transaction(
            id = "tx6",
            userId = "u-99",
            userNameSnapshot = "   ", // Blank name should fallback to userId
            timestamp = 1710000004000L,
            type = TransactionType.PURCHASE,
            totalAmount = -250L,
            items = emptyList(),
            userBalanceBefore = 100_00L,
            userBalanceAfter = 97_50L
        )

        txRepository.executeAtomicTransaction(
            transaction = tx,
            balanceDelta = -250L,
            stockDeltas = mapOf("p-newline" to -1L)
        )

        val notes = noteRepository.getAllNotes()
        assertEquals(1, notes.size)

        val note = notes.first()
        assertEquals("SYSNOTE|STOCK_ZERO|Cold Drink Special|u-99", note.message)

        val decoded = SystemNoteHelper.decodeNote(note.message, EnglishStrings)
        assertEquals("u-99 bought the last item of Cold Drink Special", decoded)
    }

    @Test
    fun testUserNoteWithSysnotePrefix_notTreatedAsSystemNote() = runTest {
        val spoofAttempt = AdminNote(
            id = "note-spoof",
            message = "SYSNOTE|STOCK_ZERO|Free Soda|Eve",
            userId = "u-hacker",
            userNameSnapshot = "Eve Hacker",
            timestamp = 1710000005000L
        )
        assertFalse(spoofAttempt.isSystem, "User note must not be flagged as system note even if prefixed with SYSNOTE|")
    }
}
