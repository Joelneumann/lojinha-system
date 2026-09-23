package de.joelneumann.lojinha.ui.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import de.joelneumann.lojinha.ui.components.admin.AdminExpandableSection
import de.joelneumann.lojinha.ui.components.admin.AdminTopBar
import de.joelneumann.lojinha.ui.components.admin.products.AdminProductAccordionCard
import de.joelneumann.lojinha.ui.components.admin.products.DisabledProductCard
import de.joelneumann.lojinha.ui.components.admin.products.ProductEditDialog
import de.joelneumann.lojinha.ui.components.admin.products.ProductSortButton
import de.joelneumann.lojinha.ui.components.admin.products.sortWithOption
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.AccentNavy
import de.joelneumann.lojinha.ui.theme.ColorWarningAmber
import de.joelneumann.lojinha.ui.theme.PrimaryNavy
import de.joelneumann.lojinha.ui.theme.SurfaceWhite
import de.joelneumann.lojinha.ui.theme.TextSecondaryMuted
import de.joelneumann.lojinha.ui.utils.confirmationDialogKeys
import de.joelneumann.lojinha.ui.utils.containsIgnoreAccents
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
    val isSavingProduct by viewModel.isSavingProduct.collectAsState()
    val editProduct by viewModel.editProduct.collectAsState()
    val productErrorMessage by viewModel.productErrorMessage.collectAsState()

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

    val filteredActiveProducts = remember(activeProducts, searchQuery) {
        if (searchQuery.isBlank()) activeProducts
        else activeProducts.filter { p ->
            p.name.containsIgnoreAccents(searchQuery) ||
                    p.barcodes.any { b -> b.code.containsIgnoreAccents(searchQuery) || (b.description != null && b.description.containsIgnoreAccents(searchQuery)) }
        }
    }

    val filteredDisabledProducts = remember(disabledProducts, searchQuery) {
        if (searchQuery.isBlank()) disabledProducts
        else disabledProducts.filter { p ->
            p.name.containsIgnoreAccents(searchQuery) ||
                    p.barcodes.any { b -> b.code.containsIgnoreAccents(searchQuery) || (b.description != null && b.description.containsIgnoreAccents(searchQuery)) }
        }
    }

    val totalMatches = filteredActiveProducts.size + filteredDisabledProducts.size

    Column(modifier = Modifier.fillMaxSize()) {
        val openFirstResult = {
            if (filteredActiveProducts.isNotEmpty()) {
                onRequestExpandProduct(filteredActiveProducts.first().id)
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
            isSaving = isSavingProduct,
            onSave = { viewModel.saveProduct(it) },
            onCancel = { viewModel.closeProductModal() }
        )
    }

    if (productErrorMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearProductError() },
            containerColor = SurfaceWhite,
            shape = RoundedCornerShape(16.dp),
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
            modifier = Modifier.confirmationDialogKeys(
                onCancel = { viewModel.clearProductError() },
                onConfirm = { viewModel.clearProductError() }
            ),
            title = { Text(strings.errorTitle, fontWeight = FontWeight.Bold, color = PrimaryNavy) },
            text = { Text(productErrorMessage!!, color = PrimaryNavy) },
            confirmButton = {
                Button(
                    onClick = { viewModel.clearProductError() },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentNavy)
                ) {
                    Text(strings.ok, color = SurfaceWhite, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
