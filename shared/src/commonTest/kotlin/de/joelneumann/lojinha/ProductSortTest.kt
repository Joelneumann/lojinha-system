package de.joelneumann.lojinha

import de.joelneumann.lojinha.domain.model.Product
import de.joelneumann.lojinha.domain.model.UnitType
import de.joelneumann.lojinha.ui.components.admin.products.ProductSortOption
import de.joelneumann.lojinha.ui.components.admin.products.sortWithOption
import kotlin.test.Test
import kotlin.test.assertEquals

class ProductSortTest {

    private val pAlcool = Product(id = "1", name = "Álcool Cooperalcool 1L", barcodes = emptyList(), basePrice = 850, unitType = UnitType.PIECE, stockQuantity = 20)
    private val pArroz = Product(id = "2", name = "Arroz Branco", barcodes = emptyList(), basePrice = 500, unitType = UnitType.WEIGHT, stockQuantity = 50000)
    private val pBanana = Product(id = "3", name = "Banana Prata", barcodes = emptyList(), basePrice = 300, unitType = UnitType.WEIGHT, stockQuantity = 10000)
    private val pOleo = Product(id = "4", name = "Óleo de Soja Soya 900 ml", barcodes = emptyList(), basePrice = 700, unitType = UnitType.PIECE, stockQuantity = 5)
    private val pXarope = Product(id = "5", name = "Xarope Piaceri Morango 690ml", barcodes = emptyList(), basePrice = 2500, unitType = UnitType.PIECE, stockQuantity = 12)
    private val pYpe = Product(id = "6", name = "Ype Limpador Multiuso", barcodes = emptyList(), basePrice = 450, unitType = UnitType.PIECE, stockQuantity = 30)

    private val allProducts = listOf(pXarope, pYpe, pAlcool, pOleo, pArroz, pBanana)

    @Test
    fun testSortByNameAscending() {
        val sorted = allProducts.sortWithOption(ProductSortOption.NAME_ASC)
        assertEquals(
            listOf(pAlcool, pArroz, pBanana, pOleo, pXarope, pYpe),
            sorted
        )
    }

    @Test
    fun testSortByNameDescending() {
        val sorted = allProducts.sortWithOption(ProductSortOption.NAME_DESC)
        assertEquals(
            listOf(pYpe, pXarope, pOleo, pBanana, pArroz, pAlcool),
            sorted
        )
    }

    @Test
    fun testSortByStockAscending() {
        val sorted = allProducts.sortWithOption(ProductSortOption.STOCK_ASC)
        assertEquals(
            listOf(pOleo, pXarope, pAlcool, pYpe, pBanana, pArroz),
            sorted
        )
    }

    @Test
    fun testSortByStockDescending() {
        val sorted = allProducts.sortWithOption(ProductSortOption.STOCK_DESC)
        assertEquals(
            listOf(pArroz, pBanana, pYpe, pAlcool, pXarope, pOleo),
            sorted
        )
    }

    @Test
    fun testSortByPriceAscending() {
        val sorted = allProducts.sortWithOption(ProductSortOption.PRICE_ASC)
        assertEquals(
            listOf(pBanana, pYpe, pArroz, pOleo, pAlcool, pXarope),
            sorted
        )
    }

    @Test
    fun testSortByPriceDescending() {
        val sorted = allProducts.sortWithOption(ProductSortOption.PRICE_DESC)
        assertEquals(
            listOf(pXarope, pAlcool, pOleo, pArroz, pYpe, pBanana),
            sorted
        )
    }

    @Test
    fun testSortByTypePieceFirst() {
        val sorted = allProducts.sortWithOption(ProductSortOption.TYPE_PIECE_FIRST)
        // PIECE: Álcool, Óleo, Xarope, Ype (alphabetical within PIECE)
        // WEIGHT: Arroz, Banana (alphabetical within WEIGHT)
        assertEquals(
            listOf(pAlcool, pOleo, pXarope, pYpe, pArroz, pBanana),
            sorted
        )
    }

    @Test
    fun testSortByTypeWeightFirst() {
        val sorted = allProducts.sortWithOption(ProductSortOption.TYPE_WEIGHT_FIRST)
        // WEIGHT: Arroz, Banana
        // PIECE: Álcool, Óleo, Xarope, Ype
        assertEquals(
            listOf(pArroz, pBanana, pAlcool, pOleo, pXarope, pYpe),
            sorted
        )
    }
}
