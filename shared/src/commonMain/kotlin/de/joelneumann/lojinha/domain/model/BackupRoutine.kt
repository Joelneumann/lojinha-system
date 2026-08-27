package de.joelneumann.lojinha.domain.model

import de.joelneumann.lojinha.ui.utils.currentTimeMillis
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable

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
enum class BackupWriteMode {
    CREATE_NEW_FILE,
    OVERWRITE_LATEST
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
        val anchorStartTimestamp: Long = currentTimeMillis()
    ) : BackupScheduleConfig

    @Serializable
    data class OnDataChange(
        val debounceMs: Long = 1000L
    ) : BackupScheduleConfig
}

@Serializable
data class BackupRoutine(
    val id: String = "",
    val name: String = "",
    val isEnabled: Boolean = true,
    val type: BackupType = BackupType.LOCAL,
    val fileType: BackupFileType = BackupFileType.DB,
    val writeMode: BackupWriteMode = BackupWriteMode.CREATE_NEW_FILE,
    val scheduleConfig: BackupScheduleConfig = BackupScheduleConfig.Timed("02:00"),
    val backupLocationPath: String = "",
    val lastBackupTimestamp: Long? = null
) {
    fun calculateNextDueTimestamp(): Long {
        return when (val config = scheduleConfig) {
            is BackupScheduleConfig.Timed -> {
                val parts = config.timeOfDay.split(":")
                val targetHour = parts.getOrNull(0)?.toIntOrNull() ?: 2
                val targetMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0

                val tz = TimeZone.currentSystemDefault()
                val nowInstant = Clock.System.now()
                val nowLdt = nowInstant.toLocalDateTime(tz)

                var targetLdt = LocalDateTime(
                    year = nowLdt.year,
                    month = nowLdt.month,
                    dayOfMonth = nowLdt.dayOfMonth,
                    hour = targetHour,
                    minute = targetMinute,
                    second = 0,
                    nanosecond = 0
                )
                var targetMs = targetLdt.toInstant(tz).toEpochMilliseconds()
                val last = lastBackupTimestamp ?: 0L

                if (targetMs <= last || targetMs <= nowInstant.toEpochMilliseconds()) {
                    targetMs += 86400000L
                }
                targetMs
            }
            is BackupScheduleConfig.Interval -> {
                val intervalMs = (config.intervalHours * 3600L + config.intervalMinutes * 60L) * 1000L
                if (intervalMs <= 0) return Long.MAX_VALUE
                val last = lastBackupTimestamp ?: config.anchorStartTimestamp
                last + intervalMs
            }
            is BackupScheduleConfig.OnDataChange -> {
                Long.MAX_VALUE
            }
        }
    }
}
