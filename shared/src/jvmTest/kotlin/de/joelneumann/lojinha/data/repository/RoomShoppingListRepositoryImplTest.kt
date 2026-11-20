package de.joelneumann.lojinha.data.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import de.joelneumann.lojinha.data.database.AppDatabase
import de.joelneumann.lojinha.data.database.AppDatabase_Impl
import de.joelneumann.lojinha.data.database.DatabaseFactory
import de.joelneumann.lojinha.domain.model.ShoppingListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class RoomShoppingListRepositoryImplTest {

    private lateinit var tempDir: File
    private lateinit var dbFile: File
    private lateinit var db: AppDatabase
    private var dataChangedCallCount = 0
    private lateinit var repository: RoomShoppingListRepositoryImpl

    @BeforeTest
    fun setup() {
        tempDir = Files.createTempDirectory("shopping_list_repo_test").toFile()
        dbFile = File(tempDir, "test_shopping_list.db")

        val builder = Room.databaseBuilder<AppDatabase>(
            name = dbFile.absolutePath,
            factory = { AppDatabase_Impl() }
        )
        builder.setDriver(BundledSQLiteDriver())
        builder.setQueryCoroutineContext(Dispatchers.IO)
        db = builder.build()

        dataChangedCallCount = 0
        repository = RoomShoppingListRepositoryImpl(
            dao = db.shoppingListItemDao(),
            onDataChanged = { dataChangedCallCount++ }
        )
    }

    @AfterTest
    fun tearDown() {
        db.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun testAddItemsAppendsInOrderAndOverwritesExistingProduct() = runTest {
        assertTrue(repository.getItemsFlow().first().isEmpty())

        repository.addOrUpdateItem(
            ShoppingListItem(productId = "p-1", name = "Club Mate", quantity = "10 Stk.")
        )
        repository.addOrUpdateItem(
            ShoppingListItem(productId = null, name = "Spülmittel", quantity = "2 Flaschen")
        )

        val afterAdd = repository.getAllItems()
        assertEquals(2, afterAdd.size)
        assertEquals("Club Mate", afterAdd[0].name)
        assertEquals(0, afterAdd[0].sortOrder)
        assertEquals("Spülmittel", afterAdd[1].name)
        assertEquals(1, afterAdd[1].sortOrder)
        assertTrue(afterAdd[0].isNewlyAdded)
        assertTrue(afterAdd[0].isReviewedForExport)

        // Overwriting existing product preserves its sortOrder and updates quantity
        repository.addOrUpdateItem(
            ShoppingListItem(productId = "p-1", name = "Club Mate", quantity = "24 Stk.")
        )
        // Even with a fresh non-blank UUID, productId deduplication still merges into existing row
        repository.addOrUpdateItem(
            ShoppingListItem(id = "new-uuid-should-merge", productId = "p-1", name = "Club Mate", quantity = "30 Stk.")
        )
        val afterOverwrite = repository.getAllItems()
        assertEquals(2, afterOverwrite.size)
        assertEquals("30 Stk.", afterOverwrite[0].quantity)
        assertEquals(0, afterOverwrite[0].sortOrder)
    }

    @Test
    fun testExportBaselineAndReviewWorkflowAndOrderIndependence() = runTest {
        repository.addOrUpdateItem(ShoppingListItem(id = "i1", productId = "p1", name = "Apples", quantity = "2 kg"))
        repository.addOrUpdateItem(ShoppingListItem(id = "i2", productId = "p2", name = "Milk", quantity = "6 Stk."))

        // Commit export baseline (simulating PDF export)
        repository.commitExportBaseline()

        val postExport = repository.getAllItems()
        assertEquals(2, postExport.size)
        assertTrue(postExport.all { it.isPendingReview }, "All items must start as pending review after export")
        assertFalse(postExport.any { it.isReviewedForExport })

        // Reorder items -> must NOT mark them reviewed!
        repository.updateItemsOrder(listOf(postExport[1], postExport[0]))
        val afterReorder = repository.getAllItems()
        assertEquals("Milk", afterReorder[0].name)
        assertEquals("Apples", afterReorder[1].name)
        assertTrue(afterReorder.all { it.isPendingReview }, "Reordering must NOT alter review status")

        // Quick-confirm unchanged item ("Milk")
        repository.confirmItem("i2")
        val afterConfirm = repository.getAllItems()
        val milk = afterConfirm.first { it.id == "i2" }
        val apples = afterConfirm.first { it.id == "i1" }
        assertTrue(milk.isReviewedForExport)
        assertFalse(milk.isPendingReview)
        assertTrue(apples.isPendingReview)

        // Edit quantity of "Apples" -> automatically reviewed
        repository.updateItem(apples.copy(quantity = "3 kg"))
        val afterEdit = repository.getAllItems()
        val updatedApples = afterEdit.first { it.id == "i1" }
        assertTrue(updatedApples.isContentChanged)
        assertTrue(updatedApples.isReviewedForExport)

        // Unconfirm edited "Apples" -> keeps edited quantity and sortOrder, sets isPendingReview = true
        repository.unconfirmItem("i1")
        val afterUnconfirm = repository.getAllItems()
        val unconfirmedApples = afterUnconfirm.first { it.id == "i1" }
        assertEquals("3 kg", unconfirmedApples.quantity)
        assertEquals(1, unconfirmedApples.sortOrder)
        assertFalse(unconfirmedApples.isConfirmedForCurrentExport)
        assertTrue(unconfirmedApples.isPendingReview)
    }

    @Test
    fun testDeleteAndClearAll() = runTest {
        repository.addOrUpdateItem(ShoppingListItem(id = "i1", name = "Item 1", quantity = "1"))
        repository.addOrUpdateItem(ShoppingListItem(id = "i2", name = "Item 2", quantity = "2"))

        repository.deleteItem("i1")
        val remaining = repository.getAllItems()
        assertEquals(1, remaining.size)
        assertEquals("i2", remaining[0].id)

        repository.clearAllItems()
        assertTrue(repository.getAllItems().isEmpty())
    }

    @Test
    fun testMigration17To18To19SqlCreatesValidTables() {
        val migrationDbFile = File(tempDir, "migration_17_18_19.db")
        val conn = BundledSQLiteDriver().open(migrationDbFile.absolutePath)
        try {
            // Minimal v17 users and settings tables so MIGRATION_18_19 ALTER TABLE statements succeed
            conn.execSQL(
                "CREATE TABLE IF NOT EXISTS `users` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `balance` INTEGER NOT NULL, `language` TEXT NOT NULL, `secondaryCurrency` TEXT NOT NULL, `pin` TEXT, `userBarcode` TEXT, `userBarcodeNumber` TEXT, `isActive` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL, `avatarType` TEXT NOT NULL, `avatarEmoji` TEXT NOT NULL, `avatarColor` TEXT NOT NULL, PRIMARY KEY(`id`))"
            )
            conn.execSQL(
                "CREATE TABLE IF NOT EXISTS `settings` (`id` INTEGER NOT NULL, `adminPasswordHash` TEXT NOT NULL, `globalMarkupPercent` REAL NOT NULL, `usdExchangeRate` REAL NOT NULL, `eurExchangeRate` REAL NOT NULL, `inactivityTimeoutMinutes` INTEGER NOT NULL, `autoUpdateExchangeRates` INTEGER NOT NULL, `lastExchangeRateUpdateTimestamp` INTEGER, `yellowBalanceWarningThresholdCents` INTEGER NOT NULL, `redBalanceWarningThresholdCents` INTEGER NOT NULL, `backupLocationPath` TEXT NOT NULL, `autoBackupEnabled` INTEGER NOT NULL, `autoBackupFormat` TEXT NOT NULL, `autoBackupScheduleType` TEXT NOT NULL, `autoBackupTime` TEXT NOT NULL, `autoBackupIntervalHours` INTEGER NOT NULL, `oneDriveClientId` TEXT NOT NULL, `oneDriveRefreshToken` TEXT, `oneDriveAccountEmail` TEXT, `oneDriveAccountName` TEXT, `oneDriveDefaultFolder` TEXT NOT NULL, `oneDriveTenant` TEXT NOT NULL, `supportEmail` TEXT, PRIMARY KEY(`id`))"
            )

            DatabaseFactory.MIGRATION_17_18.migrate(conn)
            DatabaseFactory.MIGRATION_18_19.migrate(conn)

            val expectedTables = listOf(
                "shopping_list_items",
                "veggie_unit_types",
                "veggie_products",
                "veggie_order_rounds",
                "veggie_round_product_settlements",
                "veggie_order_entries",
                "veggie_user_favorites"
            )
            for (table in expectedTables) {
                val stmt = conn.prepare("SELECT name FROM sqlite_master WHERE type='table' AND name='$table'")
                try {
                    assertTrue(stmt.step(), "$table table must exist after MIGRATION_17_18 + MIGRATION_18_19")
                } finally {
                    stmt.close()
                }
            }
        } finally {
            conn.close()
        }
    }
}
