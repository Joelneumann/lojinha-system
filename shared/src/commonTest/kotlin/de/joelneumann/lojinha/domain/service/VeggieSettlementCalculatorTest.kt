package de.joelneumann.lojinha.domain.service

import de.joelneumann.lojinha.domain.model.VEGGIE_QTY_SCALE
import de.joelneumann.lojinha.domain.model.VeggieOrderEntry
import de.joelneumann.lojinha.domain.model.VeggieRoundProductSettlement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VeggieSettlementCalculatorTest {

    @Test
    fun testDistributeProductTotalSpent_proportionalWithWeightLossAndExactCentRecovery() {
        // Buyer bought 5.0 kg of Tomatoes at R$ 10,00/kg = R$ 50,00 (5000 cents) Total Spent.
        // Due to market box weight loss, actual delivered quantities are:
        // Alice: 1.900 kg (1900L)
        // Bob:   1.900 kg (1900L)
        // Carlos: 0.950 kg (950L)
        // Total delivered = 4.750 kg (4750L).
        // Alice share = 5000 * (1900 / 4750) = 2000 cents (R$ 20,00)
        // Bob share   = 5000 * (1900 / 4750) = 2000 cents (R$ 20,00)
        // Carlos share = 5000 * (950 / 4750) = 1000 cents (R$ 10,00)
        // Sum = 5000 cents (100% market cost recovered from tomato buyers).
        val charges = VeggieSettlementCalculator.distributeProductTotalSpent(
            totalSpentCents = 5000L,
            customerQuantitiesScaled = mapOf(
                "alice" to 1900L,
                "bob" to 1900L,
                "carlos" to 950L,
                "diana_no_tomatoes" to 0L
            )
        )

        assertEquals(2000L, charges["alice"])
        assertEquals(2000L, charges["bob"])
        assertEquals(1000L, charges["carlos"])
        assertEquals(0L, charges["diana_no_tomatoes"])
        assertEquals(5000L, charges.values.sum())
    }

    @Test
    fun testDistributeProductTotalSpent_largestRemainderOneCentSplit() {
        // R$ 10,00 (1000 cents) split across 3 equal buyers (1.0 kg each) -> 334, 333, 333 cents
        val charges = VeggieSettlementCalculator.distributeProductTotalSpent(
            totalSpentCents = 1000L,
            customerQuantitiesScaled = mapOf(
                "user_b" to 1000L,
                "user_a" to 1000L,
                "user_c" to 1000L
            )
        )

        assertEquals(1000L, charges.values.sum())
        // Deterministic tie-breaker: userId ascending ("user_a" gets the 1-cent remainder)
        assertEquals(334L, charges["user_a"])
        assertEquals(333L, charges["user_b"])
        assertEquals(333L, charges["user_c"])
    }

    @Test
    fun testDistributeProductTotalSpent_unevenRemaindersPrioritizeLargestFraction() {
        // Total spent: 100 cents.
        // Deliveries:
        // u1: 1000 (1/6 = 16.666... cents) -> floor 16, rem 4000/6000
        // u2: 2000 (2/6 = 33.333... cents) -> floor 33, rem 2000/6000
        // u3: 3000 (3/6 = 50.000... cents) -> floor 50, rem 0
        // Sum of floors = 99, 1 cent left -> u1 has largest remainder (4000) so u1 gets +1 -> 17
        val charges = VeggieSettlementCalculator.distributeProductTotalSpent(
            totalSpentCents = 100L,
            customerQuantitiesScaled = mapOf(
                "u1" to 1000L,
                "u2" to 2000L,
                "u3" to 3000L
            )
        )

        assertEquals(17L, charges["u1"])
        assertEquals(33L, charges["u2"])
        assertEquals(50L, charges["u3"])
        assertEquals(100L, charges.values.sum())
    }

    @Test
    fun testBidirectionalPriceCalculations() {
        // 1. Typing Total Spent (R$ 50,00 = 5000 cents) with 4.750 kg delivered -> Effective Unit Price = 1053 cents (R$ 10,53/kg)
        val effectiveUnitPrice = VeggieSettlementCalculator.calculateEffectiveUnitPrice(
            totalSpentCents = 5000L,
            totalDeliveredQtyScaled = 4750L
        )
        assertEquals(1053L, effectiveUnitPrice)

        // 2. Typing Unit Price (R$ 8,50 = 850 cents) with 3 units (3000 scaled) delivered -> Total Spent = 2550 cents (R$ 25,50)
        val totalSpent = VeggieSettlementCalculator.calculateTotalSpentFromUnitPrice(
            unitPriceCents = 850L,
            totalDeliveredQtyScaled = 3L * VEGGIE_QTY_SCALE
        )
        assertEquals(2550L, totalSpent)
    }

    @Test
    fun testResolveCatalogReferencePriceAndAuthoritativeTotalSpent() {
        // Even if only 4.75 kg was delivered, customer billing uses Total Spent (5000 cents),
        // while the catalog reference price for next week uses the market unit price (1000 cents).
        assertEquals(
            5000L,
            VeggieSettlementCalculator.resolveAuthoritativeTotalSpent(
                marketUnitPriceCents = 1000L,
                totalSpentCents = 5000L,
                totalDeliveredQtyScaled = 4750L
            )
        )
        assertEquals(
            1000L,
            VeggieSettlementCalculator.resolveCatalogReferencePrice(
                marketUnitPriceCents = 1000L,
                totalSpentCents = 5000L,
                totalDeliveredQtyScaled = 4750L
            )
        )

        // If only Total Spent was entered (marketUnitPriceCents == null), catalog reference price uses effective unit price
        assertEquals(
            1000L, // 4800 cents / 4.8 kg = 1000 cents/kg
            VeggieSettlementCalculator.resolveCatalogReferencePrice(
                marketUnitPriceCents = null,
                totalSpentCents = 4800L,
                totalDeliveredQtyScaled = 4800L
            )
        )
    }

    @Test
    fun testDistributeCombinedFeesEqually() {
        // Buyer fee R$ 15,00 (1500) + Transport fee R$ 10,00 (1000) = R$ 25,00 (2500) across 3 customers
        // -> 834, 833, 833 cents
        val feeShares = VeggieSettlementCalculator.distributeCombinedFeesEqually(
            combinedFeeCents = 2500L,
            participatingUserIds = setOf("u2", "u1", "u3")
        )

        assertEquals(2500L, feeShares.values.sum())
        assertEquals(834L, feeShares["u1"])
        assertEquals(833L, feeShares["u2"])
        assertEquals(833L, feeShares["u3"])
    }

    @Test
    fun testFullRoundSettlementAndPostSettlementCorrectionDeltas() {
        // Round with 2 products:
        // Product 1 (Tomatoes): Total Spent = 3000 cents. Alice gets 1.0 kg, Bob gets 2.0 kg.
        //   -> Alice = 1000 cents, Bob = 2000 cents.
        // Product 2 (Lettuce): Unit Price = 500 cents, Bob gets 2 units (Total Spent = 1000 cents).
        //   -> Bob = 1000 cents, Alice = 0 cents (Alice didn't order Lettuce).
        // Combined fees: Buyer 600 + Transport 400 = 1000 cents split equally between Alice (500) and Bob (500).
        // Total charges: Alice = 1000 + 500 = 1500 cents; Bob = 3000 + 500 = 3500 cents.
        val entries = listOf(
            VeggieOrderEntry(
                id = "e1",
                roundId = "r1",
                userId = "alice",
                userNameSnapshot = "Alice",
                veggieProductId = "tomato",
                veggieNameSnapshot = "Tomato",
                unitNameSnapshot = "kg",
                allowDecimalsSnapshot = true,
                orderedQuantityScaled = 1000L,
                actualQuantityScaled = 1000L
            ),
            VeggieOrderEntry(
                id = "e2",
                roundId = "r1",
                userId = "bob",
                userNameSnapshot = "Bob",
                veggieProductId = "tomato",
                veggieNameSnapshot = "Tomato",
                unitNameSnapshot = "kg",
                allowDecimalsSnapshot = true,
                orderedQuantityScaled = 2000L,
                actualQuantityScaled = 2000L
            ),
            VeggieOrderEntry(
                id = "e3",
                roundId = "r1",
                userId = "bob",
                userNameSnapshot = "Bob",
                veggieProductId = "lettuce",
                veggieNameSnapshot = "Lettuce",
                unitNameSnapshot = "Unit",
                allowDecimalsSnapshot = false,
                orderedQuantityScaled = 2000L,
                actualQuantityScaled = 2000L
            )
        )

        val productSettlements = mapOf(
            "tomato" to VeggieRoundProductSettlement(
                roundId = "r1",
                veggieProductId = "tomato",
                marketUnitPriceCents = 1000L,
                totalSpentCents = 3000L
            ),
            "lettuce" to VeggieRoundProductSettlement(
                roundId = "r1",
                veggieProductId = "lettuce",
                marketUnitPriceCents = 500L,
                totalSpentCents = null
            )
        )

        val initialResult = VeggieSettlementCalculator.calculateRoundSettlement(
            roundId = "r1",
            entries = entries,
            productSettlementInputs = productSettlements,
            buyerOperationFeeCents = 600L,
            transportFeeCents = 400L,
            transporterUserId = "bob",
            timestamp = 1000L
        )

        val aliceSummary = initialResult.customerSummaries.first { it.userId == "alice" }
        val bobSummary = initialResult.customerSummaries.first { it.userId == "bob" }
        assertEquals(1500L, aliceSummary.grandTotalCents)
        assertEquals(3500L, bobSummary.grandTotalCents)
        assertEquals(500L, aliceSummary.combinedFeeShareCents)
        assertEquals(500L, bobSummary.combinedFeeShareCents)
        assertEquals("bob", initialResult.transporterUserId)
        assertEquals(400L, initialResult.transporterCreditCents)

        // Post-settlement correction: Admin realizes Bob only got 1 unit of Lettuce instead of 2,
        // and Alice actually got the other 1 unit of Lettuce!
        val correctedEntries = entries.map {
            if (it.id == "e3") it.copy(actualQuantityScaled = 1000L) else it
        } + VeggieOrderEntry(
            id = "e4",
            roundId = "r1",
            userId = "alice",
            userNameSnapshot = "Alice",
            veggieProductId = "lettuce",
            veggieNameSnapshot = "Lettuce",
            unitNameSnapshot = "Unit",
            allowDecimalsSnapshot = false,
            orderedQuantityScaled = 0L,
            actualQuantityScaled = 1000L
        )

        val correctedResult = VeggieSettlementCalculator.calculateRoundSettlement(
            roundId = "r1",
            entries = correctedEntries,
            productSettlementInputs = productSettlements,
            buyerOperationFeeCents = 600L,
            transportFeeCents = 400L,
            transporterUserId = "bob",
            timestamp = 2000L
        )

        // Now Alice has 500 cents of Lettuce -> total 2000 cents (charge increased by 500 -> balance delta -500)
        // Bob has 500 cents of Lettuce -> total 3000 cents (charge decreased by 500 -> balance delta +500)
        val deltas = VeggieSettlementCalculator.calculateCustomerChargeCorrectionDeltas(
            previousCustomerTotals = initialResult.customerSummaries.associate { it.userId to it.grandTotalCents },
            newCustomerTotals = correctedResult.customerSummaries.associate { it.userId to it.grandTotalCents }
        )

        assertEquals(-500L, deltas["alice"])
        assertEquals(500L, deltas["bob"])
        assertTrue(deltas.size == 2)
    }
}
