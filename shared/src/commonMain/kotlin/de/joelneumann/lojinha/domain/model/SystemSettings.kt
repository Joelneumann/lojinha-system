package de.joelneumann.lojinha.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class SystemSettings(
    val adminPasswordHash: String = "8c6976e5b5410415bde908bd4dee15dfb167a9c873fc4bb8a81f6f2ab448a918", // SHA-256 of "admin"
    val globalMarkupPercent: Double = 0.0,
    val usdExchangeRate: Double = 0.18,
    val eurExchangeRate: Double = 0.16,
    val inactivityTimeoutMinutes: Int = 3,
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
)
