package de.joelneumann.lojinha.ui.components.admin.users

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.domain.model.AvatarType
import de.joelneumann.lojinha.domain.model.Language
import de.joelneumann.lojinha.domain.model.SecondaryCurrency
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.domain.model.UserAvatarConfig
import de.joelneumann.lojinha.ui.components.admin.*
import de.joelneumann.lojinha.ui.components.userselection.PRESET_AVATAR_COLORS
import de.joelneumann.lojinha.ui.components.userselection.PRESET_AVATAR_EMOJIS
import de.joelneumann.lojinha.ui.components.userselection.UserAvatar
import de.joelneumann.lojinha.ui.components.userselection.parseHexColor
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

@Composable
fun AdminUserAccordionCard(
    user: User,
    allUsers: List<User>,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit,
    onSaveUser: (User) -> Unit,
    onAdjustBalance: (User, Long, String, Boolean) -> Unit,
    onToggleActive: (User) -> Unit,
    onDeleteUser: (User) -> Unit,
    onUnsavedStateChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = I18n.current

    var draftName by remember(user.id, user.name) { mutableStateOf(user.name) }
    var shouldResetPin by remember(user.id) { mutableStateOf(false) }
    var draftPin by remember(user.id) { mutableStateOf("") }
    var isPinVisible by remember { mutableStateOf(false) }

    var draftUserBarcode by remember(user.id, user.userBarcode) { mutableStateOf(user.userBarcode ?: "") }
    var draftUserBarcodeNumber by remember(user.id, user.userBarcodeNumber) { mutableStateOf(user.userBarcodeNumber ?: "") }
    var draftLanguage by remember(user.id, user.language) { mutableStateOf(user.language) }
    var draftSecondaryCurrency by remember(user.id, user.secondaryCurrency) { mutableStateOf(user.secondaryCurrency) }
    var draftAvatar by remember(user.id, user.avatar) { mutableStateOf(user.avatar) }

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
        draftName, shouldResetPin, draftPin, draftUserBarcode, draftUserBarcodeNumber, draftLanguage, draftSecondaryCurrency, draftAvatar, user
    ) {
        draftName != user.name ||
                shouldResetPin ||
                draftUserBarcode != (user.userBarcode ?: "") ||
                draftUserBarcodeNumber != (user.userBarcodeNumber ?: "") ||
                draftLanguage != user.language ||
                draftSecondaryCurrency != user.secondaryCurrency ||
                draftAvatar != user.avatar
    }

    LaunchedEffect(hasUnsaved, isExpanded) {
        if (isExpanded) {
            onUnsavedStateChanged(hasUnsaved)
        }
    }

    AdminAccordionCard(
        title = user.name,
        isExpanded = isExpanded,
        onExpandToggle = onExpandToggle,
        hasUnsaved = hasUnsaved,
        modifier = modifier,
        headerBadges = {
            if (!user.isActive) {
                AdminStatusBadge(
                    text = "Deactivated",
                    type = AdminBadgeType.DANGER
                )
            }
        },
        headerRightContent = {
            Text(
                text = "Balance: ${Formatting.formatBrl(user.balance)}",
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
            AdminLabeledField(
                label = "User Name",
                value = draftName,
                onValueChange = { draftName = it },
                modifier = Modifier.weight(1f)
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { shouldResetPin = !shouldResetPin }
                ) {
                    Checkbox(
                        checked = shouldResetPin,
                        onCheckedChange = { shouldResetPin = it }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Reset PIN / Password",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryNavy
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))

                if (shouldResetPin) {
                    OutlinedTextField(
                        value = draftPin,
                        onValueChange = { draftPin = it },
                        placeholder = { Text("New PIN (or blank for none)", fontSize = 13.sp, color = TextSecondaryMuted) },
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                        visualTransformation = if (isPinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isPinVisible = !isPinVisible }) {
                                Icon(
                                    imageVector = if (isPinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle PIN Visibility",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(8.dp)
                    )
                } else {
                    OutlinedTextField(
                        value = if (user.pin != null) "••••••••" else "No PIN set",
                        onValueChange = {},
                        enabled = false,
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledContainerColor = SurfaceContainerHighLight,
                            disabledTextColor = TextSecondaryMuted,
                            disabledBorderColor = DividerBorder
                        )
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AdminLabeledField(
                label = strings.barcodeSymbolLabel,
                value = draftUserBarcode,
                onValueChange = {
                    draftUserBarcode = it
                    if (draftUserBarcodeNumber.isBlank()) draftUserBarcodeNumber = it
                },
                placeholder = strings.barcodeSymbolPlaceholder,
                modifier = Modifier.weight(1f)
            )

            AdminLabeledField(
                label = strings.barcodeNumberIdLabel,
                value = draftUserBarcodeNumber,
                onValueChange = { draftUserBarcodeNumber = it },
                placeholder = strings.barcodeNumberIdPlaceholder,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(strings.profileAvatarColorTitle, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    UserAvatar(
                        user = user,
                        customAvatar = draftAvatar,
                        modifier = Modifier.size(44.dp),
                        fontSize = 20.sp
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = draftAvatar.type == AvatarType.INITIALS,
                            onClick = { draftAvatar = draftAvatar.copy(type = AvatarType.INITIALS) },
                            label = { Text(strings.initialsLabel, fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = draftAvatar.type == AvatarType.EMOJI,
                            onClick = { draftAvatar = draftAvatar.copy(type = AvatarType.EMOJI) },
                            label = { Text(strings.emojiLabel, fontSize = 12.sp) }
                        )
                    }
                }

                if (draftAvatar.type == AvatarType.EMOJI) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PRESET_AVATAR_EMOJIS.forEach { emoji ->
                            val isSelected = draftAvatar.emoji == emoji
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) AccentNavy.copy(alpha = 0.2f) else SurfaceWhite)
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) AccentNavy else DividerBorder,
                                        shape = CircleShape
                                    )
                                    .clickable { draftAvatar = draftAvatar.copy(emoji = emoji) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = emoji, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(strings.avatarColorTitle, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PRESET_AVATAR_COLORS.forEach { colorHex ->
                        val isSelected = draftAvatar.colorHex.equals(colorHex, ignoreCase = true)
                        val chipColor = parseHexColor(colorHex)
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(chipColor)
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) PrimaryNavy else SurfaceWhite.copy(alpha = 0.5f),
                                    shape = CircleShape
                                )
                                .clickable { draftAvatar = draftAvatar.copy(colorHex = colorHex) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = SurfaceWhite,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AdminSegmentedOptionsRow(
                label = strings.preferredLanguage,
                options = Language.entries,
                selected = draftLanguage,
                onSelect = { draftLanguage = it },
                optionContent = { lang ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        de.joelneumann.lojinha.ui.components.general.LanguageFlagIcon(language = lang, width = 16.dp, height = 11.dp)
                        Text(lang.code.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                },
                modifier = Modifier.weight(1f)
            )

            AdminSegmentedOptionsRow(
                label = strings.secondaryCurrency,
                options = SecondaryCurrency.entries,
                selected = draftSecondaryCurrency,
                onSelect = { draftSecondaryCurrency = it },
                optionLabel = { curr ->
                    when (curr) {
                        SecondaryCurrency.NONE -> "None"
                        SecondaryCurrency.USD -> "USD ($)"
                        SecondaryCurrency.EUR -> "EUR (€)"
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }

        if (isUserBarcodeIncomplete) {
            val missingMsg = if (isBarcodeSymbolFilled) "Barcode Number (ID) is missing!" else "Barcode Symbol is missing!"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = ColorWarningAmber,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "$missingMsg Both Barcode Symbol and Barcode Number (ID) must be filled together, or leave both empty.",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorWarningAmber
                )
            }
        }

        if (duplicateUser != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = ColorDangerCrimson,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Barcode '${barcodeToCheck}' is already assigned to user '${duplicateUser.name}'!",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorDangerCrimson
                )
            }
        }

        HorizontalDivider(color = DividerBorder)

        AdminCardActionsRow(
            hasUnsaved = hasUnsaved,
            onSave = {
                val bCode = draftUserBarcode.trim().ifBlank { null }
                val bNum = draftUserBarcodeNumber.trim().ifBlank { null }
                val finalPin = if (shouldResetPin) draftPin.trim().ifBlank { null } else user.pin
                val updatedUser = user.copy(
                    name = draftName.trim(),
                    pin = finalPin,
                    userBarcode = if (bCode != null && bNum != null) bCode else null,
                    userBarcodeNumber = if (bCode != null && bNum != null) bNum else null,
                    language = draftLanguage,
                    secondaryCurrency = draftSecondaryCurrency,
                    avatar = draftAvatar
                )
                onSaveUser(updatedUser)
                shouldResetPin = false
                draftPin = ""
                onUnsavedStateChanged(false)
            },
            onRevert = {
                draftName = user.name
                shouldResetPin = false
                draftPin = ""
                draftUserBarcode = user.userBarcode ?: ""
                draftUserBarcodeNumber = user.userBarcodeNumber ?: ""
                draftLanguage = user.language
                draftSecondaryCurrency = user.secondaryCurrency
                draftAvatar = user.avatar
                moneyInput = ""
                onUnsavedStateChanged(false)
            },
            saveEnabled = hasUnsaved && draftName.isNotBlank() && duplicateUser == null && !isUserBarcodeIncomplete,
            toggleStatusText = if (user.isActive) "Deactivate" else "Activate",
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
        val actionText = if (isDeposit) "add $formattedAmount to" else "deduct $formattedAmount from"

        AlertDialog(
            onDismissRequest = { pendingBalanceAdjustment = null },
            title = { Text(strings.confirmBalanceAdjustmentTitle, fontWeight = FontWeight.Bold) },
            text = { Text(strings.confirmBalanceAdjustmentMsg(actionText, user.name)) },
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
                    Text(strings.confirm, color = SurfaceWhite, fontWeight = FontWeight.Bold)
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
            title = { Text(strings.confirmUserStatusChangeTitle, fontWeight = FontWeight.Bold) },
            text = { Text(strings.confirmUserStatusChangeMsg(actionText, user.name)) },
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
                    Text(strings.confirm, color = SurfaceWhite, fontWeight = FontWeight.Bold)
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
            title = { Text(strings.confirmDeleteUserTitle, fontWeight = FontWeight.Bold, color = ColorDangerCrimson) },
            text = { Text(strings.confirmDeleteUserMsg(user.name)) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteUser(user)
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorDangerCrimson)
                ) {
                    Text(strings.yesDeleteUser, color = SurfaceWhite, fontWeight = FontWeight.Bold)
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
