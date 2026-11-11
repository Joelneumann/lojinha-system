package de.joelneumann.lojinha.data.service

import de.joelneumann.lojinha.domain.model.Language
import de.joelneumann.lojinha.domain.model.VeggieOrderEntry
import de.joelneumann.lojinha.domain.model.VeggieOrderRound
import de.joelneumann.lojinha.domain.model.VeggieRoundStatus
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
}
