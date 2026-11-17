package de.joelneumann.lojinha.domain.repository

import de.joelneumann.lojinha.domain.model.ShoppingListItem
import kotlinx.coroutines.flow.Flow

interface ShoppingListRepository {
    fun getItemsFlow(): Flow<List<ShoppingListItem>>
    suspend fun getAllItems(): List<ShoppingListItem>
    suspend fun addOrUpdateItem(item: ShoppingListItem)
    suspend fun updateItem(item: ShoppingListItem)
    suspend fun confirmItem(id: String)
    suspend fun updateItemsOrder(items: List<ShoppingListItem>)
    suspend fun deleteItem(id: String)
    suspend fun clearAllItems()
    suspend fun commitExportBaseline()
}
