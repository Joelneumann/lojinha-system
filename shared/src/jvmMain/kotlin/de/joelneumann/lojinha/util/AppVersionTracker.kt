package de.joelneumann.lojinha.util

import de.joelneumann.lojinha.AppVersion
import java.io.File

object AppVersionTracker {
    private const val TAG = "AppUpdate"
    private val defaultVersionFile = File(System.getProperty("user.home"), ".lojinha/.version")

    fun checkAndTrackVersion(
        currentVersion: String = AppVersion.CURRENT,
        targetFile: File = defaultVersionFile
    ) {
        try {
            targetFile.parentFile?.mkdirs()
            if (!targetFile.exists()) {
                targetFile.writeText(currentVersion.trim())
                AppLogger.info(TAG, "Fresh installation detected (v$currentVersion). Initialized version tracker.")
            } else {
                val previousVersion = targetFile.readText().trim()
                if (previousVersion != currentVersion.trim()) {
                    AppLogger.info(
                        TAG,
                        "Software update detected: Successfully upgraded from v$previousVersion to v$currentVersion."
                    )
                    targetFile.writeText(currentVersion.trim())
                }
            }
        } catch (e: Exception) {
            AppLogger.warn(TAG, "Failed to inspect or update version tracker file: ${e.message}", e)
        }
    }
}
