package de.joelneumann.lojinha.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import de.joelneumann.lojinha.domain.model.ProductStockAdjustment

@Entity(
    tableName = "product_stock_adjustments",
    indices = [
        Index(value = ["productId"]),
        Index(value = ["timestamp"])
    ]
)
data class ProductStockAdjustmentEntity(
    @PrimaryKey val id: String,
    val productId: String,
    val timestamp: Long,
    val deltaQuantity: Long,
    val resultingQuantity: Long? = null,
    val note: String? = null
) {
    fun toDomain(): ProductStockAdjustment = ProductStockAdjustment(
        id = id,
        productId = productId,
        timestamp = timestamp,
        deltaQuantity = deltaQuantity,
        resultingQuantity = resultingQuantity,
        note = note
    )

    companion object {
        fun fromDomain(adj: ProductStockAdjustment): ProductStockAdjustmentEntity = ProductStockAdjustmentEntity(
            id = adj.id,
            productId = adj.productId,
            timestamp = adj.timestamp,
            deltaQuantity = adj.deltaQuantity,
            resultingQuantity = adj.resultingQuantity,
            note = adj.note
        )
    }
}
