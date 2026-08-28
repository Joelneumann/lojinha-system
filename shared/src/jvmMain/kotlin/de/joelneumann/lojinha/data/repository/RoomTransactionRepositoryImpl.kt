package de.joelneumann.lojinha.data.repository

import de.joelneumann.lojinha.data.dao.TransactionDao
import de.joelneumann.lojinha.data.entity.TransactionEntity
import de.joelneumann.lojinha.domain.model.PagedResult
import de.joelneumann.lojinha.domain.model.Transaction
import de.joelneumann.lojinha.domain.model.TransactionType
import de.joelneumann.lojinha.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomTransactionRepositoryImpl(
    private val transactionDao: TransactionDao,
    private val onDataChanged: (() -> Unit)? = null
) : TransactionRepository {

    override fun getTransactionsFlow(): Flow<List<Transaction>> {
        return transactionDao.getTransactionsFlow().map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun getAllTransactions(): List<Transaction> {
        return transactionDao.getAllTransactions().map { it.toDomain() }
    }

    override suspend fun getTransactionsByUserId(userId: String): List<Transaction> {
        return transactionDao.getTransactionsByUserId(userId).map { it.toDomain() }
    }

    override suspend fun getTransactionById(id: String): Transaction? {
        return transactionDao.getTransactionById(id)?.toDomain()
    }

    override suspend fun recordTransaction(transaction: Transaction) {
        transactionDao.insertTransaction(TransactionEntity.fromDomain(transaction))
        onDataChanged?.invoke()
    }

    override suspend fun getTransactionCountForUser(userId: String): Int {
        return transactionDao.getTransactionCountForUser(userId)
    }

    override suspend fun getTransactionsPaged(
        page: Int,
        pageSize: Int,
        searchQuery: String?,
        typeFilter: TransactionType?
    ): PagedResult<Transaction> {
        val safePageSize = pageSize.coerceAtLeast(1)
        val safePage = page.coerceAtLeast(0)
        val offset = safePage * safePageSize
        val search = searchQuery?.takeIf { it.isNotBlank() }
        val type = typeFilter?.name

        val totalCount = transactionDao.getTransactionsCount(search, type)
        val entities = transactionDao.getTransactionsPaged(safePageSize, offset, search, type)
        val totalPages = if (totalCount == 0) 1 else kotlin.math.ceil(totalCount.toDouble() / safePageSize).toInt()

        return PagedResult(
            items = entities.map { it.toDomain() },
            totalCount = totalCount,
            page = safePage,
            pageSize = safePageSize,
            totalPages = totalPages
        )
    }

    override suspend fun getTransactionsByUserIdPaged(
        userId: String,
        page: Int,
        pageSize: Int,
        searchQuery: String?,
        typeFilter: TransactionType?
    ): PagedResult<Transaction> {
        val safePageSize = pageSize.coerceAtLeast(1)
        val safePage = page.coerceAtLeast(0)
        val offset = safePage * safePageSize
        val search = searchQuery?.takeIf { it.isNotBlank() }
        val type = typeFilter?.name

        val totalCount = transactionDao.getTransactionsByUserIdCount(userId, search, type)
        val entities = transactionDao.getTransactionsByUserIdPaged(userId, safePageSize, offset, search, type)
        val totalPages = if (totalCount == 0) 1 else kotlin.math.ceil(totalCount.toDouble() / safePageSize).toInt()

        return PagedResult(
            items = entities.map { it.toDomain() },
            totalCount = totalCount,
            page = safePage,
            pageSize = safePageSize,
            totalPages = totalPages
        )
    }
}
