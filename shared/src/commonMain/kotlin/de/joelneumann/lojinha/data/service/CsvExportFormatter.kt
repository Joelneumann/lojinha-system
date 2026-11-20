package de.joelneumann.lojinha.data.service

import de.joelneumann.lojinha.domain.model.AdminNote
import de.joelneumann.lojinha.domain.model.Product
import de.joelneumann.lojinha.domain.model.ProductHistoryEntry
import de.joelneumann.lojinha.domain.model.Transaction
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.domain.model.VeggieOrderEntry
import de.joelneumann.lojinha.domain.model.VeggieOrderRound
import de.joelneumann.lojinha.domain.model.VeggieProduct
import de.joelneumann.lojinha.domain.model.VeggieQuantityScaling
import de.joelneumann.lojinha.domain.model.VeggieRoundProductSettlement
import de.joelneumann.lojinha.domain.model.VeggieUnitType
import de.joelneumann.lojinha.security.PasswordHasher
import de.joelneumann.lojinha.ui.utils.Formatting
import de.joelneumann.lojinha.ui.utils.IsoWeekUtils
import de.joelneumann.lojinha.ui.utils.PdfFilenames
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Pure-Kotlin CSV formatter for on-demand Admin CSV exports (Desktop & Web).
 * Uses semicolon (`;`) delimiter and UTF-8 BOM (`\uFEFF`) for Excel compatibility,
 * and excludes soft-deleted items (`isDeleted == true`).
 */
object CsvExportFormatter {
    const val UTF8_BOM = "\uFEFF"
    const val CSV_DELIMITER = ";"

    private val json = Json { ignoreUnknownKeys = true }

