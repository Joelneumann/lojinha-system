package de.joelneumann.lojinha

import de.joelneumann.lojinha.domain.model.*
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.utils.Formatting
import de.joelneumann.lojinha.ui.viewmodel.admin.AdminTransactionsViewModel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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

    @Test
    fun testUserAvatarDefaultsAndCustomization() {
        val user = User(id = "1", name = "Test User")
        assertEquals(AvatarType.INITIALS, user.avatar.type)
        assertEquals("😀", user.avatar.emoji)
        assertEquals("#1E293B", user.avatar.colorHex)

        val customAvatar = UserAvatarConfig(type = AvatarType.EMOJI, emoji = "🦊", colorHex = "#2563EB")
        val customUser = user.copy(avatar = customAvatar)
        assertEquals(AvatarType.EMOJI, customUser.avatar.type)
        assertEquals("🦊", customUser.avatar.emoji)
        assertEquals("#2563EB", customUser.avatar.colorHex)
    }

    @Test
    fun testUuidGeneration() {
        val uuid1 = de.joelneumann.lojinha.ui.utils.generateUuid()
        val uuid2 = de.joelneumann.lojinha.ui.utils.generateUuid()

        assertTrue(uuid1.isNotBlank())
        assertTrue(uuid2.isNotBlank())
        assertTrue(uuid1 != uuid2)
        assertEquals(36, uuid1.length) // Standard 8-4-4-4-12 UUID format length
    }

    @Test
    fun testPurchaseCorrectionAndStornoDeltaMath() {
        // Product 1: 5.00 BRL per piece (500 cents)
        val p1 = TransactionItem(
            productId = "prod1",
            productName = "Product 1",
            unitType = UnitType.PIECE,
            quantity = 2L,
            unitPriceAtPurchase = 500L
        )

        // 1. Initial Purchase
        val initialItems = listOf(p1)
        val initialCost = initialItems.sumOf { it.totalLinePrice } // 1000 cents (R$ 10,00)
        assertEquals(1000L, initialCost)

        // 2. First Correction: Down to 1 piece (Refund 5.00 BRL, restore 1 to stock)
        val itemsAfterCorrection1 = listOf(p1.copy(quantity = 1L))
        val cost1 = itemsAfterCorrection1.sumOf { it.totalLinePrice } // 500 cents
        val delta1 = initialCost - cost1 // +500 cents (refund)
        val stockChange1 = itemsAfterCorrection1[0].quantity - initialItems[0].quantity // -1 (stock restored by +1)
        assertEquals(500L, delta1)
        assertEquals(-1L, stockChange1)

        // 3. Second Correction: Increase up to 3 pieces (Charge 10.00 BRL, deduct 2 from stock)
        val itemsAfterCorrection2 = listOf(p1.copy(quantity = 3L))
        val cost2 = itemsAfterCorrection2.sumOf { it.totalLinePrice } // 1500 cents
        val delta2 = cost1 - cost2 // -1000 cents (charge)
        val stockChange2 = itemsAfterCorrection2[0].quantity - itemsAfterCorrection1[0].quantity // +2 (stock deducted by -2)
        assertEquals(-1000L, delta2)
        assertEquals(2L, stockChange2)

        // Verify cumulative modifiers
        val cumulativeDelta = delta1 + delta2 // -500 cents
        val netPurchaseAmount = -initialCost + cumulativeDelta // -1000 + (-500) = -1500 cents
        assertEquals(-1500L, netPurchaseAmount) // Exactly matches 3 pieces @ 500 cents

        // 4. Complete Storno: Zero out remaining items
        val zeroedItems = listOf(p1.copy(quantity = 0L))
        val cost3 = zeroedItems.sumOf { it.totalLinePrice } // 0 cents
        val delta3 = cost2 - cost3 // +1500 cents (full remaining refund)
        val stockChange3 = zeroedItems[0].quantity - itemsAfterCorrection2[0].quantity // -3 (stock restored by +3)
        assertEquals(1500L, delta3)
        assertEquals(-3L, stockChange3)

        // Verify full lifecycle balance & stock neutrality
        val totalRefundedAcrossAllSteps = delta1 + delta2 + delta3 // 500 - 1000 + 1500 = 1000 cents
        assertEquals(initialCost, totalRefundedAcrossAllSteps) // Net refund exactly equals initial cost

        val netStockChange = -2L + (-stockChange1) + (-stockChange2) + (-stockChange3) // -2 + 1 - 2 + 3 = 0
        assertEquals(0L, netStockChange) // Net inventory effect across full lifecycle is exactly 0
    }

    @Test
    fun testComputeEffectiveItemsAcrossSequentialCorrections() {
        val p1 = TransactionItem("p1", "Item 1", UnitType.PIECE, 2L, 500L)
        val p2 = TransactionItem("p2", "Item 2", UnitType.PIECE, 1L, 300L)
        val p3 = TransactionItem("p3", "Item 3", UnitType.PIECE, 4L, 200L)
        val origItems = listOf(p1, p2, p3)

        // 1. Initial purchase: no corrections
        val initialEffective = AdminTransactionsViewModel.computeEffectiveItems(origItems, emptyList())
        assertEquals(2L, initialEffective[0].quantity)
        assertEquals(1L, initialEffective[1].quantity)
        assertEquals(4L, initialEffective[2].quantity)

        // 2. Correction 1: Item 1 updated to 1 pc (Item 2 & 3 not in updated items)
        val corr1 = Transaction(
            id = "c1",
            userId = "u1",
            userNameSnapshot = "User",
            timestamp = 1000L,
            type = TransactionType.CORRECTION,
            totalAmount = 500L,
            items = listOf(p1.copy(quantity = 1L, previousQuantity = 2L))
        )
        val effectiveAfterCorr1 = AdminTransactionsViewModel.computeEffectiveItems(origItems, listOf(corr1))
        assertEquals(1L, effectiveAfterCorr1[0].quantity)
        assertEquals(1L, effectiveAfterCorr1[1].quantity)
        assertEquals(4L, effectiveAfterCorr1[2].quantity)

        // 3. Correction 2: Item 3 updated to 5 pcs
        val corr2 = Transaction(
            id = "c2",
            userId = "u1",
            userNameSnapshot = "User",
            timestamp = 2000L,
            type = TransactionType.CORRECTION,
            totalAmount = -200L,
            items = listOf(p3.copy(quantity = 5L, previousQuantity = 4L))
        )
        val effectiveAfterCorr2 = AdminTransactionsViewModel.computeEffectiveItems(origItems, listOf(corr1, corr2))
        assertEquals(1L, effectiveAfterCorr2[0].quantity)
        assertEquals(1L, effectiveAfterCorr2[1].quantity)
        assertEquals(5L, effectiveAfterCorr2[2].quantity)

        // 4. Cancellation zeroes out all items
        val cancellation = Transaction(
            id = "cancel1",
            userId = "u1",
            userNameSnapshot = "User",
            timestamp = 3000L,
            type = TransactionType.CANCELLATION,
            totalAmount = 1800L
        )
        val effectiveAfterCancel = AdminTransactionsViewModel.computeEffectiveItems(origItems, listOf(corr1, corr2), cancellation)
        assertTrue(effectiveAfterCancel.all { it.quantity == 0L })
    }

    @Test
    fun testTransactionItemSerializationWithPreviousQuantity() {
        val json = Json { ignoreUnknownKeys = true }

        // Item with previousQuantity
        val itemWithPrev = TransactionItem("p1", "Item 1", UnitType.PIECE, 1L, 500L, previousQuantity = 2L)
        val encoded = json.encodeToString(itemWithPrev)
        assertTrue(encoded.contains("\"previousQuantity\":2"))

        val decoded = json.decodeFromString<TransactionItem>(encoded)
        assertEquals(2L, decoded.previousQuantity)
        assertEquals(1L, decoded.quantity)

        // Backwards compatibility: JSON without previousQuantity
        val legacyJson = """{"productId":"p1","productName":"Item 1","unitType":"PIECE","quantity":3,"unitPriceAtPurchase":400}"""
        val legacyDecoded = json.decodeFromString<TransactionItem>(legacyJson)
        assertNull(legacyDecoded.previousQuantity)
        assertEquals(3L, legacyDecoded.quantity)
    }
}
