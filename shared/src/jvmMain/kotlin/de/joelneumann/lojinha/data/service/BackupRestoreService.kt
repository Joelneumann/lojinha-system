package de.joelneumann.lojinha.data.service

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.joelneumann.lojinha.data.database.AppDatabase
import de.joelneumann.lojinha.data.database.Converters
import de.joelneumann.lojinha.data.entity.BackupEntity
import de.joelneumann.lojinha.data.entity.ProductEntity
import de.joelneumann.lojinha.data.entity.SettingsEntity
import de.joelneumann.lojinha.data.entity.TransactionEntity
import de.joelneumann.lojinha.data.entity.UserEntity
import de.joelneumann.lojinha.domain.model.Barcode
import de.joelneumann.lojinha.domain.model.Language
import de.joelneumann.lojinha.domain.model.Product
import de.joelneumann.lojinha.domain.model.SecondaryCurrency
import de.joelneumann.lojinha.domain.model.UnitType
import de.joelneumann.lojinha.domain.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CsvImportResult(
    val totalProcessed: Int,
    val addedCount: Int,
    val updatedCount: Int,
    val strippedBarcodesCount: Int,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)

data class BackupFileInfo(
    val file: File,
    val name: String,
    val format: String, // "DB" or "CSV"
    val sizeBytes: Long,
    val lastModifiedTimestamp: Long
)

