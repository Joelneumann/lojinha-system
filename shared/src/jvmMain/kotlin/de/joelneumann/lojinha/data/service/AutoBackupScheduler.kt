package de.joelneumann.lojinha.data.service

import de.joelneumann.lojinha.domain.model.BackupRoutine
import de.joelneumann.lojinha.domain.model.BackupType
import de.joelneumann.lojinha.domain.repository.BackupRepository
import de.joelneumann.lojinha.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class AutoBackupScheduler(
    private val backupRestoreService: BackupRestoreService,
    private val backupRepository: BackupRepository,
    private val externalScope: CoroutineScope,
    private val settingsRepository: SettingsRepository? = null,
    private val oneDriveBackupService: OneDriveBackupService? = null
) {
    private var schedulerJob: Job? = null

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
            if (debounceMs > 0) {
                delay(debounceMs)
            }
            evaluateAndRunDataChangeRoutines()
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
            if (routine.type == BackupType.LOCAL) {
                if (routine.backupLocationPath.isBlank()) continue
                val dir = File(routine.backupLocationPath)
                if (!dir.exists() || !dir.isDirectory) continue
            }

            val nextDue = routine.calculateNextDueTimestamp()
            if (now >= nextDue) {
                executeRoutine(routine)
            }
        }
    }

    suspend fun executeRoutine(routine: BackupRoutine) {
        try {
            var targetDir = File(routine.backupLocationPath)
            if (routine.type == BackupType.ONEDRIVE) {
                if (routine.backupLocationPath.isBlank() || !targetDir.exists() || !targetDir.isDirectory) {
                    targetDir = File(System.getProperty("java.io.tmpdir"), "lojinha_onedrive_temp").apply { mkdirs() }
                }
            } else {
                if (!targetDir.exists() || !targetDir.isDirectory) return
            }

            val routineForBackup = routine.copy(backupLocationPath = targetDir.absolutePath)
            val generatedFile = backupRestoreService.executeRoutineBackup(routineForBackup)

            if (routine.type == BackupType.ONEDRIVE) {
                uploadToOneDriveIfConfigured(routine, generatedFile)
            }

            val updated = routine.copy(lastBackupTimestamp = System.currentTimeMillis())
            backupRepository.saveBackupRoutine(updated)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun uploadToOneDriveIfConfigured(routine: BackupRoutine, fileOrDir: File) {
        val settings = settingsRepository?.getSettings() ?: return
        val oneDriveService = oneDriveBackupService ?: return
        val refreshToken = settings.oneDriveRefreshToken ?: return
        if (refreshToken.isBlank()) return

        val clientId = settings.oneDriveClientId.ifBlank { "202e1c94-b152-4751-b0e6-a2a4b8eb4901" }

        val tokenRes = oneDriveService.refreshAccessToken(clientId, refreshToken)
        val tokenData = tokenRes.getOrNull() ?: return
        val accessToken = tokenData.accessToken ?: return

        tokenData.refreshToken?.let { newRefresh ->
            if (newRefresh != refreshToken) {
                settingsRepository.updateSettings(settings.copy(oneDriveRefreshToken = newRefresh))
            }
        }

        val remoteFolder = if (routine.backupLocationPath.isNotBlank() && routine.backupLocationPath.startsWith("/")) {
            routine.backupLocationPath
        } else {
            settings.oneDriveDefaultFolder.ifBlank { "/LojinhaBackups" }
        }

        if (fileOrDir.isDirectory) {
            val subFolder = "$remoteFolder/${fileOrDir.name}"
            fileOrDir.listFiles()?.forEach { subFile ->
                if (subFile.isFile) {
                    oneDriveService.uploadFile(accessToken, subFile, subFolder)
                }
            }
        } else if (fileOrDir.isFile) {
            oneDriveService.uploadFile(accessToken, fileOrDir, remoteFolder)
        }
    }
}
