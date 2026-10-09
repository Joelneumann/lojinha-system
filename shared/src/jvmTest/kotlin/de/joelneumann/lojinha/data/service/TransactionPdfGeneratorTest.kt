package de.joelneumann.lojinha.data.service

import de.joelneumann.lojinha.domain.model.Language
import de.joelneumann.lojinha.domain.model.Transaction
import de.joelneumann.lojinha.domain.model.TransactionItem
import de.joelneumann.lojinha.domain.model.TransactionType
import de.joelneumann.lojinha.domain.model.UnitType
import de.joelneumann.lojinha.domain.model.User
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransactionPdfGeneratorTest {

    private lateinit var tempDir: File

    @BeforeTest
    fun setup() {
        tempDir = Files.createTempDirectory("pdf_test").toFile()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testGenerateStatementPdfSuccess() {
        val user = User(
            id = "user-123",
            name = "Maria Souza",
            balance = 4500L,
            userBarcode = "1042",
            userBarcodeNumber = "1042",
            language = Language.BR
        )

        val tx1 = Transaction(
            id = "tx-1",
            userId = user.id,
            userNameSnapshot = user.name,
            timestamp = 1700000000000L,
            type = TransactionType.ADMIN_DEPOSIT,
            note = "SYSNOTE|ADMIN_DEPOSIT",
            totalAmount = 5000L,
            userBalanceBefore = 0L,
            userBalanceAfter = 5000L
        )

        val tx2 = Transaction(
            id = "tx-2",
            userId = user.id,
            userNameSnapshot = user.name,
            timestamp = 1700050000000L,
            type = TransactionType.PURCHASE,
            totalAmount = -500L,
            items = listOf(
                TransactionItem(
                    productId = "prod-1",
                    productName = "Coca-Cola",
                    quantity = 1,
                    unitPriceAtPurchase = 500L,
                    unitType = UnitType.PIECE
                )
            ),
            userBalanceBefore = 5000L,
            userBalanceAfter = 4500L
        )

        val targetFile = File(tempDir, "statement.pdf")
        val resultFile = TransactionPdfGenerator.generateStatementPdf(
            outputFile = targetFile,
            user = user,
            transactions = listOf(tx1, tx2),
            language = Language.BR
        )

        assertTrue(resultFile.exists(), "PDF file must exist")
        assertTrue(resultFile.length() > 500, "PDF file must be non-empty")

        // Verify PDF Magic Bytes (%PDF-)
        val bytes = resultFile.readBytes()
        val magic = String(bytes.copyOfRange(0, 5), Charsets.US_ASCII)
        assertEquals("%PDF-", magic, "File must start with %PDF- magic bytes")
    }

    @Test
    fun testDateFilteringInPdf() {
        val user = User(
            id = "user-456",
            name = "Hans Müller",
            balance = 2000L,
            language = Language.DE
        )

        val txOld = Transaction(
            id = "tx-old",
            userId = user.id,
            userNameSnapshot = user.name,
            timestamp = 1000L,
            type = TransactionType.ADMIN_DEPOSIT,
            totalAmount = 1000L,
            userBalanceBefore = 0L,
            userBalanceAfter = 1000L
        )

        val txInside = Transaction(
            id = "tx-in",
            userId = user.id,
            userNameSnapshot = user.name,
            timestamp = 2000L,
            type = TransactionType.ADMIN_DEPOSIT,
            totalAmount = 1000L,
            userBalanceBefore = 1000L,
            userBalanceAfter = 2000L
        )

        val txFuture = Transaction(
            id = "tx-future",
            userId = user.id,
            userNameSnapshot = user.name,
            timestamp = 3000L,
            type = TransactionType.PURCHASE,
            totalAmount = -500L,
            userBalanceBefore = 2000L,
            userBalanceAfter = 1500L
        )

        val bytes = TransactionPdfGenerator.generateStatementBytes(
            user = user,
            transactions = listOf(txOld, txInside, txFuture),
            fromTimestamp = 1500L,
            toTimestamp = 2500L,
            language = Language.DE
        )

        assertTrue(bytes.isNotEmpty())
        val magic = String(bytes.copyOfRange(0, 5), Charsets.US_ASCII)
        assertEquals("%PDF-", magic)
    }

    @Test
    fun testEmptyTransactionsStatement() {
        val user = User(
            id = "user-empty",
            name = "John Doe",
            balance = 0L,
            language = Language.EN
        )

        val bytes = TransactionPdfGenerator.generateStatementBytes(
            user = user,
            transactions = emptyList(),
            language = Language.EN
        )

        assertTrue(bytes.isNotEmpty())
        val magic = String(bytes.copyOfRange(0, 5), Charsets.US_ASCII)
        assertEquals("%PDF-", magic)
    }

    @Test
    fun testGenerateStatementPdfRelativePathNullParent() {
        val user = User(
            id = "user-rel",
            name = "Test User",
            balance = 100L,
            language = Language.BR
        )
        // File with no parent directory specified
        val relFile = File("test_rel_statement_${System.currentTimeMillis()}.pdf")
        try {
            val result = TransactionPdfGenerator.generateStatementPdf(
                outputFile = relFile,
                user = user,
                transactions = emptyList(),
                language = Language.BR
            )
            assertTrue(result.exists())
            assertTrue(result.length() > 0)
        } finally {
            relFile.delete()
        }
    }

    @Test
    fun testGenerateStatementWithEnglishAndGermanLocale() {
        val user = User(
            id = "user-multi",
            name = "Johnathan Doe",
            balance = 5000L,
            language = Language.EN
        )
        val tx = Transaction(
            id = "tx-1",
            userId = user.id,
            userNameSnapshot = user.name,
            timestamp = 1700000000000L,
            type = TransactionType.PURCHASE,
            totalAmount = -1500L,
            items = listOf(
                TransactionItem(
                    productId = "p-1",
                    productName = "Coffee",
                    quantity = 2,
                    unitPriceAtPurchase = 750L,
                    unitType = UnitType.PIECE
                )
            ),
            userBalanceBefore = 6500L,
            userBalanceAfter = 5000L
        )

        // Generate in English
        val enBytes = TransactionPdfGenerator.generateStatementBytes(
            user = user,
            transactions = listOf(tx),
            fromTimestamp = 1699900000000L,
            toTimestamp = 1700100000000L,
            language = Language.EN
        )
        assertTrue(enBytes.isNotEmpty())
        assertEquals("%PDF-", String(enBytes.copyOfRange(0, 5), Charsets.US_ASCII))

        // Generate in German
        val deBytes = TransactionPdfGenerator.generateStatementBytes(
            user = user,
            transactions = listOf(tx),
            fromTimestamp = 1699900000000L,
            toTimestamp = 1700100000000L,
            language = Language.DE
        )
        assertTrue(deBytes.isNotEmpty())
        assertEquals("%PDF-", String(deBytes.copyOfRange(0, 5), Charsets.US_ASCII))
    }
}
