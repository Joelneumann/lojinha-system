package de.joelneumann.lojinha.ui.components.admin.products

import de.joelneumann.lojinha.domain.model.Product
import de.joelneumann.lojinha.domain.model.UnitType
import de.joelneumann.lojinha.ui.i18n.AppStrings
import de.joelneumann.lojinha.ui.utils.ACCENT_INSENSITIVE_COMPARATOR
import de.joelneumann.lojinha.ui.utils.sortedByAccentInsensitive

enum class ProductSortOption {
    NAME_ASC,
    NAME_DESC,
    STOCK_ASC,
    STOCK_DESC,
    PRICE_ASC,
    PRICE_DESC,
    TYPE_PIECE_FIRST,
    TYPE_WEIGHT_FIRST;

    fun getLabel(strings: AppStrings): String = when (this) {
        NAME_ASC -> strings.sortNameAsc
        NAME_DESC -> strings.sortNameDesc
        STOCK_ASC -> strings.sortStockAsc
        STOCK_DESC -> strings.sortStockDesc
        PRICE_ASC -> strings.sortPriceAsc
        PRICE_DESC -> strings.sortPriceDesc
        TYPE_PIECE_FIRST -> strings.sortTypePiece
        TYPE_WEIGHT_FIRST -> strings.sortTypeWeight
    }
}

fun List<Product>.sortWithOption(sortOption: ProductSortOption): List<Product> {
    return when (sortOption) {
        ProductSortOption.NAME_ASC -> sortedByAccentInsensitive { it.name }
        ProductSortOption.NAME_DESC -> sortedWith(compareByDescending(ACCENT_INSENSITIVE_COMPARATOR) { it.name })
        ProductSortOption.STOCK_ASC -> sortedWith(
            compareBy<Product> { it.stockQuantity }
                .thenBy(ACCENT_INSENSITIVE_COMPARATOR) { it.name }
        )
        ProductSortOption.STOCK_DESC -> sortedWith(
            compareByDescending<Product> { it.stockQuantity }
                .thenBy(ACCENT_INSENSITIVE_COMPARATOR) { it.name }
        )
        ProductSortOption.PRICE_ASC -> sortedWith(
            compareBy<Product> { it.basePrice }
                .thenBy(ACCENT_INSENSITIVE_COMPARATOR) { it.name }
        )
        ProductSortOption.PRICE_DESC -> sortedWith(
            compareByDescending<Product> { it.basePrice }
                .thenBy(ACCENT_INSENSITIVE_COMPARATOR) { it.name }
        )
        ProductSortOption.TYPE_PIECE_FIRST -> sortedWith(
            compareBy<Product> { if (it.unitType == UnitType.PIECE) 0 else 1 }
                .thenBy(ACCENT_INSENSITIVE_COMPARATOR) { it.name }
        )
        ProductSortOption.TYPE_WEIGHT_FIRST -> sortedWith(
            compareBy<Product> { if (it.unitType == UnitType.WEIGHT) 0 else 1 }
                .thenBy(ACCENT_INSENSITIVE_COMPARATOR) { it.name }
        )
    }
}
