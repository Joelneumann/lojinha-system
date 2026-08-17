package de.joelneumann.lojinha

import de.joelneumann.lojinha.domain.model.*
import de.joelneumann.lojinha.ui.utils.Formatting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DomainAndRulesTest {

    @Test
    fun testUserBarcodeParityRule() {
        // Valid: both present
        val userValid1 = User(
            id = "1",
            name = "Test",
            userBarcode = "BAR001",
            userBarcodeNumber = "BAR001"
        )
        assertEquals("BAR001", userValid1.userBarcode)

        // Valid: both null
        val userValid2 = User(
            id = "2",
            name = "Test2",
            userBarcode = null,
            userBarcodeNumber = null
        )
        assertNull(userValid2.userBarcode)

        // Invalid: barcode present, barcodeNumber null
        assertFailsWith<IllegalArgumentException> {
            User(
                id = "3",
                name = "Test3",
                userBarcode = "BAR003",
                userBarcodeNumber = null
            )
        }
    }

    @Test
    fun testUserInitials() {
        val u1 = User(id = "1", name = "Maria Silva")
        assertEquals("MS", u1.initials)

        val u2 = User(id = "2", name = "João")
        assertEquals("JO", u2.initials)
    }

    @Test
    fun testProductMarkupCalculations() {
        val product = Product(
            id = "p1",
            name = "Club Mate",
            basePrice = 800L, // R$ 8,00
            unitType = UnitType.PIECE
        )

        // No markup
        assertEquals(800L, product.calculateEffectiveUnitPrice(0.0))

        // Global markup 10%
        assertEquals(880L, product.calculateEffectiveUnitPrice(10.0))

        // Custom markup overrides global markup
        val productWithCustom = product.copy(customMarkupPercent = 20.0)
        assertEquals(960L, productWithCustom.calculateEffectiveUnitPrice(10.0))
    }

    @Test
    fun testMoneyFormatting() {
        assertEquals("R$ 10,50", Formatting.formatBrl(1050L))
        assertEquals("-R$ 12,30", Formatting.formatBrl(-1230L))
        assertEquals("R$ 0,00", Formatting.formatBrl(0L))
    }

    @Test
    fun testSecondaryCurrencyFormatting() {
        val formatted = Formatting.formatSecondaryCurrency(1550L, SecondaryCurrency.USD, 0.18)
        assertEquals(" (≈ $ 2.79)", formatted)

        val formattedEur = Formatting.formatSecondaryCurrency(1550L, SecondaryCurrency.EUR, 0.16)
        assertEquals(" (≈ € 2.48)", formattedEur)

        val none = Formatting.formatSecondaryCurrency(1550L, SecondaryCurrency.NONE, 0.18)
        assertEquals("", none)
    }

    @Test
    fun testWeightInputParser() {
        assertEquals(1500L, Formatting.parseWeightInputToGrams("1,5"))
        assertEquals(1500L, Formatting.parseWeightInputToGrams("1.5 kg"))
        assertEquals(500L, Formatting.parseWeightInputToGrams("500g"))
        assertEquals(250L, Formatting.parseWeightInputToGrams("0,25"))
    }

    @Test
    fun testTransactionLineTotal() {
        val pieceItem = TransactionItem(
            productId = "p1",
            productName = "Can",
            unitType = UnitType.PIECE,
            quantity = 3L,
            unitPriceAtPurchase = 800L
        )
        assertEquals(2400L, pieceItem.totalLinePrice)

        val weightItem = TransactionItem(
            productId = "p2",
            productName = "Apples",
            unitType = UnitType.WEIGHT,
            quantity = 1500L, // 1.5 kg
            unitPriceAtPurchase = 500L // R$ 5,00/kg
        )
        assertEquals(750L, weightItem.totalLinePrice) // R$ 7,50
    }
}
