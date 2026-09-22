package de.joelneumann.lojinha.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.joelneumann.lojinha.domain.model.SystemSettings

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val adminPasswordHash: String,
    val globalMarkupPercent: Double,
    val usdExchangeRate: Double,
    val eurExchangeRate: Double,
    val inactivityTimeoutMinutes: Int,
    val backupLocationPath: String = "",
    val autoBackupEnabled: Boolean = false,
    val autoBackupFormat: String = "DB",
    val autoBackupScheduleType: String = "DAILY",
    val autoBackupTime: String = "02:00",
    val autoBackupIntervalHours: Int = 24,
    val lastBackupTimestamp: Long? = null,
    val oneDriveClientId: String = "202e1c94-b152-4751-b0e6-a2a4b8eb4901",
    val oneDriveRefreshToken: String? = null,
    val oneDriveAccountEmail: String? = null,
    val oneDriveAccountName: String? = null,
    val oneDriveDefaultFolder: String = "/LojinhaBackups",
    val oneDriveTenant: String = "common",
    val supportEmail: String? = null
) {
    fun toDomain(): SystemSettings = SystemSettings(
        adminPasswordHash = adminPasswordHash,
        globalMarkupPercent = globalMarkupPercent,
        usdExchangeRate = usdExchangeRate,
        eurExchangeRate = eurExchangeRate,
        inactivityTimeoutMinutes = inactivityTimeoutMinutes,
        backupLocationPath = backupLocationPath,
        autoBackupEnabled = autoBackupEnabled,
        autoBackupFormat = autoBackupFormat,
        autoBackupScheduleType = autoBackupScheduleType,
        autoBackupTime = autoBackupTime,
        autoBackupIntervalHours = autoBackupIntervalHours,
        lastBackupTimestamp = lastBackupTimestamp,
        oneDriveClientId = oneDriveClientId,
        oneDriveRefreshToken = oneDriveRefreshToken,
        oneDriveAccountEmail = oneDriveAccountEmail,
        oneDriveAccountName = oneDriveAccountName,
        oneDriveDefaultFolder = oneDriveDefaultFolder,
        oneDriveTenant = oneDriveTenant,
        supportEmail = supportEmail
    )

    companion object {
        fun fromDomain(settings: SystemSettings): SettingsEntity = SettingsEntity(
            id = 1,
            adminPasswordHash = settings.adminPasswordHash,
            globalMarkupPercent = settings.globalMarkupPercent,
            usdExchangeRate = settings.usdExchangeRate,
            eurExchangeRate = settings.eurExchangeRate,
            inactivityTimeoutMinutes = settings.inactivityTimeoutMinutes,
            backupLocationPath = settings.backupLocationPath,
            autoBackupEnabled = settings.autoBackupEnabled,
            autoBackupFormat = settings.autoBackupFormat,
            autoBackupScheduleType = settings.autoBackupScheduleType,
            autoBackupTime = settings.autoBackupTime,
            autoBackupIntervalHours = settings.autoBackupIntervalHours,
            lastBackupTimestamp = settings.lastBackupTimestamp,
            oneDriveClientId = settings.oneDriveClientId,
            oneDriveRefreshToken = settings.oneDriveRefreshToken,
            oneDriveAccountEmail = settings.oneDriveAccountEmail,
            oneDriveAccountName = settings.oneDriveAccountName,
            oneDriveDefaultFolder = settings.oneDriveDefaultFolder,
            oneDriveTenant = settings.oneDriveTenant,
            supportEmail = settings.supportEmail
        )
    }
}
