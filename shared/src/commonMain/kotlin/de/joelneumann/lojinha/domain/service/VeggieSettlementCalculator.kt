package de.joelneumann.lojinha.domain.service

import de.joelneumann.lojinha.domain.model.VEGGIE_QTY_SCALE
import de.joelneumann.lojinha.domain.model.VeggieOrderEntry
import de.joelneumann.lojinha.domain.model.VeggieRoundProductSettlement
import kotlinx.serialization.Serializable
import kotlin.math.roundToLong

@Serializable
data class VeggieCustomerSettlementSummary(
    val userId: String,
    val userName: String,
    val itemsSubtotalCents: Long,
    val combinedFeeShareCents: Long,
    val grandTotalCents: Long,
    val entries: List<VeggieOrderEntry>
)

@Serializable
data class VeggieRoundSettlementResult(
    val updatedEntries: List<VeggieOrderEntry>,
    val updatedProductSettlements: List<VeggieRoundProductSettlement>,
    val catalogPriceUpdates: Map<String, Long>, // veggieProductId -> new estimatedPriceCents
    val customerSummaries: List<VeggieCustomerSettlementSummary>,
    val totalMarketProductsSpentCents: Long,
    val combinedFeeCents: Long,
    val transporterUserId: String?,
    val transporterCreditCents: Long
)

object VeggieSettlementCalculator {

    /**
     * Distributes [totalSpentCents] proportionally across customers who received the product
     * (`actualQuantityScaled > 0`), using the Largest Remainder Method so that the sum of
     * customer shares equals [totalSpentCents] to the exact cent.
     *
     * Customers with `quantity <= 0` receive `0L`.
     */
    fun distributeProductTotalSpent(
        totalSpentCents: Long,
        customerQuantitiesScaled: Map<String, Long>
    ): Map<String, Long> {
        if (customerQuantitiesScaled.isEmpty()) return emptyMap()
        val clampedTotalSpent = totalSpentCents.coerceAtLeast(0L)

        val positiveEntries = customerQuantitiesScaled.filterValues { it > 0L }
        val totalDeliveredQty = positiveEntries.values.sum()
        if (clampedTotalSpent == 0L || totalDeliveredQty <= 0L) {
            return customerQuantitiesScaled.keys.associateWith { 0L }
        }

        val floorShares = mutableMapOf<String, Long>()
        val remainders = mutableMapOf<String, Long>()
        var sumFloor = 0L

        for ((key, qty) in positiveEntries) {
            val numerator = clampedTotalSpent * qty
            val floorShare = numerator / totalDeliveredQty
            val rem = numerator % totalDeliveredQty
            floorShares[key] = floorShare
            remainders[key] = rem
            sumFloor += floorShare
        }

        val leftoverCents = (clampedTotalSpent - sumFloor).coerceAtLeast(0L).toInt()
        val sortedByRemainder = positiveEntries.keys.sortedWith(
            compareByDescending<String> { remainders[it] ?: 0L }
                .thenBy { it }
        )

        val bonusKeys = sortedByRemainder.take(leftoverCents).toSet()

        return customerQuantitiesScaled.keys.associateWith { key ->
            if (!positiveEntries.containsKey(key)) {
                0L
            } else {
                (floorShares[key] ?: 0L) + if (bonusKeys.contains(key)) 1L else 0L
            }
        }
    }

    /**
     * Splits [combinedFeeCents] (`buyerOperationFeeCents + transportFeeCents`) equally across
     * all [participatingUserIds], distributing any 1-cent remainder deterministically by sorted `userId`.
     */
    fun distributeCombinedFeesEqually(
        combinedFeeCents: Long,
        participatingUserIds: Set<String>
    ): Map<String, Long> {
        val cleanIds = participatingUserIds.filter { it.isNotBlank() }.distinct().sorted()
        if (cleanIds.isEmpty()) return emptyMap()
        val clampedFee = combinedFeeCents.coerceAtLeast(0L)
        if (clampedFee == 0L) return cleanIds.associateWith { 0L }

        val count = cleanIds.size.toLong()
        val baseShare = clampedFee / count
        val remainder = (clampedFee % count).toInt()
        val bonusUsers = cleanIds.take(remainder).toSet()

        return cleanIds.associateWith { userId ->
            baseShare + if (bonusUsers.contains(userId)) 1L else 0L
        }
    }

