package de.joelneumann.lojinha.ui.utils

import de.joelneumann.lojinha.domain.model.SecondaryCurrency
import de.joelneumann.lojinha.domain.model.UnitType
import kotlin.math.abs
import kotlin.math.round

object Formatting {

    fun formatBrl(cents: Long): String {
        val isNegative = cents < 0
        val absCents = abs(cents)
        val reais = absCents / 100
        val remainder = absCents % 100
        val centsString = remainder.toString().padStart(2, '0')
        val formatted = "R$ $reais,$centsString"
        return if (isNegative) "-$formatted" else formatted
    }

    fun formatSecondaryCurrency(brlCents: Long, secondaryCurrency: SecondaryCurrency, rate: Double): String {
        if (secondaryCurrency == SecondaryCurrency.NONE || rate <= 0.0) return ""
        val brlReais = brlCents.toDouble() / 100.0
        val convertedAmount = brlReais * rate
        val isNegative = convertedAmount < 0
        val absAmount = abs(convertedAmount)
        val integerPart = absAmount.toLong()
        val decimalPart = round((absAmount - integerPart) * 100).toLong()
        val decString = decimalPart.toString().padStart(2, '0')

        val formattedValue = "${secondaryCurrency.symbol} $integerPart.$decString"
        val signedValue = if (isNegative) "-$formattedValue" else formattedValue
        return " (≈ $signedValue)"
    }

    fun formatQuantity(quantity: Long, unitType: UnitType): String {
        return when (unitType) {
            UnitType.PIECE -> "$quantity pcs"
            UnitType.WEIGHT -> {
                val kgInt = quantity / 1000
                val remainderGrams = abs(quantity % 1000)
                val gramsStr = remainderGrams.toString().padStart(3, '0')
                "$kgInt,$gramsStr kg"
            }
        }
    }

    fun parseWeightInputToGrams(input: String): Long? {
        val trimmed = input.trim().lowercase()
        if (trimmed.isBlank()) return null

        val hasKg = trimmed.contains("kg")
        val hasG = !hasKg && trimmed.contains("g")

        val cleaned = trimmed
            .replace("kg", "")
            .replace("g", "")
            .trim()

        if (cleaned.isBlank()) return null
        val normalized = cleaned.replace(',', '.')
        val valDouble = normalized.toDoubleOrNull() ?: return null
        if (valDouble <= 0.0) return null

        return when {
            hasKg -> round(valDouble * 1000.0).toLong()
            hasG -> round(valDouble).toLong()
            normalized.contains('.') || valDouble <= 20.0 -> round(valDouble * 1000.0).toLong()
            else -> round(valDouble).toLong()
        }
    }

    fun formatTimestamp(timestampMs: Long, language: de.joelneumann.lojinha.domain.model.Language): String {
        val (pattern, locale) = when (language) {
            de.joelneumann.lojinha.domain.model.Language.EN -> "MMM dd, yyyy, hh:mm a" to java.util.Locale.US
            de.joelneumann.lojinha.domain.model.Language.DE -> "dd.MM.yyyy, HH:mm" to java.util.Locale.GERMANY
            de.joelneumann.lojinha.domain.model.Language.BR -> "dd/MM/yyyy, HH:mm" to java.util.Locale.forLanguageTag("pt-BR")
        }
        val sdf = java.text.SimpleDateFormat(pattern, locale)
        return sdf.format(java.util.Date(timestampMs))
    }
}
