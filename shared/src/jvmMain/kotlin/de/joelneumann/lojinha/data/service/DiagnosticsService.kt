package de.joelneumann.lojinha.data.service

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.joelneumann.lojinha.data.database.AppDatabase
import de.joelneumann.lojinha.util.AppLogger
import de.joelneumann.lojinha.util.FileRollingLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.Dimension
import java.awt.Toolkit
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URI
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DiagnosticsService(
    private val db: AppDatabase? = null,
    private val dbFile: File = File(System.getProperty("user.home"), ".lojinha/lojinha_room.db")
) {
    companion object {
        private const val TAG = "DiagnosticsService"
    }

    suspend fun createSupportBundle(targetFolder: File? = null): File = withContext(Dispatchers.IO) {
        AppLogger.info(TAG, "Starting diagnostic support bundle creation...")
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outputDir = targetFolder ?: File(FileRollingLogger.getLogDirectory(), "support_bundles")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        val zipFile = File(outputDir, "lojinha-support-bundle-$timestamp.zip")

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            // 1. Pack all log and crash files
            val logFiles = FileRollingLogger.getLogFiles()
            for (file in logFiles) {
                if (file.exists() && file.isFile) {
                    addFileToZip(zos, file, "logs/${file.name}")
                }
            }

            // 2. Generate and pack system_info.txt
            val systemInfo = generateSystemInfo(timestamp)
            addTextToZip(zos, systemInfo, "system_info.txt")

            // 3. Generate and pack db_diagnostics.txt
            val dbDiagnostics = generateDatabaseDiagnostics()
            addTextToZip(zos, dbDiagnostics, "db_diagnostics.txt")
        }

        AppLogger.info(TAG, "Diagnostic support bundle successfully created at ${zipFile.absolutePath} (${FileRollingLogger.formatFileSize(zipFile.length())})")
        zipFile
    }

    private fun addFileToZip(zos: ZipOutputStream, file: File, entryName: String) {
        try {
            val entry = ZipEntry(entryName)
            entry.time = file.lastModified()
            zos.putNextEntry(entry)
            FileInputStream(file).use { fis ->
                fis.copyTo(zos)
            }
            zos.closeEntry()
        } catch (e: Exception) {
            AppLogger.warn(TAG, "Failed to include file ${file.name} in zip: ${e.message}", e)
        }
    }

    private fun addTextToZip(zos: ZipOutputStream, text: String, entryName: String) {
        try {
            val entry = ZipEntry(entryName)
            entry.time = System.currentTimeMillis()
            zos.putNextEntry(entry)
            zos.write(text.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        } catch (e: Exception) {
            AppLogger.warn(TAG, "Failed to include entry $entryName in zip: ${e.message}", e)
        }
    }

    private fun generateSystemInfo(timestamp: String): String {
        val runtime = Runtime.getRuntime()
        val totalMemoryMb = runtime.totalMemory() / (1024 * 1024)
        val freeMemoryMb = runtime.freeMemory() / (1024 * 1024)
        val maxMemoryMb = runtime.maxMemory() / (1024 * 1024)
        val usedMemoryMb = totalMemoryMb - freeMemoryMb

        val screenSize: Dimension? = try {
            Toolkit.getDefaultToolkit().screenSize
        } catch (_: Exception) {
            null
        }

        return buildString {
            appendLine("================================================================================")
            appendLine("                    LOJINHA SYSTEM DIAGNOSTICS REPORT")
            appendLine("================================================================================")
            appendLine("Generated At:       $timestamp")
            appendLine("Application:        Lojinha POS & Self-Service Kiosk")
            appendLine("App Version:        1.0.0")
            appendLine()
            appendLine("--- OPERATING SYSTEM ---")
            appendLine("OS Name:            ${System.getProperty("os.name")}")
            appendLine("OS Version:         ${System.getProperty("os.version")}")
            appendLine("OS Architecture:    ${System.getProperty("os.arch")}")
            appendLine("User Name:          ${System.getProperty("user.name")}")
            appendLine("User Home:          ${System.getProperty("user.home")}")
            appendLine("User Timezone:      ${java.util.TimeZone.getDefault().id}")
            appendLine("Default Locale:     ${Locale.getDefault()}")
            appendLine()
            appendLine("--- JAVA RUNTIME ---")
            appendLine("Java Version:       ${System.getProperty("java.version")}")
            appendLine("Java Vendor:        ${System.getProperty("java.vendor")}")
            appendLine("VM Name:            ${System.getProperty("java.vm.name")}")
            appendLine("VM Version:         ${System.getProperty("java.vm.version")}")
            appendLine("Java Home:          ${System.getProperty("java.home")}")
            appendLine()
            appendLine("--- HARDWARE & MEMORY ---")
            appendLine("Available CPUs:     ${runtime.availableProcessors()}")
            appendLine("Max Heap Memory:    ${maxMemoryMb} MB")
            appendLine("Total Allocated:    ${totalMemoryMb} MB")
            appendLine("Free Memory:        ${freeMemoryMb} MB")
            appendLine("Used Memory:        ${usedMemoryMb} MB")
            appendLine("Primary Display:    ${screenSize?.let { "${it.width}x${it.height}" } ?: "Unknown"}")
            appendLine()
            appendLine("--- STORAGE & LOGS ---")
            appendLine("Log Directory:      ${FileRollingLogger.getLogDirectory().absolutePath}")
            appendLine("Total Log Files:    ${FileRollingLogger.getLogFiles().size}")
            appendLine("Total Log Size:     ${FileRollingLogger.formatFileSize(FileRollingLogger.getTotalLogSizeBytes())}")
            appendLine("================================================================================")
        }
    }

    private suspend fun generateDatabaseDiagnostics(): String = withContext(Dispatchers.IO) {
        val walFile = File("${dbFile.absolutePath}-wal")
        val shmFile = File("${dbFile.absolutePath}-shm")

        var integrityResult = "Unknown"
        if (dbFile.exists()) {
            try {
                val connection = BundledSQLiteDriver().open(dbFile.absolutePath)
                try {
                    val stmt = connection.prepare("PRAGMA integrity_check;")
                    try {
                        if (stmt.step()) {
                            integrityResult = stmt.getText(0)
                        }
                    } finally {
                        stmt.close()
                    }
                } finally {
                    connection.close()
                }
            } catch (e: Exception) {
                integrityResult = "Error checking integrity: ${e.message}"
            }
        } else {
            integrityResult = "Database file does not exist at expected path"
        }

        var usersCount = -1
        var activeUsersCount = -1
        var productsCount = -1
        var activeProductsCount = -1
        var transactionsCount = -1
        var billingListsCount = -1

        db?.let { database ->
            try {
                val users = database.userDao().getAllUsers()
                usersCount = users.size
                activeUsersCount = users.count { it.isActive }
            } catch (_: Exception) {}

            try {
                val products = database.productDao().getAllProducts()
                productsCount = products.size
                activeProductsCount = products.count { it.isActive }
            } catch (_: Exception) {}

            try {
                transactionsCount = database.transactionDao().getAllTransactions().size
            } catch (_: Exception) {}

            try {
                billingListsCount = database.billingListDao().getAllBillingLists().size
            } catch (_: Exception) {}
        }

        buildString {
            appendLine("================================================================================")
            appendLine("                   DATABASE HEALTH & INTEGRITY REPORT")
            appendLine("================================================================================")
            appendLine("Database File:      ${dbFile.absolutePath}")
            appendLine("Database Exists:    ${dbFile.exists()}")
            appendLine("Database Size:      ${if (dbFile.exists()) FileRollingLogger.formatFileSize(dbFile.length()) else "0 B"}")
            appendLine("WAL File Exists:    ${walFile.exists()} (${if (walFile.exists()) FileRollingLogger.formatFileSize(walFile.length()) else "N/A"})")
            appendLine("SHM File Exists:    ${shmFile.exists()} (${if (shmFile.exists()) FileRollingLogger.formatFileSize(shmFile.length()) else "N/A"})")
            appendLine("Integrity Check:    $integrityResult")
            appendLine()
            appendLine("--- AGGREGATE METRICS (PRIVACY SAFE) ---")
            appendLine("Users Total:        ${if (usersCount >= 0) usersCount else "N/A"}")
            appendLine("Users Active:       ${if (activeUsersCount >= 0) activeUsersCount else "N/A"}")
            appendLine("Products Total:     ${if (productsCount >= 0) productsCount else "N/A"}")
            appendLine("Products Active:    ${if (activeProductsCount >= 0) activeProductsCount else "N/A"}")
            appendLine("Transactions:       ${if (transactionsCount >= 0) transactionsCount else "N/A"}")
            appendLine("Billing Lists:      ${if (billingListsCount >= 0) billingListsCount else "N/A"}")
            appendLine("================================================================================")
        }
    }

    fun revealInFileExplorer(file: File) {
        try {
            val os = System.getProperty("os.name").lowercase(Locale.US)
            when {
                os.contains("win") -> {
                    ProcessBuilder("explorer.exe", "/select,", file.absolutePath).start()
                }
                os.contains("mac") -> {
                    ProcessBuilder("open", "-R", file.absolutePath).start()
                }
                else -> {
                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                        Desktop.getDesktop().open(if (file.isDirectory) file else file.parentFile)
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.warn(TAG, "Failed to reveal file in explorer: ${e.message}", e)
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    Desktop.getDesktop().open(if (file.isDirectory) file else file.parentFile)
                }
            } catch (_: Exception) {}
        }
    }

    fun openLogFolder() {
        val dir = FileRollingLogger.getLogDirectory()
        revealInFileExplorer(dir)
    }

    fun openEmailDraft(recipientEmail: String, zipFile: File) {
        try {
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val subject = "Lojinha Support Diagnostic Bundle - $dateStr"
            val body = """
                Hello Support,
                
                Please find attached the diagnostic support bundle from my Lojinha Kiosk:
                File: ${zipFile.name}
                Location: ${zipFile.absolutePath}
                
                Description of the issue encountered:
                [Please describe what happened, what screen you were on, or any error message]
                
                Thank you!
            """.trimIndent()

            val encodedSubject = URLEncoder.encode(subject, "UTF-8").replace("+", "%20")
            val encodedBody = URLEncoder.encode(body, "UTF-8").replace("+", "%20")
            val mailtoUri = URI("mailto:$recipientEmail?subject=$encodedSubject&body=$encodedBody")

            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MAIL)) {
                Desktop.getDesktop().mail(mailtoUri)
            } else if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(mailtoUri)
            }
        } catch (e: Exception) {
            AppLogger.warn(TAG, "Failed to launch mailto client: ${e.message}", e)
        }
    }
}
