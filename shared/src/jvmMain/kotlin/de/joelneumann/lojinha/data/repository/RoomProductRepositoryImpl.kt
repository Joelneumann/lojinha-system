package de.joelneumann.lojinha.data.repository

import de.joelneumann.lojinha.data.dao.ProductDao
import de.joelneumann.lojinha.data.dao.ProductStockAdjustmentDao
import de.joelneumann.lojinha.data.dao.TransactionDao
import de.joelneumann.lojinha.data.entity.ProductEntity
import de.joelneumann.lojinha.data.entity.ProductStockAdjustmentEntity
import de.joelneumann.lojinha.domain.model.*
import de.joelneumann.lojinha.domain.repository.ProductRepository
import de.joelneumann.lojinha.ui.utils.currentTimeMillis
import de.joelneumann.lojinha.ui.utils.generateUuid
import de.joelneumann.lojinha.ui.utils.sortedByAccentInsensitive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomProductRepositoryImpl(
    private val productDao: ProductDao,
    private val transactionDao: TransactionDao? = null,
    private val productStockAdjustmentDao: ProductStockAdjustmentDao? = null,
    private val onDataChanged: (() -> Unit)? = null
) : ProductRepository {

    override fun getProductsFlow(): Flow<List<Product>> {
        return productDao.getProductsFlow().map { entities ->
            entities.map { it.toDomain() }.sortedByAccentInsensitive { it.name }
        }
    }

    override suspend fun getAllProducts(): List<Product> {
        return productDao.getAllProducts().map { it.toDomain() }.sortedByAccentInsensitive { it.name }
    }

    override suspend fun getProductById(id: String): Product? {
        return productDao.getProductById(id)?.toDomain()
    }

    override suspend fun getProductByBarcode(barcode: String): Product? {
        val clean = barcode.trim()
        val all = productDao.getAllProducts()
        return all.firstOrNull { entity ->
            entity.isActive && entity.barcodes.any { it.code.trim().equals(clean, ignoreCase = true) }
        }?.toDomain()
    }

    override suspend fun saveProduct(product: Product) {
        val trimmedName = product.name.trim()
        require(trimmedName.isNotBlank()) { "Product name cannot be blank" }
        require(product.basePrice >= 0L) { "Product base price cannot be negative" }
        val sanitizedProduct = product.copy(
            name = trimmedName,
            stockQuantity = product.stockQuantity.coerceAtLeast(0L)
        )

        val existing = productDao.getProductById(sanitizedProduct.id)
        if (existing != null) {
            val entity = ProductEntity.fromDomain(sanitizedProduct)
            productDao.updateProductMetadata(
                id = entity.id,
                name = entity.name,
                barcodes = entity.barcodes,
                basePrice = entity.basePrice,
                unitType = entity.unitType,
                customMarkupPercent = entity.customMarkupPercent,
                isActive = entity.isActive
            )
        } else {
            productDao.insertOrUpdateProduct(ProductEntity.fromDomain(sanitizedProduct))
            if (sanitizedProduct.stockQuantity >= 0L) {
                productStockAdjustmentDao?.insertAdjustment(
                    ProductStockAdjustmentEntity(
                        id = generateUuid(),
                        productId = sanitizedProduct.id,
                        timestamp = currentTimeMillis(),
                        deltaQuantity = sanitizedProduct.stockQuantity,
                        resultingQuantity = sanitizedProduct.stockQuantity,
                        note = "SYSNOTE|INITIAL_STOCK"
                    )
                )
            }
        }
        onDataChanged?.invoke()
    }

    override suspend fun deactivateProduct(id: String) {
        productDao.deactivateProduct(id)
        onDataChanged?.invoke()
    }

    override suspend fun hardDeleteProduct(id: String) {
        val txCount = transactionDao?.getTransactionCountForProduct(id) ?: 0
        if (txCount > 0) {
            productDao.deactivateProduct(id)
        } else {
            productStockAdjustmentDao?.deleteAdjustmentsByProductId(id)
            productDao.deleteProduct(id)
        }
        onDataChanged?.invoke()
    }

    override suspend fun updateStock(productId: String, delta: Long) {
        productDao.updateStock(productId, delta)
        if (delta != 0L) {
            val updated = productDao.getProductById(productId)
            productStockAdjustmentDao?.insertAdjustment(
                ProductStockAdjustmentEntity(
                    id = generateUuid(),
                    productId = productId,
                    timestamp = currentTimeMillis(),
                    deltaQuantity = delta,
                    resultingQuantity = updated?.stockQuantity,
                    note = null
                )
            )
        }
        onDataChanged?.invoke()
    }

    override suspend fun recordStockAdjustment(adjustment: ProductStockAdjustment) {
        productStockAdjustmentDao?.insertAdjustment(ProductStockAdjustmentEntity.fromDomain(adjustment))
        onDataChanged?.invoke()
    }

    override suspend fun getProductHistory(productId: String, page: Int, pageSize: Int): PagedResult<ProductHistoryEntry> {
        val product = productDao.getProductById(productId)?.toDomain()
        val unitType = product?.unitType ?: UnitType.PIECE

        val adjustments = productStockAdjustmentDao?.getAdjustmentsByProductId(productId) ?: emptyList()
        val adjustmentEntries = adjustments.map { adj ->
            ProductHistoryEntry(
                id = adj.id,
                productId = adj.productId,
                timestamp = adj.timestamp,
                type = ProductHistoryType.STOCK_ADJUSTMENT,
                userName = null,
                quantity = adj.deltaQuantity,
                unitType = unitType,
                note = adj.note,
                unitPrice = null,
                isCancelled = false
            )
        }

        val rawTransactions = transactionDao?.getTransactionsForProduct(productId)?.distinctBy { it.id } ?: emptyList()
        val directCancellations = rawTransactions
            .filter { it.type == TransactionType.CANCELLATION.name && it.referenceTransactionId != null }
            .mapNotNull { it.referenceTransactionId }
            .toSet()

        val candidatePurchases = rawTransactions.filter { it.type == TransactionType.PURCHASE.name }
        val purchaseIds = candidatePurchases.map { it.id }
        val additionalCancellations = if (purchaseIds.isNotEmpty() && transactionDao != null) {
            purchaseIds.chunked(500).flatMap { chunk ->
                transactionDao.getTransactionsByReferenceIds(chunk)
            }
                .filter { it.type == TransactionType.CANCELLATION.name && it.referenceTransactionId != null }
                .mapNotNull { it.referenceTransactionId }
                .toSet()
        } else {
            emptySet()
        }
        val allCancelledTxIds = directCancellations + additionalCancellations

        val purchaseEntries = candidatePurchases.mapNotNull { tx ->
            val matchingItems = tx.items.filter { it.productId == productId }
            if (matchingItems.isEmpty()) return@mapNotNull null
            val totalQuantity = matchingItems.sumOf { it.quantity }
            val firstItem = matchingItems.first()
            ProductHistoryEntry(
                id = "${tx.id}_$productId",
                productId = productId,
                timestamp = tx.timestamp,
                type = ProductHistoryType.PURCHASE,
                userName = tx.userNameSnapshot,
                quantity = totalQuantity,
                unitType = firstItem.unitType,
                note = tx.note,
                unitPrice = firstItem.unitPriceAtPurchase,
                isCancelled = allCancelledTxIds.contains(tx.id)
            )
        }

        val correctionEntries = rawTransactions.filter { it.type == TransactionType.CORRECTION.name }.mapNotNull { tx ->
            val matchingItems = tx.items.filter { it.productId == productId }
            if (matchingItems.isEmpty()) return@mapNotNull null
            var totalDelta = 0L
            for (item in matchingItems) {
                val prev = item.previousQuantity ?: continue
                val diff = item.quantity - prev
                totalDelta += -diff
            }
            if (totalDelta == 0L) return@mapNotNull null
            val firstItem = matchingItems.first()
            ProductHistoryEntry(
                id = "${tx.id}_$productId",
                productId = productId,
                timestamp = tx.timestamp,
                type = ProductHistoryType.STOCK_ADJUSTMENT,
                userName = tx.userNameSnapshot,
                quantity = totalDelta,
                unitType = firstItem.unitType,
                note = tx.note ?: "Correction",
                unitPrice = firstItem.unitPriceAtPurchase,
                isCancelled = false
            )
        }

        val cancellationEntries = rawTransactions
            .filter { it.type == TransactionType.CANCELLATION.name && it.referenceTransactionId != null }
            .mapNotNull { tx ->
                val matchingItems = tx.items.filter { it.productId == productId }
                if (matchingItems.isEmpty()) return@mapNotNull null
                val restoredQty = matchingItems.sumOf { item ->
                    if (item.previousQuantity != null && item.previousQuantity > 0L) {
                        item.previousQuantity - item.quantity
                    } else {
                        item.quantity
                    }
                }
                if (restoredQty <= 0L) return@mapNotNull null
                val firstItem = matchingItems.first()
                ProductHistoryEntry(
                    id = "${tx.id}_$productId",
                    productId = productId,
                    timestamp = tx.timestamp,
                    type = ProductHistoryType.CANCELLATION,
                    userName = tx.userNameSnapshot,
                    quantity = restoredQty,
                    unitType = firstItem.unitType,
                    note = tx.note ?: "Cancellation",
                    unitPrice = firstItem.unitPriceAtPurchase,
                    isCancelled = false
                )
            }

        val rawEntries = (adjustmentEntries + purchaseEntries + correctionEntries + cancellationEntries)
            .distinctBy { it.id }

        val initialStockEntry = rawEntries.firstOrNull { it.note == "SYSNOTE|INITIAL_STOCK" }
        val earliestTxTimestamp = rawEntries.filter { it.note != "SYSNOTE|INITIAL_STOCK" }.minOfOrNull { it.timestamp }
        val effectiveEntries = if (initialStockEntry != null && earliestTxTimestamp != null && initialStockEntry.timestamp >= earliestTxTimestamp) {
            val adjustedInitial = initialStockEntry.copy(timestamp = earliestTxTimestamp - 1000L)
            rawEntries.map { if (it.id == initialStockEntry.id) adjustedInitial else it }
        } else {
            rawEntries
        }

        val sortedAscending = effectiveEntries.sortedWith(
            compareBy<ProductHistoryEntry> { it.timestamp }
                .thenBy {
                    when {
                        it.note == "SYSNOTE|INITIAL_STOCK" -> 0
                        it.type == ProductHistoryType.STOCK_ADJUSTMENT -> 1
                        it.type == ProductHistoryType.PURCHASE -> 2
                        it.type == ProductHistoryType.CANCELLATION -> 3
                        else -> 4
                    }
                }
        )

        val explicitCancelledRefIds = cancellationEntries.mapNotNull { entry ->
            val txId = entry.id.substringBefore("_$productId")
            rawTransactions.firstOrNull { it.id == txId }?.referenceTransactionId
        }.toSet()

        val hasInitialStock = sortedAscending.any { it.note == "SYSNOTE|INITIAL_STOCK" }

        val withStockAfter = if (hasInitialStock) {
            var runningStock = 0L
            var foundInitial = false
            sortedAscending.map { entry ->
                if (!foundInitial && entry.note == "SYSNOTE|INITIAL_STOCK") {
                    foundInitial = true
                    runningStock = entry.quantity
                    entry.copy(stockAfter = runningStock)
                } else {
                    when (entry.type) {
                        ProductHistoryType.STOCK_ADJUSTMENT -> {
                            runningStock += entry.quantity
                        }
                        ProductHistoryType.PURCHASE -> {
                            val rawTxId = entry.id.substringBefore("_$productId")
                            val hasSeparateCancellation = explicitCancelledRefIds.contains(rawTxId)
                            if (!entry.isCancelled || hasSeparateCancellation) {
                                runningStock -= entry.quantity
                            }
                        }
                        ProductHistoryType.CANCELLATION -> {
                            runningStock += entry.quantity
                        }
                    }
                    entry.copy(stockAfter = runningStock)
                }
            }
        } else {
            var runningStock = product?.stockQuantity ?: 0L
            val result = ArrayList<ProductHistoryEntry>(sortedAscending.size)
            for (i in sortedAscending.indices.reversed()) {
                val entry = sortedAscending[i]
                val entryWithStock = entry.copy(stockAfter = runningStock)
                when (entry.type) {
                    ProductHistoryType.STOCK_ADJUSTMENT -> {
                        runningStock -= entry.quantity
                    }
                    ProductHistoryType.PURCHASE -> {
                        val rawTxId = entry.id.substringBefore("_$productId")
                        val hasSeparateCancellation = explicitCancelledRefIds.contains(rawTxId)
                        if (!entry.isCancelled || hasSeparateCancellation) {
                            runningStock += entry.quantity
                        }
                    }
                    ProductHistoryType.CANCELLATION -> {
                        runningStock -= entry.quantity
                    }
                }
                result.add(entryWithStock)
            }
            result.reversed()
        }

        val allEntries = withStockAfter.sortedByDescending { it.timestamp }
        val totalCount = allEntries.size
        val safePageSize = pageSize.coerceAtLeast(1)
        val totalPages = if (totalCount == 0) 1 else (totalCount + safePageSize - 1) / safePageSize
        val safePage = page.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
        val fromIndex = (safePage * safePageSize).coerceAtMost(totalCount)
        val toIndex = (fromIndex + safePageSize).coerceAtMost(totalCount)
        val pageItems = allEntries.subList(fromIndex, toIndex)

        return PagedResult(
            items = pageItems,
            totalCount = totalCount,
            page = safePage,
            pageSize = safePageSize,
            totalPages = totalPages
        )
    }
}
