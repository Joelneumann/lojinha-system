package de.joelneumann.lojinha.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ProductHistoryType {
    STOCK_ADJUSTMENT,
    PURCHASE,
    CANCELLATION
}

@Serializable
data class ProductStockAdjustment(
    val id: String,
    val productId: String,
    val timestamp: Long,
    val deltaQuantity: Long,
    val resultingQuantity: Long? = null,
    val note: String? = null
)

@Serializable
data class ProductHistoryEntry(
    val id: String,
    val productId: String,
    val timestamp: Long,
    val type: ProductHistoryType,
    val userName: String? = null,
    val quantity: Long,
    val unitType: UnitType = UnitType.PIECE,
    val note: String? = null,
    val unitPrice: Long? = null,
    val isCancelled: Boolean = false,
    val stockAfter: Long? = null
)
