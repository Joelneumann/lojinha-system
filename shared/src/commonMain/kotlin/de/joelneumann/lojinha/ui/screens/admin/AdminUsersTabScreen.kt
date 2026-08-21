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
import de.joelneumann.lojinha.ui.components.admin.AdminExpandableSection
import de.joelneumann.lojinha.ui.components.admin.AdminTopBar
import de.joelneumann.lojinha.ui.components.admin.users.AdminUserAccordionCard
import de.joelneumann.lojinha.ui.components.admin.users.DeactivatedUserCard
import de.joelneumann.lojinha.ui.components.admin.users.DeletedUserCard
import de.joelneumann.lojinha.ui.components.admin.users.DepositDialog
import de.joelneumann.lojinha.ui.components.admin.users.UserEditDialog
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.ColorDangerCrimson
import de.joelneumann.lojinha.ui.theme.ColorWarningAmber
import de.joelneumann.lojinha.ui.theme.TextSecondaryMuted
import de.joelneumann.lojinha.ui.viewmodel.admin.AdminUsersViewModel

@Composable
fun AdminUsersTabScreen(
    viewModel: AdminUsersViewModel,
    expandedUserId: String?,
    onRequestToggleExpand: (String?) -> Unit,
    onRequestExpandUser: (String) -> Unit,
    onUnsavedStateChanged: (Boolean) -> Unit
) {
    val users by viewModel.users.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    val showUserModal by viewModel.showUserModal.collectAsState()
    val editUser by viewModel.editUser.collectAsState()
    val depositUser by viewModel.depositUser.collectAsState()
    val depositAmountInput by viewModel.depositAmountInput.collectAsState()
    val depositNoteInput by viewModel.depositNoteInput.collectAsState()
    val userDeleteError by viewModel.userDeleteErrorMessage.collectAsState()

    val strings = I18n.current

    val activeUsers = remember(users) { users.filter { it.isActive && !it.isDeleted } }
    val deactivatedUsers = remember(users) { users.filter { !it.isActive && !it.isDeleted } }
    val deletedUsers = remember(users) { users.filter { it.isDeleted } }

    val filteredUsers = remember(activeUsers, searchQuery) {
        if (searchQuery.isBlank()) activeUsers
        else activeUsers.filter { u ->
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

        AdminTopBar(
            searchQuery = searchQuery,
            onQueryChange = viewModel::updateSearchQuery,
            placeholder = "🔍 Search account by name or barcode...",
            countText = if (searchQuery.isBlank()) "${activeUsers.size} Accounts" else "${filteredUsers.size} / ${activeUsers.size} Accounts",
            onSearchSubmitted = openFirstResult,
            actionButtonText = strings.addUser,
            onActionButtonClick = viewModel::openNewUserModal
        )

        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (filteredUsers.isEmpty()) {
                item(key = "empty-users-msg") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No matching active user accounts found.", color = TextSecondaryMuted)
                    }
                }
            } else {
                items(filteredUsers, key = { it.id }) { user ->
                    val isExpanded = expandedUserId == user.id
                    AdminUserAccordionCard(
                        user = user,
                        allUsers = users,
                        isExpanded = isExpanded,
                        onExpandToggle = { onRequestToggleExpand(user.id) },
                        onSaveUser = viewModel::saveUser,
                        onAdjustBalance = { u, absCents, note, isDeposit ->
                            val delta = if (isDeposit) absCents else -absCents
                            viewModel.adjustUserBalance(u.id, u.name, delta, note)
                        },
                        onToggleActive = viewModel::toggleUserActive,
                        onDeleteUser = { viewModel.softDeleteUser(it.id) },
                        onUnsavedStateChanged = onUnsavedStateChanged
                    )
                }
            }

            if (deactivatedUsers.isNotEmpty()) {
                item(key = "deactivated-users-section") {
                    Spacer(modifier = Modifier.height(16.dp))
                    AdminExpandableSection(
                        title = "⚠️ Deactivated Users",
                        countText = "${deactivatedUsers.size} ${if (deactivatedUsers.size == 1) "User" else "Users"}",
                        accentColor = ColorWarningAmber,
                        showLabel = "Show Deactivated Users",
                        hideLabel = "Hide Deactivated Users"
                    ) {
                        deactivatedUsers.forEach { user ->
                            DeactivatedUserCard(
                                user = user,
                                onActivateUser = { viewModel.toggleUserActive(user) }
                            )
                        }
                    }
                }
            }

            if (deletedUsers.isNotEmpty()) {
                item(key = "deleted-users-section") {
                    Spacer(modifier = Modifier.height(16.dp))
                    AdminExpandableSection(
                        title = "🗑️ Deleted Users",
                        countText = "${deletedUsers.size} ${if (deletedUsers.size == 1) "User" else "Users"}",
                        accentColor = ColorDangerCrimson,
                        showLabel = "Show Deleted Users",
                        hideLabel = "Hide Deleted Users"
                    ) {
                        deletedUsers.forEach { user ->
                            DeletedUserCard(
                                user = user,
                                onRestoreUser = { viewModel.restoreUser(user.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showUserModal && editUser != null) {
        UserEditDialog(
            user = editUser!!,
            allUsers = users,
            onSave = { viewModel.saveUser(it) },
            onCancel = { viewModel.closeUserModal() }
        )
    }

    if (depositUser != null) {
        DepositDialog(
            user = depositUser!!,
            amountInput = depositAmountInput,
            noteInput = depositNoteInput,
            onAmountChange = viewModel::updateDepositAmount,
            onNoteChange = viewModel::updateDepositNote,
            onSubmit = viewModel::submitDeposit,
            onDismiss = viewModel::closeDepositModal
        )
    }

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
