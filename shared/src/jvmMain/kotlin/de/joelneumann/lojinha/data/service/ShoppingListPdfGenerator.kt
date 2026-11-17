package de.joelneumann.lojinha.data.service

import com.lowagie.text.*
import com.lowagie.text.pdf.*
import de.joelneumann.lojinha.domain.model.Language
import de.joelneumann.lojinha.domain.model.ShoppingListItem
import de.joelneumann.lojinha.ui.utils.Formatting
import de.joelneumann.lojinha.util.AppLogger
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ShoppingListPdfGenerator {

    private const val TAG = "ShoppingListPdfGenerator"

    // Theme Colors (aligned with VeggieOrderPdfGenerator)
    private val COLOR_PRIMARY = Color(15, 23, 42)        // Slate 900
    private val COLOR_TEXT_MUTED = Color(100, 116, 139)  // Slate 500
    private val COLOR_BG_LIGHT = Color(241, 245, 249)    // Slate 100
    private val COLOR_BG_ZEBRA = Color(248, 250, 252)    // Slate 50
    private val COLOR_BORDER = Color(203, 213, 225)      // Slate 300

    // Fonts
    private val FONT_BRAND = Font(Font.HELVETICA, 15f, Font.BOLD, COLOR_PRIMARY)
    private val FONT_TITLE = Font(Font.HELVETICA, 12f, Font.BOLD, COLOR_PRIMARY)
    private val FONT_SUBTITLE = Font(Font.HELVETICA, 9.5f, Font.NORMAL, COLOR_TEXT_MUTED)
    private val FONT_TH = Font(Font.HELVETICA, 8.5f, Font.BOLD, COLOR_PRIMARY)
    private val FONT_BODY = Font(Font.HELVETICA, 9f, Font.NORMAL, COLOR_PRIMARY)
    private val FONT_BODY_BOLD = Font(Font.HELVETICA, 9f, Font.BOLD, COLOR_PRIMARY)
    private val FONT_MUTED = Font(Font.HELVETICA, 8f, Font.NORMAL, COLOR_TEXT_MUTED)

    private data class ShoppingListPdfLabels(
        val title: String,
        val itemsCountLabel: (Int) -> String,
        val colCheck: String,
        val colQuantity: String,
        val colName: String,
        val emptyMessage: String,
        val pagePrefix: String
    )

    private fun labelsFor(language: Language): ShoppingListPdfLabels = when (language) {
        Language.DE -> ShoppingListPdfLabels(
            title = "Lojinha — Einkaufsliste",
            itemsCountLabel = { count -> if (count == 1) "1 Eintrag" else "$count Einträge" },
            colCheck = "",
            colQuantity = "Menge",
            colName = "Artikel / Produkt",
            emptyMessage = "Keine Einträge in der Einkaufsliste vorhanden.",
            pagePrefix = "Seite"
        )
        Language.EN -> ShoppingListPdfLabels(
            title = "Lojinha — Shopping List",
            itemsCountLabel = { count -> if (count == 1) "1 item" else "$count items" },
            colCheck = "",
            colQuantity = "Quantity",
            colName = "Item / Product",
            emptyMessage = "No items on the shopping list.",
            pagePrefix = "Page"
        )
        Language.BR -> ShoppingListPdfLabels(
            title = "Lojinha — Lista de Compras",
            itemsCountLabel = { count -> if (count == 1) "1 item" else "$count itens" },
            colCheck = "",
            colQuantity = "Quantidade",
            colName = "Produto / Item",
            emptyMessage = "Nenhum item na lista de compras.",
            pagePrefix = "Pág."
        )
    }

    fun generateBytes(
        items: List<ShoppingListItem>,
        language: Language = Language.DE
    ): ByteArray {
        val out = ByteArrayOutputStream()
        writePdf(out, items, language)
        return out.toByteArray()
    }

    fun generatePdf(
        outputFile: File,
        items: List<ShoppingListItem>,
        language: Language = Language.DE
    ): File {
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { fos ->
            writePdf(fos, items, language)
        }
        AppLogger.info(TAG, "Generated Shopping List PDF at: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
        return outputFile
    }

    private fun writePdf(
        outputStream: java.io.OutputStream,
        items: List<ShoppingListItem>,
        language: Language
    ) {
        val labels = labelsFor(language)
        val sortedItems = items.sortedWith(compareBy<ShoppingListItem> { it.sortOrder }.thenBy { it.createdAt })

        // Compact A4 layout to minimize paper usage
        val document = Document(PageSize.A4, 30f, 30f, 30f, 40f)
        val writer = PdfWriter.getInstance(document, outputStream)
        writer.pageEvent = ShoppingListFooterPageEvent(labels)
        document.open()

        addDocumentHeader(
            document = document,
            title = labels.title,
            dateBadge = SimpleDateFormat("dd.MM.yyyy", Locale.US).format(Date()),
            subtitle = labels.itemsCountLabel(sortedItems.size),
            language = language
        )

        val table = PdfPTable(floatArrayOf(8f, 22f, 70f)).apply {
            widthPercentage = 100f
            headerRows = 1
        }

        fun addTh(text: String, align: Int = Element.ALIGN_LEFT) {
            val cell = PdfPCell(Phrase(text, FONT_TH)).apply {
                backgroundColor = COLOR_BG_LIGHT
                setPaddingTop(5f)
                setPaddingBottom(5f)
                setPaddingLeft(5f)
                setPaddingRight(5f)
                horizontalAlignment = align
                verticalAlignment = Element.ALIGN_MIDDLE
                border = Rectangle.BOX
                borderColor = COLOR_BORDER
                borderWidth = 0.75f
            }
            table.addCell(cell)
        }

        addTh(labels.colCheck, Element.ALIGN_CENTER)
        addTh(labels.colQuantity, Element.ALIGN_CENTER)
        addTh(labels.colName, Element.ALIGN_LEFT)

        if (sortedItems.isEmpty()) {
            val emptyCell = PdfPCell(Phrase(labels.emptyMessage, FONT_MUTED)).apply {
                colspan = 3
                setPadding(16f)
                horizontalAlignment = Element.ALIGN_CENTER
                border = Rectangle.BOX
                borderColor = COLOR_BORDER
                borderWidth = 0.75f
            }
            table.addCell(emptyCell)
        } else {
            val checkboxEvent = CheckboxCellEvent()
            sortedItems.forEachIndexed { idx, item ->
                val rowBg = if (idx % 2 == 0) Color.WHITE else COLOR_BG_ZEBRA

                // Col 1: Crisp vector checkbox square
                val checkCell = PdfPCell(Phrase("")).apply {
                    backgroundColor = rowBg
                    border = Rectangle.BOX
                    borderColor = COLOR_BORDER
                    borderWidth = 0.75f
                    minimumHeight = 18f
                    setPadding(3f)
                    cellEvent = checkboxEvent
                }
                table.addCell(checkCell)

                // Col 2: Quantity
                val qtyCell = PdfPCell(Phrase(item.quantity, FONT_BODY_BOLD)).apply {
                    backgroundColor = rowBg
                    border = Rectangle.BOX
                    borderColor = COLOR_BORDER
                    borderWidth = 0.75f
                    minimumHeight = 18f
                    setPaddingLeft(5f)
                    setPaddingRight(5f)
                    setPaddingTop(3f)
                    setPaddingBottom(3f)
                    horizontalAlignment = Element.ALIGN_CENTER
                    verticalAlignment = Element.ALIGN_MIDDLE
                }
                table.addCell(qtyCell)

                // Col 3: Product / Item Name
                val nameCell = PdfPCell(Phrase(item.name, FONT_BODY)).apply {
                    backgroundColor = rowBg
                    border = Rectangle.BOX
                    borderColor = COLOR_BORDER
                    borderWidth = 0.75f
                    minimumHeight = 18f
                    setPaddingLeft(6f)
                    setPaddingRight(6f)
                    setPaddingTop(3f)
                    setPaddingBottom(3f)
                    horizontalAlignment = Element.ALIGN_LEFT
                    verticalAlignment = Element.ALIGN_MIDDLE
                }
                table.addCell(nameCell)
            }
        }

        document.add(table)
        document.close()
    }

    private class CheckboxCellEvent : PdfPCellEvent {
        override fun cellLayout(
            cell: PdfPCell,
            position: Rectangle,
            canvases: Array<out PdfContentByte>
        ) {
            val cb = canvases[PdfPTable.LINECANVAS]
            val boxSize = 9.5f
            val x = (position.left + position.right) / 2f - boxSize / 2f
            val y = (position.bottom + position.top) / 2f - boxSize / 2f

            cb.saveState()
            cb.setColorStroke(COLOR_PRIMARY)
            cb.setLineWidth(0.9f)
            cb.rectangle(x, y, boxSize, boxSize)
            cb.stroke()
            cb.restoreState()
        }
    }

    private fun addDocumentHeader(
        document: Document,
        title: String,
        dateBadge: String,
        subtitle: String,
        language: Language
    ) {
        val headerTable = PdfPTable(floatArrayOf(68f, 32f)).apply {
            widthPercentage = 100f
            setSpacingAfter(6f)
        }

        val leftCell = PdfPCell().apply {
            border = Rectangle.NO_BORDER
            setPaddingBottom(2f)
            addElement(Paragraph(title, FONT_BRAND).apply { spacingAfter = 2f })
            if (subtitle.isNotBlank()) {
                addElement(Paragraph(subtitle, FONT_SUBTITLE))
            }
        }
        val rightCell = PdfPCell().apply {
            border = Rectangle.NO_BORDER
            horizontalAlignment = Element.ALIGN_RIGHT
            setPaddingBottom(2f)
            addElement(Paragraph(dateBadge, FONT_TITLE).apply {
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
            setSpacingAfter(8f)
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

    private class ShoppingListFooterPageEvent(
        private val labels: ShoppingListPdfLabels
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
            val footerText = "Lojinha  •  $generatedAt  •  ${labels.pagePrefix} $pageNum / "

            val textWidth = baseFont.getWidthPoint(footerText, 8f)
            val y = document.bottom() - 14f
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
