package de.joelneumann.lojinha.domain.service

import de.joelneumann.lojinha.domain.model.Language
import de.joelneumann.lojinha.domain.model.SystemSettings
import de.joelneumann.lojinha.ui.i18n.EnglishStrings
import de.joelneumann.lojinha.ui.i18n.GermanStrings
import de.joelneumann.lojinha.ui.i18n.PortugueseStrings
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class VeggieAutoScheduleCalculatorTest {

    private val utc = TimeZone.UTC

    @Test
    fun testDisabledScheduleReturnsNull() {
        val settings = SystemSettings(veggieAutoOpenEnabled = false)
        val result = VeggieAutoScheduleCalculator.getNextAutoRoundSchedule(settings, nowMillis = 1000L, timeZone = utc)
        assertNull(result)
    }

    @Test
    fun testBeforeSlotThisWeekReturnsThisWeekSlot() {
        // Thursday 2026-11-12 at 06:00 UTC
        val now = LocalDateTime(2026, 11, 12, 6, 0).toInstant(utc).toEpochMilliseconds()
        // Configured for Thursday (iso 4) at 09:30
        val settings = SystemSettings(
            veggieAutoOpenEnabled = true,
            veggieAutoOpenDayOfWeek = 4,
            veggieAutoOpenTime = "09:30"
        )

        val result = VeggieAutoScheduleCalculator.getNextAutoRoundSchedule(settings, nowMillis = now, timeZone = utc)
        assertNotNull(result)
        assertEquals(4, result.targetDayIso)
        assertEquals(9, result.targetHour)
        assertEquals(30, result.targetMinute)
        assertEquals("09:30", result.timeFormatted)
        assertEquals(2026, result.scheduledYear)
        assertEquals(11, result.scheduledMonthNumber)
        assertEquals(12, result.scheduledDayOfMonth)
    }

    @Test
    fun testAfterSlotThisWeekReturnsNextWeekSlot() {
        // Thursday 2026-11-12 at 10:00 UTC (30 min after 09:30 slot)
        val now = LocalDateTime(2026, 11, 12, 10, 0).toInstant(utc).toEpochMilliseconds()
        // Configured for Thursday (iso 4) at 09:30
        val settings = SystemSettings(
            veggieAutoOpenEnabled = true,
            veggieAutoOpenDayOfWeek = 4,
            veggieAutoOpenTime = "09:30"
        )

        val result = VeggieAutoScheduleCalculator.getNextAutoRoundSchedule(settings, nowMillis = now, timeZone = utc)
        assertNotNull(result)
        assertEquals(4, result.targetDayIso)
        assertEquals("09:30", result.timeFormatted)
        assertEquals(2026, result.scheduledYear)
        assertEquals(11, result.scheduledMonthNumber)
        assertEquals(19, result.scheduledDayOfMonth) // Nov 12 + 7 days = Nov 19
    }

    @Test
    fun testFormatScheduleTextInDifferentLanguages() {
        val schedule = NextAutoRoundInfo(
            targetDayIso = 1, // Monday
            targetHour = 8,
            targetMinute = 0,
            timeFormatted = "08:00",
            scheduledYear = 2026,
            scheduledMonthNumber = 11,
            scheduledDayOfMonth = 16,
            epochMillis = 1731744000000L
        )

        val enText = schedule.formatScheduleText(EnglishStrings, Language.EN)
        assertEquals("Next round starts automatically on Monday, Nov 16 at 08:00", enText)

        val deText = schedule.formatScheduleText(GermanStrings, Language.DE)
        assertEquals("Nächste Runde startet automatisch am Montag, 16.11. um 08:00 Uhr", deText)

        val ptText = schedule.formatScheduleText(PortugueseStrings, Language.BR)
        assertEquals("Próxima rodada começa automaticamente: Segunda-feira, 16/11 às 08:00", ptText)
    }

    @Test
    fun testNormalizeTimeOfDayValidation() {
        assertEquals("08:05", VeggieAutoScheduleCalculator.normalizeTimeOfDay(" 8:5 "))
        assertEquals("00:00", VeggieAutoScheduleCalculator.normalizeTimeOfDay("0:0"))
        assertEquals("23:59", VeggieAutoScheduleCalculator.normalizeTimeOfDay("23:59"))
        assertNull(VeggieAutoScheduleCalculator.normalizeTimeOfDay(""))
        assertNull(VeggieAutoScheduleCalculator.normalizeTimeOfDay("24:00"))
        assertNull(VeggieAutoScheduleCalculator.normalizeTimeOfDay("08:60"))
        assertNull(VeggieAutoScheduleCalculator.normalizeTimeOfDay("08"))
        assertNull(VeggieAutoScheduleCalculator.normalizeTimeOfDay("abc:00"))
    }
}
