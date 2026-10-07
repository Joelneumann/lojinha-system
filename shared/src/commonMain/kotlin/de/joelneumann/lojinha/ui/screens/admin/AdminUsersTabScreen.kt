package de.joelneumann.lojinha.ui.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.key.*
import de.joelneumann.lojinha.ui.components.general.AppVerticalScrollbar
import de.joelneumann.lojinha.ui.utils.pageDown
import de.joelneumann.lojinha.ui.utils.pageUp
import kotlinx.coroutines.launch
import de.joelneumann.lojinha.ui.components.admin.AdminExpandableSection
import de.joelneumann.lojinha.ui.components.admin.AdminTopBar
import de.joelneumann.lojinha.ui.components.admin.users.AdminUserAccordionCard
import de.joelneumann.lojinha.ui.components.admin.users.DeactivatedUserCard
import de.joelneumann.lojinha.ui.components.admin.users.DeletedUserCard
import de.joelneumann.lojinha.ui.components.admin.users.UserCustomExpenseDialog
import de.joelneumann.lojinha.ui.components.admin.users.UserCustomIncomeDialog
import de.joelneumann.lojinha.ui.components.admin.users.UserEditDialog
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.ColorDangerCrimson
import de.joelneumann.lojinha.ui.theme.ColorWarningAmber
import de.joelneumann.lojinha.ui.theme.TextSecondaryMuted
import de.joelneumann.lojinha.ui.utils.containsIgnoreAccents
import de.joelneumann.lojinha.ui.utils.sortedByAccentInsensitive
import de.joelneumann.lojinha.ui.viewmodel.admin.AdminUsersViewModel
import androidx.compose.ui.window.DialogProperties
import de.joelneumann.lojinha.ui.utils.confirmationDialogKeys

