package de.joelneumann.lojinha.domain.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class VeggieSettlementMismatchTest {

    @Test
    fun exactMatchAndOneCentToleranceReturnNull() {
        assertNull(
            VeggieSettlementCalculator.detectPriceTotalMismatch(
                unitPriceCents = 1000L,
                totalSpentCents = 2500L,
                totalQtyScaled = 2500L
            )
        )
        assertNull(
            VeggieSettlementCalculator.detectPriceTotalMismatch(
                unitPriceCents = 1000L,
                totalSpentCents = 2501L,
                totalQtyScaled = 2500L
            )
        )
        assertNull(
            VeggieSettlementCalculator.detectPriceTotalMismatch(
                unitPriceCents = 1000L,
                totalSpentCents = 2499L,
                totalQtyScaled = 2500L
            )
        )
    }

    @Test
    fun missingValuesOrZeroQtyReturnNull() {
        assertNull(VeggieSettlementCalculator.detectPriceTotalMismatch(null, 2500L, 2500L))
        assertNull(VeggieSettlementCalculator.detectPriceTotalMismatch(1000L, null, 2500L))
        assertNull(VeggieSettlementCalculator.detectPriceTotalMismatch(1000L, 2500L, 0L))
    }

    @Test
    fun mismatchBeyondToleranceReturnsExpectedAndEffectivePrice() {
        val mismatch = VeggieSettlementCalculator.detectPriceTotalMismatch(
            unitPriceCents = 1000L,
            totalSpentCents = 2250L,
            totalQtyScaled = 2500L
        )
        assertNotNull(mismatch)
        assertEquals(2500L, mismatch.expectedTotalCents)
        assertEquals(900L, mismatch.effectiveUnitPriceCents)
    }
}
