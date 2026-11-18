package de.joelneumann.lojinha.data.repository

import de.joelneumann.lojinha.data.dao.ShoppingListItemDao
import de.joelneumann.lojinha.data.entity.ShoppingListItemEntity
import de.joelneumann.lojinha.domain.model.ShoppingListItem
import de.joelneumann.lojinha.domain.repository.ShoppingListRepository
import de.joelneumann.lojinha.util.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class RoomShoppingListRepositoryImpl(
    private val dao: ShoppingListItemDao,
    private val onDataChanged: (() -> Unit)? = null
) : ShoppingListRepository {

    private companion object {
        const val TAG = "RoomShoppingListRepository"
    }

    private val writeMutex = Mutex()

    override fun getItemsFlow(): Flow<List<ShoppingListItem>> {
        return dao.getAllItemsFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getAllItems(): List<ShoppingListItem> {
        return dao.getAllItems().map { it.toDomain() }
    }

    override suspend fun addOrUpdateItem(item: ShoppingListItem) {
        writeMutex.withLock {
            val all = dao.getAllItems()
            val trimmedName = item.name.trim()
            val trimmedQty = item.quantity.trim()
            val existing = item.id.takeIf { it.isNotBlank() }?.let { id -> all.find { it.id == id } }
                ?: item.productId?.let { pid -> all.find { it.productId == pid } }
                ?: all.find { it.productId == null && it.name.equals(trimmedName, ignoreCase = true) }

            if (existing != null) {
                val updated = existing.copy(
                    productId = existing.productId ?: item.productId,
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
    }

    override suspend fun updateItem(item: ShoppingListItem) {
        writeMutex.withLock {
            val all = dao.getAllItems()
            val existing = all.find { it.id == item.id } ?: run {
                AppLogger.warn(TAG, "Skipping updateItem: item '${item.id}' not found")
                return@withLock
            }
            val updated = existing.copy(
                name = item.name.trim(),
                quantity = item.quantity.trim(),
                isConfirmedForCurrentExport = item.isConfirmedForCurrentExport ||
                    (existing.name != item.name.trim() || existing.quantity != item.quantity.trim())
            )
            dao.updateItem(updated)
            onDataChanged?.invoke()
        }
    }

    override suspend fun confirmItem(id: String) {
        writeMutex.withLock {
            dao.confirmItem(id)
            onDataChanged?.invoke()
        }
    }

    override suspend fun unconfirmItem(id: String) {
        writeMutex.withLock {
            dao.unconfirmItem(id)
            onDataChanged?.invoke()
        }
    }

    override suspend fun updateItemsOrder(items: List<ShoppingListItem>) {
        writeMutex.withLock {
            val existingMap = dao.getAllItems().associateBy { it.id }
            val updated = items.mapIndexedNotNull { index, item ->
                existingMap[item.id]?.copy(sortOrder = index)
            }
            if (updated.isNotEmpty()) {
                dao.updateItems(updated)
                onDataChanged?.invoke()
            }
        }
    }

    override suspend fun deleteItem(id: String) {
        writeMutex.withLock {
            dao.deleteItemById(id)
            onDataChanged?.invoke()
        }
    }

    override suspend fun clearAllItems() {
        writeMutex.withLock {
            dao.deleteAllItems()
            AppLogger.info(TAG, "Cleared all shopping list items")
            onDataChanged?.invoke()
        }
    }

    override suspend fun commitExportBaseline() {
        writeMutex.withLock {
            dao.commitExportBaseline()
            onDataChanged?.invoke()
        }
    }
}
