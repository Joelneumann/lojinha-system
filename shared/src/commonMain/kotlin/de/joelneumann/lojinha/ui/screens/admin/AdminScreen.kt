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
import de.joelneumann.lojinha.domain.model.SystemSettings
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.ui.components.general.HeaderBar
import de.joelneumann.lojinha.ui.components.general.LogoutButton
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.viewmodel.admin.AdminProductsViewModel
import de.joelneumann.lojinha.ui.viewmodel.admin.AdminSettingsViewModel
import de.joelneumann.lojinha.ui.viewmodel.admin.AdminTransactionsViewModel
import de.joelneumann.lojinha.ui.viewmodel.admin.AdminUsersViewModel
import de.joelneumann.lojinha.ui.viewmodel.admin.AdminBulkBillingViewModel

import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.window.DialogProperties
import de.joelneumann.lojinha.ui.utils.confirmationDialogKeys
import de.joelneumann.lojinha.ui.utils.safeRequestFocus

enum class AdminTab {
    PRODUCTS,
    USERS,
    BULK_BILLING,
    TRANSACTIONS,
    SETTINGS
}

private data class AdminTabDefinition(
    val tab: AdminTab,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
    val key: Key,
    val keyLabel: String
)

@Composable
fun AdminScreen(
    productsViewModel: AdminProductsViewModel,
    usersViewModel: AdminUsersViewModel,
    transactionsViewModel: AdminTransactionsViewModel,
    bulkBillingViewModel: AdminBulkBillingViewModel? = null,
    settingsViewModel: AdminSettingsViewModel? = null,
    onExitAdmin: () -> Unit
) {
    val strings = I18n.current
    val focusRequester = remember { FocusRequester() }
    var currentTab by remember { mutableStateOf(AdminTab.PRODUCTS) }

    LaunchedEffect(Unit) {
        focusRequester.safeRequestFocus()
    }

    var expandedProductId by remember { mutableStateOf<String?>(null) }
    var expandedUserId by remember { mutableStateOf<String?>(null) }
    var expandedTransactionId by remember { mutableStateOf<String?>(null) }

    var hasUnsavedChanges by remember { mutableStateOf(false) }
    var pendingTabSwitch by remember { mutableStateOf<AdminTab?>(null) }
    var isExitAdminPending by remember { mutableStateOf(false) }

    val tabItems = remember(strings, bulkBillingViewModel, settingsViewModel) {
        buildList {
            add(AdminTabDefinition(AdminTab.PRODUCTS, Icons.Default.Inventory, strings.tabProducts, Key.F1, "F1"))
            add(AdminTabDefinition(AdminTab.USERS, Icons.Default.People, strings.tabUsers, Key.F2, "F2"))
            if (bulkBillingViewModel != null) {
                add(AdminTabDefinition(AdminTab.BULK_BILLING, Icons.AutoMirrored.Filled.ReceiptLong, strings.tabBulkBilling, Key.F3, "F3"))
            }
            add(AdminTabDefinition(AdminTab.TRANSACTIONS, Icons.Default.CreditCard, strings.tabTransactions, Key.F4, "F4"))
            if (settingsViewModel != null) {
                add(AdminTabDefinition(AdminTab.SETTINGS, Icons.Default.Settings, strings.tabSettings, Key.F5, "F5"))
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
            }
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
        val isMobile = maxWidth < 600.dp
        val isNarrow = maxWidth < 750.dp
        val isCompact = maxWidth < 950.dp

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
                            if (shortcutLabel != null && !isNarrow) {
                                Text(
                                    text = "($shortcutLabel)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = if (isSelected) SurfaceWhite.copy(alpha = 0.45f) else PrimaryNavy.copy(alpha = 0.35f),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                            if (isSelected && hasUnsavedChanges) {
                                Text(
                                    text = "●",
                                    fontSize = 10.sp,
                                    color = ColorWarningAmber
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
                    AdminTab.PRODUCTS -> {
                        AdminProductsTabScreen(
                            viewModel = productsViewModel,
                            expandedProductId = expandedProductId,
                            onRequestToggleExpand = { id ->
                                expandedProductId = if (expandedProductId == id) null else id
                            },
                            onRequestExpandProduct = { id -> expandedProductId = id },
                            onUnsavedStateChanged = { hasUnsaved -> hasUnsavedChanges = hasUnsaved }
                        )
                    }

                    AdminTab.USERS -> {
                        AdminUsersTabScreen(
                            viewModel = usersViewModel,
                            expandedUserId = expandedUserId,
                            onRequestToggleExpand = { id ->
                                expandedUserId = if (expandedUserId == id) null else id
                            },
                            onRequestExpandUser = { id -> expandedUserId = id },
                            onUnsavedStateChanged = { hasUnsaved -> hasUnsavedChanges = hasUnsaved }
                        )
                    }

                    AdminTab.TRANSACTIONS -> {
                        AdminTransactionsTabScreen(
                            viewModel = transactionsViewModel,
                            expandedTransactionId = expandedTransactionId,
                            onRequestToggleExpand = { id ->
                                expandedTransactionId = if (expandedTransactionId == id) null else id
                            },
                            onRequestExpandTransaction = { id -> expandedTransactionId = id }
                        )
                    }

                    AdminTab.BULK_BILLING -> {
                        if (bulkBillingViewModel != null) {
                            AdminBulkBillingTabScreen(
                                viewModel = bulkBillingViewModel,
                                onNavigateToTransactions = { handleTabSwitchRequest(AdminTab.TRANSACTIONS) }
                            )
                        }
                    }

                    AdminTab.SETTINGS -> {
                        if (settingsViewModel != null) {
                            AdminSettingsTabScreen(
                                viewModel = settingsViewModel,
                                onUnsavedStateChanged = { hasUnsaved -> hasUnsavedChanges = hasUnsaved }
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
}
