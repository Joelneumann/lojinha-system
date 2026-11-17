package de.joelneumann.lojinha.data.repository

import de.joelneumann.lojinha.data.dao.ShoppingListItemDao
import de.joelneumann.lojinha.data.entity.ShoppingListItemEntity
import de.joelneumann.lojinha.domain.model.ShoppingListItem
import de.joelneumann.lojinha.domain.repository.ShoppingListRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class RoomShoppingListRepositoryImpl(
    private val dao: ShoppingListItemDao,
    private val onDataChanged: (() -> Unit)? = null
) : ShoppingListRepository {

    override fun getItemsFlow(): Flow<List<ShoppingListItem>> {
        return dao.getAllItemsFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getAllItems(): List<ShoppingListItem> {
        return dao.getAllItems().map { it.toDomain() }
    }

    override suspend fun addOrUpdateItem(item: ShoppingListItem) {
        val all = dao.getAllItems()
        val trimmedName = item.name.trim()
        val trimmedQty = item.quantity.trim()
        val existing = when {
            item.id.isNotBlank() -> all.find { it.id == item.id }
            item.productId != null -> all.find { it.productId == item.productId }
            else -> all.find { it.productId == null && it.name.equals(trimmedName, ignoreCase = true) }
        }

        if (existing != null) {
            val updated = existing.copy(
                name = trimmedName,
                quantity = trimmedQty,
                isConfirmedForCurrentExport = true
            )
            dao.updateItem(updated)
        } else {
            val nextOrder = (all.maxOfOrNull { it.sortOrder } ?: -1) + 1
            val newEntity = ShoppingListItemEntity(
                id = if (item.id.isBlank()) UUID.randomUUID().toString() else item.id,
                productId = item.productId,
                name = trimmedName,
                quantity = trimmedQty,
                sortOrder = nextOrder,
                createdAt = if (item.createdAt <= 0L) System.currentTimeMillis() else item.createdAt,
                lastExportedName = null,
                lastExportedQuantity = null,
                isConfirmedForCurrentExport = true
            )
            dao.insertItem(newEntity)
        }
        onDataChanged?.invoke()
    }

    override suspend fun updateItem(item: ShoppingListItem) {
        val all = dao.getAllItems()
        val existing = all.find { it.id == item.id } ?: return
        val updated = existing.copy(
            name = item.name.trim(),
            quantity = item.quantity.trim(),
            isConfirmedForCurrentExport = item.isConfirmedForCurrentExport ||
                (existing.name != item.name.trim() || existing.quantity != item.quantity.trim())
        )
        dao.updateItem(updated)
        onDataChanged?.invoke()
    }

    override suspend fun confirmItem(id: String) {
        dao.confirmItem(id)
        onDataChanged?.invoke()
    }

    override suspend fun unconfirmItem(id: String) {
        dao.unconfirmItem(id)
        onDataChanged?.invoke()
    }

    override suspend fun updateItemsOrder(items: List<ShoppingListItem>) {
        val existingMap = dao.getAllItems().associateBy { it.id }
        val updated = items.mapIndexedNotNull { index, item ->
            existingMap[item.id]?.copy(sortOrder = index)
        }
        if (updated.isNotEmpty()) {
            dao.updateItems(updated)
            onDataChanged?.invoke()
        }
    }

    override suspend fun deleteItem(id: String) {
        dao.deleteItemById(id)
        onDataChanged?.invoke()
    }

    override suspend fun clearAllItems() {
        dao.deleteAllItems()
        onDataChanged?.invoke()
    }

    override suspend fun commitExportBaseline() {
        dao.commitExportBaseline()
        onDataChanged?.invoke()
    }
}
