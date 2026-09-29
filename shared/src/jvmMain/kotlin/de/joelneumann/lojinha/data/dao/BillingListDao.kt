package de.joelneumann.lojinha.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import de.joelneumann.lojinha.data.entity.BillingListEntity
import de.joelneumann.lojinha.data.entity.BillingListUserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BillingListDao {
    @Query("SELECT * FROM billing_lists WHERE isDeleted = 0 ORDER BY name ASC")
    fun getActiveBillingListsFlow(): Flow<List<BillingListEntity>>

    @Query("SELECT * FROM billing_list_users")
    fun getAllBillingListUsersFlow(): Flow<List<BillingListUserEntity>>

    @Query("SELECT * FROM billing_list_users WHERE listId = :listId")
    fun getUsersForListFlow(listId: String): Flow<List<BillingListUserEntity>>

    @Upsert
    suspend fun insertOrUpdateBillingList(list: BillingListEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBillingListUsers(users: List<BillingListUserEntity>)

    @androidx.room.Transaction
    suspend fun saveBillingListWithUsers(list: BillingListEntity, users: List<BillingListUserEntity>) {
        insertOrUpdateBillingList(list)
        removeAllUsersFromList(list.id)
        if (users.isNotEmpty()) {
            insertBillingListUsers(users)
        }
    }

    @Query("UPDATE billing_lists SET lastExecutionTime = :lastExecutionTime WHERE id = :id")
    suspend fun updateLastExecutionTime(id: String, lastExecutionTime: Long?)

    @Query("UPDATE billing_lists SET isDeleted = 1 WHERE id = :id")
    suspend fun softDeleteBillingList(id: String)

    @Upsert
    suspend fun insertBillingListUser(user: BillingListUserEntity)

    @Query("DELETE FROM billing_list_users WHERE listId = :listId AND userId = :userId")
    suspend fun removeUserFromList(listId: String, userId: String)

    @Query("DELETE FROM billing_list_users WHERE userId = :userId")
    suspend fun removeUserFromAllLists(userId: String)
    
    @Query("UPDATE billing_list_users SET quantity = :quantity WHERE listId = :listId AND userId = :userId")
    suspend fun updateUserQuantity(listId: String, userId: String, quantity: Int)

    @Query("DELETE FROM billing_list_users WHERE listId = :listId")
    suspend fun removeAllUsersFromList(listId: String)

    @Query("SELECT * FROM billing_lists")
    suspend fun getAllBillingLists(): List<BillingListEntity>

    @Query("SELECT * FROM billing_list_users")
    suspend fun getAllBillingListUsers(): List<BillingListUserEntity>

    @Query("DELETE FROM billing_lists")
    suspend fun deleteAllBillingLists()

    @Query("DELETE FROM billing_list_users")
    suspend fun deleteAllBillingListUsers()
}
