package de.joelneumann.lojinha.ui.utils

import kotlinx.datetime.*
import kotlinx.serialization.Serializable

@Serializable
data class IsoWeekInfo(
    val weekOfYear: Int,
    val isoYear: Int
)

object IsoWeekUtils {
    fun fromEpochMillis(epochMillis: Long): IsoWeekInfo {
        return fromEpochMillis(epochMillis, TimeZone.currentSystemDefault())
    }

    fun fromEpochMillis(
        epochMillis: Long,
        timeZone: TimeZone
    ): IsoWeekInfo {
        val localDate = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(timeZone).date
        return fromLocalDate(localDate)
    }

    fun fromLocalDate(date: LocalDate): IsoWeekInfo {
        // ISO-8601 rule: Week 1 is the week containing the first Thursday of the year.
        val dayOfWeekIso = date.dayOfWeek.isoDayNumber // 1 (Monday) .. 7 (Sunday)
        val thursdayOfThisWeek = date.plus(DatePeriod(days = 4 - dayOfWeekIso))
        val isoYear = thursdayOfThisWeek.year
        val jan4 = LocalDate(isoYear, 1, 4)
        val mondayOfWeek1 = jan4.minus(DatePeriod(days = jan4.dayOfWeek.isoDayNumber - 1))
        val daysSinceWeek1Monday = mondayOfWeek1.daysUntil(date)
        val weekOfYear = (daysSinceWeek1Monday / 7) + 1
        return IsoWeekInfo(weekOfYear = weekOfYear, isoYear = isoYear)
    }
}
