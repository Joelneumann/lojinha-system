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

    @Query("SELECT COUNT(*) FROM transactions WHERE referenceTransactionId = :refId AND type = 'CANCELLATION'")
    suspend fun getCancellationCountForReference(refId: String): Int

    @Query("""
        SELECT COUNT(*) FROM transactions, json_each(CASE WHEN items IS NULL OR items = '' THEN '[]' ELSE items END) 
        WHERE json_extract(value, '$.productId') = :productId
    """)
    suspend fun getTransactionCountForProduct(productId: String): Int


    @Query("SELECT balance FROM users WHERE id = :id")
    suspend fun getUserBalance(id: String): Long?

    @Query("UPDATE users SET balance = balance + :amountDelta WHERE id = :id")
    suspend fun updateUserBalance(id: String, amountDelta: Long)

    @Query("SELECT stockQuantity FROM products WHERE id = :id")
    suspend fun getProductStock(id: String): Long?

    @Query("UPDATE products SET stockQuantity = MAX(0, stockQuantity + :delta) WHERE id = :id")
    suspend fun updateProductStock(id: String, delta: Long)

    @androidx.room.Transaction
    suspend fun executeAtomicTransaction(
        transaction: TransactionEntity,
        balanceDelta: Long,
        stockDeltas: Map<String, Long>
    ) {
        val currentBal = getUserBalance(transaction.userId)
            ?: throw IllegalArgumentException("Cannot execute transaction: User '${transaction.userId}' does not exist.")

        if (transaction.type == "CANCELLATION" && transaction.referenceTransactionId != null) {
            val cancellations = getCancellationCountForReference(transaction.referenceTransactionId)
            check(cancellations == 0) { "Transaction '${transaction.referenceTransactionId}' has already been cancelled." }
        }

        for ((productId, delta) in stockDeltas) {
            if (delta < 0L) {
                val currentStock = getProductStock(productId) ?: 0L
                check(currentStock + delta >= 0L) {
                    "Insufficient stock for product '$productId'. Current: $currentStock, required: ${-delta}"
                }
            }
        }

        if (balanceDelta != 0L) {
            updateUserBalance(transaction.userId, balanceDelta)
        }
        for ((productId, delta) in stockDeltas) {
            if (delta != 0L) {
                updateProductStock(productId, delta)
            }
        }
        val authoritativeTx = transaction.copy(
            userBalanceBefore = currentBal,
            userBalanceAfter = currentBal + balanceDelta
        )
        insertTransaction(authoritativeTx)
    }

    @androidx.room.Transaction
    suspend fun executeBatchAtomicTransactions(
        items: List<Triple<TransactionEntity, Long, Map<String, Long>>>
    ) {
        for ((tx, balDelta, stockDeltas) in items) {
            val currentBal = getUserBalance(tx.userId)
                ?: throw IllegalArgumentException("Cannot execute batch transaction: User '${tx.userId}' does not exist.")

            if (tx.type == "CANCELLATION" && tx.referenceTransactionId != null) {
                val cancellations = getCancellationCountForReference(tx.referenceTransactionId)
                check(cancellations == 0) { "Transaction '${tx.referenceTransactionId}' has already been cancelled." }
            }

            for ((productId, delta) in stockDeltas) {
                if (delta < 0L) {
                    val currentStock = getProductStock(productId) ?: 0L
                    check(currentStock + delta >= 0L) {
                        "Insufficient stock for product '$productId'. Current: $currentStock, required: ${-delta}"
                    }
                }
            }

            if (balDelta != 0L) {
                updateUserBalance(tx.userId, balDelta)
            }
            for ((productId, delta) in stockDeltas) {
                if (delta != 0L) {
                    updateProductStock(productId, delta)
                }
            }
            val authoritativeTx = tx.copy(
                userBalanceBefore = currentBal,
                userBalanceAfter = currentBal + balDelta
            )
            insertTransaction(authoritativeTx)
        }
    }
}
