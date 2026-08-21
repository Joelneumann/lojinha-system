package de.joelneumann.lojinha.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import de.joelneumann.lojinha.domain.model.Language
import de.joelneumann.lojinha.domain.model.SecondaryCurrency
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting
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

        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                placeholder = {
                    Text(
                        text = "🔍 Search account by name or barcode...",
                        color = TextSecondaryMuted,
                        fontSize = 14.sp
                    )
                },
                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
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

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SurfaceWhite,
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder)),
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxHeight()
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isBlank()) "${activeUsers.size} Accounts" else "${filteredUsers.size} / ${activeUsers.size} Accounts",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryNavy
                    )
                }
            }

            Button(
                onClick = viewModel::openNewUserModal,
                colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxHeight()
            ) {
                Text(strings.addUser, color = SurfaceWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

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
                    AdminDeactivatedUsersSection(
                        deactivatedUsers = deactivatedUsers,
                        onToggleActive = viewModel::toggleUserActive
                    )
                }
            }

            if (deletedUsers.isNotEmpty()) {
                item(key = "deleted-users-section") {
                    Spacer(modifier = Modifier.height(16.dp))
                    AdminDeletedUsersSection(
                        deletedUsers = deletedUsers,
                        onRestoreUser = { viewModel.restoreUser(it.id) }
                    )
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

@Composable
private fun AdminUserAccordionCard(
    user: User,
    allUsers: List<User>,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit,
    onSaveUser: (User) -> Unit,
    onAdjustBalance: (User, Long, String, Boolean) -> Unit,
    onToggleActive: (User) -> Unit,
    onDeleteUser: (User) -> Unit,
    onUnsavedStateChanged: (Boolean) -> Unit
) {
    val strings = I18n.current

    var draftName by remember(user.id, user.name) { mutableStateOf(user.name) }
    var draftPin by remember(user.id, user.pin) { mutableStateOf(user.pin ?: "") }
    var draftUserBarcode by remember(user.id, user.userBarcode) { mutableStateOf(user.userBarcode ?: "") }
    var draftUserBarcodeNumber by remember(user.id, user.userBarcodeNumber) { mutableStateOf(user.userBarcodeNumber ?: "") }
    var draftLanguage by remember(user.id, user.language) { mutableStateOf(user.language) }
    var draftSecondaryCurrency by remember(user.id, user.secondaryCurrency) { mutableStateOf(user.secondaryCurrency) }

    val barcodeToCheck = remember(draftUserBarcode, draftUserBarcodeNumber) {
        val b1 = draftUserBarcode.trim()
        val b2 = draftUserBarcodeNumber.trim()
        if (b2.isNotBlank()) b2 else b1
    }

    val duplicateUser = remember(barcodeToCheck, allUsers, user.id) {
        if (barcodeToCheck.isBlank()) null
        else allUsers.firstOrNull { u ->
            !u.isDeleted && u.id != user.id && (
                (u.userBarcodeNumber != null && u.userBarcodeNumber.equals(barcodeToCheck, ignoreCase = true)) ||
                (u.userBarcode != null && u.userBarcode.equals(barcodeToCheck, ignoreCase = true))
            )
        }
    }

    val isBarcodeSymbolFilled = draftUserBarcode.isNotBlank()
    val isBarcodeNumberFilled = draftUserBarcodeNumber.isNotBlank()
    val isUserBarcodeIncomplete = (isBarcodeSymbolFilled && !isBarcodeNumberFilled) || (!isBarcodeSymbolFilled && isBarcodeNumberFilled)

    var moneyInput by remember(user.id) { mutableStateOf("") }
    var pendingBalanceAdjustment by remember { mutableStateOf<Long?>(null) }
    var showToggleActiveConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

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

            if (isExpanded) {
                HorizontalDivider(color = DividerBorder)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
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
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                                modifier = Modifier.weight(1f).height(56.dp),
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
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                                modifier = Modifier.fillMaxWidth().height(56.dp),
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
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                                modifier = Modifier.fillMaxWidth().height(56.dp),
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
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                                modifier = Modifier.fillMaxWidth().height(56.dp),
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
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                                modifier = Modifier.fillMaxWidth().height(56.dp),
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

                    if (isUserBarcodeIncomplete) {
                        val missingMsg = if (isBarcodeSymbolFilled) "⚠️ Barcode Number (ID) is missing!" else "⚠️ Barcode Symbol is missing!"
                        Text(
                            text = "$missingMsg Both Barcode Symbol and Barcode Number (ID) must be filled together, or leave both empty.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorWarningAmber,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    if (duplicateUser != null) {
                        Text(
                            text = "❌ Barcode '${barcodeToCheck}' is already assigned to user '${duplicateUser.name}'!",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorDangerCrimson,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    HorizontalDivider(color = DividerBorder)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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
                                enabled = hasUnsaved && draftName.isNotBlank() && duplicateUser == null && !isUserBarcodeIncomplete,
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

                            if (hasUnsaved) {
                                OutlinedButton(
                                    onClick = {
                                        draftName = user.name
                                        draftPin = user.pin ?: ""
                                        draftUserBarcode = user.userBarcode ?: ""
                                        draftUserBarcodeNumber = user.userBarcodeNumber ?: ""
                                        draftLanguage = user.language
                                        draftSecondaryCurrency = user.secondaryCurrency
                                        moneyInput = ""
                                        onUnsavedStateChanged(false)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(42.dp)
                                ) {
                                    Text("↩️ Revert Changes", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                                }
                            }
                        }

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

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Confirm Delete Account", fontWeight = FontWeight.Bold, color = ColorDangerCrimson) },
            text = { Text("Are you sure you want to delete user account '${user.name}'?\n\nThe user account will be soft-deleted and moved to the 'Deleted Users' section at the bottom of the page.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteUser(user)
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorDangerCrimson)
                ) {
                    Text("Yes, Delete User", color = SurfaceWhite, fontWeight = FontWeight.Bold)
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
private fun AdminDeactivatedUsersSection(
    deactivatedUsers: List<User>,
    onToggleActive: (User) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceContainerLight,
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(DividerBorder)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚠️ Deactivated Users",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorWarningAmber
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ColorWarningAmber.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "${deactivatedUsers.size} ${if (deactivatedUsers.size == 1) "User" else "Users"}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorWarningAmber,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = if (isExpanded) "▲ Hide Deactivated Users" else "▼ Show Deactivated Users",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentNavy
                )
            }

            if (isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    HorizontalDivider(color = DividerBorder)
                    Spacer(modifier = Modifier.height(14.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        deactivatedUsers.forEach { user ->
                            DeactivatedUserCard(
                                user = user,
                                onActivateUser = { onToggleActive(user) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeactivatedUserCard(
    user: User,
    onActivateUser: () -> Unit
) {
    var showActivateConfirm by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = SurfaceWhite,
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(DividerBorder)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(ColorWarningAmber.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = user.initials,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorWarningAmber
                    )
                }

                Column {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = user.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryNavy
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ColorWarningAmber.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Deactivated",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorWarningAmber,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "Balance: ${Formatting.formatBrl(user.balance)}" +
                                if (user.userBarcodeNumber != null) " • ID: ${user.userBarcodeNumber}" else "",
                        fontSize = 13.sp,
                        color = TextSecondaryMuted
                    )
                }
            }

            Button(
                onClick = { showActivateConfirm = true },
                colors = ButtonDefaults.buttonColors(containerColor = ColorSuccessEmerald),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(38.dp)
            ) {
                Text("⚡ Activate Account", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SurfaceWhite)
            }
        }
    }

    if (showActivateConfirm) {
        AlertDialog(
            onDismissRequest = { showActivateConfirm = false },
            title = { Text("Confirm Account Activation", fontWeight = FontWeight.Bold, color = ColorSuccessEmerald) },
            text = { Text("Are you sure you want to activate user account '${user.name}'?\n\nThis will move the account back into the active users list.") },
            confirmButton = {
                Button(
                    onClick = {
                        onActivateUser()
                        showActivateConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorSuccessEmerald)
                ) {
                    Text("Yes, Activate User", color = SurfaceWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showActivateConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AdminDeletedUsersSection(
    deletedUsers: List<User>,
    onRestoreUser: (User) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceContainerLight,
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(DividerBorder)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🗑️ Deleted Users",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorDangerCrimson
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ColorDangerCrimson.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "${deletedUsers.size} ${if (deletedUsers.size == 1) "User" else "Users"}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorDangerCrimson,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = if (isExpanded) "▲ Hide Deleted Users" else "▼ Show Deleted Users",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentNavy
                )
            }

            if (isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    HorizontalDivider(color = DividerBorder)
                    Spacer(modifier = Modifier.height(14.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        deletedUsers.forEach { user ->
                            DeletedUserCard(
                                user = user,
                                onRestoreUser = { onRestoreUser(user) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeletedUserCard(
    user: User,
    onRestoreUser: () -> Unit
) {
    var showRestoreConfirm by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = SurfaceWhite,
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(DividerBorder)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(ColorDangerCrimson.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = user.initials,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorDangerCrimson
                    )
                }

                Column {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = user.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryNavy
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ColorDangerCrimson.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "Deleted",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorDangerCrimson,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "Balance: ${Formatting.formatBrl(user.balance)}" +
                                if (user.userBarcodeNumber != null) " • ID: ${user.userBarcodeNumber}" else "",
                        fontSize = 13.sp,
                        color = TextSecondaryMuted
                    )
                }
            }

            Button(
                onClick = { showRestoreConfirm = true },
                colors = ButtonDefaults.buttonColors(containerColor = ColorSuccessEmerald),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(38.dp)
            ) {
                Text("♻️ Restore User", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SurfaceWhite)
            }
        }
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("Confirm Account Restoration", fontWeight = FontWeight.Bold, color = ColorSuccessEmerald) },
            text = { Text("Are you sure you want to restore user account '${user.name}'?\n\nThis will reactivate the account and move it back into the active users list.") },
            confirmButton = {
                Button(
                    onClick = {
                        onRestoreUser()
                        showRestoreConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorSuccessEmerald)
                ) {
                    Text("Yes, Restore User", color = SurfaceWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showRestoreConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun UserEditDialog(
    user: User,
    allUsers: List<User>,
    onSave: (User) -> Unit,
    onCancel: () -> Unit
) {
    val strings = I18n.current
    val isNewUser = remember(user.id) { user.id.isBlank() || user.name.isBlank() }

    var name by remember { mutableStateOf(user.name) }
    var pin by remember { mutableStateOf(user.pin ?: "") }
    var barcode by remember { mutableStateOf(user.userBarcode ?: "") }
    var barcodeNumber by remember { mutableStateOf(user.userBarcodeNumber ?: "") }
    var selectedLang by remember { mutableStateOf(user.language) }
    var selectedSecondaryCurrency by remember { mutableStateOf(user.secondaryCurrency) }

    val barcodeToCheck = remember(barcode, barcodeNumber) {
        val b1 = barcode.trim()
        val b2 = barcodeNumber.trim()
        if (b2.isNotBlank()) b2 else b1
    }

    val duplicateUser = remember(barcodeToCheck, allUsers, user.id) {
        if (barcodeToCheck.isBlank()) null
        else allUsers.firstOrNull { u ->
            !u.isDeleted && u.id != user.id && (
                (u.userBarcodeNumber != null && u.userBarcodeNumber.equals(barcodeToCheck, ignoreCase = true)) ||
                (u.userBarcode != null && u.userBarcode.equals(barcodeToCheck, ignoreCase = true))
            )
        }
    }

    val isBarcodeSymbolFilled = barcode.isNotBlank()
    val isBarcodeNumberFilled = barcodeNumber.isNotBlank()
    val isUserBarcodeIncomplete = (isBarcodeSymbolFilled && !isBarcodeNumberFilled) || (!isBarcodeSymbolFilled && isBarcodeNumberFilled)

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
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
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
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

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
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
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
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

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

                if (isUserBarcodeIncomplete) {
                    val missingMsg = if (isBarcodeSymbolFilled) "⚠️ Barcode Number (ID) is missing!" else "⚠️ Barcode Symbol is missing!"
                    Text(
                        text = "$missingMsg Both Barcode Symbol and Barcode Number (ID) must be filled together, or leave both empty.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorWarningAmber,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                if (duplicateUser != null) {
                    Text(
                        text = "❌ Barcode '${barcodeToCheck}' is already assigned to user '${duplicateUser.name}'!",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorDangerCrimson,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                HorizontalDivider(color = DividerBorder)

                val hasUserDialogChanges = name != user.name ||
                        pin != (user.pin ?: "") ||
                        barcode != (user.userBarcode ?: "") ||
                        barcodeNumber != (user.userBarcodeNumber ?: "") ||
                        selectedLang != user.language ||
                        selectedSecondaryCurrency != user.secondaryCurrency

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

                    if (!isNewUser && hasUserDialogChanges) {
                        OutlinedButton(
                            onClick = {
                                name = user.name
                                pin = user.pin ?: ""
                                barcode = user.userBarcode ?: ""
                                barcodeNumber = user.userBarcodeNumber ?: ""
                                selectedLang = user.language
                                selectedSecondaryCurrency = user.secondaryCurrency
                            },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("↩️ Revert", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                        }
                    }

                    Button(
                        onClick = {
                            val bCode = barcode.trim().ifBlank { null }
                            val bNum = barcodeNumber.trim().ifBlank { null }
                            val userBalance = if (isNewUser) 0L else user.balance

                            val updated = user.copy(
                                name = name.trim(),
                                pin = pin.trim().ifBlank { null },
                                userBarcode = if (bCode != null && bNum != null) bCode else null,
                                userBarcodeNumber = if (bCode != null && bNum != null) bNum else null,
                                language = selectedLang,
                                secondaryCurrency = selectedSecondaryCurrency,
                                balance = userBalance
                            )
                            onSave(updated)
                        },
                        enabled = name.isNotBlank() && duplicateUser == null && !isUserBarcodeIncomplete,
                        modifier = Modifier.weight(1f).height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (isNewUser) strings.addUser else strings.save, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DepositDialog(
    user: User,
    amountInput: String,
    noteInput: String,
    onAmountChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onSubmit: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = I18n.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceWhite,
            modifier = Modifier.width(400.dp).wrapContentHeight()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "${strings.depositWithdraw} (${user.name})",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavy
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(text = "Amount (BRL):", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = onAmountChange,
                    placeholder = { Text("e.g. 50,00 or 10.50", fontSize = 14.sp) },
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(text = "Note / Reason:", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = noteInput,
                    onValueChange = onNoteChange,
                    placeholder = { Text("e.g. Cash deposit via Admin", fontSize = 14.sp) },
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { onSubmit(true) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ColorSuccessEmerald)
                    ) {
                        Text("+ Deposit", color = SurfaceWhite, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { onSubmit(false) },
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
