package de.joelneumann.lojinha.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class Language(val code: String, val label: String) {
    BR("pt-BR", "Português (BR)"),
    EN("en", "English"),
    DE("de", "Deutsch");

    companion object {
        val DEFAULT = BR

        fun fromCode(code: String, fallback: Language = DEFAULT): Language {
            val cleanCode = code.trim().replace('_', '-')
            return entries.firstOrNull {
                it.code.equals(cleanCode, ignoreCase = true) ||
                it.name.equals(cleanCode, ignoreCase = true) ||
                (it == BR && cleanCode.startsWith("pt", ignoreCase = true))
            } ?: fallback
        }
    }
}

@Serializable
enum class SecondaryCurrency(val code: String, val symbol: String) {
    NONE("NONE", ""),
    USD("USD", "US$"),
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
    CORRECTION,
    VEGGIE
}

@Serializable
enum class AvatarType {
    INITIALS,
    EMOJI
}

@Serializable
enum class BalanceWarningLevel {
    NORMAL,
    LOW_BALANCE,
    NEGATIVE
}

