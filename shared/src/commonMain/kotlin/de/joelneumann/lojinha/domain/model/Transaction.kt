package de.joelneumann.lojinha.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class TransactionItem(
    val productId: String,
    val productName: String,
    val unitType: UnitType = UnitType.PIECE,
    val quantity: Long,
    val unitPriceAtPurchase: Long
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
    val items: List<TransactionItem> = emptyList()
)