    fun escapeCsv(value: String): String {
        val needsQuotes = value.contains(CSV_DELIMITER) ||
            value.contains(',') ||
            value.contains('"') ||
            value.contains('\n') ||
            value.contains('\r') ||
            value.startsWith(" ") ||
            value.endsWith(" ")
        return if (needsQuotes) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    private fun buildCsv(lines: List<String>): String {
        return UTF8_BOM + lines.joinToString("\n")
    }

    fun formatProductsCsv(products: List<Product>): String {
        val lines = mutableListOf(
            "id;name;barcodes;basePrice;unitType;stockQuantity;customMarkupPercent;isActive"
        )
        products.forEach { p ->
            val barcodeStr = p.barcodes.joinToString("|") { "${it.code}:${it.description ?: ""}" }
            lines.add(
                listOf(
                    escapeCsv(p.id),
                    escapeCsv(p.name),
                    escapeCsv(barcodeStr),
                    escapeCsv(Formatting.formatBrlCanonical(p.basePrice)),
                    p.unitType.name,
                    p.stockQuantity.toString(),
                    p.customMarkupPercent?.toString() ?: "",
                    p.isActive.toString()
                ).joinToString(CSV_DELIMITER)
            )
        }
        return buildCsv(lines)
    }

    fun formatProductHistoryCsv(entries: List<ProductHistoryEntry>): String {
        val lines = mutableListOf(
            "id;productId;timestamp;type;userName;quantity;unitType;unitPrice;isCancelled;stockAfter;note"
        )
        entries.forEach { entry ->
            lines.add(
                listOf(
                    escapeCsv(entry.id),
                    escapeCsv(entry.productId),
                    entry.timestamp.toString(),
                    entry.type.name,
                    escapeCsv(entry.userName ?: ""),
                    entry.quantity.toString(),
                    entry.unitType.name,
                    entry.unitPrice?.let { escapeCsv(Formatting.formatBrlCanonical(it)) } ?: "",
                    entry.isCancelled.toString(),
                    entry.stockAfter?.toString() ?: "",
                    escapeCsv(entry.note ?: "")
                ).joinToString(CSV_DELIMITER)
            )
        }
        return buildCsv(lines)
    }

    fun formatUsersCsv(users: List<User>): String {
        val lines = mutableListOf(
            "id;name;nickname;balance;language;secondaryCurrency;pin;userBarcode;userBarcodeNumber;isActive;isDeleted;avatarType;avatarEmoji;avatarColor;requirePinOnKiosk"
        )
        users.filter { !it.isDeleted }.forEach { u ->
            val hashedPin = u.pin?.let {
                if (PasswordHasher.isHash(it)) it else PasswordHasher.hash(it)
            } ?: ""
            lines.add(
                listOf(
                    escapeCsv(u.id),
                    escapeCsv(u.name),
                    escapeCsv(u.nickname ?: ""),
                    escapeCsv(Formatting.formatBrlCanonical(u.balance)),
                    u.language.code,
                    u.secondaryCurrency.name,
                    escapeCsv(hashedPin),
                    escapeCsv(u.userBarcode ?: ""),
                    escapeCsv(u.userBarcodeNumber ?: ""),
                    u.isActive.toString(),
                    u.isDeleted.toString(),
                    escapeCsv(u.avatar.type.name),
                    escapeCsv(u.avatar.emoji),
                    escapeCsv(u.avatar.colorHex),
                    u.requirePinOnKiosk.toString()
                ).joinToString(CSV_DELIMITER)
            )
        }
        return buildCsv(lines)
    }

    fun formatTransactionsCsv(transactions: List<Transaction>): String {
        val lines = mutableListOf(
            "id;userId;userNameSnapshot;timestamp;type;referenceTransactionId;note;totalAmount;itemCount;items;userBalanceBefore;userBalanceAfter"
        )
        transactions.forEach { t ->
            val itemsSerialized = json.encodeToString(t.items)
            lines.add(
                listOf(
                    escapeCsv(t.id),
                    escapeCsv(t.userId),
                    escapeCsv(t.userNameSnapshot),
                    t.timestamp.toString(),
                    t.type.name,
                    escapeCsv(t.referenceTransactionId ?: ""),
                    escapeCsv(t.note ?: ""),
                    escapeCsv(Formatting.formatBrlCanonical(t.totalAmount)),
                    t.items.size.toString(),
                    escapeCsv(itemsSerialized),
                    t.userBalanceBefore?.let { escapeCsv(Formatting.formatBrlCanonical(it)) } ?: "",
                    t.userBalanceAfter?.let { escapeCsv(Formatting.formatBrlCanonical(it)) } ?: ""
                ).joinToString(CSV_DELIMITER)
            )
        }
        return buildCsv(lines)
    }

    fun formatAdminNotesCsv(notes: List<AdminNote>): String {
        val lines = mutableListOf(
            "id;userId;userNameSnapshot;timestamp;message"
        )
        notes.forEach { n ->
            lines.add(
                listOf(
                    escapeCsv(n.id),
                    escapeCsv(n.userId),
                    escapeCsv(n.userNameSnapshot),
                    n.timestamp.toString(),
                    escapeCsv(n.message)
                ).joinToString(CSV_DELIMITER)
            )
        }
        return buildCsv(lines)
    }

    fun formatVeggieProductsCsv(
        products: List<VeggieProduct>,
        unitTypes: List<VeggieUnitType>
    ): String {
        val veggieUnitsById = unitTypes.associateBy { it.id }
        val lines = mutableListOf(
            "id;name;description;estimatedPrice;unitTypeId;unitTypeName;allowDecimals;imageFileName;excludeFromBuyerPdf;isActive;isDeleted;deliveryUnitTypeId"
        )
        products.filter { !it.isDeleted }.forEach { vp ->
            val unit = veggieUnitsById[vp.unitTypeId]
            lines.add(
                listOf(
                    escapeCsv(vp.id),
                    escapeCsv(vp.name),
                    escapeCsv(vp.description ?: ""),
                    vp.estimatedPriceCents?.let { escapeCsv(Formatting.formatBrlCanonical(it)) } ?: "",
                    escapeCsv(vp.unitTypeId),
                    escapeCsv(unit?.name ?: ""),
                    (unit?.allowDecimals ?: false).toString(),
                    escapeCsv(vp.imageFileName ?: ""),
                    vp.excludeFromBuyerPdf.toString(),
                    vp.isActive.toString(),
                    vp.isDeleted.toString(),
                    escapeCsv(vp.deliveryUnitTypeId ?: "")
                ).joinToString(CSV_DELIMITER)
            )
        }
        return buildCsv(lines)
    }

    fun formatVeggieOrdersCsv(
        rounds: List<VeggieOrderRound>,
        entriesByRoundId: Map<String, List<VeggieOrderEntry>>,
        settlementsByRoundId: Map<String, List<VeggieRoundProductSettlement>>
    ): String {
        val lines = mutableListOf(
            "roundId;isoYear;weekOfYear;openedAt;closedAt;settledAt;status;buyerOperationFee;transportFee;transporterUserId;entryId;userId;userName;veggieProductId;veggieName;unitName;allowDecimals;orderedQuantity;actualQuantity;marketUnitPrice;productTotalSpent;effectiveUnitPrice;finalItemTotal;deliveryUnitName;deliveryAllowDecimals"
        )
        rounds.forEach { r ->
            val weekInfo = IsoWeekUtils.fromEpochMillis(r.openedAt)
            val entries = entriesByRoundId[r.id].orEmpty()
            val settlementsByProduct = settlementsByRoundId[r.id].orEmpty().associateBy { it.veggieProductId }
            val buyerFeeStr = Formatting.formatBrlCanonical(r.buyerOperationFeeCents)
            val transportFeeStr = Formatting.formatBrlCanonical(r.transportFeeCents)

            if (entries.isEmpty()) {
                lines.add(
                    listOf(
                        escapeCsv(r.id),
                        weekInfo.isoYear.toString(),
                        weekInfo.weekOfYear.toString(),
                        r.openedAt.toString(),
                        r.closedAt?.toString() ?: "",
                        r.settledAt?.toString() ?: "",
                        r.status.name,
                        escapeCsv(buyerFeeStr),
                        escapeCsv(transportFeeStr),
                        escapeCsv(r.transporterUserId ?: ""),
                        "", "", "", "", "", "", "", "", "", "", "", "", "", "", ""
                    ).joinToString(CSV_DELIMITER)
                )
            } else {
                entries.forEach { e ->
                    val settlement = settlementsByProduct[e.veggieProductId]
                    val orderedStr = VeggieQuantityScaling.formatScaledQuantity(e.orderedQuantityScaled, e.allowDecimalsSnapshot)
                    val actualStr = e.actualQuantityScaled?.let {
                        VeggieQuantityScaling.formatScaledQuantity(it, e.deliveryAllowDecimalsSnapshot ?: e.allowDecimalsSnapshot)
                    } ?: ""
                    val marketUnitStr = settlement?.marketUnitPriceCents?.let { Formatting.formatBrlCanonical(it) } ?: ""
                    val totalSpentStr = settlement?.totalSpentCents?.let { Formatting.formatBrlCanonical(it) } ?: ""
                    val effectiveUnitStr = e.effectiveUnitPriceCents?.let { Formatting.formatBrlCanonical(it) } ?: ""
                    val finalItemTotalStr = e.finalItemTotalCents?.let { Formatting.formatBrlCanonical(it) } ?: ""

                    lines.add(
                        listOf(
                            escapeCsv(r.id),
                            weekInfo.isoYear.toString(),
                            weekInfo.weekOfYear.toString(),
                            r.openedAt.toString(),
                            r.closedAt?.toString() ?: "",
                            r.settledAt?.toString() ?: "",
                            r.status.name,
                            escapeCsv(buyerFeeStr),
                            escapeCsv(transportFeeStr),
                            escapeCsv(r.transporterUserId ?: ""),
                            escapeCsv(e.id),
                            escapeCsv(e.userId),
                            escapeCsv(e.userNameSnapshot),
                            escapeCsv(e.veggieProductId),
                            escapeCsv(e.veggieNameSnapshot),
                            escapeCsv(e.unitNameSnapshot),
                            e.allowDecimalsSnapshot.toString(),
                            escapeCsv(orderedStr),
                            escapeCsv(actualStr),
                            escapeCsv(marketUnitStr),
                            escapeCsv(totalSpentStr),
                            escapeCsv(effectiveUnitStr),
                            escapeCsv(finalItemTotalStr),
                            escapeCsv(e.deliveryUnitNameSnapshot ?: ""),
                            e.deliveryAllowDecimalsSnapshot?.toString() ?: ""
                        ).joinToString(CSV_DELIMITER)
                    )
                }
            }
        }
        return buildCsv(lines)
    }
}

object CsvExportFilenames {
    private fun withDate(base: String, dateStamp: String?): String {
        return if (!dateStamp.isNullOrBlank()) "${base}_${dateStamp}.csv" else "${base}.csv"
    }

    fun notesFilename(dateStamp: String? = null) = withDate("admin_notes", dateStamp)
    fun productsFilename(dateStamp: String? = null) = withDate("products", dateStamp)
    fun productHistoryFilename(productName: String, dateStamp: String? = null): String {
        val clean = PdfFilenames.sanitizeName(productName, fallback = "Product")
        return withDate("product_${clean}_history", dateStamp)
    }
    fun usersFilename(dateStamp: String? = null) = withDate("users", dateStamp)
    fun userTransactionsFilename(userName: String, dateStamp: String? = null): String {
        val clean = PdfFilenames.sanitizeName(userName).ifBlank { "User" }
        return withDate("user_${clean}_transactions", dateStamp)
    }
    fun transactionsFilename(dateStamp: String? = null) = withDate("transactions", dateStamp)
    fun veggieProductsFilename(dateStamp: String? = null) = withDate("veggie_products", dateStamp)
    fun veggieOrdersFilename(dateStamp: String? = null) = withDate("veggie_orders", dateStamp)
}
