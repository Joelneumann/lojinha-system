package de.joelneumann.lojinha.data.service

import de.joelneumann.lojinha.domain.model.BackupRoutine
import de.joelneumann.lojinha.domain.model.BackupType
import de.joelneumann.lojinha.domain.model.BackupWriteMode
import de.joelneumann.lojinha.domain.repository.BackupRepository
import de.joelneumann.lojinha.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class AutoBackupScheduler(
    private val backupRestoreService: BackupRestoreService,
    private val backupRepository: BackupRepository,
    private val externalScope: CoroutineScope,
    private val settingsRepository: SettingsRepository? = null,
    private val oneDriveBackupService: OneDriveBackupService? = null
) {
    private var schedulerJob: Job? = null
    private val executionMutex = Mutex()
    private val failureCooldownMap = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun startScheduler() {
        schedulerJob?.cancel()
        schedulerJob = externalScope.launch(Dispatchers.IO) {
            // 1. Initial catch-up check on app startup
            evaluateAndRunRoutines()

            // 2. Periodic loop checking every 60 seconds
            while (isActive) {
                delay(60_000L) // 1 minute ticker
                evaluateAndRunRoutines()
            }
        }
    }

    fun stopScheduler() {
        schedulerJob?.cancel()
        schedulerJob = null
    }

    private var dataChangeDebounceJob: Job? = null

    @Synchronized
    fun triggerDataChangeBackup(debounceMs: Long = 1000L) {
        dataChangeDebounceJob?.cancel()
        dataChangeDebounceJob = externalScope.launch(Dispatchers.IO) {
            try {
                if (backupRestoreService.isDatabaseEmpty()) return@launch

                val activeRoutines = backupRepository.getAllBackups().filter {
                    it.isEnabled && it.scheduleConfig is de.joelneumann.lojinha.domain.model.BackupScheduleConfig.OnDataChange
                }
                if (activeRoutines.isEmpty()) return@launch

                val configuredDebounce = activeRoutines.mapNotNull {
                    (it.scheduleConfig as? de.joelneumann.lojinha.domain.model.BackupScheduleConfig.OnDataChange)?.debounceMs
                }.minOrNull() ?: debounceMs

                if (configuredDebounce > 0) {
                    delay(configuredDebounce)
                }
                evaluateAndRunDataChangeRoutines()
            } catch (_: kotlinx.coroutines.CancellationException) {
                // Expected when debouncing rapid successive data changes
            }
        }
    }

    private suspend fun evaluateAndRunDataChangeRoutines() {
        val activeRoutines = backupRepository.getAllBackups().filter {
            it.isEnabled && it.scheduleConfig is de.joelneumann.lojinha.domain.model.BackupScheduleConfig.OnDataChange
        }

        for (routine in activeRoutines) {
            if (routine.type == BackupType.LOCAL) {
                if (routine.backupLocationPath.isBlank()) continue
                val dir = File(routine.backupLocationPath)
                if (!dir.exists() || !dir.isDirectory) continue
            }
            executeRoutine(routine)
        }
    }

    private suspend fun evaluateAndRunRoutines() {
        val activeRoutines = backupRepository.getAllBackups().filter {
            it.isEnabled && it.scheduleConfig !is de.joelneumann.lojinha.domain.model.BackupScheduleConfig.OnDataChange
        }
        val now = System.currentTimeMillis()

        for (routine in activeRoutines) {
            try {
                val nextRetry = failureCooldownMap[routine.id] ?: 0L
                if (now < nextRetry) continue

                if (routine.type == BackupType.LOCAL) {
                    if (routine.backupLocationPath.isBlank()) continue
                    val dir = File(routine.backupLocationPath)
                    if (!dir.exists() || !dir.isDirectory) continue
                }

                val nextDue = routine.calculateNextDueTimestamp()
                if (now >= nextDue) {
                    executeRoutine(routine)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                e.printStackTrace()
            }
        }
    }

    suspend fun executeRoutine(routine: BackupRoutine) = executionMutex.withLock {
        if (routine.writeMode == BackupWriteMode.OVERWRITE_LATEST && backupRestoreService.isDatabaseEmpty()) {
            println("AutoBackupScheduler: Skipping OVERWRITE_LATEST backup for routine '${routine.name}' because database is empty.")
            failureCooldownMap[routine.id] = System.currentTimeMillis() + 60 * 60_000L
            return@withLock
        }

        var isTempFolder = false
        var tempFolderToDelete: File? = null
        try {
            var targetDir = File(routine.backupLocationPath)
            if (routine.type == BackupType.ONEDRIVE) {
                if (routine.backupLocationPath.isBlank() || !targetDir.exists() || !targetDir.isDirectory) {
                    targetDir = File(System.getProperty("java.io.tmpdir"), "lojinha_onedrive_temp_${System.currentTimeMillis()}").apply { mkdirs() }
                    isTempFolder = true
                    tempFolderToDelete = targetDir
                }
            } else {
                if (!targetDir.exists() || !targetDir.isDirectory) return@withLock
            }

            val routineForBackup = routine.copy(backupLocationPath = targetDir.absolutePath)
            val generatedFiles = backupRestoreService.executeRoutineBackup(routineForBackup)

            if (routine.type == BackupType.ONEDRIVE) {
                uploadToOneDriveIfConfigured(routine, generatedFiles)
            }

            // Re-fetch current routine to ensure it wasn't deleted or modified during slow backup upload
            val currentRoutine = backupRepository.getAllBackups().find { it.id == routine.id }
            if (currentRoutine != null) {
                val updated = currentRoutine.copy(lastBackupTimestamp = System.currentTimeMillis())
                backupRepository.saveBackupRoutine(updated)
            }
            failureCooldownMap.remove(routine.id)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            e.printStackTrace()
            failureCooldownMap[routine.id] = System.currentTimeMillis() + 15 * 60_000L
        } finally {
            if (isTempFolder && tempFolderToDelete != null) {
                tempFolderToDelete.deleteRecursively()
            }
        }
    }

    private suspend fun uploadToOneDriveIfConfigured(routine: BackupRoutine, files: List<File>) {
        val settings = settingsRepository?.getSettings() ?: error("OneDrive backup failed: SettingsRepository not configured")
        val oneDriveService = oneDriveBackupService ?: error("OneDrive backup failed: OneDriveBackupService not configured")
        val refreshToken = settings.oneDriveRefreshToken?.takeIf { it.isNotBlank() }
            ?: error("OneDrive backup failed: not authenticated (missing refresh token)")

        val clientId = settings.oneDriveClientId.ifBlank { "202e1c94-b152-4751-b0e6-a2a4b8eb4901" }
        val tenant = settings.oneDriveTenant.ifBlank { "common" }

        val tokenRes = oneDriveService.refreshAccessToken(clientId, refreshToken, tenant)
        val tokenData = tokenRes.getOrThrow()
        val accessToken = tokenData.accessToken ?: error("OneDrive refresh token response did not contain an access token")

        tokenData.refreshToken?.let { newRefresh ->
            if (newRefresh != refreshToken) {
                settingsRepository.updateSettings(settings.copy(oneDriveRefreshToken = newRefresh), notifyDataChanged = false)
            }
        }

        val remoteFolder = if (routine.backupLocationPath.isNotBlank() && routine.backupLocationPath.startsWith("/")) {
            routine.backupLocationPath
        } else {
            settings.oneDriveDefaultFolder.ifBlank { "/LojinhaBackups" }
        }

        for (fileOrDir in files) {
            if (fileOrDir.isDirectory) {
                val subFolder = "$remoteFolder/${fileOrDir.name}"
                fileOrDir.listFiles()?.forEach { subFile ->
                    if (subFile.isFile) {
                        oneDriveService.uploadFile(accessToken, subFile, subFolder).getOrThrow()
                    }
                }
            } else if (fileOrDir.isFile) {
                oneDriveService.uploadFile(accessToken, fileOrDir, remoteFolder).getOrThrow()
            }
        }
    }
}
