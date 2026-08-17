package de.joelneumann.lojinha.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Barcode(
    val code: String,
    val description: String? = null
)

@Serializable
data class Product(
    val id: String,
    val name: String,
    val barcodes: List<Barcode> = emptyList(),
    val basePrice: Long,
    val unitType: UnitType = UnitType.PIECE,
    val stockQuantity: Long = 0L,
    val customMarkupPercent: Double? = null,
    val isActive: Boolean = true
) {
    fun calculateEffectiveUnitPrice(globalMarkupPercent: Double): Long {
        val markup = customMarkupPercent ?: globalMarkupPercent
        if (markup <= 0.0) return basePrice
        val multiplier = 1.0 + (markup / 100.0)
        return kotlin.math.round(basePrice * multiplier).toLong()
    }
}
