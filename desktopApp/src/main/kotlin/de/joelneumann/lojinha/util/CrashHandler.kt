package de.joelneumann.lojinha.util

import java.awt.Desktop
import java.awt.Dimension
import java.awt.Toolkit
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import kotlin.system.exitProcess

object CrashHandler {
    private const val TAG = "CrashHandler"

    fun install() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                handleCrash(thread, throwable)
            } catch (fatal: Throwable) {
                System.err.println("Fatal error in CrashHandler: ${fatal.message}")
                fatal.printStackTrace()
            } finally {
                previousHandler?.uncaughtException(thread, throwable) ?: exitProcess(1)
            }
        }
    }

    fun handleCrash(thread: Thread, throwable: Throwable) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val crashFileName = "crash_$timestamp.txt"
        val logDir = FileRollingLogger.getLogDirectory()
        val crashFile = File(logDir, crashFileName)

        val reportContent = buildCrashReport(thread, throwable, timestamp)

        // 1. Write dedicated crash report file
        try {
            crashFile.writeText(reportContent)
        } catch (e: Exception) {
            System.err.println("Failed to write crash report file: ${e.message}")
        }

        // 2. Log via AppLogger to ensure it is in primary lojinha.log
        AppLogger.error(TAG, "FATAL CRASH in thread '${thread.name}': ${throwable.message}", throwable)

        // 3. Show dialog to user
        try {
            showCrashDialog(crashFile, logDir)
        } catch (e: Exception) {
            System.err.println("Failed to display crash dialog: ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun buildCrashReport(thread: Thread, throwable: Throwable, timestamp: String): String {
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

        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))

        return buildString {
            appendLine("================================================================================")
            appendLine("                        LOJINHA CRASH REPORT")
            appendLine("================================================================================")
            appendLine("Timestamp:      $timestamp")
            appendLine("Application:    Lojinha POS & Self-Service Kiosk (Version 1.0.0)")
            appendLine("Thread:         ${thread.name} (id=${thread.id}, priority=${thread.priority}, state=${thread.state})")
            appendLine()
            appendLine("--- SYSTEM ENVIRONMENT ---")
            appendLine("OS Name:        ${System.getProperty("os.name")}")
            appendLine("OS Version:     ${System.getProperty("os.version")}")
            appendLine("OS Arch:        ${System.getProperty("os.arch")}")
            appendLine("Java Version:   ${System.getProperty("java.version")} (${System.getProperty("java.vendor")})")
            appendLine("Java Home:      ${System.getProperty("java.home")}")
            appendLine("Screen Size:    ${screenSize?.let { "${it.width}x${it.height}" } ?: "Unknown"}")
            appendLine("Memory (Heap):  Used: ${usedMemoryMb}MB | Free: ${freeMemoryMb}MB | Total: ${totalMemoryMb}MB | Max: ${maxMemoryMb}MB")
            appendLine()
            appendLine("--- EXCEPTION STACK TRACE ---")
            appendLine(sw.toString())
            appendLine("================================================================================")
        }
    }

    private fun showCrashDialog(crashFile: File, logDir: File) {
        val runnable = Runnable {
            val message = """
                An unexpected error occurred and the application cannot continue.
                
                Crash Report File:
                ${crashFile.absolutePath}
                
                Please share this crash report with your system administrator or support.
            """.trimIndent()

            val options = arrayOf("Open Logs Folder", "Close")
            val choice = JOptionPane.showOptionDialog(
                null,
                message,
                "Lojinha - Unexpected Error",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE,
                null,
                options,
                options[0]
            )

            if (choice == 0) {
                try {
                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                        Desktop.getDesktop().open(logDir)
                    }
                } catch (e: Exception) {
                    System.err.println("Could not open log folder: ${e.message}")
                }
            }
        }

        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run()
        } else {
            SwingUtilities.invokeAndWait(runnable)
        }
    }
}
