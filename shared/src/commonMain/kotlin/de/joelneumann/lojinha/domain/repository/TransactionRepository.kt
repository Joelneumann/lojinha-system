package de.joelneumann.lojinha.domain.repository

import de.joelneumann.lojinha.domain.model.Transaction
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    fun getTransactionsFlow(): Flow<List<Transaction>>
    suspend fun getAllTransactions(): List<Transaction>
    suspend fun getTransactionsByUserId(userId: String): List<Transaction>
    suspend fun getTransactionById(id: String): Transaction?
    suspend fun recordTransaction(transaction: Transaction)
    suspend fun getTransactionCountForUser(userId: String): Int
}
