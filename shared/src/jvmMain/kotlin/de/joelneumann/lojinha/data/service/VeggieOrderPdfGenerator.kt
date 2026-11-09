package de.joelneumann.lojinha.data.service

import com.lowagie.text.*
import com.lowagie.text.pdf.*
import de.joelneumann.lojinha.domain.model.Language
import de.joelneumann.lojinha.domain.model.VeggieOrderEntry
import de.joelneumann.lojinha.domain.model.VeggieOrderRound
import de.joelneumann.lojinha.domain.model.VeggieQuantityScaling
import de.joelneumann.lojinha.ui.utils.Formatting
import de.joelneumann.lojinha.ui.utils.IsoWeekUtils
import de.joelneumann.lojinha.ui.utils.sortedByAccentInsensitive
import de.joelneumann.lojinha.util.AppLogger
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object VeggieOrderPdfGenerator {

    private const val TAG = "VeggieOrderPdfGenerator"
    private const val MAX_CUSTOMERS_PER_MATRIX_TABLE = 10

    // Theme Colors (matching TransactionPdfGenerator)
    private val COLOR_PRIMARY = Color(15, 23, 42)        // Slate 900
    private val COLOR_TEXT_MUTED = Color(100, 116, 139)  // Slate 500
    private val COLOR_BG_LIGHT = Color(241, 245, 249)    // Slate 100
    private val COLOR_BG_ZEBRA = Color(248, 250, 252)    // Slate 50
    private val COLOR_BORDER = Color(203, 213, 225)      // Slate 300
    private val COLOR_BORDER_SUBTLE = Color(226, 232, 240) // Slate 200

    // Fonts
    private val FONT_BRAND = Font(Font.HELVETICA, 16f, Font.BOLD, COLOR_PRIMARY)
    private val FONT_TITLE = Font(Font.HELVETICA, 14f, Font.BOLD, COLOR_PRIMARY)
    private val FONT_SUBTITLE = Font(Font.HELVETICA, 10f, Font.NORMAL, COLOR_TEXT_MUTED)
    private val FONT_TH = Font(Font.HELVETICA, 8.5f, Font.BOLD, Color.WHITE)
    private val FONT_BODY = Font(Font.HELVETICA, 9.5f, Font.NORMAL, COLOR_PRIMARY)
    private val FONT_BODY_BOLD = Font(Font.HELVETICA, 9.5f, Font.BOLD, COLOR_PRIMARY)
    private val FONT_MATRIX_ORDER = Font(Font.HELVETICA, 8.5f, Font.BOLD, COLOR_PRIMARY)
    private val FONT_MUTED = Font(Font.HELVETICA, 8f, Font.NORMAL, COLOR_TEXT_MUTED)

    private data class VeggiePdfLabels(
        val buyerListTitle: String,
        val workerMatrixTitle: String,
        val weekLabel: (Int, Int) -> String,
        val openedAtLabel: String,
        val customersCountLabel: (Int) -> String,
        val productsCountLabel: (Int) -> String,
        val colProductName: String,
        val colUnit: String,
        val colTotalQty: String,
        val colActualUnitPrice: String,
        val colTotalSpent: String,
        val buyerOperationCostLabel: String,
        val notesLabel: String,
        val emptyRoundMessage: String,
        val matrixLegend: String,
        val pagePrefix: String
    )

    private fun labelsFor(language: Language): VeggiePdfLabels = when (language) {
        Language.DE -> VeggiePdfLabels(
            buyerListTitle = "Gemüsebestellung — Einkaufsliste",
            workerMatrixTitle = "Gemüsebestellung — Verteilmatrix",
            weekLabel = { week, year -> "Kalenderwoche $week / $year" },
            openedAtLabel = "Eröffnet am",
            customersCountLabel = { count -> "$count Kunden" },
            productsCountLabel = { count -> "$count Produkte" },
            colProductName = "Produkt",
            colUnit = "Einheit",
            colTotalQty = "Gesamtmenge",
            colActualUnitPrice = "Stück-/kg-Preis",
            colTotalSpent = "Gesamtpreis",
            buyerOperationCostLabel = "Einkäufer-Gebühr (R$):",
            notesLabel = "Notizen:",
            emptyRoundMessage = "Keine Bestellungen in dieser Runde vorhanden.",
            matrixLegend = "Zellenaufbau: Oben = Bestellte Menge  |  Unten = Tatsächlich gelieferte Menge eintragen",
            pagePrefix = "Seite"
        )
        Language.EN -> VeggiePdfLabels(
            buyerListTitle = "Veggie Order — Buyer Shopping List",
            workerMatrixTitle = "Veggie Order — Worker Sorting Matrix",
            weekLabel = { week, year -> "Week $week / $year" },
            openedAtLabel = "Opened",
            customersCountLabel = { count -> "$count Customers" },
            productsCountLabel = { count -> "$count Products" },
            colProductName = "Product Name",
            colUnit = "Unit",
            colTotalQty = "Total Quantity",
            colActualUnitPrice = "Actual Unit Price",
            colTotalSpent = "Total Spent",
            buyerOperationCostLabel = "Buyer Operation Cost (R$):",
            notesLabel = "Notes:",
            emptyRoundMessage = "No orders placed in this round.",
            matrixLegend = "Cell layout: Top half = Ordered Quantity  |  Bottom half = Fill in actual delivered quantity",
            pagePrefix = "Page"
        )
        Language.BR -> VeggiePdfLabels(
            buyerListTitle = "Pedido de Verduras — Lista de Compras",
            workerMatrixTitle = "Pedido de Verduras — Matriz de Separação",
            weekLabel = { week, year -> "Semana $week / $year" },
            openedAtLabel = "Aberto em",
            customersCountLabel = { count -> "$count Clientes" },
            productsCountLabel = { count -> "$count Produtos" },
            colProductName = "Produto",
            colUnit = "Unidade",
            colTotalQty = "Qtd. Total",
            colActualUnitPrice = "Preço Unitário",
            colTotalSpent = "Total Gasto",
            buyerOperationCostLabel = "Taxa do Comprador (R$):",
            notesLabel = "Observações:",
            emptyRoundMessage = "Nenhum pedido registrado nesta rodada.",
            matrixLegend = "Célula: Parte superior = Qtd. Pedida  |  Parte inferior = Preencher qtd./peso real entregue",
            pagePrefix = "Pág."
        )
    }

    // -------------------------------------------------------------------------
    // PDF 1: Buyer Shopping List (A4 Portrait)
    // -------------------------------------------------------------------------
    fun generateBuyerShoppingListBytes(
        round: VeggieOrderRound,
        entries: List<VeggieOrderEntry>,
        language: Language = Language.BR
    ): ByteArray {
        val out = ByteArrayOutputStream()
        writeBuyerShoppingListPdf(out, round, entries, language)
        return out.toByteArray()
    }

    fun generateBuyerShoppingListPdf(
        outputFile: File,
        round: VeggieOrderRound,
        entries: List<VeggieOrderEntry>,
        language: Language = Language.BR
    ): File {
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { fos ->
            writeBuyerShoppingListPdf(fos, round, entries, language)
        }
        AppLogger.info(TAG, "Generated Buyer Shopping List PDF at: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
        return outputFile
    }

    private fun writeBuyerShoppingListPdf(
        outputStream: java.io.OutputStream,
        round: VeggieOrderRound,
        entries: List<VeggieOrderEntry>,
        language: Language
    ) {
        val labels = labelsFor(language)
        val weekInfo = IsoWeekUtils.fromEpochMillis(round.openedAt)
        val positiveEntries = entries.filter { it.orderedQuantityScaled > 0L }
        val groupedByProduct = positiveEntries
            .groupBy { it.veggieProductId }
            .values
            .filter { it.isNotEmpty() }
            .sortedByAccentInsensitive { it.first().veggieNameSnapshot }

        val customerCount = positiveEntries.map { it.userId }.distinct().size
        val decimalSep = if (language == Language.EN) '.' else ','

        val document = Document(PageSize.A4, 36f, 36f, 36f, 45f)
        val writer = PdfWriter.getInstance(document, outputStream)
        writer.pageEvent = VeggieFooterPageEvent(labels)
        document.open()

        addDocumentHeader(
            document = document,
            title = labels.buyerListTitle,
            weekBadge = labels.weekLabel(weekInfo.weekOfYear, weekInfo.isoYear),
            subtitle = "${labels.openedAtLabel}: ${Formatting.formatTimestamp(round.openedAt, language)}  •  " +
                "${labels.customersCountLabel(customerCount)}  •  ${labels.productsCountLabel(groupedByProduct.size)}",
            language = language
        )

        val table = PdfPTable(floatArrayOf(36f, 13f, 15f, 18f, 18f)).apply {
            widthPercentage = 100f
            headerRows = 1
            setSpacingAfter(16f)
        }

        fun addTh(text: String, align: Int = Element.ALIGN_LEFT) {
            val cell = PdfPCell(Phrase(text, FONT_TH)).apply {
                backgroundColor = COLOR_PRIMARY
                setPaddingTop(7f)
                setPaddingBottom(7f)
                setPaddingLeft(6f)
                setPaddingRight(6f)
                horizontalAlignment = align
                verticalAlignment = Element.ALIGN_MIDDLE
                border = Rectangle.BOX
                borderColor = COLOR_PRIMARY
            }
            table.addCell(cell)
        }

        // Note: No "[Write-in]" label in headers per user requirement
        addTh(labels.colProductName)
        addTh(labels.colUnit, Element.ALIGN_CENTER)
        addTh(labels.colTotalQty, Element.ALIGN_RIGHT)
        addTh(labels.colActualUnitPrice, Element.ALIGN_RIGHT)
        addTh(labels.colTotalSpent, Element.ALIGN_RIGHT)

        if (groupedByProduct.isEmpty()) {
            val emptyCell = PdfPCell(Phrase(labels.emptyRoundMessage, FONT_MUTED)).apply {
                colspan = 5
                setPadding(18f)
                horizontalAlignment = Element.ALIGN_CENTER
                border = Rectangle.BOX
                borderColor = COLOR_BORDER
            }
            table.addCell(emptyCell)
        } else {
            groupedByProduct.forEachIndexed { idx, productEntries ->
                val sample = productEntries.first()
                val totalOrderedScaled = productEntries.sumOf { it.orderedQuantityScaled }
                val formattedTotalQty = VeggieQuantityScaling.formatScaledQuantity(
                    scaled = totalOrderedScaled,
                    allowDecimals = sample.allowDecimalsSnapshot,
                    decimalSeparator = decimalSep
                )
                val rowBg = if (idx % 2 == 0) Color.WHITE else COLOR_BG_ZEBRA

                fun buildDataCell(
                    text: String,
                    font: Font = FONT_BODY,
                    align: Int = Element.ALIGN_LEFT,
                    bg: Color = rowBg
                ): PdfPCell {
                    return PdfPCell(Phrase(text, font)).apply {
                        backgroundColor = bg
                        border = Rectangle.BOX
                        borderColor = COLOR_BORDER
                        borderWidth = 0.75f
                        minimumHeight = 26f
                        setPaddingLeft(6f)
                        setPaddingRight(6f)
                        setPaddingTop(6f)
                        setPaddingBottom(6f)
                        horizontalAlignment = align
                        verticalAlignment = Element.ALIGN_MIDDLE
                    }
                }

                table.addCell(buildDataCell(sample.veggieNameSnapshot, FONT_BODY_BOLD, Element.ALIGN_LEFT))
                table.addCell(buildDataCell(sample.unitNameSnapshot, FONT_BODY, Element.ALIGN_CENTER))
                table.addCell(buildDataCell(formattedTotalQty, FONT_BODY_BOLD, Element.ALIGN_RIGHT))
                // Blank write-in cells for Actual Unit Price and Total Spent
                table.addCell(buildDataCell("", FONT_BODY, Element.ALIGN_RIGHT, Color.WHITE))
                table.addCell(buildDataCell("", FONT_BODY, Element.ALIGN_RIGHT, Color.WHITE))
            }
        }

        document.add(table)

        // Footer Box for Buyer Operation Cost & Notes
        val footerBox = PdfPTable(floatArrayOf(45f, 55f)).apply {
            widthPercentage = 100f
            setSpacingBefore(4f)
        }
        val buyerFeeHint = if (round.buyerOperationFeeCents > 0L) {
            Formatting.formatBrl(round.buyerOperationFeeCents, language)
        } else {
            "____________________"
        }
        val feeCell = PdfPCell().apply {
            border = Rectangle.BOX
            borderColor = COLOR_PRIMARY
            borderWidth = 1f
            backgroundColor = COLOR_BG_ZEBRA
            setPadding(10f)
            minimumHeight = 44f
            addElement(Paragraph(labels.buyerOperationCostLabel, FONT_BODY_BOLD).apply { spacingAfter = 6f })
            addElement(Paragraph(buyerFeeHint, FONT_BODY))
        }
        val notesCell = PdfPCell().apply {
            border = Rectangle.BOX
            borderColor = COLOR_BORDER
            borderWidth = 1f
            backgroundColor = Color.WHITE
            setPadding(10f)
            minimumHeight = 44f
            addElement(Paragraph(labels.notesLabel, FONT_MUTED))
        }
        footerBox.addCell(feeCell)
        footerBox.addCell(notesCell)
        document.add(footerBox)

        document.close()
    }

    // -------------------------------------------------------------------------
    // PDF 2: Worker Sorting Matrix (A4 Landscape, Split Top/Bottom Cells)
    // -------------------------------------------------------------------------
    fun generateWorkerSortingMatrixBytes(
        round: VeggieOrderRound,
        entries: List<VeggieOrderEntry>,
        language: Language = Language.BR
    ): ByteArray {
        val out = ByteArrayOutputStream()
        writeWorkerSortingMatrixPdf(out, round, entries, language)
        return out.toByteArray()
    }

    fun generateWorkerSortingMatrixPdf(
        outputFile: File,
        round: VeggieOrderRound,
        entries: List<VeggieOrderEntry>,
        language: Language = Language.BR
    ): File {
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { fos ->
            writeWorkerSortingMatrixPdf(fos, round, entries, language)
        }
        AppLogger.info(TAG, "Generated Worker Sorting Matrix PDF at: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
        return outputFile
    }

    private fun writeWorkerSortingMatrixPdf(
        outputStream: java.io.OutputStream,
        round: VeggieOrderRound,
        entries: List<VeggieOrderEntry>,
        language: Language
    ) {
        val labels = labelsFor(language)
        val weekInfo = IsoWeekUtils.fromEpochMillis(round.openedAt)
        val positiveEntries = entries.filter { it.orderedQuantityScaled > 0L }
        val decimalSep = if (language == Language.EN) '.' else ','

        val customers = positiveEntries
            .groupBy { it.userId }
            .map { (userId, list) -> userId to list.first().userNameSnapshot }
            .sortedByAccentInsensitive { it.second }

        val products = positiveEntries
            .groupBy { it.veggieProductId }
            .values
            .filter { it.isNotEmpty() }
            .sortedByAccentInsensitive { it.first().veggieNameSnapshot }

        val entriesByProductAndUser = positiveEntries.associateBy { it.veggieProductId to it.userId }

        // A4 Landscape
        val document = Document(PageSize.A4.rotate(), 28f, 28f, 28f, 40f)
        val writer = PdfWriter.getInstance(document, outputStream)
        writer.pageEvent = VeggieFooterPageEvent(labels)
        document.open()

        addDocumentHeader(
            document = document,
            title = labels.workerMatrixTitle,
            weekBadge = labels.weekLabel(weekInfo.weekOfYear, weekInfo.isoYear),
            subtitle = "${labels.openedAtLabel}: ${Formatting.formatTimestamp(round.openedAt, language)}  •  " +
                "${labels.customersCountLabel(customers.size)}  •  ${labels.productsCountLabel(products.size)}",
            language = language
        )

        document.add(Paragraph(labels.matrixLegend, FONT_MUTED).apply {
            spacingAfter = 8f
        })

        if (products.isEmpty() || customers.isEmpty()) {
            val emptyTable = PdfPTable(1).apply { widthPercentage = 100f }
            val emptyCell = PdfPCell(Phrase(labels.emptyRoundMessage, FONT_MUTED)).apply {
                setPadding(18f)
                horizontalAlignment = Element.ALIGN_CENTER
                border = Rectangle.BOX
                borderColor = COLOR_BORDER
            }
            emptyTable.addCell(emptyCell)
            document.add(emptyTable)
            document.close()
            return
        }

        val customerChunks = customers.chunked(MAX_CUSTOMERS_PER_MATRIX_TABLE)
        customerChunks.forEachIndexed { chunkIdx, customerSlice ->
            if (chunkIdx > 0) {
                document.newPage()
                addDocumentHeader(
                    document = document,
                    title = "${labels.workerMatrixTitle} (${chunkIdx + 1}/${customerChunks.size})",
                    weekBadge = labels.weekLabel(weekInfo.weekOfYear, weekInfo.isoYear),
                    subtitle = labels.matrixLegend,
                    language = language
                )
            }

            // Column widths: Product Name (20f), Unit (7f), Total (8f), + N customer columns
            val colWidths = FloatArray(3 + customerSlice.size).apply {
                this[0] = 20f
                this[1] = 7f
                this[2] = 8f
                val customerWidth = (65f / customerSlice.size.coerceAtLeast(1))
                for (i in customerSlice.indices) {
                    this[3 + i] = customerWidth
                }
            }

            val matrixTable = PdfPTable(colWidths).apply {
                widthPercentage = 100f
                headerRows = 1
                setSpacingAfter(12f)
            }

            fun addHeaderCell(text: String, align: Int = Element.ALIGN_CENTER) {
                val cell = PdfPCell(Phrase(text, FONT_TH)).apply {
                    backgroundColor = COLOR_PRIMARY
                    setPaddingTop(6f)
                    setPaddingBottom(6f)
                    setPaddingLeft(4f)
                    setPaddingRight(4f)
                    horizontalAlignment = align
                    verticalAlignment = Element.ALIGN_MIDDLE
                    border = Rectangle.BOX
                    borderColor = COLOR_PRIMARY
                }
                matrixTable.addCell(cell)
            }

            addHeaderCell(labels.colProductName, Element.ALIGN_LEFT)
            addHeaderCell(labels.colUnit, Element.ALIGN_CENTER)
            addHeaderCell(labels.colTotalQty, Element.ALIGN_RIGHT)
            for ((_, userName) in customerSlice) {
                addHeaderCell(userName, Element.ALIGN_CENTER)
            }

            for (productEntries in products) {
                val sample = productEntries.first()
                val totalOrderedScaled = productEntries.sumOf { it.orderedQuantityScaled }
                val totalFormatted = VeggieQuantityScaling.formatScaledQuantity(
                    scaled = totalOrderedScaled,
                    allowDecimals = sample.allowDecimalsSnapshot,
                    decimalSeparator = decimalSep
                )

                val prodCell = PdfPCell(Phrase(sample.veggieNameSnapshot, FONT_BODY_BOLD)).apply {
                    border = Rectangle.BOX
                    borderColor = COLOR_BORDER
                    setPadding(5f)
                    verticalAlignment = Element.ALIGN_MIDDLE
                }
                val unitCell = PdfPCell(Phrase(sample.unitNameSnapshot, FONT_MUTED)).apply {
                    border = Rectangle.BOX
                    borderColor = COLOR_BORDER
                    setPadding(4f)
                    horizontalAlignment = Element.ALIGN_CENTER
                    verticalAlignment = Element.ALIGN_MIDDLE
                }
                val totalCell = PdfPCell(Phrase(totalFormatted, FONT_BODY_BOLD)).apply {
                    border = Rectangle.BOX
                    borderColor = COLOR_BORDER
                    backgroundColor = COLOR_BG_ZEBRA
                    setPadding(5f)
                    horizontalAlignment = Element.ALIGN_RIGHT
                    verticalAlignment = Element.ALIGN_MIDDLE
                }

                matrixTable.addCell(prodCell)
                matrixTable.addCell(unitCell)
                matrixTable.addCell(totalCell)

                for ((userId, _) in customerSlice) {
                    val entry = entriesByProductAndUser[sample.veggieProductId to userId]
                    val orderedQtyText = if (entry != null && entry.orderedQuantityScaled > 0L) {
                        VeggieQuantityScaling.formatScaledQuantity(
                            scaled = entry.orderedQuantityScaled,
                            allowDecimals = sample.allowDecimalsSnapshot,
                            decimalSeparator = decimalSep
                        )
                    } else {
                        ""
                    }
                    matrixTable.addCell(createSplitMatrixCell(orderedQtyText))
                }
            }

            document.add(matrixTable)
        }

        document.close()
    }

    /**
     * Builds a horizontally divided cell:
     * - Top half: Printed Ordered Quantity (tinted background when non-empty)
     * - Bottom half: Blank space to write the actual weighed/delivered quantity
     */
    private fun createSplitMatrixCell(orderedQtyText: String): PdfPCell {
        val hasOrder = orderedQtyText.isNotBlank()
        val innerTable = PdfPTable(1).apply {
            widthPercentage = 100f
        }

        val topHalf = PdfPCell(Phrase(if (hasOrder) orderedQtyText else "-", if (hasOrder) FONT_MATRIX_ORDER else FONT_MUTED)).apply {
            border = Rectangle.BOTTOM
            borderColorBottom = COLOR_BORDER_SUBTLE
            borderWidthBottom = 0.5f
            backgroundColor = if (hasOrder) COLOR_BG_LIGHT else COLOR_BG_ZEBRA
            horizontalAlignment = Element.ALIGN_CENTER
            verticalAlignment = Element.ALIGN_MIDDLE
            minimumHeight = 14f
            setPaddingTop(2f)
            setPaddingBottom(2f)
        }

        val bottomHalf = PdfPCell(Phrase("", FONT_BODY)).apply {
            border = Rectangle.NO_BORDER
            backgroundColor = if (hasOrder) Color.WHITE else COLOR_BG_ZEBRA
            minimumHeight = 18f
            setPadding(2f)
        }

        innerTable.addCell(topHalf)
        innerTable.addCell(bottomHalf)

        return PdfPCell(innerTable).apply {
            border = Rectangle.BOX
            borderColor = COLOR_BORDER
            borderWidth = 0.75f
            setPadding(0f)
        }
    }

    private fun addDocumentHeader(
        document: Document,
        title: String,
        weekBadge: String,
        subtitle: String,
        language: Language
    ) {
        val headerTable = PdfPTable(floatArrayOf(68f, 32f)).apply {
            widthPercentage = 100f
            setSpacingAfter(8f)
        }

        val leftCell = PdfPCell().apply {
            border = Rectangle.NO_BORDER
            setPaddingBottom(4f)
            addElement(Paragraph("LOJINHA", FONT_BRAND).apply { spacingAfter = 2f })
            addElement(Paragraph(title, FONT_TITLE).apply { spacingAfter = 2f })
            addElement(Paragraph(subtitle, FONT_SUBTITLE))
        }
        val rightCell = PdfPCell().apply {
            border = Rectangle.NO_BORDER
            horizontalAlignment = Element.ALIGN_RIGHT
            setPaddingBottom(4f)
            addElement(Paragraph(weekBadge, FONT_TITLE).apply {
                alignment = Element.ALIGN_RIGHT
                spacingAfter = 2f
            })
            val nowFormatted = Formatting.formatTimestamp(System.currentTimeMillis(), language)
            addElement(Paragraph(nowFormatted, FONT_MUTED).apply {
                alignment = Element.ALIGN_RIGHT
            })
        }

        headerTable.addCell(leftCell)
        headerTable.addCell(rightCell)
        document.add(headerTable)

        val divider = PdfPTable(1).apply {
            widthPercentage = 100f
            setSpacingAfter(10f)
        }
        divider.addCell(PdfPCell().apply {
            border = Rectangle.BOTTOM
            borderWidthBottom = 1.5f
            borderColorBottom = COLOR_PRIMARY
            setPadding(0f)
            fixedHeight = 2f
        })
        document.add(divider)
    }

    private class VeggieFooterPageEvent(
        private val labels: VeggiePdfLabels
    ) : PdfPageEventHelper() {
        private var totalPagesTemplate: PdfTemplate? = null
        private val baseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED)

        override fun onOpenDocument(writer: PdfWriter, document: Document) {
            totalPagesTemplate = writer.directContent.createTemplate(30f, 16f)
        }

        override fun onEndPage(writer: PdfWriter, document: Document) {
            val cb = writer.directContent
            val pageNum = writer.pageNumber
            val generatedAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
            val footerText = "Lojinha Veggie Order  •  $generatedAt  •  ${labels.pagePrefix} $pageNum / "

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
