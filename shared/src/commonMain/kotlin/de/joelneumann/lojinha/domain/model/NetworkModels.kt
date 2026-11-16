package de.joelneumann.lojinha.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val password: String = "")

@Serializable
data class LoginResponse(
    val success: Boolean,
    val message: String = "",
    val token: String? = null
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
data class CustomerLoginRequest(
    val userId: String,
    val pin: String = "",
    val keepLoggedIn: Boolean = false
)

@Serializable
data class CustomerLoginResponse(
    val success: Boolean,
    val message: String = "",
    val token: String? = null,
    val user: User? = null
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
data class CustomerVeggieStateResponse(
    val unitTypes: List<VeggieUnitType> = emptyList(),
    val products: List<VeggieProduct> = emptyList(),
    val activeRound: VeggieOrderRound? = null,
    val currentEntries: List<VeggieOrderEntry> = emptyList(),
    val previousRoundQuantities: Map<String, Long> = emptyMap(),
    val favoriteProductIds: Set<String> = emptySet()
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
    val settledAt: Long = 0L
)


