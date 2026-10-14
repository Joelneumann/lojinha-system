package de.joelneumann.lojinha.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class UserAvatarConfig(
    val type: AvatarType = AvatarType.INITIALS,
    val emoji: String = "😀",
    val colorHex: String = "#1E293B"
)

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
    val isDeleted: Boolean = false,
    val avatar: UserAvatarConfig = UserAvatarConfig()
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

const val DEFAULT_YELLOW_BALANCE_WARNING_THRESHOLD_CENTS: Long = 300_00L // R$ 300,00
const val DEFAULT_RED_BALANCE_WARNING_THRESHOLD_CENTS: Long = 0L        // R$ 0,00
const val LOW_BALANCE_WARNING_THRESHOLD_CENTS: Long = DEFAULT_YELLOW_BALANCE_WARNING_THRESHOLD_CENTS

fun User.balanceWarningLevel(
    yellowThresholdCents: Long = DEFAULT_YELLOW_BALANCE_WARNING_THRESHOLD_CENTS,
    redThresholdCents: Long = DEFAULT_RED_BALANCE_WARNING_THRESHOLD_CENTS
): BalanceWarningLevel = when {
    balance < redThresholdCents -> BalanceWarningLevel.NEGATIVE
    balance <= yellowThresholdCents -> BalanceWarningLevel.LOW_BALANCE
    else -> BalanceWarningLevel.NORMAL
}

val User.balanceWarningLevel: BalanceWarningLevel
    get() = balanceWarningLevel(DEFAULT_YELLOW_BALANCE_WARNING_THRESHOLD_CENTS, DEFAULT_RED_BALANCE_WARNING_THRESHOLD_CENTS)

