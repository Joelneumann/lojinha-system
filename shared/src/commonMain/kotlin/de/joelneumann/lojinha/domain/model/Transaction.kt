package de.joelneumann.lojinha.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class TransactionItem(
    val productId: String,
    val productName: String,
    val unitType: UnitType = UnitType.PIECE,
    val quantity: Long,
    val unitPriceAtPurchase: Long,
    val previousQuantity: Long? = null
) {
    val totalLinePrice: Long
        get() = when (unitType) {
            UnitType.PIECE -> unitPriceAtPurchase * quantity
            UnitType.WEIGHT -> kotlin.math.round((unitPriceAtPurchase * quantity) / 1000.0).toLong()
        }
}

@Serializable
data class Transaction(
    val id: String,
    val userId: String,
    val userNameSnapshot: String,
    val timestamp: Long,
    val type: TransactionType,
    val referenceTransactionId: String? = null,
    val note: String? = null,
    val totalAmount: Long,
    val items: List<TransactionItem> = emptyList(),
    val userBalanceBefore: Long? = null,
    val userBalanceAfter: Long? = null
) {
    companion object {
        fun computeEffectiveItems(
            originalItems: List<TransactionItem>,
            corrections: List<Transaction>,
            cancellation: Transaction? = null
        ): List<TransactionItem> {
            if (cancellation != null) {
                return originalItems.map { it.copy(quantity = 0L) }
            }
            if (corrections.isEmpty()) return originalItems

            val itemsMap = originalItems.associateBy { it.productId }.toMutableMap()
            val sortedCorrections = corrections.sortedBy { it.timestamp }
            for (corr in sortedCorrections) {
                for (item in corr.items) {
                    val existing = itemsMap[item.productId]
                    if (existing != null) {
                        itemsMap[item.productId] = existing.copy(quantity = item.quantity)
                    }
                }
            }
            return originalItems.map { itemsMap[it.productId] ?: it }
        }
    }
}

data class ItemAdjustmentDetails(
    val previousQuantity: Long,
    val quantityDifference: Long,
    val adjustmentAmount: Long
)

fun TransactionItem.calculateAdjustment(
    parentTransaction: Transaction?,
    totalTransactionAmount: Long,
    totalItemCount: Int
): ItemAdjustmentDetails {
    val (prev, diff, adjustment) = if (previousQuantity != null) {
        val p = previousQuantity
        val d = quantity - p
        val costDiff = when (unitType) {
            UnitType.PIECE -> unitPriceAtPurchase * d
            UnitType.WEIGHT -> {
                val newC = kotlin.math.round((unitPriceAtPurchase * quantity) / 1000.0).toLong()
                val prevC = kotlin.math.round((unitPriceAtPurchase * p) / 1000.0).toLong()
                newC - prevC
            }
        }
        Triple(p, d, -costDiff)
    } else if (totalItemCount == 1 && unitPriceAtPurchase > 0) {
        val d = when (unitType) {
            UnitType.PIECE -> -totalTransactionAmount / unitPriceAtPurchase
            UnitType.WEIGHT -> kotlin.math.round((-totalTransactionAmount * 1000.0) / unitPriceAtPurchase).toLong()
        }
        Triple(quantity - d, d, totalTransactionAmount)
    } else {
        val p = parentTransaction?.items?.firstOrNull { it.productId == productId }?.quantity ?: quantity
        val d = quantity - p
        val costDiff = when (unitType) {
            UnitType.PIECE -> unitPriceAtPurchase * d
            UnitType.WEIGHT -> {
                val newC = kotlin.math.round((unitPriceAtPurchase * quantity) / 1000.0).toLong()
                val prevC = kotlin.math.round((unitPriceAtPurchase * p) / 1000.0).toLong()
                newC - prevC
            }
        }
        Triple(p, d, -costDiff)
    }
    return ItemAdjustmentDetails(prev, diff, adjustment)
}

