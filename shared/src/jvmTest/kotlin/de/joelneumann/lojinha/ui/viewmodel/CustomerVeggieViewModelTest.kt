package de.joelneumann.lojinha.ui.viewmodel

import de.joelneumann.lojinha.domain.model.*
import de.joelneumann.lojinha.domain.repository.CustomerVeggieRoundHistoryItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CustomerVeggieViewModelTest {

    private fun createViewModel(
        initialFavorites: Set<String> = emptySet(),
        onFavoritesChanged: ((Set<String>) -> Unit)? = null,
        testScope: TestScope
    ): CustomerVeggieViewModel {
        val fakeRound = VeggieOrderRound(
            id = "round-1",
            openedAt = 1731150000000L,
            buyerOperationFeeCents = 300L,
            transportFeeCents = 600L,
            status = VeggieRoundStatus.OPEN
        )
        val fakeProduct = VeggieProduct(
            id = "prod-1",
            name = "Organic Carrots",
            unitTypeId = "unit-kg",
            estimatedPriceCents = 500L
        )
        val fakeUnitType = VeggieUnitType(
            id = "unit-kg",
            name = "kg",
            allowDecimals = true
        )

        return CustomerVeggieViewModel(
            onFetchVeggieState = {
                CustomerVeggieStateResponse(
                    activeRound = fakeRound,
                    unitTypes = listOf(fakeUnitType),
                    products = listOf(fakeProduct),
                    currentEntries = emptyList(),
                    previousRoundQuantities = emptyMap(),
                    favoriteProductIds = initialFavorites
                )
            },
            onSaveOrder = { _, _ -> true },
            onFetchHistory = { emptyList() },
            initialFavoriteProductIds = initialFavorites,
            onFavoritesChanged = onFavoritesChanged,
            coroutineScope = testScope
        )
    }

    @Test
    fun testInitialFavoritesLoaded() = runTest {
        val initial = setOf("prod-1", "prod-2")
        val vm = createViewModel(initialFavorites = initial, testScope = this)
        advanceUntilIdle()

        assertEquals(initial, vm.favoriteProductIds.value)
        assertTrue(vm.isFavorite("prod-1"))
        assertTrue(vm.isFavorite("prod-2"))
        assertFalse(vm.isFavorite("prod-3"))
    }

    @Test
    fun testToggleFavoriteAddsAndRemoves() = runTest {
        var savedFavorites: Set<String>? = null
        val vm = createViewModel(
            initialFavorites = emptySet(),
            onFavoritesChanged = { savedFavorites = it },
            testScope = this
        )
        advanceUntilIdle()

        // Toggle on
        vm.toggleFavorite("prod-1")
        assertEquals(setOf("prod-1"), vm.favoriteProductIds.value)
        assertEquals(setOf("prod-1"), savedFavorites)
        assertTrue(vm.isFavorite("prod-1"))

        // Add second
        vm.toggleFavorite("prod-2")
        assertEquals(setOf("prod-1", "prod-2"), vm.favoriteProductIds.value)
        assertEquals(setOf("prod-1", "prod-2"), savedFavorites)

        // Toggle off first
        vm.toggleFavorite("prod-1")
        assertEquals(setOf("prod-2"), vm.favoriteProductIds.value)
        assertEquals(setOf("prod-2"), savedFavorites)
        assertFalse(vm.isFavorite("prod-1"))
        assertTrue(vm.isFavorite("prod-2"))
    }

    @Test
    fun testToggleFilterFavoritesOnly() = runTest {
        val vm = createViewModel(testScope = this)
        advanceUntilIdle()

        assertFalse(vm.filterFavoritesOnly.value)

        vm.toggleFilterFavoritesOnly()
        assertTrue(vm.filterFavoritesOnly.value)

        vm.toggleFilterFavoritesOnly()
        assertFalse(vm.filterFavoritesOnly.value)

        vm.setFilterFavoritesOnly(true)
        assertTrue(vm.filterFavoritesOnly.value)
    }

    @Test
    fun testSetFavoriteProductIdsReplacesSet() = runTest {
        var notified: Set<String>? = null
        val vm = createViewModel(
            initialFavorites = setOf("old-1"),
            onFavoritesChanged = { notified = it },
            testScope = this
        )
        advanceUntilIdle()

        vm.setFavoriteProductIds(setOf("new-1", "new-2"))
        assertEquals(setOf("new-1", "new-2"), vm.favoriteProductIds.value)
        assertEquals(setOf("new-1", "new-2"), notified)
    }

    @Test
    fun testHistoryPaginationAndSearchReset() = runTest {
        val vm = createViewModel(testScope = this)
        advanceUntilIdle()

        assertEquals(0, vm.historyPage.value)
        assertEquals(10, vm.historyPageSize.value)

        vm.setHistoryPage(2)
        assertEquals(2, vm.historyPage.value)

        vm.setHistoryPageSize(25)
        assertEquals(25, vm.historyPageSize.value)
        assertEquals(0, vm.historyPage.value)

        vm.setHistoryPage(3)
        assertEquals(3, vm.historyPage.value)

        vm.updateHistorySearchQuery("tomato")
        assertEquals("tomato", vm.historySearchQuery.value)
        assertEquals(0, vm.historyPage.value)
    }

    @Test
    fun testToggleFilterInOrderOnly() = runTest {
        val vm = createViewModel(testScope = this)
        advanceUntilIdle()

        assertFalse(vm.filterInOrderOnly.value)

        vm.toggleFilterInOrderOnly()
        assertTrue(vm.filterInOrderOnly.value)

        vm.toggleFilterInOrderOnly()
        assertFalse(vm.filterInOrderOnly.value)

        vm.setFilterInOrderOnly(true)
        assertTrue(vm.filterInOrderOnly.value)
    }

    @Test
    fun testInactiveOrderedProductExposedAndIncludedInSubtotal() = runTest {
        val fakeRound = VeggieOrderRound(
            id = "round-1",
            openedAt = 1731150000000L,
            status = VeggieRoundStatus.OPEN
        )
        val activeProd = VeggieProduct(
            id = "prod-active",
            name = "Carrots",
            unitTypeId = "unit-kg",
            estimatedPriceCents = 500L,
            isActive = true
        )
        val inactiveProd = VeggieProduct(
            id = "prod-inactive",
            name = "Strawberries",
            unitTypeId = "unit-kg",
            estimatedPriceCents = 1200L,
            isActive = false
        )
        val unitKg = VeggieUnitType(id = "unit-kg", name = "kg", allowDecimals = true)
        val entryInactive = VeggieOrderEntry(
            id = "entry-1",
            roundId = "round-1",
            userId = "user-1",
            userNameSnapshot = "Alice",
            veggieProductId = "prod-inactive",
            veggieNameSnapshot = "Strawberries",
            unitNameSnapshot = "kg",
            allowDecimalsSnapshot = true,
            orderedQuantityScaled = 2000L
        )

        val vm = CustomerVeggieViewModel(
            onFetchVeggieState = {
                CustomerVeggieStateResponse(
                    activeRound = fakeRound,
                    unitTypes = listOf(unitKg),
                    products = listOf(activeProd, inactiveProd),
                    currentEntries = listOf(entryInactive),
                    previousRoundQuantities = mapOf("prod-inactive" to 1000L, "prod-active" to 1000L)
                )
            },
            onSaveOrder = { _, _ -> true },
            onFetchHistory = { emptyList() },
            coroutineScope = this
        )
        advanceUntilIdle()

        // Catalog only has active product; inactiveOrderedProducts has the ordered inactive product
        assertEquals(listOf("prod-active"), vm.products.value.map { it.id })
        assertEquals(listOf("prod-inactive"), vm.inactiveOrderedProducts.value.map { it.id })

        // Subtotal includes 2 kg * 1200 cents = 2400 cents for the inactive ordered product
        assertEquals(2400L, vm.calculateEstimatedItemsSubtotalCents())

        // Copying previous round order still excludes inactive products
        vm.copyPreviousRoundOrder()
        assertEquals(mapOf("prod-active" to 1000L), vm.draftQuantitiesScaled.value)
    }

    @Test
    fun testFavoritesSyncFromServerStateAndInvokeOnSaveFavorites() = runTest {
        var serverFavorites = setOf("prod-1")
        val savedBatches = mutableListOf<Set<String>>()

        val vm = CustomerVeggieViewModel(
            onFetchVeggieState = {
                CustomerVeggieStateResponse(
                    favoriteProductIds = serverFavorites
                )
            },
            onSaveOrder = { _, _ -> true },
            onFetchHistory = { emptyList() },
            onSaveFavorites = { ids ->
                savedBatches.add(ids)
                serverFavorites = ids
                true
            },
            coroutineScope = this
        )
        advanceUntilIdle()

        assertEquals(setOf("prod-1"), vm.favoriteProductIds.value)

        vm.toggleFavorite("prod-2")
        advanceUntilIdle()
        assertEquals(setOf("prod-1", "prod-2"), vm.favoriteProductIds.value)
        assertEquals(listOf(setOf("prod-1", "prod-2")), savedBatches)

        // Simulate server-side update from another device
        serverFavorites = setOf("prod-3")
        vm.loadData()
        advanceUntilIdle()
        assertEquals(setOf("prod-3"), vm.favoriteProductIds.value)
    }

    @Test
    fun testLocalDraftIsSavedRestoredAndClearedOnSave() = runTest {
        val round = VeggieOrderRound(
            id = "round-draft-1",
            status = VeggieRoundStatus.OPEN,
            openedAt = 1000L
        )
        val unit = VeggieUnitType(id = "u1", name = "kg", allowDecimals = true)
        val prod = VeggieProduct(id = "p1", name = "Tomato", unitTypeId = "u1", estimatedPriceCents = 500L)
        val persistedDrafts = mutableMapOf<String, Map<String, Long>>()

        val vm1 = CustomerVeggieViewModel(
            onFetchVeggieState = {
                CustomerVeggieStateResponse(
                    activeRound = round,
                    unitTypes = listOf(unit),
                    products = listOf(prod)
                )
            },
            onSaveOrder = { _, _ -> false },
            onFetchHistory = { emptyList() },
            onLoadDraft = { rid -> persistedDrafts[rid] },
            onSaveDraft = { rid, qty -> persistedDrafts[rid] = qty },
            onClearDraft = { rid -> persistedDrafts.remove(rid) },
            coroutineScope = this
        )
        advanceUntilIdle()

        vm1.updateProductQuantity("p1", 2500L)
        assertEquals(mapOf("p1" to 2500L), persistedDrafts["round-draft-1"])
        assertTrue(vm1.hasUnsavedOrderChanges.value)

        // Simulate browser reload / new ViewModel instance after Kiosk reconnects
        val vm2 = CustomerVeggieViewModel(
            onFetchVeggieState = {
                CustomerVeggieStateResponse(
                    activeRound = round,
                    unitTypes = listOf(unit),
                    products = listOf(prod)
                )
            },
            onSaveOrder = { _, _ -> true },
            onFetchHistory = { emptyList() },
            onLoadDraft = { rid -> persistedDrafts[rid] },
            onSaveDraft = { rid, qty -> persistedDrafts[rid] = qty },
            onClearDraft = { rid -> persistedDrafts.remove(rid) },
            coroutineScope = this
        )
        advanceUntilIdle()

        assertEquals(mapOf("p1" to 2500L), vm2.draftQuantitiesScaled.value)
        assertTrue(vm2.hasUnsavedOrderChanges.value)

        vm2.saveOrder()
        advanceUntilIdle()

        assertFalse(persistedDrafts.containsKey("round-draft-1"))
        assertFalse(vm2.hasUnsavedOrderChanges.value)
    }

    @Test
    fun testDiscardUnsavedChangesClearsPersistedDraft() = runTest {
        val round = VeggieOrderRound(
            id = "round-discard-1",
            status = VeggieRoundStatus.OPEN,
            openedAt = 1000L
        )
        val unit = VeggieUnitType(id = "u1", name = "kg", allowDecimals = true)
        val prod = VeggieProduct(id = "p1", name = "Tomato", unitTypeId = "u1", estimatedPriceCents = 500L)
        val persistedDrafts = mutableMapOf<String, Map<String, Long>>()

        val vm = CustomerVeggieViewModel(
            onFetchVeggieState = {
                CustomerVeggieStateResponse(
                    activeRound = round,
                    unitTypes = listOf(unit),
                    products = listOf(prod)
                )
            },
            onSaveOrder = { _, _ -> true },
            onFetchHistory = { emptyList() },
            onLoadDraft = { rid -> persistedDrafts[rid] },
            onSaveDraft = { rid, qty -> persistedDrafts[rid] = qty },
            onClearDraft = { rid -> persistedDrafts.remove(rid) },
            coroutineScope = this
        )
        advanceUntilIdle()

        vm.updateProductQuantity("p1", 3000L)
        assertTrue(vm.hasUnsavedOrderChanges.value)
        assertEquals(mapOf("p1" to 3000L), persistedDrafts["round-discard-1"])

        vm.discardUnsavedChanges()
        advanceUntilIdle()

        assertFalse(persistedDrafts.containsKey("round-discard-1"))
        assertFalse(vm.hasUnsavedOrderChanges.value)
        assertTrue(vm.draftQuantitiesScaled.value.isEmpty())
    }
}
