package de.joelneumann.lojinha.ui.components.admin.users

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.ui.components.admin.*
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting
import de.joelneumann.lojinha.ui.utils.confirmationDialogKeys

@Composable
fun AdminUserAccordionCard(
    user: User,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit,
    onEditUser: (User) -> Unit,
    onCustomExpense: (User) -> Unit,
    onAdjustBalance: (User, Long, String, Boolean) -> Unit,
    onToggleActive: (User) -> Unit,
    onDeleteUser: (User) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = I18n.current

    var moneyInput by remember(user.id) { mutableStateOf("") }
    var pendingBalanceAdjustment by remember { mutableStateOf<Long?>(null) }
    var showToggleActiveConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    AdminAccordionCard(
        title = user.name,
        isExpanded = isExpanded,
        onExpandToggle = onExpandToggle,
        hasUnsaved = false,
        modifier = modifier,
        headerBadges = {
            if (!user.isActive) {
                AdminStatusBadge(
                    text = strings.deactivated,
                    type = AdminBadgeType.DANGER
                )
            }
        },
        headerRightContent = {
            Text(
                text = "${strings.balance}: ${Formatting.formatBrl(user.balance)}",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (user.balance >= 0) ColorSuccessEmerald else ColorDangerCrimson
            )
        }
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
                    text = strings.balanceChangeLabel,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavy
                )

                OutlinedTextField(
                    value = moneyInput,
                    onValueChange = { moneyInput = it },
                    placeholder = { Text(strings.amountPlaceholder, fontSize = 13.sp, color = TextSecondaryMuted) },
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
                    Text(strings.adjustBalanceBtn, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SurfaceWhite)
                }
            }
        }

        AdminCardActionsRow(
            onEdit = { onEditUser(user) },
            onCustomExpense = { onCustomExpense(user) },
            toggleStatusText = if (user.isActive) strings.deactivate else strings.activate,
            onToggleStatus = { showToggleActiveConfirm = true },
            isStatusActive = user.isActive,
            onDelete = { showDeleteConfirm = true }
        )
    }

    if (pendingBalanceAdjustment != null) {
        val cents = pendingBalanceAdjustment!!
        val isDeposit = cents > 0
        val absCents = kotlin.math.abs(cents)
        val formattedAmount = Formatting.formatBrl(absCents)
        val dismissDialog = { pendingBalanceAdjustment = null }
        val confirmAdjustment = {
            val note = if (isDeposit) strings.depositViaAdmin else strings.debitViaAdmin
            onAdjustBalance(user, absCents, note, isDeposit)
            pendingBalanceAdjustment = null
            moneyInput = ""
        }

        AlertDialog(
            onDismissRequest = dismissDialog,
            properties = DialogProperties(
                dismissOnClickOutside = true,
                dismissOnBackPress = true
            ),
            modifier = Modifier.confirmationDialogKeys(onCancel = dismissDialog, onConfirm = confirmAdjustment),
            title = { Text(strings.confirmBalanceAdjustmentTitle, fontWeight = FontWeight.Bold) },
            text = { Text(strings.confirmBalanceAdjustmentMsg(isDeposit, formattedAmount, user.name)) },
            confirmButton = {
                Button(
                    onClick = confirmAdjustment,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDeposit) ColorSuccessEmerald else ColorDangerCrimson
                    )
                ) {
                    Text(strings.confirm, color = SurfaceWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = dismissDialog) {
                    Text(strings.cancel)
                }
            }
        )
    }

    if (showToggleActiveConfirm) {
        val dismissDialog = { showToggleActiveConfirm = false }
        val confirmToggle = {
            onToggleActive(user)
            showToggleActiveConfirm = false
        }

        AlertDialog(
            onDismissRequest = dismissDialog,
            properties = DialogProperties(
                dismissOnClickOutside = true,
                dismissOnBackPress = true
            ),
            modifier = Modifier.confirmationDialogKeys(onCancel = dismissDialog, onConfirm = confirmToggle),
            title = { Text(strings.confirmUserStatusChangeTitle, fontWeight = FontWeight.Bold) },
            text = { Text(strings.confirmUserStatusChangeMsg(user.isActive, user.name)) },
            confirmButton = {
                Button(
                    onClick = confirmToggle,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (user.isActive) ColorWarningAmber else ColorSuccessEmerald
                    )
                ) {
                    Text(strings.confirm, color = SurfaceWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = dismissDialog) {
                    Text(strings.cancel)
                }
            }
        )
    }

    if (showDeleteConfirm) {
        val dismissDialog = { showDeleteConfirm = false }
        val confirmDelete = {
            onDeleteUser(user)
            showDeleteConfirm = false
        }

        AlertDialog(
            onDismissRequest = dismissDialog,
            properties = DialogProperties(
                dismissOnClickOutside = true,
                dismissOnBackPress = true
            ),
            modifier = Modifier.confirmationDialogKeys(onCancel = dismissDialog, onConfirm = confirmDelete),
            title = { Text(strings.confirmDeleteUserTitle, fontWeight = FontWeight.Bold, color = ColorDangerCrimson) },
            text = { Text(strings.confirmDeleteUserMsg(user.name)) },
            confirmButton = {
                Button(
                    onClick = confirmDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = ColorDangerCrimson)
                ) {
                    Text(strings.yesDeleteUser, color = SurfaceWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = dismissDialog) {
                    Text(strings.cancel)
                }
            }
        )
    }
}
