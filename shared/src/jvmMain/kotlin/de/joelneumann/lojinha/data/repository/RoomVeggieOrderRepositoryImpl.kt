package de.joelneumann.lojinha.data.repository

import de.joelneumann.lojinha.data.dao.TransactionDao
import de.joelneumann.lojinha.data.dao.UserDao
import de.joelneumann.lojinha.data.dao.VeggieOrderDao
import de.joelneumann.lojinha.data.entity.*
import de.joelneumann.lojinha.domain.model.*
import de.joelneumann.lojinha.domain.repository.CustomerVeggieRoundHistoryItem
import de.joelneumann.lojinha.domain.repository.VeggieOrderRepository
import de.joelneumann.lojinha.domain.service.VeggieRoundSettlementResult
import de.joelneumann.lojinha.domain.service.VeggieSettlementCalculator
import de.joelneumann.lojinha.ui.utils.IsoWeekUtils
import de.joelneumann.lojinha.ui.utils.currentTimeMillis
import de.joelneumann.lojinha.ui.utils.generateUuid
import de.joelneumann.lojinha.ui.utils.sortedByAccentInsensitive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RoomVeggieOrderRepositoryImpl(
    private val veggieOrderDao: VeggieOrderDao,
    private val userDao: UserDao,
    private val transactionDao: TransactionDao,
    private val onDataChanged: (() -> Unit)? = null
) : VeggieOrderRepository {

    private val mutationMutex = Mutex()

    // -------------------------------------------------------------------------
    // 1. Unit Types
    // -------------------------------------------------------------------------
    override fun getUnitTypesFlow(): Flow<List<VeggieUnitType>> {
        return veggieOrderDao.getUnitTypesFlow().map { entities ->
            entities.map { it.toDomain() }.sortedByAccentInsensitive { it.name }
        }
    }

    override suspend fun getAllUnitTypes(): List<VeggieUnitType> {
        return veggieOrderDao.getAllUnitTypes().map { it.toDomain() }.sortedByAccentInsensitive { it.name }
    }

    override suspend fun saveUnitType(unitType: VeggieUnitType) {
        val trimmedName = unitType.name.trim()
        require(trimmedName.isNotBlank()) { "Unit type name cannot be blank" }
        val now = if (unitType.updatedAt > 0L) unitType.updatedAt else currentTimeMillis()
        val sanitized = unitType.copy(name = trimmedName, updatedAt = now)
        veggieOrderDao.insertOrUpdateUnitType(VeggieUnitTypeEntity.fromDomain(sanitized))
        onDataChanged?.invoke()
    }

    // -------------------------------------------------------------------------
    // 2. Veggie Catalog Products
    // -------------------------------------------------------------------------
    override fun getVeggieProductsFlow(): Flow<List<VeggieProduct>> {
        return veggieOrderDao.getVeggieProductsFlow().map { entities ->
            entities.map { it.toDomain() }.sortedByAccentInsensitive { it.name }
        }
    }

    override suspend fun getAllVeggieProducts(): List<VeggieProduct> {
        return veggieOrderDao.getAllVeggieProducts(includeDeleted = false)
            .map { it.toDomain() }
            .sortedByAccentInsensitive { it.name }
    }

    override suspend fun getVeggieProductById(id: String): VeggieProduct? {
        return veggieOrderDao.getVeggieProductById(id)?.toDomain()
    }

    override suspend fun saveVeggieProduct(product: VeggieProduct) {
        val trimmedName = product.name.trim()
        require(trimmedName.isNotBlank()) { "Veggie product name cannot be blank" }
        val now = if (product.updatedAt > 0L) product.updatedAt else currentTimeMillis()
        val sanitized = product.copy(
            name = trimmedName,
            description = product.description?.trim()?.takeIf { it.isNotEmpty() },
            updatedAt = now
        )
        veggieOrderDao.insertOrUpdateVeggieProduct(VeggieProductEntity.fromDomain(sanitized))
        onDataChanged?.invoke()
    }

    override suspend fun deactivateVeggieProduct(id: String) {
        veggieOrderDao.deactivateVeggieProduct(id, currentTimeMillis())
        onDataChanged?.invoke()
    }

    override suspend fun hardDeleteVeggieProduct(id: String) {
        val entryCount = veggieOrderDao.getEntryCountForProduct(id)
        if (entryCount == 0) {
            veggieOrderDao.hardDeleteVeggieProduct(id)
        } else {
            veggieOrderDao.softDeleteVeggieProduct(id, currentTimeMillis())
        }
        onDataChanged?.invoke()
    }

    // -------------------------------------------------------------------------
    // 3. Order Rounds
    // -------------------------------------------------------------------------
    override fun getRoundsFlow(): Flow<List<VeggieOrderRound>> {
        return veggieOrderDao.getAllRoundsFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getAllRounds(): List<VeggieOrderRound> {
        return veggieOrderDao.getAllRounds().map { it.toDomain() }
    }

    override suspend fun getActiveOrLatestRound(): VeggieOrderRound? {
        val active = veggieOrderDao.getActiveRound()?.toDomain()
        if (active != null) return active
        return veggieOrderDao.getAllRounds().firstOrNull()?.toDomain()
    }

    override suspend fun getRoundById(roundId: String): VeggieOrderRound? {
        return veggieOrderDao.getRoundById(roundId)?.toDomain()
    }

    override suspend fun openNewRound(
        buyerOperationFeeCents: Long,
        transportFeeCents: Long,
        openedAt: Long
    ): VeggieOrderRound = mutationMutex.withLock {
        val existingActive = veggieOrderDao.getActiveRound()
        if (existingActive != null) {
            return@withLock existingActive.toDomain()
        }

        val now = if (openedAt > 0L) openedAt else currentTimeMillis()
        val newRound = VeggieOrderRound(
            id = generateUuid(),
            openedAt = now,
            closedAt = null,
            settledAt = null,
            status = VeggieRoundStatus.OPEN,
            buyerOperationFeeCents = buyerOperationFeeCents.coerceAtLeast(0L),
            transportFeeCents = transportFeeCents.coerceAtLeast(0L),
            transporterUserId = null,
            updatedAt = now
        )
        veggieOrderDao.insertOrUpdateRound(VeggieOrderRoundEntity.fromDomain(newRound))
        onDataChanged?.invoke()
        newRound
    }

    override suspend fun closeRound(roundId: String, closedAt: Long): Boolean = mutationMutex.withLock {
        val roundEntity = veggieOrderDao.getRoundById(roundId) ?: return@withLock false
        val round = roundEntity.toDomain()
        if (round.status != VeggieRoundStatus.OPEN) return@withLock false

        val now = if (closedAt > 0L) closedAt else currentTimeMillis()
        val updated = round.copy(
            status = VeggieRoundStatus.CLOSED,
            closedAt = now,
            updatedAt = now
        )
        veggieOrderDao.insertOrUpdateRound(VeggieOrderRoundEntity.fromDomain(updated))
        onDataChanged?.invoke()
        true
    }

    override suspend fun reopenRound(roundId: String): Boolean = mutationMutex.withLock {
        val roundEntity = veggieOrderDao.getRoundById(roundId) ?: return@withLock false
        val round = roundEntity.toDomain()
        if (round.status != VeggieRoundStatus.CLOSED) return@withLock false

        // Ensure no other round is currently active
        val active = veggieOrderDao.getActiveRound()
        if (active != null && active.id != roundId) return@withLock false

        val now = currentTimeMillis()
        val updated = round.copy(
            status = VeggieRoundStatus.OPEN,
            closedAt = null,
            updatedAt = now
        )
        veggieOrderDao.insertOrUpdateRound(VeggieOrderRoundEntity.fromDomain(updated))
        onDataChanged?.invoke()
        true
    }

    // -------------------------------------------------------------------------
    // 4. Customer Order Entries & History
    // -------------------------------------------------------------------------
    override suspend fun getEntriesForRound(roundId: String): List<VeggieOrderEntry> {
        return veggieOrderDao.getRoundEntries(roundId).map { it.toDomain() }
    }

    override suspend fun getEntriesForUserInRound(roundId: String, userId: String): List<VeggieOrderEntry> {
        return veggieOrderDao.getEntriesForUserInRound(roundId, userId).map { it.toDomain() }
    }

    override suspend fun getPreviousRoundQuantitiesForUser(
        currentRoundId: String,
        userId: String
    ): Map<String, Long> {
        val allRounds = veggieOrderDao.getAllRounds()
        val currentRound = allRounds.firstOrNull { it.id == currentRoundId }
        val candidateRounds = allRounds.filter { round ->
            round.id != currentRoundId && (currentRound == null || round.openedAt <= currentRound.openedAt)
        }.sortedByDescending { it.openedAt }

        for (round in candidateRounds) {
            val entries = veggieOrderDao.getEntriesForUserInRound(round.id, userId)
            val positive = entries.filter { it.orderedQuantityScaled > 0L }
            if (positive.isNotEmpty()) {
                return positive.associate { it.veggieProductId to it.orderedQuantityScaled }
            }
        }
        return emptyMap()
    }

    override suspend fun saveCustomerOrder(
        roundId: String,
        user: User,
        productQuantitiesScaled: Map<String, Long>
    ): Boolean = mutationMutex.withLock {
        val round = veggieOrderDao.getRoundById(roundId)?.toDomain() ?: return@withLock false
        if (round.status != VeggieRoundStatus.OPEN) return@withLock false

        val existingEntries = veggieOrderDao.getEntriesForUserInRound(roundId, user.id)
            .associateBy { it.veggieProductId }
        val allProducts = veggieOrderDao.getAllVeggieProducts(includeDeleted = true)
            .associateBy { it.id }
        val allUnits = veggieOrderDao.getAllUnitTypes()
            .associateBy { it.id }

        val now = currentTimeMillis()
        val entriesToUpsert = mutableListOf<VeggieOrderEntryEntity>()
        val entryIdsToDelete = mutableListOf<String>()

        // Any existing entry not in productQuantitiesScaled or with quantity <= 0 is removed
        for ((productId, existing) in existingEntries) {
            val newQty = productQuantitiesScaled[productId] ?: 0L
            if (newQty <= 0L) {
                entryIdsToDelete.add(existing.id)
            }
        }

        for ((productId, rawQty) in productQuantitiesScaled) {
            if (rawQty <= 0L) continue
            val existing = existingEntries[productId]
            val product = allProducts[productId]
            if (existing == null && (product == null || !product.isActive || product.isDeleted)) {
                continue
            }
            val unit = product?.let { allUnits[it.unitTypeId] }
            val allowDecimals = unit?.allowDecimals ?: existing?.allowDecimalsSnapshot ?: false
            // Round to nearest whole unit if decimals are disallowed
            val normalizedQty = if (allowDecimals) {
                rawQty
            } else {
                (rawQty / VEGGIE_QTY_SCALE) * VEGGIE_QTY_SCALE
            }
            if (normalizedQty <= 0L) {
                if (existing != null) entryIdsToDelete.add(existing.id)
                continue
            }

            val entity = VeggieOrderEntryEntity(
                id = existing?.id ?: generateUuid(),
                roundId = roundId,
                userId = user.id,
                userNameSnapshot = user.name,
                veggieProductId = productId,
                veggieNameSnapshot = product?.name ?: existing?.veggieNameSnapshot ?: productId,
                unitNameSnapshot = unit?.name ?: existing?.unitNameSnapshot ?: "",
                allowDecimalsSnapshot = allowDecimals,
                orderedQuantityScaled = normalizedQty,
                actualQuantityScaled = existing?.actualQuantityScaled,
                effectiveUnitPriceCents = existing?.effectiveUnitPriceCents,
                finalItemTotalCents = existing?.finalItemTotalCents,
                updatedAt = now
            )
            entriesToUpsert.add(entity)
        }

        veggieOrderDao.replaceUserOrderEntriesForRound(entriesToUpsert, entryIdsToDelete)
        onDataChanged?.invoke()
        true
    }

    override suspend fun getCustomerOrderHistory(userId: String): List<CustomerVeggieRoundHistoryItem> {
        val userEntriesAllRounds = veggieOrderDao.getEntriesForUserAcrossRounds(userId)
        if (userEntriesAllRounds.isEmpty()) return emptyList()

        val entriesByRoundId = userEntriesAllRounds.groupBy { it.roundId }
        val productsById = veggieOrderDao.getAllVeggieProducts(includeDeleted = true).associateBy { it.id }

        val historyItems = mutableListOf<CustomerVeggieRoundHistoryItem>()
        for ((roundId, rawUserEntries) in entriesByRoundId) {
            val round = veggieOrderDao.getRoundById(roundId)?.toDomain() ?: continue
            val allRoundEntries = veggieOrderDao.getRoundEntries(roundId).map { it.toDomain() }
            val userEntries = rawUserEntries.map { it.toDomain() }.sortedByAccentInsensitive { it.veggieNameSnapshot }

            val participatingUserIds = allRoundEntries
                .filter { it.deliveredQuantityScaled > 0L }
                .map { it.userId }
                .toSet()

            val feeShares = VeggieSettlementCalculator.distributeCombinedFeesEqually(
                combinedFeeCents = round.combinedFeeCents,
                participatingUserIds = participatingUserIds
            )
            val userFeeShare = feeShares[userId] ?: 0L

            val itemsSubtotal = if (round.status == VeggieRoundStatus.SETTLED) {
                userEntries.sumOf { it.finalItemTotalCents ?: 0L }
            } else {
                userEntries.sumOf { entry ->
                    val estPrice = entry.effectiveUnitPriceCents ?: productsById[entry.veggieProductId]?.estimatedPriceCents
                    if (estPrice != null && estPrice > 0L) {
                        VeggieSettlementCalculator.calculateTotalSpentFromUnitPrice(
                            unitPriceCents = estPrice,
                            totalDeliveredQtyScaled = entry.deliveredQuantityScaled
                        )
                    } else {
                        0L
                    }
                }
            }

            historyItems.add(
                CustomerVeggieRoundHistoryItem(
                    round = round,
                    entries = userEntries,
                    itemsSubtotalCents = itemsSubtotal,
                    combinedFeeShareCents = userFeeShare,
                    grandTotalCents = itemsSubtotal + userFeeShare
                )
            )
        }

        return historyItems.sortedByDescending { it.round.openedAt }
    }

    // -------------------------------------------------------------------------
    // 5. Settlement & Corrections
    // -------------------------------------------------------------------------
    override suspend fun upsertAdminOrderEntry(entry: VeggieOrderEntry): Boolean = mutationMutex.withLock {
        val round = veggieOrderDao.getRoundById(entry.roundId)?.toDomain() ?: return@withLock false
        val now = if (entry.updatedAt > 0L) entry.updatedAt else currentTimeMillis()
        val existing = veggieOrderDao.getEntryByRoundUserProduct(entry.roundId, entry.userId, entry.veggieProductId)
        val merged = entry.copy(
            id = existing?.id ?: entry.id.ifBlank { generateUuid() },
            updatedAt = now
        )
        veggieOrderDao.insertOrUpdateEntry(VeggieOrderEntryEntity.fromDomain(merged))
        if (round.updatedAt < now) {
            veggieOrderDao.insertOrUpdateRound(
                VeggieOrderRoundEntity.fromDomain(round.copy(updatedAt = now))
            )
        }
        onDataChanged?.invoke()
        true
    }

    override suspend fun getProductSettlementsForRound(roundId: String): List<VeggieRoundProductSettlement> {
        return veggieOrderDao.getRoundProductSettlements(roundId).map { it.toDomain() }
    }

    override suspend fun saveDraftSettlement(
        roundId: String,
        buyerOperationFeeCents: Long,
        transportFeeCents: Long,
        transporterUserId: String?,
        productSettlements: List<VeggieRoundProductSettlement>,
        actualQuantitiesByEntryId: Map<String, Long>
    ): Boolean = mutationMutex.withLock {
        val roundEntity = veggieOrderDao.getRoundById(roundId) ?: return@withLock false
        val round = roundEntity.toDomain()
        if (round.status == VeggieRoundStatus.SETTLED) return@withLock false

        val now = currentTimeMillis()
        val existingEntries = veggieOrderDao.getRoundEntries(roundId).map { it.toDomain() }
        val updatedEntries = existingEntries.map { entry ->
            val newActual = actualQuantitiesByEntryId[entry.id]
            if (newActual != null) {
                VeggieOrderEntryEntity.fromDomain(
                    entry.copy(actualQuantityScaled = newActual.coerceAtLeast(0L), updatedAt = now)
                )
            } else {
                VeggieOrderEntryEntity.fromDomain(entry)
            }
        }

        val updatedRound = round.copy(
            buyerOperationFeeCents = buyerOperationFeeCents.coerceAtLeast(0L),
            transportFeeCents = transportFeeCents.coerceAtLeast(0L),
            transporterUserId = transporterUserId?.takeIf { it.isNotBlank() },
            updatedAt = now
        )

        val settlementEntities = productSettlements.map {
            VeggieRoundProductSettlementEntity.fromDomain(it.copy(roundId = roundId, updatedAt = now))
        }

        veggieOrderDao.saveSettlementState(
            round = VeggieOrderRoundEntity.fromDomain(updatedRound),
            updatedEntries = updatedEntries,
            updatedProductSettlements = settlementEntities,
            catalogPriceUpdates = emptyMap(),
            timestamp = now
        )
        onDataChanged?.invoke()
        true
    }

    override suspend fun executeRoundSettlement(
        roundId: String,
        buyerOperationFeeCents: Long,
        transportFeeCents: Long,
        transporterUserId: String?,
        productSettlements: List<VeggieRoundProductSettlement>,
        actualQuantitiesByEntryId: Map<String, Long>,
        settledAt: Long
    ): VeggieRoundSettlementResult? = mutationMutex.withLock {
        val roundEntity = veggieOrderDao.getRoundById(roundId) ?: return@withLock null
        val previousRound = roundEntity.toDomain()
        val previousEntries = veggieOrderDao.getRoundEntries(roundId).map { it.toDomain() }

        val now = if (settledAt > 0L) settledAt else currentTimeMillis()
        val entriesWithActuals = previousEntries.map { entry ->
            val actualQty = actualQuantitiesByEntryId[entry.id]
                ?: entry.actualQuantityScaled
                ?: entry.orderedQuantityScaled
            entry.copy(actualQuantityScaled = actualQty.coerceAtLeast(0L), updatedAt = now)
        }

        val settlementInputsByProduct = productSettlements.associateBy { it.veggieProductId }
        val calculationResult = VeggieSettlementCalculator.calculateRoundSettlement(
            roundId = roundId,
            entries = entriesWithActuals,
            productSettlementInputs = settlementInputsByProduct,
            buyerOperationFeeCents = buyerOperationFeeCents,
            transportFeeCents = transportFeeCents,
            transporterUserId = transporterUserId,
            timestamp = now
        )

        val weekInfo = IsoWeekUtils.fromEpochMillis(previousRound.openedAt)
        val batchTransactions = mutableListOf<Triple<TransactionEntity, Long, Map<String, Long>>>()

        if (previousRound.status != VeggieRoundStatus.SETTLED) {
            // Initial settlement: 1 summary ADMIN_WITHDRAWAL per customer + optional ADMIN_DEPOSIT for transporter
            for (summary in calculationResult.customerSummaries) {
                if (summary.grandTotalCents <= 0L) continue
                // Verify user exists in DB before recording transaction
                val userEntity = userDao.getUserById(summary.userId) ?: continue
                val withdrawalAmount = -summary.grandTotalCents
                val txEntity = TransactionEntity(
                    id = generateUuid(),
                    userId = userEntity.id,
                    userNameSnapshot = userEntity.name,
                    timestamp = now,
                    type = TransactionType.ADMIN_WITHDRAWAL.name,
                    referenceTransactionId = null,
                    note = "SYSNOTE|VEGGIE_ORDER|${weekInfo.weekOfYear}|${weekInfo.isoYear}|$roundId",
                    totalAmount = withdrawalAmount,
                    items = emptyList()
                )
                batchTransactions.add(Triple(txEntity, withdrawalAmount, emptyMap()))
            }

            val cleanTransporterId = calculationResult.transporterUserId
            if (cleanTransporterId != null && calculationResult.transporterCreditCents > 0L) {
                val transporterEntity = userDao.getUserById(cleanTransporterId)
                if (transporterEntity != null) {
                    val creditAmount = calculationResult.transporterCreditCents
                    val transportTx = TransactionEntity(
                        id = generateUuid(),
                        userId = transporterEntity.id,
                        userNameSnapshot = transporterEntity.name,
                        timestamp = now,
                        type = TransactionType.ADMIN_DEPOSIT.name,
                        referenceTransactionId = null,
                        note = "SYSNOTE|VEGGIE_TRANSPORT|${weekInfo.weekOfYear}|${weekInfo.isoYear}|$roundId",
                        totalAmount = creditAmount,
                        items = emptyList()
                    )
                    batchTransactions.add(Triple(transportTx, creditAmount, emptyMap()))
                }
            }
        } else {
            // Post-settlement correction: compute previous customer grand totals and apply differential transactions
            val prevEntriesByUser = previousEntries.groupBy { it.userId }
            val prevParticipatingUsers = prevEntriesByUser
                .filter { (_, list) -> list.any { it.deliveredQuantityScaled > 0L } }
                .keys
            val prevFeeShares = VeggieSettlementCalculator.distributeCombinedFeesEqually(
                combinedFeeCents = previousRound.combinedFeeCents,
                participatingUserIds = prevParticipatingUsers
            )
            val previousCustomerTotals = prevParticipatingUsers.associateWith { uid ->
                val itemsSub = prevEntriesByUser[uid]?.sumOf { it.finalItemTotalCents ?: 0L } ?: 0L
                itemsSub + (prevFeeShares[uid] ?: 0L)
            }
            val newCustomerTotals = calculationResult.customerSummaries.associate {
                it.userId to it.grandTotalCents
            }

            val customerBalanceDeltas = VeggieSettlementCalculator.calculateCustomerChargeCorrectionDeltas(
                previousCustomerTotals = previousCustomerTotals,
                newCustomerTotals = newCustomerTotals
            )

            for ((userId, balanceDelta) in customerBalanceDeltas) {
                if (balanceDelta == 0L) continue
                val userEntity = userDao.getUserById(userId) ?: continue
                val txType = if (balanceDelta > 0L) TransactionType.ADMIN_DEPOSIT else TransactionType.ADMIN_WITHDRAWAL
                val txEntity = TransactionEntity(
                    id = generateUuid(),
                    userId = userEntity.id,
                    userNameSnapshot = userEntity.name,
                    timestamp = now,
                    type = txType.name,
                    referenceTransactionId = null,
                    note = "SYSNOTE|VEGGIE_CORRECTION|${weekInfo.weekOfYear}|${weekInfo.isoYear}|$roundId",
                    totalAmount = balanceDelta,
                    items = emptyList()
                )
                batchTransactions.add(Triple(txEntity, balanceDelta, emptyMap()))
            }

            // Also check if transporter or transport fee changed
            val prevTransporterId = previousRound.transporterUserId?.takeIf { it.isNotBlank() }
            val prevTransportCredit = if (prevTransporterId != null) previousRound.transportFeeCents.coerceAtLeast(0L) else 0L
            val newTransporterId = calculationResult.transporterUserId
            val newTransportCredit = calculationResult.transporterCreditCents

            val transporterDeltas = mutableMapOf<String, Long>()
            if (prevTransporterId != null && prevTransportCredit > 0L) {
                transporterDeltas[prevTransporterId] = (transporterDeltas[prevTransporterId] ?: 0L) - prevTransportCredit
            }
            if (newTransporterId != null && newTransportCredit > 0L) {
                transporterDeltas[newTransporterId] = (transporterDeltas[newTransporterId] ?: 0L) + newTransportCredit
            }

            for ((tUserId, tDelta) in transporterDeltas) {
                if (tDelta == 0L) continue
                val userEntity = userDao.getUserById(tUserId) ?: continue
                val txType = if (tDelta > 0L) TransactionType.ADMIN_DEPOSIT else TransactionType.ADMIN_WITHDRAWAL
                val txEntity = TransactionEntity(
                    id = generateUuid(),
                    userId = userEntity.id,
                    userNameSnapshot = userEntity.name,
                    timestamp = now,
                    type = txType.name,
                    referenceTransactionId = null,
                    note = "SYSNOTE|VEGGIE_CORRECTION|${weekInfo.weekOfYear}|${weekInfo.isoYear}|$roundId",
                    totalAmount = tDelta,
                    items = emptyList()
                )
                batchTransactions.add(Triple(txEntity, tDelta, emptyMap()))
            }
        }

        if (batchTransactions.isNotEmpty()) {
            transactionDao.executeBatchAtomicTransactions(batchTransactions)
        }

        val updatedRound = previousRound.copy(
            status = VeggieRoundStatus.SETTLED,
            closedAt = previousRound.closedAt ?: now,
            settledAt = previousRound.settledAt ?: now,
            buyerOperationFeeCents = buyerOperationFeeCents.coerceAtLeast(0L),
            transportFeeCents = transportFeeCents.coerceAtLeast(0L),
            transporterUserId = calculationResult.transporterUserId,
            updatedAt = now
        )

        veggieOrderDao.saveSettlementState(
            round = VeggieOrderRoundEntity.fromDomain(updatedRound),
            updatedEntries = calculationResult.updatedEntries.map { VeggieOrderEntryEntity.fromDomain(it) },
            updatedProductSettlements = calculationResult.updatedProductSettlements.map {
                VeggieRoundProductSettlementEntity.fromDomain(it)
            },
            catalogPriceUpdates = calculationResult.catalogPriceUpdates,
            timestamp = now
        )

        onDataChanged?.invoke()
        calculationResult
    }
}
