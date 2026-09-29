package de.joelneumann.lojinha.ui.utils

import de.joelneumann.lojinha.ui.i18n.AppStrings
import de.joelneumann.lojinha.domain.model.TransactionType

object SystemNoteHelper {
    fun decodeNote(note: String?, strings: AppStrings): String? {
        if (note == null) return null
        if (!note.startsWith("SYSNOTE|")) return note
        
        val parts = note.split("|")
        val code = parts.getOrNull(1) ?: return note
        
        return when (code) {
            "COMPLETE_STORNO" -> {
                val rawDate = parts.getOrNull(2) ?: ""
                val rawAmt = parts.getOrNull(3) ?: ""
                val date = rawDate.toLongOrNull()?.let { Formatting.formatTimestamp(it) } ?: rawDate
                val amount = rawAmt.toLongOrNull()?.let { Formatting.formatBrl(kotlin.math.abs(it)) } ?: rawAmt
                strings.sysNoteCompleteStorno(date = date, amount = amount)
            }
            "PARTIAL_STORNO" -> {
                val rawDate = parts.getOrNull(2) ?: ""
                val rawAmt = parts.getOrNull(3) ?: ""
                val date = rawDate.toLongOrNull()?.let { Formatting.formatTimestamp(it) } ?: rawDate
                val amount = rawAmt.toLongOrNull()?.let { Formatting.formatBrl(kotlin.math.abs(it)) } ?: rawAmt
                strings.sysNotePartialStorno(date = date, amount = amount)
            }
            "NON_PURCHASE_STORNO" -> {
                val rawType = parts.getOrNull(2) ?: ""
                val hasItems = parts.getOrNull(3)?.toBooleanStrictOrNull() ?: false
                
                // Map to localized string based on the TransactionType
                val typeLabel = when (rawType) {
                    TransactionType.ADMIN_DEPOSIT.name -> {
                        if (hasItems) strings.historyTypeCustomIncome else strings.historyTypeDeposit
                    }
                    TransactionType.ADMIN_WITHDRAWAL.name -> {
                        if (hasItems) strings.historyTypeCustomExpense else strings.historyTypeDebit
                    }
                    TransactionType.PURCHASE.name -> strings.historyTypePurchase
                    TransactionType.CANCELLATION.name -> strings.historyTypeCancellation
                    TransactionType.CORRECTION.name -> strings.historyTypeCorrection
                    else -> rawType
                }
                
                val rawDate = parts.getOrNull(4) ?: ""
                val rawAmt = parts.getOrNull(5) ?: ""
                val date = rawDate.toLongOrNull()?.let { Formatting.formatTimestamp(it) } ?: rawDate
                val amount = rawAmt.toLongOrNull()?.let { Formatting.formatBrl(kotlin.math.abs(it)) } ?: rawAmt

                strings.sysNoteNonPurchaseStorno(
                    type = typeLabel,
                    date = date,
                    amount = amount
                )
            }
            "ADMIN_DEPOSIT" -> strings.depositViaAdmin
            "ADMIN_DEBIT" -> strings.debitViaAdmin
            else -> note // fallback if code not recognized
        }
    }
}
