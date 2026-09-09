package de.joelneumann.lojinha.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import de.joelneumann.lojinha.data.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getTransactionsFlow(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY timestamp DESC")
    suspend fun getTransactionsByUserId(userId: String): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: String): TransactionEntity?

    @Query("SELECT COUNT(*) FROM transactions WHERE userId = :userId")
    suspend fun getTransactionCountForUser(userId: String): Int

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    suspend fun getAllTransactions(): List<TransactionEntity>

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Query("""
        SELECT * FROM transactions 
        WHERE (:type IS NULL OR :type = '' OR type = :type)
          AND (:search IS NULL OR :search = '' OR userNameSnapshot LIKE '%' || :search || '%' OR note LIKE '%' || :search || '%' OR items LIKE '%' || :search || '%')
        ORDER BY timestamp DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getTransactionsPaged(limit: Int, offset: Int, search: String?, type: String?): List<TransactionEntity>

    @Query("""
        SELECT COUNT(*) FROM transactions 
        WHERE (:type IS NULL OR :type = '' OR type = :type)
          AND (:search IS NULL OR :search = '' OR userNameSnapshot LIKE '%' || :search || '%' OR note LIKE '%' || :search || '%' OR items LIKE '%' || :search || '%')
    """)
    suspend fun getTransactionsCount(search: String?, type: String?): Int

    @Query("""
        SELECT * FROM transactions 
        WHERE userId = :userId
          AND (:type IS NULL OR :type = '' OR type = :type)
          AND (:search IS NULL OR :search = '' OR userNameSnapshot LIKE '%' || :search || '%' OR note LIKE '%' || :search || '%' OR items LIKE '%' || :search || '%')
        ORDER BY timestamp DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getTransactionsByUserIdPaged(userId: String, limit: Int, offset: Int, search: String?, type: String?): List<TransactionEntity>

    @Query("""
        SELECT COUNT(*) FROM transactions 
        WHERE userId = :userId
          AND (:type IS NULL OR :type = '' OR type = :type)
          AND (:search IS NULL OR :search = '' OR userNameSnapshot LIKE '%' || :search || '%' OR note LIKE '%' || :search || '%' OR items LIKE '%' || :search || '%')
    """)
    suspend fun getTransactionsByUserIdCount(userId: String, search: String?, type: String?): Int

    @Query("SELECT * FROM transactions WHERE referenceTransactionId IN (:referenceIds)")
    suspend fun getTransactionsByReferenceIds(referenceIds: List<String>): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id IN (:ids)")
    suspend fun getTransactionsByIds(ids: List<String>): List<TransactionEntity>
}
