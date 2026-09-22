package de.joelneumann.lojinha

import de.joelneumann.lojinha.ui.utils.containsIgnoreAccents
import de.joelneumann.lojinha.ui.utils.filterAndRankProducts
import de.joelneumann.lojinha.ui.utils.removeAccents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchUtilsTest {

    @Test
    fun testRemoveAccentsRemovesDiacritics() {
        assertEquals("Agua Mineral", "Água Mineral".removeAccents())
        assertEquals("Maca Gala", "Maçã Gala".removeAccents())
        assertEquals("Pao de Queijo", "Pão de Queijo".removeAccents())
        assertEquals("Cafe Expresso", "Café Expresso".removeAccents())
        assertEquals("Acucar Refinado", "Açúcar Refinado".removeAccents())
        assertEquals("Jalapeno", "Jalapeño".removeAccents())
        assertEquals("Uber", "Über".removeAccents())
        assertEquals("Strassenschild", "Straßenschild".removeAccents())
    }

    @Test
    fun testSearchLetterMatchesAccentedVowels() {
        // Typing 'a' or 'A' matches accented A's like Á, Â, Ã, À, Ä
        assertTrue("Água Mineral".containsIgnoreAccents("a"))
        assertTrue("Água Mineral".containsIgnoreAccents("A"))
        assertTrue("Âncora".containsIgnoreAccents("a"))
        assertTrue("Âncora".containsIgnoreAccents("A"))
        assertTrue("Maçã".containsIgnoreAccents("a"))
        assertTrue("Pão de Queijo".containsIgnoreAccents("a"))

        // Typing 'e' matches É, Ê
        assertTrue("Café".containsIgnoreAccents("e"))
        assertTrue("Café".containsIgnoreAccents("E"))

        // Typing 'o' matches Ó, Ô, Õ
        assertTrue("Limão".containsIgnoreAccents("o"))
        assertTrue("Pó de Café".containsIgnoreAccents("o"))

        // Typing 'u' matches Ú, Ü
        assertTrue("Açúcar".containsIgnoreAccents("u"))
        assertTrue("Müsli".containsIgnoreAccents("u"))

        // Typing 'c' matches Ç
        assertTrue("Maçã".containsIgnoreAccents("c"))
        assertTrue("Açúcar".containsIgnoreAccents("C"))
    }

    @Test
    fun testSearchTermWithoutAccentsMatchesAccentedTarget() {
        assertTrue("Água Mineral".containsIgnoreAccents("agua"))
        assertTrue("Água Mineral".containsIgnoreAccents("AGUA"))
        assertTrue("Maçã Gala".containsIgnoreAccents("maca"))
        assertTrue("Pão de Queijo".containsIgnoreAccents("pao"))
        assertTrue("Café Expresso".containsIgnoreAccents("cafe"))
        assertTrue("Açúcar".containsIgnoreAccents("acucar"))
    }

    @Test
    fun testSearchTermWithAccentsMatchesUnaccentedTarget() {
        assertTrue("Agua Mineral".containsIgnoreAccents("água"))
        assertTrue("Maca Gala".containsIgnoreAccents("maçã"))
        assertTrue("Pao de Queijo".containsIgnoreAccents("pão"))
        assertTrue("Cafe Expresso".containsIgnoreAccents("café"))
    }

    @Test
    fun testCombiningDiacriticsAreHandled() {
        val nfdAgua = "A\u0301gua" // Decomposed 'Á'
        assertTrue(nfdAgua.containsIgnoreAccents("a"))
        assertTrue(nfdAgua.containsIgnoreAccents("agua"))
        assertTrue("Água".containsIgnoreAccents(nfdAgua))
    }

    @Test
    fun testEmptyOrNonMatchingQueries() {
        assertTrue("Água".containsIgnoreAccents(""))
        assertFalse("Água".containsIgnoreAccents("xyz"))
        assertFalse("Café".containsIgnoreAccents("b"))
    }

    @Test
    fun testAccentInsensitiveSorting() {
        val items = listOf(
            "Xarope Piaceri Morango 690ml",
            "Ype Limpador Multiuso",
            "Álcool Cooperalcool 1L",
            "Óleo de Soja Soya 900 ml",
            "Arroz Branco",
            "Banana Prata"
        )
        val sorted = items.sortedWith(de.joelneumann.lojinha.ui.utils.ACCENT_INSENSITIVE_COMPARATOR)

        val expected = listOf(
            "Álcool Cooperalcool 1L",
            "Arroz Branco",
            "Banana Prata",
            "Óleo de Soja Soya 900 ml",
            "Xarope Piaceri Morango 690ml",
            "Ype Limpador Multiuso"
        )
        assertEquals(expected, sorted)
    }

    @Test
    fun testEmptyQueryReturnsEmptyList() {
        val p1 = de.joelneumann.lojinha.domain.model.Product(id = "1", name = "Orange", basePrice = 100)
        val p2 = de.joelneumann.lojinha.domain.model.Product(id = "2", name = "Avocado", basePrice = 200)
        val products = listOf(p1, p2)

        assertTrue(products.filterAndRankProducts("").isEmpty())
        assertTrue(products.filterAndRankProducts("   ").isEmpty())
    }

    @Test
    fun testRankTypingORanksOrangeBeforeAvocado() {
        val avocado = de.joelneumann.lojinha.domain.model.Product(id = "1", name = "Avocado", basePrice = 500)
        val bolo = de.joelneumann.lojinha.domain.model.Product(id = "2", name = "Bolo de Chocolate", basePrice = 1200)
        val orange = de.joelneumann.lojinha.domain.model.Product(id = "3", name = "Orange", basePrice = 300)
        val oreo = de.joelneumann.lojinha.domain.model.Product(id = "4", name = "Oreo", basePrice = 450)
        val freshOrange = de.joelneumann.lojinha.domain.model.Product(id = "5", name = "Fresh Orange", basePrice = 400)

        val products = listOf(avocado, bolo, orange, oreo, freshOrange)
        val ranked = products.filterAndRankProducts("O")

        // 1. Orange and Oreo start with O (Tier 1, sorted alphabetically: Orange, then Oreo)
        // 2. Fresh Orange has a word starting with O (Tier 2)
        // 3. Avocado and Bolo contain O as a substring (Tier 5, sorted alphabetically: Avocado, then Bolo)
        assertEquals(listOf("Orange", "Oreo", "Fresh Orange", "Avocado", "Bolo de Chocolate"), ranked.map { it.name })
    }

    @Test
    fun testWordBoundaryMatchesRankBeforeInternalSubstrings() {
        val clubMate = de.joelneumann.lojinha.domain.model.Product(id = "1", name = "Club Mate", basePrice = 800)
        val tomate = de.joelneumann.lojinha.domain.model.Product(id = "2", name = "Tomate", basePrice = 400)
        val mateLeao = de.joelneumann.lojinha.domain.model.Product(id = "3", name = "Mate Leão", basePrice = 600)

        val products = listOf(clubMate, tomate, mateLeao)
        val ranked = products.filterAndRankProducts("mate")

        // Mate Leão (Tier 1: name starts with mate)
        // Club Mate (Tier 2: word 'mate' starts with mate)
        // Tomate (Tier 5: substring inside 'to-mate')
        assertEquals(listOf("Mate Leão", "Club Mate", "Tomate"), ranked.map { it.name })
    }

    @Test
    fun testMultiWordQueryMatching() {
        val clubMate = de.joelneumann.lojinha.domain.model.Product(id = "1", name = "Club Mate (330ml)", basePrice = 800)
        val cocaZero = de.joelneumann.lojinha.domain.model.Product(id = "2", name = "Coca-Cola Zero", basePrice = 700)
        val mateTea = de.joelneumann.lojinha.domain.model.Product(id = "3", name = "Chá Mate", basePrice = 500)

        val products = listOf(clubMate, cocaZero, mateTea)
        val ranked = products.filterAndRankProducts("mate 330")

        assertEquals(1, ranked.size)
        assertEquals("Club Mate (330ml)", ranked.first().name)
    }

    @Test
    fun testBarcodeExactAndSubstringMatching() {
        val barcode = de.joelneumann.lojinha.domain.model.Barcode(code = "7891234564821", description = "Single Can")
        val prod = de.joelneumann.lojinha.domain.model.Product(id = "1", name = "Guaraná Antarctica", barcodes = listOf(barcode), basePrice = 500)
        val products = listOf(prod)

        // Exact barcode code
        assertEquals(1, products.filterAndRankProducts("7891234564821").size)

        // 4-digit barcode substring
        assertEquals(1, products.filterAndRankProducts("4821").size)

        // 2-digit barcode substring
        assertEquals(1, products.filterAndRankProducts("21").size)

        // Single digit in middle should NOT match to avoid flooding with 700 products
        assertEquals(0, products.filterAndRankProducts("6").size)

        // Barcode description match
        assertEquals(1, products.filterAndRankProducts("Can").size)
    }

    @Test
    fun testSearchDigitsProgressionMatchesContinuously() {
        val prodWithShortBarcode = de.joelneumann.lojinha.domain.model.Product(
            id = "1",
            name = "Product 111",
            barcodes = listOf(de.joelneumann.lojinha.domain.model.Barcode(code = "111")),
            basePrice = 100
        )
        val prodWithLongBarcode = de.joelneumann.lojinha.domain.model.Product(
            id = "2",
            name = "Olive Oil",
            barcodes = listOf(de.joelneumann.lojinha.domain.model.Barcode(code = "789000111222")),
            basePrice = 1500
        )
        val products = listOf(prodWithShortBarcode, prodWithLongBarcode)

        // Query "1": matches prodWithShortBarcode (barcode starts with 1) and prodWithShortBarcode (name has 1)
        val res1 = products.filterAndRankProducts("1")
        assertTrue(res1.isNotEmpty())
        assertTrue(res1.any { it.id == "1" })

        // Query "11": must NOT drop to 0! Matches both products (barcode starts with 11 and barcode contains 11)
        val res11 = products.filterAndRankProducts("11")
        assertEquals(2, res11.size)

        // Query "111": matches both products
        val res111 = products.filterAndRankProducts("111")
        assertEquals(2, res111.size)
    }

    @Test
    fun testAlphabeticalSortingWithinSameTier() {
        val p1 = de.joelneumann.lojinha.domain.model.Product(id = "1", name = "Ovos Brancos", basePrice = 600)
        val p2 = de.joelneumann.lojinha.domain.model.Product(id = "2", name = "Orange", basePrice = 300)
        val p3 = de.joelneumann.lojinha.domain.model.Product(id = "3", name = "Oreo", basePrice = 400)

        val products = listOf(p1, p2, p3)
        val ranked = products.filterAndRankProducts("O")

        assertEquals(listOf("Orange", "Oreo", "Ovos Brancos"), ranked.map { it.name })
    }
}

