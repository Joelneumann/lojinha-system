package de.joelneumann.lojinha.data.entity

import androidx.room.ColumnInfo
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
    val supportEmail: String? = null,
    val autoUpdateExchangeRates: Boolean = true,
    val lastExchangeRateUpdateTimestamp: Long? = null,
    val yellowBalanceWarningThresholdCents: Long = 300_00L,
    val redBalanceWarningThresholdCents: Long = 0L,
    @ColumnInfo(defaultValue = "0") val veggieAutoOpenEnabled: Boolean = false,
    @ColumnInfo(defaultValue = "1") val veggieAutoOpenDayOfWeek: Int = 1,
    @ColumnInfo(defaultValue = "'08:00'") val veggieAutoOpenTime: String = "08:00",
    @ColumnInfo(defaultValue = "0") val defaultVeggieBuyerFeeCents: Long = 0L,
    @ColumnInfo(defaultValue = "0") val defaultVeggieTransportFeeCents: Long = 0L,
    @ColumnInfo(defaultValue = "''") val buyerPasswordHash: String = ""
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
        supportEmail = supportEmail,
        autoUpdateExchangeRates = autoUpdateExchangeRates,
        lastExchangeRateUpdateTimestamp = lastExchangeRateUpdateTimestamp,
        yellowBalanceWarningThresholdCents = yellowBalanceWarningThresholdCents,
        redBalanceWarningThresholdCents = redBalanceWarningThresholdCents,
        veggieAutoOpenEnabled = veggieAutoOpenEnabled,
        veggieAutoOpenDayOfWeek = veggieAutoOpenDayOfWeek,
        veggieAutoOpenTime = veggieAutoOpenTime,
        defaultVeggieBuyerFeeCents = defaultVeggieBuyerFeeCents,
        defaultVeggieTransportFeeCents = defaultVeggieTransportFeeCents,
        buyerPasswordHash = buyerPasswordHash
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
            supportEmail = settings.supportEmail,
            autoUpdateExchangeRates = settings.autoUpdateExchangeRates,
            lastExchangeRateUpdateTimestamp = settings.lastExchangeRateUpdateTimestamp,
            yellowBalanceWarningThresholdCents = settings.yellowBalanceWarningThresholdCents,
            redBalanceWarningThresholdCents = settings.redBalanceWarningThresholdCents,
            veggieAutoOpenEnabled = settings.veggieAutoOpenEnabled,
            veggieAutoOpenDayOfWeek = settings.veggieAutoOpenDayOfWeek,
            veggieAutoOpenTime = settings.veggieAutoOpenTime,
            defaultVeggieBuyerFeeCents = settings.defaultVeggieBuyerFeeCents,
            defaultVeggieTransportFeeCents = settings.defaultVeggieTransportFeeCents,
            buyerPasswordHash = settings.buyerPasswordHash
        )
    }
}
