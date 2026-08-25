package de.joelneumann.lojinha.domain.repository

import de.joelneumann.lojinha.domain.model.BackupRoutine
import kotlinx.coroutines.flow.Flow

interface BackupRepository {
    fun getBackupsFlow(): Flow<List<BackupRoutine>>
    suspend fun getAllBackups(): List<BackupRoutine>
    suspend fun getBackupById(id: String): BackupRoutine?
    suspend fun saveBackupRoutine(routine: BackupRoutine)
    suspend fun deleteBackupRoutine(id: String)
    suspend fun deleteAllBackupRoutines()
}
