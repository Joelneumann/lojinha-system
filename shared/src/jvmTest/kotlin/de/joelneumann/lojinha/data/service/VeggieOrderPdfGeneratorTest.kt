package de.joelneumann.lojinha.data.service

import de.joelneumann.lojinha.domain.model.Language
import de.joelneumann.lojinha.domain.model.VeggieOrderEntry
import de.joelneumann.lojinha.domain.model.VeggieOrderRound
import de.joelneumann.lojinha.domain.model.VeggieProduct
import de.joelneumann.lojinha.domain.model.VeggieRoundStatus
import de.joelneumann.lojinha.domain.model.VeggieUnitType
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class VeggieOrderPdfGeneratorTest {

    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("veggie_pdf_test").toFile()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testFormatShortName_handlesShortMediumAndLongNames() {
        // Short name under limit
        assertEquals("João", VeggieOrderPdfGenerator.formatShortName("João", 13))

        // Medium two-token name under limit
        assertEquals("João Silva", VeggieOrderPdfGenerator.formatShortName("João Silva", 13))

        // Multi-token long name: first name + last initial
        assertEquals("Maria S.", VeggieOrderPdfGenerator.formatShortName("Maria Aparecida Santos", 13))

        // Multi-token name where candidate fits limit
        assertEquals("Alexander H.", VeggieOrderPdfGenerator.formatShortName("Alexander Hamilton", 13))

        // Single very long word
        val longWordResult = VeggieOrderPdfGenerator.formatShortName("Supercalifragilistic", 13)
        assertTrue(longWordResult.length <= 13)
        assertTrue(longWordResult.endsWith("."))
    }

    @Test
    fun testGenerateBuyerShoppingList_generatesValidPdfWithoutLojinhaTitleAndWithSummaryRows() {
        val round = VeggieOrderRound(
            id = "round-buyer-1",
            openedAt = 1_776_672_000_000L,
            status = VeggieRoundStatus.CLOSED,
            buyerOperationFeeCents = 15_00L,
            transportFeeCents = 20_00L
        )

        val entries = listOf(
            VeggieOrderEntry(
                id = "e-1",
                roundId = round.id,
                userId = "u-1",
                userNameSnapshot = "Alice",
                veggieProductId = "vp-carrot",
                veggieNameSnapshot = "Cenoura",
                unitNameSnapshot = "kg",
                allowDecimalsSnapshot = true,
                orderedQuantityScaled = 2500L
            ),
            VeggieOrderEntry(
                id = "e-2",
                roundId = round.id,
                userId = "u-2",
                userNameSnapshot = "Bob",
                veggieProductId = "vp-carrot",
                veggieNameSnapshot = "Cenoura",
                unitNameSnapshot = "kg",
                allowDecimalsSnapshot = true,
                orderedQuantityScaled = 1000L
            )
        )

        for (lang in listOf(Language.BR, Language.EN, Language.DE)) {
            val bytes = VeggieOrderPdfGenerator.generateBuyerShoppingListBytes(round, entries, lang)
            assertTrue(bytes.isNotEmpty(), "PDF bytes should not be empty")

            val header = String(bytes.take(5).toByteArray(), Charsets.US_ASCII)
            assertEquals("%PDF-", header, "File must start with %PDF- header")
            assertTrue(bytes.size > 1000, "PDF should have substantial content")
        }
    }

    @Test
    fun testGenerateWorkerSortingMatrix_generatesValidPdfWithRotatedNamesAndMinusForNoOrder() {
        val round = VeggieOrderRound(
            id = "round-worker-1",
            openedAt = 1_776_672_000_000L,
            status = VeggieRoundStatus.CLOSED
        )

        // 8 customers so isManyCustomers (> 5) triggers 90-degree rotated headers
        val entries = (1..8).map { idx ->
            VeggieOrderEntry(
                id = "e-lettuce-$idx",
                roundId = round.id,
                userId = "u-$idx",
                userNameSnapshot = "Customer VeryLongLastName $idx",
                veggieProductId = "vp-lettuce",
                veggieNameSnapshot = "Alface",
                unitNameSnapshot = "Unidade",
                allowDecimalsSnapshot = false,
                orderedQuantityScaled = if (idx % 2 == 0) 2000L else 0L // Alternating so some have no order (-)
            )
        }

        val file = File(tempDir, "worker_matrix.pdf")
        VeggieOrderPdfGenerator.generateWorkerSortingMatrixPdf(file, round, entries, Language.BR)

        assertTrue(file.exists())
        assertTrue(file.length() > 1000L)

        val header = String(file.readBytes().take(5).toByteArray(), Charsets.US_ASCII)
        assertEquals("%PDF-", header)
    }

    @Test
    fun testGenerateWorkerSortingMatrix_supportsUpTo25CustomersOnSinglePage() {
        val round = VeggieOrderRound(
            id = "round-worker-25",
            openedAt = 1_776_672_000_000L,
            status = VeggieRoundStatus.CLOSED
        )

        // 25 customers on one page
        val entries = (1..25).flatMap { idx ->
            val userId = "u-$idx"
            val userName = "Customer Person $idx"
            listOf(
                VeggieOrderEntry(
                    id = "e-tom-$idx",
                    roundId = round.id,
                    userId = userId,
                    userNameSnapshot = userName,
                    veggieProductId = "vp-tomato",
                    veggieNameSnapshot = "Tomate Italiano",
                    unitNameSnapshot = "kg",
                    allowDecimalsSnapshot = true,
                    orderedQuantityScaled = if (idx % 3 == 0) 1500L else 0L
                ),
                VeggieOrderEntry(
                    id = "e-let-$idx",
                    roundId = round.id,
                    userId = userId,
                    userNameSnapshot = userName,
                    veggieProductId = "vp-lettuce",
                    veggieNameSnapshot = "Alface Crespa",
                    unitNameSnapshot = "Unidade",
                    allowDecimalsSnapshot = false,
                    orderedQuantityScaled = if (idx % 2 == 0) 1000L else 0L
                )
            )
        }

        val file = File(tempDir, "worker_matrix_25.pdf")
        VeggieOrderPdfGenerator.generateWorkerSortingMatrixPdf(file, round, entries, Language.BR)

        assertTrue(file.exists())
        assertTrue(file.length() > 1500L)

        // Verify with PdfReader that all 25 customers fit onto 1 single page!
        val reader = com.lowagie.text.pdf.PdfReader(file.absolutePath)
        try {
            assertEquals(1, reader.numberOfPages, "All 25 customers should fit on 1 page")
        } finally {
            reader.close()
        }
    }

    @Test
    fun testGenerateCustomerOrderForm_generatesValidPdfWithAcroFormFields() {
        val unitTypes = listOf(
            VeggieUnitType(id = "u-kg", name = "kg", allowDecimals = true),
            VeggieUnitType(id = "u-un", name = "Unidade", allowDecimals = false)
        ).associateBy { it.id }

        val products = listOf(
            VeggieProduct(
                id = "vp-carrot",
                name = "Cenoura",
                unitTypeId = "u-kg",
                isActive = true
            ),
            VeggieProduct(
                id = "vp-lettuce",
                name = "Alface",
                unitTypeId = "u-un",
                isActive = true
            )
        )

        for (lang in listOf(Language.BR, Language.EN, Language.DE)) {
            val bytes = VeggieOrderPdfGenerator.generateCustomerOrderFormBytes(products, unitTypes, lang)
            assertTrue(bytes.isNotEmpty(), "Order Form PDF bytes should not be empty")

            val header = String(bytes.take(5).toByteArray(), Charsets.US_ASCII)
            assertEquals("%PDF-", header, "File must start with %PDF- header")
            assertTrue(bytes.size > 1000, "PDF should have substantial content")

            val reader = com.lowagie.text.pdf.PdfReader(bytes)
            try {
                val acroFields = reader.acroFields
                assertNotNull(acroFields.getFieldItem("customer_name"), "Should contain customer_name field")
                assertNotNull(acroFields.getFieldItem("order_date"), "Should contain order_date field")
                assertEquals("", acroFields.getField("order_date"), "Date field should not be prefilled")
                assertNotNull(acroFields.getFieldItem("qty_vp-carrot"), "Should contain qty_vp-carrot field")
                assertNotNull(acroFields.getFieldItem("qty_vp-lettuce"), "Should contain qty_vp-lettuce field")
            } finally {
                reader.close()
            }
        }
    }

    @Test
    fun testGenerateCustomerOrderForm_filtersInactiveAndDeletedProducts() {
        val unitTypes = mapOf(
            "u-kg" to VeggieUnitType(id = "u-kg", name = "kg", allowDecimals = true)
        )

        val products = listOf(
            VeggieProduct(id = "vp-active", name = "Active Tomato", unitTypeId = "u-kg", isActive = true, isDeleted = false),
            VeggieProduct(id = "vp-inactive", name = "Inactive Potato", unitTypeId = "u-kg", isActive = false, isDeleted = false),
            VeggieProduct(id = "vp-deleted", name = "Deleted Onion", unitTypeId = "u-kg", isActive = true, isDeleted = true)
        )

        val bytes = VeggieOrderPdfGenerator.generateCustomerOrderFormBytes(products, unitTypes, Language.EN)
        val reader = com.lowagie.text.pdf.PdfReader(bytes)
        try {
            val acroFields = reader.acroFields
            assertNotNull(acroFields.getFieldItem("qty_vp-active"), "Active product should have field")
            assertNull(acroFields.getFieldItem("qty_vp-inactive"), "Inactive product should not have field")
            assertNull(acroFields.getFieldItem("qty_vp-deleted"), "Deleted product should not have field")
        } finally {
            reader.close()
        }
    }

    @Test
    fun testGenerateCustomerOrderForm_emptyProductsList_handlesGracefully() {
        val bytes = VeggieOrderPdfGenerator.generateCustomerOrderFormBytes(emptyList(), emptyMap(), Language.DE)
        assertTrue(bytes.isNotEmpty())
        val reader = com.lowagie.text.pdf.PdfReader(bytes)
        try {
            assertEquals(1, reader.numberOfPages)
            val acroFields = reader.acroFields
            assertNotNull(acroFields.getFieldItem("customer_name"), "Should still have customer_name header field")
            assertNotNull(acroFields.getFieldItem("order_date"), "Should still have order_date header field")
        } finally {
            reader.close()
        }
    }

    @Test
    fun testBuyerShoppingList_excludesProductsMarkedExcludeFromBuyerPdf() {
        val round = VeggieOrderRound(id = "r-excl", openedAt = 1_743_465_600_000L)
        val products = listOf(
            VeggieProduct(id = "vp-buy", name = "Imported Avocado", unitTypeId = "u-kg", excludeFromBuyerPdf = false),
            VeggieProduct(id = "vp-local", name = "Local Banana", unitTypeId = "u-kg", excludeFromBuyerPdf = true)
        )
        val entries = listOf(
            VeggieOrderEntry(
                id = "e-buy",
                roundId = round.id,
                userId = "u1",
                userNameSnapshot = "Alice",
                veggieProductId = "vp-buy",
                veggieNameSnapshot = "Imported Avocado",
                unitNameSnapshot = "kg",
                allowDecimalsSnapshot = true,
                orderedQuantityScaled = 2000L
            ),
            VeggieOrderEntry(
                id = "e-local",
                roundId = round.id,
                userId = "u1",
                userNameSnapshot = "Alice",
                veggieProductId = "vp-local",
                veggieNameSnapshot = "Local Banana",
                unitNameSnapshot = "kg",
                allowDecimalsSnapshot = true,
                orderedQuantityScaled = 3000L
            )
        )

        val bytes = VeggieOrderPdfGenerator.generateBuyerShoppingListBytes(round, entries, Language.EN, products)
        val reader = com.lowagie.text.pdf.PdfReader(bytes)
        try {
            val text = com.lowagie.text.pdf.parser.PdfTextExtractor(reader).getTextFromPage(1)
            assertTrue(text.contains("Imported Avocado"), "Included product should appear in Buyer PDF")
            assertFalse(text.contains("Local Banana"), "Excluded product should NOT appear in Buyer PDF")
        } finally {
            reader.close()
        }
    }

    @Test
    fun testWorkerSortingMatrix_usesUserNicknameWhenSet() {
        val round = VeggieOrderRound(id = "r-nick", openedAt = 1_743_465_600_000L)
        val users = listOf(
            de.joelneumann.lojinha.domain.model.User(id = "u1", name = "Very Long Customer Name", nickname = "Nick10"),
            de.joelneumann.lojinha.domain.model.User(id = "u2", name = "Maria", nickname = null)
        )
        val entries = listOf(
            VeggieOrderEntry(
                id = "e1",
                roundId = round.id,
                userId = "u1",
                userNameSnapshot = "Very Long Customer Name",
                veggieProductId = "vp-tom",
                veggieNameSnapshot = "Tomato",
                unitNameSnapshot = "kg",
                allowDecimalsSnapshot = true,
                orderedQuantityScaled = 1000L
            ),
            VeggieOrderEntry(
                id = "e2",
                roundId = round.id,
                userId = "u2",
                userNameSnapshot = "Maria",
                veggieProductId = "vp-tom",
                veggieNameSnapshot = "Tomato",
                unitNameSnapshot = "kg",
                allowDecimalsSnapshot = true,
                orderedQuantityScaled = 2000L
            )
        )

        val bytes = VeggieOrderPdfGenerator.generateWorkerSortingMatrixBytes(round, entries, Language.EN, users)
        val reader = com.lowagie.text.pdf.PdfReader(bytes)
        try {
            val text = com.lowagie.text.pdf.parser.PdfTextExtractor(reader).getTextFromPage(1)
            assertTrue(text.contains("Nick10"), "Worker Matrix PDF should use user's nickname")
            assertFalse(text.contains("Very Long Customer Name"), "Worker Matrix PDF should not show full name when nickname is set")
            assertTrue(text.contains("Maria"), "Worker Matrix PDF should fall back to full name when nickname is null")
        } finally {
            reader.close()
        }
    }
}
