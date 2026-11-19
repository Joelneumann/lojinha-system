package de.joelneumann.lojinha.data.service

import de.joelneumann.lojinha.domain.model.AdminNote
import de.joelneumann.lojinha.domain.model.Barcode
import de.joelneumann.lojinha.domain.model.Language
import de.joelneumann.lojinha.domain.model.Product
import de.joelneumann.lojinha.domain.model.ProductHistoryEntry
import de.joelneumann.lojinha.domain.model.ProductHistoryType
import de.joelneumann.lojinha.domain.model.SecondaryCurrency
import de.joelneumann.lojinha.domain.model.Transaction
import de.joelneumann.lojinha.domain.model.TransactionItem
import de.joelneumann.lojinha.domain.model.TransactionType
import de.joelneumann.lojinha.domain.model.UnitType
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.domain.model.UserAvatarConfig
import de.joelneumann.lojinha.domain.model.VeggieOrderEntry
import de.joelneumann.lojinha.domain.model.VeggieOrderRound
import de.joelneumann.lojinha.domain.model.VeggieProduct
import de.joelneumann.lojinha.domain.model.VeggieRoundProductSettlement
import de.joelneumann.lojinha.domain.model.VeggieRoundStatus
import de.joelneumann.lojinha.domain.model.VeggieUnitType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CsvExportFormatterTest {

    @Test
    fun testFormatUsersCsv_excludesDeletedUsersButKeepsInactive() {
        val users = listOf(
            User(
                id = "u-active",
                name = "Active User",
                balance = 1500,
                language = Language.EN,
                secondaryCurrency = SecondaryCurrency.NONE,
                isActive = true,
                isDeleted = false,
                avatar = UserAvatarConfig()
            ),
            User(
                id = "u-inactive",
                name = "Inactive User",
                balance = 0,
                language = Language.DE,
                secondaryCurrency = SecondaryCurrency.EUR,
                isActive = false,
                isDeleted = false,
                avatar = UserAvatarConfig()
            ),
            User(
                id = "u-deleted",
                name = "Deleted User",
                balance = -500,
                language = Language.BR,
                secondaryCurrency = SecondaryCurrency.NONE,
                isActive = false,
                isDeleted = true,
                avatar = UserAvatarConfig()
            )
        )

        val csv = CsvExportFormatter.formatUsersCsv(users)
        assertTrue(csv.startsWith(CsvExportFormatter.UTF8_BOM))
        assertTrue(csv.contains("u-active;Active User"))
        assertTrue(csv.contains("u-inactive;Inactive User"))
        assertFalse(csv.contains("u-deleted"))
        assertFalse(csv.contains("Deleted User"))
    }

    @Test
    fun testFormatVeggieProductsCsv_excludesDeletedVeggiesButKeepsInactive() {
        val units = listOf(VeggieUnitType(id = "unit-kg", name = "kg", allowDecimals = true))
        val veggies = listOf(
            VeggieProduct(
                id = "v-1",
                name = "Carrots",
                unitTypeId = "unit-kg",
                estimatedPriceCents = 650,
                isActive = true,
                isDeleted = false
            ),
            VeggieProduct(
                id = "v-2",
                name = "Inactive Parsnips",
                unitTypeId = "unit-kg",
                estimatedPriceCents = 900,
                isActive = false,
                isDeleted = false
            ),
            VeggieProduct(
                id = "v-3",
                name = "Deleted Turnips",
                unitTypeId = "unit-kg",
                estimatedPriceCents = 400,
                isActive = false,
                isDeleted = true
            )
        )

        val csv = CsvExportFormatter.formatVeggieProductsCsv(veggies, units)
        assertTrue(csv.startsWith(CsvExportFormatter.UTF8_BOM))
        assertTrue(csv.contains("v-1;Carrots"))
        assertTrue(csv.contains("v-2;Inactive Parsnips"))
        assertFalse(csv.contains("v-3"))
        assertFalse(csv.contains("Deleted Turnips"))
    }

    @Test
    fun testFormatProductsAndProductHistoryCsv() {
        val products = listOf(
            Product(
                id = "p-1",
                name = "Club; \"Mate\"",
                barcodes = listOf(Barcode("12345", "Bottle")),
                basePrice = 850,
                unitType = UnitType.PIECE,
                stockQuantity = 24,
                isActive = true
            )
        )
        val prodCsv = CsvExportFormatter.formatProductsCsv(products)
        assertTrue(prodCsv.startsWith(CsvExportFormatter.UTF8_BOM))
        assertTrue(prodCsv.contains("\"Club; \"\"Mate\"\"\""))

        val history = listOf(
            ProductHistoryEntry(
                id = "h-1",
                productId = "p-1",
                timestamp = 1700000000000L,
                type = ProductHistoryType.PURCHASE,
                userName = "Alice",
                quantity = -2,
                unitType = UnitType.PIECE,
                unitPrice = 850,
                isCancelled = false,
                stockAfter = 22
            ),
            ProductHistoryEntry(
                id = "h-2",
                productId = "p-1",
                timestamp = 1700000100000L,
                type = ProductHistoryType.STOCK_ADJUSTMENT,
                userName = null,
                quantity = 10,
                unitType = UnitType.PIECE,
                note = "Restock",
                stockAfter = 32
            )
        )
        val histCsv = CsvExportFormatter.formatProductHistoryCsv(history)
        assertTrue(histCsv.startsWith(CsvExportFormatter.UTF8_BOM))
        assertTrue(histCsv.contains("h-1;p-1;1700000000000;PURCHASE;Alice;-2;PIECE"))
        assertTrue(histCsv.contains("h-2;p-1;1700000100000;STOCK_ADJUSTMENT;;10;PIECE;;false;32;Restock"))
    }

    @Test
    fun testFormatNotesTransactionsAndVeggieOrdersCsv() {
        val notesCsv = CsvExportFormatter.formatAdminNotesCsv(
            listOf(AdminNote(id = "n-1", userId = "u-1", userNameSnapshot = "Bob", timestamp = 100L, message = "Need milk"))
        )
        assertTrue(notesCsv.contains("n-1;u-1;Bob;100;Need milk"))

        val txCsv = CsvExportFormatter.formatTransactionsCsv(
            listOf(
                Transaction(
                    id = "t-1",
                    userId = "u-1",
                    userNameSnapshot = "Bob",
                    timestamp = 200L,
                    type = TransactionType.PURCHASE,
                    totalAmount = -500,
                    items = listOf(TransactionItem("p-1", "Milk", UnitType.PIECE, 1, 500)),
                    userBalanceBefore = 1000,
                    userBalanceAfter = 500
                )
            )
        )
        assertTrue(txCsv.contains("t-1;u-1;Bob;200;PURCHASE"))

        val round = VeggieOrderRound(
            id = "r-1",
            openedAt = 1700000000000L,
            status = VeggieRoundStatus.SETTLED,
            buyerOperationFeeCents = 100,
            transportFeeCents = 200
        )
        val entry = VeggieOrderEntry(
            id = "e-1",
            roundId = "r-1",
            userId = "u-1",
            userNameSnapshot = "Bob",
            veggieProductId = "v-1",
            veggieNameSnapshot = "Carrots",
            unitNameSnapshot = "kg",
            allowDecimalsSnapshot = true,
            orderedQuantityScaled = 1500,
            actualQuantityScaled = 1500,
            effectiveUnitPriceCents = 600,
            finalItemTotalCents = 900
        )
        val settlement = VeggieRoundProductSettlement(
            roundId = "r-1",
            veggieProductId = "v-1",
            marketUnitPriceCents = 600,
            totalSpentCents = 900
        )
        val veggieOrdersCsv = CsvExportFormatter.formatVeggieOrdersCsv(
            rounds = listOf(round),
            entriesByRoundId = mapOf("r-1" to listOf(entry)),
            settlementsByRoundId = mapOf("r-1" to listOf(settlement))
        )
        assertTrue(veggieOrdersCsv.contains("r-1;"))
        assertTrue(veggieOrdersCsv.contains("e-1;u-1;Bob;v-1;Carrots;kg;true"))
    }
}
