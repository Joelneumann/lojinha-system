package de.joelneumann.lojinha.ui.utils

/**
 * Normalizes a string by stripping combining diacritics and converting
 * accented / special Latin characters (e.g. Á, Â, Ã, ç, etc.) into their base ASCII counterparts.
 */
fun String.removeAccents(): String {
    val sb = StringBuilder(length)
    for (ch in this) {
        if (ch in '\u0300'..'\u036F') continue // Combining Diacritical Marks

        when (ch) {
            'À', 'Á', 'Â', 'Ã', 'Ä', 'Å', 'Ā', 'Ă', 'Ą', 'Ǎ' -> sb.append('A')
            'à', 'á', 'â', 'ã', 'ä', 'å', 'ā', 'ă', 'ą', 'ǎ' -> sb.append('a')
            'È', 'É', 'Ê', 'Ë', 'Ē', 'Ĕ', 'Ė', 'Ę', 'Ě' -> sb.append('E')
            'è', 'é', 'ê', 'ë', 'ē', 'ĕ', 'ė', 'ę', 'ě' -> sb.append('e')
            'Ì', 'Í', 'Î', 'Ï', 'Ĩ', 'Ī', 'Ĭ', 'Į', 'İ', 'Ǐ' -> sb.append('I')
            'ì', 'í', 'î', 'ï', 'ĩ', 'ī', 'ĭ', 'į', 'ı', 'ǐ' -> sb.append('i')
            'Ò', 'Ó', 'Ô', 'Õ', 'Ö', 'Ø', 'Ō', 'Ŏ', 'Ő', 'Ǒ' -> sb.append('O')
            'ò', 'ó', 'ô', 'õ', 'ö', 'ø', 'ō', 'ŏ', 'ő', 'ǒ' -> sb.append('o')
            'Ù', 'Ú', 'Û', 'Ü', 'Ũ', 'Ū', 'Ŭ', 'Ů', 'Ű', 'Ų', 'Ǔ', 'Ǖ', 'Ǘ', 'Ǚ', 'Ǜ' -> sb.append('U')
            'ù', 'ú', 'û', 'ü', 'ũ', 'ū', 'ŭ', 'ů', 'ű', 'ų', 'ǔ', 'ǖ', 'ǘ', 'ǚ', 'ǜ' -> sb.append('u')
            'Ç', 'Ć', 'Ĉ', 'Ċ', 'Č' -> sb.append('C')
            'ç', 'ć', 'ĉ', 'ċ', 'č' -> sb.append('c')
            'Ñ', 'Ń', 'Ņ', 'Ň' -> sb.append('N')
            'ñ', 'ń', 'ņ', 'ň' -> sb.append('n')
            'Ý', 'Ŷ', 'Ÿ' -> sb.append('Y')
            'ý', 'ŷ', 'ÿ' -> sb.append('y')
            'Ś', 'Ŝ', 'Ş', 'Š', 'Ș' -> sb.append('S')
            'ś', 'ŝ', 'ş', 'š', 'ș' -> sb.append('s')
            'Ź', 'Ż', 'Ž' -> sb.append('Z')
            'ź', 'ż', 'ž' -> sb.append('z')
            'Ð', 'Ď', 'Đ' -> sb.append('D')
            'ð', 'ď', 'đ' -> sb.append('d')
            'Ţ', 'Ť', 'Ŧ', 'Ț' -> sb.append('T')
            'ţ', 'ť', 'ŧ', 'ț' -> sb.append('t')
            'Ĺ', 'Ļ', 'Ľ', 'Ł' -> sb.append('L')
            'ĺ', 'ļ', 'ľ', 'ł' -> sb.append('l')
            'Ŕ', 'Ŗ', 'Ř' -> sb.append('R')
            'ŕ', 'ŗ', 'ř' -> sb.append('r')
            'Ĝ', 'Ğ', 'Ġ', 'Ģ' -> sb.append('G')
            'ĝ', 'ğ', 'ġ', 'ģ' -> sb.append('g')
            'ß' -> sb.append("ss")
            'Æ' -> sb.append("AE")
            'æ' -> sb.append("ae")
            'Œ' -> sb.append("OE")
            'œ' -> sb.append("oe")
            else -> sb.append(ch)
        }
    }
    return sb.toString()
}

/**
 * Returns true if this string contains [query], ignoring diacritics/accents (e.g. 'a' matches 'á', 'Â', etc.)
 * and case (by default).
 */