    /**
     * Computes `Effective Unit Price = round((totalSpentCents * 1000) / totalDeliveredQtyScaled)`.
     */
    fun calculateEffectiveUnitPrice(
        totalSpentCents: Long,
        totalDeliveredQtyScaled: Long
    ): Long {
        if (totalSpentCents <= 0L || totalDeliveredQtyScaled <= 0L) return 0L
        return ((totalSpentCents.toDouble() * VEGGIE_QTY_SCALE) / totalDeliveredQtyScaled.toDouble())
            .roundToLong()
            .coerceAtLeast(0L)
    }

    /**
     * Computes `Total Spent = round((unitPriceCents * totalDeliveredQtyScaled) / 1000)`.
     */
    fun calculateTotalSpentFromUnitPrice(
        unitPriceCents: Long,
        totalDeliveredQtyScaled: Long
    ): Long {
        if (unitPriceCents <= 0L || totalDeliveredQtyScaled <= 0L) return 0L
        return ((unitPriceCents.toDouble() * totalDeliveredQtyScaled.toDouble()) / VEGGIE_QTY_SCALE)
            .roundToLong()
            .coerceAtLeast(0L)
    }

    /**
     * Resolves the reference unit price to store in `VeggieProduct.estimatedPriceCents` for next week:
     * prefers explicit [marketUnitPriceCents] if entered by Admin, otherwise falls back to
     * the effective unit price derived from [totalSpentCents].
     */
    fun resolveCatalogReferencePrice(
        marketUnitPriceCents: Long?,
        totalSpentCents: Long?,
        totalDeliveredQtyScaled: Long
    ): Long? {
        if (marketUnitPriceCents != null && marketUnitPriceCents > 0L) {
            return marketUnitPriceCents
        }
        if (totalSpentCents != null && totalSpentCents > 0L && totalDeliveredQtyScaled > 0L) {
            val effective = calculateEffectiveUnitPrice(totalSpentCents, totalDeliveredQtyScaled)
            if (effective > 0L) return effective
        }
        return null
    }

    /**
     * Resolves the authoritative `totalSpentCents` for a product:
     * if [totalSpentCents] is provided (`!= null`), uses it directly;
     * otherwise, if [marketUnitPriceCents] is provided, computes `marketUnitPriceCents * totalDeliveredQty / 1000`.
     */
    fun resolveAuthoritativeTotalSpent(
        marketUnitPriceCents: Long?,
        totalSpentCents: Long?,
        totalDeliveredQtyScaled: Long
    ): Long {
        if (totalSpentCents != null && totalSpentCents >= 0L) {
            return totalSpentCents
        }
        if (marketUnitPriceCents != null && marketUnitPriceCents > 0L && totalDeliveredQtyScaled > 0L) {
            return calculateTotalSpentFromUnitPrice(marketUnitPriceCents, totalDeliveredQtyScaled)
        }
        return 0L
    }

