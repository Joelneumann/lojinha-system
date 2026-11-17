package de.joelneumann.lojinha.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ShoppingListItem(
    val id: String = "",
    val productId: String? = null,
    val name: String,
    val quantity: String,
    val sortOrder: Int = 0,
    val createdAt: Long = 0L,
    val lastExportedName: String? = null,
    val lastExportedQuantity: String? = null,
    val isConfirmedForCurrentExport: Boolean = false
) {
    val isNewlyAdded: Boolean
        get() = lastExportedQuantity == null

    val isContentChanged: Boolean
        get() = lastExportedQuantity != null &&
            (name != lastExportedName || quantity != lastExportedQuantity)

    val isReviewedForExport: Boolean
        get() = isNewlyAdded || isContentChanged || isConfirmedForCurrentExport

    val isPendingReview: Boolean
        get() = !isReviewedForExport
}
