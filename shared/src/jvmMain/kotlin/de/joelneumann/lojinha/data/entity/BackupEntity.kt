package de.joelneumann.lojinha.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.joelneumann.lojinha.domain.model.BackupFileType
import de.joelneumann.lojinha.domain.model.BackupRoutine
import de.joelneumann.lojinha.domain.model.BackupScheduleConfig
import de.joelneumann.lojinha.domain.model.BackupType

@Entity(tableName = "backup_routines")
data class BackupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isEnabled: Boolean,
    val type: String,
    val fileType: String,
    val scheduleConfig: BackupScheduleConfig,
    val backupLocationPath: String,
    val lastBackupTimestamp: Long?
) {
    fun toDomain(): BackupRoutine = BackupRoutine(
        id = id,
        name = name,
        isEnabled = isEnabled,
        type = try { BackupType.valueOf(type) } catch (e: Exception) { BackupType.LOCAL },
        fileType = try { BackupFileType.valueOf(fileType) } catch (e: Exception) { BackupFileType.DB },
        scheduleConfig = scheduleConfig,
        backupLocationPath = backupLocationPath,
        lastBackupTimestamp = lastBackupTimestamp
    )

    companion object {
        fun fromDomain(routine: BackupRoutine): BackupEntity = BackupEntity(
            id = routine.id,
            name = routine.name,
            isEnabled = routine.isEnabled,
            type = routine.type.name,
            fileType = routine.fileType.name,
            scheduleConfig = routine.scheduleConfig,
            backupLocationPath = routine.backupLocationPath,
            lastBackupTimestamp = routine.lastBackupTimestamp
        )
    }
}
