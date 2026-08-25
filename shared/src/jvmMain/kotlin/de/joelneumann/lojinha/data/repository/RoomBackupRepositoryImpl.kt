package de.joelneumann.lojinha.data.repository

import de.joelneumann.lojinha.data.dao.BackupDao
import de.joelneumann.lojinha.data.entity.BackupEntity
import de.joelneumann.lojinha.domain.model.BackupRoutine
import de.joelneumann.lojinha.domain.repository.BackupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomBackupRepositoryImpl(
    private val backupDao: BackupDao
) : BackupRepository {

    override fun getBackupsFlow(): Flow<List<BackupRoutine>> {
        return backupDao.getBackupsFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getAllBackups(): List<BackupRoutine> {
        return backupDao.getAllBackups().map { it.toDomain() }
    }

    override suspend fun getBackupById(id: String): BackupRoutine? {
        return backupDao.getBackupById(id)?.toDomain()
    }

    override suspend fun saveBackupRoutine(routine: BackupRoutine) {
        backupDao.insertOrUpdateBackup(BackupEntity.fromDomain(routine))
    }

    override suspend fun deleteBackupRoutine(id: String) {
        backupDao.deleteBackup(id)
    }

    override suspend fun deleteAllBackupRoutines() {
        backupDao.deleteAllBackups()
    }
}
