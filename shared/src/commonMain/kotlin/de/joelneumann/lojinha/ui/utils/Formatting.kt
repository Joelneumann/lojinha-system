package de.joelneumann.lojinha.ui.utils

import de.joelneumann.lojinha.domain.model.SecondaryCurrency
import de.joelneumann.lojinha.domain.model.UnitType
import de.joelneumann.lojinha.ui.i18n.AppStrings
import de.joelneumann.lojinha.ui.i18n.I18n
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
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

        val totalCents = round(absAmount * 100.0).toLong()
        val integerPart = totalCents / 100
        val decimalPart = totalCents % 100
        val decString = decimalPart.toString().padStart(2, '0')

        val formattedValue = "${secondaryCurrency.symbol} $integerPart.$decString"
        val signedValue = if (isNegative) "-$formattedValue" else formattedValue
        return " (≈ $signedValue)"
    }

    fun formatQuantity(quantity: Long, unitType: UnitType, strings: AppStrings = I18n.get()): String {
        return when (unitType) {
            UnitType.PIECE -> strings.pieceUnitSuffix(quantity)
            UnitType.WEIGHT -> {
                val kgInt = quantity / 1000
                val remainderGrams = abs(quantity % 1000)
                val gramsStr = remainderGrams.toString().padStart(3, '0')
                "$kgInt,$gramsStr kg"
            }
        }
    }

    fun formatStockForAdmin(stockQuantity: Long, unitType: UnitType): String {
        return when (unitType) {
            UnitType.PIECE -> stockQuantity.toString()
            UnitType.WEIGHT -> {
                val kg = stockQuantity.toDouble() / 1000.0
                if (kg == kg.toLong().toDouble()) {
                    kg.toLong().toString()
                } else {
                    kg.toString().replace('.', ',')
                }
            }
        }
    }

    fun parseAdminStockToDb(input: String, unitType: UnitType): Long? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null
        val normalized = trimmed.replace(',', '.')

        return when (unitType) {
            UnitType.PIECE -> normalized.toLongOrNull()
            UnitType.WEIGHT -> {
                val kgDouble = normalized.toDoubleOrNull() ?: return null
                round(kgDouble * 1000.0).toLong()
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

        val grams = when {
            hasKg -> round(valDouble * 1000.0).toLong()
            hasG -> round(valDouble).toLong()
            normalized.contains('.') || valDouble <= 20.0 -> round(valDouble * 1000.0).toLong()
            else -> round(valDouble).toLong()
        }

        // Sanity limit: max 100 kg (100,000 g) per weighted item to prevent barcode scanner overflow
        if (grams <= 0L || grams > 100_000L) return null
        return grams
    }

    enum class WeightUnitDisplay(val symbol: String) {
        KG("KG"),
        G("G")
    }

    fun detectWeightUnit(input: String): WeightUnitDisplay? {
        val trimmed = input.trim().lowercase()
        if (trimmed.isBlank()) return null

        val hasKg = trimmed.contains("kg")
        val hasG = !hasKg && trimmed.contains("g")

        if (hasKg) {
            val numStr = trimmed.replace("kg", "").trim().replace(',', '.')
            val kgVal = numStr.toDoubleOrNull() ?: return null
            if (kgVal <= 0.0 || kgVal > 100.0) return null
            return WeightUnitDisplay.KG
        }
        if (hasG) {
            val numStr = trimmed.replace("g", "").trim().replace(',', '.')
            val gVal = numStr.toDoubleOrNull() ?: return null
            if (gVal <= 0.0 || gVal > 100_000.0) return null
            return WeightUnitDisplay.G
        }

        val cleaned = trimmed.trim()
        if (cleaned.isBlank()) return null
        val normalized = cleaned.replace(',', '.')
        val valDouble = normalized.toDoubleOrNull() ?: return null

        if (cleaned.contains(',') || cleaned.contains('.')) {
            if (valDouble < 0.0 || valDouble > 100.0) return null
            return WeightUnitDisplay.KG
        }
        if (valDouble <= 0.0 || valDouble > 100_000.0) return null

        return if (valDouble <= 20.0) WeightUnitDisplay.KG else WeightUnitDisplay.G
    }

    fun formatTimestamp(
        timestampMs: Long,
        language: de.joelneumann.lojinha.domain.model.Language = de.joelneumann.lojinha.ui.i18n.LanguageManager.currentLanguage
    ): String {
        val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(timestampMs)
        val ldt = instant.toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
        val dayStr = ldt.dayOfMonth.toString().padStart(2, '0')
        val monthStr = ldt.monthNumber.toString().padStart(2, '0')
        val yearStr = ldt.year.toString()
        val hour24Str = ldt.hour.toString().padStart(2, '0')
        val minuteStr = ldt.minute.toString().padStart(2, '0')

        return when (language) {
            de.joelneumann.lojinha.domain.model.Language.EN -> {
                val hour12 = if (ldt.hour % 12 == 0) 12 else ldt.hour % 12
                val amPm = if (ldt.hour >= 12) "PM" else "AM"
                val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                val monthName = monthNames[ldt.monthNumber - 1]
                "$monthName $dayStr, $yearStr, ${hour12.toString().padStart(2, '0')}:$minuteStr $amPm"
            }
            de.joelneumann.lojinha.domain.model.Language.DE -> "$dayStr.$monthStr.$yearStr, $hour24Str:$minuteStr"
            de.joelneumann.lojinha.domain.model.Language.BR -> "$dayStr/$monthStr/$yearStr, $hour24Str:$minuteStr"
        }
    }

    fun parsePercentageInput(input: String): Double? {
        val cleaned = input.trim()
            .replace("%", "")
            .replace(',', '.')
            .trim()
        if (cleaned.isBlank()) return null
        val value = cleaned.toDoubleOrNull() ?: return null
        return if (value.isFinite()) value else null
    }

    fun formatDecimal(value: Double): String {
        if (!value.isFinite()) return "0"
        val isWhole = value == kotlin.math.floor(value)
        return if (isWhole) value.toLong().toString() else value.toString().replace('.', ',')
    }

    fun formatMarkupPercent(markup: Double): String {
        if (!markup.isFinite() || markup < 0.0) return "+0%"
        val isWhole = markup == kotlin.math.floor(markup) && !markup.isInfinite()
        val formatted = if (isWhole) markup.toLong().toString() else markup.toString().replace('.', ',')
        return "+$formatted%"
    }

    fun formatMarkupDisplay(markup: Double, isCustom: Boolean, strings: de.joelneumann.lojinha.ui.i18n.AppStrings): String {
        val pct = formatMarkupPercent(markup)
        val suffix = if (isCustom) strings.markupCustomSuffix else strings.markupStandardSuffix
        return "${strings.markupPercent}: $pct ($suffix)"
    }
}

