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
    val inactivityTimeoutMinutes: Int
) {
    fun toDomain(): SystemSettings = SystemSettings(
        adminPasswordHash = adminPasswordHash,
        globalMarkupPercent = globalMarkupPercent,
        usdExchangeRate = usdExchangeRate,
        eurExchangeRate = eurExchangeRate,
        inactivityTimeoutMinutes = inactivityTimeoutMinutes
    )

    companion object {
        fun fromDomain(settings: SystemSettings): SettingsEntity = SettingsEntity(
            id = 1,
            adminPasswordHash = settings.adminPasswordHash,
            globalMarkupPercent = settings.globalMarkupPercent,
            usdExchangeRate = settings.usdExchangeRate,
            eurExchangeRate = settings.eurExchangeRate,
            inactivityTimeoutMinutes = settings.inactivityTimeoutMinutes
        )
    }
}
