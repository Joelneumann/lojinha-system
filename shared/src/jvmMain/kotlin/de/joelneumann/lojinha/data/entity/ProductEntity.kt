package de.joelneumann.lojinha.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.joelneumann.lojinha.domain.model.Barcode
import de.joelneumann.lojinha.domain.model.Product
import de.joelneumann.lojinha.domain.model.UnitType

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    val barcodes: List<Barcode>,
    val basePrice: Long,
    val unitType: String,
    val stockQuantity: Long,
    val customMarkupPercent: Double?,
    val isActive: Boolean
) {
    fun toDomain(): Product = Product(
        id = id,
        name = name,
        barcodes = barcodes,
        basePrice = basePrice,
        unitType = try { UnitType.valueOf(unitType) } catch (e: Exception) { UnitType.PIECE },
        stockQuantity = stockQuantity.coerceAtLeast(0L),
        customMarkupPercent = customMarkupPercent,
        isActive = isActive
    )

    companion object {
        fun fromDomain(product: Product): ProductEntity = ProductEntity(
            id = product.id,
            name = product.name,
            barcodes = product.barcodes,
            basePrice = product.basePrice,
            unitType = product.unitType.name,
            stockQuantity = product.stockQuantity,
            customMarkupPercent = product.customMarkupPercent,
            isActive = product.isActive
        )
    }
}