    /**
     * Executes the complete round settlement calculation across all products, customers, and fees.
     */
    fun calculateRoundSettlement(
        roundId: String,
        entries: List<VeggieOrderEntry>,
        productSettlementInputs: Map<String, VeggieRoundProductSettlement>,
        buyerOperationFeeCents: Long,
        transportFeeCents: Long,
        transporterUserId: String?,
        timestamp: Long = 0L
    ): VeggieRoundSettlementResult {
        val entriesByProduct = entries.groupBy { it.veggieProductId }
        val updatedEntries = mutableListOf<VeggieOrderEntry>()
        val updatedSettlements = mutableListOf<VeggieRoundProductSettlement>()
        val catalogUpdates = mutableMapOf<String, Long>()
        var totalMarketSpent = 0L

        for ((productId, productEntries) in entriesByProduct) {
            val input = productSettlementInputs[productId]
            val deliveredByEntryId = productEntries.associate { entry ->
                entry.id to entry.deliveredQuantityScaled.coerceAtLeast(0L)
            }
            val totalDeliveredQty = deliveredByEntryId.values.sum()

            val authoritativeTotalSpent = resolveAuthoritativeTotalSpent(
                marketUnitPriceCents = input?.marketUnitPriceCents,
                totalSpentCents = input?.totalSpentCents,
                totalDeliveredQtyScaled = totalDeliveredQty
            )
            totalMarketSpent += authoritativeTotalSpent

            val effectiveUnitPrice = calculateEffectiveUnitPrice(
                totalSpentCents = authoritativeTotalSpent,
                totalDeliveredQtyScaled = totalDeliveredQty
            )

            val catalogRefPrice = resolveCatalogReferencePrice(
                marketUnitPriceCents = input?.marketUnitPriceCents,
                totalSpentCents = authoritativeTotalSpent,
                totalDeliveredQtyScaled = totalDeliveredQty
            )
            if (catalogRefPrice != null && catalogRefPrice > 0L) {
                catalogUpdates[productId] = catalogRefPrice
            }

            val sharesByEntryId = distributeProductTotalSpent(
                totalSpentCents = authoritativeTotalSpent,
                customerQuantitiesScaled = deliveredByEntryId
            )

            for (entry in productEntries) {
                val deliveredQty = deliveredByEntryId[entry.id] ?: 0L
                val itemCost = sharesByEntryId[entry.id] ?: 0L
                updatedEntries.add(
                    entry.copy(
                        actualQuantityScaled = deliveredQty,
                        effectiveUnitPriceCents = effectiveUnitPrice,
                        finalItemTotalCents = itemCost,
                        updatedAt = if (timestamp > 0L) timestamp else entry.updatedAt
                    )
                )
            }

            updatedSettlements.add(
                VeggieRoundProductSettlement(
                    roundId = roundId,
                    veggieProductId = productId,
                    marketUnitPriceCents = input?.marketUnitPriceCents ?: effectiveUnitPrice.takeIf { it > 0L },
                    totalSpentCents = authoritativeTotalSpent,
                    updatedAt = timestamp
                )
            )
        }

        // Participating customers are those with at least one delivered item (> 0)
        val entriesByUser = updatedEntries.groupBy { it.userId }
        val participatingUserIds = entriesByUser
            .filter { (_, userEntries) -> userEntries.any { it.deliveredQuantityScaled > 0L } }
            .keys

        val combinedFee = buyerOperationFeeCents.coerceAtLeast(0L) + transportFeeCents.coerceAtLeast(0L)
        val feeSharesByUser = distributeCombinedFeesEqually(combinedFee, participatingUserIds)

        val customerSummaries = entriesByUser.mapNotNull { (userId, userEntries) ->
            if (!participatingUserIds.contains(userId)) return@mapNotNull null
            val userName = userEntries.firstOrNull()?.userNameSnapshot ?: ""
            val itemsSubtotal = userEntries.sumOf { it.finalItemTotalCents ?: 0L }
            val feeShare = feeSharesByUser[userId] ?: 0L
            VeggieCustomerSettlementSummary(
                userId = userId,
                userName = userName,
                itemsSubtotalCents = itemsSubtotal,
                combinedFeeShareCents = feeShare,
                grandTotalCents = itemsSubtotal + feeShare,
                entries = userEntries
            )
        }.sortedBy { it.userName.lowercase() }

        val cleanTransporterId = transporterUserId?.takeIf { it.isNotBlank() }
        val transporterCredit = if (cleanTransporterId != null) transportFeeCents.coerceAtLeast(0L) else 0L

        return VeggieRoundSettlementResult(
            updatedEntries = updatedEntries,
            updatedProductSettlements = updatedSettlements,
            catalogPriceUpdates = catalogUpdates,
            customerSummaries = customerSummaries,
            totalMarketProductsSpentCents = totalMarketSpent,
            combinedFeeCents = combinedFee,
            transporterUserId = cleanTransporterId,
            transporterCreditCents = transporterCredit
        )
    }

    /**
     * Computes per-user balance deltas when an already-settled round is edited/corrected.
     *
     * Returns a map of `userId -> balanceDeltaCents` (where a positive delta credits the user
     * because their charge decreased, and a negative delta debits the user because their charge increased).
     */
    fun calculateCustomerChargeCorrectionDeltas(
        previousCustomerTotals: Map<String, Long>,
        newCustomerTotals: Map<String, Long>
    ): Map<String, Long> {
        val allUsers = previousCustomerTotals.keys + newCustomerTotals.keys
        val deltas = mutableMapOf<String, Long>()
        for (userId in allUsers) {
            val prevCharge = previousCustomerTotals[userId] ?: 0L
            val newCharge = newCustomerTotals[userId] ?: 0L
            // Since charges are debits (-charge), balance delta is -(newCharge - prevCharge) = prevCharge - newCharge
            val balanceDelta = prevCharge - newCharge
            if (balanceDelta != 0L) {
                deltas[userId] = balanceDelta
            }
        }
        return deltas
    }
}
