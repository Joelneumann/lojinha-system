package de.joelneumann.lojinha.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class UnifiedLoginRequest(
    val identifier: String = "",
    val password: String = "",
    val keepLoggedIn: Boolean = false
)

@Serializable
data class UnifiedLoginResponse(
    val success: Boolean,
    val role: String? = null,
    val message: String = ""
)

@Serializable
data class SessionStatusResponse(
    val role: String? = null
)

@Serializable
data class DeltaRequest(val id: String, val delta: Long)

@Serializable
data class PurchaseCorrectionRequest(
    val originalTransactionId: String,
    val newItems: List<TransactionItem>
)

@Serializable
data class StornoNonPurchaseRequest(
    val transactionId: String
)

@Serializable
data class AtomicTransactionRequest(
    val transaction: Transaction,
    val balanceDelta: Long = 0L,
    val stockDeltas: Map<String, Long> = emptyMap()
)

@Serializable
data class CustomerSettingsUpdateRequest(
    val language: Language = Language.BR,
    val secondaryCurrency: SecondaryCurrency? = null,
    val avatar: UserAvatarConfig? = null,
    val newPin: String? = null,
    val requirePinOnKiosk: Boolean = true
)

@Serializable
data class CustomerDisplaySettings(
    val usdExchangeRate: Double = 0.18,
    val eurExchangeRate: Double = 0.16,
    val yellowBalanceWarningThresholdCents: Long = 300_00L,
    val redBalanceWarningThresholdCents: Long = 0L
) {
    fun toSystemSettings(): SystemSettings = SystemSettings(
        adminPasswordHash = "",
        usdExchangeRate = usdExchangeRate,
        eurExchangeRate = eurExchangeRate,
        yellowBalanceWarningThresholdCents = yellowBalanceWarningThresholdCents,
        redBalanceWarningThresholdCents = redBalanceWarningThresholdCents
    )
}

@Serializable
data class CustomerVeggieStateResponse(
    val unitTypes: List<VeggieUnitType> = emptyList(),
    val products: List<VeggieProduct> = emptyList(),
    val activeRound: VeggieOrderRound? = null,
    val currentEntries: List<VeggieOrderEntry> = emptyList(),
    val previousRoundQuantities: Map<String, Long> = emptyMap(),
    val favoriteProductIds: Set<String> = emptySet(),
    val displaySettings: CustomerDisplaySettings? = null
)

@Serializable
data class UpdateCustomerVeggieFavoritesRequest(
    val productIds: Set<String> = emptySet()
)

@Serializable
data class SaveCustomerVeggieOrderRequest(
    val roundId: String,
    val productQuantitiesScaled: Map<String, Long> = emptyMap()
)

@Serializable
data class OpenVeggieRoundRequest(
    val buyerOperationFeeCents: Long = 0L,
    val transportFeeCents: Long = 0L,
    val openedAt: Long = 0L
)

@Serializable
data class CloseVeggieRoundRequest(
    val closedAt: Long = 0L
)

@Serializable
data class VeggieSettlementRequest(
    val buyerOperationFeeCents: Long = 0L,
    val transportFeeCents: Long = 0L,
    val transporterUserId: String? = null,
    val productSettlements: List<VeggieRoundProductSettlement> = emptyList(),
    val actualQuantitiesByEntryId: Map<String, Long> = emptyMap(),
    val pendingEntries: List<VeggieOrderEntry> = emptyList(),
    val settledAt: Long = 0L
)

@Serializable
data class BuyerVeggieProductRowDto(
    val productId: String,
    val name: String,
    val imageFileName: String? = null,
    val unitAbbreviation: String,
    val allowDecimals: Boolean = true,
    val deliveryUnitAbbreviation: String? = null,
    val totalOrderedScaled: Long,
    val marketUnitPriceCents: Long? = null,
    val totalSpentCents: Long? = null
) {
    val isDualUnit: Boolean
        get() = deliveryUnitAbbreviation != null && deliveryUnitAbbreviation != unitAbbreviation
}

@Serializable
data class BuyerVeggieRoundStateResponse(
    val round: VeggieOrderRound? = null,
    val items: List<BuyerVeggieProductRowDto> = emptyList(),
    val buyerOperationFeeCents: Long = 0L,
    val ordersChangedSinceLastPdf: Boolean = false
)

@Serializable
data class BuyerProductPriceDraft(
    val productId: String,
    val marketUnitPriceCents: Long? = null,
    val totalSpentCents: Long? = null
)

@Serializable
data class BuyerSaveDraftSettlementRequest(
    val roundId: String,
    val buyerOperationFeeCents: Long = 0L,
    val productSettlements: List<BuyerProductPriceDraft> = emptyList()
)



