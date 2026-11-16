package de.joelneumann.lojinha.ui.utils

import de.joelneumann.lojinha.domain.model.Language
import kotlin.test.Test
import kotlin.test.assertEquals

class PdfFilenamesTest {

    @Test
    fun testSanitizeName() {
        assertEquals("User", PdfFilenames.sanitizeName(""))
        assertEquals("User", PdfFilenames.sanitizeName("   "))
        assertEquals("Maria_Silva", PdfFilenames.sanitizeName("Maria/Silva"))
        assertEquals("John_Doe", PdfFilenames.sanitizeName("John:Doe"))
        assertEquals("A_B_C", PdfFilenames.sanitizeName("A?B*C"))
        assertEquals("Line1_Line2", PdfFilenames.sanitizeName("Line1\r\nLine2".replace("\r\n", "\n")))
        assertEquals("A_B", PdfFilenames.sanitizeName("A\rB"))
        assertEquals("Standard Name", PdfFilenames.sanitizeName("Standard Name"))
    }

    @Test
    fun testTransactionStatementFilename() {
        // Without dateSuffix
        assertEquals("Extrato_Maria_Silva.pdf", PdfFilenames.transactionStatementFilename("Maria_Silva", Language.BR))
        assertEquals("Statement_Maria_Silva.pdf", PdfFilenames.transactionStatementFilename("Maria_Silva", Language.EN))
        assertEquals("Kontoauszug_Maria_Silva.pdf", PdfFilenames.transactionStatementFilename("Maria_Silva", Language.DE))

        // With dateSuffix
        assertEquals("Extrato_Maria_Silva_20261112.pdf", PdfFilenames.transactionStatementFilename("Maria_Silva", Language.BR, "20261112"))
        assertEquals("Statement_Maria_Silva_20261112.pdf", PdfFilenames.transactionStatementFilename("Maria_Silva", Language.EN, "20261112"))
        assertEquals("Kontoauszug_Maria_Silva_20261112.pdf", PdfFilenames.transactionStatementFilename("Maria_Silva", Language.DE, "20261112"))
    }

    @Test
    fun testBuyerShoppingListFilename() {
        // Without timestamp
        assertEquals("Lista_de_Compras_Semana_45_2026.pdf", PdfFilenames.buyerShoppingListFilename(45, 2026, Language.BR))
        assertEquals("Buyer_Shopping_List_W45_2026.pdf", PdfFilenames.buyerShoppingListFilename(45, 2026, Language.EN))
        assertEquals("Einkaufsliste_KW45_2026.pdf", PdfFilenames.buyerShoppingListFilename(45, 2026, Language.DE))

        // Single digit week padding & with timestamp
        assertEquals("Lista_de_Compras_Semana_05_2026_20261112_100000.pdf", PdfFilenames.buyerShoppingListFilename(5, 2026, Language.BR, "20261112_100000"))
        assertEquals("Buyer_Shopping_List_W05_2026_20261112_100000.pdf", PdfFilenames.buyerShoppingListFilename(5, 2026, Language.EN, "20261112_100000"))
        assertEquals("Einkaufsliste_KW05_2026_20261112_100000.pdf", PdfFilenames.buyerShoppingListFilename(5, 2026, Language.DE, "20261112_100000"))
    }

    @Test
    fun testWorkerSortingMatrixFilename() {
        // Without timestamp
        assertEquals("Matriz_de_Separacao_Semana_45_2026.pdf", PdfFilenames.workerSortingMatrixFilename(45, 2026, Language.BR))
        assertEquals("Worker_Sorting_Matrix_W45_2026.pdf", PdfFilenames.workerSortingMatrixFilename(45, 2026, Language.EN))
        assertEquals("Verteilmatrix_KW45_2026.pdf", PdfFilenames.workerSortingMatrixFilename(45, 2026, Language.DE))

        // Single digit week padding & with timestamp
        assertEquals("Matriz_de_Separacao_Semana_08_2026_20261112_120000.pdf", PdfFilenames.workerSortingMatrixFilename(8, 2026, Language.BR, "20261112_120000"))
        assertEquals("Worker_Sorting_Matrix_W08_2026_20261112_120000.pdf", PdfFilenames.workerSortingMatrixFilename(8, 2026, Language.EN, "20261112_120000"))
        assertEquals("Verteilmatrix_KW08_2026_20261112_120000.pdf", PdfFilenames.workerSortingMatrixFilename(8, 2026, Language.DE, "20261112_120000"))
    }

    @Test
    fun testCustomerOrderFormFilename() {
        // Without timestamp
        assertEquals("Formulario_de_Pedido_2026-11-12.pdf", PdfFilenames.customerOrderFormFilename("2026-11-12", Language.BR))
        assertEquals("Customer_Order_Form_2026-11-12.pdf", PdfFilenames.customerOrderFormFilename("2026-11-12", Language.EN))
        assertEquals("Bestellformular_2026-11-12.pdf", PdfFilenames.customerOrderFormFilename("2026-11-12", Language.DE))

        // With timestamp
        assertEquals("Formulario_de_Pedido_2026-11-12_20261112_143000.pdf", PdfFilenames.customerOrderFormFilename("2026-11-12", Language.BR, "20261112_143000"))
        assertEquals("Customer_Order_Form_2026-11-12_20261112_143000.pdf", PdfFilenames.customerOrderFormFilename("2026-11-12", Language.EN, "20261112_143000"))
        assertEquals("Bestellformular_2026-11-12_20261112_143000.pdf", PdfFilenames.customerOrderFormFilename("2026-11-12", Language.DE, "20261112_143000"))
    }
}
