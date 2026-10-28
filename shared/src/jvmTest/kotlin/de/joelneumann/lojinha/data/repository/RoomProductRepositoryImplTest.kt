package de.joelneumann.lojinha.data.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.joelneumann.lojinha.data.database.AppDatabase
import de.joelneumann.lojinha.data.database.AppDatabase_Impl
import de.joelneumann.lojinha.data.entity.ProductEntity
import de.joelneumann.lojinha.data.entity.TransactionEntity
import de.joelneumann.lojinha.data.entity.UserEntity
import de.joelneumann.lojinha.domain.model.Product
import de.joelneumann.lojinha.domain.model.ProductHistoryType
import de.joelneumann.lojinha.domain.model.TransactionItem
import de.joelneumann.lojinha.domain.model.TransactionType
import de.joelneumann.lojinha.domain.model.UnitType
import de.joelneumann.lojinha.domain.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class RoomProductRepositoryImplTest {

    private lateinit var tempDir: File
    private lateinit var dbFile: File
    private lateinit var db: AppDatabase
    private var dataChangedCallCount = 0
    private lateinit var repository: RoomProductRepositoryImpl

    @BeforeTest
    fun setup() {
        tempDir = Files.createTempDirectory("product_repo_test").toFile()
        dbFile = File(tempDir, "test_products.db")

        val builder = Room.databaseBuilder<AppDatabase>(
            name = dbFile.absolutePath,
            factory = { AppDatabase_Impl() }
        )
        builder.setDriver(BundledSQLiteDriver())
        builder.setQueryCoroutineContext(Dispatchers.IO)
        db = builder.build()

        dataChangedCallCount = 0
        repository = RoomProductRepositoryImpl(
            productDao = db.productDao(),
            transactionDao = db.transactionDao(),
            productStockAdjustmentDao = db.productStockAdjustmentDao(),
            onDataChanged = { dataChangedCallCount++ }
        )
    }

    @AfterTest
    fun tearDown() {
        db.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun testGetProductHistory_sumsMultiLineItemsAndAvoidsDuplicateKeys() = runTest {
        val product = Product(
            id = "p-1",
            name = "Mate",
            barcodes = emptyList(),
            basePrice = 250,
            unitType = UnitType.PIECE,
            stockQuantity = 10
        )
        repository.saveProduct(product)

        // Insert a user for the transaction
        db.userDao().insertOrUpdateUser(
            UserEntity.fromDomain(User(id = "u-1", name = "Alice", balance = 1000, isActive = true))
        )

        // Insert a transaction with 2 separate line items for the same product
        db.transactionDao().insertTransaction(
            TransactionEntity(
                id = "tx-1",
                userId = "u-1",
                userNameSnapshot = "Alice",
                timestamp = 2000L,
                type = TransactionType.PURCHASE.name,
                referenceTransactionId = null,
                note = null,
                totalAmount = 750L,
                items = listOf(
                    TransactionItem(productId = "p-1", productName = "Mate", quantity = 2, unitPriceAtPurchase = 250, unitType = UnitType.PIECE),
                    TransactionItem(productId = "p-1", productName = "Mate", quantity = 1, unitPriceAtPurchase = 250, unitType = UnitType.PIECE)
                )
            )
        )

        val history = repository.getProductHistory("p-1", page = 0, pageSize = 25)

        // We expect initial stock adjustment (+10) and one purchase entry (2 + 1 = 3)
        assertEquals(2, history.items.size)
        val purchaseEntry = history.items.first { it.type == ProductHistoryType.PURCHASE }
        val initialEntry = history.items.first { it.type == ProductHistoryType.STOCK_ADJUSTMENT }
        assertEquals("tx-1_p-1", purchaseEntry.id)
        assertEquals(3L, purchaseEntry.quantity)
        assertFalse(purchaseEntry.isCancelled)
        assertEquals(10L, initialEntry.stockAfter)
        assertEquals(7L, purchaseEntry.stockAfter)

        // Verify keys are unique
        val ids = history.items.map { it.id }
        assertEquals(ids.distinct().size, ids.size)
    }

    @Test
    fun testGetProductHistory_includesCorrectionTransactions() = runTest {
        val product = Product(
            id = "p-2",
            name = "Coffee",
            barcodes = emptyList(),
            basePrice = 300,
            unitType = UnitType.PIECE,
            stockQuantity = 20
        )
        repository.saveProduct(product)

        db.userDao().insertOrUpdateUser(
            UserEntity.fromDomain(User(id = "u-2", name = "Bob", balance = 5000, isActive = true))
        )

        // Original purchase: 5 items
        db.transactionDao().insertTransaction(
            TransactionEntity(
                id = "tx-orig",
                userId = "u-2",
                userNameSnapshot = "Bob",
                timestamp = 1000L,
                type = TransactionType.PURCHASE.name,
                referenceTransactionId = null,
                note = null,
                totalAmount = 1500L,
                items = listOf(
                    TransactionItem(productId = "p-2", productName = "Coffee", quantity = 5, unitPriceAtPurchase = 300, unitType = UnitType.PIECE)
                )
            )
        )

        // Correction: changed from 5 to 2 (delta: 3 returned to stock)
        db.transactionDao().insertTransaction(
            TransactionEntity(
                id = "tx-corr",
                userId = "u-2",
                userNameSnapshot = "Bob",
                timestamp = 2000L,
                type = TransactionType.CORRECTION.name,
                referenceTransactionId = "tx-orig",
                note = "Correction",
                totalAmount = -900L,
                items = listOf(
                    TransactionItem(productId = "p-2", productName = "Coffee", quantity = 2, unitPriceAtPurchase = 300, unitType = UnitType.PIECE, previousQuantity = 5)
                )
            )
        )

        val history = repository.getProductHistory("p-2", page = 0, pageSize = 25)
        val correctionEntry = history.items.firstOrNull { it.id == "tx-corr_p-2" }
        val purchaseEntry = history.items.first { it.id == "tx-orig_p-2" }

        assertNotNull(correctionEntry)
        assertEquals(ProductHistoryType.STOCK_ADJUSTMENT, correctionEntry.type)
        assertEquals(3L, correctionEntry.quantity) // +3 units restored to stock
        assertEquals(15L, purchaseEntry.stockAfter) // 20 - 5 = 15
        assertEquals(18L, correctionEntry.stockAfter) // 15 + 3 = 18
    }

    @Test
    fun testHardDeleteProduct_cleansUpStockAdjustments() = runTest {
        val product = Product(
            id = "p-del",
            name = "To Delete",
            barcodes = emptyList(),
            basePrice = 100,
            unitType = UnitType.PIECE,
            stockQuantity = 5
        )
        repository.saveProduct(product)

        val adjustmentsBefore = db.productStockAdjustmentDao().getAdjustmentsByProductId("p-del")
        assertEquals(1, adjustmentsBefore.size)

        repository.hardDeleteProduct("p-del")

        val adjustmentsAfter = db.productStockAdjustmentDao().getAdjustmentsByProductId("p-del")
        assertTrue(adjustmentsAfter.isEmpty(), "Stock adjustments should be cleaned up on hard delete")
    }

    @Test
    fun testProductHistory_withoutInitialStockAdjustment_backCalculatesFromCurrentStock() = runTest {
        val product = Product(
            id = "p-legacy",
            name = "Legacy Item",
            barcodes = emptyList(),
            basePrice = 100,
            unitType = UnitType.PIECE,
            stockQuantity = 8
        )
        repository.saveProduct(product)

        // Clear adjustments so it simulates a product with no initial stock entry
        db.productStockAdjustmentDao().deleteAdjustmentsByProductId("p-legacy")
        assertEquals(0, db.productStockAdjustmentDao().getAdjustmentsByProductId("p-legacy").size)

        db.userDao().insertOrUpdateUser(
            UserEntity.fromDomain(User(id = "u-legacy", name = "Legacy User", balance = 5000, isActive = true))
        )

        // 2 purchases
        db.transactionDao().insertTransaction(
            TransactionEntity(
                id = "tx-leg-1",
                userId = "u-legacy",
                userNameSnapshot = "Legacy User",
                timestamp = 1000L,
                type = TransactionType.PURCHASE.name,
                referenceTransactionId = null,
                note = null,
                totalAmount = 200L,
                items = listOf(TransactionItem(productId = "p-legacy", productName = "Legacy Item", quantity = 2, unitPriceAtPurchase = 100, unitType = UnitType.PIECE))
            )
        )
        db.transactionDao().insertTransaction(
            TransactionEntity(
                id = "tx-leg-2",
                userId = "u-legacy",
                userNameSnapshot = "Legacy User",
                timestamp = 2000L,
                type = TransactionType.PURCHASE.name,
                referenceTransactionId = null,
                note = null,
                totalAmount = 400L,
                items = listOf(TransactionItem(productId = "p-legacy", productName = "Legacy Item", quantity = 4, unitPriceAtPurchase = 100, unitType = UnitType.PIECE))
            )
        )

        val history = repository.getProductHistory("p-legacy", page = 0, pageSize = 25)
        assertEquals(2, history.items.size)

        // tx-leg-2 is newest (timestamp 2000), resulting in current stock (8)
        val newestTx = history.items.first { it.id == "tx-leg-2_p-legacy" }
        assertEquals(8L, newestTx.stockAfter)

        // tx-leg-1 is older (timestamp 1000), stock before tx-leg-2 was 8 + 4 = 12
        val olderTx = history.items.first { it.id == "tx-leg-1_p-legacy" }
        assertEquals(12L, olderTx.stockAfter)
    }
}
