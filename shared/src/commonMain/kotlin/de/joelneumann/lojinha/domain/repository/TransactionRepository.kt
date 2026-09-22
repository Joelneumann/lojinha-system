package de.joelneumann.lojinha.domain.repository

import de.joelneumann.lojinha.domain.model.PagedResult
import de.joelneumann.lojinha.domain.model.Transaction
import de.joelneumann.lojinha.domain.model.TransactionItem
import de.joelneumann.lojinha.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    fun getTransactionsFlow(): Flow<List<Transaction>>
    suspend fun getAllTransactions(): List<Transaction>
    suspend fun getTransactionsByUserId(userId: String): List<Transaction>
    suspend fun getTransactionById(id: String): Transaction?
    suspend fun recordTransaction(transaction: Transaction)
    suspend fun getTransactionCountForUser(userId: String): Int

    suspend fun getTransactionsPaged(
        page: Int,
        pageSize: Int,
        searchQuery: String? = null,
        typeFilter: TransactionType? = null
    ): PagedResult<Transaction>

    suspend fun getTransactionsByUserIdPaged(
        userId: String,
        page: Int,
        pageSize: Int,
        searchQuery: String? = null,
        typeFilter: TransactionType? = null
    ): PagedResult<Transaction>

    suspend fun getTransactionsByReferenceIds(referenceIds: List<String>): List<Transaction>
    suspend fun getTransactionsByIds(ids: List<String>): List<Transaction>

    suspend fun getCancellationCountForReference(refId: String): Int
    suspend fun executeAtomicTransaction(
        transaction: Transaction,
        balanceDelta: Long,
        stockDeltas: Map<String, Long> = emptyMap()
    )
    suspend fun applyPurchaseCorrection(
        originalTransactionId: String,
        newItems: List<TransactionItem>
    ): Boolean
    suspend fun stornoNonPurchase(
        transactionId: String
    ): Boolean

    suspend fun executeBatchTransactions(
        requests: List<de.joelneumann.lojinha.domain.model.AtomicTransactionRequest>
    ): Boolean
}


