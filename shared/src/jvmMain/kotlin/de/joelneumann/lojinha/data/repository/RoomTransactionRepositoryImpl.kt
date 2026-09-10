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
    ): Boolean {
        val originalTx = getTransactionById(originalTransactionId) ?: return false
        if (originalTx.type != TransactionType.PURCHASE) return false
        if (getCancellationCountForReference(originalTx.id) > 0) return false

        val children = getTransactionsByReferenceIds(listOf(originalTx.id))
        val correctionChildren = children.filter { it.type == TransactionType.CORRECTION }.sortedBy { it.timestamp }
        val currentItems = de.joelneumann.lojinha.ui.viewmodel.admin.AdminTransactionsViewModel.computeEffectiveItems(originalTx.items, correctionChildren)

        // Merge incoming newItems with currentItems so partial payloads are safely handled
        val newItemsMap = newItems.associateBy { it.productId }
        val effectiveNewItems = currentItems.map { current ->
            newItemsMap[current.productId] ?: current
        }

        val isAllZero = effectiveNewItems.all { it.quantity == 0L }

        val currentCost = currentItems.sumOf { item ->
            when (item.unitType) {
                de.joelneumann.lojinha.domain.model.UnitType.PIECE -> item.unitPriceAtPurchase * item.quantity
                de.joelneumann.lojinha.domain.model.UnitType.WEIGHT -> kotlin.math.round((item.unitPriceAtPurchase * item.quantity) / 1000.0).toLong()
            }
        }
        val newCost = effectiveNewItems.sumOf { item ->
            when (item.unitType) {
                de.joelneumann.lojinha.domain.model.UnitType.PIECE -> item.unitPriceAtPurchase * item.quantity
                de.joelneumann.lojinha.domain.model.UnitType.WEIGHT -> kotlin.math.round((item.unitPriceAtPurchase * item.quantity) / 1000.0).toLong()
            }
        }
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
            return false
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
        val dateStr = de.joelneumann.lojinha.ui.utils.Formatting.formatTimestamp(originalTx.timestamp, de.joelneumann.lojinha.domain.model.Language.EN)
        val origAmountStr = de.joelneumann.lojinha.ui.utils.Formatting.formatBrl(kotlin.math.abs(originalTx.totalAmount))
        val humanNote = if (isAllZero) {
            "Complete Storno of Purchase ($dateStr - $origAmountStr)"
        } else {
            "Item quantity correction for Purchase ($dateStr - Original $origAmountStr)"
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
        return true
    }

    override suspend fun stornoNonPurchase(transactionId: String): Boolean {
        val tx = getTransactionById(transactionId) ?: return false
        if (tx.type != TransactionType.ADMIN_DEPOSIT && tx.type != TransactionType.ADMIN_WITHDRAWAL) return false
        if (getCancellationCountForReference(tx.id) > 0) return false

        val refundAmount = -tx.totalAmount
        val balBefore = transactionDao.getUserBalance(tx.userId) ?: 0L
        val balAfter = balBefore + refundAmount

        val nowMillis = de.joelneumann.lojinha.ui.utils.currentTimeMillis()
        val dateStr = de.joelneumann.lojinha.ui.utils.Formatting.formatTimestamp(tx.timestamp, de.joelneumann.lojinha.domain.model.Language.EN)
        val amountStr = de.joelneumann.lojinha.ui.utils.Formatting.formatBrl(kotlin.math.abs(tx.totalAmount))
        val typeLabel = when (tx.type) {
            TransactionType.ADMIN_DEPOSIT -> "Deposit"
            TransactionType.ADMIN_WITHDRAWAL -> if (tx.items.isNotEmpty()) "Custom Expense" else "Debit"
            else -> tx.type.name
        }

        val stornoTx = Transaction(
            id = de.joelneumann.lojinha.ui.utils.generateUuid(),
            userId = tx.userId,
            userNameSnapshot = tx.userNameSnapshot,
            timestamp = nowMillis,
            type = TransactionType.CANCELLATION,
            referenceTransactionId = tx.id,
            note = "Storno of $typeLabel ($dateStr - $amountStr)",
            totalAmount = refundAmount,
            items = emptyList(),
            userBalanceBefore = balBefore,
            userBalanceAfter = balAfter
        )

        executeAtomicTransaction(stornoTx, refundAmount, emptyMap())
        return true
    }
}
