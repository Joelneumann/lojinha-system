package de.joelneumann.lojinha.util

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileRollingLogger {
    private const val MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024L // 5 MB
    private const val MAX_BACKUP_FILES = 5

    private val logDir: File by lazy {
        val dir = File(System.getProperty("user.home"), ".lojinha/logs")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dir
    }

    private val primaryLogFile: File by lazy {
        File(logDir, "lojinha.log")
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val lock = Any()

    fun getLogDirectory(): File = logDir

    fun <T> withLogLock(block: () -> T): T = synchronized(lock) {
        block()
    }

    fun getLogFiles(): List<File> {
        val dir = logDir
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return dir.listFiles { file ->
            file.isFile && (file.name.endsWith(".log") || file.name.endsWith(".txt"))
        }?.sortedByDescending { it.lastModified() }?.toList() ?: emptyList()
    }

    fun getTotalLogSizeBytes(): Long {
        return getLogFiles().sumOf { it.length() }
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
        }
    }

    fun log(level: String, tag: String, message: String, throwable: Throwable? = null) {
        val timestamp = synchronized(dateFormat) { dateFormat.format(Date()) }
        val threadName = Thread.currentThread().name
        val sb = StringBuilder()
        sb.append(timestamp)
            .append(" [").append(threadName).append("] ")
            .append(level.padEnd(5))
            .append(" [").append(tag).append("] - ")
            .append(message)
            .append("\n")

        if (throwable != null) {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            sb.append(sw.toString()).append("\n")
        }

        val logLine = sb.toString()

        // Also print to console for terminal debugging
        if (level == "ERROR" || level == "WARN") {
            System.err.print(logLine)
        } else {
            System.out.print(logLine)
        }

        // Write to rolling file
        synchronized(lock) {
            try {
                if (!logDir.exists()) {
                    logDir.mkdirs()
                }
                rotateIfNeeded()
                FileOutputStream(primaryLogFile, true).use { fos ->
                    OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                        writer.write(logLine)
                        writer.flush()
                    }
                }
            } catch (e: Exception) {
                System.err.println("FileRollingLogger failed to write log: ${e.message}")
            }
        }
    }

    private fun rotateIfNeeded() {
        if (!primaryLogFile.exists() || primaryLogFile.length() < MAX_FILE_SIZE_BYTES) {
            return
        }

        try {
            // Delete the oldest backup file if it exists
            val oldestFile = File(logDir, "lojinha.$MAX_BACKUP_FILES.log")
            if (oldestFile.exists()) {
                oldestFile.delete()
            }

            // Shift existing backup files up (e.g. lojinha.4.log -> lojinha.5.log)
            for (i in (MAX_BACKUP_FILES - 1) downTo 1) {
                val current = File(logDir, "lojinha.$i.log")
                if (current.exists()) {
                    val next = File(logDir, "lojinha.${i + 1}.log")
                    Files.move(current.toPath(), next.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            }

            // Rename current primary log to lojinha.1.log
            val firstBackup = File(logDir, "lojinha.1.log")
            Files.move(primaryLogFile.toPath(), firstBackup.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            System.err.println("FileRollingLogger failed to rotate logs: ${e.message}")
        }
    }
}