class BackupRestoreService(
    private val db: AppDatabase
) {
    private val dbFile = File(System.getProperty("user.home"), ".lojinha/lojinha_room.db")

    suspend fun listBackupsInDirectory(dirPath: String): List<BackupFileInfo> = withContext(Dispatchers.IO) {
        if (dirPath.isBlank()) return@withContext emptyList()
        val dir = File(dirPath)
        if (!dir.exists() || !dir.isDirectory) return@withContext emptyList()

        val files = dir.listFiles() ?: return@withContext emptyList()
        files.filter { f ->
            (f.isFile && f.name.endsWith(".db")) || (f.isDirectory && f.name.startsWith("lojinha_csv_export_"))
        }.map { f ->
            val format = if (f.isFile && f.name.endsWith(".db")) "DB" else "CSV"
            val size = if (f.isFile) f.length() else (f.listFiles()?.sumOf { it.length() } ?: 0L)
            BackupFileInfo(
                file = f,
                name = f.name,
                format = format,
                sizeBytes = size,
                lastModifiedTimestamp = f.lastModified()
            )
        }.sortedByDescending { it.lastModifiedTimestamp }
    }

    suspend fun deleteBackup(file: File): Boolean = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext false
        if (file.isDirectory) {
            file.deleteRecursively()
        } else {
            file.delete()
        }
    }

    private fun getTimestampString(): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        return sdf.format(Date())
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

        // Checkpoint WAL via BundledSQLiteDriver to flush all active transactions into lojinha_room.db
        try {
            val connection = BundledSQLiteDriver().open(dbFile.absolutePath)
            try {
                val stmt = connection.prepare("PRAGMA wal_checkpoint(FULL)")
                try {
                    stmt.step()
                } finally {
                    stmt.close()
                }
            } finally {
                connection.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (dbFile.exists()) {
            dbFile.copyTo(targetFile, overwrite = true)
        } else {
            throw IllegalStateException("Database file not found at ${dbFile.absolutePath}")
        }
        targetFile
    }

    suspend fun executeRoutineBackup(routine: de.joelneumann.lojinha.domain.model.BackupRoutine): File = withContext(Dispatchers.IO) {
        val dir = File(routine.backupLocationPath)
        require(dir.exists() && dir.isDirectory) { "Target directory does not exist: ${dir.absolutePath}" }
        when (routine.fileType) {
            de.joelneumann.lojinha.domain.model.BackupFileType.DB -> performDbBackup(dir, routine.writeMode)
            de.joelneumann.lojinha.domain.model.BackupFileType.CSV -> performCsvBackup(dir, routine.writeMode)
            de.joelneumann.lojinha.domain.model.BackupFileType.BOTH -> {
                val dbResult = performDbBackup(dir, routine.writeMode)
                performCsvBackup(dir, routine.writeMode)
                dbResult
            }
        }
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

        // 1. Export Products
        val products = db.productDao().getAllProducts().map { it.toDomain() }
        val productsCsv = File(exportFolder, "products.csv")
        val productLines = mutableListOf("id,name,barcodes,basePrice,unitType,stockQuantity,customMarkupPercent,isActive")
        products.forEach { p ->
            val barcodeStr = p.barcodes.joinToString("|") { "${it.code}:${it.description ?: ""}" }
            val line = listOf(
                escapeCsv(p.id),
                escapeCsv(p.name),
                escapeCsv(barcodeStr),
                p.basePrice.toString(),
                p.unitType.name,
                p.stockQuantity.toString(),
                p.customMarkupPercent?.toString() ?: "",
                p.isActive.toString()
            ).joinToString(",")
            productLines.add(line)
        }
        productsCsv.writeText(productLines.joinToString("\n"))

        // 2. Export Users
        val users = db.userDao().getAllUsers().map { it.toDomain() }
        val usersCsv = File(exportFolder, "users.csv")
        val userLines = mutableListOf("id,name,balance,language,secondaryCurrency,pin,userBarcode,userBarcodeNumber,isActive,isDeleted")
        users.forEach { u ->
            val line = listOf(
                escapeCsv(u.id),
                escapeCsv(u.name),
                u.balance.toString(),
                u.language.code,
                u.secondaryCurrency.name,
                escapeCsv(u.pin ?: ""),
                escapeCsv(u.userBarcode ?: ""),
                escapeCsv(u.userBarcodeNumber ?: ""),
                u.isActive.toString(),
                u.isDeleted.toString()
            ).joinToString(",")
            userLines.add(line)
        }
        usersCsv.writeText(userLines.joinToString("\n"))

        // 3. Export Transactions
        val transactions = db.transactionDao().getAllTransactions().map { it.toDomain() }
        val txCsv = File(exportFolder, "transactions.csv")
        val txLines = mutableListOf("id,userId,userNameSnapshot,timestamp,type,referenceTransactionId,note,totalAmount,itemCount")
        transactions.forEach { t ->
            val line = listOf(
                escapeCsv(t.id),
                escapeCsv(t.userId),
                escapeCsv(t.userNameSnapshot),
                t.timestamp.toString(),
                t.type.name,
                escapeCsv(t.referenceTransactionId ?: ""),
                escapeCsv(t.note ?: ""),
                t.totalAmount.toString(),
                t.items.size.toString()
            ).joinToString(",")
            txLines.add(line)
        }
        txCsv.writeText(txLines.joinToString("\n"))

        exportFolder
    }

    suspend fun restoreDbFromBackup(backupFile: File) = withContext(Dispatchers.IO) {
        require(backupFile.exists() && backupFile.isFile) { "Backup file does not exist: ${backupFile.absolutePath}" }

        val converters = Converters()
        val backupUsers = mutableListOf<UserEntity>()
        val backupProducts = mutableListOf<ProductEntity>()
        val backupTransactions = mutableListOf<TransactionEntity>()
        var backupSettings: SettingsEntity? = null
        val backupRoutines = mutableListOf<BackupEntity>()

        // 1. Read tables from backupFile using BundledSQLiteDriver (read-only C driver, cross-platform)
        val connection = BundledSQLiteDriver().open(backupFile.absolutePath)
        try {
            val tableNames = mutableSetOf<String>()
            val stmtTables = connection.prepare("SELECT name FROM sqlite_master WHERE type='table'")
            try {
                while (stmtTables.step()) {
                    tableNames.add(stmtTables.getText(0).lowercase())
                }
            } finally {
                stmtTables.close()
            }

            // Read users
            if (tableNames.contains("users")) {
                val stmt = connection.prepare("SELECT id, name, balance, language, secondaryCurrency, pin, userBarcode, userBarcodeNumber, isActive, isDeleted FROM users")
                try {
                    while (stmt.step()) {
                        val u = UserEntity(
                            id = stmt.getText(0),
                            name = stmt.getText(1),
                            balance = stmt.getLong(2),
                            language = stmt.getText(3),
                            secondaryCurrency = stmt.getText(4),
                            pin = if (stmt.isNull(5)) null else stmt.getText(5),
                            userBarcode = if (stmt.isNull(6)) null else stmt.getText(6),
                            userBarcodeNumber = if (stmt.isNull(7)) null else stmt.getText(7),
                            isActive = stmt.getLong(8) != 0L,
                            isDeleted = try { stmt.getLong(9) != 0L } catch (e: Exception) { false }
                        )
                        backupUsers.add(u)
                    }
                } finally {
                    stmt.close()
                }
            }

            // Read products
            if (tableNames.contains("products")) {
                val stmt = connection.prepare("SELECT id, name, barcodes, basePrice, unitType, stockQuantity, customMarkupPercent, isActive FROM products")
                try {
                    while (stmt.step()) {
                        val barcodesStr = if (stmt.isNull(2)) "" else stmt.getText(2)
                        val customMarkup = if (stmt.isNull(6)) null else stmt.getDouble(6)

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
                val stmt = connection.prepare("SELECT id, userId, userNameSnapshot, timestamp, type, referenceTransactionId, note, totalAmount, items FROM transactions")
                try {
                    while (stmt.step()) {
                        val itemsStr = if (stmt.isNull(8)) "" else stmt.getText(8)
                        val t = TransactionEntity(
                            id = stmt.getText(0),
                            userId = stmt.getText(1),
                            userNameSnapshot = stmt.getText(2),
                            timestamp = stmt.getLong(3),
                            type = stmt.getText(4),
                            referenceTransactionId = if (stmt.isNull(5)) null else stmt.getText(5),
                            note = if (stmt.isNull(6)) null else stmt.getText(6),
                            totalAmount = stmt.getLong(7),
                            items = converters.toTransactionItemList(itemsStr)
                        )
                        backupTransactions.add(t)
                    }
                } finally {
                    stmt.close()
                }
            }

            // Read settings
            if (tableNames.contains("settings")) {
                val stmt = connection.prepare("SELECT id, adminPasswordHash, globalMarkupPercent, usdExchangeRate, eurExchangeRate, inactivityTimeoutMinutes FROM settings LIMIT 1")
                try {
                    if (stmt.step()) {
                        backupSettings = SettingsEntity(
                            id = stmt.getLong(0).toInt(),
                            adminPasswordHash = stmt.getText(1),
                            globalMarkupPercent = stmt.getDouble(2),
                            usdExchangeRate = stmt.getDouble(3),
                            eurExchangeRate = stmt.getDouble(4),
                            inactivityTimeoutMinutes = stmt.getLong(5).toInt()
                        )
                    }
                } finally {
                    stmt.close()
                }
            }

            // Read backup_routines
            if (tableNames.contains("backup_routines")) {
                val stmt = connection.prepare("SELECT id, name, isEnabled, type, fileType, scheduleConfig, backupLocationPath, lastBackupTimestamp, writeMode FROM backup_routines")
                try {
                    while (stmt.step()) {
                        val cfgStr = if (stmt.isNull(5)) "" else stmt.getText(5)
                        val lastTs = if (stmt.isNull(7)) null else stmt.getLong(7)
                        val writeModeStr = try { if (stmt.isNull(8)) "CREATE_NEW_FILE" else stmt.getText(8) } catch (e: Exception) { "CREATE_NEW_FILE" }
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
        } finally {
            connection.close()
        }

        // 2. Clear current database tables
        db.userDao().deleteAllUsers()
        db.productDao().deleteAllProducts()
        db.transactionDao().deleteAllTransactions()
        db.backupDao().deleteAllBackups()

        // 3. Insert extracted backup records into active database
        backupUsers.forEach { db.userDao().insertOrUpdateUser(it) }
        backupProducts.forEach { db.productDao().insertOrUpdateProduct(it) }
        backupTransactions.forEach { db.transactionDao().insertTransaction(it) }
        backupRoutines.forEach { db.backupDao().insertOrUpdateBackup(it) }
        if (backupSettings != null) {
            db.settingsDao().insertOrUpdateSettings(backupSettings)
        }
    }

    suspend fun wipeAllData() = withContext(Dispatchers.IO) {
        db.userDao().deleteAllUsers()
        db.productDao().deleteAllProducts()
        db.transactionDao().deleteAllTransactions()
        // Reset settings to initial defaults
        val defaultSettings = SettingsEntity(
            id = 1,
            adminPasswordHash = "admin",
            globalMarkupPercent = 0.0,
            usdExchangeRate = 0.18,
            eurExchangeRate = 0.16,
            inactivityTimeoutMinutes = 3
        )
        db.settingsDao().insertOrUpdateSettings(defaultSettings)
    }

    suspend fun importProductsFromCsv(csvFile: File): CsvImportResult = withContext(Dispatchers.IO) {
        require(csvFile.exists() && csvFile.isFile) { "Product CSV file does not exist: ${csvFile.absolutePath}" }
        val lines = csvFile.readLines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) {
            return@withContext CsvImportResult(0, 0, 0, 0, errors = listOf("CSV file is empty."))
        }

        val header = parseCsvLine(lines[0])
        val idIdx = header.indexOf("id")
        val nameIdx = header.indexOf("name")
        val barcodesIdx = header.indexOf("barcodes")
        val priceIdx = header.indexOf("basePrice")
        val unitIdx = header.indexOf("unitType")
        val stockIdx = header.indexOf("stockQuantity")
        val markupIdx = header.indexOf("customMarkupPercent")
        val activeIdx = header.indexOf("isActive")

        if (nameIdx == -1 || priceIdx == -1) {
            return@withContext CsvImportResult(0, 0, 0, 0, errors = listOf("Required headers 'name' and 'basePrice' missing."))
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

        for (i in 1 until lines.size) {
            val cols = parseCsvLine(lines[i])
            if (cols.size <= maxOf(nameIdx, priceIdx)) {
                warnings.add("Row ${i + 1}: Skipped due to missing columns.")
                continue
            }

            val rawId = if (idIdx != -1 && idIdx < cols.size) cols[idIdx].trim() else ""
            val name = cols[nameIdx].trim()
            val priceStr = cols[priceIdx].trim()
            val price = priceStr.toLongOrNull() ?: 0L

            val unitTypeStr = if (unitIdx != -1 && unitIdx < cols.size) cols[unitIdx].trim() else "PIECE"
            val unitType = try { UnitType.valueOf(unitTypeStr) } catch (e: Exception) { UnitType.PIECE }

            val stockStr = if (stockIdx != -1 && stockIdx < cols.size) cols[stockIdx].trim() else "0"
            val stock = stockStr.toLongOrNull() ?: 0L

            val markupStr = if (markupIdx != -1 && markupIdx < cols.size) cols[markupIdx].trim() else ""
            val markup = markupStr.toDoubleOrNull()

            val activeStr = if (activeIdx != -1 && activeIdx < cols.size) cols[activeIdx].trim() else "true"
            val isActive = activeStr.toBooleanStrictOrNull() ?: true

            val productId = if (rawId.isNotEmpty()) rawId else "p-imp-${getTimestampString()}-$i"

            // Parse Barcodes
            val rawBarcodesStr = if (barcodesIdx != -1 && barcodesIdx < cols.size) cols[barcodesIdx].trim() else ""
            val parsedBarcodes = mutableListOf<Barcode>()
            if (rawBarcodesStr.isNotEmpty()) {
                val parts = rawBarcodesStr.split("|")
                parts.forEach { part ->
                    val pair = part.split(":")
                    val code = pair[0].trim()
                    val label = if (pair.size > 1) pair[1].trim() else "Barcode"

                    if (code.isNotEmpty()) {
                        val assignedProductId = allExistingBarcodesMap[code]
                        if (assignedProductId != null && assignedProductId != productId) {
                            strippedBarcodesCount++
                            warnings.add("Row ${i + 1} ('$name'): Barcode '$code' stripped because it is already assigned to product '$assignedProductId'.")
                        } else {
                            parsedBarcodes.add(Barcode(code, label))
                            allExistingBarcodesMap[code] = productId
                        }
                    }
                }
            }

            val isExisting = existingProducts.containsKey(productId)
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

            db.productDao().insertOrUpdateProduct(productEntity)
            if (isExisting) updatedCount++ else addedCount++
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

    suspend fun importUsersFromCsv(csvFile: File): CsvImportResult = withContext(Dispatchers.IO) {
        require(csvFile.exists() && csvFile.isFile) { "User CSV file does not exist: ${csvFile.absolutePath}" }
        val lines = csvFile.readLines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) {
            return@withContext CsvImportResult(0, 0, 0, 0, errors = listOf("CSV file is empty."))
        }

        val header = parseCsvLine(lines[0])
        val idIdx = header.indexOf("id")
        val nameIdx = header.indexOf("name")
        val balanceIdx = header.indexOf("balance")
        val langIdx = header.indexOf("language")
        val secCurrIdx = header.indexOf("secondaryCurrency")
        val pinIdx = header.indexOf("pin")
        val userBarcodeIdx = header.indexOf("userBarcode")
        val userBarcodeNumberIdx = header.indexOf("userBarcodeNumber")
        val activeIdx = header.indexOf("isActive")

        if (nameIdx == -1) {
            return@withContext CsvImportResult(0, 0, 0, 0, errors = listOf("Required header 'name' missing."))
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

        for (i in 1 until lines.size) {
            val cols = parseCsvLine(lines[i])
            if (cols.size <= nameIdx) {
                warnings.add("Row ${i + 1}: Skipped due to missing columns.")
                continue
            }

            val rawId = if (idIdx != -1 && idIdx < cols.size) cols[idIdx].trim() else ""
            val name = cols[nameIdx].trim()
            val balanceStr = if (balanceIdx != -1 && balanceIdx < cols.size) cols[balanceIdx].trim() else "0"
            val balance = balanceStr.toLongOrNull() ?: 0L

            val langStr = if (langIdx != -1 && langIdx < cols.size) cols[langIdx].trim() else Language.DE.code
            val secCurrStr = if (secCurrIdx != -1 && secCurrIdx < cols.size) cols[secCurrIdx].trim() else SecondaryCurrency.NONE.name
            val pin = if (pinIdx != -1 && pinIdx < cols.size && cols[pinIdx].trim().isNotEmpty()) cols[pinIdx].trim() else null

            val activeStr = if (activeIdx != -1 && activeIdx < cols.size) cols[activeIdx].trim() else "true"
            val isActive = activeStr.toBooleanStrictOrNull() ?: true

            val userId = if (rawId.isNotEmpty()) rawId else "u-imp-${getTimestampString()}-$i"
            val isExisting = existingUsers.containsKey(userId)

            val rawUserBarcode = if (userBarcodeIdx != -1 && userBarcodeIdx < cols.size) cols[userBarcodeIdx].trim() else ""
            val rawUserBarcodeNumber = if (userBarcodeNumberIdx != -1 && userBarcodeNumberIdx < cols.size) cols[userBarcodeNumberIdx].trim() else ""

            var finalUserBarcode: String? = null
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

            var finalUserBarcodeNumber: String? = null
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

            val userEntity = UserEntity(
                id = userId,
                name = name,
                balance = balance,
                language = langStr,
                secondaryCurrency = secCurrStr,
                pin = pin,
                userBarcode = finalUserBarcode,
                userBarcodeNumber = finalUserBarcodeNumber,
                isActive = isActive
            )

            db.userDao().insertOrUpdateUser(userEntity)
            if (isExisting) updatedCount++ else addedCount++
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

    private fun escapeCsv(value: String): String {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            val escaped = value.replace("\"", "\"\"")
            return "\"$escaped\""
        }
        return value
    }

    private fun parseCsvLine(line: String): List<String> {
        val tokens = mutableListOf<String>()
        var inQuotes = false
        val sb = StringBuilder()
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    sb.append('"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == ',' && !inQuotes) {
                tokens.add(sb.toString())
                sb.clear()
            } else {
                sb.append(c)
            }
            i++
        }
        tokens.add(sb.toString())
        return tokens
    }
}
