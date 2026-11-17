package de.joelneumann.lojinha.data.service

import de.joelneumann.lojinha.domain.model.Language
import de.joelneumann.lojinha.domain.model.ShoppingListItem
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShoppingListPdfGeneratorTest {

    private lateinit var tempDir: File

    @BeforeTest
    fun setup() {
        tempDir = Files.createTempDirectory("shopping_pdf_test").toFile()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testGenerateBytesForAllLanguagesAndEmptyList() {
        for (lang in Language.entries) {
            val emptyBytes = ShoppingListPdfGenerator.generateBytes(emptyList(), lang)
            assertTrue(emptyBytes.size > 500, "Empty PDF should still render header/table for $lang")
            assertEquals("%PDF-", String(emptyBytes.copyOfRange(0, 5), Charsets.US_ASCII))
        }
    }

    @Test
    fun testGenerateCompactPdfWithManyItems() {
        val items = (1..80).map { i ->
            ShoppingListItem(
                id = "item-$i",
                name = "Product Item #$i",
                quantity = "${i * 2} Stk.",
                sortOrder = i - 1
            )
        }
        val targetFile = File(tempDir, "shopping_list.pdf")
        val resultFile = ShoppingListPdfGenerator.generatePdf(targetFile, items, Language.DE)

        assertTrue(resultFile.exists())
        assertTrue(resultFile.length() > 1000)
        val bytes = resultFile.readBytes()
        assertEquals("%PDF-", String(bytes.copyOfRange(0, 5), Charsets.US_ASCII))
    }
}