fun String.containsIgnoreAccents(query: String, ignoreCase: Boolean = true): Boolean {
    if (query.isEmpty()) return true
    return this.removeAccents().contains(query.removeAccents(), ignoreCase = ignoreCase)
}

/**
 * A comparator for strings that compares text ignoring diacritics/accents and case,
 * while maintaining a deterministic fallback order.
 */
val ACCENT_INSENSITIVE_COMPARATOR = Comparator<String> { s1, s2 ->
    val n1 = s1.removeAccents()
    val n2 = s2.removeAccents()
    val cmp = n1.compareTo(n2, ignoreCase = true)
    if (cmp != 0) return@Comparator cmp
    val origIgnoreCase = s1.compareTo(s2, ignoreCase = true)
    if (origIgnoreCase != 0) return@Comparator origIgnoreCase
    s1.compareTo(s2)
}

/**
 * Sorts an Iterable by a string selector, ignoring accents and case.
 */
fun <T> Iterable<T>.sortedByAccentInsensitive(selector: (T) -> String): List<T> {
    return sortedWith(compareBy(ACCENT_INSENSITIVE_COMPARATOR, selector))
}

/**
 * Filters and ranks a list of products against a search query.
 * If [query] is blank, returns an empty list to prevent cluttering the screen for large catalogs.
 *
 * Products are ranked into relevance tiers:
 * Tier 0: Exact match (exact product name or exact barcode code)
 * Tier 1: Product name starts with query
 * Tier 2: A word in the product name starts with query
 * Tier 3: Multi-token query where all tokens match prefixes of words in the product name
 * Tier 4: Multi-token query where all tokens are contained in the product name or barcode description
 * Tier 5: Substring match inside the product name
 * Tier 6: Barcode description match, or barcode code match (minimum 3 digits if purely numeric)
 *
 * Within each tier, products are sorted strictly alphabetically by name.
 */
fun List<de.joelneumann.lojinha.domain.model.Product>.filterAndRankProducts(query: String): List<de.joelneumann.lojinha.domain.model.Product> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return emptyList()

    val normQuery = trimmed.removeAccents().lowercase()
    val queryTokens = normQuery.split("\\s+".toRegex()).filter { it.isNotEmpty() }
    val isDigitsOnly = trimmed.all { it.isDigit() }

    val rankedList = mutableListOf<Pair<Int, de.joelneumann.lojinha.domain.model.Product>>()

    for (product in this) {
        val normName = product.name.removeAccents().lowercase()
        val nameWords = normName.split("[\\s\\-_/(),.:;+]+".toRegex()).filter { it.isNotEmpty() }
        val barcodeCodes = product.barcodes.map { it.code.trim().removeAccents().lowercase() }
        val barcodeDescs = product.barcodes.mapNotNull { it.description?.trim()?.removeAccents()?.lowercase() }

        val tier: Int? = when {
            // Tier 0: Exact name match or exact barcode code match
            normName == normQuery || barcodeCodes.any { it == normQuery } -> 0

            // Tier 1: Product name starts with query OR barcode code starts with query
            normName.startsWith(normQuery) || barcodeCodes.any { it.startsWith(normQuery) } -> 1

            // Tier 2: Any word in product name starts with query
            nameWords.any { it.startsWith(normQuery) } -> 2

            // Tier 3: Multi-token query where all tokens match prefixes of words in product name
            queryTokens.size > 1 && queryTokens.all { token -> nameWords.any { word -> word.startsWith(token) } } -> 3

            // Tier 4: Multi-token query where all tokens are found in product name or barcode description
            queryTokens.size > 1 && queryTokens.all { token ->
                normName.contains(token) || barcodeDescs.any { it.contains(token) }
            } -> 4

            // Tier 5: Substring match in product name
            normName.contains(normQuery) -> 5

            // Tier 6: Barcode description contains query, OR barcode code contains query (minimum 2 chars for numeric queries)
            barcodeDescs.any { it.contains(normQuery) } ||
                    ((!isDigitsOnly || trimmed.length >= 2) && barcodeCodes.any { it.contains(normQuery) }) -> 6

            else -> null
        }

        if (tier != null) {
            rankedList.add(tier to product)
        }
    }

    return rankedList
        .sortedWith(
            compareBy<Pair<Int, de.joelneumann.lojinha.domain.model.Product>> { it.first }
                .thenBy(ACCENT_INSENSITIVE_COMPARATOR) { it.second.name }
        )
        .map { it.second }
}

