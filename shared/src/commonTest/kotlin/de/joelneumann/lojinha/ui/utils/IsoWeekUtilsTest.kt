package de.joelneumann.lojinha.ui.utils

import de.joelneumann.lojinha.domain.model.VEGGIE_QTY_SCALE
import de.joelneumann.lojinha.domain.model.VeggieQuantityScaling
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IsoWeekUtilsTest {

    @Test
    fun testStandardMidYearDates() {
        // 2026-04-20 is a Monday in ISO Week 17 of 2026
        val info = IsoWeekUtils.fromLocalDate(LocalDate(2026, 4, 20))
        assertEquals(17, info.weekOfYear)
        assertEquals(2026, info.isoYear)

        // 2026-01-05 is Monday of Week 2 in 2026
        val week2 = IsoWeekUtils.fromLocalDate(LocalDate(2026, 1, 5))
        assertEquals(2, week2.weekOfYear)
        assertEquals(2026, week2.isoYear)
    }

    @Test
    fun testIsoYearBoundary_LateDecemberInWeek1OfNextYear() {
        // 2025-12-29 is a Monday; Jan 1 2026 is Thursday -> 2025-12-29 is in ISO Week 1 of 2026
        val dec29 = IsoWeekUtils.fromLocalDate(LocalDate(2025, 12, 29))
        assertEquals(1, dec29.weekOfYear)
        assertEquals(2026, dec29.isoYear)
    }

    @Test
    fun testIsoYearBoundary_EarlyJanuaryInWeek52Or53OfPreviousYear() {
        // 2027-01-01 is a Friday -> belongs to ISO Week 53 of 2026 (2026 starts on Thursday Jan 1, so has 53 weeks)
        val jan1_2027 = IsoWeekUtils.fromLocalDate(LocalDate(2027, 1, 1))
        assertEquals(53, jan1_2027.weekOfYear)
        assertEquals(2026, jan1_2027.isoYear)

        // 2023-01-01 is a Sunday -> belongs to ISO Week 52 of 2022
        val jan1_2023 = IsoWeekUtils.fromLocalDate(LocalDate(2023, 1, 1))
        assertEquals(52, jan1_2023.weekOfYear)
        assertEquals(2022, jan1_2023.isoYear)
    }

    @Test
    fun testVeggieQuantityScaling_parseAndFormat() {
        // Integer unit (allowDecimals = false)
        assertEquals(3L * VEGGIE_QTY_SCALE, VeggieQuantityScaling.parseQuantityScaled("3", allowDecimals = false))
        assertNull(VeggieQuantityScaling.parseQuantityScaled("3.5", allowDecimals = false))
        assertEquals("3", VeggieQuantityScaling.formatScaledQuantity(3000L, allowDecimals = false))

        // Decimal unit (allowDecimals = true)
        assertEquals(1350L, VeggieQuantityScaling.parseQuantityScaled("1.35", allowDecimals = true))
        assertEquals(1350L, VeggieQuantityScaling.parseQuantityScaled("1,35", allowDecimals = true))
        assertEquals("1,35", VeggieQuantityScaling.formatScaledQuantity(1350L, allowDecimals = true))
        assertEquals("1.35", VeggieQuantityScaling.formatScaledQuantity(1350L, allowDecimals = true, decimalSeparator = '.'))
        assertEquals("2", VeggieQuantityScaling.formatScaledQuantity(2000L, allowDecimals = true))
        assertEquals("0,5", VeggieQuantityScaling.formatScaledQuantity(500L, allowDecimals = true))
        assertEquals("0,125", VeggieQuantityScaling.formatScaledQuantity(125L, allowDecimals = true))
    }
}
