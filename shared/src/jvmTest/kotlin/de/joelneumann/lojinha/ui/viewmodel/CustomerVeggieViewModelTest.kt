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
                    previousRoundQuantities = emptyMap()
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
}
