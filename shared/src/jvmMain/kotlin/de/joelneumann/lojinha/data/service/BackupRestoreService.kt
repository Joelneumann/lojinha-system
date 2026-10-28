package de.joelneumann.lojinha.data.service

import androidx.room.Room
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.joelneumann.lojinha.data.database.AppDatabase
import de.joelneumann.lojinha.data.database.Converters
import de.joelneumann.lojinha.data.entity.AdminNoteEntity
import de.joelneumann.lojinha.data.entity.BackupEntity
import de.joelneumann.lojinha.data.entity.BillingListEntity
import de.joelneumann.lojinha.data.entity.BillingListUserEntity
import de.joelneumann.lojinha.data.entity.ProductEntity
import de.joelneumann.lojinha.data.entity.ProductStockAdjustmentEntity
import de.joelneumann.lojinha.data.entity.SettingsEntity
import de.joelneumann.lojinha.data.entity.TransactionEntity
import de.joelneumann.lojinha.data.entity.UserEntity
import de.joelneumann.lojinha.domain.model.Barcode
import de.joelneumann.lojinha.domain.model.CsvImportResult
import de.joelneumann.lojinha.domain.model.Language
import de.joelneumann.lojinha.domain.model.Product
import de.joelneumann.lojinha.domain.model.SecondaryCurrency
import de.joelneumann.lojinha.domain.model.TransactionType
import de.joelneumann.lojinha.domain.model.UnitType
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.utils.Formatting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.math.round
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupRestoreService(
    private val db: AppDatabase,
    private val dbFile: File = de.joelneumann.lojinha.data.database.DatabaseFactory.getDatabaseFile()
) {

    private fun getTimestampString(): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        return sdf.format(Date())
    }

    fun parseBooleanFlexible(str: String): Boolean? = when (str.trim().lowercase()) {
        "true", "1", "yes", "sim", "ja" -> true
        "false", "0", "no", "não", "nao", "nein" -> false
        else -> null
    }

    suspend fun performDbSnapshot(targetFile: File): File = withContext(Dispatchers.IO) {
        if (!dbFile.exists()) {
            db.useWriterConnection { }
        }
        require(dbFile.exists()) { "Database file not found at ${dbFile.absolutePath}" }

        // SQLite VACUUM INTO requires target file to not exist prior to command execution
        if (targetFile.exists()) {
            targetFile.delete()
        }
        targetFile.parentFile?.mkdirs()

        // Use native VACUUM INTO for an atomic, crash-consistent live snapshot with full WAL integration
        val escapedPath = targetFile.absolutePath.replace("'", "''")
        val connection = BundledSQLiteDriver().open(dbFile.absolutePath)
        try {
            val busyStmt = connection.prepare("PRAGMA busy_timeout = 5000")
            try {
                busyStmt.step()
            } finally {
                busyStmt.close()
            }
            val stmt = connection.prepare("VACUUM INTO '$escapedPath'")
            try {
                stmt.step()
            } finally {
                stmt.close()
            }
        } finally {
            connection.close()
        }

        if (!targetFile.exists()) {
            throw IllegalStateException("Failed to create database snapshot at ${targetFile.absolutePath}")
        }
        targetFile
    }

    suspend fun performDbBackup(
        destinationDir: File,
        writeMode: de.joelneumann.lojinha.domain.model.BackupWriteMode = de.joelneumann.lojinha.domain.model.BackupWriteMode.CREATE_NEW_FILE
    ): File = withContext(Dispatchers.IO) {
        require(destinationDir.exists() && destinationDir.isDirectory) { "Destination directory does not exist or is not a directory: ${destinationDir.absolutePath}" }
        val backupFileName = if (writeMode == de.joelneumann.lojinha.domain.model.BackupWriteMode.OVERWRITE_LATEST) {
            "lojinha_backup_latest.db"
        } else {
            "lojinha_backup_${getTimestampString()}.db"
        }
        val targetFile = File(destinationDir, backupFileName)
        performDbSnapshot(targetFile)
    }

    suspend fun performLocalBackup(
        destinationDir: File,
        fileType: de.joelneumann.lojinha.domain.model.BackupFileType,
        writeMode: de.joelneumann.lojinha.domain.model.BackupWriteMode = de.joelneumann.lojinha.domain.model.BackupWriteMode.CREATE_NEW_FILE
    ): List<File> = withContext(Dispatchers.IO) {
        if (!destinationDir.exists()) {
            destinationDir.mkdirs()
        }
        require(destinationDir.exists() && destinationDir.isDirectory) { "Destination directory does not exist or is not a directory: ${destinationDir.absolutePath}" }
        when (fileType) {
            de.joelneumann.lojinha.domain.model.BackupFileType.DB -> listOf(performDbBackup(destinationDir, writeMode))
            de.joelneumann.lojinha.domain.model.BackupFileType.CSV -> listOf(performCsvBackup(destinationDir, writeMode))
            de.joelneumann.lojinha.domain.model.BackupFileType.BOTH -> {
                val dbResult = performDbBackup(destinationDir, writeMode)
                val csvResult = performCsvBackup(destinationDir, writeMode)
                listOf(dbResult, csvResult)
            }
        }
    }

    suspend fun executeRoutineBackup(routine: de.joelneumann.lojinha.domain.model.BackupRoutine): List<File> = withContext(Dispatchers.IO) {
        val dir = File(routine.backupLocationPath)
        performLocalBackup(dir, routine.fileType, routine.writeMode)
    }

    suspend fun performCsvBackup(
        destinationDir: File,
        writeMode: de.joelneumann.lojinha.domain.model.BackupWriteMode = de.joelneumann.lojinha.domain.model.BackupWriteMode.CREATE_NEW_FILE
    ): File = withContext(Dispatchers.IO) {
        require(destinationDir.exists() && destinationDir.isDirectory) { "Destination directory does not exist: ${destinationDir.absolutePath}" }
        val folderName = if (writeMode == de.joelneumann.lojinha.domain.model.BackupWriteMode.OVERWRITE_LATEST) {
            "lojinha_csv_export_latest"
        } else {
            "lojinha_csv_export_${getTimestampString()}"
        }
        val exportFolder = File(destinationDir, folderName)
        if (writeMode == de.joelneumann.lojinha.domain.model.BackupWriteMode.OVERWRITE_LATEST && exportFolder.exists()) {
            exportFolder.deleteRecursively()
        }
        exportFolder.mkdirs()

        val converters = Converters()

        // 1. Export Products
        val products = db.productDao().getAllProducts().map { it.toDomain() }
        val productsCsv = File(exportFolder, "products.csv")
        val productLines = mutableListOf("id;name;barcodes;basePrice;unitType;stockQuantity;customMarkupPercent;isActive")
        products.forEach { p ->
            val barcodeStr = p.barcodes.joinToString("|") { "${it.code}:${it.description ?: ""}" }
            val line = listOf(
                escapeCsv(p.id),
                escapeCsv(p.name),
                escapeCsv(barcodeStr),
                escapeCsv(Formatting.formatBrlCanonical(p.basePrice)),
                p.unitType.name,
                p.stockQuantity.toString(),
                p.customMarkupPercent?.toString() ?: "",
                p.isActive.toString()
            ).joinToString(CSV_DELIMITER)
            productLines.add(line)
        }
        writeCsvFile(productsCsv, productLines)

        // 2. Export Users
        val users = db.userDao().getAllUsers().map { it.toDomain() }
        val usersCsv = File(exportFolder, "users.csv")
        val userLines = mutableListOf("id;name;balance;language;secondaryCurrency;pin;userBarcode;userBarcodeNumber;isActive;isDeleted;avatarType;avatarEmoji;avatarColor")
        users.forEach { u ->
            val line = listOf(
                escapeCsv(u.id),
                escapeCsv(u.name),
                escapeCsv(Formatting.formatBrlCanonical(u.balance)),
                u.language.code,
                u.secondaryCurrency.name,
                escapeCsv(u.pin?.let { if (de.joelneumann.lojinha.security.PasswordHasher.isHash(it)) it else de.joelneumann.lojinha.security.PasswordHasher.hash(it) } ?: ""),
                escapeCsv(u.userBarcode ?: ""),
                escapeCsv(u.userBarcodeNumber ?: ""),
                u.isActive.toString(),
                u.isDeleted.toString(),
                escapeCsv(u.avatar.type.name),
                escapeCsv(u.avatar.emoji),
                escapeCsv(u.avatar.colorHex)
            ).joinToString(CSV_DELIMITER)
            userLines.add(line)
        }
        writeCsvFile(usersCsv, userLines)

        // 3. Export Transactions
        val transactions = db.transactionDao().getAllTransactions().map { it.toDomain() }
        val txCsv = File(exportFolder, "transactions.csv")
        val txLines = mutableListOf("id;userId;userNameSnapshot;timestamp;type;referenceTransactionId;note;totalAmount;itemCount;items;userBalanceBefore;userBalanceAfter")
        transactions.forEach { t ->
            val itemsSerialized = converters.fromTransactionItemList(t.items)
            val line = listOf(
                escapeCsv(t.id),
                escapeCsv(t.userId),
                escapeCsv(t.userNameSnapshot),
                t.timestamp.toString(),
                t.type.name,
                escapeCsv(t.referenceTransactionId ?: ""),
                escapeCsv(t.note ?: ""),
                escapeCsv(Formatting.formatBrlCanonical(t.totalAmount)),
                t.items.size.toString(),
                escapeCsv(itemsSerialized),
                t.userBalanceBefore?.let { escapeCsv(Formatting.formatBrlCanonical(it)) } ?: "",
                t.userBalanceAfter?.let { escapeCsv(Formatting.formatBrlCanonical(it)) } ?: ""
            ).joinToString(CSV_DELIMITER)
            txLines.add(line)
        }
        writeCsvFile(txCsv, txLines)

        // 4. Export Combined Billing Lists & Members (QA-05)
        val billingLists = db.billingListDao().getAllBillingLists()
        val billingListUsers = db.billingListDao().getAllBillingListUsers().groupBy { it.listId }
        val usersMap = users.associateBy { it.id }
        val blCsv = File(exportFolder, "billing_lists.csv")
        val blLines = mutableListOf("listId;listName;type;basePrice;comment;isDeleted;userId;userName;quantity")
        billingLists.forEach { bl ->
            val members = billingListUsers[bl.id]
            val priceStr = bl.basePrice?.let { Formatting.formatBrlCanonical(it) } ?: ""
            if (members.isNullOrEmpty()) {
                blLines.add(
                    listOf(
                        escapeCsv(bl.id),
                        escapeCsv(bl.name),
                        bl.type,
                        escapeCsv(priceStr),
                        escapeCsv(bl.comment ?: ""),
                        bl.isDeleted.toString(),
                        "",
                        "",
                        "0"
                    ).joinToString(CSV_DELIMITER)
                )
            } else {
                members.forEach { m ->
                    blLines.add(
                        listOf(
                            escapeCsv(bl.id),
                            escapeCsv(bl.name),
                            bl.type,
                            escapeCsv(priceStr),
                            escapeCsv(bl.comment ?: ""),
                            bl.isDeleted.toString(),
                            escapeCsv(m.userId),
                            escapeCsv(usersMap[m.userId]?.name ?: ""),
                            m.quantity.toString()
                        ).joinToString(CSV_DELIMITER)
                    )
                }
            }
        }
        writeCsvFile(blCsv, blLines)

        // 5. Export Admin Notes & Suggestions
        val notes = db.adminNoteDao().getAllNotes()
        val notesCsv = File(exportFolder, "admin_notes.csv")
        val noteLines = mutableListOf("id;userId;userNameSnapshot;timestamp;message")
        notes.forEach { n ->
            val line = listOf(
                escapeCsv(n.id),
                escapeCsv(n.userId),
                escapeCsv(n.userNameSnapshot),
                n.timestamp.toString(),
                escapeCsv(n.message)
            ).joinToString(CSV_DELIMITER)
            noteLines.add(line)
        }
        writeCsvFile(notesCsv, noteLines)

        // 6. Export Product Stock Adjustments
        val stockAdjustments = db.productStockAdjustmentDao().getAllAdjustments()
        val stockAdjCsv = File(exportFolder, "product_stock_adjustments.csv")
        val stockAdjLines = mutableListOf("id;productId;timestamp;deltaQuantity;resultingQuantity;note")
        stockAdjustments.forEach { sa ->
            val line = listOf(
                escapeCsv(sa.id),
                escapeCsv(sa.productId),
                sa.timestamp.toString(),
                sa.deltaQuantity.toString(),
                sa.resultingQuantity?.toString() ?: "",
                escapeCsv(sa.note ?: "")
            ).joinToString(CSV_DELIMITER)
            stockAdjLines.add(line)
        }
        writeCsvFile(stockAdjCsv, stockAdjLines)

        exportFolder
    }

    suspend fun restoreDbFromBackup(
        backupFile: File,
        preserveExistingRoutines: Boolean = false,
        preserveCurrentAdminPassword: Boolean = true
    ) = withContext(Dispatchers.IO) {
        require(backupFile.exists() && backupFile.isFile) { "Backup file does not exist: ${backupFile.absolutePath}" }

        val converters = Converters()
        val backupUsers = mutableListOf<UserEntity>()
        val backupProducts = mutableListOf<ProductEntity>()
        val backupTransactions = mutableListOf<TransactionEntity>()
        var backupSettings: SettingsEntity? = null
        val backupRoutines = mutableListOf<BackupEntity>()
        val backupBillingLists = mutableListOf<BillingListEntity>()
        val backupBillingListUsers = mutableListOf<BillingListUserEntity>()
        val backupNotes = mutableListOf<AdminNoteEntity>()
        val backupStockAdjustments = mutableListOf<ProductStockAdjustmentEntity>()
        val tableNames = mutableSetOf<String>()

        // 1. Read tables from backupFile using BundledSQLiteDriver (read-only C driver, cross-platform)
        val connection = BundledSQLiteDriver().open(backupFile.absolutePath)
        try {
            // Quick integrity check before processing
            val integrityStmt = connection.prepare("PRAGMA quick_check")
            try {
                if (integrityStmt.step()) {
                    val result = integrityStmt.getText(0)
                    if (result.lowercase() != "ok") {
                        throw IllegalStateException("Backup file failed integrity check: $result")
                    }
                }
            } finally {
                integrityStmt.close()
            }

            val stmtTables = connection.prepare("SELECT name FROM sqlite_master WHERE type='table'")
            try {
                while (stmtTables.step()) {
                    tableNames.add(stmtTables.getText(0).lowercase())
                }
            } finally {
                stmtTables.close()
            }

            // Guard: Ensure target file is a genuine Lojinha backup before proceeding
            val requiredTables = setOf("users", "products", "transactions", "settings")
            require(tableNames.any { it in requiredTables }) {
                "The selected database file does not contain Lojinha tables and cannot be restored."
            }

            // Read users
            if (tableNames.contains("users")) {
                val userColumns = mutableSetOf<String>()
                val stmtCols = connection.prepare("PRAGMA table_info(users)")
                try {
                    while (stmtCols.step()) {
                        userColumns.add(stmtCols.getText(1).lowercase())
                    }
                } finally {
                    stmtCols.close()
                }

                val hasIsDeleted = userColumns.contains("isdeleted")
                val hasAvatarType = userColumns.contains("avatartype")
                val hasAvatarEmoji = userColumns.contains("avataremoji")
                val hasAvatarColor = userColumns.contains("avatarcolor")

                val userQuery = buildString {
                    append("SELECT id, name, balance, language, secondaryCurrency, pin, userBarcode, userBarcodeNumber, isActive")
                    if (hasIsDeleted) append(", isDeleted") else append(", 0")
                    if (hasAvatarType) append(", avatarType") else append(", 'INITIALS'")
                    if (hasAvatarEmoji) append(", avatarEmoji") else append(", '😀'")
                    if (hasAvatarColor) append(", avatarColor") else append(", '#1E293B'")
                    append(" FROM users")
                }

                val stmt = connection.prepare(userQuery)
                try {
                    while (stmt.step()) {
                        val u = UserEntity(
                            id = stmt.getText(0),
                            name = stmt.getText(1),
                            balance = stmt.getLong(2),
                            language = stmt.getText(3),
                            secondaryCurrency = stmt.getText(4),
                            pin = if (stmt.isNull(5)) null else {
                                val raw = stmt.getText(5)
                                if (raw.isBlank()) null else if (de.joelneumann.lojinha.security.PasswordHasher.isHash(raw)) raw else de.joelneumann.lojinha.security.PasswordHasher.hash(raw.trim())
                            },
                            userBarcode = if (stmt.isNull(6)) null else stmt.getText(6),
                            userBarcodeNumber = if (stmt.isNull(7)) null else stmt.getText(7),
                            isActive = stmt.getLong(8) != 0L,
                            isDeleted = stmt.getLong(9) != 0L,
                            avatarType = if (stmt.isNull(10)) "INITIALS" else stmt.getText(10),
                            avatarEmoji = if (stmt.isNull(11)) "😀" else stmt.getText(11),
                            avatarColor = if (stmt.isNull(12)) "#1E293B" else stmt.getText(12)
                        )
                        backupUsers.add(u)
                    }
                } finally {
                    stmt.close()
                }
            }

            // Read products
            if (tableNames.contains("products")) {
                val productCols = mutableSetOf<String>()
                val stmtCols = connection.prepare("PRAGMA table_info(products)")
                try {
                    while (stmtCols.step()) {
                        productCols.add(stmtCols.getText(1).lowercase())
                    }
                } finally {
                    stmtCols.close()
                }

                val hasMarkup = productCols.contains("custommarkuppercent")
                val query = buildString {
                    append("SELECT id, name, barcodes, basePrice, unitType, stockQuantity, ")
                    append(if (hasMarkup) "customMarkupPercent" else "NULL")
                    append(", isActive FROM products")
                }

                val stmt = connection.prepare(query)
                try {
                    while (stmt.step()) {
                        val barcodesStr = if (stmt.isNull(2)) "" else stmt.getText(2)
                        val customMarkup = if (hasMarkup && !stmt.isNull(6)) stmt.getDouble(6) else null

                        val p = ProductEntity(
                            id = stmt.getText(0),
                            name = stmt.getText(1),
                            barcodes = converters.toBarcodeList(barcodesStr),
                            basePrice = stmt.getLong(3),
                            unitType = stmt.getText(4),
                            stockQuantity = stmt.getLong(5),
                            customMarkupPercent = customMarkup,
                            isActive = stmt.getLong(7) != 0L
                        )
                        backupProducts.add(p)
                    }
                } finally {
                    stmt.close()
                }
            }

            // Read transactions
            if (tableNames.contains("transactions")) {
                val txColumns = mutableSetOf<String>()
                val stmtCols = connection.prepare("PRAGMA table_info(transactions)")
                try {
                    while (stmtCols.step()) {
                        txColumns.add(stmtCols.getText(1).lowercase())
                    }
                } finally {
                    stmtCols.close()
                }

                val hasSnapshots = txColumns.contains("userbalancebefore") && txColumns.contains("userbalanceafter")
                val query = if (hasSnapshots) {
                    "SELECT id, userId, userNameSnapshot, timestamp, type, referenceTransactionId, note, totalAmount, items, userBalanceBefore, userBalanceAfter FROM transactions"
                } else {
                    "SELECT id, userId, userNameSnapshot, timestamp, type, referenceTransactionId, note, totalAmount, items FROM transactions"
                }

                val stmt = connection.prepare(query)
                try {
                    while (stmt.step()) {
                        val itemsStr = if (stmt.isNull(8)) "" else stmt.getText(8)
                        val balBefore = if (hasSnapshots && !stmt.isNull(9)) stmt.getLong(9) else null
                        val balAfter = if (hasSnapshots && !stmt.isNull(10)) stmt.getLong(10) else null

                        val t = TransactionEntity(
                            id = stmt.getText(0),
                            userId = stmt.getText(1),
                            userNameSnapshot = stmt.getText(2),
                            timestamp = stmt.getLong(3),
                            type = stmt.getText(4),
                            referenceTransactionId = if (stmt.isNull(5)) null else stmt.getText(5),
                            note = if (stmt.isNull(6)) null else stmt.getText(6),
                            totalAmount = stmt.getLong(7),
                            items = converters.toTransactionItemList(itemsStr),
                            userBalanceBefore = balBefore,
                            userBalanceAfter = balAfter
                        )
                        backupTransactions.add(t)
                    }
                } finally {
                    stmt.close()
                }
            }

            // Read settings
            if (tableNames.contains("settings")) {
                val settingsColumns = mutableSetOf<String>()
                val stmtCols = connection.prepare("PRAGMA table_info(settings)")
                try {
                    while (stmtCols.step()) {
                        settingsColumns.add(stmtCols.getText(1).lowercase())
                    }
                } finally {
                    stmtCols.close()
                }

                val settingsQuery = buildString {
                    append("SELECT id, adminPasswordHash, globalMarkupPercent, usdExchangeRate, eurExchangeRate, inactivityTimeoutMinutes")
                    append(if (settingsColumns.contains("backuplocationpath")) ", backupLocationPath" else ", ''")
                    append(if (settingsColumns.contains("autobackupenabled")) ", autoBackupEnabled" else ", 0")
                    append(if (settingsColumns.contains("autobackupformat")) ", autoBackupFormat" else ", 'DB'")
                    append(if (settingsColumns.contains("autobackupscheduletype")) ", autoBackupScheduleType" else ", 'DAILY'")
                    append(if (settingsColumns.contains("autobackuptime")) ", autoBackupTime" else ", '02:00'")
                    append(if (settingsColumns.contains("autobackupintervalhours")) ", autoBackupIntervalHours" else ", 24")
                    append(if (settingsColumns.contains("lastbackuptimestamp")) ", lastBackupTimestamp" else ", NULL")
                    append(if (settingsColumns.contains("onedriveclientid")) ", oneDriveClientId" else ", '202e1c94-b152-4751-b0e6-a2a4b8eb4901'")
                    append(if (settingsColumns.contains("onedriverefreshtoken")) ", oneDriveRefreshToken" else ", NULL")
                    append(if (settingsColumns.contains("onedriveaccountemail")) ", oneDriveAccountEmail" else ", NULL")
                    append(if (settingsColumns.contains("onedriveaccountname")) ", oneDriveAccountName" else ", NULL")
                    append(if (settingsColumns.contains("onedrivedefaultfolder")) ", oneDriveDefaultFolder" else ", '/LojinhaBackups'")
                    append(if (settingsColumns.contains("onedrivetenant")) ", oneDriveTenant" else ", 'common'")
                    append(if (settingsColumns.contains("supportemail")) ", supportEmail" else ", NULL")
                    append(if (settingsColumns.contains("autoupdateexchangerates")) ", autoUpdateExchangeRates" else ", 1")
                    append(if (settingsColumns.contains("lastexchangerateupdatetimestamp")) ", lastExchangeRateUpdateTimestamp" else ", NULL")
                    append(if (settingsColumns.contains("yellowbalancewarningthresholdcents")) ", yellowBalanceWarningThresholdCents" else ", 30000")
                    append(if (settingsColumns.contains("redbalancewarningthresholdcents")) ", redBalanceWarningThresholdCents" else ", 0")
                    append(" FROM settings LIMIT 1")
                }

                val stmt = connection.prepare(settingsQuery)
                try {
                    if (stmt.step()) {
                        backupSettings = SettingsEntity(
                            id = stmt.getLong(0).toInt(),
                            adminPasswordHash = {
                                val raw = stmt.getText(1)
                                if (de.joelneumann.lojinha.security.PasswordHasher.isHash(raw)) raw else de.joelneumann.lojinha.security.PasswordHasher.hash(raw)
                            }(),
                            globalMarkupPercent = stmt.getDouble(2),
                            usdExchangeRate = stmt.getDouble(3),
                            eurExchangeRate = stmt.getDouble(4),
                            inactivityTimeoutMinutes = stmt.getLong(5).toInt(),
                            backupLocationPath = if (stmt.isNull(6)) "" else stmt.getText(6),
                            autoBackupEnabled = stmt.getLong(7) != 0L,
                            autoBackupFormat = if (stmt.isNull(8)) "DB" else stmt.getText(8),
                            autoBackupScheduleType = if (stmt.isNull(9)) "DAILY" else stmt.getText(9),
                            autoBackupTime = if (stmt.isNull(10)) "02:00" else stmt.getText(10),
                            autoBackupIntervalHours = stmt.getLong(11).toInt(),
                            lastBackupTimestamp = if (stmt.isNull(12)) null else stmt.getLong(12),
                            oneDriveClientId = if (stmt.isNull(13)) "202e1c94-b152-4751-b0e6-a2a4b8eb4901" else stmt.getText(13),
                            oneDriveRefreshToken = if (stmt.isNull(14)) null else stmt.getText(14),
                            oneDriveAccountEmail = if (stmt.isNull(15)) null else stmt.getText(15),
                            oneDriveAccountName = if (stmt.isNull(16)) null else stmt.getText(16),
                            oneDriveDefaultFolder = if (stmt.isNull(17)) "/LojinhaBackups" else stmt.getText(17),
                            oneDriveTenant = if (stmt.isNull(18)) "common" else stmt.getText(18),
                            supportEmail = if (stmt.isNull(19)) null else stmt.getText(19),
                            autoUpdateExchangeRates = if (stmt.isNull(20)) true else stmt.getLong(20) != 0L,
                            lastExchangeRateUpdateTimestamp = if (stmt.isNull(21)) null else stmt.getLong(21),
                            yellowBalanceWarningThresholdCents = if (stmt.isNull(22)) 300_00L else stmt.getLong(22),
                            redBalanceWarningThresholdCents = if (stmt.isNull(23)) 0L else stmt.getLong(23)
                        )
                    }
                } finally {
                    stmt.close()
                }
            }

            // Read backup_routines
            if (tableNames.contains("backup_routines")) {
                val routineColumns = mutableSetOf<String>()
                val stmtCols = connection.prepare("PRAGMA table_info(backup_routines)")
                try {
                    while (stmtCols.step()) {
                        routineColumns.add(stmtCols.getText(1).lowercase())
                    }
                } finally {
                    stmtCols.close()
                }

                val hasWriteMode = routineColumns.contains("writemode")
                val routineQuery = buildString {
                    append("SELECT id, name, isEnabled, type, fileType, scheduleConfig, backupLocationPath, lastBackupTimestamp")
                    if (hasWriteMode) append(", writeMode") else append(", 'CREATE_NEW_FILE'")
                    append(" FROM backup_routines")
                }

                val stmt = connection.prepare(routineQuery)
                try {
                    while (stmt.step()) {
                        val cfgStr = if (stmt.isNull(5)) "" else stmt.getText(5)
                        val lastTs = if (stmt.isNull(7)) null else stmt.getLong(7)
                        val writeModeStr = if (stmt.isNull(8)) "CREATE_NEW_FILE" else stmt.getText(8)
                        val b = BackupEntity(
                            id = stmt.getText(0),
                            name = stmt.getText(1),
                            isEnabled = stmt.getLong(2) != 0L,
                            type = stmt.getText(3),
                            fileType = stmt.getText(4),
                            writeMode = writeModeStr,
                            scheduleConfig = converters.toBackupScheduleConfig(cfgStr),
                            backupLocationPath = stmt.getText(6),
                            lastBackupTimestamp = lastTs
                        )
                        backupRoutines.add(b)
                    }
                } finally {
                    stmt.close()
                }
            }

            // Read billing_lists
            if (tableNames.contains("billing_lists")) {
                val listCols = mutableSetOf<String>()
                val stmtCols = connection.prepare("PRAGMA table_info(billing_lists)")
                try {
                    while (stmtCols.step()) {
                        listCols.add(stmtCols.getText(1).lowercase())
                    }
                } finally {
                    stmtCols.close()
                }

                val hasComment = listCols.contains("comment")
                val hasIsDeleted = listCols.contains("isdeleted")
                val hasLastExecution = listCols.contains("lastexecutiontime")
                val listQuery = buildString {
                    append("SELECT id, name, type, basePrice")
                    if (hasComment) append(", comment") else append(", NULL")
                    if (hasIsDeleted) append(", isDeleted") else append(", 0")
                    if (hasLastExecution) append(", lastExecutionTime") else append(", NULL")
                    append(" FROM billing_lists")
                }

                val stmt = connection.prepare(listQuery)
                try {
                    while (stmt.step()) {
                        val basePrice = if (stmt.isNull(3)) null else stmt.getLong(3)
                        val comment = if (stmt.isNull(4)) null else stmt.getText(4)
                        val isDel = stmt.getLong(5) != 0L
                        val lastExecution = if (stmt.isNull(6)) null else stmt.getLong(6)
                        val bl = BillingListEntity(
                            id = stmt.getText(0),
                            name = stmt.getText(1),
                            type = stmt.getText(2),
                            basePrice = basePrice,
                            comment = comment,
                            isDeleted = isDel,
                            lastExecutionTime = lastExecution
                        )
                        backupBillingLists.add(bl)
                    }
                } finally {
                    stmt.close()
                }
            }

            // Read billing_list_users
            if (tableNames.contains("billing_list_users")) {
                val stmt = connection.prepare("SELECT id, listId, userId, quantity FROM billing_list_users")
                try {
                    while (stmt.step()) {
                        val blu = BillingListUserEntity(
                            id = stmt.getText(0),
                            listId = stmt.getText(1),
                            userId = stmt.getText(2),
                            quantity = stmt.getLong(3).toInt()
                        )
                        backupBillingListUsers.add(blu)
                    }
                } finally {
                    stmt.close()
                }
            }
            // Read admin_notes
            if (tableNames.contains("admin_notes")) {
                val stmt = connection.prepare("SELECT id, message, userId, userNameSnapshot, timestamp FROM admin_notes")
                try {
                    while (stmt.step()) {
                        val note = AdminNoteEntity(
                            id = stmt.getText(0),
                            message = stmt.getText(1),
                            userId = stmt.getText(2),
                            userNameSnapshot = stmt.getText(3),
                            timestamp = stmt.getLong(4)
                        )
                        backupNotes.add(note)
                    }
                } finally {
                    stmt.close()
                }
            }

            // Read product_stock_adjustments
            if (tableNames.contains("product_stock_adjustments")) {
                val stmt = connection.prepare("SELECT id, productId, timestamp, deltaQuantity, resultingQuantity, note FROM product_stock_adjustments")
                try {
                    while (stmt.step()) {
                        val adj = de.joelneumann.lojinha.data.entity.ProductStockAdjustmentEntity(
                            id = stmt.getText(0),
                            productId = stmt.getText(1),
                            timestamp = stmt.getLong(2),
                            deltaQuantity = stmt.getLong(3),
                            resultingQuantity = if (stmt.isNull(4)) null else stmt.getLong(4),
                            note = if (stmt.isNull(5)) null else stmt.getText(5)
                        )
                        backupStockAdjustments.add(adj)
                    }
                } finally {
                    stmt.close()
                }
            }
        } finally {
            connection.close()
        }

        // 2. Create pre-restore safety snapshot before touching active database
        val safetyFile = File(dbFile.parentFile ?: File(System.getProperty("user.home"), ".lojinha"), "lojinha_room_pre_restore_safety.db")
        if (dbFile.exists()) {
            performDbSnapshot(safetyFile)
        }

        // 3. Atomically clear and restore active database inside an immediate transaction
        val hasBackupRoutinesInBackup = tableNames.contains("backup_routines")
        val currentSettings = db.settingsDao().getSettings()
        withContext(NonCancellable) {
            db.useWriterConnection { transactor ->
                transactor.immediateTransaction {
                    // Clear tables in reverse dependency order (children first)
                    db.productStockAdjustmentDao().deleteAllAdjustments()
                    db.adminNoteDao().deleteAllNotes()
                    db.billingListDao().deleteAllBillingListUsers()
                    db.billingListDao().deleteAllBillingLists()
                    db.userDao().deleteAllUsers()
                    db.productDao().deleteAllProducts()
                    db.transactionDao().deleteAllTransactions()
                    if (hasBackupRoutinesInBackup && !preserveExistingRoutines) {
                        db.backupDao().deleteAllBackups()
                    }

                    // Insert extracted backup records (parents first)
                    backupUsers.forEach { db.userDao().insertOrUpdateUser(it) }
                    backupProducts.forEach { db.productDao().insertOrUpdateProduct(it) }
                    backupTransactions.forEach { db.transactionDao().insertTransaction(it) }
                    backupBillingLists.forEach { db.billingListDao().insertOrUpdateBillingList(it) }
                    val validUserIds = backupUsers.map { it.id }.toSet()
                    val validListIds = backupBillingLists.map { it.id }.toSet()
                    backupBillingListUsers
                        .filter { it.userId in validUserIds && it.listId in validListIds }
                        .distinctBy { it.listId to it.userId }
                        .forEach { db.billingListDao().insertBillingListUser(it) }
                    backupNotes.forEach { db.adminNoteDao().insertNote(it) }
                    backupStockAdjustments.forEach { db.productStockAdjustmentDao().insertAdjustment(it) }

                    if (hasBackupRoutinesInBackup && !preserveExistingRoutines) {
                        backupRoutines.forEach { db.backupDao().insertOrUpdateBackup(it) }
                    }
                    if (backupSettings != null) {
                        val finalPassword = if (preserveCurrentAdminPassword && currentSettings != null) {
                            currentSettings.adminPasswordHash
                        } else {
                            backupSettings.adminPasswordHash
                        }
                        val finalSettings = if (preserveExistingRoutines && currentSettings != null) {
                            backupSettings.copy(
                                adminPasswordHash = finalPassword,
                                backupLocationPath = currentSettings.backupLocationPath,
                                autoBackupEnabled = currentSettings.autoBackupEnabled,
                                autoBackupFormat = currentSettings.autoBackupFormat,
                                autoBackupScheduleType = currentSettings.autoBackupScheduleType,
                                autoBackupTime = currentSettings.autoBackupTime,
                                autoBackupIntervalHours = currentSettings.autoBackupIntervalHours,
                                oneDriveClientId = currentSettings.oneDriveClientId,
                                oneDriveRefreshToken = currentSettings.oneDriveRefreshToken,
                                oneDriveAccountEmail = currentSettings.oneDriveAccountEmail,
                                oneDriveAccountName = currentSettings.oneDriveAccountName,
                                oneDriveDefaultFolder = currentSettings.oneDriveDefaultFolder,
                                oneDriveTenant = currentSettings.oneDriveTenant
                            )
                        } else {
                            backupSettings.copy(adminPasswordHash = finalPassword)
                        }
                        db.settingsDao().insertOrUpdateSettings(finalSettings)
                    }
                }
            }
        }
    }

    suspend fun wipeAllData(
        preserveBackupRoutines: Boolean = true,
        preserveAdminPassword: Boolean = true
    ) = withContext(Dispatchers.IO) {
        val safetyFile = File(dbFile.parentFile ?: File(System.getProperty("user.home"), ".lojinha"), "lojinha_room_pre_wipe_safety.db")
        if (dbFile.exists()) {
            performDbSnapshot(safetyFile)
        }

        val existingSettings = db.settingsDao().getSettings()
        val finalPasswordHash = if (preserveAdminPassword && existingSettings != null) {
            existingSettings.adminPasswordHash
        } else {
            de.joelneumann.lojinha.security.PasswordHasher.hash("admin")
        }

        withContext(NonCancellable) {
            db.useWriterConnection { transactor ->
                transactor.immediateTransaction {
                    db.adminNoteDao().deleteAllNotes()
                    db.billingListDao().deleteAllBillingListUsers()
                    db.billingListDao().deleteAllBillingLists()
                    db.userDao().deleteAllUsers()
                    db.productDao().deleteAllProducts()
                    db.transactionDao().deleteAllTransactions()

                    if (!preserveBackupRoutines) {
                        db.backupDao().deleteAllBackups()
                    }

                    // Reset settings to initial defaults
                    val defaultSettings = SettingsEntity(
                        id = 1,
                        adminPasswordHash = finalPasswordHash,
                        globalMarkupPercent = 0.0,
                        usdExchangeRate = 0.18,
                        eurExchangeRate = 0.16,
                        inactivityTimeoutMinutes = 3,
                        autoUpdateExchangeRates = true,
                        lastExchangeRateUpdateTimestamp = null,
                        yellowBalanceWarningThresholdCents = 300_00L,
                        redBalanceWarningThresholdCents = 0L,
                        backupLocationPath = if (preserveBackupRoutines) existingSettings?.backupLocationPath ?: "" else "",
                        autoBackupEnabled = if (preserveBackupRoutines) existingSettings?.autoBackupEnabled ?: false else false,
                        autoBackupFormat = if (preserveBackupRoutines) existingSettings?.autoBackupFormat ?: "DB" else "DB",
                        autoBackupScheduleType = if (preserveBackupRoutines) existingSettings?.autoBackupScheduleType ?: "DAILY" else "DAILY",
                        autoBackupTime = if (preserveBackupRoutines) existingSettings?.autoBackupTime ?: "02:00" else "02:00",
                        autoBackupIntervalHours = if (preserveBackupRoutines) existingSettings?.autoBackupIntervalHours ?: 24 else 24,
                        oneDriveClientId = existingSettings?.oneDriveClientId ?: "202e1c94-b152-4751-b0e6-a2a4b8eb4901",
                        oneDriveRefreshToken = if (preserveBackupRoutines) existingSettings?.oneDriveRefreshToken else null,
                        oneDriveAccountEmail = if (preserveBackupRoutines) existingSettings?.oneDriveAccountEmail else null,
                        oneDriveAccountName = if (preserveBackupRoutines) existingSettings?.oneDriveAccountName else null,
                        oneDriveDefaultFolder = if (preserveBackupRoutines) existingSettings?.oneDriveDefaultFolder ?: "/LojinhaBackups" else "/LojinhaBackups",
                        oneDriveTenant = existingSettings?.oneDriveTenant ?: "common",
                        supportEmail = existingSettings?.supportEmail
                    )
                    db.settingsDao().insertOrUpdateSettings(defaultSettings)
                }
            }
        }
    }

    suspend fun importProductsFromCsv(csvFile: File, dryRun: Boolean = false): CsvImportResult = withContext(Dispatchers.IO) {
        require(csvFile.exists() && csvFile.isFile) { "Product CSV file does not exist: ${csvFile.absolutePath}" }
        val records = parseCsvRecords(csvFile.readText())
        if (records.isEmpty()) {
            return@withContext CsvImportResult(0, 0, 0, 0, errors = listOf(I18n.get().csvEmptyError))
        }

        val header = records[0].map { it.trim().removePrefix("\uFEFF") }
        fun findHeaderIndex(vararg candidates: String): Int =
            header.indexOfFirst { col -> candidates.any { it.equals(col, ignoreCase = true) } }

        val idIdx = findHeaderIndex("id", "productId")
        val nameIdx = findHeaderIndex("name", "productName", "title")
        val barcodesIdx = findHeaderIndex("barcodes", "barcode")
        val priceIdx = findHeaderIndex("basePrice", "price", "base_price")
        val unitIdx = findHeaderIndex("unitType", "unit", "unit_type")
        val stockIdx = findHeaderIndex("stockQuantity", "stock", "quantity", "stock_quantity")
        val markupIdx = findHeaderIndex("customMarkupPercent", "markup", "custom_markup_percent")
        val activeIdx = findHeaderIndex("isActive", "active", "is_active")

        if (nameIdx == -1 || priceIdx == -1) {
            return@withContext CsvImportResult(0, 0, 0, 0, errors = listOf(I18n.get().csvMissingHeadersError))
        }

        val existingProducts = db.productDao().getAllProducts().associateBy { it.id }
        val allExistingBarcodesMap = mutableMapOf<String, String>() // barcode string -> product ID
        existingProducts.values.forEach { ep ->
            ep.barcodes.forEach { b ->
                allExistingBarcodesMap[b.code] = ep.id
            }
        }

        var addedCount = 0
        var updatedCount = 0
        var strippedBarcodesCount = 0
        val warnings = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val productsToInsert = mutableListOf<ProductEntity>()

        for (i in 1 until records.size) {
            val cols = records[i]
            if (cols.size <= maxOf(nameIdx, priceIdx)) {
                warnings.add("Row ${i + 1}: Skipped due to missing columns.")
                continue
            }

            val rawId = if (idIdx != -1 && idIdx < cols.size) cols[idIdx].trim() else ""
            val name = cols[nameIdx].trim()
            if (name.isBlank()) {
                warnings.add("Row ${i + 1}: Skipped product because name is blank.")
                continue
            }
            val priceStr = cols[priceIdx].trim()
            val parsedPrice = parseCurrencyToCents(priceStr)
            if (parsedPrice == null) {
                warnings.add("Row ${i + 1} ('$name'): Invalid price '$priceStr'. Row skipped.")
                continue
            }
            val price = if (parsedPrice < 0) {
                warnings.add("Row ${i + 1} ('$name'): Negative price '$priceStr' was adjusted to 0,00.")
                0L
            } else {
                parsedPrice
            }

            val productId = if (rawId.isNotEmpty()) rawId else "p-imp-${getTimestampString()}-$i"
            val existing = existingProducts[productId]
            val isExisting = existing != null

            val unitTypeStr = if (unitIdx != -1 && unitIdx < cols.size) cols[unitIdx].trim() else ""
            val matchedUnit = if (unitTypeStr.isNotEmpty()) {
                UnitType.entries.firstOrNull { it.name.equals(unitTypeStr, ignoreCase = true) }
                    ?: when (unitTypeStr.lowercase()) {
                        "kg", "g", "gram", "kilo", "weight" -> UnitType.WEIGHT
                        "un", "pc", "stk", "piece" -> UnitType.PIECE
                        else -> null
                    }
            } else {
                null
            }
            val unitType = matchedUnit ?: existing?.unitType?.let { runCatching { UnitType.valueOf(it) }.getOrNull() } ?: run {
                if (unitTypeStr.isNotEmpty()) {
                    warnings.add("Row ${i + 1} ('$name'): Unrecognized unit type '$unitTypeStr' defaulted to PIECE.")
                }
                UnitType.PIECE
            }

            val stockStr = if (stockIdx != -1 && stockIdx < cols.size) cols[stockIdx].trim() else ""
            val stock = if (stockStr.isNotEmpty()) {
                val parsedStock = stockStr.toLongOrNull()
                if (parsedStock != null && parsedStock < 0) {
                    warnings.add("Row ${i + 1} ('$name'): Negative stock '$stockStr' was adjusted to 0.")
                    0L
                } else {
                    parsedStock ?: existing?.stockQuantity ?: 0L
                }
            } else {
                existing?.stockQuantity ?: 0L
            }

            val markupStr = if (markupIdx != -1 && markupIdx < cols.size) cols[markupIdx].trim() else ""
            val parsedMarkup = if (markupStr.isNotEmpty()) Formatting.parsePercentageInput(markupStr) else null
            val markup = if (parsedMarkup != null && parsedMarkup in 0.0..1000.0) {
                parsedMarkup
            } else if (markupStr.isNotEmpty()) {
                warnings.add("Row ${i + 1} ('$name'): Invalid custom markup '$markupStr' ignored.")
                existing?.customMarkupPercent
            } else {
                existing?.customMarkupPercent
            }

            val activeStr = if (activeIdx != -1 && activeIdx < cols.size) cols[activeIdx].trim() else ""
            val isActive = if (activeStr.isNotEmpty()) {
                parseBooleanFlexible(activeStr) ?: existing?.isActive ?: true
            } else {
                existing?.isActive ?: true
            }

            // Parse Barcodes
            val rawBarcodesStr = if (barcodesIdx != -1 && barcodesIdx < cols.size) cols[barcodesIdx].trim() else ""
            val parsedBarcodes = mutableListOf<Barcode>()
            if (isExisting && barcodesIdx != -1) {
                // Evict previously assigned barcodes of this product so reassignment within this CSV works
                allExistingBarcodesMap.entries.removeAll { it.value == productId }
            }

            if (rawBarcodesStr.isNotEmpty()) {
                val parts = rawBarcodesStr.split("|")
                parts.forEach { part ->
                    val pair = part.split(":", limit = 2)
                    val code = pair[0].trim()
                    val label = if (pair.size > 1 && pair[1].trim().isNotEmpty()) pair[1].trim() else null

                    if (code.isNotEmpty()) {
                        val assignedProductId = allExistingBarcodesMap[code]
                        if (assignedProductId != null && assignedProductId != productId) {
                            strippedBarcodesCount++
                            warnings.add("Row ${i + 1} ('$name'): Barcode '$code' stripped because it is already assigned to product '$assignedProductId'.")
                        } else {
                            if (parsedBarcodes.none { it.code == code }) {
                                parsedBarcodes.add(Barcode(code, label))
                                allExistingBarcodesMap[code] = productId
                            }
                        }
                    }
                }
            } else if (isExisting && barcodesIdx == -1) {
                // If barcodes column was omitted from CSV, preserve existing barcodes
                parsedBarcodes.addAll(existing!!.barcodes)
            }

            val productEntity = ProductEntity(
                id = productId,
                name = name,
                barcodes = parsedBarcodes,
                basePrice = price,
                unitType = unitType.name,
                stockQuantity = stock,
                customMarkupPercent = markup,
                isActive = isActive
            )

            productsToInsert.add(productEntity)
            if (isExisting) updatedCount++ else addedCount++
        }

        if (!dryRun && productsToInsert.isNotEmpty()) {
            val safetyFile = File(dbFile.parentFile ?: File(System.getProperty("user.home"), ".lojinha"), "lojinha_room_pre_csv_import_safety.db")
            if (dbFile.exists()) {
                performDbSnapshot(safetyFile)
            }
            db.useWriterConnection { transactor ->
                transactor.immediateTransaction {
                    productsToInsert.forEach { db.productDao().insertOrUpdateProduct(it) }
                }
            }
        }

        CsvImportResult(
            totalProcessed = addedCount + updatedCount,
            addedCount = addedCount,
            updatedCount = updatedCount,
            strippedBarcodesCount = strippedBarcodesCount,
            errors = errors,
            warnings = warnings
        )
    }

    suspend fun isDatabaseEmpty(): Boolean = withContext(Dispatchers.IO) {
        val activeUsers = db.userDao().getAllUsers().count { !it.isDeleted }
        val activeProducts = db.productDao().getAllProducts().count { it.isActive }
        val transactions = db.transactionDao().getAllTransactions().size
        activeUsers == 0 && activeProducts == 0 && transactions == 0
    }

    fun parseCurrencyToCents(input: String): Long? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        var isNegative = false
        var s = trimmed
        if (s.startsWith("(") && s.endsWith(")")) {
            isNegative = true
            s = s.substring(1, s.length - 1).trim()
        }
        if (s.contains("-")) {
            isNegative = true
            s = s.replace("-", "").trim()
        }
        if (s.startsWith("+")) {
            s = s.removePrefix("+").trim()
        }

        s = s.replace("R$", "", ignoreCase = true)
            .replace("BRL", "", ignoreCase = true)
            .replace("$", "")
            .replace("€", "")
            .replace("\u00A0", " ")
            .replace(" ", "")

        if (s.isEmpty()) return null

        val lastDot = s.lastIndexOf('.')
        val lastComma = s.lastIndexOf(',')

        val normalized: String
        if (lastDot != -1 && lastComma != -1) {
            normalized = if (lastComma > lastDot) {
                s.replace(".", "").replace(",", ".")
            } else {
                s.replace(",", "")
            }
        } else if (lastComma != -1) {
            val commaCount = s.count { it == ',' }
            normalized = if (commaCount > 1) {
                s.replace(",", "")
            } else {
                s.replace(",", ".")
            }
        } else if (lastDot != -1) {
            val dotCount = s.count { it == '.' }
            normalized = if (dotCount > 1) {
                s.replace(".", "")
            } else {
                s
            }
        } else {
            normalized = s
        }

        val doubleVal = normalized.toDoubleOrNull() ?: return null
        if (!doubleVal.isFinite()) return null

        val cents = round(doubleVal * 100.0).toLong()
        return if (isNegative) -cents else cents
    }

    suspend fun importUsersFromCsv(csvFile: File, dryRun: Boolean = false): CsvImportResult = withContext(Dispatchers.IO) {
        require(csvFile.exists() && csvFile.isFile) { "User CSV file does not exist: ${csvFile.absolutePath}" }
        val records = parseCsvRecords(csvFile.readText())
        if (records.isEmpty()) {
            return@withContext CsvImportResult(0, 0, 0, 0, errors = listOf(I18n.get().csvEmptyError))
        }

        val header = records[0].map { it.trim().removePrefix("\uFEFF") }
        fun findHeaderIndex(vararg candidates: String): Int =
            header.indexOfFirst { col -> candidates.any { it.equals(col, ignoreCase = true) } }

        val idIdx = findHeaderIndex("id", "userId", "user_id")
        val nameIdx = findHeaderIndex("name", "userName", "user_name")
        val balanceIdx = findHeaderIndex("balance", "kontostand", "saldo")
        val langIdx = findHeaderIndex("language", "lang", "sprache")
        val secCurrIdx = findHeaderIndex("secondaryCurrency", "secondary_currency", "secCurr")
        val pinIdx = findHeaderIndex("pin", "password")
        val userBarcodeIdx = findHeaderIndex("userBarcode", "user_barcode", "barcode")
        val userBarcodeNumberIdx = findHeaderIndex("userBarcodeNumber", "user_barcode_number", "barcodeNumber", "barcode_number")
        val activeIdx = findHeaderIndex("isActive", "active", "is_active")
        val isDeletedIdx = findHeaderIndex("isDeleted", "deleted", "is_deleted")
        val avatarTypeIdx = findHeaderIndex("avatarType", "avatar_type")
        val avatarEmojiIdx = findHeaderIndex("avatarEmoji", "avatar_emoji", "emoji")
        val avatarColorIdx = findHeaderIndex("avatarColor", "avatar_color", "color")

        if (nameIdx == -1) {
            return@withContext CsvImportResult(0, 0, 0, 0, errors = listOf(I18n.get().csvMissingNameHeaderError))
        }

        val existingUsers = db.userDao().getAllUsers().associateBy { it.id }
        val allExistingUserBarcodesMap = mutableMapOf<String, String>() // barcode string -> userId
        existingUsers.values.forEach { eu ->
            eu.userBarcode?.let { if (it.isNotBlank()) allExistingUserBarcodesMap[it] = eu.id }
            eu.userBarcodeNumber?.let { if (it.isNotBlank()) allExistingUserBarcodesMap[it] = eu.id }
        }

        var addedCount = 0
        var updatedCount = 0
        var strippedBarcodesCount = 0
        val warnings = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val usersToInsert = mutableListOf<UserEntity>()
        val transactionsToInsert = mutableListOf<TransactionEntity>()
        val importTimestamp = System.currentTimeMillis()

        for (i in 1 until records.size) {
            val cols = records[i]
            if (cols.size <= nameIdx) {
                warnings.add("Row ${i + 1}: Skipped due to missing columns.")
                continue
            }

            val rawId = if (idIdx != -1 && idIdx < cols.size) cols[idIdx].trim() else ""
            val name = cols[nameIdx].trim()
            if (name.isBlank()) {
                warnings.add("Row ${i + 1}: Skipped user because name is blank.")
                continue
            }

            val userId = if (rawId.isNotEmpty()) rawId else "u-imp-${getTimestampString()}-$i"
            val existing = existingUsers[userId]
            val isExisting = existing != null

            // Balance parsing
            val balanceStr = if (balanceIdx != -1 && balanceIdx < cols.size) cols[balanceIdx].trim() else ""
            val targetBalance = if (balanceStr.isNotEmpty()) {
                val parsed = parseCurrencyToCents(balanceStr)
                if (parsed != null) {
                    parsed
                } else {
                    warnings.add("Row ${i + 1} ('$name'): Invalid balance '$balanceStr'. Kept existing balance or 0.")
                    existing?.balance ?: 0L
                }
            } else {
                existing?.balance ?: 0L
            }

            val balBefore = existing?.balance ?: 0L
            val delta = targetBalance - balBefore
            if (delta != 0L) {
                val isDeposit = delta > 0
                val txType = if (isDeposit) TransactionType.ADMIN_DEPOSIT else TransactionType.ADMIN_WITHDRAWAL
                val tx = TransactionEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    userId = userId,
                    userNameSnapshot = name,
                    timestamp = importTimestamp + i,
                    type = txType.name,
                    referenceTransactionId = null,
                    note = if (isDeposit) "SYSNOTE|ADMIN_DEPOSIT" else "SYSNOTE|ADMIN_DEBIT",
                    totalAmount = delta,
                    items = emptyList(),
                    userBalanceBefore = balBefore,
                    userBalanceAfter = targetBalance
                )
                transactionsToInsert.add(tx)
            }

            val langStr = if (langIdx != -1 && langIdx < cols.size && cols[langIdx].trim().isNotEmpty()) {
                Language.fromCode(cols[langIdx].trim()).code
            } else {
                existing?.language ?: Language.DE.code
            }

            val secCurrStr = if (secCurrIdx != -1 && secCurrIdx < cols.size && cols[secCurrIdx].trim().isNotEmpty()) {
                val raw = cols[secCurrIdx].trim()
                SecondaryCurrency.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }?.name
                    ?: existing?.secondaryCurrency ?: SecondaryCurrency.NONE.name
            } else {
                existing?.secondaryCurrency ?: SecondaryCurrency.NONE.name
            }

            val rawPin = if (pinIdx != -1 && pinIdx < cols.size && cols[pinIdx].trim().isNotEmpty()) cols[pinIdx].trim() else null
            val pin = rawPin?.let {
                if (de.joelneumann.lojinha.security.PasswordHasher.isHash(it)) it else de.joelneumann.lojinha.security.PasswordHasher.hash(it)
            } ?: existing?.pin

            val activeStr = if (activeIdx != -1 && activeIdx < cols.size && cols[activeIdx].trim().isNotEmpty()) cols[activeIdx].trim() else ""
            val isActive = if (activeStr.isNotEmpty()) {
                parseBooleanFlexible(activeStr) ?: existing?.isActive ?: true
            } else {
                existing?.isActive ?: true
            }

            val isDeletedStr = if (isDeletedIdx != -1 && isDeletedIdx < cols.size && cols[isDeletedIdx].trim().isNotEmpty()) cols[isDeletedIdx].trim() else ""
            val isDeleted = if (isDeletedStr.isNotEmpty()) {
                parseBooleanFlexible(isDeletedStr) ?: existing?.isDeleted ?: false
            } else {
                existing?.isDeleted ?: false
            }

            val avatarTypeStr = if (avatarTypeIdx != -1 && avatarTypeIdx < cols.size && cols[avatarTypeIdx].trim().isNotEmpty()) {
                cols[avatarTypeIdx].trim()
            } else {
                existing?.avatarType ?: "INITIALS"
            }

            val avatarEmojiStr = if (avatarEmojiIdx != -1 && avatarEmojiIdx < cols.size && cols[avatarEmojiIdx].trim().isNotEmpty()) {
                cols[avatarEmojiIdx].trim()
            } else {
                existing?.avatarEmoji ?: "😀"
            }

            val avatarColorStr = if (avatarColorIdx != -1 && avatarColorIdx < cols.size && cols[avatarColorIdx].trim().isNotEmpty()) {
                cols[avatarColorIdx].trim()
            } else {
                existing?.avatarColor ?: "#1E293B"
            }

            val rawUserBarcode = if (userBarcodeIdx != -1 && userBarcodeIdx < cols.size) cols[userBarcodeIdx].trim() else ""
            val rawUserBarcodeNumber = if (userBarcodeNumberIdx != -1 && userBarcodeNumberIdx < cols.size) cols[userBarcodeNumberIdx].trim() else ""

            var finalUserBarcode: String? = null
            var finalUserBarcodeNumber: String? = null

            if (userBarcodeIdx == -1 && userBarcodeNumberIdx == -1 && isExisting) {
                // If barcode columns were omitted from CSV, preserve existing barcodes
                finalUserBarcode = existing?.userBarcode
                finalUserBarcodeNumber = existing?.userBarcodeNumber
            } else {
                if (isExisting) {
                    // Evict previously assigned barcodes of this user so reassignment within this CSV works
                    allExistingUserBarcodesMap.entries.removeAll { it.value == userId }
                }
                if (rawUserBarcode.isNotEmpty()) {
                    val assignedUserId = allExistingUserBarcodesMap[rawUserBarcode]
                    if (assignedUserId != null && assignedUserId != userId) {
                        strippedBarcodesCount++
                        warnings.add("Row ${i + 1} ('$name'): User barcode '$rawUserBarcode' stripped because it is already assigned to user '$assignedUserId'.")
                    } else {
                        finalUserBarcode = rawUserBarcode
                        allExistingUserBarcodesMap[rawUserBarcode] = userId
                    }
                }

                if (rawUserBarcodeNumber.isNotEmpty()) {
                    val assignedUserId = allExistingUserBarcodesMap[rawUserBarcodeNumber]
                    if (assignedUserId != null && assignedUserId != userId) {
                        strippedBarcodesCount++
                        warnings.add("Row ${i + 1} ('$name'): User barcode number '$rawUserBarcodeNumber' stripped because it is already assigned to user '$assignedUserId'.")
                    } else {
                        finalUserBarcodeNumber = rawUserBarcodeNumber
                        allExistingUserBarcodesMap[rawUserBarcodeNumber] = userId
                    }
                }

                // If one column was omitted from CSV, preserve existing value for that column if the other was provided
                if (isExisting) {
                    if (userBarcodeIdx == -1 && finalUserBarcodeNumber != null) {
                        finalUserBarcode = existing?.userBarcode
                    }
                    if (userBarcodeNumberIdx == -1 && finalUserBarcode != null) {
                        finalUserBarcodeNumber = existing?.userBarcodeNumber
                    }
                }
            }

            val effectiveBarcode = if (finalUserBarcode != null && finalUserBarcodeNumber != null) {
                finalUserBarcode to finalUserBarcodeNumber
            } else if (finalUserBarcode != null) {
                finalUserBarcode to finalUserBarcode
            } else if (finalUserBarcodeNumber != null) {
                finalUserBarcodeNumber to finalUserBarcodeNumber
            } else {
                null to null
            }

            val userEntity = UserEntity(
                id = userId,
                name = name,
                balance = targetBalance,
                language = langStr,
                secondaryCurrency = secCurrStr,
                pin = pin,
                userBarcode = effectiveBarcode.first,
                userBarcodeNumber = effectiveBarcode.second,
                isActive = isActive,
                isDeleted = isDeleted,
                avatarType = avatarTypeStr,
                avatarEmoji = avatarEmojiStr,
                avatarColor = avatarColorStr
            )

            usersToInsert.add(userEntity)
            if (isExisting) updatedCount++ else addedCount++
        }

        if (!dryRun && (usersToInsert.isNotEmpty() || transactionsToInsert.isNotEmpty())) {
            val safetyFile = File(dbFile.parentFile ?: File(System.getProperty("user.home"), ".lojinha"), "lojinha_room_pre_csv_import_safety.db")
            if (dbFile.exists()) {
                performDbSnapshot(safetyFile)
            }
            db.useWriterConnection { transactor ->
                transactor.immediateTransaction {
                    usersToInsert.forEach { db.userDao().insertOrUpdateUser(it) }
                    transactionsToInsert.forEach { db.transactionDao().insertTransaction(it) }
                }
            }
        }

        CsvImportResult(
            totalProcessed = addedCount + updatedCount,
            addedCount = addedCount,
            updatedCount = updatedCount,
            strippedBarcodesCount = strippedBarcodesCount,
            errors = errors,
            warnings = warnings
        )
    }

    private fun writeCsvFile(file: File, lines: List<String>) {
        file.writeText(UTF8_BOM + lines.joinToString("\n"))
    }

    private fun escapeCsv(value: String): String {
        if (value.contains(",") || value.contains(";") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            val escaped = value.replace("\"", "\"\"")
            return "\"$escaped\""
        }
        return value
    }

    private fun detectDelimiter(csvText: String): Char {
        val firstLine = csvText.lineSequence().firstOrNull { it.isNotBlank() } ?: return CSV_DELIMITER.first()
        var inQuotes = false
        var commaCount = 0
        var semicolonCount = 0
        for (c in firstLine) {
            if (c == '"') {
                inQuotes = !inQuotes
            } else if (!inQuotes) {
                if (c == ',') commaCount++
                else if (c == ';') semicolonCount++
            }
        }
        return if (semicolonCount > commaCount) ';' else if (commaCount > 0) ',' else CSV_DELIMITER.first()
    }

    fun parseCsvRecords(csvText: String): List<List<String>> {
        var cleanText = csvText.removePrefix(UTF8_BOM)
        if (cleanText.isBlank()) return emptyList()

        // Check for optional "sep=X" directive on the first line
        val firstLine = cleanText.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: ""
        var explicitDelimiter: Char? = null
        if (firstLine.startsWith("sep=", ignoreCase = true) && firstLine.length == 5) {
            explicitDelimiter = firstLine[4]
            val newlineIndex = cleanText.indexOfAny(charArrayOf('\r', '\n'))
            cleanText = if (newlineIndex >= 0) {
                cleanText.substring(newlineIndex).trimStart('\r', '\n')
            } else {
                ""
            }
            if (cleanText.isBlank()) return emptyList()
        }

        val delimiter = explicitDelimiter ?: detectDelimiter(cleanText)
        val records = mutableListOf<List<String>>()
        val currentRecord = mutableListOf<String>()
        val currentField = StringBuilder()
        var inQuotes = false
        var i = 0
        val len = cleanText.length

        while (i < len) {
            val c = cleanText[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < len && cleanText[i + 1] == '"') {
                        currentField.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == delimiter && !inQuotes -> {
                    currentRecord.add(currentField.toString())
                    currentField.clear()
                }
                (c == '\r' || c == '\n') && !inQuotes -> {
                    if (c == '\r' && i + 1 < len && cleanText[i + 1] == '\n') {
                        i++
                    }
                    currentRecord.add(currentField.toString())
                    currentField.clear()
                    if (currentRecord.size > 1 || (currentRecord.isNotEmpty() && currentRecord[0].isNotEmpty())) {
                        records.add(currentRecord.toList())
                    }
                    currentRecord.clear()
                }
                else -> {
                    currentField.append(c)
                }
            }
            i++
        }
        if (currentField.isNotEmpty() || currentRecord.isNotEmpty()) {
            currentRecord.add(currentField.toString())
            if (currentRecord.size > 1 || (currentRecord.isNotEmpty() && currentRecord[0].isNotEmpty())) {
                records.add(currentRecord.toList())
            }
        }
        return records
    }

    companion object {
        const val CSV_DELIMITER = ";"
        const val UTF8_BOM = "\uFEFF"
    }
}
