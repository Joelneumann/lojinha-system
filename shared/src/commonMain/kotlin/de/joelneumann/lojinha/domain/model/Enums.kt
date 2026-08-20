package de.joelneumann.lojinha.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class Language(val code: String, val flagEmoji: String, val label: String) {
    BR("pt-BR", "🇧🇷", "Português (BR)"),
    EN("en", "🇬🇧", "English"),
    DE("de", "🇩🇪", "Deutsch");

    companion object {
        fun fromCode(code: String): Language = entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: DE
    }
}

@Serializable
enum class SecondaryCurrency(val code: String, val symbol: String) {
    NONE("NONE", ""),
    USD("USD", "$"),
    EUR("EUR", "€")
}

@Serializable
enum class UnitType {
    PIECE,
    WEIGHT
}

@Serializable
enum class TransactionType {
    PURCHASE,
    ADMIN_DEPOSIT,
    ADMIN_WITHDRAWAL,
    CANCELLATION,
    CORRECTION
}
