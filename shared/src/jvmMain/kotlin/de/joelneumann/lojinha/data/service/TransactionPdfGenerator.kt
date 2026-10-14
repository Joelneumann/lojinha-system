package de.joelneumann.lojinha.data.service

import com.lowagie.text.*
import com.lowagie.text.pdf.*
import de.joelneumann.lojinha.domain.model.Language
import de.joelneumann.lojinha.domain.model.Transaction
import de.joelneumann.lojinha.domain.model.TransactionType
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.utils.Formatting
import de.joelneumann.lojinha.ui.utils.SystemNoteHelper
import de.joelneumann.lojinha.util.AppLogger
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TransactionPdfGenerator {

    private const val TAG = "TransactionPdfGenerator"

    // Theme Colors (using java.awt.Color for OpenPDF)
    private val COLOR_PRIMARY = Color(15, 23, 42) // Slate 900
    private val COLOR_SECONDARY = Color(30, 41, 59) // Slate 800
    private val COLOR_TEXT_MUTED = Color(100, 116, 139) // Slate 500
    private val COLOR_BG_LIGHT = Color(248, 250, 252) // Slate 50
    private val COLOR_BORDER = Color(226, 232, 240) // Slate 200
    private val COLOR_SUCCESS = Color(22, 101, 52) // Emerald 800
    private val COLOR_DANGER = Color(185, 28, 28) // Crimson 700

    // Fonts
    private val FONT_TITLE = Font(Font.HELVETICA, 16f, Font.BOLD, COLOR_PRIMARY)
    private val FONT_SUBTITLE = Font(Font.HELVETICA, 10f, Font.NORMAL, COLOR_TEXT_MUTED)
    private val FONT_SECTION = Font(Font.HELVETICA, 11f, Font.BOLD, COLOR_PRIMARY)
    private val FONT_BODY = Font(Font.HELVETICA, 9f, Font.NORMAL, COLOR_PRIMARY)
    private val FONT_BODY_BOLD = Font(Font.HELVETICA, 9f, Font.BOLD, COLOR_PRIMARY)
    private val FONT_MUTED = Font(Font.HELVETICA, 8f, Font.NORMAL, COLOR_TEXT_MUTED)
    private val FONT_TH = Font(Font.HELVETICA, 8.5f, Font.BOLD, Color.WHITE)
    private val FONT_AMOUNT_POS = Font(Font.HELVETICA, 9f, Font.BOLD, COLOR_SUCCESS)
    private val FONT_AMOUNT_NEG = Font(Font.HELVETICA, 9f, Font.BOLD, COLOR_DANGER)

    fun generateStatementBytes(
        user: User,
        transactions: List<Transaction>,
        fromTimestamp: Long? = null,
        toTimestamp: Long? = null,
        language: Language = Language.BR
    ): ByteArray {
        val out = ByteArrayOutputStream()
        writePdf(out, user, transactions, fromTimestamp, toTimestamp, language)
        return out.toByteArray()
    }

    fun generateStatementPdf(
        outputFile: File,
        user: User,
        transactions: List<Transaction>,
        fromTimestamp: Long? = null,
        toTimestamp: Long? = null,
        language: Language = Language.BR
    ): File {
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { fos ->
            writePdf(fos, user, transactions, fromTimestamp, toTimestamp, language)
        }
        AppLogger.info(TAG, "Generated PDF statement at: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
        return outputFile
    }

    private fun writePdf(
        outputStream: java.io.OutputStream,
        user: User,
        allUserTransactions: List<Transaction>,
        fromTimestamp: Long?,
        toTimestamp: Long?,
        language: Language
    ) {
        val strings = I18n.get(language)

        // Filter and sort chronologically (oldest to newest)
        val filtered = allUserTransactions
            .filter { tx ->
                (fromTimestamp == null || tx.timestamp >= fromTimestamp) &&
                (toTimestamp == null || tx.timestamp <= toTimestamp)
            }
            .sortedBy { it.timestamp }

        // Financial balances calculation
        val initialBalance = if (filtered.isNotEmpty()) {
            filtered.first().userBalanceBefore ?: user.balance
        } else {
            user.balance
        }
        val finalBalance = if (filtered.isNotEmpty()) {
            filtered.last().userBalanceAfter ?: user.balance
        } else {
            user.balance
        }

        var totalDeposits = 0L
        var totalPurchases = 0L
        for (tx in filtered) {
            if (tx.totalAmount > 0) {
                totalDeposits += tx.totalAmount
            } else if (tx.totalAmount < 0) {
                totalPurchases += kotlin.math.abs(tx.totalAmount)
            }
        }
        val netChange = totalDeposits - totalPurchases

        // Document Setup: A4, 36pt margins (0.5 inch)
        val document = Document(PageSize.A4, 36f, 36f, 36f, 45f)
        val writer = PdfWriter.getInstance(document, outputStream)

        // Add Header/Footer Page Events
        writer.pageEvent = StatementPageEvent(strings, language)

        document.open()

        // 1. Header Banner
        val headerTable = PdfPTable(floatArrayOf(65f, 35f)).apply {
            widthPercentage = 100f
            setSpacingAfter(12f)
        }

        // Left Header: Brand & Title
        val leftHeaderCell = PdfPCell().apply {
            border = Rectangle.NO_BORDER
            setPaddingBottom(4f)
            addElement(Paragraph("LOJINHA", Font(Font.HELVETICA, 18f, Font.BOLD, COLOR_PRIMARY)).apply {
                spacingAfter = 2f
            })
            addElement(Paragraph(strings.statementPdfTitle, FONT_TITLE).apply {
                spacingAfter = 2f
            })

            val periodText = buildPeriodString(fromTimestamp, toTimestamp, strings, language)
            addElement(Paragraph(periodText, FONT_SUBTITLE))
        }
        headerTable.addCell(leftHeaderCell)

        // Right Header: Issue date & Account status
        val rightHeaderCell = PdfPCell().apply {
            border = Rectangle.NO_BORDER
            horizontalAlignment = Element.ALIGN_RIGHT
            setPaddingBottom(4f)
            val formattedNow = Formatting.formatTimestamp(System.currentTimeMillis(), language)
            addElement(Paragraph(formattedNow, FONT_MUTED).apply {
                alignment = Element.ALIGN_RIGHT
            })
            val statusLabel = if (user.isActive && !user.isDeleted) strings.statementPdfActive else strings.statementPdfInactive
            val statusFont = if (user.isActive && !user.isDeleted) FONT_AMOUNT_POS else FONT_AMOUNT_NEG
            addElement(Paragraph("${strings.statementPdfAccountStatus}: $statusLabel", statusFont).apply {
                alignment = Element.ALIGN_RIGHT
            })
            if (!user.userBarcode.isNullOrBlank() || !user.userBarcodeNumber.isNullOrBlank()) {
                val code = user.userBarcodeNumber ?: user.userBarcode ?: ""
                addElement(Paragraph("ID / Barcode: $code", FONT_MUTED).apply {
                    alignment = Element.ALIGN_RIGHT
                })
            }
        }
        headerTable.addCell(rightHeaderCell)
        document.add(headerTable)

        // Divider
        val dividerTable = PdfPTable(1).apply {
            widthPercentage = 100f
            setSpacingAfter(14f)
        }
        val dividerCell = PdfPCell().apply {
            border = Rectangle.BOTTOM
            borderWidthBottom = 2f
            borderColorBottom = COLOR_PRIMARY
            setPadding(0f)
            setFixedHeight(2f)
        }
        dividerTable.addCell(dividerCell)
        document.add(dividerTable)

        // 2. Summary Overview Cards
        val summaryTable = PdfPTable(floatArrayOf(25f, 25f, 25f, 25f)).apply {
            widthPercentage = 100f
            setSpacingAfter(16f)
        }

        fun createSummaryCell(title: String, mainText: String, subText: String? = null, isHighlight: Boolean = false): PdfPCell {
            return PdfPCell().apply {
                border = Rectangle.BOX
                borderColor = if (isHighlight) COLOR_PRIMARY else COLOR_BORDER
                borderWidth = 1f
                backgroundColor = if (isHighlight) COLOR_BG_LIGHT else Color.WHITE
                setPadding(8f)
                addElement(Paragraph(title.uppercase(Locale.ROOT), FONT_MUTED).apply { spacingAfter = 3f })
                addElement(Paragraph(mainText, if (isHighlight) Font(Font.HELVETICA, 12f, Font.BOLD, COLOR_PRIMARY) else FONT_SECTION))
                if (subText != null) {
                    addElement(Paragraph(subText, FONT_MUTED).apply { spacingBefore = 2f })
                }
            }
        }

        // Card 1: User Info
        summaryTable.addCell(createSummaryCell(
            title = strings.userNameLabel,
            mainText = user.name,
            subText = user.userBarcodeNumber?.let { "Code: $it" } ?: "ID: ${user.id.take(8)}"
        ))

        // Card 2: Initial Balance
        summaryTable.addCell(createSummaryCell(
            title = strings.initialBalance,
            mainText = Formatting.formatBrl(initialBalance, language),
            subText = fromTimestamp?.let { Formatting.formatTimestamp(it, language) }
        ))

        // Card 3: Net Movement
        val netPrefix = if (netChange > 0) "+" else ""
        summaryTable.addCell(createSummaryCell(
            title = strings.periodNetLabel,
            mainText = "$netPrefix${Formatting.formatBrl(netChange, language)}",
            subText = "+${Formatting.formatBrl(totalDeposits, language)} / -${Formatting.formatBrl(totalPurchases, language)}"
        ))

        // Card 4: Final Balance
        summaryTable.addCell(createSummaryCell(
            title = strings.finalBalance,
            mainText = Formatting.formatBrl(finalBalance, language),
            subText = strings.transactionsCountPreview(filtered.size),
            isHighlight = true
        ))

        document.add(summaryTable)

        // 3. Transactions Table
        val ledgerTable = PdfPTable(floatArrayOf(18f, 14f, 38f, 15f, 15f)).apply {
            widthPercentage = 100f
            headerRows = 1
            setSpacingAfter(14f)
        }

        fun addThCell(text: String, align: Int = Element.ALIGN_LEFT) {
            val cell = PdfPCell(Phrase(text, FONT_TH)).apply {
                backgroundColor = COLOR_PRIMARY
                setPaddingTop(6f)
                setPaddingBottom(6f)
                setPaddingLeft(5f)
                setPaddingRight(5f)
                horizontalAlignment = align
                border = Rectangle.NO_BORDER
            }
            ledgerTable.addCell(cell)
        }

        addThCell(strings.statementPdfColDateTime)
        addThCell(strings.statementPdfColType)
        addThCell(strings.statementPdfColDescription)
        addThCell(strings.statementPdfColAmount, Element.ALIGN_RIGHT)
        addThCell(strings.statementPdfColBalance, Element.ALIGN_RIGHT)

        if (filtered.isEmpty()) {
            val emptyCell = PdfPCell(Phrase(strings.errNoTransactionsToExport, FONT_MUTED)).apply {
                colspan = 5
                setPadding(16f)
                horizontalAlignment = Element.ALIGN_CENTER
                border = Rectangle.BOX
                borderColor = COLOR_BORDER
            }
            ledgerTable.addCell(emptyCell)
        } else {
            filtered.forEachIndexed { index, tx ->
                val isEven = index % 2 == 0
                val rowBg = if (isEven) Color.WHITE else COLOR_BG_LIGHT

                fun createCell(phrase: Phrase, align: Int = Element.ALIGN_LEFT): PdfPCell {
                    return PdfPCell(phrase).apply {
                        backgroundColor = rowBg
                        border = Rectangle.BOTTOM
                        borderColorBottom = COLOR_BORDER
                        borderWidthBottom = 0.5f
                        setPaddingTop(5f)
                        setPaddingBottom(5f)
                        setPaddingLeft(5f)
                        setPaddingRight(5f)
                        horizontalAlignment = align
                    }
                }

                // Col 1: Date & Time
                val dateStr = Formatting.formatTimestamp(tx.timestamp, language)
                ledgerTable.addCell(createCell(Phrase(dateStr, FONT_BODY)))

                // Col 2: Type
                val typeLabel = when (tx.type) {
                    TransactionType.PURCHASE -> strings.historyTypePurchase
                    TransactionType.ADMIN_DEPOSIT -> if (tx.items.isNotEmpty()) strings.historyTypeCustomIncome else strings.historyTypeDeposit
                    TransactionType.ADMIN_WITHDRAWAL -> if (tx.items.isNotEmpty()) strings.historyTypeCustomExpense else strings.historyTypeDebit
                    TransactionType.CORRECTION -> strings.historyTypeCorrection
                    TransactionType.CANCELLATION -> strings.historyTypeCancellation
                }
                val typeFont = when {
                    tx.type == TransactionType.CANCELLATION -> FONT_AMOUNT_NEG
                    tx.totalAmount > 0 -> FONT_AMOUNT_POS
                    else -> FONT_BODY_BOLD
                }
                ledgerTable.addCell(createCell(Phrase(typeLabel, typeFont)))

                // Col 3: Description / Items
                val desc = buildDescription(tx, strings, language)
                ledgerTable.addCell(createCell(Phrase(desc, FONT_BODY)))

                // Col 4: Amount
                val amountStr = when {
                    tx.totalAmount > 0 -> "+${Formatting.formatBrl(tx.totalAmount, language)}"
                    tx.totalAmount < 0 -> "-${Formatting.formatBrl(kotlin.math.abs(tx.totalAmount), language)}"
                    else -> Formatting.formatBrl(0L, language)
                }
                val amountFont = when {
                    tx.totalAmount > 0 -> FONT_AMOUNT_POS
                    tx.totalAmount < 0 -> FONT_AMOUNT_NEG
                    else -> FONT_BODY_BOLD
                }
                ledgerTable.addCell(createCell(Phrase(amountStr, amountFont), Element.ALIGN_RIGHT))

                // Col 5: Balance
                val balStr = tx.userBalanceAfter?.let { Formatting.formatBrl(it, language) } ?: "-"
                ledgerTable.addCell(createCell(Phrase(balStr, FONT_BODY_BOLD), Element.ALIGN_RIGHT))
            }
        }

        document.add(ledgerTable)

        // 4. Disclaimer Note
        val noticeTable = PdfPTable(1).apply {
            widthPercentage = 100f
            setSpacingBefore(6f)
        }
        val noticeCell = PdfPCell().apply {
            backgroundColor = COLOR_BG_LIGHT
            border = Rectangle.BOX
            borderColor = COLOR_BORDER
            borderWidth = 0.5f
            setPadding(7f)
            addElement(Paragraph(strings.statementPdfDisclaimer, FONT_MUTED))
        }
        noticeTable.addCell(noticeCell)
        document.add(noticeTable)

        document.close()
    }

    private fun buildDescription(
        tx: Transaction,
        strings: de.joelneumann.lojinha.ui.i18n.AppStrings,
        language: Language
    ): String {
        return when {
            tx.items.isNotEmpty() -> {
                val itemLines = tx.items.joinToString(", ") { item ->
                    val qtyStr = Formatting.formatQuantity(item.quantity, item.unitType, strings)
                    "$qtyStr ${item.productName} (${Formatting.formatBrl(item.totalLinePrice, language)})"
                }
                if (!tx.note.isNullOrBlank()) {
                    val decoded = SystemNoteHelper.decodeNote(tx.note, strings) ?: tx.note
                    "$itemLines - $decoded"
                } else {
                    itemLines
                }
            }
            !tx.note.isNullOrBlank() -> {
                SystemNoteHelper.decodeNote(tx.note, strings) ?: tx.note
            }
            else -> "-"
        }
    }

    private fun buildPeriodString(
        fromTimestamp: Long?,
        toTimestamp: Long?,
        strings: de.joelneumann.lojinha.ui.i18n.AppStrings,
        language: Language
    ): String {
        return when {
            fromTimestamp != null && toTimestamp != null -> {
                val fromStr = Formatting.formatTimestamp(fromTimestamp, language).substringBeforeLast(",")
                val toStr = Formatting.formatTimestamp(toTimestamp, language).substringBeforeLast(",")
                "${strings.filterPeriod}: $fromStr ${strings.dateTo.lowercase()} $toStr"
            }
            fromTimestamp != null -> {
                val fromStr = Formatting.formatTimestamp(fromTimestamp, language).substringBeforeLast(",")
                "${strings.filterPeriod}: ${strings.dateFrom} $fromStr"
            }
            toTimestamp != null -> {
                val toStr = Formatting.formatTimestamp(toTimestamp, language).substringBeforeLast(",")
                "${strings.filterPeriod}: ${strings.dateTo} $toStr"
            }
            else -> {
                "${strings.filterPeriod}: ${strings.periodAllTime}"
            }
        }
    }

    // Page Event for Footer & Page Numbers
    private class StatementPageEvent(
        private val strings: de.joelneumann.lojinha.ui.i18n.AppStrings,
        private val language: Language
    ) : PdfPageEventHelper() {
        private var totalPagesTemplate: PdfTemplate? = null
        private val baseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED)

        override fun onOpenDocument(writer: PdfWriter, document: Document) {
            totalPagesTemplate = writer.directContent.createTemplate(30f, 16f)
        }

        override fun onEndPage(writer: PdfWriter, document: Document) {
            val cb = writer.directContent
            val pageNum = writer.pageNumber
            val pagePrefix = when (language) {
                Language.DE -> "Seite $pageNum / "
                Language.EN -> "Page $pageNum / "
                Language.BR -> "Pág. $pageNum / "
            }
            val footerText = "Lojinha System  •  " + strings.statementPdfFooterGenerated(
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
            ) + "  •  $pagePrefix"

            val textWidth = baseFont.getWidthPoint(footerText, 8f)
            val y = document.bottom() - 15f
            val x = (document.right() + document.left()) / 2f - (textWidth / 2f)

            cb.beginText()
            cb.setFontAndSize(baseFont, 8f)
            cb.setColorFill(COLOR_TEXT_MUTED)
            cb.setTextMatrix(x, y)
            cb.showText(footerText)
            cb.endText()

            cb.addTemplate(totalPagesTemplate, x + textWidth, y)
        }

        override fun onCloseDocument(writer: PdfWriter, document: Document) {
            totalPagesTemplate?.let { tpl ->
                tpl.beginText()
                tpl.setFontAndSize(baseFont, 8f)
                tpl.setColorFill(COLOR_TEXT_MUTED)
                tpl.setTextMatrix(0f, 0f)
                tpl.showText((writer.pageNumber - 1).coerceAtLeast(1).toString())
                tpl.endText()
            }
        }
    }
}
