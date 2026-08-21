package de.joelneumann.lojinha.ui.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.ColorWarningAmber
import de.joelneumann.lojinha.ui.theme.TextSecondaryMuted
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

    val showProductModal by viewModel.showProductModal.collectAsState()
    val editProduct by viewModel.editProduct.collectAsState()

    val strings = I18n.current

    val activeProducts = remember(products) { products.filter { it.isActive } }
    val disabledProducts = remember(products) { products.filter { !it.isActive } }

    val filteredActiveProducts = remember(activeProducts, searchQuery) {
        if (searchQuery.isBlank()) activeProducts
        else activeProducts.filter { p ->
            p.name.contains(searchQuery, ignoreCase = true) ||
                    p.barcodes.any { b -> b.code.contains(searchQuery, ignoreCase = true) || (b.description != null && b.description.contains(searchQuery, ignoreCase = true)) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val openFirstResult = {
            if (filteredActiveProducts.isNotEmpty()) {
                onRequestExpandProduct(filteredActiveProducts.first().id)
            }
        }

        AdminTopBar(
            searchQuery = searchQuery,
            onQueryChange = viewModel::updateSearchQuery,
            placeholder = "🔍 Search product by name or barcode...",
            countText = if (searchQuery.isBlank()) "${activeProducts.size} Products" else "${filteredActiveProducts.size} / ${activeProducts.size} Products",
            onSearchSubmitted = openFirstResult,
            actionButtonText = strings.addProduct,
            onActionButtonClick = viewModel::openNewProductModal
        )

        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (filteredActiveProducts.isEmpty()) {
                item(key = "empty-products-msg") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No matching active products found.", color = TextSecondaryMuted)
                    }
                }
            } else {
                items(filteredActiveProducts, key = { it.id }) { product ->
                    val isExpanded = expandedProductId == product.id
                    AdminProductAccordionCard(
                        product = product,
                        allProducts = products,
                        isExpanded = isExpanded,
                        onExpandToggle = { onRequestToggleExpand(product.id) },
                        onSaveProduct = viewModel::saveProduct,
                        onAdjustStock = viewModel::adjustProductStock,
                        onToggleActive = viewModel::toggleProductActive,
                        onDeleteProduct = { viewModel.deleteProduct(it.id) },
                        onUnsavedStateChanged = onUnsavedStateChanged
                    )
                }
            }

            if (disabledProducts.isNotEmpty()) {
                item(key = "disabled-products-section") {
                    Spacer(modifier = Modifier.height(16.dp))
                    AdminExpandableSection(
                        title = "⚠️ Disabled Products",
                        countText = "${disabledProducts.size} ${if (disabledProducts.size == 1) "Product" else "Products"}",
                        accentColor = ColorWarningAmber,
                        showLabel = "Show Disabled Products",
                        hideLabel = "Hide Disabled Products"
                    ) {
                        disabledProducts.forEach { product ->
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
