package de.joelneumann.lojinha.ui.utils

import de.joelneumann.lojinha.domain.model.Language

object PdfFilenames {

    fun sanitizeName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "User" }
    }

    /**
     * Returns a localized filename for the user transaction statement.
     * Examples:
     * - BR: Extrato_Maria_Silva.pdf or Extrato_Maria_Silva_20261112.pdf
     * - EN: Statement_Maria_Silva.pdf or Statement_Maria_Silva_20261112.pdf
     * - DE: Kontoauszug_Maria_Silva.pdf or Kontoauszug_Maria_Silva_20261112.pdf
     */
    fun transactionStatementFilename(
        userName: String,
        language: Language,
        dateSuffix: String? = null
    ): String {
        val cleanName = sanitizeName(userName)
        val prefix = when (language) {
            Language.BR -> "Extrato"
            Language.EN -> "Statement"
            Language.DE -> "Kontoauszug"
        }
        return if (!dateSuffix.isNullOrBlank()) {
            "${prefix}_${cleanName}_${dateSuffix}.pdf"
        } else {
            "${prefix}_${cleanName}.pdf"
        }
    }

    /**
     * Returns a localized filename for the Veggie Buyer Shopping List.
     * Examples:
     * - BR: Lista_de_Compras_Semana_45_2026.pdf
     * - EN: Buyer_Shopping_List_W45_2026.pdf
     * - DE: Einkaufsliste_KW45_2026.pdf
     */
    fun buyerShoppingListFilename(
        weekOfYear: Int,
        isoYear: Int,
        language: Language,
        timeStamp: String? = null
    ): String {
        val weekPadded = weekOfYear.toString().padStart(2, '0')
        val base = when (language) {
            Language.BR -> "Lista_de_Compras_Semana_${weekPadded}_${isoYear}"
            Language.EN -> "Buyer_Shopping_List_W${weekPadded}_${isoYear}"
            Language.DE -> "Einkaufsliste_KW${weekPadded}_${isoYear}"
        }
        return if (!timeStamp.isNullOrBlank()) {
            "${base}_${timeStamp}.pdf"
        } else {
            "${base}.pdf"
        }
    }

    /**
     * Returns a localized filename for the Veggie Worker Sorting Matrix.
     * Examples:
     * - BR: Matriz_de_Separacao_Semana_45_2026.pdf
     * - EN: Worker_Sorting_Matrix_W45_2026.pdf
     * - DE: Verteilmatrix_KW45_2026.pdf
     */
    fun workerSortingMatrixFilename(
        weekOfYear: Int,
        isoYear: Int,
        language: Language,
        timeStamp: String? = null
    ): String {
        val weekPadded = weekOfYear.toString().padStart(2, '0')
        val base = when (language) {
            Language.BR -> "Matriz_de_Separacao_Semana_${weekPadded}_${isoYear}"
            Language.EN -> "Worker_Sorting_Matrix_W${weekPadded}_${isoYear}"
            Language.DE -> "Verteilmatrix_KW${weekPadded}_${isoYear}"
        }
        return if (!timeStamp.isNullOrBlank()) {
            "${base}_${timeStamp}.pdf"
        } else {
            "${base}.pdf"
        }
    }

    /**
     * Returns a localized filename for the Veggie Customer Order Form.
     * Examples:
     * - BR: Formulario_de_Pedido_2026-11-12.pdf
     * - EN: Customer_Order_Form_2026-11-12.pdf
     * - DE: Bestellformular_2026-11-12.pdf
     */
    fun customerOrderFormFilename(
        dateStamp: String,
        language: Language,
        timeStamp: String? = null
    ): String {
        val base = when (language) {
            Language.BR -> "Formulario_de_Pedido_${dateStamp}"
            Language.EN -> "Customer_Order_Form_${dateStamp}"
            Language.DE -> "Bestellformular_${dateStamp}"
        }
        return if (!timeStamp.isNullOrBlank()) {
            "${base}_${timeStamp}.pdf"
        } else {
            "${base}.pdf"
        }
    }
}
