package de.joelneumann.lojinha.ui.viewmodel

import de.joelneumann.lojinha.domain.model.*
import de.joelneumann.lojinha.domain.repository.CustomerVeggieRoundHistoryItem
import de.joelneumann.lojinha.domain.repository.SettingsRepository
import de.joelneumann.lojinha.domain.repository.UserRepository
import de.joelneumann.lojinha.domain.repository.VeggieOrderRepository
import de.joelneumann.lojinha.domain.service.VeggieRoundSettlementResult
import de.joelneumann.lojinha.ui.viewmodel.admin.AdminVeggieSubTab
import de.joelneumann.lojinha.ui.viewmodel.admin.AdminVeggieViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AdminVeggieViewModelTest {

    private class FakeUserRepository(
        private val users: MutableList<User> = mutableListOf()
    ) : UserRepository {
        override fun getUsersFlow(): Flow<List<User>> = flowOf(users)
        override suspend fun getAllUsers(): List<User> = users
        override suspend fun getUserById(id: String): User? = users.find { it.id == id }
        override suspend fun getUserByBarcode(barcode: String): User? = users.find { it.userBarcode == barcode }
        override suspend fun saveUser(user: User) {
            users.removeIf { it.id == user.id }
            users.add(user)
        }
        override suspend fun deactivateUser(id: String) {}
        override suspend fun softDeleteUser(id: String) {}
        override suspend fun restoreUser(id: String) {}
        override suspend fun canHardDeleteUser(id: String): Boolean = true
        override suspend fun hardDeleteUser(id: String): Boolean = true
        override suspend fun updateBalance(userId: String, amountDelta: Long) {}
    }

    private class FakeSettingsRepository(
        var settings: SystemSettings = SystemSettings()
    ) : SettingsRepository {
        override fun getSettingsFlow(): Flow<SystemSettings> = flowOf(settings)
        override suspend fun getSettings(): SystemSettings = settings
        override suspend fun updateSettings(settings: SystemSettings, notifyDataChanged: Boolean) {
            this.settings = settings
        }
    }

    private class FakeVeggieRepository(
        val rounds: MutableList<VeggieOrderRound> = mutableListOf(),
        val entries: MutableList<VeggieOrderEntry> = mutableListOf(),
        val settlements: MutableList<VeggieRoundProductSettlement> = mutableListOf(),
        val products: MutableList<VeggieProduct> = mutableListOf(),
        val unitTypes: MutableList<VeggieUnitType> = mutableListOf()
    ) : VeggieOrderRepository {
        override fun getUnitTypesFlow(): Flow<List<VeggieUnitType>> = flowOf(unitTypes)
        override suspend fun getAllUnitTypes(): List<VeggieUnitType> = unitTypes
        override suspend fun saveUnitType(unitType: VeggieUnitType) {}

        override fun getVeggieProductsFlow(): Flow<List<VeggieProduct>> = flowOf(products)
        override suspend fun getAllVeggieProducts(): List<VeggieProduct> = products
        override suspend fun getVeggieProductById(id: String): VeggieProduct? = products.find { it.id == id }
        override suspend fun saveVeggieProduct(product: VeggieProduct) {}
        override suspend fun deactivateVeggieProduct(id: String) {}
        override suspend fun hardDeleteVeggieProduct(id: String) {}

        override fun getRoundsFlow(): Flow<List<VeggieOrderRound>> = flowOf(rounds)
        override suspend fun getAllRounds(): List<VeggieOrderRound> = rounds
        override suspend fun getActiveOrLatestRound(): VeggieOrderRound? = rounds.firstOrNull { it.status != VeggieRoundStatus.SETTLED } ?: rounds.firstOrNull()
        override suspend fun getRoundById(roundId: String): VeggieOrderRound? = rounds.find { it.id == roundId }

        override suspend fun openNewRound(buyerOperationFeeCents: Long, transportFeeCents: Long, openedAt: Long): VeggieOrderRound {
            val r = VeggieOrderRound(
                id = "round-${rounds.size + 1}",
                openedAt = openedAt,
                buyerOperationFeeCents = buyerOperationFeeCents,
                transportFeeCents = transportFeeCents,
                status = VeggieRoundStatus.OPEN
            )
            rounds.add(r)
            return r
        }

        override suspend fun closeRound(roundId: String, closedAt: Long): Boolean {
            val idx = rounds.indexOfFirst { it.id == roundId }
            if (idx >= 0) {
                rounds[idx] = rounds[idx].copy(status = VeggieRoundStatus.CLOSED, closedAt = closedAt)
                return true
            }
            return false
        }

        override suspend fun reopenRound(roundId: String): Boolean {
            val idx = rounds.indexOfFirst { it.id == roundId }
            if (idx >= 0) {
                rounds[idx] = rounds[idx].copy(status = VeggieRoundStatus.OPEN, closedAt = null)
                return true
            }
            return false
        }

        override suspend fun deleteRound(roundId: String): Boolean {
            return rounds.removeIf { it.id == roundId }
        }

        override suspend fun markBuyerPdfExported(roundId: String, timestamp: Long): Boolean {
            val idx = rounds.indexOfFirst { it.id == roundId }
            if (idx >= 0) rounds[idx] = rounds[idx].copy(buyerPdfExportedAt = timestamp)
            return true
        }

        override suspend fun markWorkerPdfExported(roundId: String, timestamp: Long): Boolean {
            val idx = rounds.indexOfFirst { it.id == roundId }
            if (idx >= 0) rounds[idx] = rounds[idx].copy(workerPdfExportedAt = timestamp)
            return true
        }

        override suspend fun getEntriesForRound(roundId: String): List<VeggieOrderEntry> {
            return entries.filter { it.roundId == roundId }
        }

        override suspend fun getEntriesForUserInRound(roundId: String, userId: String): List<VeggieOrderEntry> {
            return entries.filter { it.roundId == roundId && it.userId == userId }
        }

        override suspend fun getPreviousRoundQuantitiesForUser(currentRoundId: String, userId: String): Map<String, Long> = emptyMap()

        override suspend fun saveCustomerOrder(roundId: String, user: User, productQuantitiesScaled: Map<String, Long>): Boolean = true

        override suspend fun getCustomerOrderHistory(userId: String): List<CustomerVeggieRoundHistoryItem> = emptyList()

        override suspend fun getProductSettlementsForRound(roundId: String): List<VeggieRoundProductSettlement> {
            return settlements.filter { it.roundId == roundId }
        }

        override suspend fun saveDraftSettlement(
            roundId: String,
            buyerOperationFeeCents: Long,
            transportFeeCents: Long,
            transporterUserId: String?,
            productSettlements: List<VeggieRoundProductSettlement>,
            actualQuantitiesByEntryId: Map<String, Long>
        ): Boolean {
            val idx = rounds.indexOfFirst { it.id == roundId }
            if (idx >= 0) {
                rounds[idx] = rounds[idx].copy(
                    buyerOperationFeeCents = buyerOperationFeeCents,
                    transportFeeCents = transportFeeCents,
                    transporterUserId = transporterUserId,
                    buyerPdfExportedAt = null,
                    workerPdfExportedAt = null
                )
            }
            settlements.removeIf { it.roundId == roundId }
            settlements.addAll(productSettlements)
            actualQuantitiesByEntryId.forEach { (entryId, qty) ->
                val eIdx = entries.indexOfFirst { it.id == entryId }
                if (eIdx >= 0) {
                    entries[eIdx] = entries[eIdx].copy(actualQuantityScaled = qty)
                }
            }
            return true
        }

        override suspend fun executeRoundSettlement(
            roundId: String,
            buyerOperationFeeCents: Long,
            transportFeeCents: Long,
            transporterUserId: String?,
            productSettlements: List<VeggieRoundProductSettlement>,
            actualQuantitiesByEntryId: Map<String, Long>,
            settledAt: Long
        ): VeggieRoundSettlementResult {
            saveDraftSettlement(roundId, buyerOperationFeeCents, transportFeeCents, transporterUserId, productSettlements, actualQuantitiesByEntryId)
            val idx = rounds.indexOfFirst { it.id == roundId }
            if (idx >= 0) {
                rounds[idx] = rounds[idx].copy(status = VeggieRoundStatus.SETTLED, settledAt = settledAt)
            }
            return VeggieRoundSettlementResult(
                updatedEntries = emptyList(),
                updatedProductSettlements = productSettlements,
                catalogPriceUpdates = emptyMap(),
                customerSummaries = emptyList(),
                totalMarketProductsSpentCents = 800L,
                combinedFeeCents = buyerOperationFeeCents + transportFeeCents,
                transporterUserId = transporterUserId,
                transporterCreditCents = transportFeeCents
            )
        }
    }

    private fun setupTestFixture(): Triple<FakeVeggieRepository, FakeUserRepository, User> {
        val testUser = User(id = "user-1", name = "Joel", userBarcode = "1001", userBarcodeNumber = "1001", isActive = true)
        val userRepo = FakeUserRepository(mutableListOf(testUser))

        val product = VeggieProduct(id = "prod-1", name = "Batata", unitTypeId = "unit-kg", estimatedPriceCents = 500L)
        val unit = VeggieUnitType(id = "unit-kg", name = "kg", allowDecimals = true)

        val settledRound = VeggieOrderRound(
            id = "round-past",
            openedAt = 1000L,
            settledAt = 2000L,
            status = VeggieRoundStatus.SETTLED,
            buyerOperationFeeCents = 300L,
            transportFeeCents = 500L
        )
        val openRound = VeggieOrderRound(
            id = "round-active",
            openedAt = 3000L,
            status = VeggieRoundStatus.OPEN,
            buyerOperationFeeCents = 400L,
            transportFeeCents = 600L
        )

        val entryPast = VeggieOrderEntry(
            id = "entry-past-1",
            roundId = "round-past",
            userId = "user-1",
            userNameSnapshot = "Joel",
            veggieProductId = "prod-1",
            veggieNameSnapshot = "Batata",
            unitNameSnapshot = "kg",
            allowDecimalsSnapshot = true,
            orderedQuantityScaled = 2000L,
            actualQuantityScaled = 2000L
        )
        val entryActive = VeggieOrderEntry(
            id = "entry-act-1",
            roundId = "round-active",
            userId = "user-1",
            userNameSnapshot = "Joel",
            veggieProductId = "prod-1",
            veggieNameSnapshot = "Batata",
            unitNameSnapshot = "kg",
            allowDecimalsSnapshot = true,
            orderedQuantityScaled = 1000L,
            actualQuantityScaled = 1000L
        )

        val veggieRepo = FakeVeggieRepository(
            rounds = mutableListOf(openRound, settledRound),
            entries = mutableListOf(entryPast, entryActive),
            products = mutableListOf(product),
            unitTypes = mutableListOf(unit)
        )

        return Triple(veggieRepo, userRepo, testUser)
    }

    @Test
    fun testActiveRoundScopingOnlyPicksUpNonSettledRound() = runTest {
        val (veggieRepo, userRepo) = setupTestFixture()
        val vm = AdminVeggieViewModel(veggieOrderRepository = veggieRepo, userRepository = userRepo, coroutineScope = this)
        advanceUntilIdle()

        assertEquals("round-active", vm.activeRound.value?.id)
        assertEquals("round-active", vm.selectedRound.value?.id)

        // Settle the active round
        val idx = veggieRepo.rounds.indexOfFirst { it.id == "round-active" }
        veggieRepo.rounds[idx] = veggieRepo.rounds[idx].copy(status = VeggieRoundStatus.SETTLED)

        vm.loadData()
        advanceUntilIdle()

        // When all rounds are settled, activeRound MUST be null
        assertNull(vm.activeRound.value)
        assertNull(vm.selectedRound.value)
    }

    @Test
    fun testOpenPastRoundForEditingStaysOnHistoryTabAndLoadsDetails() = runTest {
        val (veggieRepo, userRepo) = setupTestFixture()
        val vm = AdminVeggieViewModel(veggieOrderRepository = veggieRepo, userRepository = userRepo, coroutineScope = this)
        advanceUntilIdle()

        // Navigate to History tab
        vm.selectSubTab(AdminVeggieSubTab.HISTORY)
        assertEquals(AdminVeggieSubTab.HISTORY, vm.currentSubTab.value)

        // Open past round for editing
        vm.openPastRoundForEditing("round-past")
        advanceUntilIdle()

        // Tab should still be HISTORY, never jumped to ACTIVE_ROUND!
        assertEquals(AdminVeggieSubTab.HISTORY, vm.currentSubTab.value)
        assertEquals("round-past", vm.editingPastRound.value?.id)
        assertEquals("round-past", vm.selectedRound.value?.id)
        assertEquals(1, vm.selectedRoundEntries.value.size)
        assertEquals("entry-past-1", vm.selectedRoundEntries.value.first().id)
        assertFalse(vm.hasUnsavedSettlementChanges.value)
    }

    @Test
    fun testClosePastRoundEditorClearsEditingStateAndRestoresActiveRound() = runTest {
        val (veggieRepo, userRepo) = setupTestFixture()
        val vm = AdminVeggieViewModel(veggieOrderRepository = veggieRepo, userRepository = userRepo, coroutineScope = this)
        advanceUntilIdle()

        vm.selectSubTab(AdminVeggieSubTab.HISTORY)
        vm.openPastRoundForEditing("round-past")
        advanceUntilIdle()

        assertEquals("round-past", vm.editingPastRound.value?.id)

        // Close past round editor
        vm.closePastRoundEditor()
        advanceUntilIdle()

        assertNull(vm.editingPastRound.value)
        assertFalse(vm.hasUnsavedSettlementChanges.value)
        // Should restore active round into selectedRound
        assertEquals("round-active", vm.selectedRound.value?.id)
        assertEquals("entry-act-1", vm.selectedRoundEntries.value.first().id)
    }

    @Test
    fun testExecuteSettlementOnPastRoundClosesEditorAndReturnsToList() = runTest {
        val (veggieRepo, userRepo) = setupTestFixture()
        val vm = AdminVeggieViewModel(veggieOrderRepository = veggieRepo, userRepository = userRepo, coroutineScope = this)
        advanceUntilIdle()

        vm.selectSubTab(AdminVeggieSubTab.HISTORY)
        vm.openPastRoundForEditing("round-past")
        advanceUntilIdle()

        // Make an edit
        vm.updateEntryActualQuantity("entry-past-1", "3.0")
        assertTrue(vm.hasUnsavedSettlementChanges.value)

        // Save / Update settled round
        vm.executeSettlement()
        advanceUntilIdle()

        // In-place editor must be closed
        assertNull(vm.editingPastRound.value)
        // Sub-tab must still be HISTORY
        assertEquals(AdminVeggieSubTab.HISTORY, vm.currentSubTab.value)
        assertFalse(vm.hasUnsavedSettlementChanges.value)
        assertNotNull(vm.statusMessage.value)
    }

    @Test
    fun testExecuteSettlementOnActiveRoundTransitionsActiveRoundToNullOnActiveTab() = runTest {
        val (veggieRepo, userRepo) = setupTestFixture()
        val vm = AdminVeggieViewModel(veggieOrderRepository = veggieRepo, userRepository = userRepo, coroutineScope = this)
        advanceUntilIdle()

        assertEquals(AdminVeggieSubTab.ACTIVE_ROUND, vm.currentSubTab.value)
        assertEquals("round-active", vm.activeRound.value?.id)

        vm.executeSettlement()
        advanceUntilIdle()

        // Active round was settled, so activeRound is now null
        assertNull(vm.activeRound.value)
        // Remains on ACTIVE_ROUND tab to show empty state with success banner
        assertEquals(AdminVeggieSubTab.ACTIVE_ROUND, vm.currentSubTab.value)
        assertFalse(vm.hasUnsavedSettlementChanges.value)
        assertNotNull(vm.statusMessage.value)
    }

    @Test
    fun testDiscardDraftRevertsUnsavedChanges() = runTest {
        val (veggieRepo, userRepo) = setupTestFixture()
        val vm = AdminVeggieViewModel(veggieOrderRepository = veggieRepo, userRepository = userRepo, coroutineScope = this)
        advanceUntilIdle()

        val origQty = vm.draftActualQuantities.value["entry-act-1"]
        val origPrice = vm.draftMarketPrices.value["prod-1"]
        val origBuyerFee = vm.draftBuyerFeeInput.value

        vm.updateEntryActualQuantity("entry-act-1", "99")
        vm.updateProductMarketUnitPrice("prod-1", "99,00")
        vm.updateBuyerFeeInput("12,34")
        assertTrue(vm.hasUnsavedSettlementChanges.value)
        assertEquals("99", vm.draftActualQuantities.value["entry-act-1"])
        assertEquals("99,00", vm.draftMarketPrices.value["prod-1"])
        assertEquals("12,34", vm.draftBuyerFeeInput.value)

        vm.discardDraft()
        advanceUntilIdle()

        assertFalse(vm.hasUnsavedSettlementChanges.value)
        assertEquals(origQty, vm.draftActualQuantities.value["entry-act-1"])
        assertEquals(origPrice, vm.draftMarketPrices.value["prod-1"])
        assertEquals(origBuyerFee, vm.draftBuyerFeeInput.value)
    }

    @Test
    fun testDiscardDraftRevertsUnsavedChangesInPastRoundEditing() = runTest {
        val (veggieRepo, userRepo) = setupTestFixture()
        val vm = AdminVeggieViewModel(veggieOrderRepository = veggieRepo, userRepository = userRepo, coroutineScope = this)
        advanceUntilIdle()

        vm.openPastRoundForEditing("round-past")
        advanceUntilIdle()

        assertEquals("round-past", vm.editingPastRound.value?.id)
        assertFalse(vm.hasUnsavedSettlementChanges.value)

        val origQty = vm.draftActualQuantities.value["entry-past-1"]
        vm.updateEntryActualQuantity("entry-past-1", "88")
        assertTrue(vm.hasUnsavedSettlementChanges.value)
        assertEquals("88", vm.draftActualQuantities.value["entry-past-1"])

        vm.discardDraft()
        advanceUntilIdle()

        assertFalse(vm.hasUnsavedSettlementChanges.value)
        assertEquals(origQty, vm.draftActualQuantities.value["entry-past-1"])
    }

    @Test
    fun testNextAutoRoundScheduleReflectsSettings() = runTest {
        val (veggieRepo, userRepo) = setupTestFixture()
        val settingsRepo = FakeSettingsRepository(
            settings = SystemSettings(
                veggieAutoOpenEnabled = true,
                veggieAutoOpenDayOfWeek = 1,
                veggieAutoOpenTime = "08:00"
            )
        )
        val vm = AdminVeggieViewModel(
            veggieOrderRepository = veggieRepo,
            userRepository = userRepo,
            settingsRepository = settingsRepo,
            coroutineScope = this
        )
        advanceUntilIdle()

        assertNotNull(vm.nextAutoRoundSchedule.value)
        assertEquals(1, vm.nextAutoRoundSchedule.value?.targetDayIso)
        assertEquals("08:00", vm.nextAutoRoundSchedule.value?.timeFormatted)

        // If auto-open is disabled:
        settingsRepo.settings = settingsRepo.settings.copy(veggieAutoOpenEnabled = false)
        vm.loadData()
        advanceUntilIdle()

        assertNull(vm.nextAutoRoundSchedule.value)
    }

    @Test
    fun testPastRoundsPaginationCalculatesPagesAndSlicesCorrectly() = runTest {
        val (veggieRepo, userRepo) = setupTestFixture()
        // Add 25 settled rounds + 1 open round
        veggieRepo.rounds.clear()
        val openRound = VeggieOrderRound(
            id = "round-open",
            openedAt = 100000L,
            status = VeggieRoundStatus.OPEN,
            buyerOperationFeeCents = 100L,
            transportFeeCents = 100L
        )
        veggieRepo.rounds.add(openRound)
        for (i in 1..25) {
            veggieRepo.rounds.add(
                VeggieOrderRound(
                    id = "settled-$i",
                    openedAt = 1000L * i,
                    settledAt = 1000L * i + 500L,
                    status = VeggieRoundStatus.SETTLED,
                    buyerOperationFeeCents = 200L,
                    transportFeeCents = 300L
                )
            )
        }

        val vm = AdminVeggieViewModel(
            veggieOrderRepository = veggieRepo,
            userRepository = userRepo,
            coroutineScope = this
        )
        advanceUntilIdle()

        // 1. Initial State: pageSize = 10, totalCount = 25 (open round excluded)
        assertEquals(25, vm.historyTotalCount.value)
        assertEquals(10, vm.historyPageSize.value)
        assertEquals(3, vm.historyTotalPages.value)
        assertEquals(0, vm.historyCurrentPage.value)
        assertEquals(10, vm.pagedSettledRounds.value.size)
        // Descending order: newest settled round first (settled-25 has openedAt 25000)
        assertEquals("settled-25", vm.pagedSettledRounds.value.first().id)
        assertEquals("settled-16", vm.pagedSettledRounds.value.last().id)

        // 2. Navigate to Page 1
        vm.setHistoryPage(1)
        assertEquals(1, vm.historyCurrentPage.value)
        assertEquals(10, vm.pagedSettledRounds.value.size)
        assertEquals("settled-15", vm.pagedSettledRounds.value.first().id)
        assertEquals("settled-6", vm.pagedSettledRounds.value.last().id)

        // 3. Navigate to Page 2 (last page, partial 5 items)
        vm.setHistoryPage(2)
        assertEquals(2, vm.historyCurrentPage.value)
        assertEquals(5, vm.pagedSettledRounds.value.size)
        assertEquals("settled-5", vm.pagedSettledRounds.value.first().id)
        assertEquals("settled-1", vm.pagedSettledRounds.value.last().id)

        // 4. Out-of-bounds page is rejected
        vm.setHistoryPage(3)
        assertEquals(2, vm.historyCurrentPage.value)
        vm.setHistoryPage(-1)
        assertEquals(2, vm.historyCurrentPage.value)

        // 5. Change Page Size to 25 -> resets to page 0
        vm.setHistoryPageSize(25)
        assertEquals(0, vm.historyCurrentPage.value)
        assertEquals(25, vm.historyPageSize.value)
        assertEquals(1, vm.historyTotalPages.value)
        assertEquals(25, vm.pagedSettledRounds.value.size)
        assertEquals("settled-25", vm.pagedSettledRounds.value.first().id)
        assertEquals("settled-1", vm.pagedSettledRounds.value.last().id)

        // 6. Change Page Size to 50
        vm.setHistoryPageSize(50)
        assertEquals(0, vm.historyCurrentPage.value)
        assertEquals(50, vm.historyPageSize.value)
        assertEquals(1, vm.historyTotalPages.value)
        assertEquals(25, vm.pagedSettledRounds.value.size)
    }

    @Test
    fun testPastRoundsPaginationClampsWhenRoundsDeleted() = runTest {
        val (veggieRepo, userRepo) = setupTestFixture()
        veggieRepo.rounds.clear()
        for (i in 1..15) {
            veggieRepo.rounds.add(
                VeggieOrderRound(
                    id = "settled-$i",
                    openedAt = 1000L * i,
                    settledAt = 1000L * i + 500L,
                    status = VeggieRoundStatus.SETTLED,
                    buyerOperationFeeCents = 200L,
                    transportFeeCents = 300L
                )
            )
        }

        val vm = AdminVeggieViewModel(
            veggieOrderRepository = veggieRepo,
            userRepository = userRepo,
            coroutineScope = this
        )
        advanceUntilIdle()

        // 15 items with pageSize 10 => 2 pages
        assertEquals(15, vm.historyTotalCount.value)
        assertEquals(2, vm.historyTotalPages.value)

        // Go to page 1
        vm.setHistoryPage(1)
        assertEquals(1, vm.historyCurrentPage.value)
        assertEquals(5, vm.pagedSettledRounds.value.size)

        // Delete 6 items so total becomes 9 => only 1 page left
        veggieRepo.rounds.removeAll { it.id in listOf("settled-1", "settled-2", "settled-3", "settled-4", "settled-5", "settled-6") }
        vm.loadData()
        advanceUntilIdle()

        assertEquals(9, vm.historyTotalCount.value)
        assertEquals(1, vm.historyTotalPages.value)
        assertEquals(0, vm.historyCurrentPage.value) // Clamped to 0
        assertEquals(9, vm.pagedSettledRounds.value.size)
    }

    @Test
    fun testSavingDraftInvalidatesPdfExportCheckmark() = runTest {
        val (veggieRepo, userRepo) = setupTestFixture()
        val vm = AdminVeggieViewModel(veggieOrderRepository = veggieRepo, userRepository = userRepo, coroutineScope = this)
        advanceUntilIdle()

        val roundId = "round-active"
        vm.markPdfExported(roundId, isBuyer = true)
        vm.markPdfExported(roundId, isBuyer = false)
        advanceUntilIdle()

        assertNotNull(vm.activeRound.value?.buyerPdfExportedAt)
        assertNotNull(vm.activeRound.value?.workerPdfExportedAt)

        // Change a quantity in the list (unsaved draft changes)
        vm.updateEntryActualQuantity("entry-act-1", "99")

        // Unsaved draft edits do NOT invalidate export timestamps
        assertNotNull(vm.activeRound.value?.buyerPdfExportedAt)
        assertNotNull(vm.activeRound.value?.workerPdfExportedAt)

        // Save draft settlement triggers onSuccess callback and resets timestamps
        var callbackCalled = false
        vm.saveDraftSettlement(onSuccess = { callbackCalled = true })
        advanceUntilIdle()

        assertTrue(callbackCalled)
        assertNull(vm.activeRound.value?.buyerPdfExportedAt)
        assertNull(vm.activeRound.value?.workerPdfExportedAt)
    }
}
