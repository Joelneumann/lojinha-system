package de.joelneumann.lojinha.domain.model

import kotlinx.serialization.Serializable
import kotlin.math.roundToLong

const val VEGGIE_QTY_SCALE: Long = 1000L
const val MAX_VEGGIE_QUANTITY_SCALED: Long = 100_000L * VEGGIE_QTY_SCALE // 100,000 units max (prevents Long overflow)
const val MAX_VEGGIE_MONEY_CENTS: Long = 10_000_000L // R$ 100.000,00 max per field (matches Formatting.parsePriceInputToCents)

@Serializable
enum class VeggieRoundStatus {
    OPEN,
    CLOSED,
    SETTLED
}

@Serializable
data class VeggieUnitType(
    val id: String,
    val name: String,
    val allowDecimals: Boolean = false,
    val isActive: Boolean = true,
    val updatedAt: Long = 0L
)

@Serializable
data class VeggieProduct(
    val id: String,
    val name: String,
    val description: String? = null,
    val estimatedPriceCents: Long? = null,
    val unitTypeId: String,
    val deliveryUnitTypeId: String? = null,
    val imageFileName: String? = null,
    val excludeFromBuyerPdf: Boolean = false,
    val isActive: Boolean = true,
    val isDeleted: Boolean = false,
    val updatedAt: Long = 0L
) {
    val isDualUnit: Boolean
        get() = deliveryUnitTypeId != null && deliveryUnitTypeId != unitTypeId
}

@Serializable
data class VeggieOrderRound(
    val id: String,
    val openedAt: Long,
    val closedAt: Long? = null,
    val settledAt: Long? = null,
    val status: VeggieRoundStatus = VeggieRoundStatus.OPEN,
    val buyerOperationFeeCents: Long = 0L,
    val transportFeeCents: Long = 0L,
    val transporterUserId: String? = null,
    val buyerPdfExportedAt: Long? = null,
    val buyerPortalPdfExportedAt: Long? = null,
    val workerPdfExportedAt: Long? = null,
    val ordersUpdatedAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    val combinedFeeCents: Long
        get() = buyerOperationFeeCents.coerceAtLeast(0L) + transportFeeCents.coerceAtLeast(0L)

    val isAdminBuyerPdfOutdated: Boolean
        get() = buyerPdfExportedAt != null && ordersUpdatedAt > buyerPdfExportedAt

    val isBuyerPortalPdfOutdated: Boolean
        get() = buyerPortalPdfExportedAt != null && ordersUpdatedAt > buyerPortalPdfExportedAt
}

@Serializable
data class VeggieRoundProductSettlement(
    val roundId: String,
    val veggieProductId: String,
    val marketUnitPriceCents: Long? = null,
    val totalSpentCents: Long? = null,
    val updatedAt: Long = 0L
)

@Serializable
data class VeggieOrderEntry(
    val id: String,
    val roundId: String,
    val userId: String,
    val userNameSnapshot: String,
    val veggieProductId: String,
    val veggieNameSnapshot: String,
    val unitNameSnapshot: String,
    val allowDecimalsSnapshot: Boolean = false,
    val deliveryUnitNameSnapshot: String? = null,
    val deliveryAllowDecimalsSnapshot: Boolean? = null,
    val orderedQuantityScaled: Long,
    val actualQuantityScaled: Long? = null,
    val effectiveUnitPriceCents: Long? = null,
    val finalItemTotalCents: Long? = null,
    val updatedAt: Long = 0L
) {
    val deliveredQuantityScaled: Long
        get() = actualQuantityScaled ?: orderedQuantityScaled

    val effectiveDeliveryUnitName: String
        get() = deliveryUnitNameSnapshot ?: unitNameSnapshot

    val effectiveAllowDecimals: Boolean
        get() = deliveryAllowDecimalsSnapshot ?: allowDecimalsSnapshot

    val isDualUnit: Boolean
        get() = deliveryUnitNameSnapshot != null && deliveryUnitNameSnapshot != unitNameSnapshot
}

object VeggieQuantityScaling {
    fun fromWholeUnits(units: Long): Long = (units * VEGGIE_QTY_SCALE).coerceIn(0L, MAX_VEGGIE_QUANTITY_SCALED)

    fun fromDouble(value: Double, allowDecimals: Boolean): Long {
        if (value <= 0.0 || !value.isFinite()) return 0L
        return if (allowDecimals) {
            (value * VEGGIE_QTY_SCALE).roundToLong().coerceIn(0L, MAX_VEGGIE_QUANTITY_SCALED)
        } else {
            (value.roundToLong().coerceAtLeast(0L) * VEGGIE_QTY_SCALE).coerceAtMost(MAX_VEGGIE_QUANTITY_SCALED)
        }
    }

    fun parseQuantityScaled(input: String, allowDecimals: Boolean): Long? {
        val clean = input.trim().replace(',', '.')
        if (clean.isEmpty()) return 0L
        return if (allowDecimals) {
            val dbl = clean.toDoubleOrNull() ?: return null
            if (dbl < 0.0 || !dbl.isFinite() || dbl > (MAX_VEGGIE_QUANTITY_SCALED.toDouble() / VEGGIE_QTY_SCALE)) return null
            (dbl * VEGGIE_QTY_SCALE).roundToLong().coerceIn(0L, MAX_VEGGIE_QUANTITY_SCALED)
        } else {
            val whole = clean.toLongOrNull() ?: return null
            if (whole < 0L || whole > MAX_VEGGIE_QUANTITY_SCALED / VEGGIE_QTY_SCALE) return null
            whole * VEGGIE_QTY_SCALE
        }
    }

    fun formatScaledQuantity(
        scaled: Long,
        allowDecimals: Boolean,
        decimalSeparator: Char = ','
    ): String {
        val clamped = scaled.coerceAtLeast(0L)
        if (!allowDecimals || clamped % VEGGIE_QTY_SCALE == 0L) {
            return (clamped / VEGGIE_QTY_SCALE).toString()
        }
        val whole = clamped / VEGGIE_QTY_SCALE
        val fracRaw = (clamped % VEGGIE_QTY_SCALE).toString().padStart(3, '0').trimEnd('0')
        return if (fracRaw.isEmpty()) {
            whole.toString()
        } else {
            "$whole$decimalSeparator$fracRaw"
        }
    }
}