@Composable
fun AdminUsersTabScreen(
    viewModel: AdminUsersViewModel,
    expandedUserId: String?,
    onRequestToggleExpand: (String?) -> Unit,
    onRequestExpandUser: (String) -> Unit,
    onUnsavedStateChanged: (Boolean) -> Unit,
    onRegisterPageScroller: (((Boolean) -> Boolean) -> Unit)? = null
) {
    val users by viewModel.users.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    val showUserModal by viewModel.showUserModal.collectAsState()
    val isSavingUser by viewModel.isSavingUser.collectAsState()
    val editUser by viewModel.editUser.collectAsState()
    val customExpenseUser by viewModel.customExpenseUser.collectAsState()
    val customIncomeUser by viewModel.customIncomeUser.collectAsState()
    val userErrorMessage by viewModel.userErrorMessage.collectAsState()

    val strings = I18n.current

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val isModalOpen = showUserModal || customExpenseUser != null || customIncomeUser != null

    LaunchedEffect(onRegisterPageScroller, isModalOpen) {
        onRegisterPageScroller?.invoke { isDown ->
            if (!isModalOpen) {
                coroutineScope.launch {
                    if (isDown) listState.pageDown() else listState.pageUp()
                }
                true
            } else false
        }
    }

    LaunchedEffect(Unit) {
        onUnsavedStateChanged(false)
    }

    val activeUsers = remember(users) { users.filter { it.isActive && !it.isDeleted }.sortedByAccentInsensitive { it.name } }
    val deactivatedUsers = remember(users) { users.filter { !it.isActive && !it.isDeleted }.sortedByAccentInsensitive { it.name } }
    val deletedUsers = remember(users) { users.filter { it.isDeleted }.sortedByAccentInsensitive { it.name } }

    val filteredActiveUsers = remember(activeUsers, searchQuery) {
        if (searchQuery.isBlank()) activeUsers
        else activeUsers.filter { u ->
            u.name.containsIgnoreAccents(searchQuery) ||
                    (u.userBarcodeNumber != null && u.userBarcodeNumber.contains(searchQuery, ignoreCase = true)) ||
                    (u.userBarcode != null && u.userBarcode.contains(searchQuery, ignoreCase = true))
        }.sortedByAccentInsensitive { it.name }
    }

    val filteredDeactivatedUsers = remember(deactivatedUsers, searchQuery) {
        if (searchQuery.isBlank()) deactivatedUsers
        else deactivatedUsers.filter { u ->
            u.name.containsIgnoreAccents(searchQuery) ||
                    (u.userBarcodeNumber != null && u.userBarcodeNumber.contains(searchQuery, ignoreCase = true)) ||
                    (u.userBarcode != null && u.userBarcode.contains(searchQuery, ignoreCase = true))
        }.sortedByAccentInsensitive { it.name }
    }

    val filteredDeletedUsers = remember(deletedUsers, searchQuery) {
        if (searchQuery.isBlank()) deletedUsers
        else deletedUsers.filter { u ->
            u.name.containsIgnoreAccents(searchQuery) ||
                    (u.userBarcodeNumber != null && u.userBarcodeNumber.contains(searchQuery, ignoreCase = true)) ||
                    (u.userBarcode != null && u.userBarcode.contains(searchQuery, ignoreCase = true))
        }.sortedByAccentInsensitive { it.name }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.PageDown -> {
                            if (!isModalOpen) {
                                coroutineScope.launch { listState.pageDown() }
                                true
                            } else false
                        }
                        Key.PageUp -> {
                            if (!isModalOpen) {
                                coroutineScope.launch { listState.pageUp() }
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        val openFirstResult = {
            if (filteredActiveUsers.isNotEmpty()) {
                onRequestExpandUser(filteredActiveUsers.first().id)
            }
        }

        AdminTopBar(
            searchQuery = searchQuery,
            onQueryChange = viewModel::updateSearchQuery,
            placeholder = strings.searchAccountAdminPlaceholder,
            countText = if (searchQuery.isBlank()) strings.accountsCountText(activeUsers.size) else strings.accountsCountText(filteredActiveUsers.size, activeUsers.size),
            onSearchSubmitted = openFirstResult,
            actionButtonText = strings.addUser,
            onActionButtonClick = viewModel::openNewUserModal
        )

        Spacer(modifier = Modifier.height(14.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 32.dp, end = 14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (filteredActiveUsers.isEmpty() && filteredDeactivatedUsers.isEmpty() && filteredDeletedUsers.isEmpty()) {
                    item(key = "empty-users-msg") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(strings.noMatchingAccounts, color = TextSecondaryMuted)
                        }
                    }
                } else {
                    items(filteredActiveUsers, key = { it.id }) { user ->
                        val isExpanded = expandedUserId == user.id
                        AdminUserAccordionCard(
                            user = user,
                            isExpanded = isExpanded,
                            onExpandToggle = { onRequestToggleExpand(user.id) },
                            onEditUser = { viewModel.openEditUserModal(it) },
                            onCustomExpense = { viewModel.openCustomExpenseModal(it) },
                            onCustomIncome = { viewModel.openCustomIncomeModal(it) },
                            onAdjustBalance = { u, absCents, note, isDeposit ->
                                val delta = if (isDeposit) absCents else -absCents
                                viewModel.adjustUserBalance(u.id, u.name, delta, note)
                            },
                            onToggleActive = viewModel::toggleUserActive,
                            onDeleteUser = { viewModel.softDeleteUser(it.id) }
                        )
                    }
                }

                if (filteredDeactivatedUsers.isNotEmpty()) {
                    item(key = "deactivated-users-section") {
                        Spacer(modifier = Modifier.height(16.dp))
                        AdminExpandableSection(
                            title = strings.deactivatedUsers,
                            countText = strings.accountsCountText(filteredDeactivatedUsers.size),
                            accentColor = ColorWarningAmber,
                            showLabel = strings.showDeactivatedUsers,
                            hideLabel = strings.hideDeactivatedUsers
                        ) {
                            filteredDeactivatedUsers.forEach { user ->
                                DeactivatedUserCard(
                                    user = user,
                                    onActivateUser = { viewModel.toggleUserActive(user) }
                                )
                            }
                        }
                    }
                }

                if (filteredDeletedUsers.isNotEmpty()) {
                    item(key = "deleted-users-section") {
                        Spacer(modifier = Modifier.height(16.dp))
                        AdminExpandableSection(
                            title = strings.deletedUsers,
                            countText = strings.accountsCountText(filteredDeletedUsers.size),
                            accentColor = ColorDangerCrimson,
                            showLabel = strings.showDeletedUsers,
                            hideLabel = strings.hideDeletedUsers
                        ) {
                            filteredDeletedUsers.forEach { user ->
                                DeletedUserCard(
                                    user = user,
                                    onRestoreUser = { viewModel.restoreUser(user.id) },
                                    allUsers = users
                                )
                            }
                        }
                    }
                }
            }

            AppVerticalScrollbar(
                scrollState = listState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(vertical = 4.dp)
            )
        }
    }

    if (showUserModal && editUser != null) {
        UserEditDialog(
            user = editUser!!,
            allUsers = users,
            onSave = { viewModel.saveUser(it) },
            onCancel = { viewModel.closeUserModal() },
            isSaving = isSavingUser
        )
    }

    if (customExpenseUser != null) {
        val targetUser = customExpenseUser!!
        UserCustomExpenseDialog(
            user = targetUser,
            onSubmit = { deltaCents, description ->
                viewModel.submitCustomExpense(targetUser, deltaCents, description)
            },
            onDismiss = { viewModel.closeCustomExpenseModal() }
        )
    }

    if (customIncomeUser != null) {
        val targetUser = customIncomeUser!!
        UserCustomIncomeDialog(
            user = targetUser,
            onSubmit = { amountCents, description ->
                viewModel.submitCustomIncome(targetUser, amountCents, description)
            },
            onDismiss = { viewModel.closeCustomIncomeModal() }
        )
    }

    if (userErrorMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearUserError() },
            containerColor = de.joelneumann.lojinha.ui.theme.SurfaceWhite,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
            modifier = Modifier.confirmationDialogKeys(
                onCancel = { viewModel.clearUserError() },
                onConfirm = { viewModel.clearUserError() }
            ),
            title = { Text(strings.auditLedgerTitle, fontWeight = FontWeight.Bold, color = de.joelneumann.lojinha.ui.theme.PrimaryNavy) },
            text = { Text(userErrorMessage!!, color = de.joelneumann.lojinha.ui.theme.PrimaryNavy) },
            confirmButton = {
                Button(
                    onClick = { viewModel.clearUserError() },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = de.joelneumann.lojinha.ui.theme.AccentNavy)
                ) {
                    Text(strings.ok, color = de.joelneumann.lojinha.ui.theme.SurfaceWhite, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
