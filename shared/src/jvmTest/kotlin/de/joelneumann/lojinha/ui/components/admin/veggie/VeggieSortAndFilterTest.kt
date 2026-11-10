package de.joelneumann.lojinha.ui.components.admin.veggie

import de.joelneumann.lojinha.domain.model.VeggieProduct
import de.joelneumann.lojinha.domain.model.VeggieUnitType
import kotlin.test.Test
import kotlin.test.assertEquals

class VeggieSortAndFilterTest {

    private fun sampleProduct(id: String, name: String, priceCents: Long?, unitId: String = "unit_kg"): VeggieProduct {
        return VeggieProduct(
            id = id,
            name = name,
            description = null,
            estimatedPriceCents = priceCents,
            unitTypeId = unitId,
            imageFileName = null,
            isActive = true,
            isDeleted = false,
            updatedAt = 0L
        )
    }

    @Test
    fun testSortByNameAscendingAndDescending() {
        val p1 = sampleProduct("1", "Tomate", 500L)
        val p2 = sampleProduct("2", "alface", 300L)
        val p3 = sampleProduct("3", "Água de coco", 800L)
        val p4 = sampleProduct("4", "Cenoura", 400L)

        val list = listOf(p1, p2, p3, p4)

        val asc = list.sortWithOption(VeggieSortOption.NAME_ASC)
        assertEquals(listOf("Água de coco", "alface", "Cenoura", "Tomate"), asc.map { it.name })

        val desc = list.sortWithOption(VeggieSortOption.NAME_DESC)
        assertEquals(listOf("Tomate", "Cenoura", "alface", "Água de coco"), desc.map { it.name })
    }

    @Test
    fun testSortByPriceAscendingAndDescending() {
        val p1 = sampleProduct("1", "Batata", 600L)
        val p2 = sampleProduct("2", "Tomate", 1200L)
        val p3 = sampleProduct("3", "Ervilha", null) // unknown / market price
        val p4 = sampleProduct("4", "Cenoura", 450L)

        val list = listOf(p1, p2, p3, p4)

        val priceAsc = list.sortWithOption(VeggieSortOption.PRICE_ASC)
        // Cenoura (450), Batata (600), Tomate (1200), Ervilha (null -> Long.MAX_VALUE)
        assertEquals(listOf("Cenoura", "Batata", "Tomate", "Ervilha"), priceAsc.map { it.name })

        val priceDesc = list.sortWithOption(VeggieSortOption.PRICE_DESC)
        // Tomate (1200), Batata (600), Cenoura (450), Ervilha (null -> -1L)
        assertEquals(listOf("Tomate", "Batata", "Cenoura", "Ervilha"), priceDesc.map { it.name })
    }

    @Test
    fun testFilterByUnitTypeId() {
        val unitKg = VeggieUnitType(id = "unit_kg", name = "kg", allowDecimals = true, isActive = true, updatedAt = 0L)
        val unitMaco = VeggieUnitType(id = "unit_maco", name = "Maço", allowDecimals = false, isActive = true, updatedAt = 0L)

        val p1 = sampleProduct("1", "Batata", 500L, unitId = unitKg.id)
        val p2 = sampleProduct("2", "Coentro", 300L, unitId = unitMaco.id)
        val p3 = sampleProduct("3", "Cenoura", 400L, unitId = unitKg.id)

        val products = listOf(p1, p2, p3)

        val kgOnly = products.filter { it.unitTypeId == unitKg.id }
        assertEquals(listOf("Batata", "Cenoura"), kgOnly.map { it.name })

        val macoOnly = products.filter { it.unitTypeId == unitMaco.id }
        assertEquals(listOf("Coentro"), macoOnly.map { it.name })
    }
}
