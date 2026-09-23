package de.joelneumann.lojinha.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import de.joelneumann.lojinha.data.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY name ASC")
    fun getUsersFlow(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users ORDER BY name ASC")
    suspend fun getAllUsers(): List<UserEntity>

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getUserById(id: String): UserEntity?

    @Query("""
        SELECT * FROM users 
        WHERE (userBarcode = :barcode COLLATE NOCASE OR userBarcodeNumber = :barcode COLLATE NOCASE)
          AND isDeleted = 0 
        ORDER BY isActive DESC 
        LIMIT 1
    """)
    suspend fun getUserByBarcode(barcode: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateUser(user: UserEntity)

    @Query("""
        UPDATE users 
        SET name = :name, 
            language = :language, 
            secondaryCurrency = :secondaryCurrency, 
            pin = :pin, 
            userBarcode = :userBarcode, 
            userBarcodeNumber = :userBarcodeNumber, 
            isActive = :isActive, 
            isDeleted = :isDeleted, 
            avatarType = :avatarType, 
            avatarEmoji = :avatarEmoji, 
            avatarColor = :avatarColor 
        WHERE id = :id
    """)
    suspend fun updateUserProfile(
        id: String,
        name: String,
        language: String,
        secondaryCurrency: String,
        pin: String?,
        userBarcode: String?,
        userBarcodeNumber: String?,
        isActive: Boolean,
        isDeleted: Boolean,
        avatarType: String,
        avatarEmoji: String,
        avatarColor: String
    )

    @Query("DELETE FROM billing_list_users WHERE userId = :id")
    suspend fun removeUserFromAllBillingLists(id: String)

    @Query("UPDATE users SET isDeleted = 1, isActive = 0 WHERE id = :id")
    suspend fun updateSoftDelete(id: String)

    @Transaction
    suspend fun softDeleteUser(id: String) {
        updateSoftDelete(id)
        removeUserFromAllBillingLists(id)
    }

    @Query("UPDATE users SET isDeleted = 0, isActive = 1 WHERE id = :id")
    suspend fun restoreUser(id: String)

    @Query("UPDATE users SET userBarcode = NULL, userBarcodeNumber = NULL WHERE id = :id")
    suspend fun clearUserBarcode(id: String)

    @Query("UPDATE users SET isActive = 0 WHERE id = :id")
    suspend fun deactivateUser(id: String)

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun deleteUserRow(id: String)

    @Transaction
    suspend fun deleteUser(id: String) {
        removeUserFromAllBillingLists(id)
        deleteUserRow(id)
    }

    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()

    @Query("UPDATE users SET balance = balance + :amountDelta WHERE id = :id")
    suspend fun updateBalance(id: String, amountDelta: Long)
}
