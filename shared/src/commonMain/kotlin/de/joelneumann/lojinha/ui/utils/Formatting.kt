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
                val kg = quantity.toDouble() / 1000.0
                val kgInt = quantity / 1000
                val remainderGrams = quantity % 1000
                if (remainderGrams == 0L) {
                    "$kgInt kg"
                } else {
                    val decStr = (remainderGrams / 10).toString().padStart(2, '0').trimEnd('0')
                    "$kgInt,$decStr kg"
                }
            }
        }
    }

    fun parseWeightInputToGrams(input: String): Long? {
        val cleaned = input.trim().replace("kg", "", ignoreCase = true).replace("g", "", ignoreCase = true).trim()
        if (cleaned.isBlank()) return null
        val normalized = cleaned.replace(',', '.')
        val valDouble = normalized.toDoubleOrNull() ?: return null
        if (valDouble <= 0.0) return null

        // If user typed 500 without decimals, and <= 50, treat as kg (e.g. 1.5 -> 1500g). If > 50 (e.g. 500), check if typed in grams or kg.
        return if (normalized.contains('.') || valDouble <= 20.0) {
            round(valDouble * 1000.0).toLong()
        } else {
            round(valDouble).toLong()
        }
    }
}
