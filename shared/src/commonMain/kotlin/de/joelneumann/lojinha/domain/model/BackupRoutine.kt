package de.joelneumann.lojinha.domain.model

import kotlinx.serialization.Serializable
import java.util.Calendar

@Serializable
enum class BackupType {
    LOCAL
}

@Serializable
enum class BackupFileType {
    DB,
    CSV,
    BOTH
}

@Serializable
sealed interface BackupScheduleConfig {
    @Serializable
    data class Timed(
        val timeOfDay: String = "02:00" // HH:mm 24h format
    ) : BackupScheduleConfig

    @Serializable
    data class Interval(
        val intervalHours: Int = 1,
        val intervalMinutes: Int = 0,
        val anchorStartTimestamp: Long = System.currentTimeMillis()
    ) : BackupScheduleConfig
}

@Serializable
data class BackupRoutine(
    val id: String = "",
    val name: String = "",
    val isEnabled: Boolean = true,
    val type: BackupType = BackupType.LOCAL,
    val fileType: BackupFileType = BackupFileType.DB,
    val scheduleConfig: BackupScheduleConfig = BackupScheduleConfig.Timed("02:00"),
    val backupLocationPath: String = "",
    val lastBackupTimestamp: Long? = null
) {
    fun calculateNextDueTimestamp(): Long {
        return when (val config = scheduleConfig) {
            is BackupScheduleConfig.Timed -> {
                val parts = config.timeOfDay.split(":")
                val hour = parts.getOrNull(0)?.toIntOrNull() ?: 2
                val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, hour)
                cal.set(Calendar.MINUTE, minute)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)

                val last = lastBackupTimestamp ?: 0L
                if (cal.timeInMillis <= last) {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
                cal.timeInMillis
            }
            is BackupScheduleConfig.Interval -> {
                val intervalMs = (config.intervalHours * 3600L + config.intervalMinutes * 60L) * 1000L
                if (intervalMs <= 0) return Long.MAX_VALUE
                val last = lastBackupTimestamp ?: config.anchorStartTimestamp
                last + intervalMs
            }
        }
    }
}
