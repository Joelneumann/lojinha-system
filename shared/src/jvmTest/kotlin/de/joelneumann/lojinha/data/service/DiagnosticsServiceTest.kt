package de.joelneumann.lojinha.data.service

import de.joelneumann.lojinha.util.AppLogger
import de.joelneumann.lojinha.util.FileRollingLogger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class DiagnosticsServiceTest {

    @Test
    fun testFileRollingLoggerWritesAndFormats() {
        AppLogger.info("DiagnosticsTest", "Test log message for verification")
        val logDir = FileRollingLogger.getLogDirectory()
        assertTrue("Log directory should exist", logDir.exists())

        val primaryLog = File(logDir, "lojinha.log")
        assertTrue("Primary log file should exist", primaryLog.exists())
        val content = primaryLog.readText()
        assertTrue("Log should contain logged message", content.contains("Test log message for verification"))

        val formattedSize = FileRollingLogger.formatFileSize(1024 * 1024 * 2)
        assertEquals("2.00 MB", formattedSize)
    }

    @Test
    fun testCreateSupportBundleProducesValidZip() = runBlocking {
        // Ensure a log entry exists
        AppLogger.warn("DiagnosticsTest", "Warning for test bundle")

        val tempOutputDir = File(System.getProperty("java.io.tmpdir"), "lojinha_test_bundles_" + System.currentTimeMillis())
        tempOutputDir.mkdirs()

        try {
            val service = DiagnosticsService()
            val bundleZip = service.createSupportBundle(targetFolder = tempOutputDir)

            assertTrue("Bundle zip should exist", bundleZip.exists())
            assertTrue("Bundle zip should have positive size", bundleZip.length() > 0)
            assertTrue("Bundle zip should end with .zip", bundleZip.name.endsWith(".zip"))

            // Verify Zip contents
            val zip = ZipFile(bundleZip)
            val entryNames = zip.entries().asSequence().map { it.name }.toSet()

            assertTrue("Zip should contain system_info.txt", entryNames.contains("system_info.txt"))
            assertTrue("Zip should contain db_diagnostics.txt", entryNames.contains("db_diagnostics.txt"))
            assertTrue("Zip should contain at least one log file", entryNames.any { it.startsWith("logs/") })

            // Verify system_info.txt content
            val systemInfoEntry = zip.getEntry("system_info.txt")
            val systemInfoText = zip.getInputStream(systemInfoEntry).bufferedReader().readText()
            assertTrue("system_info should contain OS Name", systemInfoText.contains("OS Name:"))
            assertTrue("system_info should contain Java Version", systemInfoText.contains("Java Version:"))
            assertTrue("system_info should contain App Version", systemInfoText.contains("App Version:"))

            // Verify db_diagnostics.txt content
            val dbDiagEntry = zip.getEntry("db_diagnostics.txt")
            val dbDiagText = zip.getInputStream(dbDiagEntry).bufferedReader().readText()
            assertTrue("db_diagnostics should contain Database File", dbDiagText.contains("Database File:"))
            assertTrue("db_diagnostics should contain Integrity Check", dbDiagText.contains("Integrity Check:"))

            zip.close()
        } finally {
            tempOutputDir.deleteRecursively()
        }
    }
}
