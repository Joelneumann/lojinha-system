package de.joelneumann.lojinha.data.service

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.joelneumann.lojinha.data.database.AppDatabase
import de.joelneumann.lojinha.data.database.AppDatabase_Impl
import de.joelneumann.lojinha.data.entity.*
import de.joelneumann.lojinha.domain.model.*
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
        assertEquals(de.joelneumann.lojinha.security.PasswordHasher.hash("admin"), settings.adminPasswordHash)
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

    @Test
    fun testParseCsvRecordsWithBomQuotesAndMultiline() {
        val rawCsv = "\uFEFFid,name,description\n" +
                "1,\"Item with, comma\",\"Simple desc\"\n" +
                "2,\"Item with \"\"escaped quotes\"\"\",\"Line 1\nLine 2\"\n" +
                "3,Normal,\"\"\n"

        val records = service.parseCsvRecords(rawCsv)
        assertEquals(4, records.size)

        // Header
        assertEquals(listOf("id", "name", "description"), records[0])

        // Row 1: comma inside quotes
        assertEquals(listOf("1", "Item with, comma", "Simple desc"), records[1])

        // Row 2: escaped quotes and multiline
        assertEquals(listOf("2", "Item with \"escaped quotes\"", "Line 1\nLine 2"), records[2])

        // Row 3: empty quoted string
        assertEquals(listOf("3", "Normal", ""), records[3])
    }

    @Test
    fun testPerformCsvBackupIncludesItemsInTransactions() = runTest {
        // Seed user and transaction with items
        val user = UserEntity("u-csv", "CsvUser", 1000L, "en", "NONE", null, null, null, true)
        db.userDao().insertOrUpdateUser(user)

        val txItem1 = TransactionItem("p1", "Item One", UnitType.PIECE, 2L, 400L)
        val txItem2 = TransactionItem("p2", "Item Two", UnitType.WEIGHT, 150L, 300L)
        val transaction = TransactionEntity(
            id = "tx-csv-1",
            userId = "u-csv",
            userNameSnapshot = "CsvUser",
            timestamp = 1700000000000L,
            type = "PURCHASE",
            referenceTransactionId = null,
            note = "Testing items backup",
            totalAmount = 700L,
            items = listOf(txItem1, txItem2),
            userBalanceBefore = 1000L,
            userBalanceAfter = 300L
        )
        db.transactionDao().insertTransaction(transaction)

        val csvFolder = service.performCsvBackup(tempDir)
        assertTrue(csvFolder.exists() && csvFolder.isDirectory)

        val txFile = File(csvFolder, "transactions.csv")
        assertTrue(txFile.exists(), "transactions.csv should exist")

        val records = service.parseCsvRecords(txFile.readText())
        assertTrue(records.size >= 2, "Should contain header and at least 1 record")

        val header = records[0]
        val itemsIndex = header.indexOf("items")
        assertTrue(itemsIndex >= 0, "transactions.csv header must contain 'items' column")

        val row = records[1]
        val itemsValue = row[itemsIndex]
        assertTrue(itemsValue.contains("Item One"), "items column should contain 'Item One'")
        assertTrue(itemsValue.contains("Item Two"), "items column should contain 'Item Two'")
    }

    @Test
    fun testExecuteRoutineBackupReturnsAllFiles() = runTest {
        val routineBoth = BackupRoutine(
            id = "b-both",
            name = "Test Both",
            isEnabled = true,
            type = BackupType.LOCAL,
            fileType = BackupFileType.BOTH,
            writeMode = BackupWriteMode.CREATE_NEW_FILE,
            scheduleConfig = BackupScheduleConfig.Timed("00:00"),
            backupLocationPath = tempDir.absolutePath,
            lastBackupTimestamp = 0L
        )

        val filesBoth = service.executeRoutineBackup(routineBoth)
        assertEquals(2, filesBoth.size, "BOTH should return both DB file and CSV folder")
        assertTrue(filesBoth.any { it.isFile && it.name.endsWith(".db") }, "Must include a .db file")
        assertTrue(filesBoth.any { it.isDirectory && it.name.startsWith("lojinha_csv_export_") }, "Must include CSV folder")

        val routineDb = routineBoth.copy(id = "b-db", fileType = BackupFileType.DB)
        val filesDb = service.executeRoutineBackup(routineDb)
        assertEquals(1, filesDb.size)
        assertTrue(filesDb[0].isFile && filesDb[0].name.endsWith(".db"))

        val routineCsv = routineBoth.copy(id = "b-csv", fileType = BackupFileType.CSV)
        val filesCsv = service.executeRoutineBackup(routineCsv)
        assertEquals(1, filesCsv.size)
        assertTrue(filesCsv[0].isDirectory && filesCsv[0].name.startsWith("lojinha_csv_export_"))
    }

    @Test
    fun testParseCurrencyToCents() {
        assertEquals(243L, service.parseCurrencyToCents("R$ 2,43"))
        assertEquals(243L, service.parseCurrencyToCents("2,43"))
        assertEquals(243L, service.parseCurrencyToCents("2.43"))
        assertEquals(150050L, service.parseCurrencyToCents("1.500,50"))
        assertEquals(150050L, service.parseCurrencyToCents("1,500.50"))
        assertEquals(1050L, service.parseCurrencyToCents("10,5"))
        assertEquals(1050L, service.parseCurrencyToCents("10.5"))
        assertEquals(1000L, service.parseCurrencyToCents("10"))
        assertEquals(0L, service.parseCurrencyToCents("0"))
        assertEquals(-500L, service.parseCurrencyToCents("-R$ 5,00"))
        assertEquals(-500L, service.parseCurrencyToCents("R$ -5,00"))
        assertEquals(-243L, service.parseCurrencyToCents("-2,43"))
        assertNull(service.parseCurrencyToCents("invalid"))
        assertNull(service.parseCurrencyToCents(""))
    }

    @Test
    fun testImportProductsAndUsersFromCsv() = runTest {
        // Test Product import with BOM, quoted commas, and R$ / comma decimal currency
        val productsCsvFile = File(tempDir, "products_import.csv")
        productsCsvFile.writeText(
            "\uFEFFid,name,barcodes,basePrice,unitType,stockQuantity,customMarkupPercent,isActive\n" +
            "p-imp-1,\"Chocolate, Dark\",123456789;Dark Bar,\"R$ 1,50\",PIECE,20,0.0,true\n" +
            "p-imp-2,\"Coffee \"\"Special\"\" Beans\",987654321,\"5,00\",WEIGHT,1000,10.0,true\n" +
            "p-imp-3,\"Mineral Water\",,2.43,PIECE,50,,true\n"
        )

        val productResult = service.importProductsFromCsv(productsCsvFile)
        assertEquals(3, productResult.addedCount)
        assertEquals(3, productResult.totalProcessed)
        assertEquals(0, productResult.errors.size)

        val importedProducts = db.productDao().getAllProducts().associateBy { it.id }
        assertEquals("Chocolate, Dark", importedProducts["p-imp-1"]?.name)
        assertEquals(150L, importedProducts["p-imp-1"]?.basePrice)
        assertEquals("Coffee \"Special\" Beans", importedProducts["p-imp-2"]?.name)
        assertEquals(500L, importedProducts["p-imp-2"]?.basePrice)
        assertEquals(1000L, importedProducts["p-imp-2"]?.stockQuantity)
        assertEquals(243L, importedProducts["p-imp-3"]?.basePrice)

        // Test User import with BOM and R$ balance
        val usersCsvFile = File(tempDir, "users_import.csv")
        usersCsvFile.writeText(
            "\uFEFFid,name,balance,language,secondaryCurrency,pin,userBarcode,isActive\n" +
            "u-imp-1,\"Doe, Jane\",\"R$ 25,00\",en,USD,4321,USR999,true\n"
        )

        val userResult = service.importUsersFromCsv(usersCsvFile)
        assertEquals(1, userResult.addedCount)
        assertEquals(1, userResult.totalProcessed)
        assertEquals(0, userResult.errors.size)

        val importedUser = db.userDao().getUserById("u-imp-1")
        assertNotNull(importedUser)
        assertEquals("Doe, Jane", importedUser.name)
        assertEquals(2500L, importedUser.balance)
        val expectedHash = de.joelneumann.lojinha.security.PasswordHasher.hash("4321")
        assertEquals(expectedHash, importedUser.pin)

        // Test performCsvBackup ensures users.csv contains hash and formatted currency
        val exportedFolder = service.performCsvBackup(tempDir, BackupWriteMode.CREATE_NEW_FILE)
        val exportedUsersCsv = File(exportedFolder, "users.csv")
        assertTrue(exportedUsersCsv.exists())
        val csvText = exportedUsersCsv.readText()
        assertTrue(csvText.contains(expectedHash), "Exported CSV must contain hashed PIN")
        assertFalse(csvText.contains(",4321,"), "Exported CSV must not contain plain text PIN")
        assertTrue(csvText.contains("\"R$ 25,00\"") || csvText.contains("R$ 25,00"), "Exported CSV must contain formatted BRL balance")

        // Test User import with already hashed PIN and plain decimal balance
        val usersCsvFileHashed = File(tempDir, "users_import_hashed.csv")
        usersCsvFileHashed.writeText(
            "id,name,balance,language,secondaryCurrency,pin,userBarcode,isActive\n" +
            "u-imp-2,\"Smith, John\",10.00,en,USD,$expectedHash,USR888,true\n"
        )
        val userResultHashed = service.importUsersFromCsv(usersCsvFileHashed)
        assertEquals(1, userResultHashed.addedCount)
        val importedUserHashed = db.userDao().getUserById("u-imp-2")
        assertNotNull(importedUserHashed)
        assertEquals(1000L, importedUserHashed.balance)
        assertEquals(expectedHash, importedUserHashed.pin, "Pre-hashed PIN must be preserved as-is without double-hashing")
    }

    @Test
    fun testCombinedBillingListsCsvExport() = runTest {
        val user1 = UserEntity("u-b1", "Alice Member", 0L, "en", "NONE", null, null, null, true)
        val user2 = UserEntity("u-b2", "Bob Member", 0L, "en", "NONE", null, null, null, true)
        db.userDao().insertOrUpdateUser(user1)
        db.userDao().insertOrUpdateUser(user2)

        val listWithUsers = BillingListEntity("bl-1", "Friday Drinks", "USER", 350L, "Weekly club", false)
        val emptyList = BillingListEntity("bl-2", "Empty List", "USER", null, null, false)
        db.billingListDao().insertOrUpdateBillingList(listWithUsers)
        db.billingListDao().insertOrUpdateBillingList(emptyList)

        db.billingListDao().insertBillingListUser(BillingListUserEntity("blu-1", "bl-1", "u-b1", 2))
        db.billingListDao().insertBillingListUser(BillingListUserEntity("blu-2", "bl-1", "u-b2", 1))

        val exportFolder = service.performCsvBackup(tempDir, BackupWriteMode.CREATE_NEW_FILE)
        val blCsv = File(exportFolder, "billing_lists.csv")
        assertTrue(blCsv.exists(), "billing_lists.csv must exist")

        val records = service.parseCsvRecords(blCsv.readText())
        // Header + 2 entries for bl-1 + 1 entry for empty bl-2 = 4 lines
        assertEquals(4, records.size)
        assertEquals(listOf("listId", "listName", "type", "basePrice", "comment", "isDeleted", "userId", "userName", "quantity"), records[0])

        val aliceRow = records.find { it.getOrNull(6) == "u-b1" }
        assertNotNull(aliceRow)
        assertEquals("Friday Drinks", aliceRow[1])
        assertEquals("R$ 3,50", aliceRow[3])
        assertEquals("Alice Member", aliceRow[7])
        assertEquals("2", aliceRow[8])

        val emptyRow = records.find { it.getOrNull(0) == "bl-2" }
        assertNotNull(emptyRow)
        assertEquals("Empty List", emptyRow[1])
        assertEquals("", emptyRow[3])
        assertEquals("", emptyRow[6])
        assertEquals("", emptyRow[7])
    }

    @Test
    fun testNonDestructiveCsvImportPreservesUserDataAndProductData() = runTest {
        // Seed existing user with custom avatar, PIN, barcode
        val existingUser = UserEntity(
            id = "u-orig",
            name = "Original User",
            balance = 500L,
            language = "de",
            secondaryCurrency = "EUR",
            pin = "hashed_secret_pin",
            userBarcode = "BC-12345",
            userBarcodeNumber = "BC-12345",
            isActive = true,
            isDeleted = false,
            avatarType = "EMOJI",
            avatarEmoji = "⭐",
            avatarColor = "#123456"
        )
        db.userDao().insertOrUpdateUser(existingUser)

        // Seed existing product with custom markup and stock
        val existingProduct = ProductEntity(
            id = "p-orig",
            name = "Original Product",
            barcodes = listOf(Barcode("BC-PROD", "Main")),
            basePrice = 200L,
            unitType = "PIECE",
            stockQuantity = 40L,
            customMarkupPercent = 25.0,
            isActive = true
        )
        db.productDao().insertOrUpdateProduct(existingProduct)

        // Partial update CSV for product (omits barcodes, customMarkupPercent, stockQuantity)
        val productUpdateCsv = File(tempDir, "product_update.csv")
        productUpdateCsv.writeText(
            "id,name,basePrice\n" +
            "p-orig,\"Updated Product Name\",\"R$ 3,00\"\n"
        )
        service.importProductsFromCsv(productUpdateCsv)

        val updatedProduct = db.productDao().getAllProducts().first { it.id == "p-orig" }
        assertEquals("Updated Product Name", updatedProduct.name)
        assertEquals(300L, updatedProduct.basePrice)
        assertEquals(40L, updatedProduct.stockQuantity, "Stock must be preserved")
        assertEquals(25.0, updatedProduct.customMarkupPercent, "Custom markup must be preserved")
        assertEquals(1, updatedProduct.barcodes.size, "Barcodes must be preserved")
        assertEquals("BC-PROD", updatedProduct.barcodes.first().code)

        // Partial update CSV for user (only updates balance, omits pin, avatar, barcode)
        val userUpdateCsv = File(tempDir, "user_update.csv")
        userUpdateCsv.writeText(
            "id,name,balance\n" +
            "u-orig,\"Renamed User\",\"R$ 15,00\"\n"
        )
        service.importUsersFromCsv(userUpdateCsv)

        val updatedUser = db.userDao().getUserById("u-orig")
        assertNotNull(updatedUser)
        assertEquals("Renamed User", updatedUser.name)
        assertEquals(1500L, updatedUser.balance)
        assertEquals("hashed_secret_pin", updatedUser.pin, "PIN must be preserved")
        assertEquals("BC-12345", updatedUser.userBarcode, "User barcode must be preserved")
        assertEquals("EMOJI", updatedUser.avatarType, "Avatar type must be preserved")
        assertEquals("⭐", updatedUser.avatarEmoji, "Avatar emoji must be preserved")
        assertEquals("#123456", updatedUser.avatarColor, "Avatar color must be preserved")
        assertEquals(false, updatedUser.isDeleted, "isDeleted must be preserved")
    }

    @Test
    fun testSemicolonDelimitedCsvImport() = runTest {
        val semiCsv = File(tempDir, "semi_import.csv")
        semiCsv.writeText(
            "id;name;barcodes;basePrice;unitType;stockQuantity;customMarkupPercent;isActive\n" +
            "p-semi;\"Brazilian Soda, Can\";BAR_SEMI;\"R$ 4,50\";PIECE;12;5.0;true\n"
        )
        val result = service.importProductsFromCsv(semiCsv)
        assertEquals(1, result.addedCount)
        assertEquals(0, result.errors.size)

        val imported = db.productDao().getAllProducts().first { it.id == "p-semi" }
        assertEquals("Brazilian Soda, Can", imported.name)
        assertEquals(450L, imported.basePrice)
        assertEquals(12L, imported.stockQuantity)
    }

    @Test
    fun testEmptyDatabaseDetectionAndAutoBackupSuppression() = runTest {
        assertTrue(service.isDatabaseEmpty(), "Fresh/empty database must report empty")

        // Seed a user -> database no longer empty
        val user = UserEntity("u-test", "Test", 0L, "en", "NONE", null, null, null, true)
        db.userDao().insertOrUpdateUser(user)
        assertFalse(service.isDatabaseEmpty(), "Database with user must not report empty")

        // Wipe data -> database empty again
        service.wipeAllData()
        assertTrue(service.isDatabaseEmpty(), "Wiped database must report empty")
    }

    @Test
    fun testRestoreDbPreservesBillingListLastExecutionTime() = runTest {
        // Seed a billing list with lastExecutionTime
        val list = BillingListEntity(
            id = "bl-exec",
            name = "Executed List",
            type = "FIXED",
            basePrice = 1000L,
            comment = "Test execution time",
            isDeleted = false,
            lastExecutionTime = 1726000000000L
        )
        db.billingListDao().insertOrUpdateBillingList(list)

        // Perform DB backup
        val backupFile = service.performDbBackup(tempDir, BackupWriteMode.CREATE_NEW_FILE)
        assertTrue(backupFile.exists())

        // Wipe data
        service.wipeAllData()
        assertEquals(0, db.billingListDao().getAllBillingLists().size)

        // Restore DB from backup
        service.restoreDbFromBackup(backupFile)

        val restoredLists = db.billingListDao().getAllBillingLists()
        assertEquals(1, restoredLists.size)
        val restored = restoredLists.first()
        assertEquals("bl-exec", restored.id)
        assertEquals("Executed List", restored.name)
        assertEquals(1726000000000L, restored.lastExecutionTime, "lastExecutionTime must be preserved across DB restore")
    }
}
