package de.joelneumann.lojinha.ui.components.admin.veggie

import de.joelneumann.lojinha.domain.model.VeggieQuantityScaling
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdminVeggieSettlementMatrixTest {

    @Test
    fun testEqualIntegerAndDecimalValuesAreNotHighlighted() {
        val twoUnits = VeggieQuantityScaling.fromWholeUnits(2)
        assertFalse(isDeliveredDifferentFromOrdered("2", twoUnits, deliveryAllowDecimals = false))
        assertFalse(isDeliveredDifferentFromOrdered("2", twoUnits, deliveryAllowDecimals = true))
        assertFalse(isDeliveredDifferentFromOrdered("2.0", twoUnits, deliveryAllowDecimals = true))
        assertFalse(isDeliveredDifferentFromOrdered("2,0", twoUnits, deliveryAllowDecimals = true))

        val oneAndHalf = VeggieQuantityScaling.fromDouble(1.5, allowDecimals = true)
        assertFalse(isDeliveredDifferentFromOrdered("1.5", oneAndHalf, deliveryAllowDecimals = true))
        assertFalse(isDeliveredDifferentFromOrdered("1,5", oneAndHalf, deliveryAllowDecimals = true))
    }

    @Test
    fun testDifferentValuesAreHighlighted() {
        val twoUnits = VeggieQuantityScaling.fromWholeUnits(2)
        assertTrue(isDeliveredDifferentFromOrdered("3", twoUnits, deliveryAllowDecimals = false))
        assertTrue(isDeliveredDifferentFromOrdered("1", twoUnits, deliveryAllowDecimals = false))
        assertTrue(isDeliveredDifferentFromOrdered("0", twoUnits, deliveryAllowDecimals = false))

        val oneAndHalf = VeggieQuantityScaling.fromDouble(1.5, allowDecimals = true)
        assertTrue(isDeliveredDifferentFromOrdered("1.25", oneAndHalf, deliveryAllowDecimals = true))
        assertTrue(isDeliveredDifferentFromOrdered("2", oneAndHalf, deliveryAllowDecimals = true))
        assertTrue(isDeliveredDifferentFromOrdered("0", oneAndHalf, deliveryAllowDecimals = true))
    }

    @Test
    fun testEmptyInputIsTreatedAsZero() {
        val twoUnits = VeggieQuantityScaling.fromWholeUnits(2)
        assertTrue(isDeliveredDifferentFromOrdered("", twoUnits, deliveryAllowDecimals = false))
        assertTrue(isDeliveredDifferentFromOrdered("   ", twoUnits, deliveryAllowDecimals = true))

        assertFalse(isDeliveredDifferentFromOrdered("", 0L, deliveryAllowDecimals = false))
        assertFalse(isDeliveredDifferentFromOrdered("", 0L, deliveryAllowDecimals = true))
    }
}
