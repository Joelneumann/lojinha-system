package de.joelneumann.lojinha.data.dao

import androidx.room.*
import de.joelneumann.lojinha.data.entity.ShoppingListItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingListItemDao {
    @Query("SELECT * FROM shopping_list_items ORDER BY sortOrder ASC, createdAt ASC")
    fun getAllItemsFlow(): Flow<List<ShoppingListItemEntity>>

    @Query("SELECT * FROM shopping_list_items ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun getAllItems(): List<ShoppingListItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ShoppingListItemEntity)

    @Update
    suspend fun updateItem(item: ShoppingListItemEntity)

    @Update
    suspend fun updateItems(items: List<ShoppingListItemEntity>)

    @Query("UPDATE shopping_list_items SET isConfirmedForCurrentExport = 1 WHERE id = :id")
    suspend fun confirmItem(id: String)

    @Query("UPDATE shopping_list_items SET isConfirmedForCurrentExport = 0 WHERE id = :id")
    suspend fun unconfirmItem(id: String)

    @Query("DELETE FROM shopping_list_items WHERE id = :id")
    suspend fun deleteItemById(id: String)

    @Query("DELETE FROM shopping_list_items")
    suspend fun deleteAllItems()

    @Query("UPDATE shopping_list_items SET lastExportedName = name, lastExportedQuantity = quantity, isConfirmedForCurrentExport = 0")
    suspend fun commitExportBaseline()
}
