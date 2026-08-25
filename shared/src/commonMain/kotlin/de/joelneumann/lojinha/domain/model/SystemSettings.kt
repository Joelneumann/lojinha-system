package de.joelneumann.lojinha.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class SystemSettings(
    val adminPasswordHash: String = "admin",
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
    val lastBackupTimestamp: Long? = null
)
