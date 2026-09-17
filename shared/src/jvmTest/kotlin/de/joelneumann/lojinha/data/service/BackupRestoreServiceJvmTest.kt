package de.joelneumann.lojinha.data.service

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.joelneumann.lojinha.data.database.AppDatabase
import de.joelneumann.lojinha.data.database.AppDatabase_Impl
import de.joelneumann.lojinha.data.entity.*
import de.joelneumann.lojinha.domain.model.Barcode
import de.joelneumann.lojinha.domain.model.TransactionItem
import de.joelneumann.lojinha.domain.model.UnitType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class BackupRestoreServiceJvmTest {

    private lateinit var tempDir: File
    private lateinit var dbFile: File
    private lateinit var db: AppDatabase
    private lateinit var service: BackupRestoreService

    @BeforeTest
    fun setup() {
        tempDir = Files.createTempDirectory("backup_restore_test").toFile()
        dbFile = File(tempDir, "test_lojinha.db")

        val builder = Room.databaseBuilder<AppDatabase>(
            name = dbFile.absolutePath,
            factory = { AppDatabase_Impl() }
        )
        builder.setDriver(BundledSQLiteDriver())
        builder.setQueryCoroutineContext(Dispatchers.IO)
        db = builder.build()

        service = BackupRestoreService(db, dbFile)
    }

    @AfterTest
    fun tearDown() {
        db.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun testFullV11SchemaBackupAndRestore() = runTest {
        // 1. Seed complete v11 schema data
        val user = UserEntity(
            id = "u1",
            name = "Alice",
            balance = 1500L,
            language = "en",
            secondaryCurrency = "USD",
            pin = "1234",
            userBarcode = "BAR123",
            userBarcodeNumber = "999",
            isActive = true,
            isDeleted = false,
            avatarType = "EMOJI",
            avatarEmoji = "🚀",
            avatarColor = "#FF5500"
        )
        db.userDao().insertOrUpdateUser(user)

        val product = ProductEntity(
            id = "p1",
            name = "Club Mate",
            barcodes = listOf(Barcode("4029764001807", "Bottle")),
            basePrice = 250L,
            unitType = "PIECE",
            stockQuantity = 48L,
            customMarkupPercent = 15.0,
            isActive = true
        )
        db.productDao().insertOrUpdateProduct(product)

        val transaction = TransactionEntity(
            id = "t1",
            userId = "u1",
            userNameSnapshot = "Alice",
            timestamp = 1700000000000L,
            type = "PURCHASE",
            referenceTransactionId = null,
            note = "Bought Club Mate",
            totalAmount = 250L,
            items = listOf(TransactionItem("p1", "Club Mate", UnitType.PIECE, 1L, 250L)),
            userBalanceBefore = 1750L,
            userBalanceAfter = 1500L
        )
        db.transactionDao().insertTransaction(transaction)

        val list = BillingListEntity(
            id = "l1",
            name = "VIP Club",
            type = "USER",
            basePrice = 500L,
            comment = "Monthly recurring VIP list",
            isDeleted = false
        )
        db.billingListDao().insertOrUpdateBillingList(list)

        val listUser = BillingListUserEntity(
            id = "lu1",
            listId = "l1",
            userId = "u1",
            quantity = 2
        )
        db.billingListDao().insertBillingListUser(listUser)

        val routine = BackupEntity(
            id = "b1",
            name = "Nightly DB",
            isEnabled = true,
            type = "TIMED",
            fileType = "DB",
            writeMode = "CREATE_NEW_FILE",
            scheduleConfig = de.joelneumann.lojinha.domain.model.BackupScheduleConfig.Timed("02:00"),
            backupLocationPath = tempDir.absolutePath,
            lastBackupTimestamp = 1699999000000L
        )
        db.backupDao().insertOrUpdateBackup(routine)

        val settings = SettingsEntity(
            id = 1,
            adminPasswordHash = "secret_hash",
            globalMarkupPercent = 10.0,
            usdExchangeRate = 0.18,
            eurExchangeRate = 0.16,
            inactivityTimeoutMinutes = 5,
            backupLocationPath = tempDir.absolutePath,
            autoBackupEnabled = true,
            autoBackupFormat = "DB",
            autoBackupScheduleType = "DAILY",
            autoBackupTime = "03:00",
            autoBackupIntervalHours = 24,
            lastBackupTimestamp = 1700000000000L,
            oneDriveClientId = "custom-client-id",
            oneDriveRefreshToken = "token-xyz",
            oneDriveAccountEmail = "admin@example.com",
            oneDriveAccountName = "Admin User",
            oneDriveDefaultFolder = "/CustomFolder",
            oneDriveTenant = "consumers",
            supportEmail = "support@lojinha.app"
        )
        db.settingsDao().insertOrUpdateSettings(settings)

        // 2. Perform DB Backup snapshot
        val backupDir = File(tempDir, "backups")
        backupDir.mkdirs()
        val backupFile = service.performDbBackup(backupDir)
        assertTrue(backupFile.exists(), "Backup file should exist")

        // 3. Clear or change active database
        db.billingListDao().deleteAllBillingListUsers()
        db.billingListDao().deleteAllBillingLists()
        db.userDao().deleteAllUsers()
        db.productDao().deleteAllProducts()
        db.transactionDao().deleteAllTransactions()
        db.backupDao().deleteAllBackups()

        val emptyUsers = db.userDao().getAllUsers()
        assertEquals(0, emptyUsers.size, "Active database should be cleared before restore")

        // 4. Restore from backup
        service.restoreDbFromBackup(backupFile)

        // 5. Verify pre-restore safety snapshot was created
        val safetySnapshot = File(tempDir, "lojinha_room_pre_restore_safety.db")
        assertTrue(safetySnapshot.exists(), "Pre-restore safety snapshot file must exist")

        // 6. Verify restored Users & Avatar columns
        val restoredUsers = db.userDao().getAllUsers()
        assertEquals(1, restoredUsers.size)
        val restoredUser = restoredUsers.first()
        assertEquals("u1", restoredUser.id)
        assertEquals("Alice", restoredUser.name)
        assertEquals("EMOJI", restoredUser.avatarType)
        assertEquals("🚀", restoredUser.avatarEmoji)
        assertEquals("#FF5500", restoredUser.avatarColor)

        // 7. Verify restored Products
        val restoredProducts = db.productDao().getAllProducts()
        assertEquals(1, restoredProducts.size)
        assertEquals("Club Mate", restoredProducts.first().name)
        assertEquals(15.0, restoredProducts.first().customMarkupPercent)

        // 8. Verify restored Transactions
        val restoredTransactions = db.transactionDao().getAllTransactions()
        assertEquals(1, restoredTransactions.size)
        assertEquals(1750L, restoredTransactions.first().userBalanceBefore)
        assertEquals(1500L, restoredTransactions.first().userBalanceAfter)

        // 9. Verify restored Billing Lists & Users (including comment)
        val restoredLists = db.billingListDao().getAllBillingLists()
        assertEquals(1, restoredLists.size)
        assertEquals("VIP Club", restoredLists.first().name)
        assertEquals("Monthly recurring VIP list", restoredLists.first().comment)

        val restoredListUsers = db.billingListDao().getAllBillingListUsers()
        assertEquals(1, restoredListUsers.size)
        assertEquals("lu1", restoredListUsers.first().id)
        assertEquals(2, restoredListUsers.first().quantity)

        // 10. Verify restored Backup Routines
        val restoredRoutines = db.backupDao().getAllBackups()
        assertEquals(1, restoredRoutines.size)
        assertEquals("Nightly DB", restoredRoutines.first().name)

        // 11. Verify restored Settings (OneDrive & Support Email)
        val restoredSettings = db.settingsDao().getSettings()
        assertNotNull(restoredSettings)
        assertEquals("admin@example.com", restoredSettings.oneDriveAccountEmail)
        assertEquals("/CustomFolder", restoredSettings.oneDriveDefaultFolder)
        assertEquals("support@lojinha.app", restoredSettings.supportEmail)
        assertEquals(5, restoredSettings.inactivityTimeoutMinutes)
    }

    @Test
    fun testWipeAllDataCreatesSafetySnapshotAndCleansAllTables() = runTest {
        // Seed user and list
        val user = UserEntity("u2", "Bob", 500L, "pt", "NONE", null, null, null, true)
        db.userDao().insertOrUpdateUser(user)

        val list = BillingListEntity("l2", "Coffee Group", "USER", null, null)
        db.billingListDao().insertOrUpdateBillingList(list)

        val listUser = BillingListUserEntity("lu2", "l2", "u2", 1)
        db.billingListDao().insertBillingListUser(listUser)

        // Wipe all data
        service.wipeAllData()

        // Verify safety snapshot created
        val safetySnapshot = File(tempDir, "lojinha_room_pre_wipe_safety.db")
        assertTrue(safetySnapshot.exists(), "Pre-wipe safety snapshot must exist")

        // Verify tables are wiped
        assertEquals(0, db.userDao().getAllUsers().size)
        assertEquals(0, db.productDao().getAllProducts().size)
        assertEquals(0, db.transactionDao().getAllTransactions().size)
        assertEquals(0, db.billingListDao().getAllBillingLists().size)
        assertEquals(0, db.billingListDao().getAllBillingListUsers().size)

        // Verify default settings reset
        val settings = db.settingsDao().getSettings()
        assertNotNull(settings)
        assertEquals("admin", settings.adminPasswordHash)
    }

    @Test
    fun testCorruptedBackupFailsIntegrityCheckWithoutModifyingDatabase() = runTest {
        // Seed initial data
        val user = UserEntity("u3", "Charlie", 200L, "en", "NONE", null, null, null, true)
        db.userDao().insertOrUpdateUser(user)

        // Create a corrupted fake backup file
        val corruptFile = File(tempDir, "corrupted.db")
        corruptFile.writeText("THIS IS NOT A VALID SQLITE DATABASE FILE")

        assertFailsWith<Exception> {
            service.restoreDbFromBackup(corruptFile)
        }

        // Active database must not have been touched
        val users = db.userDao().getAllUsers()
        assertEquals(1, users.size, "Database should remain untouched on restore failure")
        assertEquals("Charlie", users.first().name)
    }
}
