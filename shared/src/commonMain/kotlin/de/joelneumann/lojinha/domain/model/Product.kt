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
        if (markup.isNaN() || !markup.isFinite() || markup <= 0.0) return basePrice
        val multiplier = 1.0 + (markup / 100.0)
        val calculated = kotlin.math.round(basePrice * multiplier)
        if (!calculated.isFinite() || calculated > Long.MAX_VALUE.toDouble()) return basePrice
        return calculated.toLong().coerceAtLeast(basePrice)
    }
}
