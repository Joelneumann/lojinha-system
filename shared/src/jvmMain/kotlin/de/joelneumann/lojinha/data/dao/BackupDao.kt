package de.joelneumann.lojinha.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import de.joelneumann.lojinha.data.entity.BackupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BackupDao {
    @Query("SELECT * FROM backup_routines ORDER BY name ASC")
    fun getBackupsFlow(): Flow<List<BackupEntity>>

    @Query("SELECT * FROM backup_routines ORDER BY name ASC")
    suspend fun getAllBackups(): List<BackupEntity>

    @Query("SELECT * FROM backup_routines WHERE id = :id")
    suspend fun getBackupById(id: String): BackupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateBackup(backup: BackupEntity)

    @Query("DELETE FROM backup_routines WHERE id = :id")
    suspend fun deleteBackup(id: String)

    @Query("DELETE FROM backup_routines")
    suspend fun deleteAllBackups()
}
