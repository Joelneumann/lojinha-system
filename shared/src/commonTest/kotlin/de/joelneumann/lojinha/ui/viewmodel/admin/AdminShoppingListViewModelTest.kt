package de.joelneumann.lojinha.ui.viewmodel.admin

import de.joelneumann.lojinha.domain.model.*
import de.joelneumann.lojinha.domain.repository.AdminNoteRepository
import de.joelneumann.lojinha.domain.repository.ProductRepository
import de.joelneumann.lojinha.domain.repository.ShoppingListRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AdminShoppingListViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeShoppingListRepository : ShoppingListRepository {
        val itemsFlow = MutableStateFlow<List<ShoppingListItem>>(emptyList())

        override fun getItemsFlow(): Flow<List<ShoppingListItem>> = itemsFlow
        override suspend fun getAllItems(): List<ShoppingListItem> = itemsFlow.value

        override suspend fun addOrUpdateItem(item: ShoppingListItem) {
            val existing = itemsFlow.value.find {
                (item.id.isNotBlank() && it.id == item.id) ||
                    (item.productId != null && it.productId == item.productId)
            }
            itemsFlow.value = if (existing != null) {
                itemsFlow.value.map {
                    if (it.id == existing.id) it.copy(name = item.name, quantity = item.quantity, isConfirmedForCurrentExport = true) else it
                }
            } else {
                itemsFlow.value + item
            }
        }

        override suspend fun updateItem(item: ShoppingListItem) {
            itemsFlow.value = itemsFlow.value.map { if (it.id == item.id) item else it }
        }

        override suspend fun confirmItem(id: String) {
            itemsFlow.value = itemsFlow.value.map { if (it.id == id) it.copy(isConfirmedForCurrentExport = true) else it }
        }

        override suspend fun unconfirmItem(id: String) {
            itemsFlow.value = itemsFlow.value.map { if (it.id == id) it.copy(isConfirmedForCurrentExport = false) else it }
        }

        override suspend fun updateItemsOrder(items: List<ShoppingListItem>) {
            itemsFlow.value = items.mapIndexed { idx, item -> item.copy(sortOrder = idx) }
        }

        override suspend fun deleteItem(id: String) {
            itemsFlow.value = itemsFlow.value.filter { it.id != id }
        }

        override suspend fun clearAllItems() {
            itemsFlow.value = emptyList()
        }

        override suspend fun commitExportBaseline() {
            itemsFlow.value = itemsFlow.value.map {
                it.copy(lastExportedName = it.name, lastExportedQuantity = it.quantity, isConfirmedForCurrentExport = false)
            }
        }
    }

    private class FakeProductRepository(products: List<Product> = emptyList()) : ProductRepository {
        val productsFlow = MutableStateFlow(products)
        override fun getProductsFlow(): Flow<List<Product>> = productsFlow
        override suspend fun getAllProducts(): List<Product> = productsFlow.value
        override suspend fun getProductById(id: String): Product? = productsFlow.value.find { it.id == id }
        override suspend fun getProductByBarcode(barcode: String): Product? = null
        override suspend fun saveProduct(product: Product) {}
        override suspend fun deactivateProduct(id: String) {}
        override suspend fun hardDeleteProduct(id: String) {}
        override suspend fun updateStock(productId: String, delta: Long) {}
        override suspend fun getProductHistory(productId: String, page: Int, pageSize: Int) = PagedResult<ProductHistoryEntry>(emptyList(), 0, page, pageSize, 0)
        override suspend fun recordStockAdjustment(adjustment: ProductStockAdjustment) {}
    }

    private class FakeAdminNoteRepository(notes: List<AdminNote> = emptyList()) : AdminNoteRepository {
        val notesFlow = MutableStateFlow(notes)
        val deletedIds = mutableListOf<String>()
        override fun getNotesFlow(): Flow<List<AdminNote>> = notesFlow
        override suspend fun getAllNotes(): List<AdminNote> = notesFlow.value
        override suspend fun addNote(note: AdminNote) {}
        override suspend fun deleteNote(id: String) {
            deletedIds.add(id)
            notesFlow.value = notesFlow.value.filter { it.id != id }
        }
        override suspend fun deleteAllNotes() {}
    }

    @Test
    fun testRequestAddFromStockZeroNoteAndDeleteNote() = runTest {
        val clubMate = Product(id = "p-1", name = "Club Mate", basePrice = 500L, unitType = UnitType.PIECE, stockQuantity = 0L)
        val note = AdminNote(id = "n-1", message = "SYSNOTE|STOCK_ZERO|Club Mate|Alice", userId = "SYSTEM", userNameSnapshot = "System")
        val shoppingRepo = FakeShoppingListRepository()
        val productRepo = FakeProductRepository(listOf(clubMate))
        val noteRepo = FakeAdminNoteRepository(listOf(note))

        val vm = AdminShoppingListViewModel(shoppingRepo, productRepo, noteRepo)
        vm.requestAddFromStockZeroNote("Club Mate", "n-1")

        val modal = vm.pendingModalRequest.value
        assertNotNull(modal)
        assertEquals("p-1", modal.product?.id)
        assertEquals("n-1", modal.sourceNoteId)
        assertNull(modal.existingItem)

        val added = vm.confirmModalAdd(
            name = modal.customName,
            rawQuantityInput = "12",
            product = modal.product,
            sourceNoteId = modal.sourceNoteId,
            existingItem = modal.existingItem,
            removeNote = true
        )
        assertTrue(added)
        assertNull(vm.pendingModalRequest.value)
        assertTrue(noteRepo.deletedIds.contains("n-1"))
        assertEquals(1, vm.items.value.size)
        assertEquals("Club Mate", vm.items.value[0].name)
    }

    @Test
    fun testOverwriteNoticeWhenProductAlreadyExists() = runTest {
        val bananas = Product(id = "p-2", name = "Bananas", basePrice = 300L, unitType = UnitType.WEIGHT, stockQuantity = 1000L)
        val shoppingRepo = FakeShoppingListRepository()
        val productRepo = FakeProductRepository(listOf(bananas))
        val vm = AdminShoppingListViewModel(shoppingRepo, productRepo)

        vm.requestAddProduct(bananas)
        vm.confirmModalAdd("Bananas", "2,5", bananas, null, null, removeNote = false)

        // Request adding Bananas again -> existingItem should be populated with normalized numeric quantity
        vm.requestAddProduct(bananas)
        val secondModal = vm.pendingModalRequest.value
        assertNotNull(secondModal)
        assertNotNull(secondModal.existingItem)
        assertEquals("2,5", secondModal.existingItem?.quantity)
    }

    @Test
    fun testRankedSearchAndCustomForceAndQuantityFilter() = runTest {
        val iceTea = Product(id = "p-1", name = "Mate Ice Tea", basePrice = 400L, unitType = UnitType.PIECE, stockQuantity = 5L)
        val clubMate = Product(id = "p-2", name = "Club Mate", basePrice = 500L, unitType = UnitType.PIECE, stockQuantity = 5L)
        val mate = Product(id = "p-3", name = "Mate", basePrice = 300L, unitType = UnitType.WEIGHT, stockQuantity = 1000L)

        val vm = AdminShoppingListViewModel(
            FakeShoppingListRepository(),
            FakeProductRepository(listOf(clubMate, iceTea, mate))
        )

        // Exact match "Mate" -> Tier 0, "Mate Ice Tea" starts with -> Tier 1, "Club Mate" word starts -> Tier 2
        val ranked = vm.getMatchingProducts("Mate")
        assertEquals(listOf("Mate", "Mate Ice Tea", "Club Mate"), ranked.map { it.name })

        // Force custom item even when "Mate" exists in catalog (Search -> Enter workflow)
        vm.requestAddCustomItem("Mate", forceCustom = true)
        val customModal = vm.pendingModalRequest.value
        assertNotNull(customModal)
        assertNull(customModal.product)
        assertEquals("Mate", customModal.customName)

        // Quantity character filtering by unit type
        assertTrue(AdminShoppingListViewModel.isAllowedQuantityText("123", UnitType.PIECE))
        assertFalse(AdminShoppingListViewModel.isAllowedQuantityText("12.5", UnitType.PIECE))
        assertFalse(AdminShoppingListViewModel.isAllowedQuantityText("12a", UnitType.PIECE))

        assertTrue(AdminShoppingListViewModel.isAllowedQuantityText("2,5", UnitType.WEIGHT))
        assertTrue(AdminShoppingListViewModel.isAllowedQuantityText("2.5", UnitType.WEIGHT))
        assertFalse(AdminShoppingListViewModel.isAllowedQuantityText("2,5.1", UnitType.WEIGHT))
        assertFalse(AdminShoppingListViewModel.isAllowedQuantityText("2kg", UnitType.WEIGHT))

        assertTrue(AdminShoppingListViewModel.isAllowedQuantityText("2 packs", null))
    }

    @Test
    fun testMoveItemAndConfirmItemPreservesStatusProperly() = runTest {
        val shoppingRepo = FakeShoppingListRepository()
        shoppingRepo.itemsFlow.value = listOf(
            ShoppingListItem(id = "1", name = "A", quantity = "1", sortOrder = 0, lastExportedName = "A", lastExportedQuantity = "1", isConfirmedForCurrentExport = false),
            ShoppingListItem(id = "2", name = "B", quantity = "2", sortOrder = 1, lastExportedName = "B", lastExportedQuantity = "2", isConfirmedForCurrentExport = false)
        )
        val vm = AdminShoppingListViewModel(shoppingRepo, FakeProductRepository())

        assertTrue(vm.items.value.all { it.isPendingReview })

        vm.moveItem(0, 1)
        vm.persistCurrentOrder()

        assertEquals("B", vm.items.value[0].name)
        assertEquals("A", vm.items.value[1].name)
        assertTrue(vm.items.value.all { it.isPendingReview })

        vm.confirmItem("1")
        assertFalse(vm.items.value.first { it.id == "1" }.isPendingReview)
        assertTrue(vm.items.value.first { it.id == "2" }.isPendingReview)

        // Focus-loss without text changes (even with legacy unit suffix in baseline) must NOT mark item edited
        val milkWithLegacy = Product(id = "p-milk", name = "Milk", basePrice = 100L, unitType = UnitType.PIECE)
        val repo2 = FakeShoppingListRepository()
        repo2.itemsFlow.value = listOf(
            ShoppingListItem(id = "m1", productId = "p-milk", name = "Milk", quantity = "10 Stk.", sortOrder = 0, lastExportedName = "Milk", lastExportedQuantity = "10 Stk.", isConfirmedForCurrentExport = false)
        )
        val vm2 = AdminShoppingListViewModel(repo2, FakeProductRepository(listOf(milkWithLegacy)))
        vm2.updateItemInPlace("m1", "Milk", "10", confirm = false)
        assertTrue(vm2.items.value.first().isPendingReview, "Unchanged quantity on blur must remain unadjusted")
    }

    @Test
    fun testUnconfirmWorkflowForConfirmedEditedAndNewItems() = runTest {
        val shoppingRepo = FakeShoppingListRepository()
        shoppingRepo.itemsFlow.value = listOf(
            ShoppingListItem(
                id = "c1",
                name = "Confirmed Item",
                quantity = "5",
                sortOrder = 0,
                lastExportedName = "Confirmed Item",
                lastExportedQuantity = "5",
                isConfirmedForCurrentExport = true
            ),
            ShoppingListItem(
                id = "e1",
                name = "Edited Item",
                quantity = "8",
                sortOrder = 1,
                lastExportedName = "Edited Item",
                lastExportedQuantity = "4",
                isConfirmedForCurrentExport = true
            ),
            ShoppingListItem(
                id = "n1",
                name = "New Item",
                quantity = "3",
                sortOrder = 2,
                lastExportedName = null,
                lastExportedQuantity = null,
                isConfirmedForCurrentExport = true
            )
        )
        val vm = AdminShoppingListViewModel(shoppingRepo, FakeProductRepository())

        // 1. Unconfirm Confirmed -> moves to Unadjusted (isPendingReview == true)
        vm.unconfirmItem("c1")
        val c1AfterUnconfirm = vm.items.value.first { it.id == "c1" }
        assertTrue(c1AfterUnconfirm.isPendingReview)
        assertFalse(c1AfterUnconfirm.isConfirmedForCurrentExport)

        // 2. Unconfirm Edited -> keeps edited text while moving to Unadjusted; re-confirming restores Edited
        vm.unconfirmItem("e1")
        val e1AfterUnconfirm = vm.items.value.first { it.id == "e1" }
        assertEquals("8", e1AfterUnconfirm.quantity)
        assertTrue(e1AfterUnconfirm.isPendingReview)
        assertTrue(e1AfterUnconfirm.isContentChanged)

        vm.confirmItem("e1")
        val e1AfterReconfirm = vm.items.value.first { it.id == "e1" }
        assertTrue(e1AfterReconfirm.isReviewedForExport)
        assertTrue(e1AfterReconfirm.isContentChanged)

        // 3. Attempting to unconfirm New item is a no-op
        vm.unconfirmItem("n1")
        val n1After = vm.items.value.first { it.id == "n1" }
        assertTrue(n1After.isNewlyAdded)
        assertFalse(n1After.isPendingReview)
    }
}
