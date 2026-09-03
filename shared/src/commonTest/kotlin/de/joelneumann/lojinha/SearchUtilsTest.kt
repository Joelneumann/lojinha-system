package de.joelneumann.lojinha

import de.joelneumann.lojinha.ui.utils.containsIgnoreAccents
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
}
