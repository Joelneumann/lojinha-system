package de.joelneumann.lojinha.domain.repository

import de.joelneumann.lojinha.domain.model.*
import de.joelneumann.lojinha.domain.service.VeggieRoundSettlementResult
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class CustomerVeggieRoundHistoryItem(
    val round: VeggieOrderRound,
    val entries: List<VeggieOrderEntry>,
    val itemsSubtotalCents: Long,
    val combinedFeeShareCents: Long,
    val grandTotalCents: Long
)

interface VeggieOrderRepository {
    // 1. Unit Types
    fun getUnitTypesFlow(): Flow<List<VeggieUnitType>>
    suspend fun getAllUnitTypes(): List<VeggieUnitType>
    suspend fun saveUnitType(unitType: VeggieUnitType)

    // 2. Veggie Catalog Products
    fun getVeggieProductsFlow(): Flow<List<VeggieProduct>>
    suspend fun getAllVeggieProducts(): List<VeggieProduct>
    suspend fun getVeggieProductById(id: String): VeggieProduct?
    suspend fun saveVeggieProduct(product: VeggieProduct)
    suspend fun updateVeggieProductImage(productId: String, imageFileName: String?, updatedAt: Long) {
        val existing = getVeggieProductById(productId) ?: return
        saveVeggieProduct(existing.copy(imageFileName = imageFileName, updatedAt = updatedAt))
    }
    suspend fun deactivateVeggieProduct(id: String)
    suspend fun cancelProductEntriesInOpenRound(roundId: String, productId: String): Boolean = false
    suspend fun hardDeleteVeggieProduct(id: String)

    // 3. Order Rounds
    fun getRoundsFlow(): Flow<List<VeggieOrderRound>>
    suspend fun getAllRounds(): List<VeggieOrderRound>
    suspend fun getActiveOrLatestRound(): VeggieOrderRound?
    suspend fun getRoundById(roundId: String): VeggieOrderRound?
    suspend fun openNewRound(
        buyerOperationFeeCents: Long,
        transportFeeCents: Long,
        openedAt: Long
    ): VeggieOrderRound
    suspend fun closeRound(roundId: String, closedAt: Long): Boolean
    suspend fun reopenRound(roundId: String): Boolean
    suspend fun deleteRound(roundId: String): Boolean
    suspend fun markBuyerPdfExported(roundId: String, timestamp: Long): Boolean
    suspend fun markBuyerPortalPdfExported(roundId: String, timestamp: Long): Boolean = false
    suspend fun markWorkerPdfExported(roundId: String, timestamp: Long): Boolean

    // 4. Customer Order Entries & History
    suspend fun getEntriesForRound(roundId: String): List<VeggieOrderEntry>
    suspend fun getEntriesForUserInRound(roundId: String, userId: String): List<VeggieOrderEntry>
    suspend fun getPreviousRoundQuantitiesForUser(currentRoundId: String, userId: String): Map<String, Long>
    suspend fun saveCustomerOrder(
        roundId: String,
        user: User,
        productQuantitiesScaled: Map<String, Long>
    ): Boolean
    suspend fun getCustomerOrderHistory(userId: String): List<CustomerVeggieRoundHistoryItem>

    // 5. Settlement & Corrections
    suspend fun upsertAdminOrderEntry(entry: VeggieOrderEntry): Boolean = false
    suspend fun deleteAdminOrderEntry(roundId: String, entryId: String): Boolean = false
    suspend fun getProductSettlementsForRound(roundId: String): List<VeggieRoundProductSettlement>
    suspend fun saveDraftSettlement(
        roundId: String,
        buyerOperationFeeCents: Long,
        transportFeeCents: Long,
        transporterUserId: String?,
        productSettlements: List<VeggieRoundProductSettlement>,
        actualQuantitiesByEntryId: Map<String, Long>
    ): Boolean
    suspend fun saveBuyerDraftPrices(
        roundId: String,
        buyerOperationFeeCents: Long,
        productSettlements: List<VeggieRoundProductSettlement>
    ): Boolean {
        val round = getRoundById(roundId) ?: return false
        if (round.status != VeggieRoundStatus.CLOSED) return false
        return saveDraftSettlement(
            roundId = roundId,
            buyerOperationFeeCents = buyerOperationFeeCents.coerceAtLeast(0L),
            transportFeeCents = round.transportFeeCents,
            transporterUserId = round.transporterUserId,
            productSettlements = productSettlements,
            actualQuantitiesByEntryId = emptyMap()
        )
    }
    suspend fun executeRoundSettlement(
        roundId: String,
        buyerOperationFeeCents: Long,
        transportFeeCents: Long,
        transporterUserId: String?,
        productSettlements: List<VeggieRoundProductSettlement>,
        actualQuantitiesByEntryId: Map<String, Long>,
        settledAt: Long
    ): VeggieRoundSettlementResult?

    // 6. Customer Favorites
    suspend fun getFavoriteProductIdsForUser(userId: String): Set<String> = emptySet()
    suspend fun setFavoriteProductIdsForUser(userId: String, productIds: Set<String>) {}
}
