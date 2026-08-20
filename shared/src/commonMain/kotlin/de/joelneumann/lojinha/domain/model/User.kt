package de.joelneumann.lojinha.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val name: String,
    val balance: Long = 0L,
    val language: Language = Language.DE,
    val secondaryCurrency: SecondaryCurrency = SecondaryCurrency.NONE,
    val pin: String? = null,
    val userBarcode: String? = null,
    val userBarcodeNumber: String? = null,
    val isActive: Boolean = true,
    val isDeleted: Boolean = false
) {
    init {
        require((userBarcode == null && userBarcodeNumber == null) || (userBarcode != null && userBarcodeNumber != null)) {
            "userBarcode and userBarcodeNumber must both be present or both be null"
        }
    }

    val initials: String
        get() {
            val parts = name.trim().split("\\s+".toRegex())
            return when {
                parts.isEmpty() || parts[0].isEmpty() -> "?"
                parts.size == 1 -> parts[0].take(2).uppercase()
                else -> "${parts[0].first()}${parts.last().first()}".uppercase()
            }
        }
}
