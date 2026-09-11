package de.joelneumann.lojinha.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class BillingListType {
    FIXED,
    VARIABLE
}

@Serializable
data class BillingList(
    val id: String,
    val name: String,
    val type: BillingListType,
    val basePrice: Long? = null,
    val isDeleted: Boolean = false,
    val users: List<BillingListUser> = emptyList()
)

@Serializable
data class BillingListUser(
    val id: String,
    val listId: String,
    val userId: String,
    val quantity: Int
)
