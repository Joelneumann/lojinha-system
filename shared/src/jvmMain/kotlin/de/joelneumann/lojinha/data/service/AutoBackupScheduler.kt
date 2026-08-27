package de.joelneumann.lojinha.data.service

import de.joelneumann.lojinha.domain.model.BackupRoutine
import de.joelneumann.lojinha.domain.repository.BackupRepository
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
    private val externalScope: CoroutineScope
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
            if (routine.backupLocationPath.isBlank()) continue
            val dir = File(routine.backupLocationPath)
            if (!dir.exists() || !dir.isDirectory) continue

            executeRoutine(routine)
        }
    }

    private suspend fun evaluateAndRunRoutines() {
        val activeRoutines = backupRepository.getAllBackups().filter {
            it.isEnabled && it.scheduleConfig !is de.joelneumann.lojinha.domain.model.BackupScheduleConfig.OnDataChange
        }
        val now = System.currentTimeMillis()

        for (routine in activeRoutines) {
            if (routine.backupLocationPath.isBlank()) continue
            val dir = File(routine.backupLocationPath)
            if (!dir.exists() || !dir.isDirectory) continue

            val nextDue = routine.calculateNextDueTimestamp()
            if (now >= nextDue) {
                executeRoutine(routine)
            }
        }
    }

    suspend fun executeRoutine(routine: BackupRoutine) {
        try {
            backupRestoreService.executeRoutineBackup(routine)
            val updated = routine.copy(lastBackupTimestamp = System.currentTimeMillis())
            backupRepository.saveBackupRoutine(updated)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
