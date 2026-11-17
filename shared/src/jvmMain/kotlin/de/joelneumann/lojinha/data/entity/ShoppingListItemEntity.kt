package de.joelneumann.lojinha.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import de.joelneumann.lojinha.domain.model.ShoppingListItem

@Entity(
    tableName = "shopping_list_items",
    indices = [
        Index(value = ["sortOrder"])
    ]
)
data class ShoppingListItemEntity(
    @PrimaryKey val id: String,
    val productId: String?,
    val name: String,
    val quantity: String,
    val sortOrder: Int,
    val createdAt: Long,
    val lastExportedName: String?,
    val lastExportedQuantity: String?,
    val isConfirmedForCurrentExport: Boolean
) {
    fun toDomain(): ShoppingListItem = ShoppingListItem(
        id = id,
        productId = productId,
        name = name,
        quantity = quantity,
        sortOrder = sortOrder,
        createdAt = createdAt,
        lastExportedName = lastExportedName,
        lastExportedQuantity = lastExportedQuantity,
        isConfirmedForCurrentExport = isConfirmedForCurrentExport
    )

    companion object {
        fun fromDomain(item: ShoppingListItem): ShoppingListItemEntity = ShoppingListItemEntity(
            id = item.id,
            productId = item.productId,
            name = item.name,
            quantity = item.quantity,
            sortOrder = item.sortOrder,
            createdAt = item.createdAt,
            lastExportedName = item.lastExportedName,
            lastExportedQuantity = item.lastExportedQuantity,
            isConfirmedForCurrentExport = item.isConfirmedForCurrentExport
        )
    }
}
