package de.joelneumann.lojinha.data.service

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.joelneumann.lojinha.data.database.AppDatabase
import de.joelneumann.lojinha.data.database.AppDatabase_Impl
import de.joelneumann.lojinha.data.database.DatabaseFactory
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

        val txs1 = db.transactionDao().getTransactionsByUserId("u-imp-1")
        assertEquals(1, txs1.size)
        assertEquals(de.joelneumann.lojinha.domain.model.TransactionType.ADMIN_DEPOSIT.name, txs1.first().type)
        assertEquals(2500L, txs1.first().totalAmount)
        assertEquals(0L, txs1.first().userBalanceBefore)
        assertEquals(2500L, txs1.first().userBalanceAfter)

        // Test performCsvBackup ensures users.csv contains hash and formatted currency
        val exportedFolder = service.performCsvBackup(tempDir, BackupWriteMode.CREATE_NEW_FILE)
        val exportedUsersCsv = File(exportedFolder, "users.csv")
        assertTrue(exportedUsersCsv.exists())
        val csvText = exportedUsersCsv.readText()
        assertTrue(csvText.startsWith(BackupRestoreService.UTF8_BOM), "Exported CSV must start with UTF-8 BOM")
        assertTrue(csvText.contains(expectedHash), "Exported CSV must contain hashed PIN")
        assertFalse(csvText.contains(";4321;") || csvText.contains(",4321,"), "Exported CSV must not contain plain text PIN")
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

    @Test
    fun testImportUsersWithBalanceCreatesTransactions() = runTest {
        val usersCsv = File(tempDir, "users_balance_tx.csv")
        usersCsv.writeText(
            "id,name,balance\n" +
            "u-pos,Positive User,15.50\n" +
            "u-neg,Negative User,-7.25\n" +
            "u-zero,Zero User,0.00\n"
        )
        val result = service.importUsersFromCsv(usersCsv)
        assertEquals(3, result.addedCount)

        // Positive user: starts at 0, deposit of 15.50 (1550L)
        val userPos = db.userDao().getUserById("u-pos")
        assertNotNull(userPos)
        assertEquals(1550L, userPos.balance)
        val txsPos = db.transactionDao().getTransactionsByUserId("u-pos")
        assertEquals(1, txsPos.size)
        assertEquals(de.joelneumann.lojinha.domain.model.TransactionType.ADMIN_DEPOSIT.name, txsPos.first().type)
        assertEquals(1550L, txsPos.first().totalAmount)
        assertEquals(0L, txsPos.first().userBalanceBefore)
        assertEquals(1550L, txsPos.first().userBalanceAfter)

        // Negative user: starts at 0, withdrawal of -7.25 (-725L)
        val userNeg = db.userDao().getUserById("u-neg")
        assertNotNull(userNeg)
        assertEquals(-725L, userNeg.balance)
        val txsNeg = db.transactionDao().getTransactionsByUserId("u-neg")
        assertEquals(1, txsNeg.size)
        assertEquals(de.joelneumann.lojinha.domain.model.TransactionType.ADMIN_WITHDRAWAL.name, txsNeg.first().type)
        assertEquals(-725L, txsNeg.first().totalAmount)
        assertEquals(0L, txsNeg.first().userBalanceBefore)
        assertEquals(-725L, txsNeg.first().userBalanceAfter)

        // Zero user: starts at 0, no transaction created
        val userZero = db.userDao().getUserById("u-zero")
        assertNotNull(userZero)
        assertEquals(0L, userZero.balance)
        val txsZero = db.transactionDao().getTransactionsByUserId("u-zero")
        assertEquals(0, txsZero.size)
    }

    @Test
    fun testPerformLocalBackup_dbOnly() = runTest {
        val destDir = File(tempDir, "manual_local_db_backup").apply { mkdirs() }
        val results = service.performLocalBackup(destDir, BackupFileType.DB, BackupWriteMode.CREATE_NEW_FILE)
        assertEquals(1, results.size)
        val backupFile = results.first()
        assertTrue(backupFile.exists())
        assertTrue(backupFile.name.startsWith("lojinha_backup_") && backupFile.name.endsWith(".db"))
        assertTrue(backupFile.length() > 0)
    }

    @Test
    fun testPerformLocalBackup_csvOnly() = runTest {
        val destDir = File(tempDir, "manual_local_csv_backup").apply { mkdirs() }
        val results = service.performLocalBackup(destDir, BackupFileType.CSV, BackupWriteMode.CREATE_NEW_FILE)
        assertEquals(1, results.size)
        val csvDir = results.first()
        assertTrue(csvDir.exists() && csvDir.isDirectory)
        assertTrue(File(csvDir, "products.csv").exists())
        assertTrue(File(csvDir, "users.csv").exists())
        assertTrue(File(csvDir, "transactions.csv").exists())
        assertTrue(File(csvDir, "billing_lists.csv").exists())
        assertTrue(File(csvDir, "admin_notes.csv").exists())
    }

    @Test
    fun testPerformLocalBackup_both() = runTest {
        val destDir = File(tempDir, "manual_local_both_backup").apply { mkdirs() }
        val results = service.performLocalBackup(destDir, BackupFileType.BOTH, BackupWriteMode.CREATE_NEW_FILE)
        assertEquals(2, results.size)
        val dbFile = results.find { it.name.endsWith(".db") }
        val csvDir = results.find { it.isDirectory }
        assertNotNull(dbFile)
        assertTrue(dbFile.exists() && dbFile.length() > 0)
        assertNotNull(csvDir)
        assertTrue(csvDir.exists() && csvDir.isDirectory)
        assertTrue(File(csvDir, "products.csv").exists())
        assertTrue(File(csvDir, "admin_notes.csv").exists())
    }

    @Test
    fun testPerformLocalBackup_overwriteLatest() = runTest {
        val destDir = File(tempDir, "manual_local_overwrite_backup").apply { mkdirs() }
        val firstResults = service.performLocalBackup(destDir, BackupFileType.DB, BackupWriteMode.OVERWRITE_LATEST)
        assertEquals("lojinha_backup_latest.db", firstResults.first().name)
        val secondResults = service.performLocalBackup(destDir, BackupFileType.DB, BackupWriteMode.OVERWRITE_LATEST)
        assertEquals("lojinha_backup_latest.db", secondResults.first().name)
        assertEquals(firstResults.first().absolutePath, secondResults.first().absolutePath)
    }

    @Test
    fun testRestoreDbPreservesActiveRoutinesWhenBackupLacksBackupRoutinesTable() = runTest {
        // 1. Seed active database with a backup routine
        val activeRoutine = BackupEntity(
            id = "active-r1",
            name = "Active Routine",
            isEnabled = true,
            type = "TIMED",
            fileType = "DB",
            writeMode = "CREATE_NEW_FILE",
            scheduleConfig = BackupScheduleConfig.Timed("04:00"),
            backupLocationPath = tempDir.absolutePath,
            lastBackupTimestamp = 1700000000000L
        )
        db.backupDao().insertOrUpdateBackup(activeRoutine)
        assertEquals(1, db.backupDao().getAllBackups().size)

        // 2. Create a legacy backup SQLite database that has users, but NO backup_routines table
        val legacyDbFile = File(tempDir, "legacy_no_routines.db")
        val driver = BundledSQLiteDriver()
        val conn = driver.open(legacyDbFile.absolutePath)
        try {
            val s1 = conn.prepare("CREATE TABLE users (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, balance INTEGER NOT NULL, language TEXT NOT NULL, secondaryCurrency TEXT NOT NULL, pin TEXT, userBarcode TEXT, userBarcodeNumber TEXT, isActive INTEGER NOT NULL, isDeleted INTEGER NOT NULL, avatarType TEXT NOT NULL, avatarEmoji TEXT NOT NULL, avatarColor TEXT NOT NULL)")
            s1.step(); s1.close()
            val s2 = conn.prepare("INSERT INTO users VALUES ('u-legacy', 'Legacy User', 2000, 'de', 'EUR', NULL, NULL, NULL, 1, 0, 'INITIALS', '😀', '#1E293B')")
            s2.step(); s2.close()
        } finally {
            conn.close()
        }

        // 3. Restore the legacy database
        service.restoreDbFromBackup(legacyDbFile)

        // 4. Assert: user was restored, AND active routine was NOT wiped!
        val users = db.userDao().getAllUsers()
        assertEquals(1, users.size)
        assertEquals("Legacy User", users.first().name)

        val routines = db.backupDao().getAllBackups()
        assertEquals(1, routines.size, "Active backup routines must be preserved when restoring legacy DB lacking backup_routines")
        assertEquals("active-r1", routines.first().id)
    }

    @Test
    fun testRestoreDbHandlesDuplicateBillingListUsersWithoutCrashing() = runTest {
        // Create a backup SQLite DB with users, billing_lists, and DUPLICATE billing_list_users (same listId and userId)
        val backupFile = File(tempDir, "dup_blu.db")
        val driver = BundledSQLiteDriver()
        val conn = driver.open(backupFile.absolutePath)
        try {
            val s1 = conn.prepare("CREATE TABLE users (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, balance INTEGER NOT NULL, language TEXT NOT NULL, secondaryCurrency TEXT NOT NULL, pin TEXT, userBarcode TEXT, userBarcodeNumber TEXT, isActive INTEGER NOT NULL, isDeleted INTEGER NOT NULL, avatarType TEXT NOT NULL, avatarEmoji TEXT NOT NULL, avatarColor TEXT NOT NULL)")
            s1.step(); s1.close()
            val s2 = conn.prepare("INSERT INTO users VALUES ('u-dup', 'Dup User', 100, 'de', 'EUR', NULL, NULL, NULL, 1, 0, 'INITIALS', '😀', '#1E293B')")
            s2.step(); s2.close()

            val s3 = conn.prepare("CREATE TABLE billing_lists (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, type TEXT NOT NULL, basePrice INTEGER, comment TEXT, isDeleted INTEGER NOT NULL, lastExecutionTime INTEGER)")
            s3.step(); s3.close()
            val s4 = conn.prepare("INSERT INTO billing_lists VALUES ('bl-1', 'List 1', 'USER', 500, NULL, 0, NULL)")
            s4.step(); s4.close()

            // In legacy DB, billing_list_users has two rows for same listId and userId with different IDs
            val s5 = conn.prepare("CREATE TABLE billing_list_users (id TEXT PRIMARY KEY NOT NULL, listId TEXT NOT NULL, userId TEXT NOT NULL, quantity INTEGER NOT NULL)")
            s5.step(); s5.close()
            val s6 = conn.prepare("INSERT INTO billing_list_users VALUES ('blu-1', 'bl-1', 'u-dup', 1)")
            s6.step(); s6.close()
            val s7 = conn.prepare("INSERT INTO billing_list_users VALUES ('blu-2', 'bl-1', 'u-dup', 2)")
            s7.step(); s7.close()
        } finally {
            conn.close()
        }

        // Restore should NOT throw SQLiteConstraintException and should deduplicate properly
        service.restoreDbFromBackup(backupFile)

        val restoredMembers = db.billingListDao().getAllBillingListUsers()
        assertEquals(1, restoredMembers.size, "Duplicate composite key entries must be filtered to a single entry")
        assertEquals("u-dup", restoredMembers.first().userId)
    }

    @Test
    fun testAdminNotesBackupRestoreAndWipeData() = runTest {
        val note = AdminNoteEntity(
            id = "note-test-1",
            message = "We ran out of Guarana",
            userId = "u-user-1",
            userNameSnapshot = "Bob",
            timestamp = 1700000000000L
        )
        db.adminNoteDao().insertNote(note)
        assertEquals(1, db.adminNoteDao().getAllNotes().size)

        val csvDir = File(tempDir, "csv_notes_test").apply { mkdirs() }
        val generatedFiles = service.performLocalBackup(csvDir, BackupFileType.CSV, BackupWriteMode.CREATE_NEW_FILE)
        val backupDir = generatedFiles.first()
        val notesCsv = File(backupDir, "admin_notes.csv")
        assertTrue(notesCsv.exists(), "admin_notes.csv must exist in CSV backup")
        val lines = notesCsv.readLines()
        assertTrue(lines.size >= 2)
        assertEquals("id;userId;userNameSnapshot;timestamp;message", lines[0].removePrefix(BackupRestoreService.UTF8_BOM))
        assertTrue(lines[1].contains("We ran out of Guarana"))

        // Wipe data
        service.wipeAllData()
        assertEquals(0, db.adminNoteDao().getAllNotes().size, "wipeAllData must clear admin notes")

        // Also test restoring from a DB backup containing admin_notes
        val backupDbFile = File(tempDir, "backup_with_notes.db")
        val driver = BundledSQLiteDriver()
        val conn = driver.open(backupDbFile.absolutePath)
        try {
            val s1 = conn.prepare("CREATE TABLE users (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, balance INTEGER NOT NULL, language TEXT NOT NULL, secondaryCurrency TEXT NOT NULL, pin TEXT, userBarcode TEXT, userBarcodeNumber TEXT, isActive INTEGER NOT NULL, isDeleted INTEGER NOT NULL, avatarType TEXT NOT NULL, avatarEmoji TEXT NOT NULL, avatarColor TEXT NOT NULL)")
            s1.step(); s1.close()
            val s2 = conn.prepare("INSERT INTO users VALUES ('u1', 'User 1', 100, 'en', 'USD', NULL, NULL, NULL, 1, 0, 'INITIALS', '😀', '#1E293B')")
            s2.step(); s2.close()

            val s3 = conn.prepare("CREATE TABLE admin_notes (id TEXT PRIMARY KEY NOT NULL, message TEXT NOT NULL, userId TEXT NOT NULL, userNameSnapshot TEXT NOT NULL, timestamp INTEGER NOT NULL)")
            s3.step(); s3.close()
            val s4 = conn.prepare("INSERT INTO admin_notes VALUES ('note-restored', 'Restored note message', 'u1', 'User 1', 1710000000000)")
            s4.step(); s4.close()
        } finally {
            conn.close()
        }

        service.restoreDbFromBackup(backupDbFile)
        val restoredNotes = db.adminNoteDao().getAllNotes()
        assertEquals(1, restoredNotes.size)
        assertEquals("note-restored", restoredNotes.first().id)
        assertEquals("Restored note message", restoredNotes.first().message)
        assertEquals("User 1", restoredNotes.first().userNameSnapshot)
    }

    @Test
    fun testAllCsvExportsUseSemicolonAndUtf8BomAndSupportRoundtrip() = runTest {
        // Seed product and user
        val product = ProductEntity(
            id = "p-excel",
            name = "Guaraná Antarctica, 350ml",
            barcodes = listOf(Barcode("7891234567890", "Can")),
            basePrice = 450L,
            unitType = "PIECE",
            stockQuantity = 20L,
            customMarkupPercent = null,
            isActive = true
        )
        db.productDao().insertOrUpdateProduct(product)

        val user = UserEntity(
            id = "u-excel",
            name = "José Silva",
            balance = 1250L,
            language = "pt-BR",
            secondaryCurrency = "NONE",
            pin = "secret_hash",
            userBarcode = "USR-JOS",
            userBarcodeNumber = "USR-JOS",
            isActive = true,
            isDeleted = false,
            avatarType = "EMOJI",
            avatarEmoji = "🚀",
            avatarColor = "#1E293B"
        )
        db.userDao().insertOrUpdateUser(user)

        val exportFolder = service.performCsvBackup(tempDir, BackupWriteMode.CREATE_NEW_FILE)
        assertTrue(exportFolder.exists() && exportFolder.isDirectory)

        val csvNames = listOf("products.csv", "users.csv", "transactions.csv", "billing_lists.csv", "admin_notes.csv")
        for (name in csvNames) {
            val file = File(exportFolder, name)
            assertTrue(file.exists(), "$name must exist")
            val text = file.readText()
            assertTrue(text.startsWith(BackupRestoreService.UTF8_BOM), "$name must start with UTF-8 BOM")
            val firstLine = text.removePrefix(BackupRestoreService.UTF8_BOM).lineSequence().first()
            assertTrue(firstLine.contains(";"), "$name header must contain semicolon delimiters")
            assertFalse(firstLine.contains(","), "$name header must not contain comma delimiters")
        }

        // Verify parseCsvRecords on products.csv
        val productsCsvFile = File(exportFolder, "products.csv")
        val parsedProducts = service.parseCsvRecords(productsCsvFile.readText())
        assertTrue(parsedProducts.size >= 2)
        assertEquals("id", parsedProducts[0][0])
        assertEquals("name", parsedProducts[0][1])
        assertEquals("Guaraná Antarctica, 350ml", parsedProducts[1][1])

        // Verify parseCsvRecords handles optional "sep=;" and "sep=," directives
        val csvWithSepSemi = "sep=;\n" +
                "id;name;basePrice\n" +
                "p1;Coffee;\"R$ 5,00\"\n"
        val parsedSemi = service.parseCsvRecords(csvWithSepSemi)
        assertEquals(2, parsedSemi.size)
        assertEquals(listOf("id", "name", "basePrice"), parsedSemi[0])
        assertEquals(listOf("p1", "Coffee", "R$ 5,00"), parsedSemi[1])

        val csvWithSepComma = "sep=,\n" +
                "id,name,basePrice\n" +
                "p2,Tea,3.00\n"
        val parsedComma = service.parseCsvRecords(csvWithSepComma)
        assertEquals(2, parsedComma.size)
        assertEquals(listOf("id", "name", "basePrice"), parsedComma[0])
        assertEquals(listOf("p2", "Tea", "3.00"), parsedComma[1])
    }

    @Test
    fun testWipeAllDataPreserveOptions() = runTest {
        // 1. Seed custom password, cloud settings, routine, and a user
        val customPassHash = de.joelneumann.lojinha.security.PasswordHasher.hash("custom-secret-pass")
        val customSettings = SettingsEntity(
            id = 1,
            adminPasswordHash = customPassHash,
            globalMarkupPercent = 10.0,
            usdExchangeRate = 0.20,
            eurExchangeRate = 0.18,
            inactivityTimeoutMinutes = 5,
            autoUpdateExchangeRates = false,
            lastExchangeRateUpdateTimestamp = 123456L,
            oneDriveRefreshToken = "secret-token",
            oneDriveAccountEmail = "admin@example.com"
        )
        db.settingsDao().insertOrUpdateSettings(customSettings)

        val routine = BackupEntity(
            id = "r-wipe-test",
            name = "Nightly Cloud",
            isEnabled = true,
            type = "TIMED",
            fileType = "DB",
            writeMode = "CREATE_NEW_FILE",
            scheduleConfig = de.joelneumann.lojinha.domain.model.BackupScheduleConfig.Timed("02:00"),
            backupLocationPath = "/cloud/backups",
            lastBackupTimestamp = null
        )
        db.backupDao().insertOrUpdateBackup(routine)

        val user = UserEntity("u-wipe", "Wipe User", 1000L, "pt", "NONE", null, null, null, true)
        db.userDao().insertOrUpdateUser(user)

        // 2. Wipe with preserveBoth = true
        service.wipeAllData(preserveBackupRoutines = true, preserveAdminPassword = true)

        // User should be wiped
        assertEquals(0, db.userDao().getAllUsers().size)

        // Routine and custom password must be preserved
        val routinesKept = db.backupDao().getAllBackups()
        assertEquals(1, routinesKept.size)
        assertEquals("r-wipe-test", routinesKept.first().id)

        val settingsKept = db.settingsDao().getSettings()
        assertNotNull(settingsKept)
        assertEquals(customPassHash, settingsKept.adminPasswordHash)
        assertEquals("secret-token", settingsKept.oneDriveRefreshToken)
        assertEquals("admin@example.com", settingsKept.oneDriveAccountEmail)

        // 3. Wipe with preserveBoth = false
        service.wipeAllData(preserveBackupRoutines = false, preserveAdminPassword = false)

        // Routine must now be wiped
        assertEquals(0, db.backupDao().getAllBackups().size)

        // Settings password must be reset to factory default 'admin' and token cleared
        val settingsWiped = db.settingsDao().getSettings()
        assertNotNull(settingsWiped)
        assertEquals(de.joelneumann.lojinha.security.PasswordHasher.hash("admin"), settingsWiped.adminPasswordHash)
        assertNull(settingsWiped.oneDriveRefreshToken)
        assertNull(settingsWiped.oneDriveAccountEmail)
    }

    @Test
    fun testRestoreDbPreserveOptions() = runTest {
        // 1. Seed active DB with routine A and active password
        val activePasswordHash = de.joelneumann.lojinha.security.PasswordHasher.hash("active-pass")
        db.settingsDao().insertOrUpdateSettings(
            SettingsEntity(
                id = 1,
                adminPasswordHash = activePasswordHash,
                globalMarkupPercent = 5.0,
                usdExchangeRate = 0.18,
                eurExchangeRate = 0.16,
                inactivityTimeoutMinutes = 3,
                autoUpdateExchangeRates = true,
                lastExchangeRateUpdateTimestamp = null
            )
        )
        val activeRoutine = BackupEntity(
            id = "r-active",
            name = "Active Routine A",
            isEnabled = true,
            type = "TIMED",
            fileType = "DB",
            writeMode = "CREATE_NEW_FILE",
            scheduleConfig = de.joelneumann.lojinha.domain.model.BackupScheduleConfig.Timed("01:00"),
            backupLocationPath = "/active/path",
            lastBackupTimestamp = null
        )
        db.backupDao().insertOrUpdateBackup(activeRoutine)

        // 2. Create a backup file that contains routine B and backup password
        val backupPasswordHash = de.joelneumann.lojinha.security.PasswordHasher.hash("old-backup-pass")
        val backupFile = File(tempDir, "test_preserve_backup.db")
        val driver = androidx.sqlite.driver.bundled.BundledSQLiteDriver()
        val conn = driver.open(backupFile.absolutePath)
        try {
            conn.prepare("CREATE TABLE settings (id INTEGER PRIMARY KEY NOT NULL, adminPasswordHash TEXT NOT NULL, globalMarkupPercent REAL NOT NULL, usdExchangeRate REAL NOT NULL, eurExchangeRate REAL NOT NULL, inactivityTimeoutMinutes INTEGER NOT NULL)").apply { step(); close() }
            conn.prepare("INSERT INTO settings VALUES (1, '$backupPasswordHash', 0.0, 0.18, 0.16, 3)").apply { step(); close() }

            conn.prepare("CREATE TABLE backup_routines (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, isEnabled INTEGER NOT NULL, type TEXT NOT NULL, fileType TEXT NOT NULL, writeMode TEXT NOT NULL, scheduleConfig TEXT NOT NULL, backupLocationPath TEXT NOT NULL, lastBackupTimestamp INTEGER)").apply { step(); close() }
            conn.prepare("INSERT INTO backup_routines VALUES ('r-backup-b', 'Backup Routine B', 1, 'TIMED', 'DB', 'CREATE_NEW_FILE', '03:00', '/backup/path', NULL)").apply { step(); close() }

            conn.prepare("CREATE TABLE users (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, balance INTEGER NOT NULL, language TEXT NOT NULL, secondaryCurrency TEXT NOT NULL, pin TEXT, userBarcode TEXT, userBarcodeNumber TEXT, isActive INTEGER NOT NULL, isDeleted INTEGER NOT NULL, avatarType TEXT NOT NULL, avatarEmoji TEXT NOT NULL, avatarColor TEXT NOT NULL)").apply { step(); close() }
            conn.prepare("INSERT INTO users VALUES ('u-restored', 'Restored User', 500, 'en', 'NONE', NULL, NULL, NULL, 1, 0, 'INITIALS', '😀', '#1E293B')").apply { step(); close() }
        } finally {
            conn.close()
        }

        // 3. Restore with preserveExistingRoutines = true AND preserveCurrentAdminPassword = true
        service.restoreDbFromBackup(backupFile, preserveExistingRoutines = true, preserveCurrentAdminPassword = true)

        // Assert user was restored
        assertEquals(1, db.userDao().getAllUsers().size)

        // Assert routine A was kept (not replaced by routine B)
        val routinesKept = db.backupDao().getAllBackups()
        assertEquals(1, routinesKept.size)
        assertEquals("r-active", routinesKept.first().id)

        // Assert active password was kept
        val settingsKept = db.settingsDao().getSettings()
        assertNotNull(settingsKept)
        assertEquals(activePasswordHash, settingsKept.adminPasswordHash)

        // 4. Restore with preserveExistingRoutines = false AND preserveCurrentAdminPassword = false
        service.restoreDbFromBackup(backupFile, preserveExistingRoutines = false, preserveCurrentAdminPassword = false)

        // Assert routine B was restored
        val routinesRestored = db.backupDao().getAllBackups()
        assertEquals(1, routinesRestored.size)
        assertEquals("r-backup-b", routinesRestored.first().id)

        // Assert backup password was applied
        val settingsRestored = db.settingsDao().getSettings()
        assertNotNull(settingsRestored)
        assertEquals(backupPasswordHash, settingsRestored.adminPasswordHash)
    }

    @Test
    fun testCsvImportCreatesSafetySnapshot() = runTest {
        val safetySnapshot = File(tempDir, "lojinha_room_pre_csv_import_safety.db")
        if (safetySnapshot.exists()) safetySnapshot.delete()

        // 1. Dry run should NOT create safety snapshot
        val dryRunCsv = File(tempDir, "dryrun.csv")
        dryRunCsv.writeText("name,basePrice\nCoffee,R$ 5,00\n")
        service.importProductsFromCsv(dryRunCsv, dryRun = true)
        assertFalse(safetySnapshot.exists(), "Dry run must NOT create safety snapshot")

        // 2. Real import should create safety snapshot
        service.importProductsFromCsv(dryRunCsv, dryRun = false)
        assertTrue(safetySnapshot.exists(), "Real product CSV import MUST create pre-import safety snapshot")

        // 3. Real user import should also create safety snapshot
        safetySnapshot.delete()
        val userCsv = File(tempDir, "users_import.csv")
        userCsv.writeText("name,balance\nNew User,R$ 10,00\n")
        service.importUsersFromCsv(userCsv, dryRun = false)
        assertTrue(safetySnapshot.exists(), "Real user CSV import MUST create pre-import safety snapshot")
    }

    @Test
    fun testRestoreDbRejectsNonLojinhaDatabaseWithoutWipingData() = runTest {
        // Seed active production user
        val activeUser = UserEntity(
            id = "u-prod-1",
            name = "Production User",
            balance = 5000L,
            language = "de",
            secondaryCurrency = "EUR",
            pin = null,
            userBarcode = null,
            userBarcodeNumber = null,
            isActive = true
        )
        db.userDao().insertOrUpdateUser(activeUser)
        assertEquals(1, db.userDao().getAllUsers().size)

        // Create an unrelated SQLite DB (e.g. from another app)
        val foreignDb = File(tempDir, "foreign_app.db")
        val driver = BundledSQLiteDriver()
        val conn = driver.open(foreignDb.absolutePath)
        try {
            val stmt = conn.prepare("CREATE TABLE foreign_notes (id INTEGER PRIMARY KEY, note TEXT)")
            stmt.step()
            stmt.close()
        } finally {
            conn.close()
        }

        // Attempting to restore foreign DB must fail with IllegalArgumentException
        var exceptionThrown = false
        try {
            service.restoreDbFromBackup(foreignDb)
        } catch (e: IllegalArgumentException) {
            exceptionThrown = true
            assertTrue(e.message?.contains("does not contain Lojinha tables") == true)
        }
        assertTrue(exceptionThrown, "Restoring foreign database must throw IllegalArgumentException")

        // Crucial assertion: Active production data was NOT wiped!
        val usersAfter = db.userDao().getAllUsers()
        assertEquals(1, usersAfter.size, "Active user data must remain untouched when restore is rejected")
        assertEquals("Production User", usersAfter.first().name)
    }

    @Test
    fun testRestoreDbPreservesOneDriveAndAutoBackupSettingsWhenPreserveExistingRoutinesIsTrue() = runTest {
        // Setup current settings with active OneDrive token and local backup path
        val currentSettings = SettingsEntity(
            id = 1,
            adminPasswordHash = "hash123",
            globalMarkupPercent = 10.0,
            usdExchangeRate = 0.20,
            eurExchangeRate = 0.18,
            inactivityTimeoutMinutes = 5,
            backupLocationPath = "/current/backup/path",
            autoBackupEnabled = true,
            autoBackupFormat = "DB",
            autoBackupScheduleType = "HOURLY",
            autoBackupTime = "03:00",
            autoBackupIntervalHours = 6,
            oneDriveClientId = "client-id-123",
            oneDriveRefreshToken = "active-onedrive-token",
            oneDriveAccountEmail = "user@live.com",
            oneDriveAccountName = "Live User",
            oneDriveDefaultFolder = "/CloudLojinha",
            oneDriveTenant = "consumers"
        )
        db.settingsDao().insertOrUpdateSettings(currentSettings)

        // Create backup database with user, settings lacking OneDrive, and backup routines
        val backupFile = File(tempDir, "backup_with_empty_onedrive.db")
        val driver = BundledSQLiteDriver()
        val conn = driver.open(backupFile.absolutePath)
        try {
            conn.prepare("CREATE TABLE users (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, balance INTEGER NOT NULL, language TEXT NOT NULL, secondaryCurrency TEXT NOT NULL, pin TEXT, userBarcode TEXT, userBarcodeNumber TEXT, isActive INTEGER NOT NULL, isDeleted INTEGER NOT NULL, avatarType TEXT NOT NULL, avatarEmoji TEXT NOT NULL, avatarColor TEXT NOT NULL)").apply { step(); close() }
            conn.prepare("INSERT INTO users VALUES ('u-from-backup', 'Restored User', 1000, 'en', 'NONE', NULL, NULL, NULL, 1, 0, 'INITIALS', '😀', '#1E293B')").apply { step(); close() }

            conn.prepare("CREATE TABLE settings (id INTEGER PRIMARY KEY NOT NULL, adminPasswordHash TEXT NOT NULL, globalMarkupPercent REAL NOT NULL, usdExchangeRate REAL NOT NULL, eurExchangeRate REAL NOT NULL, inactivityTimeoutMinutes INTEGER NOT NULL, backupLocationPath TEXT, autoBackupEnabled INTEGER, autoBackupFormat TEXT, autoBackupScheduleType TEXT, autoBackupTime TEXT, autoBackupIntervalHours INTEGER, lastBackupTimestamp INTEGER, oneDriveClientId TEXT, oneDriveRefreshToken TEXT, oneDriveAccountEmail TEXT, oneDriveAccountName TEXT, oneDriveDefaultFolder TEXT, oneDriveTenant TEXT, supportEmail TEXT, autoUpdateExchangeRates INTEGER, lastExchangeRateUpdateTimestamp INTEGER)").apply { step(); close() }
            conn.prepare("INSERT INTO settings VALUES (1, 'backupPasswordHash', 0.0, 0.18, 0.16, 3, '', 0, 'DB', 'DAILY', '02:00', 24, NULL, 'default-client', NULL, NULL, NULL, '/DefaultFolder', 'common', NULL, 1, NULL)").apply { step(); close() }
        } finally {
            conn.close()
        }

        // Restore with preserveExistingRoutines = true
        service.restoreDbFromBackup(backupFile, preserveExistingRoutines = true, preserveCurrentAdminPassword = true)

        val restoredSettings = db.settingsDao().getSettings()
        assertNotNull(restoredSettings)
        assertEquals("active-onedrive-token", restoredSettings.oneDriveRefreshToken, "Active OneDrive token must be preserved")
        assertEquals("user@live.com", restoredSettings.oneDriveAccountEmail, "Active OneDrive email must be preserved")
        assertEquals("/current/backup/path", restoredSettings.backupLocationPath, "Current backup path must be preserved")
        assertTrue(restoredSettings.autoBackupEnabled, "Current auto-backup flag must be preserved")
        assertEquals("/CloudLojinha", restoredSettings.oneDriveDefaultFolder, "Current OneDrive folder must be preserved")
    }

    @Test
    fun testFlexibleBooleanParsingInCsvImports() = runTest {
        // 1. Direct unit test of parseBooleanFlexible
        assertTrue(service.parseBooleanFlexible("TRUE") == true)
        assertTrue(service.parseBooleanFlexible("true") == true)
        assertTrue(service.parseBooleanFlexible("1") == true)
        assertTrue(service.parseBooleanFlexible("yes") == true)
        assertTrue(service.parseBooleanFlexible("ja") == true)
        assertTrue(service.parseBooleanFlexible("sim") == true)

        assertTrue(service.parseBooleanFlexible("FALSE") == false)
        assertTrue(service.parseBooleanFlexible("false") == false)
        assertTrue(service.parseBooleanFlexible("0") == false)
        assertTrue(service.parseBooleanFlexible("no") == false)
        assertTrue(service.parseBooleanFlexible("nein") == false)
        assertTrue(service.parseBooleanFlexible("não") == false)

        assertNull(service.parseBooleanFlexible("unknown"))

        // 2. CSV product import with uppercase FALSE
        val prodCsv = File(tempDir, "prod_bool.csv")
        prodCsv.writeText("name,basePrice,isActive\nInactiveItem,\"R$ 10,00\",FALSE\n")
        val prodRes = service.importProductsFromCsv(prodCsv, dryRun = false)
        assertEquals(1, prodRes.addedCount)
        val prod = db.productDao().getAllProducts().first { it.name == "InactiveItem" }
        assertFalse(prod.isActive, "Product with isActive=FALSE must be imported as inactive")

        // 3. CSV user import with uppercase TRUE for isDeleted
        val userCsv = File(tempDir, "user_bool.csv")
        userCsv.writeText("name,balance,isDeleted\nDeletedUser,\"R$ 0,00\",TRUE\n")
        val userRes = service.importUsersFromCsv(userCsv, dryRun = false)
        assertEquals(1, userRes.addedCount)
        val user = db.userDao().getAllUsers().first { it.name == "DeletedUser" }
        assertTrue(user.isDeleted, "User with isDeleted=TRUE must be imported as deleted")
    }

    @Test
    fun testUnitTypeParsingCaseInsensitivityAndWarnings() = runTest {
        val prodCsv = File(tempDir, "prod_units.csv")
        prodCsv.writeText(
            """
            name,basePrice,unitType
            Apple,"R$ 2,00",weight
            Banana,"R$ 1,50",kg
            Orange,"R$ 3,00",Piece
            Grape,"R$ 5,00",unrecognized_unit
            """.trimIndent()
        )
        val res = service.importProductsFromCsv(prodCsv, dryRun = false)
        assertEquals(4, res.addedCount)
        assertEquals(1, res.warnings.size)
        assertTrue(res.warnings.first().contains("Unrecognized unit type 'unrecognized_unit' defaulted to PIECE"))

        val apple = db.productDao().getAllProducts().first { it.name == "Apple" }
        assertEquals("WEIGHT", apple.unitType)

        val banana = db.productDao().getAllProducts().first { it.name == "Banana" }
        assertEquals("WEIGHT", banana.unitType)

        val orange = db.productDao().getAllProducts().first { it.name == "Orange" }
        assertEquals("PIECE", orange.unitType)

        val grape = db.productDao().getAllProducts().first { it.name == "Grape" }
        assertEquals("PIECE", grape.unitType)
    }

    @Test
    fun testBarcodeReassignmentWithinSameCsv() = runTest {
        // Setup existing product P1 with barcode 999888
        val existingProduct = ProductEntity(
            id = "p-1",
            name = "Old Owner",
            barcodes = listOf(Barcode("999888", "Main")),
            basePrice = 1000L,
            unitType = "PIECE",
            stockQuantity = 5L,
            customMarkupPercent = null,
            isActive = true
        )
        db.productDao().insertOrUpdateProduct(existingProduct)

        // CSV where P1 changes barcode to 111222, and P2 takes 999888
        val prodCsv = File(tempDir, "barcode_reassign.csv")
        prodCsv.writeText(
            """
            id,name,basePrice,barcodes
            p-1,Old Owner,"R$ 10,00",111222:NewBarcode
            p-2,New Owner,"R$ 20,00",999888:ReassignedBarcode
            """.trimIndent()
        )

        val res = service.importProductsFromCsv(prodCsv, dryRun = false)
        assertEquals(1, res.updatedCount)
        assertEquals(1, res.addedCount)
        assertEquals(0, res.strippedBarcodesCount, "Reassigned barcode must NOT be stripped")

        val p1 = db.productDao().getProductById("p-1")
        assertNotNull(p1)
        assertEquals("111222", p1.barcodes.first().code)

        val p2 = db.productDao().getProductById("p-2")
        assertNotNull(p2)
        assertEquals("999888", p2.barcodes.first().code)
    }

    @Test
    fun testBarcodeDescriptionNullByDefaultOnImport() = runTest {
        val prodCsv = File(tempDir, "barcode_nodesc.csv")
        prodCsv.writeText("name,basePrice,barcodes\nItemWithoutDesc,\"R$ 5,00\",777888:\n")

        val res = service.importProductsFromCsv(prodCsv, dryRun = false)
        assertEquals(1, res.addedCount)

        val prod = db.productDao().getAllProducts().first { it.name == "ItemWithoutDesc" }
        assertEquals("777888", prod.barcodes.first().code)
        assertNull(prod.barcodes.first().description, "Empty barcode description in CSV must import as null, not 'Barcode'")
    }

    @Test
    fun testDatabaseFactoryGetDatabaseFile() {
        val file = DatabaseFactory.getDatabaseFile()
        assertTrue(file.absolutePath.endsWith(".lojinha/lojinha_room.db"))
    }
}

