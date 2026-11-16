package de.joelneumann.lojinha.ui.viewmodel

import de.joelneumann.lojinha.domain.model.*
import de.joelneumann.lojinha.domain.repository.CustomerVeggieRoundHistoryItem
import de.joelneumann.lojinha.domain.repository.SettingsRepository
import de.joelneumann.lojinha.domain.repository.UserRepository
import de.joelneumann.lojinha.domain.repository.VeggieOrderRepository
import de.joelneumann.lojinha.domain.service.VeggieRoundSettlementResult
import de.joelneumann.lojinha.ui.viewmodel.admin.AddOrderEntryMode
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
        override suspend fun saveUnitType(unitType: VeggieUnitType) {
            val idx = unitTypes.indexOfFirst { it.id == unitType.id }
            if (idx >= 0) unitTypes[idx] = unitType else unitTypes.add(unitType)
        }

        override fun getVeggieProductsFlow(): Flow<List<VeggieProduct>> = flowOf(products)
        override suspend fun getAllVeggieProducts(): List<VeggieProduct> = products
        override suspend fun getVeggieProductById(id: String): VeggieProduct? = products.find { it.id == id }
        override suspend fun saveVeggieProduct(product: VeggieProduct) {
            val idx = products.indexOfFirst { it.id == product.id }
            if (idx >= 0) products[idx] = product else products.add(product)
        }
        override suspend fun deactivateVeggieProduct(id: String) {
            val idx = products.indexOfFirst { it.id == id }
            if (idx >= 0) products[idx] = products[idx].copy(isActive = false)
        }
        override suspend fun cancelProductEntriesInOpenRound(roundId: String, productId: String): Boolean {
            val round = rounds.find { it.id == roundId } ?: return false
            if (round.status != VeggieRoundStatus.OPEN) return false
            entries.removeAll { it.roundId == roundId && it.veggieProductId == productId }
            return true
        }
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

        override suspend fun upsertAdminOrderEntry(entry: VeggieOrderEntry): Boolean {
            val idx = entries.indexOfFirst {
                it.id == entry.id || (it.roundId == entry.roundId && it.userId == entry.userId && it.veggieProductId == entry.veggieProductId)
            }
            if (idx >= 0) {
                entries[idx] = entry
            } else {
                entries.add(entry)
            }
            return true
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

    @Test
    fun testAddOrUpdateCustomerEntry_newEntry_createsOrderEntryWithScaledQuantity() = runTest {
        val (veggieRepo, userRepo, _) = setupTestFixture()
        val newUser = User(id = "user-2", name = "Maria", isActive = true)
        userRepo.saveUser(newUser)
        val product = veggieRepo.products.first()

        val vm = AdminVeggieViewModel(veggieOrderRepository = veggieRepo, userRepository = userRepo, coroutineScope = this)
        advanceUntilIdle()

        vm.addOrUpdateCustomerEntry(user = newUser, product = product, quantityInput = "2.5")
        advanceUntilIdle()

        val created = veggieRepo.entries.find { it.userId == "user-2" && it.veggieProductId == product.id && it.roundId == "round-active" }
        assertNotNull(created)
        assertEquals(2500L, created.orderedQuantityScaled)
        assertEquals(2500L, created.actualQuantityScaled)
    }

    @Test
    fun testAddOrUpdateCustomerEntry_existingEntry_overwriteMode_replacesQuantity() = runTest {
        val (veggieRepo, userRepo, testUser) = setupTestFixture()
        val product = veggieRepo.products.first()

        val vm = AdminVeggieViewModel(veggieOrderRepository = veggieRepo, userRepository = userRepo, coroutineScope = this)
        advanceUntilIdle()

        // Existing entry is 1000L. Overwrite with 3.5
        vm.addOrUpdateCustomerEntry(
            user = testUser,
            product = product,
            quantityInput = "3.5",
            mode = AddOrderEntryMode.OVERWRITE
        )
        advanceUntilIdle()

        val updated = veggieRepo.entries.find { it.userId == testUser.id && it.veggieProductId == product.id && it.roundId == "round-active" }
        assertNotNull(updated)
        assertEquals(3500L, updated.orderedQuantityScaled)
        assertEquals(3500L, updated.actualQuantityScaled)
    }

    @Test
    fun testAddOrUpdateCustomerEntry_existingEntry_addMode_sumsQuantities() = runTest {
        val (veggieRepo, userRepo, testUser) = setupTestFixture()
        val product = veggieRepo.products.first()

        val vm = AdminVeggieViewModel(veggieOrderRepository = veggieRepo, userRepository = userRepo, coroutineScope = this)
        advanceUntilIdle()

        // Existing entry is 1000L. Add 2.0 => 3000L
        vm.addOrUpdateCustomerEntry(
            user = testUser,
            product = product,
            quantityInput = "2.0",
            mode = AddOrderEntryMode.ADD
        )
        advanceUntilIdle()

        val updated = veggieRepo.entries.find { it.userId == testUser.id && it.veggieProductId == product.id && it.roundId == "round-active" }
        assertNotNull(updated)
        assertEquals(3000L, updated.orderedQuantityScaled)
        assertEquals(3000L, updated.actualQuantityScaled)
    }

    @Test
    fun testAddOrUpdateCustomerEntry_closedRound_preservesOrderedQuantityAndUpdatesActual() = runTest {
        val (veggieRepo, userRepo, testUser) = setupTestFixture()
        val product = veggieRepo.products.first()

        // Close the round
        val activeRound = veggieRepo.rounds.first { it.id == "round-active" }
        veggieRepo.rounds[veggieRepo.rounds.indexOf(activeRound)] = activeRound.copy(status = VeggieRoundStatus.CLOSED)

        val vm = AdminVeggieViewModel(veggieOrderRepository = veggieRepo, userRepository = userRepo, coroutineScope = this)
        advanceUntilIdle()

        // Existing entry was 1000L ordered and 1000L actual. In closed round, adding 1.5 should keep ordered at 1000L and set actual to 2500L
        vm.addOrUpdateCustomerEntry(
            user = testUser,
            product = product,
            quantityInput = "1.5",
            mode = AddOrderEntryMode.ADD
        )
        advanceUntilIdle()

        val updated = veggieRepo.entries.find { it.userId == testUser.id && it.veggieProductId == product.id && it.roundId == "round-active" }
        assertNotNull(updated)
        assertEquals(1000L, updated.orderedQuantityScaled)
        assertEquals(2500L, updated.actualQuantityScaled)
    }

    @Test
    fun testSaveUnitType_editingExistingUnit_updatesExistingWithoutCreatingDuplicate() = runTest {
        val (veggieRepo, userRepo, _) = setupTestFixture()
        val existingUnit = VeggieUnitType(id = "unit-pack", name = "Pack", allowDecimals = false, isActive = true)
        val inactiveUnit = VeggieUnitType(id = "unit-old-pacote", name = "Pacote", allowDecimals = false, isActive = false)
        veggieRepo.unitTypes.add(existingUnit)
        veggieRepo.unitTypes.add(inactiveUnit)

        val vm = AdminVeggieViewModel(veggieOrderRepository = veggieRepo, userRepository = userRepo, coroutineScope = this)
        advanceUntilIdle()

        // Edit existing unit from "Pack" to "Pacote"
        vm.saveUnitType(existingUnit.copy(name = "Pacote"))
        advanceUntilIdle()

        // Existing unit should have its name updated and keep its id
        val updated = veggieRepo.unitTypes.find { it.id == "unit-pack" }
        assertNotNull(updated)
        assertEquals("Pacote", updated.name)

        // Inactive unit should still exist unchanged and not have hijacked the edit
        val inactive = veggieRepo.unitTypes.find { it.id == "unit-old-pacote" }
        assertNotNull(inactive)
        assertFalse(inactive.isActive)
    }

    @Test
    fun testSaveUnitType_addingNewUnitWithSameNameAsInactive_reactivatesInactive() = runTest {
        val (veggieRepo, userRepo, _) = setupTestFixture()
        val inactiveUnit = VeggieUnitType(id = "unit-old-box", name = "Box", allowDecimals = false, isActive = false)
        veggieRepo.unitTypes.add(inactiveUnit)

        val vm = AdminVeggieViewModel(veggieOrderRepository = veggieRepo, userRepository = userRepo, coroutineScope = this)
        advanceUntilIdle()

        // Add brand new unit with name "Box" (id is generated or different)
        val newUnit = VeggieUnitType(id = "new-temp-id", name = "Box", allowDecimals = true, isActive = true)
        vm.saveUnitType(newUnit)
        advanceUntilIdle()

        // It should have reactivated the inactive unit with id "unit-old-box" rather than creating a duplicate
        val reactivated = veggieRepo.unitTypes.find { it.id == "unit-old-box" }
        assertNotNull(reactivated)
        assertTrue(reactivated.isActive)
        assertTrue(reactivated.allowDecimals)
    }

    @Test
    fun testSaveVeggieProduct_passesProductNameToImageSaverAndUpdatesFileName() = runTest {
        val (veggieRepo, userRepo, _) = setupTestFixture()
        var receivedProductName: String? = null
        var receivedProductId: String? = null

        val vm = AdminVeggieViewModel(
            veggieOrderRepository = veggieRepo,
            userRepository = userRepo,
            onSaveProductImage = { prodId, prodName, bytes, prevFileName ->
                receivedProductId = prodId
                receivedProductName = prodName
                "tomate_italiano"
            },
            coroutineScope = this
        )
        advanceUntilIdle()

        val product = veggieRepo.products.first().copy(name = "Tomate Italiano")
        vm.saveVeggieProduct(product, newImageBytes = byteArrayOf(1, 2, 3), removeExistingImage = false)
        advanceUntilIdle()

        assertEquals(product.id, receivedProductId)
        assertEquals("Tomate Italiano", receivedProductName)

        val updatedProduct = veggieRepo.products.find { it.id == product.id }
        assertNotNull(updatedProduct)
        assertEquals("tomate_italiano", updatedProduct.imageFileName)
    }

    @Test
    fun testDualUnitProduct_preservesDecimalActualQuantityAndCalculatesSettlement() = runTest {
        val (veggieRepo, userRepo, testUser) = setupTestFixture()
        // Watermelon: Customer Unit = unit-piece (integers), Settlement Unit = unit-kg (decimals)
        val unitKg = VeggieUnitType(id = "unit-kg", name = "kg", allowDecimals = true, isActive = true)
        val unitPiece = VeggieUnitType(id = "unit-piece", name = "Unit", allowDecimals = false, isActive = true)
        veggieRepo.unitTypes.clear()
        veggieRepo.unitTypes.addAll(listOf(unitKg, unitPiece))

        val watermelon = VeggieProduct(
            id = "prod-watermelon",
            name = "Watermelon",
            unitTypeId = "unit-piece",
            deliveryUnitTypeId = "unit-kg",
            isActive = true
        )
        veggieRepo.products.clear()
        veggieRepo.products.add(watermelon)

        val entry = VeggieOrderEntry(
            id = "entry-wm",
            roundId = "round-active",
            userId = testUser.id,
            userNameSnapshot = testUser.name,
            veggieProductId = watermelon.id,
            veggieNameSnapshot = watermelon.name,
            unitNameSnapshot = "Unit",
            allowDecimalsSnapshot = false,
            deliveryUnitNameSnapshot = "kg",
            deliveryAllowDecimalsSnapshot = true,
            orderedQuantityScaled = 1000L, // 1 Unit
            actualQuantityScaled = null
        )
        veggieRepo.entries.clear()
        veggieRepo.entries.add(entry)

        val vm = AdminVeggieViewModel(veggieOrderRepository = veggieRepo, userRepository = userRepo, coroutineScope = this)
        advanceUntilIdle()

        // Admin enters 3.2 kg delivered for the entry
        vm.updateEntryActualQuantity(entryId = "entry-wm", input = "3.2")
        vm.updateProductTotalSpent(productId = "prod-watermelon", input = "16,00")
        advanceUntilIdle()

        val preview = vm.liveSettlementPreview.value
        assertNotNull(preview)
        val updatedEntry = preview.updatedEntries.find { it.id == "entry-wm" }
        assertNotNull(updatedEntry)
        assertEquals(3200L, updatedEntry.actualQuantityScaled) // 3.2 kg preserved!
        assertEquals("kg", updatedEntry.effectiveDeliveryUnitName)
        assertEquals(1600L, updatedEntry.finalItemTotalCents) // Full spent attributed
        assertEquals(500L, updatedEntry.effectiveUnitPriceCents) // 16.00 / 3.2 = 5.00/kg
    }

    @Test
    fun testToggleVeggieProductActive_stopNewOrdersOnly_vs_cancelExistingOrders() = runTest {
        val (veggieRepo, userRepo) = setupTestFixture()
        val vm = AdminVeggieViewModel(veggieOrderRepository = veggieRepo, userRepository = userRepo, coroutineScope = this)
        advanceUntilIdle()

        val product = veggieRepo.products.first()
        assertTrue(product.isActive)
        assertEquals(1, vm.selectedRoundEntries.value.size)

        // 1. Stop new orders only -> product deactivated, existing entry preserved
        vm.toggleVeggieProductActive(product, cancelOpenRoundOrders = false)
        advanceUntilIdle()
        assertFalse(veggieRepo.products.first().isActive)
        assertEquals(1, vm.selectedRoundEntries.value.size)

        // Reactivate
        vm.toggleVeggieProductActive(veggieRepo.products.first())
        advanceUntilIdle()
        assertTrue(veggieRepo.products.first().isActive)

        // 2. Cancel existing orders too -> product deactivated AND entry removed from open round
        vm.toggleVeggieProductActive(veggieRepo.products.first(), cancelOpenRoundOrders = true)
        advanceUntilIdle()
        assertFalse(veggieRepo.products.first().isActive)
        assertEquals(0, vm.selectedRoundEntries.value.size)
    }

    @Test
    fun testEmptyRoundCannotBeClosedSavedOrSettled_onlyDeleted() = runTest {
        val (veggieRepo, userRepo) = setupTestFixture()
        veggieRepo.entries.removeAll { it.roundId == "round-active" }

        val vm = AdminVeggieViewModel(veggieOrderRepository = veggieRepo, userRepository = userRepo, coroutineScope = this)
        advanceUntilIdle()

        assertEquals(0, vm.selectedRoundEntries.value.size)
        assertEquals(VeggieRoundStatus.OPEN, vm.activeRound.value?.status)

        vm.closeSelectedRound()
        advanceUntilIdle()
        assertEquals(VeggieRoundStatus.OPEN, vm.activeRound.value?.status)

        var saveCallbackCalled = false
        vm.updateBuyerFeeInput("10,00")
        vm.saveDraftSettlement(onSuccess = { saveCallbackCalled = true })
        advanceUntilIdle()
        assertFalse(saveCallbackCalled)
        assertEquals(400L, veggieRepo.rounds.first { it.id == "round-active" }.buyerOperationFeeCents)

        vm.executeSettlement()
        advanceUntilIdle()
        assertEquals(VeggieRoundStatus.OPEN, vm.activeRound.value?.status)

        vm.deleteCurrentRound()
        advanceUntilIdle()
        assertNull(vm.activeRound.value)
        assertFalse(veggieRepo.rounds.any { it.id == "round-active" })
    }

    @Test
    fun settledRound_addOrderEntry_stagesUntilExecuteSettlementAndDiscardsCleanly() = runTest {
        val userA = User(id = "u-a", name = "Alice", balance = 10000L)
        val userB = User(id = "u-b", name = "Bob", balance = 10000L)
        val unitKg = VeggieUnitType(id = "unit-kg", name = "kg", allowDecimals = true, isActive = true)
        val prodTomato = VeggieProduct(id = "prod-t", name = "Tomato", unitTypeId = "unit-kg", estimatedPriceCents = 1000L, isActive = true)
        val settledRound = VeggieOrderRound(
            id = "round-settled",
            status = VeggieRoundStatus.SETTLED,
            buyerOperationFeeCents = 0L,
            transportFeeCents = 0L,
            openedAt = 1000L,
            closedAt = 2000L,
            settledAt = 3000L
        )
        val entryA = VeggieOrderEntry(
            id = "e-a",
            roundId = "round-settled",
            userId = "u-a",
            userNameSnapshot = "Alice",
            veggieProductId = "prod-t",
            veggieNameSnapshot = "Tomato",
            unitNameSnapshot = "kg",
            allowDecimalsSnapshot = true,
            orderedQuantityScaled = 1000L,
            actualQuantityScaled = 1000L,
            effectiveUnitPriceCents = 1000L,
            finalItemTotalCents = 1000L
        )

        val veggieRepo = FakeVeggieRepository(
            unitTypes = mutableListOf(unitKg),
            products = mutableListOf(prodTomato),
            rounds = mutableListOf(settledRound),
            entries = mutableListOf(entryA),
            settlements = mutableListOf(
                VeggieRoundProductSettlement(
                    roundId = "round-settled",
                    veggieProductId = "prod-t",
                    marketUnitPriceCents = 1000L,
                    totalSpentCents = 1000L
                )
            )
        )
        val vm = AdminVeggieViewModel(
            veggieOrderRepository = veggieRepo,
            userRepository = FakeUserRepository(mutableListOf(userA, userB)),
            coroutineScope = this
        )
        advanceUntilIdle()

        vm.openPastRoundForEditing("round-settled")
        advanceUntilIdle()

        // Adding an entry on a settled round must NOT write to repository immediately
        vm.addOrUpdateCustomerEntry(userB, prodTomato, "2", AddOrderEntryMode.OVERWRITE)
        advanceUntilIdle()

        assertTrue(vm.hasUnsavedSettlementChanges.value)
        assertEquals(1, veggieRepo.entries.size)
        assertEquals(2, vm.selectedRoundEntries.value.size)

        // Discarding reverts the staged entry without touching the repository
        vm.discardDraft()
        advanceUntilIdle()
        assertFalse(vm.hasUnsavedSettlementChanges.value)
        assertEquals(1, veggieRepo.entries.size)
        assertEquals(1, vm.selectedRoundEntries.value.size)

        // Re-adding and executing settlement flushes the staged entry
        vm.addOrUpdateCustomerEntry(userB, prodTomato, "2", AddOrderEntryMode.OVERWRITE)
        advanceUntilIdle()
        vm.executeSettlement()
        advanceUntilIdle()

        assertEquals(2, veggieRepo.entries.size)
        assertTrue(veggieRepo.entries.any { it.userId == "u-b" && it.actualQuantityScaled == 2000L })
    }

    @Test
    fun executeSettlement_rejectsMalformedDraftInputs() = runTest {
        val userA = User(id = "u-a", name = "Alice", balance = 10000L)
        val unitKg = VeggieUnitType(id = "unit-kg", name = "kg", allowDecimals = true, isActive = true)
        val prodTomato = VeggieProduct(id = "prod-t", name = "Tomato", unitTypeId = "unit-kg", estimatedPriceCents = 1000L, isActive = true)
        val settledRound = VeggieOrderRound(
            id = "round-settled",
            status = VeggieRoundStatus.SETTLED,
            openedAt = 1000L,
            closedAt = 2000L,
            settledAt = 3000L
        )
        val entryA = VeggieOrderEntry(
            id = "e-a",
            roundId = "round-settled",
            userId = "u-a",
            userNameSnapshot = "Alice",
            veggieProductId = "prod-t",
            veggieNameSnapshot = "Tomato",
            unitNameSnapshot = "kg",
            allowDecimalsSnapshot = true,
            orderedQuantityScaled = 1000L,
            actualQuantityScaled = 1000L
        )
        val veggieRepo = FakeVeggieRepository(
            unitTypes = mutableListOf(unitKg),
            products = mutableListOf(prodTomato),
            rounds = mutableListOf(settledRound),
            entries = mutableListOf(entryA)
        )
        val vm = AdminVeggieViewModel(
            veggieOrderRepository = veggieRepo,
            userRepository = FakeUserRepository(mutableListOf(userA)),
            coroutineScope = this
        )
        advanceUntilIdle()

        vm.openPastRoundForEditing("round-settled")
        advanceUntilIdle()

        vm.updateEntryActualQuantity("e-a", "1..5")
        vm.executeSettlement()
        advanceUntilIdle()

        assertNotNull(vm.errorMessage.value)
        assertEquals("round-settled", vm.editingPastRound.value?.id)
    }
}
