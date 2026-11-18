package de.joelneumann.lojinha.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.domain.model.Language
import de.joelneumann.lojinha.domain.model.SystemSettings
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.ui.components.general.HeaderBar
import de.joelneumann.lojinha.ui.components.general.LogoutButton
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.viewmodel.admin.AdminNotesViewModel
import de.joelneumann.lojinha.ui.viewmodel.admin.AdminProductsViewModel
import de.joelneumann.lojinha.ui.viewmodel.admin.AdminSettingsViewModel
import de.joelneumann.lojinha.ui.viewmodel.admin.AdminShoppingListViewModel
import de.joelneumann.lojinha.ui.viewmodel.admin.AdminTransactionsViewModel
import de.joelneumann.lojinha.ui.viewmodel.admin.AdminUsersViewModel
import de.joelneumann.lojinha.ui.viewmodel.admin.AdminBulkBillingViewModel
import de.joelneumann.lojinha.ui.viewmodel.admin.AdminVeggieViewModel
import de.joelneumann.lojinha.ui.components.admin.shoppinglist.ShoppingListQuantityDialog
import kotlinx.coroutines.delay

import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.window.DialogProperties
import de.joelneumann.lojinha.ui.utils.confirmationDialogKeys
import de.joelneumann.lojinha.ui.utils.safeRequestFocus

enum class AdminTab {
    NOTES,
    PRODUCTS,
    SHOPPING_LIST,
    USERS,
    BULK_BILLING,
    VEGGIE_ORDERS,
    TRANSACTIONS,
    SETTINGS
}

private fun fKeyForIndex(index: Int): Key = when (index) {
    1 -> Key.F1
    2 -> Key.F2
    3 -> Key.F3
    4 -> Key.F4
    5 -> Key.F5
    6 -> Key.F6
    7 -> Key.F7
    else -> Key.F8
}

private data class AdminTabDefinition(
    val tab: AdminTab,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
    val key: Key,
    val keyLabel: String,
    val badgeCount: Int? = null
)

