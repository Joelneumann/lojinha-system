package de.joelneumann.lojinha.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val password: String = "")

@Serializable
data class LoginResponse(val success: Boolean, val message: String = "")

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

