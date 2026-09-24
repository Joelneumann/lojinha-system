package de.joelneumann.lojinha.data.repository

import de.joelneumann.lojinha.data.dao.TransactionDao
import de.joelneumann.lojinha.data.entity.TransactionEntity
import de.joelneumann.lojinha.domain.model.PagedResult
import de.joelneumann.lojinha.domain.model.Transaction
import de.joelneumann.lojinha.domain.model.TransactionType
import de.joelneumann.lojinha.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RoomTransactionRepositoryImpl(
    private val transactionDao: TransactionDao,
    private val onDataChanged: (() -> Unit)? = null
) : TransactionRepository {

    private val mutationMutex = Mutex()

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

    override suspend fun getTransactionsByReferenceIds(referenceIds: List<String>): List<Transaction> {
        if (referenceIds.isEmpty()) return emptyList()
        return transactionDao.getTransactionsByReferenceIds(referenceIds).map { it.toDomain() }
    }

    override suspend fun getTransactionsByIds(ids: List<String>): List<Transaction> {
        if (ids.isEmpty()) return emptyList()
        return transactionDao.getTransactionsByIds(ids).map { it.toDomain() }
    }

    override suspend fun getCancellationCountForReference(refId: String): Int {
        return transactionDao.getCancellationCountForReference(refId)
    }

    override suspend fun executeAtomicTransaction(
        transaction: Transaction,
        balanceDelta: Long,
        stockDeltas: Map<String, Long>
    ) {
        transactionDao.executeAtomicTransaction(
            TransactionEntity.fromDomain(transaction),
            balanceDelta,
            stockDeltas
        )
        onDataChanged?.invoke()
    }

    override suspend fun applyPurchaseCorrection(
        originalTransactionId: String,
        newItems: List<de.joelneumann.lojinha.domain.model.TransactionItem>
    ): Boolean = mutationMutex.withLock {
        if (newItems.any { it.quantity < 0L }) return@withLock false

        val originalTx = getTransactionById(originalTransactionId) ?: return@withLock false
        if (originalTx.type != TransactionType.PURCHASE) return@withLock false
        if (getCancellationCountForReference(originalTx.id) > 0) return@withLock false

        val children = getTransactionsByReferenceIds(listOf(originalTx.id))
        val correctionChildren = children.filter { it.type == TransactionType.CORRECTION }.sortedBy { it.timestamp }
        val currentItems = Transaction.computeEffectiveItems(originalTx.items, correctionChildren)

        // Merge incoming newItems with currentItems so partial payloads are safely handled,
        // strictly locking unit prices and product names from currentItems to prevent tampering
        val newItemsMap = newItems.associateBy { it.productId }
        val effectiveNewItems = currentItems.map { current ->
            val incoming = newItemsMap[current.productId]
            if (incoming != null) {
                current.copy(quantity = incoming.quantity)
            } else {
                current
            }
        }

        val isAllZero = effectiveNewItems.all { it.quantity == 0L }

        val currentCost = currentItems.sumOf { it.totalLinePrice }
        val newCost = effectiveNewItems.sumOf { it.totalLinePrice }
        val costDifference = newCost - currentCost
        val balanceDelta = -costDifference

        val updatedItems = effectiveNewItems.filter { newItem ->
            val currentItem = currentItems.firstOrNull { it.productId == newItem.productId }
            val currentQty = currentItem?.quantity ?: 0L
            newItem.quantity != currentQty
        }.map { newItem ->
            val currentItem = currentItems.firstOrNull { it.productId == newItem.productId }
            newItem.copy(previousQuantity = currentItem?.quantity ?: 0L)
        }

        if (updatedItems.isEmpty()) {
            return@withLock false
        }

        val stockDeltas = mutableMapOf<String, Long>()
        effectiveNewItems.forEach { newItem ->
            val currentItem = currentItems.firstOrNull { it.productId == newItem.productId }
            val currentQty = currentItem?.quantity ?: 0L
            val qtyChange = newItem.quantity - currentQty
            if (qtyChange != 0L) {
                stockDeltas[newItem.productId] = -qtyChange
            }
        }

        val balBefore = transactionDao.getUserBalance(originalTx.userId) ?: 0L
        val balAfter = balBefore + balanceDelta

        val nowMillis = de.joelneumann.lojinha.ui.utils.currentTimeMillis()
        val humanNote = if (isAllZero) {
            "SYSNOTE|COMPLETE_STORNO|${originalTx.timestamp}|${originalTx.totalAmount}"
        } else {
            "SYSNOTE|PARTIAL_STORNO|${originalTx.timestamp}|${originalTx.totalAmount}"
        }

        val correctionTx = Transaction(
            id = de.joelneumann.lojinha.ui.utils.generateUuid(),
            userId = originalTx.userId,
            userNameSnapshot = originalTx.userNameSnapshot,
            timestamp = nowMillis,
            type = if (isAllZero) TransactionType.CANCELLATION else TransactionType.CORRECTION,
            referenceTransactionId = originalTx.id,
            note = humanNote,
            totalAmount = balanceDelta,
            items = updatedItems,
            userBalanceBefore = balBefore,
            userBalanceAfter = balAfter
        )

        executeAtomicTransaction(correctionTx, balanceDelta, stockDeltas)
        true
    }

    override suspend fun stornoNonPurchase(transactionId: String): Boolean = mutationMutex.withLock {
        val tx = getTransactionById(transactionId) ?: return@withLock false
        if (tx.type != TransactionType.ADMIN_DEPOSIT && tx.type != TransactionType.ADMIN_WITHDRAWAL) return@withLock false
        if (getCancellationCountForReference(tx.id) > 0) return@withLock false

        val refundAmount = -tx.totalAmount
        val balBefore = transactionDao.getUserBalance(tx.userId) ?: 0L
        val balAfter = balBefore + refundAmount

        val nowMillis = de.joelneumann.lojinha.ui.utils.currentTimeMillis()

        val stornoTx = Transaction(
            id = de.joelneumann.lojinha.ui.utils.generateUuid(),
            userId = tx.userId,
            userNameSnapshot = tx.userNameSnapshot,
            timestamp = nowMillis,
            type = TransactionType.CANCELLATION,
            referenceTransactionId = tx.id,
            note = "SYSNOTE|NON_PURCHASE_STORNO|${tx.type.name}|${tx.items.isNotEmpty()}|${tx.timestamp}|${tx.totalAmount}",
            totalAmount = refundAmount,
            items = emptyList(),
            userBalanceBefore = balBefore,
            userBalanceAfter = balAfter
        )

        executeAtomicTransaction(stornoTx, refundAmount, emptyMap())
        true
    }

    override suspend fun executeBatchTransactions(
        requests: List<de.joelneumann.lojinha.domain.model.AtomicTransactionRequest>
    ): Boolean {
        if (requests.isEmpty()) return true
        return try {
            val items = requests.map { req ->
                Triple(
                    TransactionEntity.fromDomain(req.transaction),
                    req.balanceDelta,
                    req.stockDeltas
                )
            }
            transactionDao.executeBatchAtomicTransactions(items)
            onDataChanged?.invoke()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
