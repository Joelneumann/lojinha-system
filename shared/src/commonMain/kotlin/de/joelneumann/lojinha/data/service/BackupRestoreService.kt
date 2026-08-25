package de.joelneumann.lojinha.data.service

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.joelneumann.lojinha.data.database.AppDatabase
import de.joelneumann.lojinha.data.database.AppDatabaseConstructor
import de.joelneumann.lojinha.data.entity.ProductEntity
import de.joelneumann.lojinha.data.entity.SettingsEntity
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

    suspend fun performDbBackup(destinationDir: File): File = withContext(Dispatchers.IO) {
        require(destinationDir.exists() && destinationDir.isDirectory) { "Destination directory does not exist or is not a directory: ${destinationDir.absolutePath}" }
        val backupFileName = "lojinha_backup_${getTimestampString()}.db"
        val targetFile = File(destinationDir, backupFileName)

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
            de.joelneumann.lojinha.domain.model.BackupFileType.DB -> performDbBackup(dir)
            de.joelneumann.lojinha.domain.model.BackupFileType.CSV -> performCsvBackup(dir)
            de.joelneumann.lojinha.domain.model.BackupFileType.BOTH -> {
                val dbResult = performDbBackup(dir)
                performCsvBackup(dir)
                dbResult
            }
        }
    }

    suspend fun performCsvBackup(destinationDir: File): File = withContext(Dispatchers.IO) {
        require(destinationDir.exists() && destinationDir.isDirectory) { "Destination directory does not exist: ${destinationDir.absolutePath}" }
        val exportFolder = File(destinationDir, "lojinha_csv_export_${getTimestampString()}")
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

        val backupBuilder = Room.databaseBuilder<AppDatabase>(
            name = backupFile.absolutePath,
            factory = { AppDatabaseConstructor.initialize() }
        )
        backupBuilder.setDriver(BundledSQLiteDriver())
        backupBuilder.setQueryCoroutineContext(Dispatchers.IO)
        backupBuilder.fallbackToDestructiveMigration(true)
        val backupDb = backupBuilder.build()

        try {
            val backupUsers = backupDb.userDao().getAllUsers()
            val backupProducts = backupDb.productDao().getAllProducts()
            val backupTransactions = backupDb.transactionDao().getAllTransactions()
            val backupSettings = backupDb.settingsDao().getSettings()
            val backupRoutines = backupDb.backupDao().getAllBackups()

            db.userDao().deleteAllUsers()
            db.productDao().deleteAllProducts()
            db.transactionDao().deleteAllTransactions()
            db.backupDao().deleteAllBackups()

            backupUsers.forEach { db.userDao().insertOrUpdateUser(it) }
            backupProducts.forEach { db.productDao().insertOrUpdateProduct(it) }
            backupTransactions.forEach { db.transactionDao().insertTransaction(it) }
            backupRoutines.forEach { db.backupDao().insertOrUpdateBackup(it) }
            if (backupSettings != null) {
                db.settingsDao().insertOrUpdateSettings(backupSettings)
            }
        } finally {
            backupDb.close()
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
