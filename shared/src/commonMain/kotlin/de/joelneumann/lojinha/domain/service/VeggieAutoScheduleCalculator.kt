package de.joelneumann.lojinha.domain.service

import de.joelneumann.lojinha.domain.model.Language
import de.joelneumann.lojinha.domain.model.SystemSettings
import de.joelneumann.lojinha.ui.i18n.AppStrings
import kotlinx.datetime.*
import kotlinx.serialization.Serializable

@Serializable
data class NextAutoRoundInfo(
    val targetDayIso: Int, // 1..7 (1 = Monday, 7 = Sunday)
    val targetHour: Int,
    val targetMinute: Int,
    val timeFormatted: String, // e.g. "08:00"
    val scheduledYear: Int,
    val scheduledMonthNumber: Int,
    val scheduledDayOfMonth: Int,
    val epochMillis: Long
) {
    fun formatScheduleText(strings: AppStrings, language: Language): String {
        val weekdayName = when (targetDayIso) {
            1 -> strings.weekdayMonday
            2 -> strings.weekdayTuesday
            3 -> strings.weekdayWednesday
            4 -> strings.weekdayThursday
            5 -> strings.weekdayFriday
            6 -> strings.weekdaySaturday
            7 -> strings.weekdaySunday
            else -> strings.weekdayMonday
        }

        val day = scheduledDayOfMonth.toString().padStart(2, '0')
        val month = scheduledMonthNumber.toString().padStart(2, '0')
        val formattedDate = when (language) {
            Language.EN -> {
                val monthNames = strings.monthNames
                val mName = monthNames.getOrElse(scheduledMonthNumber - 1) { "" }
                "$mName $scheduledDayOfMonth"
            }
            Language.DE -> "$day.$month."
            Language.BR -> "$day/$month"
        }

        return strings.nextAutoRoundScheduled(weekdayName, formattedDate, timeFormatted)
    }
}

object VeggieAutoScheduleCalculator {

    fun parseValidTimeOfDay(rawTime: String): Pair<Int, Int>? {
        val parts = rawTime.trim().split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull()?.takeIf { it in 0..23 } ?: return null
        val minute = parts[1].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
        return hour to minute
    }

    fun normalizeTimeOfDay(rawTime: String): String? {
        val (hour, minute) = parseValidTimeOfDay(rawTime) ?: return null
        return "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
    }

    fun parseTimeOfDay(rawTime: String): Pair<Int, Int> {
        parseValidTimeOfDay(rawTime)?.let { return it }
        val parts = rawTime.trim().split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 8
        val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        return hour to minute
    }

    private fun computeThisWeekSlotDateTime(
        settings: SystemSettings,
        nowMillis: Long,
        timeZone: TimeZone
    ): LocalDateTime {
        val targetDayIso = settings.veggieAutoOpenDayOfWeek.coerceIn(1, 7)
        val (targetHour, targetMinute) = parseTimeOfDay(settings.veggieAutoOpenTime)

        val nowLocal = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(timeZone)
        val currentDayIso = nowLocal.date.dayOfWeek.isoDayNumber // 1..7

        val mondayOfCurrentWeek = nowLocal.date.minus(DatePeriod(days = currentDayIso - 1))
        val scheduledDateThisWeek = mondayOfCurrentWeek.plus(DatePeriod(days = targetDayIso - 1))
        return LocalDateTime(
            year = scheduledDateThisWeek.year,
            monthNumber = scheduledDateThisWeek.monthNumber,
            dayOfMonth = scheduledDateThisWeek.dayOfMonth,
            hour = targetHour,
            minute = targetMinute,
            second = 0,
            nanosecond = 0
        )
    }

    fun computeThisWeekSlotMillis(
        settings: SystemSettings,
        nowMillis: Long,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): Long {
        return computeThisWeekSlotDateTime(settings, nowMillis, timeZone)
            .toInstant(timeZone)
            .toEpochMilliseconds()
    }

    /**
     * Calculates the next upcoming scheduled epoch timestamp and date/time for automatic round opening,
     * based on [settings.veggieAutoOpenEnabled], [settings.veggieAutoOpenDayOfWeek] (1=Monday..7=Sunday),
     * and [settings.veggieAutoOpenTime] ("HH:mm").
     *
     * Returns null if [settings.veggieAutoOpenEnabled] is false.
     */
    fun getNextAutoRoundSchedule(
        settings: SystemSettings,
        nowMillis: Long,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): NextAutoRoundInfo? {
        if (!settings.veggieAutoOpenEnabled) return null

        val scheduledDateTimeThisWeek = computeThisWeekSlotDateTime(settings, nowMillis, timeZone)
        val slotThisWeekMillis = scheduledDateTimeThisWeek.toInstant(timeZone).toEpochMilliseconds()

        val nextSlotDateTime = if (nowMillis < slotThisWeekMillis) {
            scheduledDateTimeThisWeek
        } else {
            val nextWeekDate = scheduledDateTimeThisWeek.date.plus(DatePeriod(days = 7))
            LocalDateTime(
                year = nextWeekDate.year,
                monthNumber = nextWeekDate.monthNumber,
                dayOfMonth = nextWeekDate.dayOfMonth,
                hour = scheduledDateTimeThisWeek.hour,
                minute = scheduledDateTimeThisWeek.minute,
                second = 0,
                nanosecond = 0
            )
        }

        val nextSlotMillis = nextSlotDateTime.toInstant(timeZone).toEpochMilliseconds()
        val targetHour = scheduledDateTimeThisWeek.hour
        val targetMinute = scheduledDateTimeThisWeek.minute
        val timeFormatted = "${targetHour.toString().padStart(2, '0')}:${targetMinute.toString().padStart(2, '0')}"

        return NextAutoRoundInfo(
            targetDayIso = settings.veggieAutoOpenDayOfWeek.coerceIn(1, 7),
            targetHour = targetHour,
            targetMinute = targetMinute,
            timeFormatted = timeFormatted,
            scheduledYear = nextSlotDateTime.year,
            scheduledMonthNumber = nextSlotDateTime.monthNumber,
            scheduledDayOfMonth = nextSlotDateTime.dayOfMonth,
            epochMillis = nextSlotMillis
        )
    }
}
