package de.joelneumann.lojinha.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import de.joelneumann.lojinha.domain.model.*
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting
import de.joelneumann.lojinha.ui.viewmodel.AdminTab
import de.joelneumann.lojinha.ui.viewmodel.AdminViewModel
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun AdminScreen(
    viewModel: AdminViewModel,
    language: Language,
    onCloseAdmin: () -> Unit
) {
    val strings = I18n.get(language)
    val currentTab by viewModel.currentTab.collectAsState()
    val products by viewModel.products.collectAsState()
    val users by viewModel.users.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val settings by viewModel.settings.collectAsState()

    val depositUser by viewModel.depositUser.collectAsState()
    val depositAmountInput by viewModel.depositAmountInput.collectAsState()
    val depositNoteInput by viewModel.depositNoteInput.collectAsState()

    val editProduct by viewModel.editProduct.collectAsState()
    val showProductModal by viewModel.showProductModal.collectAsState()

    val editUser by viewModel.editUser.collectAsState()
    val showUserModal by viewModel.showUserModal.collectAsState()

    val userDeleteError by viewModel.userDeleteErrorMessage.collectAsState()

    // Accordion & Unsaved Changes Guard State
    var expandedUserId by remember { mutableStateOf<String?>(null) }
    var expandedProductId by remember { mutableStateOf<String?>(null) }
    var hasUnsavedUserChanges by remember { mutableStateOf(false) }
    var hasUnsavedProductChanges by remember { mutableStateOf(false) }
    var pendingNavigationAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showUnsavedChangesGuardDialog by remember { mutableStateOf(false) }

    val safeNavigate = { action: () -> Unit ->
        if (hasUnsavedUserChanges || hasUnsavedProductChanges) {
            pendingNavigationAction = action
            showUnsavedChangesGuardDialog = true
        } else {
            action()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceContainerLight)
            .padding(24.dp)
    ) {
        // Full-Width Navigation Tab Bar
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = SurfaceWhite,
            shadowElevation = 2.dp,
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder)),
            modifier = Modifier.fillMaxWidth()
        ) {
            val tabs = listOf(
                AdminTab.PRODUCTS to "📦 ${strings.tabProducts}",
                AdminTab.USERS to "👥 ${strings.tabUsers}",
                AdminTab.TRANSACTIONS to "📜 ${strings.tabTransactions}",
                AdminTab.SETTINGS to "⚙️ ${strings.tabSettings}"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tabs.forEach { (tab, label) ->
                    val isSel = currentTab == tab
                    Button(
                        onClick = { safeNavigate { viewModel.selectTab(tab) } },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSel) AccentNavy else SurfaceContainerHighLight,
                            contentColor = if (isSel) SurfaceWhite else PrimaryNavy
                        ),
                        shape = RoundedCornerShape(8.dp),
                        elevation = if (isSel) ButtonDefaults.buttonElevation(defaultElevation = 2.dp) else ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 14.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Tab Body Content
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (currentTab) {
                AdminTab.PRODUCTS -> AdminProductsTab(
                    products = products,
                    language = language,
                    expandedProductId = expandedProductId,
                    onAdd = { viewModel.openNewProductModal() },
                    onSaveProduct = { viewModel.saveProduct(it) },
                    onAdjustStock = { productId, delta ->
                        viewModel.adjustProductStock(productId, delta)
                    },
                    onToggleActive = { viewModel.toggleProductActive(it) },
                    onDeleteProduct = { viewModel.deleteProduct(it.id) },
                    onRequestToggleExpand = { targetId ->
                        val action = {
                            expandedProductId = if (expandedProductId == targetId) null else targetId
                            hasUnsavedProductChanges = false
                        }
                        safeNavigate(action)
                    },
                    onRequestExpandProduct = { targetId ->
                        val action = {
                            expandedProductId = targetId
                            hasUnsavedProductChanges = false
                        }
                        safeNavigate(action)
                    },
                    onUnsavedStateChanged = { unsaved ->
                        hasUnsavedProductChanges = unsaved
                    }
                )

                AdminTab.USERS -> AdminUsersTab(
                    users = users,
                    language = language,
                    expandedUserId = expandedUserId,
                    onAdd = { viewModel.openNewUserModal() },
                    onSaveUser = { viewModel.saveUser(it) },
                    onAdjustBalance = { user, cents, note, isDeposit ->
                        val delta = if (isDeposit) cents else -cents
                        viewModel.adjustUserBalance(user.id, user.name, delta, note)
                    },
                    onToggleActive = { viewModel.toggleUserActive(it) },
                    onDeleteUser = { viewModel.attemptDeleteUser(it) },
                    onRequestToggleExpand = { targetUserId ->
                        val action = {
                            expandedUserId = if (expandedUserId == targetUserId) null else targetUserId
                            hasUnsavedUserChanges = false
                        }
                        safeNavigate(action)
                    },
                    onRequestExpandUser = { targetUserId ->
                        val action = {
                            expandedUserId = targetUserId
                            hasUnsavedUserChanges = false
                        }
                        safeNavigate(action)
                    },
                    onUnsavedStateChanged = { unsaved ->
                        hasUnsavedUserChanges = unsaved
                    }
                )

                AdminTab.TRANSACTIONS -> AdminTransactionsTab(
                    transactions = transactions,
                    language = language,
                    onReverse = { viewModel.reverseTransaction(it) }
                )

                AdminTab.SETTINGS -> AdminSettingsTab(
                    settings = settings,
                    language = language,
                    onSaveSettings = { viewModel.updateSystemSettings(it) }
                )
            }
        }
    }

    // Unsaved Changes Navigation Guard Dialog
    if (showUnsavedChangesGuardDialog) {
        AlertDialog(
            onDismissRequest = {
                showUnsavedChangesGuardDialog = false
                pendingNavigationAction = null
            },
            title = { Text("Unsaved Changes", fontWeight = FontWeight.Bold, color = ColorWarningAmber) },
            text = { Text("You have unsaved changes on the expanded user account. Leaving will discard your modifications. Do you want to proceed?") },
            confirmButton = {
                Button(
                    onClick = {
                        hasUnsavedUserChanges = false
                        showUnsavedChangesGuardDialog = false
                        val navAction = pendingNavigationAction
                        pendingNavigationAction = null
                        navAction?.invoke()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorDangerCrimson)
                ) {
                    Text("Discard Changes", color = SurfaceWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showUnsavedChangesGuardDialog = false
                        pendingNavigationAction = null
                    }
                ) {
                    Text("Stay & Edit")
                }
            }
        )
    }

    // Quick Deposit / Withdrawal Modal Dialog
    if (depositUser != null) {
        Dialog(onDismissRequest = { viewModel.closeDepositModal() }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceWhite,
                modifier = Modifier.width(400.dp).wrapContentHeight()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "${strings.depositWithdraw} (${depositUser!!.name})",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNavy
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(text = "Amount (BRL):", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = depositAmountInput,
                        onValueChange = { viewModel.updateDepositAmount(it) },
                        placeholder = { Text("e.g. 50,00 or 10.50") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(text = "Note / Reason:", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = depositNoteInput,
                        onValueChange = { viewModel.updateDepositNote(it) },
                        placeholder = { Text("e.g. Cash deposit via Admin") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { viewModel.submitDeposit(isDeposit = true) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ColorSuccessEmerald)
                        ) {
                            Text("+ Deposit", color = SurfaceWhite, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.submitDeposit(isDeposit = false) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ColorDangerCrimson)
                        ) {
                            Text("- Withdraw", color = SurfaceWhite, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Product Edit Modal
    if (showProductModal && editProduct != null) {
        ProductEditDialog(
            product = editProduct!!,
            language = language,
            onSave = { viewModel.saveProduct(it) },
            onCancel = { viewModel.closeProductModal() }
        )
    }

    // User Edit Modal
    if (showUserModal && editUser != null) {
        UserEditDialog(
            user = editUser!!,
            language = language,
            onSave = { viewModel.saveUser(it) },
            onCancel = { viewModel.closeUserModal() }
        )
    }

    // User Delete Error Dialog (Soft Delete Enforcement Notice)
    if (userDeleteError != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearUserDeleteError() },
            title = { Text("Audit Ledger Enforcement", fontWeight = FontWeight.Bold) },
            text = { Text(userDeleteError!!) },
            confirmButton = {
                Button(onClick = { viewModel.clearUserDeleteError() }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun AdminProductsTab(
    products: List<Product>,
    language: Language,
    expandedProductId: String?,
    onAdd: () -> Unit,
    onSaveProduct: (Product) -> Unit,
    onAdjustStock: (String, Long) -> Unit,
    onToggleActive: (Product) -> Unit,
    onDeleteProduct: (Product) -> Unit,
    onRequestToggleExpand: (String?) -> Unit,
    onRequestExpandProduct: (String) -> Unit,
    onUnsavedStateChanged: (Boolean) -> Unit
) {
    val strings = I18n.get(language)
    var searchQuery by remember { mutableStateOf("") }

    val filteredProducts = remember(products, searchQuery) {
        if (searchQuery.isBlank()) products
        else products.filter { p ->
            p.name.contains(searchQuery, ignoreCase = true) ||
                    p.barcodes.any { b -> b.code.contains(searchQuery, ignoreCase = true) || (b.description != null && b.description.contains(searchQuery, ignoreCase = true)) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val openFirstResult = {
            if (filteredProducts.isNotEmpty()) {
                onRequestExpandProduct(filteredProducts.first().id)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Search bar to filter products
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = "🔍 Search product by name or barcode...",
                        color = TextSecondaryMuted,
                        fontSize = 14.sp
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown &&
                            (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)
                        ) {
                            openFirstResult()
                            true
                        } else {
                            false
                        }
                    },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { openFirstResult() }),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceWhite,
                    unfocusedContainerColor = SurfaceWhite,
                    focusedBorderColor = AccentNavy,
                    unfocusedBorderColor = DividerBorder
                ),
                singleLine = true
            )

            Button(
                onClick = onAdd,
                colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxHeight()
            ) {
                Text(strings.addProduct, color = SurfaceWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (filteredProducts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No products found.", color = TextSecondaryMuted)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(filteredProducts, key = { it.id }) { product ->
                    val isExpanded = expandedProductId == product.id
                    AdminProductAccordionCard(
                        product = product,
                        isExpanded = isExpanded,
                        language = language,
                        onExpandToggle = { onRequestToggleExpand(product.id) },
                        onSaveProduct = onSaveProduct,
                        onAdjustStock = onAdjustStock,
                        onToggleActive = onToggleActive,
                        onDeleteProduct = onDeleteProduct,
                        onUnsavedStateChanged = onUnsavedStateChanged
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminProductAccordionCard(
    product: Product,
    isExpanded: Boolean,
    language: Language,
    onExpandToggle: () -> Unit,
    onSaveProduct: (Product) -> Unit,
    onAdjustStock: (String, Long) -> Unit,
    onToggleActive: (Product) -> Unit,
    onDeleteProduct: (Product) -> Unit,
    onUnsavedStateChanged: (Boolean) -> Unit
) {
    val strings = I18n.get(language)

    // Draft states for editing
    var draftName by remember(product.id, product.name) { mutableStateOf(product.name) }
    var draftPriceBrl by remember(product.id, product.basePrice) {
        mutableStateOf(Formatting.formatBrl(product.basePrice).replace("R$", "").trim())
    }
    var draftUnitType by remember(product.id, product.unitType) { mutableStateOf(product.unitType) }
    var draftStock by remember(product.id, product.stockQuantity) { mutableStateOf(product.stockQuantity.toString()) }
    var draftMarkup by remember(product.id, product.customMarkupPercent) {
        mutableStateOf(product.customMarkupPercent?.toString() ?: "")
    }
    var draftBarcodes by remember(product.id, product.barcodes) { mutableStateOf(product.barcodes) }

    // New barcode inputs
    var newBarcodeCode by remember(product.id) { mutableStateOf("") }
    var newBarcodeDesc by remember(product.id) { mutableStateOf("") }

    // Quick Stock Adjustment Draft state
    var stockDeltaInput by remember(product.id) { mutableStateOf("") }

    // Confirmation Prompts state
    var pendingStockAdjustment by remember { mutableStateOf<Long?>(null) } // positive or negative quantity
    var showToggleActiveConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Check for unsaved changes
    val parsedPriceCents = remember(draftPriceBrl) {
        val valDouble = draftPriceBrl.replace(',', '.').trim().toDoubleOrNull() ?: 0.0
        kotlin.math.round(valDouble * 100.0).toLong()
    }
    val parsedStock = remember(draftStock) { draftStock.toLongOrNull() ?: 0L }
    val parsedMarkup = remember(draftMarkup) { draftMarkup.toDoubleOrNull() }

    val hasUnsaved = remember(
        draftName, parsedPriceCents, draftUnitType, parsedStock, parsedMarkup, draftBarcodes, product
    ) {
        draftName != product.name ||
                parsedPriceCents != product.basePrice ||
                draftUnitType != product.unitType ||
                parsedStock != product.stockQuantity ||
                parsedMarkup != product.customMarkupPercent ||
                draftBarcodes != product.barcodes
    }

    LaunchedEffect(hasUnsaved, isExpanded) {
        if (isExpanded) {
            onUnsavedStateChanged(hasUnsaved)
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceWhite,
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (hasUnsaved && isExpanded) ColorWarningAmber else DividerBorder
            )
        ),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // View-Only Header Row (Clicking toggles accordion)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandToggle() }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = product.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNavy
                    )
                    if (!product.isActive) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ColorDangerCrimson.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "Deactivated",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorDangerCrimson,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (isExpanded && hasUnsaved) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ColorWarningAmber.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "● Unsaved Edits",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorWarningAmber,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Price: ${Formatting.formatBrl(product.basePrice)} • Stock: ${Formatting.formatQuantity(product.stockQuantity, product.unitType)}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNavy
                    )
                    Text(
                        text = if (isExpanded) "▲" else "▼",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondaryMuted
                    )
                }
            }

            // Expanded Accordion Settings Body
            if (isExpanded) {
                HorizontalDivider(color = DividerBorder)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // SECTION 1: Quick Single-Button Stock Adjustment (+ / -)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SurfaceContainerHighLight,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📦 Quick Stock Change (+/-):",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryNavy
                            )

                            OutlinedTextField(
                                value = stockDeltaInput,
                                onValueChange = { stockDeltaInput = it },
                                placeholder = { Text("e.g. 10 or -5", fontSize = 13.sp, color = TextSecondaryMuted) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = SurfaceWhite,
                                    unfocusedContainerColor = SurfaceWhite,
                                    focusedBorderColor = AccentNavy,
                                    unfocusedBorderColor = DividerBorder
                                ),
                                singleLine = true
                            )

                            Button(
                                onClick = {
                                    val delta = stockDeltaInput.trim().toLongOrNull()
                                    if (delta != null && delta != 0L) {
                                        pendingStockAdjustment = delta
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Text("📦 Adjust Stock", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SurfaceWhite)
                            }
                        }
                    }

                    // SECTION 2: Product Settings Fields
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1.5f)) {
                            Text("Product Name", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = draftName,
                                onValueChange = { draftName = it },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text("Base Price (R$)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = draftPriceBrl,
                                onValueChange = { draftPriceBrl = it },
                                placeholder = { Text("0,00", fontSize = 13.sp, color = TextSecondaryMuted) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1.5f),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Unit Type Selection
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Unit Type", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    UnitType.entries.forEach { unit ->
                                        val isSel = draftUnitType == unit
                                        OutlinedButton(
                                            onClick = { draftUnitType = unit },
                                            modifier = Modifier.weight(1f).height(44.dp),
                                            contentPadding = PaddingValues(0.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = if (isSel) AccentNavy else SurfaceWhite,
                                                contentColor = if (isSel) SurfaceWhite else PrimaryNavy
                                            ),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(unit.name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }

                            // Total Stock Quantity
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Current Stock Quantity", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = draftStock,
                                    onValueChange = { draftStock = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    singleLine = true
                                )
                            }
                        }

                        // Custom Markup % (Optional) - aligned directly on top/below Base Price (weight 1f)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Custom Markup % (Optional)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = draftMarkup,
                                onValueChange = { draftMarkup = it },
                                placeholder = { Text("Standard", fontSize = 13.sp, color = TextSecondaryMuted) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true
                            )
                        }
                    }

                    // Barcodes Management Section
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Associated Barcodes (${draftBarcodes.size})", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                        Spacer(modifier = Modifier.height(8.dp))

                        if (draftBarcodes.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                draftBarcodes.forEach { b ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = SurfaceContainerHighLight,
                                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text("🏷️ ${b.code}${if (b.description != null) " (${b.description})" else ""}", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = PrimaryNavy)
                                            Text(
                                                text = "✕",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ColorDangerCrimson,
                                                modifier = Modifier.clickable {
                                                    draftBarcodes = draftBarcodes.filter { it.code != b.code }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newBarcodeCode,
                                onValueChange = { newBarcodeCode = it },
                                placeholder = { Text("Barcode Code / Number", fontSize = 13.sp, color = TextSecondaryMuted) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = newBarcodeDesc,
                                onValueChange = { newBarcodeDesc = it },
                                placeholder = { Text("Description (Optional)", fontSize = 13.sp, color = TextSecondaryMuted) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true
                            )

                            Button(
                                onClick = {
                                    if (newBarcodeCode.isNotBlank()) {
                                        val code = newBarcodeCode.trim()
                                        if (draftBarcodes.none { it.code == code }) {
                                            draftBarcodes = draftBarcodes + Barcode(code, newBarcodeDesc.trim().ifBlank { null })
                                            newBarcodeCode = ""
                                            newBarcodeDesc = ""
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                                modifier = Modifier.height(52.dp)
                            ) {
                                Text("+ Add Barcode", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // SECTION 3: Save Button & Danger Zone Actions Row
                    HorizontalDivider(color = DividerBorder)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Save Changes Button
                        Button(
                            onClick = {
                                val updatedProduct = product.copy(
                                    name = draftName.trim(),
                                    basePrice = parsedPriceCents,
                                    unitType = draftUnitType,
                                    stockQuantity = parsedStock,
                                    customMarkupPercent = parsedMarkup,
                                    barcodes = draftBarcodes
                                )
                                onSaveProduct(updatedProduct)
                                onUnsavedStateChanged(false)
                            },
                            enabled = hasUnsaved && draftName.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentNavy,
                                disabledContainerColor = SurfaceContainerHighLight
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(42.dp)
                        ) {
                            Text(
                                text = if (hasUnsaved) "💾 Save Changes" else "✓ Saved",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Right: Danger Area (Activate/Deactivate & Delete)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { showToggleActiveConfirm = true },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(42.dp)
                            ) {
                                Text(
                                    text = if (product.isActive) "⚠️ Deactivate" else "⚡ Activate",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (product.isActive) ColorWarningAmber else ColorSuccessEmerald
                                )
                            }

                            Button(
                                onClick = { showDeleteConfirm = true },
                                colors = ButtonDefaults.buttonColors(containerColor = ColorDangerCrimson),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(42.dp)
                            ) {
                                Text(
                                    text = "🗑️ Delete",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SurfaceWhite
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Stock Adjustment Confirmation Dialog Prompt
    if (pendingStockAdjustment != null) {
        val delta = pendingStockAdjustment!!
        val isAdd = delta > 0
        val absQty = kotlin.math.abs(delta)
        val actionText = if (isAdd) "add $absQty items to" else "deduct $absQty items from"

        AlertDialog(
            onDismissRequest = { pendingStockAdjustment = null },
            title = { Text("Confirm Stock Adjustment", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to $actionText '${product.name}' stock level?") },
            confirmButton = {
                Button(
                    onClick = {
                        onAdjustStock(product.id, delta)
                        pendingStockAdjustment = null
                        stockDeltaInput = ""
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAdd) ColorSuccessEmerald else ColorDangerCrimson
                    )
                ) {
                    Text("Confirm", color = SurfaceWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingStockAdjustment = null }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    // Toggle Active Status Confirmation Dialog Prompt
    if (showToggleActiveConfirm) {
        val actionText = if (product.isActive) "deactivate" else "activate"
        AlertDialog(
            onDismissRequest = { showToggleActiveConfirm = false },
            title = { Text("Confirm Product Status Change", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to $actionText product '${product.name}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        onToggleActive(product)
                        showToggleActiveConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (product.isActive) ColorWarningAmber else ColorSuccessEmerald
                    )
                ) {
                    Text("Confirm", color = SurfaceWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showToggleActiveConfirm = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    // Delete Product Confirmation Dialog Prompt
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Confirm Delete Product", fontWeight = FontWeight.Bold, color = ColorDangerCrimson) },
            text = { Text("Are you sure you want to delete product '${product.name}'? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteProduct(product)
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorDangerCrimson)
                ) {
                    Text("Delete Product", color = SurfaceWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}

@Composable
private fun AdminUsersTab(
    users: List<User>,
    language: Language,
    expandedUserId: String?,
    onAdd: () -> Unit,
    onSaveUser: (User) -> Unit,
    onAdjustBalance: (User, Long, String, Boolean) -> Unit,
    onToggleActive: (User) -> Unit,
    onDeleteUser: (User) -> Unit,
    onRequestToggleExpand: (String?) -> Unit,
    onRequestExpandUser: (String) -> Unit,
    onUnsavedStateChanged: (Boolean) -> Unit
) {
    val strings = I18n.get(language)
    var searchQuery by remember { mutableStateOf("") }

    val filteredUsers = remember(users, searchQuery) {
        if (searchQuery.isBlank()) users
        else users.filter { u ->
            u.name.contains(searchQuery, ignoreCase = true) ||
                    (u.userBarcodeNumber != null && u.userBarcodeNumber.contains(searchQuery, ignoreCase = true)) ||
                    (u.pin != null && u.pin.contains(searchQuery, ignoreCase = true))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val openFirstResult = {
            if (filteredUsers.isNotEmpty()) {
                onRequestExpandUser(filteredUsers.first().id)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Search bar to filter user accounts
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = "🔍 Search account by name or barcode...",
                        color = TextSecondaryMuted,
                        fontSize = 14.sp
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown &&
                            (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)
                        ) {
                            openFirstResult()
                            true
                        } else {
                            false
                        }
                    },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { openFirstResult() }),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceWhite,
                    unfocusedContainerColor = SurfaceWhite,
                    focusedBorderColor = AccentNavy,
                    unfocusedBorderColor = DividerBorder
                ),
                singleLine = true
            )

            Button(
                onClick = onAdd,
                colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxHeight()
            ) {
                Text(strings.addUser, color = SurfaceWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (filteredUsers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No user accounts found.", color = TextSecondaryMuted)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(filteredUsers, key = { it.id }) { user ->
                    val isExpanded = expandedUserId == user.id
                    AdminUserAccordionCard(
                        user = user,
                        isExpanded = isExpanded,
                        language = language,
                        onExpandToggle = { onRequestToggleExpand(user.id) },
                        onSaveUser = onSaveUser,
                        onAdjustBalance = onAdjustBalance,
                        onToggleActive = onToggleActive,
                        onDeleteUser = onDeleteUser,
                        onUnsavedStateChanged = onUnsavedStateChanged
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminUserAccordionCard(
    user: User,
    isExpanded: Boolean,
    language: Language,
    onExpandToggle: () -> Unit,
    onSaveUser: (User) -> Unit,
    onAdjustBalance: (User, Long, String, Boolean) -> Unit,
    onToggleActive: (User) -> Unit,
    onDeleteUser: (User) -> Unit,
    onUnsavedStateChanged: (Boolean) -> Unit
) {
    val strings = I18n.get(language)

    // Draft states for editing
    var draftName by remember(user.id, user.name) { mutableStateOf(user.name) }
    var draftPin by remember(user.id, user.pin) { mutableStateOf(user.pin ?: "") }
    var draftUserBarcode by remember(user.id, user.userBarcode) { mutableStateOf(user.userBarcode ?: "") }
    var draftUserBarcodeNumber by remember(user.id, user.userBarcodeNumber) { mutableStateOf(user.userBarcodeNumber ?: "") }
    var draftLanguage by remember(user.id, user.language) { mutableStateOf(user.language) }
    var draftSecondaryCurrency by remember(user.id, user.secondaryCurrency) { mutableStateOf(user.secondaryCurrency) }

    // Quick Money Adjustment Draft state
    var moneyInput by remember(user.id) { mutableStateOf("") }

    // Confirmation Prompts state inside card
    var pendingBalanceAdjustment by remember { mutableStateOf<Long?>(null) } // positive (deposit) or negative (withdrawal) cents
    var showToggleActiveConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Check for unsaved changes
    val hasUnsaved = remember(
        draftName, draftPin, draftUserBarcode, draftUserBarcodeNumber, draftLanguage, draftSecondaryCurrency, user
    ) {
        draftName != user.name ||
                draftPin != (user.pin ?: "") ||
                draftUserBarcode != (user.userBarcode ?: "") ||
                draftUserBarcodeNumber != (user.userBarcodeNumber ?: "") ||
                draftLanguage != user.language ||
                draftSecondaryCurrency != user.secondaryCurrency
    }

    LaunchedEffect(hasUnsaved, isExpanded) {
        if (isExpanded) {
            onUnsavedStateChanged(hasUnsaved)
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceWhite,
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (hasUnsaved && isExpanded) ColorWarningAmber else DividerBorder
            )
        ),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // View-Only Header Row (Clicking toggles accordion)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandToggle() }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = user.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNavy
                    )
                    if (!user.isActive) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ColorDangerCrimson.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "Deactivated",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorDangerCrimson,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (isExpanded && hasUnsaved) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ColorWarningAmber.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "● Unsaved Edits",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorWarningAmber,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Balance: ${Formatting.formatBrl(user.balance)}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (user.balance >= 0) ColorSuccessEmerald else ColorDangerCrimson
                    )
                    Text(
                        text = if (isExpanded) "▲" else "▼",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondaryMuted
                    )
                }
            }

            // Expanded Accordion Settings Body
            if (isExpanded) {
                HorizontalDivider(color = DividerBorder)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // SECTION 1: Quick Single-Button Balance Adjustment (+ / -)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SurfaceContainerHighLight,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "💵 Balance Change (+/-):",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryNavy
                            )

                            OutlinedTextField(
                                value = moneyInput,
                                onValueChange = { moneyInput = it },
                                placeholder = { Text("e.g. 20 or -20", fontSize = 13.sp, color = TextSecondaryMuted) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = SurfaceWhite,
                                    unfocusedContainerColor = SurfaceWhite,
                                    focusedBorderColor = AccentNavy,
                                    unfocusedBorderColor = DividerBorder
                                ),
                                singleLine = true
                            )

                            Button(
                                onClick = {
                                    val cleaned = moneyInput.replace(',', '.').trim()
                                    val valDouble = cleaned.toDoubleOrNull()
                                    if (valDouble != null && valDouble != 0.0) {
                                        val cents = kotlin.math.round(valDouble * 100.0).toLong()
                                        pendingBalanceAdjustment = cents
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Text("💵 Adjust Balance", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SurfaceWhite)
                            }
                        }
                    }

                    // SECTION 2: User Settings Fields
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("User Name", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = draftName,
                                onValueChange = { draftName = it },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text("PIN (Optional)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = draftPin,
                                onValueChange = { draftPin = it },
                                placeholder = { Text("No PIN", fontSize = 13.sp, color = TextSecondaryMuted) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Barcode Symbol", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = draftUserBarcode,
                                onValueChange = { draftUserBarcode = it },
                                placeholder = { Text("e.g. USR-001", fontSize = 13.sp, color = TextSecondaryMuted) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text("Barcode Number (ID)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = draftUserBarcodeNumber,
                                onValueChange = { draftUserBarcodeNumber = it },
                                placeholder = { Text("e.g. 100000000001", fontSize = 13.sp, color = TextSecondaryMuted) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Language Selection
                        Column(modifier = Modifier.weight(1f)) {
                            Text(strings.preferredLanguage, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Language.entries.forEach { lang ->
                                    val isSel = draftLanguage == lang
                                    OutlinedButton(
                                        onClick = { draftLanguage = lang },
                                        modifier = Modifier.weight(1f).height(38.dp),
                                        contentPadding = PaddingValues(0.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (isSel) AccentNavy else SurfaceWhite,
                                            contentColor = if (isSel) SurfaceWhite else PrimaryNavy
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("${lang.flagEmoji} ${lang.code.uppercase()}", fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        // Secondary Currency Selection
                        Column(modifier = Modifier.weight(1f)) {
                            Text(strings.secondaryCurrency, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(
                                    SecondaryCurrency.NONE to "None",
                                    SecondaryCurrency.USD to "USD ($)",
                                    SecondaryCurrency.EUR to "EUR (€)"
                                ).forEach { (curr, label) ->
                                    val isSel = draftSecondaryCurrency == curr
                                    OutlinedButton(
                                        onClick = { draftSecondaryCurrency = curr },
                                        modifier = Modifier.weight(1f).height(38.dp),
                                        contentPadding = PaddingValues(0.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (isSel) AccentNavy else SurfaceWhite,
                                            contentColor = if (isSel) SurfaceWhite else PrimaryNavy
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(label, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    // SECTION 3: Save Button & Danger Zone Actions Row
                    HorizontalDivider(color = DividerBorder)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Save Changes Button
                        Button(
                            onClick = {
                                val bCode = draftUserBarcode.trim().ifBlank { null }
                                val bNum = draftUserBarcodeNumber.trim().ifBlank { null }
                                val updatedUser = user.copy(
                                    name = draftName.trim(),
                                    pin = draftPin.trim().ifBlank { null },
                                    userBarcode = if (bCode != null && bNum != null) bCode else null,
                                    userBarcodeNumber = if (bCode != null && bNum != null) bNum else null,
                                    language = draftLanguage,
                                    secondaryCurrency = draftSecondaryCurrency
                                )
                                onSaveUser(updatedUser)
                                onUnsavedStateChanged(false)
                            },
                            enabled = hasUnsaved,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentNavy,
                                disabledContainerColor = SurfaceContainerHighLight
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(42.dp)
                        ) {
                            Text(
                                text = if (hasUnsaved) "💾 Save Changes" else "✓ Saved",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Right: Danger Area (Activate/Deactivate & Delete)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { showToggleActiveConfirm = true },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(42.dp)
                            ) {
                                Text(
                                    text = if (user.isActive) "⚠️ Deactivate" else "⚡ Activate",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (user.isActive) ColorWarningAmber else ColorSuccessEmerald
                                )
                            }

                            Button(
                                onClick = { showDeleteConfirm = true },
                                colors = ButtonDefaults.buttonColors(containerColor = ColorDangerCrimson),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(42.dp)
                            ) {
                                Text(
                                    text = "🗑️ Delete",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SurfaceWhite
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Money Balance Adjustment Confirmation Dialog Prompt
    if (pendingBalanceAdjustment != null) {
        val cents = pendingBalanceAdjustment!!
        val isDeposit = cents > 0
        val absCents = kotlin.math.abs(cents)
        val formattedAmount = Formatting.formatBrl(absCents)
        val actionText = if (isDeposit) "add $formattedAmount to" else "deduct $formattedAmount from"

        AlertDialog(
            onDismissRequest = { pendingBalanceAdjustment = null },
            title = { Text("Confirm Balance Adjustment", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to $actionText ${user.name}'s account balance?") },
            confirmButton = {
                Button(
                    onClick = {
                        val note = if (isDeposit) "Deposit via Admin" else "Withdrawal via Admin"
                        onAdjustBalance(user, absCents, note, isDeposit)
                        pendingBalanceAdjustment = null
                        moneyInput = ""
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDeposit) ColorSuccessEmerald else ColorDangerCrimson
                    )
                ) {
                    Text("Confirm", color = SurfaceWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingBalanceAdjustment = null }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    // Toggle Active Status Confirmation Dialog Prompt
    if (showToggleActiveConfirm) {
        val actionText = if (user.isActive) "deactivate" else "activate"
        AlertDialog(
            onDismissRequest = { showToggleActiveConfirm = false },
            title = { Text("Confirm Account Status Change", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to $actionText account '${user.name}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        onToggleActive(user)
                        showToggleActiveConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (user.isActive) ColorWarningAmber else ColorSuccessEmerald
                    )
                ) {
                    Text("Confirm", color = SurfaceWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showToggleActiveConfirm = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    // Delete User Confirmation Dialog Prompt
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Confirm Delete Account", fontWeight = FontWeight.Bold, color = ColorDangerCrimson) },
            text = { Text("Are you sure you want to delete account '${user.name}'? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteUser(user)
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorDangerCrimson)
                ) {
                    Text("Delete Account", color = SurfaceWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}

@Composable
private fun AdminTransactionsTab(
    transactions: List<Transaction>,
    language: Language,
    onReverse: (Transaction) -> Unit
) {
    val strings = I18n.get(language)
    Column(modifier = Modifier.fillMaxSize()) {
        Text("System Transactions & Strict Reversals (${transactions.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            items(transactions, key = { it.id }) { tx ->
                val dateStr = remember(tx.timestamp, language) { Formatting.formatTimestamp(tx.timestamp, language) }
                val isCancelled = tx.type == TransactionType.CANCELLATION || transactions.any { it.referenceTransactionId == tx.id }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceWhite,
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${tx.type.name} • ${tx.userNameSnapshot} • $dateStr", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Total: ${Formatting.formatBrl(tx.totalAmount)} • Note: ${tx.note ?: "-"}", fontSize = 13.sp, color = TextSecondaryMuted)
                        }

                        if (!isCancelled && tx.type != TransactionType.CANCELLATION) {
                            Button(
                                onClick = { onReverse(tx) },
                                colors = ButtonDefaults.buttonColors(containerColor = ColorDangerCrimson),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(strings.reversalBtn, fontSize = 12.sp, color = SurfaceWhite)
                            }
                        } else {
                            Text("CANCELED / REVERSED", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondaryMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminSettingsTab(
    settings: SystemSettings,
    language: Language,
    onSaveSettings: (SystemSettings) -> Unit
) {
    var adminPassword by remember { mutableStateOf(settings.adminPasswordHash) }
    var globalMarkup by remember { mutableStateOf(settings.globalMarkupPercent.toString()) }
    var usdRate by remember { mutableStateOf(settings.usdExchangeRate.toString()) }
    var eurRate by remember { mutableStateOf(settings.eurExchangeRate.toString()) }
    var inactivityTimeout by remember { mutableStateOf(settings.inactivityTimeoutMinutes.toString()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("System Settings", fontSize = 16.sp, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = adminPassword,
            onValueChange = { adminPassword = it },
            label = { Text("Admin Password") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = globalMarkup,
            onValueChange = { globalMarkup = it },
            label = { Text("Global Product Markup % (e.g. 10.0)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = usdRate,
            onValueChange = { usdRate = it },
            label = { Text("USD Exchange Rate (1 BRL = X USD, e.g. 0.18)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = eurRate,
            onValueChange = { eurRate = it },
            label = { Text("EUR Exchange Rate (1 BRL = X EUR, e.g. 0.16)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = inactivityTimeout,
            onValueChange = { inactivityTimeout = it },
            label = { Text("Inactivity Timeout (Minutes)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                val newSet = settings.copy(
                    adminPasswordHash = adminPassword,
                    globalMarkupPercent = globalMarkup.toDoubleOrNull() ?: settings.globalMarkupPercent,
                    usdExchangeRate = usdRate.toDoubleOrNull() ?: settings.usdExchangeRate,
                    eurExchangeRate = eurRate.toDoubleOrNull() ?: settings.eurExchangeRate,
                    inactivityTimeoutMinutes = inactivityTimeout.toIntOrNull() ?: settings.inactivityTimeoutMinutes
                )
                onSaveSettings(newSet)
            },
            colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Save System Settings", color = SurfaceWhite, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ProductEditDialog(
    product: Product,
    language: Language,
    onSave: (Product) -> Unit,
    onCancel: () -> Unit
) {
    val strings = I18n.get(language)
    var name by remember { mutableStateOf(product.name) }
    var basePriceBrl by remember { mutableStateOf((product.basePrice.toDouble() / 100.0).toString()) }
    var unitType by remember { mutableStateOf(product.unitType) }
    var stockQuantity by remember { mutableStateOf(product.stockQuantity.toString()) }
    var customMarkup by remember { mutableStateOf(product.customMarkupPercent?.toString() ?: "") }
    var barcodeCode by remember { mutableStateOf("") }
    var barcodeDesc by remember { mutableStateOf("") }
    var barcodeList by remember { mutableStateOf(product.barcodes) }

    Dialog(onDismissRequest = onCancel) {
        Surface(shape = RoundedCornerShape(16.dp), color = SurfaceWhite, modifier = Modifier.width(480.dp).wrapContentHeight()) {
            Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                Text(strings.editProduct, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Product Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(value = basePriceBrl, onValueChange = { basePriceBrl = it }, label = { Text("Base Price BRL (e.g. 8.00)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UnitType.entries.forEach { u ->
                        OutlinedButton(
                            onClick = { unitType = u },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = if (unitType == u) AccentNavy else SurfaceWhite, contentColor = if (unitType == u) SurfaceWhite else PrimaryNavy)
                        ) {
                            Text(u.name)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = stockQuantity, onValueChange = { stockQuantity = it }, label = { Text(if (unitType == UnitType.PIECE) "Stock (Units)" else "Stock (Grams, e.g. 15000)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = customMarkup, onValueChange = { customMarkup = it }, label = { Text("Custom Markup % (Optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                Spacer(modifier = Modifier.height(12.dp))
                Text("Barcodes (${barcodeList.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                barcodeList.forEach { b ->
                    Text("• ${b.code} (${b.description ?: "Default"})", fontSize = 12.sp)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = barcodeCode, onValueChange = { barcodeCode = it }, placeholder = { Text("Code") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = barcodeDesc, onValueChange = { barcodeDesc = it }, placeholder = { Text("Desc") }, modifier = Modifier.weight(1f), singleLine = true)
                    Button(onClick = {
                        if (barcodeCode.isNotBlank()) {
                            barcodeList = barcodeList + Barcode(barcodeCode.trim(), barcodeDesc.ifBlank { null })
                            barcodeCode = ""
                            barcodeDesc = ""
                        }
                    }) { Text("+ Add") }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text(strings.cancel) }
                    Button(
                        onClick = {
                            val priceCents = kotlin.math.round((basePriceBrl.replace(',', '.').toDoubleOrNull() ?: 0.0) * 100).toLong()
                            val stock = stockQuantity.toLongOrNull() ?: 0L
                            val markup = customMarkup.toDoubleOrNull()
                            val updated = product.copy(
                                name = name,
                                basePrice = priceCents,
                                unitType = unitType,
                                stockQuantity = stock,
                                customMarkupPercent = markup,
                                barcodes = barcodeList
                            )
                            onSave(updated)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentNavy)
                    ) {
                        Text(strings.save, color = SurfaceWhite)
                    }
                }
            }
        }
    }
}

@Composable
private fun UserEditDialog(
    user: User,
    language: Language,
    onSave: (User) -> Unit,
    onCancel: () -> Unit
) {
    val strings = I18n.get(language)
    val isNewUser = remember(user.id) { user.id.isBlank() || user.name.isBlank() }

    var name by remember { mutableStateOf(user.name) }
    var pin by remember { mutableStateOf(user.pin ?: "") }
    var barcode by remember { mutableStateOf(user.userBarcode ?: "") }
    var barcodeNumber by remember { mutableStateOf(user.userBarcodeNumber ?: "") }
    var selectedLang by remember { mutableStateOf(user.language) }
    var selectedSecondaryCurrency by remember { mutableStateOf(user.secondaryCurrency) }
    var initialBalanceInput by remember { mutableStateOf(if (isNewUser) "0,00" else Formatting.formatBrl(user.balance).replace("R$", "").trim()) }

    Dialog(onDismissRequest = onCancel) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceWhite,
            shadowElevation = 8.dp,
            modifier = Modifier.width(480.dp).wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = if (isNewUser) strings.addUser else strings.editUser,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavy
                )

                HorizontalDivider(color = DividerBorder)

                // User Name & PIN
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("User Name", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            placeholder = { Text("Full Name", fontSize = 13.sp, color = TextSecondaryMuted) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text("PIN (Optional)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = pin,
                            onValueChange = { pin = it },
                            placeholder = { Text("No PIN", fontSize = 13.sp, color = TextSecondaryMuted) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                // Barcode Symbol & Barcode ID
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Barcode Symbol", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = barcode,
                            onValueChange = {
                                barcode = it
                                if (barcodeNumber.isBlank()) barcodeNumber = it
                            },
                            placeholder = { Text("e.g. USR-001", fontSize = 13.sp, color = TextSecondaryMuted) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text("Barcode Number (ID)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = barcodeNumber,
                            onValueChange = { barcodeNumber = it },
                            placeholder = { Text("e.g. 100000000001", fontSize = 13.sp, color = TextSecondaryMuted) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                // Preferred Language Selection
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(strings.preferredLanguage, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Language.entries.forEach { lang ->
                            val isSel = selectedLang == lang
                            OutlinedButton(
                                onClick = { selectedLang = lang },
                                modifier = Modifier.weight(1f).height(38.dp),
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSel) AccentNavy else SurfaceWhite,
                                    contentColor = if (isSel) SurfaceWhite else PrimaryNavy
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("${lang.flagEmoji} ${lang.code.uppercase()}", fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Secondary Currency Selection
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(strings.secondaryCurrency, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            SecondaryCurrency.NONE to "None",
                            SecondaryCurrency.USD to "USD ($)",
                            SecondaryCurrency.EUR to "EUR (€)"
                        ).forEach { (curr, label) ->
                            val isSel = selectedSecondaryCurrency == curr
                            OutlinedButton(
                                onClick = { selectedSecondaryCurrency = curr },
                                modifier = Modifier.weight(1f).height(38.dp),
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSel) AccentNavy else SurfaceWhite,
                                    contentColor = if (isSel) SurfaceWhite else PrimaryNavy
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(label, fontSize = 11.sp)
                            }
                        }
                    }
                }

                // Initial Balance (if new user)
                if (isNewUser) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Initial Balance (R$)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = initialBalanceInput,
                            onValueChange = { initialBalanceInput = it },
                            placeholder = { Text("0,00", fontSize = 13.sp, color = TextSecondaryMuted) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                HorizontalDivider(color = DividerBorder)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(strings.cancel, fontSize = 14.sp)
                    }

                    Button(
                        onClick = {
                            val bCode = barcode.trim().ifBlank { null }
                            val bNum = barcodeNumber.trim().ifBlank { null }
                            val initialCents = if (isNewUser) {
                                val valDouble = initialBalanceInput.replace(',', '.').trim().toDoubleOrNull() ?: 0.0
                                kotlin.math.round(valDouble * 100.0).toLong()
                            } else {
                                user.balance
                            }

                            val updated = user.copy(
                                name = name.trim(),
                                pin = pin.trim().ifBlank { null },
                                userBarcode = if (bCode != null && bNum != null) bCode else null,
                                userBarcodeNumber = if (bCode != null && bNum != null) bNum else null,
                                language = selectedLang,
                                secondaryCurrency = selectedSecondaryCurrency,
                                balance = initialCents
                            )
                            onSave(updated)
                        },
                        enabled = name.isNotBlank(),
                        modifier = Modifier.weight(1f).height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(strings.save, color = SurfaceWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