@Composable
fun AdminScreen(
    notesViewModel: AdminNotesViewModel? = null,
    productsViewModel: AdminProductsViewModel,
    shoppingListViewModel: AdminShoppingListViewModel? = null,
    usersViewModel: AdminUsersViewModel,
    transactionsViewModel: AdminTransactionsViewModel,
    bulkBillingViewModel: AdminBulkBillingViewModel? = null,
    veggieViewModel: AdminVeggieViewModel? = null,
    settingsViewModel: AdminSettingsViewModel? = null,
    defaultSaveDirectory: String = "",
    onExecutePdfExport: ((user: User, destinationFolder: String, fromTimestamp: Long?, toTimestamp: Long?, language: Language) -> Unit)? = null,
    onExportShoppingListPdf: ((Language) -> Unit)? = null,
    onExportVeggieBuyerPdf: ((roundId: String, language: Language) -> Unit)? = null,
    onExportVeggieWorkerPdf: ((roundId: String, language: Language) -> Unit)? = null,
    onExportVeggieOrderFormPdf: ((language: Language) -> Unit)? = null,
    onOpenExportedPdf: ((String) -> Unit)? = null,
    onRevealExportedFolder: ((String) -> Unit)? = null,
    onExitAdmin: () -> Unit
) {
    val strings = I18n.current
    val focusRequester = remember { FocusRequester() }
    val initialTab = if (notesViewModel != null) AdminTab.NOTES else AdminTab.PRODUCTS
    var currentTab by remember { mutableStateOf(initialTab) }
    var tabFocusTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(currentTab) {
        if (currentTab == AdminTab.BULK_BILLING || currentTab == AdminTab.VEGGIE_ORDERS || currentTab == AdminTab.SETTINGS) {
            focusRequester.safeRequestFocus()
        }
    }

    val notes by (notesViewModel?.notes?.collectAsState() ?: remember { mutableStateOf(emptyList()) })
    val shoppingListItems by (shoppingListViewModel?.items?.collectAsState() ?: remember { mutableStateOf(emptyList()) })
    val pendingShoppingModal by (shoppingListViewModel?.pendingModalRequest?.collectAsState() ?: remember { mutableStateOf(null) })
    val shoppingStatusMessage by (shoppingListViewModel?.statusMessage?.collectAsState() ?: remember { mutableStateOf(null) })

    LaunchedEffect(shoppingStatusMessage) {
        if (shoppingStatusMessage != null) {
            delay(3000)
            shoppingListViewModel?.clearStatusMessage()
        }
    }

    var expandedProductId by remember { mutableStateOf<String?>(null) }
    var expandedUserId by remember { mutableStateOf<String?>(null) }
    var expandedTransactionId by remember { mutableStateOf<String?>(null) }

    var hasUnsavedChanges by remember { mutableStateOf(false) }
    var pendingTabSwitch by remember { mutableStateOf<AdminTab?>(null) }
    var isExitAdminPending by remember { mutableStateOf(false) }
    var activePageScroller by remember { mutableStateOf<((Boolean) -> Boolean)?>(null) }

    val tabItems = remember(strings, notes, shoppingListItems, shoppingListViewModel, bulkBillingViewModel, veggieViewModel, settingsViewModel, notesViewModel) {
        buildList {
            var fKeyIndex = 1
            if (notesViewModel != null) {
                add(
                    AdminTabDefinition(
                        tab = AdminTab.NOTES,
                        icon = Icons.Default.ChatBubbleOutline,
                        label = strings.tabNotes,
                        key = fKeyForIndex(fKeyIndex),
                        keyLabel = "F$fKeyIndex",
                        badgeCount = notes.size.takeIf { it > 0 }
                    )
                )
                fKeyIndex++
            }
            add(AdminTabDefinition(AdminTab.PRODUCTS, Icons.Default.Inventory, strings.tabProducts, fKeyForIndex(fKeyIndex), "F$fKeyIndex"))
            fKeyIndex++
            if (shoppingListViewModel != null) {
                add(
                    AdminTabDefinition(
                        tab = AdminTab.SHOPPING_LIST,
                        icon = Icons.Default.ShoppingCart,
                        label = strings.tabShoppingList,
                        key = fKeyForIndex(fKeyIndex),
                        keyLabel = "F$fKeyIndex",
                        badgeCount = shoppingListItems.size.takeIf { it > 0 }
                    )
                )
                fKeyIndex++
            }
            add(AdminTabDefinition(AdminTab.USERS, Icons.Default.People, strings.tabUsers, fKeyForIndex(fKeyIndex), "F$fKeyIndex"))
            fKeyIndex++
            if (bulkBillingViewModel != null) {
                add(AdminTabDefinition(AdminTab.BULK_BILLING, Icons.AutoMirrored.Filled.ReceiptLong, strings.tabBulkBilling, fKeyForIndex(fKeyIndex), "F$fKeyIndex"))
                fKeyIndex++
            }
            if (veggieViewModel != null) {
                add(AdminTabDefinition(AdminTab.VEGGIE_ORDERS, Icons.Default.Eco, strings.tabVeggieOrders, fKeyForIndex(fKeyIndex), "F$fKeyIndex"))
                fKeyIndex++
            }
            add(AdminTabDefinition(AdminTab.TRANSACTIONS, Icons.Default.CreditCard, strings.tabTransactions, fKeyForIndex(fKeyIndex), "F$fKeyIndex"))
            fKeyIndex++
            if (settingsViewModel != null) {
                add(AdminTabDefinition(AdminTab.SETTINGS, Icons.Default.Settings, strings.tabSettings, fKeyForIndex(fKeyIndex), "F$fKeyIndex"))
            }
        }
    }

    val handleTabSwitchRequest = { targetTab: AdminTab ->
        if (currentTab != targetTab) {
            if (hasUnsavedChanges) {
                pendingTabSwitch = targetTab
            } else {
                currentTab = targetTab
                expandedProductId = null
                expandedUserId = null
                expandedTransactionId = null
                activePageScroller = null
                tabFocusTrigger++
            }
        } else {
            expandedProductId = null
            expandedUserId = null
            expandedTransactionId = null
            tabFocusTrigger++
        }
    }

    val handleExitAdminRequest = {
        if (hasUnsavedChanges) {
            isExitAdminPending = true
        } else {
            onExitAdmin()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceContainerLight)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    val targetTabDef = tabItems.find { it.key == event.key }
                    if (targetTabDef != null) {
                        handleTabSwitchRequest(targetTabDef.tab)
                        true
                    } else if ((event.isCtrlPressed || event.isMetaPressed) && event.key == Key.F) {
                        if (pendingTabSwitch == null && !isExitAdminPending) {
                            expandedProductId = null
                            expandedUserId = null
                            expandedTransactionId = null
                            tabFocusTrigger++
                            true
                        } else false
                    } else if (event.key == Key.PageDown) {
                        activePageScroller?.invoke(true) ?: false
                    } else if (event.key == Key.PageUp) {
                        activePageScroller?.invoke(false) ?: false
                    } else if (event.key == Key.Escape) {
                        if (pendingTabSwitch != null || isExitAdminPending) {
                            false
                        } else if (expandedProductId != null || expandedUserId != null || expandedTransactionId != null) {
                            expandedProductId = null
                            expandedUserId = null
                            expandedTransactionId = null
                            true
                        } else {
                            handleExitAdminRequest()
                            true
                        }
                    } else {
                        false
                    }
                } else false
            }
    ) {
        val isMobile = maxWidth < MOBILE_BREAKPOINT
        val isNarrow = maxWidth < 750.dp
        val isCompact = maxWidth < 1100.dp

        Column(modifier = Modifier.fillMaxSize()) {
            HeaderBar(
                title = strings.adminPanel,
                actions = {
                    LogoutButton(
                        onClick = handleExitAdminRequest
                    )
                }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceWhite)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = if (isMobile) 10.dp else ScreenPadding, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabItems.forEach { item ->
                    val tab = item.tab
                    val icon = item.icon
                    val label = item.label
                    val isSelected = currentTab == tab
                    val shortcutLabel = item.keyLabel
                    Box(
                        modifier = Modifier
                            .then(if (isCompact) Modifier.wrapContentWidth() else Modifier.weight(1f))
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) AccentNavy else SurfaceContainerHighLight)
                            .focusProperties { canFocus = false }
                            .clickable { handleTabSwitchRequest(tab) }
                            .padding(
                                horizontal = if (isNarrow) 10.dp else 14.dp,
                                vertical = if (isNarrow) 8.dp else 10.dp
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(if (isNarrow) 4.dp else 6.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isSelected) SurfaceWhite else PrimaryNavy,
                                modifier = Modifier.size(if (isNarrow) 15.dp else 16.dp)
                            )
                            Text(
                                text = label,
                                fontSize = if (isNarrow) 13.sp else 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) SurfaceWhite else PrimaryNavy,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (item.badgeCount != null && item.badgeCount > 0) {
                                Text(
                                    text = "(${item.badgeCount})",
                                    fontSize = if (isNarrow) 12.sp else 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) SurfaceWhite else ColorWarningAmber,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                            if (!isNarrow) {
                                Text(
                                    text = "($shortcutLabel)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = if (isSelected) SurfaceWhite.copy(alpha = 0.45f) else PrimaryNavy.copy(alpha = 0.35f),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = DividerBorder)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isMobile) 10.dp else ScreenPadding)
            ) {
                when (currentTab) {
                    AdminTab.NOTES -> {
                        if (notesViewModel != null) {
                            AdminNotesTabScreen(
                                viewModel = notesViewModel,
                                onAddToShoppingListFromNote = shoppingListViewModel?.let { vm ->
                                    { productName, noteId -> vm.requestAddFromStockZeroNote(productName, noteId) }
                                },
                                onRegisterPageScroller = { scroller -> activePageScroller = scroller },
                                isExternalModalOpen = pendingShoppingModal != null,
                                focusTrigger = tabFocusTrigger
                            )
                        }
                    }

                    AdminTab.PRODUCTS -> {
                        AdminProductsTabScreen(
                            viewModel = productsViewModel,
                            expandedProductId = expandedProductId,
                            onRequestToggleExpand = { id ->
                                expandedProductId = if (expandedProductId == id) null else id
                            },
                            onRequestExpandProduct = { id -> expandedProductId = id },
                            onUnsavedStateChanged = { hasUnsaved -> hasUnsavedChanges = hasUnsaved },
                            onAddToShoppingList = shoppingListViewModel?.let { vm ->
                                { product -> vm.requestAddProduct(product) }
                            },
                            onRegisterPageScroller = { scroller -> activePageScroller = scroller },
                            isExternalModalOpen = pendingShoppingModal != null,
                            focusTrigger = tabFocusTrigger
                        )
                    }

                    AdminTab.SHOPPING_LIST -> {
                        if (shoppingListViewModel != null) {
                            AdminShoppingListTabScreen(
                                viewModel = shoppingListViewModel,
                                onExportPdf = onExportShoppingListPdf,
                                onOpenExportedPdf = onOpenExportedPdf,
                                onRevealExportedFolder = onRevealExportedFolder,
                                onRegisterPageScroller = { scroller -> activePageScroller = scroller },
                                focusTrigger = tabFocusTrigger
                            )
                        }
                    }

                    AdminTab.USERS -> {
                        AdminUsersTabScreen(
                            viewModel = usersViewModel,
                            expandedUserId = expandedUserId,
                            onRequestToggleExpand = { id ->
                                expandedUserId = if (expandedUserId == id) null else id
                            },
                            onRequestExpandUser = { id -> expandedUserId = id },
                            onUnsavedStateChanged = { hasUnsaved -> hasUnsavedChanges = hasUnsaved },
                            onRegisterPageScroller = { scroller -> activePageScroller = scroller },
                            defaultSaveDirectory = defaultSaveDirectory,
                            onExecutePdfExport = onExecutePdfExport,
                            onOpenExportedPdf = onOpenExportedPdf,
                            onRevealExportedFolder = onRevealExportedFolder,
                            focusTrigger = tabFocusTrigger
                        )
                    }

                    AdminTab.TRANSACTIONS -> {
                        AdminTransactionsTabScreen(
                            viewModel = transactionsViewModel,
                            expandedTransactionId = expandedTransactionId,
                            onRequestToggleExpand = { id ->
                                expandedTransactionId = if (expandedTransactionId == id) null else id
                            },
                            onRequestExpandTransaction = { id -> expandedTransactionId = id },
                            onRegisterPageScroller = { scroller -> activePageScroller = scroller },
                            focusTrigger = tabFocusTrigger
                        )
                    }

                    AdminTab.BULK_BILLING -> {
                        if (bulkBillingViewModel != null) {
                            AdminBulkBillingTabScreen(
                                viewModel = bulkBillingViewModel,
                                onNavigateToTransactions = { handleTabSwitchRequest(AdminTab.TRANSACTIONS) },
                                onRegisterPageScroller = { scroller -> activePageScroller = scroller }
                            )
                        }
                    }

                    AdminTab.VEGGIE_ORDERS -> {
                        if (veggieViewModel != null) {
                            AdminVeggieTabScreen(
                                viewModel = veggieViewModel,
                                onUnsavedStateChanged = { hasUnsaved -> hasUnsavedChanges = hasUnsaved },
                                onExportBuyerPdf = onExportVeggieBuyerPdf,
                                onExportWorkerPdf = onExportVeggieWorkerPdf,
                                onExportOrderFormPdf = onExportVeggieOrderFormPdf,
                                onOpenExportedPdf = onOpenExportedPdf,
                                onRevealExportedFolder = onRevealExportedFolder
                            )
                        }
                    }

                    AdminTab.SETTINGS -> {
                        if (settingsViewModel != null) {
                            AdminSettingsTabScreen(
                                viewModel = settingsViewModel,
                                onUnsavedStateChanged = { hasUnsaved -> hasUnsavedChanges = hasUnsaved },
                                onRegisterPageScroller = { scroller -> activePageScroller = scroller }
                            )
                        }
                    }
                }
            }
        }
    }

    if (pendingTabSwitch != null || isExitAdminPending) {
        val targetName = if (isExitAdminPending) {
            strings.mainScreen
        } else {
            tabItems.find { it.tab == pendingTabSwitch }?.label ?: ""
        }
        val dismissDialog = {
            pendingTabSwitch = null
            isExitAdminPending = false
        }
        val confirmDiscard = {
            hasUnsavedChanges = false
            veggieViewModel?.let { vm ->
                if (vm.editingPastRound.value != null) {
                    vm.closePastRoundEditor()
                } else {
                    vm.discardDraft()
                }
            }
            if (isExitAdminPending) {
                isExitAdminPending = false
                onExitAdmin()
            } else if (pendingTabSwitch != null) {
                currentTab = pendingTabSwitch!!
                pendingTabSwitch = null
                expandedProductId = null
                expandedUserId = null
                expandedTransactionId = null
            }
        }

        AlertDialog(
            onDismissRequest = dismissDialog,
            containerColor = SurfaceWhite,
            shape = RoundedCornerShape(16.dp),
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
            modifier = Modifier.confirmationDialogKeys(onCancel = dismissDialog, onConfirm = confirmDiscard),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = ColorWarningAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = strings.unsavedChangesTitle,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorWarningAmber
                    )
                }
            },
            text = {
                Text(
                    text = strings.unsavedChangesMsg(targetName),
                    fontSize = 14.sp,
                    color = TextSecondarySubtle
                )
            },
            confirmButton = {
                Button(
                    onClick = confirmDiscard,
                    colors = ButtonDefaults.buttonColors(containerColor = ColorDangerCrimson)
                ) {
                    Text(strings.discardAndSwitch, color = SurfaceWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = dismissDialog) {
                    Text(strings.keepEditing)
                }
            }
        )
    }

    pendingShoppingModal?.let { request ->
        shoppingListViewModel?.let { vm ->
            ShoppingListQuantityDialog(
                request = request,
                onDismiss = { vm.dismissModal() },
                onConfirm = { name, rawQty, removeNote ->
                    vm.confirmModalAdd(
                        name = name,
                        rawQuantityInput = rawQty,
                        product = request.product,
                        sourceNoteId = request.sourceNoteId,
                        existingItem = request.existingItem,
                        removeNote = removeNote,
                        clearSearchQuery = currentTab == AdminTab.SHOPPING_LIST
                    )
                }
            )
        }
    }

    shoppingStatusMessage?.let { toastMsg ->
        Box(
            modifier = Modifier.fillMaxSize().padding(bottom = 24.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                color = PrimaryNavy,
                shape = RoundedCornerShape(12.dp),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = ColorSuccessEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = toastMsg,
                        color = SurfaceWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
