package de.joelneumann.lojinha

import de.joelneumann.lojinha.domain.model.*
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.utils.Formatting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        assertEquals(30000L, Formatting.parseWeightInputToGrams("30kg"))
        assertEquals(30000L, Formatting.parseWeightInputToGrams("30 kg"))
        assertEquals(30L, Formatting.parseWeightInputToGrams("30g"))
        assertEquals(30L, Formatting.parseWeightInputToGrams("30"))
        assertEquals(500L, Formatting.parseWeightInputToGrams("500g"))
        assertEquals(500L, Formatting.parseWeightInputToGrams("500"))
        assertEquals(250L, Formatting.parseWeightInputToGrams("0,25"))
    }

    @Test
    fun testAdminStockFormattingAndParsing() {
        // Test Piece unit type (must NOT be multiplied by 1000)
        assertEquals("10", Formatting.formatStockForAdmin(10L, UnitType.PIECE))
        assertEquals(10L, Formatting.parseAdminStockToDb("10", UnitType.PIECE))

        // Test Weight unit type (grams in DB <-> kg in Admin)
        assertEquals("2,5", Formatting.formatStockForAdmin(2500L, UnitType.WEIGHT))
        assertEquals(2500L, Formatting.parseAdminStockToDb("2,5", UnitType.WEIGHT))
        assertEquals(2500L, Formatting.parseAdminStockToDb("2.5", UnitType.WEIGHT))
        assertEquals(750L, Formatting.parseAdminStockToDb("0,75", UnitType.WEIGHT))
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

    @Test
    fun testI18nStronglyTyped() {
        val de = I18n.get(Language.DE)
        val en = I18n.get(Language.EN)
        val br = I18n.get(Language.BR)

        assertEquals("Lojinha POS", de.appTitle)
        assertEquals("Lojinha POS", en.appTitle)
        assertEquals("Lojinha POS", br.appTitle)

        assertEquals("Möchten Sie den Kauf über R$ 10,00 bestätigen?", de.confirmPurchaseMsg("R$ 10,00"))
        assertEquals("Confirm purchase for R$ 10,00?", en.confirmPurchaseMsg("R$ 10,00"))
        assertEquals("Confirmar compra no valor de R$ 10,00?", br.confirmPurchaseMsg("R$ 10,00"))
    }

    @Test
    fun testTimestampFormatting() {
        val timestamp = 1776534600000L
        val enStr = Formatting.formatTimestamp(timestamp, Language.EN)
        val deStr = Formatting.formatTimestamp(timestamp, Language.DE)
        val brStr = Formatting.formatTimestamp(timestamp, Language.BR)

        assertTrue(enStr.contains("AM") || enStr.contains("PM"))
        assertFalse(deStr.contains("AM") || deStr.contains("PM"))
        assertFalse(brStr.contains("AM") || brStr.contains("PM"))
    }
}
