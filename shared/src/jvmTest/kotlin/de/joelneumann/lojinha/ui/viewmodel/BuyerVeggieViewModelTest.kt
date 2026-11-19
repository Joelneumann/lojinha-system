package de.joelneumann.lojinha.ui.viewmodel

import de.joelneumann.lojinha.domain.model.*
import de.joelneumann.lojinha.domain.repository.BuyerVeggieRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BuyerVeggieViewModelTest {

    private class FakeBuyerVeggieRepository(
        var currentState: BuyerVeggieRoundStateResponse
    ) : BuyerVeggieRepository {
        val dataChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        var lastSavedRequest: BuyerSaveDraftSettlementRequest? = null

        override suspend fun getRoundState(): BuyerVeggieRoundStateResponse = currentState

        override suspend fun saveDraftSettlement(request: BuyerSaveDraftSettlementRequest): Boolean {
            lastSavedRequest = request
            return currentState.round?.status == VeggieRoundStatus.CLOSED
        }

        override suspend fun logout() {}

        override fun observeDataChanges(): Flow<Unit> = dataChanges
    }

    @Test
    fun initiallyEmptyPricesAutoCalculateTotalSpentAcrossKeystrokesAndRespectManualOverride() = runBlocking {
        val repo = FakeBuyerVeggieRepository(
            BuyerVeggieRoundStateResponse(
                round = VeggieOrderRound(id = "r1", openedAt = 1000L, status = VeggieRoundStatus.CLOSED),
                items = listOf(
                    BuyerVeggieProductRowDto(
                        productId = "p1",
                        name = "Carrots",
                        unitAbbreviation = "kg",
                        allowDecimals = true,
                        totalOrderedScaled = 2500L
                    )
                ),
                buyerOperationFeeCents = 0L
            )
        )
        val vm = BuyerVeggieViewModel(repo, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))

        // Initially empty
        assertEquals(null, vm.marketPrices.value["p1"])
        assertEquals(null, vm.totalSpent.value["p1"])
        assertFalse(vm.hasUnsavedChanges.value)

        // Typing multi-digit unit price keystroke-by-keystroke updates auto-calculated total spent continuously
        vm.updateMarketUnitPrice("p1", "1")
        assertEquals("2,50", vm.totalSpent.value["p1"])
        vm.updateMarketUnitPrice("p1", "10")
        assertEquals("25", vm.totalSpent.value["p1"])
        vm.updateMarketUnitPrice("p1", "10,00")
        assertTrue(vm.hasUnsavedChanges.value)
        assertEquals("25", vm.totalSpent.value["p1"])

        vm.saveDraft("Saved!", "Error!")
        assertFalse(vm.hasUnsavedChanges.value)
        assertEquals(1000L, repo.lastSavedRequest?.productSettlements?.first()?.marketUnitPriceCents)
        assertEquals(2500L, repo.lastSavedRequest?.productSettlements?.first()?.totalSpentCents)

        // Editing total spent manually locks it so subsequent unit price edits do NOT overwrite it
        vm.updateTotalSpent("p1", "22,50")
        vm.updateMarketUnitPrice("p1", "12,00")
        assertEquals("22,50", vm.totalSpent.value["p1"])

        // Clearing total spent re-enables auto-calculation for subsequent unit price edits
        vm.updateTotalSpent("p1", "")
        vm.updateMarketUnitPrice("p1", "12,00")
        assertEquals("30", vm.totalSpent.value["p1"])
    }

    @Test
    fun localDraftRestoresUnsavedPricesAndClearsOnSave() = runBlocking {
        val repo = FakeBuyerVeggieRepository(
            BuyerVeggieRoundStateResponse(
                round = VeggieOrderRound(id = "r1", openedAt = 1000L, status = VeggieRoundStatus.CLOSED),
                items = listOf(
                    BuyerVeggieProductRowDto(
                        productId = "p1",
                        name = "Carrots",
                        unitAbbreviation = "kg",
                        totalOrderedScaled = 2000L
                    )
                )
            )
        )
        var storedDraft: BuyerVeggiePriceDraft? = null
        val vm1 = BuyerVeggieViewModel(
            repository = repo,
            coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            onLoadDraft = { storedDraft },
            onSaveDraft = { _, draft -> storedDraft = draft },
            onClearDraft = { storedDraft = null }
        )
        vm1.updateMarketUnitPrice("p1", "12,50")
        vm1.updateBuyerFee("5,00")
        assertTrue(storedDraft != null)

        // Simulate reload / re-login after Kiosk restart
        val vm2 = BuyerVeggieViewModel(
            repository = repo,
            coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            onLoadDraft = { storedDraft },
            onSaveDraft = { _, draft -> storedDraft = draft },
            onClearDraft = { storedDraft = null }
        )
        assertEquals("12,50", vm2.marketPrices.value["p1"])
        assertEquals("25", vm2.totalSpent.value["p1"])
        assertEquals("5,00", vm2.buyerFeeInput.value)
        assertTrue(vm2.hasUnsavedChanges.value)

        // Saving clears local draft
        vm2.saveDraft("Saved!", "Error!")
        assertEquals(null, storedDraft)
        assertFalse(vm2.hasUnsavedChanges.value)
    }

    @Test
    fun reopeningRoundLocksEditingEvenWhenUnsavedChangesExist() = runBlocking {
        val repo = FakeBuyerVeggieRepository(
            BuyerVeggieRoundStateResponse(
                round = VeggieOrderRound(id = "r1", openedAt = 1000L, status = VeggieRoundStatus.CLOSED),
                items = listOf(
                    BuyerVeggieProductRowDto(
                        productId = "p1",
                        name = "Tomatoes",
                        unitAbbreviation = "kg",
                        totalOrderedScaled = 1000L
                    )
                )
            )
        )
        val vm = BuyerVeggieViewModel(repo, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        vm.updateMarketUnitPrice("p1", "8,00")
        assertTrue(vm.hasUnsavedChanges.value)

        // Admin reopens round -> SSE notifies
        repo.currentState = BuyerVeggieRoundStateResponse(
            round = VeggieOrderRound(id = "r1", openedAt = 1000L, status = VeggieRoundStatus.OPEN),
            items = emptyList()
        )
        repo.dataChanges.emit(Unit)

        assertEquals(VeggieRoundStatus.OPEN, vm.roundState.value?.round?.status)
        assertFalse(vm.hasUnsavedChanges.value)
        assertTrue(vm.marketPrices.value.isEmpty())
    }
}
