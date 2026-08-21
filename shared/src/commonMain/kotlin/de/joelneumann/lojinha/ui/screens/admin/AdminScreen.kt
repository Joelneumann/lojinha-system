package de.joelneumann.lojinha.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

enum class AdminTab {
    PRODUCTS,
    USERS,
    TRANSACTIONS,
    SETTINGS
}

@Composable
fun AdminScreen(
    productsViewModel: AdminProductsViewModel,
    usersViewModel: AdminUsersViewModel,
    transactionsViewModel: AdminTransactionsViewModel,
    settingsViewModel: AdminSettingsViewModel,
    onExitAdmin: () -> Unit
) {
    val strings = I18n.current
    var currentTab by remember { mutableStateOf(AdminTab.PRODUCTS) }

    var expandedProductId by remember { mutableStateOf<String?>(null) }
    var expandedUserId by remember { mutableStateOf<String?>(null) }
    var expandedTransactionId by remember { mutableStateOf<String?>(null) }

    var hasUnsavedChanges by remember { mutableStateOf(false) }
    var pendingTabSwitch by remember { mutableStateOf<AdminTab?>(null) }
    var isExitAdminPending by remember { mutableStateOf(false) }

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

    Column(modifier = Modifier.fillMaxSize().background(SurfaceContainerLight)) {
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
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabItems = listOf(
                AdminTab.PRODUCTS to "📦 ${strings.tabProducts}",
                AdminTab.USERS to "👥 ${strings.tabUsers}",
                AdminTab.TRANSACTIONS to "💳 ${strings.tabTransactions}",
                AdminTab.SETTINGS to "⚙️ ${strings.tabSettings}"
            )

            tabItems.forEach { (tab, label) ->
                val isSelected = currentTab == tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) AccentNavy else SurfaceContainerHighLight)
                        .clickable { handleTabSwitchRequest(tab) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) SurfaceWhite else PrimaryNavy
                        )
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
                .padding(20.dp)
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

                AdminTab.SETTINGS -> {
                    AdminSettingsTabScreen(
                        viewModel = settingsViewModel,
                        onUnsavedStateChanged = { hasUnsaved -> hasUnsavedChanges = hasUnsaved }
                    )
                }
            }
        }
    }

    if (pendingTabSwitch != null || isExitAdminPending) {
        val targetName = if (isExitAdminPending) "Main Screen" else pendingTabSwitch?.name ?: ""
        AlertDialog(
            onDismissRequest = {
                pendingTabSwitch = null
                isExitAdminPending = false
            },
            title = {
                Text(
                    text = "⚠️ Unsaved Changes Warning",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorWarningAmber
                )
            },
            text = {
                Text(
                    text = "You have unsaved changes on this page. If you leave to $targetName, your unsaved changes will be discarded.\n\nAre you sure you want to discard changes and continue?",
                    fontSize = 14.sp,
                    color = TextSecondarySubtle
                )
            },
            confirmButton = {
                Button(
                    onClick = {
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
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorDangerCrimson)
                ) {
                    Text("Discard & Switch", color = SurfaceWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        pendingTabSwitch = null
                        isExitAdminPending = false
                    }
                ) {
                    Text("Keep Editing")
                }
            }
        )
    }
}
