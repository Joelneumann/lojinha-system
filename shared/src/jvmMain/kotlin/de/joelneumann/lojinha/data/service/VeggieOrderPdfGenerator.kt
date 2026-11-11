package de.joelneumann.lojinha.data.service

import com.lowagie.text.*
import com.lowagie.text.pdf.*
import de.joelneumann.lojinha.domain.model.Language
import de.joelneumann.lojinha.domain.model.VeggieOrderEntry
import de.joelneumann.lojinha.domain.model.VeggieOrderRound
import de.joelneumann.lojinha.domain.model.VeggieProduct
import de.joelneumann.lojinha.domain.model.VeggieQuantityScaling
import de.joelneumann.lojinha.domain.model.VeggieUnitType
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
    private const val MAX_CUSTOMERS_PER_MATRIX_TABLE = 25

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
    private val FONT_TH_BUYER = Font(Font.HELVETICA, 9f, Font.BOLD, COLOR_PRIMARY)
    private val FONT_TH_LIGHT = Font(Font.HELVETICA, 7.5f, Font.BOLD, COLOR_PRIMARY)
    private val FONT_BODY = Font(Font.HELVETICA, 9.5f, Font.NORMAL, COLOR_PRIMARY)
    private val FONT_BODY_BOLD = Font(Font.HELVETICA, 9.5f, Font.BOLD, COLOR_PRIMARY)
    private val FONT_MATRIX_PROD = Font(Font.HELVETICA, 8f, Font.BOLD, COLOR_PRIMARY)
    private val FONT_MATRIX_TOTAL = Font(Font.HELVETICA, 8f, Font.BOLD, COLOR_PRIMARY)
    private val FONT_MATRIX_ORDER = Font(Font.HELVETICA, 7.5f, Font.BOLD, COLOR_PRIMARY)
    private val FONT_MUTED = Font(Font.HELVETICA, 8f, Font.NORMAL, COLOR_TEXT_MUTED)
    private val FONT_MUTED_SMALL = Font(Font.HELVETICA, 7.5f, Font.NORMAL, COLOR_TEXT_MUTED)

    private data class VeggiePdfLabels(
        val buyerListTitle: String,
        val workerMatrixTitle: String,
        val orderFormTitle: String,
        val customerNameLabel: String,
        val dateLabel: String,
        val colOrderQty: String,
        val emptyCatalogMessage: String,
        val weekLabel: (Int, Int) -> String,
        val openedAtLabel: String,
        val customersCountLabel: (Int) -> String,
        val productsCountLabel: (Int) -> String,
        val colProductName: String,
        val colTotalQty: String,
        val colUnit: String,
        val colActualUnitPrice: String,
        val colTotalSpent: String,
        val subtotalLabel: String,
        val freightLabel: String,
        val grandTotalLabel: String,
        val emptyRoundMessage: String,
        val matrixLegend: String,
        val pagePrefix: String
    )

    private fun labelsFor(language: Language): VeggiePdfLabels = when (language) {
        Language.DE -> VeggiePdfLabels(
            buyerListTitle = "Gemüsebestellung — Einkaufsliste",
            workerMatrixTitle = "Gemüsebestellung — Verteilmatrix",
            orderFormTitle = "Gemüsebestellung — Bestellformular",
            customerNameLabel = "Kunde / Name:",
            dateLabel = "Datum:",
            colOrderQty = "Bestellmenge",
            emptyCatalogMessage = "Keine aktiven Produkte im Katalog vorhanden.",
            weekLabel = { week, year -> "Kalenderwoche $week / $year" },
            openedAtLabel = "Eröffnet am",
            customersCountLabel = { count -> "$count Kunden" },
            productsCountLabel = { count -> "$count Produkte" },
            colProductName = "Produkt",
            colTotalQty = "Gesamtmenge",
            colUnit = "Einheit",
            colActualUnitPrice = "Stück-/kg-Preis",
            colTotalSpent = "Gesamtpreis",
            subtotalLabel = "Zwischensumme:",
            freightLabel = "Fracht / Lieferung:",
            grandTotalLabel = "Gesamtsumme:",
            emptyRoundMessage = "Keine Bestellungen in dieser Runde vorhanden.",
            matrixLegend = "Zellenaufbau: Oben = Bestellte Menge  |  Unten = Tatsächlich gelieferte Menge eintragen",
            pagePrefix = "Seite"
        )
        Language.EN -> VeggiePdfLabels(
            buyerListTitle = "Veggie Order — Buyer Shopping List",
            workerMatrixTitle = "Veggie Order — Worker Sorting Matrix",
            orderFormTitle = "Veggie Order — Customer Order Form",
            customerNameLabel = "Customer Name:",
            dateLabel = "Date:",
            colOrderQty = "Order Quantity",
            emptyCatalogMessage = "No active products available in catalog.",
            weekLabel = { week, year -> "Week $week / $year" },
            openedAtLabel = "Opened",
            customersCountLabel = { count -> "$count Customers" },
            productsCountLabel = { count -> "$count Products" },
            colProductName = "Product Name",
            colTotalQty = "Total Quantity",
            colUnit = "Unit",
            colActualUnitPrice = "Actual Unit Price",
            colTotalSpent = "Total Spent",
            subtotalLabel = "Subtotal:",
            freightLabel = "Freight:",
            grandTotalLabel = "Grand Total:",
            emptyRoundMessage = "No orders placed in this round.",
            matrixLegend = "Cell layout: Top half = Ordered Quantity  |  Bottom half = Fill in actual delivered quantity",
            pagePrefix = "Page"
        )
        Language.BR -> VeggiePdfLabels(
            buyerListTitle = "Pedido de Verduras — Lista de Compras",
            workerMatrixTitle = "Pedido de Verduras — Matriz de Separação",
            orderFormTitle = "Pedido de Verduras — Formulário de Pedido",
            customerNameLabel = "Nome do Cliente:",
            dateLabel = "Data:",
            colOrderQty = "Quantidade",
            emptyCatalogMessage = "Nenhum produto ativo disponível no catálogo.",
            weekLabel = { week, year -> "Semana $week / $year" },
            openedAtLabel = "Aberto em",
            customersCountLabel = { count -> "$count Clientes" },
            productsCountLabel = { count -> "$count Produtos" },
            colProductName = "Produto",
            colTotalQty = "Qtd. Total",
            colUnit = "Unidade",
            colActualUnitPrice = "Preço Unitário",
            colTotalSpent = "Total Gasto",
            subtotalLabel = "Sub-Total:",
            freightLabel = "Frete:",
            grandTotalLabel = "Total Geral:",
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

        val decimalSep = if (language == Language.EN) '.' else ','

        val document = Document(PageSize.A4, 36f, 36f, 36f, 45f)
        val writer = PdfWriter.getInstance(document, outputStream)
        writer.pageEvent = VeggieFooterPageEvent(labels)
        document.open()

        addDocumentHeader(
            document = document,
            title = labels.buyerListTitle,
            weekBadge = labels.weekLabel(weekInfo.weekOfYear, weekInfo.isoYear),
            subtitle = labels.productsCountLabel(groupedByProduct.size),
            language = language
        )

        val table = PdfPTable(floatArrayOf(36f, 15f, 13f, 18f, 18f)).apply {
            widthPercentage = 100f
            headerRows = 1
            setSpacingAfter(16f)
        }

        // Light, printer-friendly header styling (avoids dark toner-heavy bars)
        fun addTh(text: String, align: Int = Element.ALIGN_LEFT) {
            val cell = PdfPCell(Phrase(text, FONT_TH_BUYER)).apply {
                backgroundColor = COLOR_BG_LIGHT
                setPaddingTop(7f)
                setPaddingBottom(7f)
                setPaddingLeft(6f)
                setPaddingRight(6f)
                horizontalAlignment = align
                verticalAlignment = Element.ALIGN_MIDDLE
                border = Rectangle.BOX
                borderColor = COLOR_BORDER
                borderWidth = 0.75f
            }
            table.addCell(cell)
        }

        // Note: No "[Write-in]" label in headers per user requirement
        addTh(labels.colProductName, Element.ALIGN_LEFT)
        addTh(labels.colTotalQty, Element.ALIGN_CENTER)
        addTh(labels.colUnit, Element.ALIGN_CENTER)
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
                table.addCell(buildDataCell(formattedTotalQty, FONT_BODY_BOLD, Element.ALIGN_CENTER))
                table.addCell(buildDataCell(sample.unitNameSnapshot, FONT_BODY, Element.ALIGN_CENTER))
                // Blank write-in cells for Actual Unit Price and Total Spent
                table.addCell(buildDataCell("", FONT_BODY, Element.ALIGN_RIGHT, Color.WHITE))
                table.addCell(buildDataCell("", FONT_BODY, Element.ALIGN_RIGHT, Color.WHITE))
            }

            // Summary rows under the list: Sub-Total, Frete, Total Geral aligning under Total Spent column
            fun addSummaryRow(labelText: String, isGrandTotal: Boolean = false, topPadding: Float = 6f) {
                val labelCell = PdfPCell(Phrase(labelText, if (isGrandTotal) FONT_BODY_BOLD else FONT_BODY)).apply {
                    colspan = 4
                    horizontalAlignment = Element.ALIGN_RIGHT
                    verticalAlignment = Element.ALIGN_MIDDLE
                    setPaddingRight(8f)
                    setPaddingTop(topPadding)
                    setPaddingBottom(5f)
                    border = Rectangle.NO_BORDER
                }
                val inputCell = PdfPCell(Phrase("", FONT_BODY)).apply {
                    colspan = 1
                    backgroundColor = Color.WHITE
                    border = Rectangle.BOX
                    borderColor = if (isGrandTotal) COLOR_PRIMARY else COLOR_BORDER
                    borderWidth = if (isGrandTotal) 1.25f else 0.75f
                    minimumHeight = 26f
                    setPadding(4f)
                }
                table.addCell(labelCell)
                table.addCell(inputCell)
            }

            addSummaryRow(labels.subtotalLabel, isGrandTotal = false, topPadding = 10f)
            addSummaryRow(labels.freightLabel, isGrandTotal = false, topPadding = 5f)
            addSummaryRow(labels.grandTotalLabel, isGrandTotal = true, topPadding = 5f)
        }

        document.add(table)
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

        // A4 Landscape - Compact side margins (18f) to maximize available table width for up to 25 persons
        val document = Document(PageSize.A4.rotate(), 18f, 18f, 20f, 35f)
        val writer = PdfWriter.getInstance(document, outputStream)
        writer.pageEvent = VeggieFooterPageEvent(labels)
        document.open()

        addDocumentHeader(
            document = document,
            title = labels.workerMatrixTitle,
            weekBadge = labels.weekLabel(weekInfo.weekOfYear, weekInfo.isoYear),
            subtitle = "${labels.customersCountLabel(customers.size)}  •  ${labels.productsCountLabel(products.size)}",
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

            // Adaptive column widths: compact fixed columns (24-32%) leaving up to 76% for up to 25 customers
            val (prodWidth, qtyWidth, unitWidth) = when {
                customerSlice.size >= 18 -> Triple(14f, 5.5f, 4.5f) // 24% fixed, 76% for customers
                customerSlice.size >= 10 -> Triple(16f, 6.5f, 5.5f) // 28% fixed, 72% for customers
                else -> Triple(18f, 7.5f, 6.5f)                    // 32% fixed, 68% for customers
            }
            val remainingWidth = 100f - prodWidth - qtyWidth - unitWidth
            val customerWidth = remainingWidth / customerSlice.size.coerceAtLeast(1)

            val colWidths = FloatArray(3 + customerSlice.size).apply {
                this[0] = prodWidth
                this[1] = qtyWidth
                this[2] = unitWidth
                for (i in customerSlice.indices) {
                    this[3 + i] = customerWidth
                }
            }

            val matrixTable = PdfPTable(colWidths).apply {
                widthPercentage = 100f
                headerRows = 1
                setSpacingAfter(12f)
            }

            val isManyCustomers = customerSlice.size > 5
            // Light, printer-friendly header styling (avoids dark toner-heavy bars)
            fun addHeaderCell(text: String, align: Int = Element.ALIGN_CENTER) {
                val cell = PdfPCell(Phrase(text, FONT_TH_LIGHT)).apply {
                    backgroundColor = COLOR_BG_LIGHT
                    setPaddingTop(5f)
                    setPaddingBottom(5f)
                    setPaddingLeft(2f)
                    setPaddingRight(2f)
                    horizontalAlignment = align
                    verticalAlignment = Element.ALIGN_MIDDLE
                    border = Rectangle.BOX
                    borderColor = COLOR_BORDER
                    borderWidth = 0.75f
                }
                matrixTable.addCell(cell)
            }

            addHeaderCell(labels.colProductName, Element.ALIGN_LEFT)
            addHeaderCell(labels.colTotalQty, Element.ALIGN_CENTER)
            addHeaderCell(labels.colUnit, Element.ALIGN_CENTER)
            for ((_, userName) in customerSlice) {
                val displayName = if (isManyCustomers) formatShortName(userName, 14) else userName
                val cell = PdfPCell(Phrase(displayName, FONT_TH_LIGHT)).apply {
                    backgroundColor = COLOR_BG_LIGHT
                    setPadding(2f)
                    horizontalAlignment = Element.ALIGN_CENTER
                    verticalAlignment = Element.ALIGN_MIDDLE
                    border = Rectangle.BOX
                    borderColor = COLOR_BORDER
                    borderWidth = 0.75f
                    if (isManyCustomers) {
                        rotation = 90
                        minimumHeight = 75f
                    }
                }
                matrixTable.addCell(cell)
            }

            for (productEntries in products) {
                val sample = productEntries.first()
                val totalOrderedScaled = productEntries.sumOf { it.orderedQuantityScaled }
                val totalFormatted = VeggieQuantityScaling.formatScaledQuantity(
                    scaled = totalOrderedScaled,
                    allowDecimals = sample.allowDecimalsSnapshot,
                    decimalSeparator = decimalSep
                )

                // Product Name column: compact padding with auto text-wrapping enabled
                val prodCell = PdfPCell(Phrase(sample.veggieNameSnapshot, FONT_MATRIX_PROD)).apply {
                    border = Rectangle.BOX
                    borderColor = COLOR_BORDER
                    borderWidth = 0.75f
                    setPaddingLeft(3f)
                    setPaddingRight(3f)
                    setPaddingTop(3f)
                    setPaddingBottom(3f)
                    isNoWrap = false
                    verticalAlignment = Element.ALIGN_MIDDLE
                }
                val totalCell = PdfPCell(Phrase(totalFormatted, FONT_MATRIX_TOTAL)).apply {
                    border = Rectangle.BOX
                    borderColor = COLOR_BORDER
                    borderWidth = 0.75f
                    backgroundColor = COLOR_BG_ZEBRA
                    setPadding(2f)
                    horizontalAlignment = Element.ALIGN_CENTER
                    verticalAlignment = Element.ALIGN_MIDDLE
                }
                val unitCell = PdfPCell(Phrase(sample.unitNameSnapshot, FONT_MUTED_SMALL)).apply {
                    border = Rectangle.BOX
                    borderColor = COLOR_BORDER
                    borderWidth = 0.75f
                    setPadding(2f)
                    horizontalAlignment = Element.ALIGN_CENTER
                    verticalAlignment = Element.ALIGN_MIDDLE
                }

                matrixTable.addCell(prodCell)
                matrixTable.addCell(totalCell)
                matrixTable.addCell(unitCell)

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
     * Shortens long customer names for narrow matrix columns (e.g. "Maria Aparecida Silva" -> "Maria S.").
     */
    fun formatShortName(fullName: String, maxChars: Int = 14): String =
        Formatting.formatShortName(fullName, maxChars)

    /**
     * Builds a horizontally divided cell:
     * - Top half: Printed Ordered Quantity (tinted background when non-empty, or "-" when empty)
     * - Bottom half: Blank space to write actual quantity (or "-" when empty)
     */
    private fun createSplitMatrixCell(orderedQtyText: String): PdfPCell {
        val hasOrder = orderedQtyText.isNotBlank()
        val innerTable = PdfPTable(1).apply {
            widthPercentage = 100f
        }

        val topHalf = PdfPCell(Phrase(if (hasOrder) orderedQtyText else "-", if (hasOrder) FONT_MATRIX_ORDER else FONT_MUTED_SMALL)).apply {
            border = Rectangle.BOTTOM
            borderColorBottom = COLOR_BORDER_SUBTLE
            borderWidthBottom = 0.5f
            backgroundColor = if (hasOrder) COLOR_BG_LIGHT else COLOR_BG_ZEBRA
            horizontalAlignment = Element.ALIGN_CENTER
            verticalAlignment = Element.ALIGN_MIDDLE
            minimumHeight = 12f
            setPaddingLeft(1f)
            setPaddingRight(1f)
            setPaddingTop(1f)
            setPaddingBottom(1f)
        }

        val bottomHalf = PdfPCell(Phrase(if (hasOrder) "" else "-", if (hasOrder) FONT_BODY else FONT_MUTED_SMALL)).apply {
            border = Rectangle.NO_BORDER
            backgroundColor = if (hasOrder) Color.WHITE else COLOR_BG_ZEBRA
            horizontalAlignment = Element.ALIGN_CENTER
            verticalAlignment = Element.ALIGN_MIDDLE
            minimumHeight = 16f
            setPaddingLeft(1f)
            setPaddingRight(1f)
            setPaddingTop(1f)
            setPaddingBottom(1f)
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

    // -------------------------------------------------------------------------
    // PDF 3: Customer Order Form (A4 Portrait, Interactive AcroForm Fields)
    // -------------------------------------------------------------------------
    fun generateCustomerOrderFormBytes(
        products: List<VeggieProduct>,
        unitMap: Map<String, VeggieUnitType>,
        language: Language = Language.BR
    ): ByteArray {
        val out = ByteArrayOutputStream()
        writeCustomerOrderFormPdf(out, products, unitMap, language)
        return out.toByteArray()
    }

    fun generateCustomerOrderFormPdf(
        outputFile: File,
        products: List<VeggieProduct>,
        unitMap: Map<String, VeggieUnitType>,
        language: Language = Language.BR
    ): File {
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { fos ->
            writeCustomerOrderFormPdf(fos, products, unitMap, language)
        }
        AppLogger.info(TAG, "Generated Customer Order Form PDF at: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
        return outputFile
    }

    private fun writeCustomerOrderFormPdf(
        outputStream: java.io.OutputStream,
        products: List<VeggieProduct>,
        unitMap: Map<String, VeggieUnitType>,
        language: Language
    ) {
        val labels = labelsFor(language)
        val activeProducts = products
            .filter { it.isActive && !it.isDeleted }
            .sortedByAccentInsensitive { it.name }

        val document = Document(PageSize.A4, 36f, 36f, 36f, 45f)
        val writer = PdfWriter.getInstance(document, outputStream)
        writer.pageEvent = VeggieFooterPageEvent(labels)
        document.open()

        // 1. Header
        val headerTable = PdfPTable(1).apply {
            widthPercentage = 100f
            setSpacingAfter(4f)
        }
        val titleCell = PdfPCell().apply {
            border = Rectangle.NO_BORDER
            setPaddingBottom(3f)
            addElement(Paragraph(labels.orderFormTitle, FONT_BRAND).apply { spacingAfter = 2f })
            addElement(Paragraph(labels.productsCountLabel(activeProducts.size), FONT_SUBTITLE))
        }
        headerTable.addCell(titleCell)
        document.add(headerTable)

        // Divider
        val divider = PdfPTable(1).apply {
            widthPercentage = 100f
            setSpacingAfter(12f)
        }
        divider.addCell(PdfPCell().apply {
            border = Rectangle.BOTTOM
            borderWidthBottom = 1.5f
            borderColorBottom = COLOR_PRIMARY
            setPadding(0f)
            fixedHeight = 2f
        })
        document.add(divider)

        // 2. Customer Name & Date interactive input boxes
        val infoTable = PdfPTable(floatArrayOf(68f, 32f)).apply {
            widthPercentage = 100f
            setSpacingAfter(14f)
        }

        val nameCell = PdfPCell().apply {
            border = Rectangle.NO_BORDER
            setPaddingRight(10f)
            setPaddingBottom(4f)
            addElement(Paragraph(labels.customerNameLabel, FONT_BODY_BOLD).apply { spacingAfter = 4f })
        }
        val nameInputTable = PdfPTable(1).apply { widthPercentage = 100f }
        val nameInputCell = PdfPCell().apply {
            border = Rectangle.BOX
            borderColor = COLOR_BORDER
            borderWidth = 0.75f
            backgroundColor = Color.WHITE
            minimumHeight = 26f
            cellEvent = PdfPCellEvent { _, position, _ ->
                try {
                    val rect = Rectangle(position.left + 2f, position.bottom + 2f, position.right - 2f, position.top - 2f)
                    val tf = TextField(writer, rect, "customer_name").apply {
                        fontSize = 10.5f
                        textColor = COLOR_PRIMARY
                        alignment = Element.ALIGN_LEFT
                        borderColor = COLOR_BORDER
                        borderWidth = 0.75f
                        backgroundColor = Color.WHITE
                    }
                    writer.addAnnotation(tf.textField)
                } catch (e: Exception) {
                    AppLogger.error(TAG, "Failed to create customer_name field: ${e.message}", e)
                }
            }
        }
        nameInputTable.addCell(nameInputCell)
        nameCell.addElement(nameInputTable)

        val dateCell = PdfPCell().apply {
            border = Rectangle.NO_BORDER
            setPaddingLeft(6f)
            setPaddingBottom(4f)
            addElement(Paragraph(labels.dateLabel, FONT_BODY_BOLD).apply { spacingAfter = 4f })
        }
        val dateInputTable = PdfPTable(1).apply { widthPercentage = 100f }
        val dateInputCell = PdfPCell().apply {
            border = Rectangle.BOX
            borderColor = COLOR_BORDER
            borderWidth = 0.75f
            backgroundColor = Color.WHITE
            minimumHeight = 26f
            cellEvent = PdfPCellEvent { _, position, _ ->
                try {
                    val rect = Rectangle(position.left + 2f, position.bottom + 2f, position.right - 2f, position.top - 2f)
                    val tf = TextField(writer, rect, "order_date").apply {
                        fontSize = 10.5f
                        textColor = COLOR_PRIMARY
                        alignment = Element.ALIGN_CENTER
                        borderColor = COLOR_BORDER
                        borderWidth = 0.75f
                        backgroundColor = Color.WHITE
                    }
                    writer.addAnnotation(tf.textField)
                } catch (e: Exception) {
                    AppLogger.error(TAG, "Failed to create order_date field: ${e.message}", e)
                }
            }
        }
        dateInputTable.addCell(dateInputCell)
        dateCell.addElement(dateInputTable)

        infoTable.addCell(nameCell)
        infoTable.addCell(dateCell)
        document.add(infoTable)

        // 3. Product Table
        val table = PdfPTable(floatArrayOf(52f, 26f, 22f)).apply {
            widthPercentage = 100f
            headerRows = 1
            setSpacingAfter(16f)
        }

        fun addTh(text: String, align: Int = Element.ALIGN_LEFT) {
            val cell = PdfPCell(Phrase(text, FONT_TH_BUYER)).apply {
                backgroundColor = COLOR_BG_LIGHT
                setPaddingTop(7f)
                setPaddingBottom(7f)
                setPaddingLeft(6f)
                setPaddingRight(6f)
                horizontalAlignment = align
                verticalAlignment = Element.ALIGN_MIDDLE
                border = Rectangle.BOX
                borderColor = COLOR_BORDER
                borderWidth = 0.75f
            }
            table.addCell(cell)
        }

        addTh(labels.colProductName, Element.ALIGN_LEFT)
        addTh(labels.colOrderQty, Element.ALIGN_CENTER)
        addTh(labels.colUnit, Element.ALIGN_CENTER)

        if (activeProducts.isEmpty()) {
            val emptyCell = PdfPCell(Phrase(labels.emptyCatalogMessage, FONT_MUTED)).apply {
                colspan = 3
                setPadding(18f)
                horizontalAlignment = Element.ALIGN_CENTER
                border = Rectangle.BOX
                borderColor = COLOR_BORDER
            }
            table.addCell(emptyCell)
        } else {
            activeProducts.forEachIndexed { idx, product ->
                val rowBg = if (idx % 2 == 0) Color.WHITE else COLOR_BG_ZEBRA
                val unitName = unitMap[product.unitTypeId]?.name ?: ""

                val nameCell = PdfPCell(Phrase(product.name, FONT_BODY_BOLD)).apply {
                    backgroundColor = rowBg
                    border = Rectangle.BOX
                    borderColor = COLOR_BORDER
                    borderWidth = 0.75f
                    minimumHeight = 26f
                    setPaddingLeft(6f)
                    setPaddingRight(6f)
                    setPaddingTop(5f)
                    setPaddingBottom(5f)
                    horizontalAlignment = Element.ALIGN_LEFT
                    verticalAlignment = Element.ALIGN_MIDDLE
                }

                val unitCell = PdfPCell(Phrase(unitName, FONT_BODY)).apply {
                    backgroundColor = rowBg
                    border = Rectangle.BOX
                    borderColor = COLOR_BORDER
                    borderWidth = 0.75f
                    minimumHeight = 26f
                    setPaddingLeft(6f)
                    setPaddingRight(6f)
                    setPaddingTop(5f)
                    setPaddingBottom(5f)
                    horizontalAlignment = Element.ALIGN_CENTER
                    verticalAlignment = Element.ALIGN_MIDDLE
                }

                val qtyCell = PdfPCell().apply {
                    backgroundColor = Color.WHITE
                    border = Rectangle.BOX
                    borderColor = COLOR_BORDER
                    borderWidth = 0.75f
                    minimumHeight = 26f
                    cellEvent = PdfPCellEvent { _, position, _ ->
                        try {
                            val rect = Rectangle(position.left + 2f, position.bottom + 2f, position.right - 2f, position.top - 2f)
                            val tf = TextField(writer, rect, "qty_${product.id}").apply {
                                fontSize = 10f
                                textColor = COLOR_PRIMARY
                                alignment = Element.ALIGN_CENTER
                                borderColor = COLOR_BORDER
                                borderWidth = 0.75f
                                backgroundColor = Color.WHITE
                            }
                            writer.addAnnotation(tf.textField)
                        } catch (e: Exception) {
                            AppLogger.error(TAG, "Failed to create qty field for ${product.id}: ${e.message}", e)
                        }
                    }
                }

                table.addCell(nameCell)
                table.addCell(qtyCell)
                table.addCell(unitCell)
            }
        }

        document.add(table)
        document.close()
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
            addElement(Paragraph(title, FONT_BRAND).apply { spacingAfter = 3f })
            if (subtitle.isNotBlank()) {
                addElement(Paragraph(subtitle, FONT_SUBTITLE))
            }
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
