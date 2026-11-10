package de.joelneumann.lojinha.ui.components.veggie

import kotlin.test.Test
import kotlin.test.assertEquals

class VeggieAmountInputFilterTest {

    @Test
    fun testIntegerFiltering_rejectsNonDigits() {
        assertEquals("123", filterVeggieAmountInput("123", allowDecimals = false))
        assertEquals("123", filterVeggieAmountInput("1a2b3c", allowDecimals = false))
        assertEquals("12", filterVeggieAmountInput("1.2", allowDecimals = false))
        assertEquals("12", filterVeggieAmountInput("1,2", allowDecimals = false))
        assertEquals("", filterVeggieAmountInput("abc!@#", allowDecimals = false))
    }

    @Test
    fun testDecimalFiltering_allowsSingleSeparatorAndDigits() {
        assertEquals("1.5", filterVeggieAmountInput("1.5", allowDecimals = true))
        assertEquals("1,5", filterVeggieAmountInput("1,5", allowDecimals = true))
        assertEquals("2.5", filterVeggieAmountInput("2.5.6", allowDecimals = true))
        assertEquals("2,5", filterVeggieAmountInput("2,5,6", allowDecimals = true))
        assertEquals("1.5", filterVeggieAmountInput("1.5abc", allowDecimals = true))
        assertEquals("1.234", filterVeggieAmountInput("1.23456", allowDecimals = true))
    }
}
