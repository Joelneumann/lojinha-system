package de.joelneumann.lojinha.ui.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.joelneumann.lojinha.ui.components.admin.AdminExpandableSection
import de.joelneumann.lojinha.ui.components.admin.AdminTopBar
import de.joelneumann.lojinha.ui.components.admin.products.AdminProductAccordionCard
import de.joelneumann.lojinha.ui.components.admin.products.DisabledProductCard
import de.joelneumann.lojinha.ui.components.admin.products.ProductEditDialog
import de.joelneumann.lojinha.ui.components.admin.products.ProductSortButton
import de.joelneumann.lojinha.ui.components.admin.products.sortWithOption
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.ColorWarningAmber
import de.joelneumann.lojinha.ui.theme.TextSecondaryMuted
import de.joelneumann.lojinha.ui.utils.containsIgnoreAccents
import de.joelneumann.lojinha.ui.utils.sortedByAccentInsensitive
import de.joelneumann.lojinha.ui.viewmodel.admin.AdminProductsViewModel

@Composable
fun AdminProductsTabScreen(
    viewModel: AdminProductsViewModel,
    expandedProductId: String?,
    onRequestToggleExpand: (String?) -> Unit,
    onRequestExpandProduct: (String) -> Unit,
    onUnsavedStateChanged: (Boolean) -> Unit
) {
    val products by viewModel.products.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val settings by viewModel.settings.collectAsState()

    val showProductModal by viewModel.showProductModal.collectAsState()
    val editProduct by viewModel.editProduct.collectAsState()

    val strings = I18n.current

    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        onUnsavedStateChanged(false)
    }

    LaunchedEffect(sortOption) {
        listState.scrollToItem(0)
    }

    val activeProducts = remember(products, sortOption) { products.filter { it.isActive }.sortWithOption(sortOption) }
    val disabledProducts = remember(products, sortOption) { products.filter { !it.isActive }.sortWithOption(sortOption) }

    val filteredActiveProducts = remember(activeProducts, searchQuery, sortOption) {
        if (searchQuery.isBlank()) activeProducts
        else activeProducts.filter { p ->
            p.name.containsIgnoreAccents(searchQuery) ||
                    p.barcodes.any { b -> b.code.containsIgnoreAccents(searchQuery) || (b.description != null && b.description.containsIgnoreAccents(searchQuery)) }
        }.sortWithOption(sortOption)
    }

    val filteredDisabledProducts = remember(disabledProducts, searchQuery, sortOption) {
        if (searchQuery.isBlank()) disabledProducts
        else disabledProducts.filter { p ->
            p.name.containsIgnoreAccents(searchQuery) ||
                    p.barcodes.any { b -> b.code.containsIgnoreAccents(searchQuery) || (b.description != null && b.description.containsIgnoreAccents(searchQuery)) }
        }.sortWithOption(sortOption)
    }

    val totalMatches = filteredActiveProducts.size + filteredDisabledProducts.size

    Column(modifier = Modifier.fillMaxSize()) {
        val openFirstResult = {
            if (filteredActiveProducts.isNotEmpty()) {
                onRequestExpandProduct(filteredActiveProducts.first().id)
            } else if (filteredDisabledProducts.isNotEmpty()) {
                // First result is disabled
            }
        }

        AdminTopBar(
            searchQuery = searchQuery,
            onQueryChange = viewModel::updateSearchQuery,
            placeholder = strings.searchProductAdminPlaceholder,
            countText = if (searchQuery.isBlank()) strings.productsCountText(activeProducts.size) else strings.productsCountText(filteredActiveProducts.size, activeProducts.size),
            onSearchSubmitted = openFirstResult,
            actionButtonText = strings.addProduct,
            onActionButtonClick = viewModel::openNewProductModal,
            sortContent = {
                ProductSortButton(
                    selectedOption = sortOption,
                    onOptionSelected = viewModel::updateSortOption,
                    modifier = Modifier.fillMaxHeight()
                )
            }
        )

        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            if (filteredActiveProducts.isEmpty() && filteredDisabledProducts.isEmpty()) {
                item(key = "empty-products-msg") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(strings.noMatchingProducts, color = TextSecondaryMuted)
                    }
                }
            } else {
                items(filteredActiveProducts, key = { it.id }) { product ->
                    val isExpanded = expandedProductId == product.id
                    AdminProductAccordionCard(
                        product = product,
                        globalMarkup = settings.globalMarkupPercent,
                        isExpanded = isExpanded,
                        onExpandToggle = { onRequestToggleExpand(product.id) },
                        onEditProduct = { viewModel.openEditProductModal(it) },
                        onAdjustStock = viewModel::adjustProductStock,
                        onToggleActive = viewModel::toggleProductActive,
                        onDeleteProduct = { viewModel.deleteProduct(it.id) }
                    )
                }
            }

            if (filteredDisabledProducts.isNotEmpty()) {
                item(key = "disabled-products-section") {
                    Spacer(modifier = Modifier.height(16.dp))
                    AdminExpandableSection(
                        title = strings.disabledProducts,
                        countText = strings.productsCountText(filteredDisabledProducts.size),
                        accentColor = ColorWarningAmber,
                        showLabel = strings.showDisabledProducts,
                        hideLabel = strings.hideDisabledProducts
                    ) {
                        filteredDisabledProducts.forEach { product ->
                            DisabledProductCard(
                                product = product,
                                onEnableProduct = { viewModel.toggleProductActive(product) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showProductModal && editProduct != null) {
        ProductEditDialog(
            product = editProduct!!,
            allProducts = products,
            onSave = { viewModel.saveProduct(it) },
            onCancel = { viewModel.closeProductModal() }
        )
    }
}
