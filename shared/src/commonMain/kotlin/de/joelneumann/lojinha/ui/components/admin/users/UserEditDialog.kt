package de.joelneumann.lojinha.ui.components.admin.users

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.joelneumann.lojinha.domain.model.Language
import de.joelneumann.lojinha.domain.model.SecondaryCurrency
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.domain.model.UserAvatarConfig
import de.joelneumann.lojinha.ui.components.admin.AdminLabeledField
import de.joelneumann.lojinha.ui.components.admin.AdminSegmentedOptionsRow
import de.joelneumann.lojinha.ui.components.general.ConfirmationDialog
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.formModalKeys

import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import de.joelneumann.lojinha.domain.model.AvatarType
import de.joelneumann.lojinha.ui.components.userselection.PRESET_AVATAR_COLORS
import de.joelneumann.lojinha.ui.components.userselection.PRESET_AVATAR_EMOJIS
import de.joelneumann.lojinha.ui.components.userselection.PlatformEmoji
import de.joelneumann.lojinha.ui.components.userselection.UserAvatar
import de.joelneumann.lojinha.ui.components.userselection.parseHexColor

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent

@Composable
fun UserEditDialog(
    user: User,
    allUsers: List<User>,
    onSave: (User) -> Unit,
    onCancel: () -> Unit,
    isSaving: Boolean = false
) {
    val strings = I18n.current
    val isNewUser = remember(user.id, allUsers) { allUsers.none { it.id == user.id } }

    var name by remember(user.id) { mutableStateOf(user.name) }
    var shouldResetPin by remember(user.id) { mutableStateOf(false) }
    var pin by remember(user.id) { mutableStateOf("") }
    var isPinVisible by remember(user.id) { mutableStateOf(false) }

    var barcode by remember(user.id) { mutableStateOf(user.userBarcode ?: "") }
    var barcodeNumber by remember(user.id) { mutableStateOf(user.userBarcodeNumber ?: "") }
    var selectedLang by remember(user.id) { mutableStateOf(user.language) }
    var selectedSecondaryCurrency by remember(user.id) { mutableStateOf(user.secondaryCurrency) }
    var selectedAvatar by remember(user.id, user.avatar) { mutableStateOf(user.avatar) }

    val trimmedBarcode = remember(barcode) { barcode.trim() }
    val trimmedBarcodeNumber = remember(barcodeNumber) { barcodeNumber.trim() }

    val duplicateBarcodeUser = remember(trimmedBarcode, allUsers, user.id) {
        if (trimmedBarcode.isBlank()) null
        else allUsers.firstOrNull { u ->
            u.id != user.id && (
                (u.userBarcode != null && u.userBarcode.equals(trimmedBarcode, ignoreCase = true)) ||
                (u.userBarcodeNumber != null && u.userBarcodeNumber.equals(trimmedBarcode, ignoreCase = true))
            )
        }
    }

    val duplicateNumberUser = remember(trimmedBarcodeNumber, allUsers, user.id) {
        if (trimmedBarcodeNumber.isBlank()) null
        else allUsers.firstOrNull { u ->
            u.id != user.id && (
                (u.userBarcode != null && u.userBarcode.equals(trimmedBarcodeNumber, ignoreCase = true)) ||
                (u.userBarcodeNumber != null && u.userBarcodeNumber.equals(trimmedBarcodeNumber, ignoreCase = true))
            )
        }
    }

    val duplicateConflict = duplicateBarcodeUser?.let { trimmedBarcode to it }
        ?: duplicateNumberUser?.let { trimmedBarcodeNumber to it }

    val isBarcodeSymbolFilled = barcode.isNotBlank()
    val isBarcodeNumberFilled = barcodeNumber.isNotBlank()
    val isUserBarcodeIncomplete = (isBarcodeSymbolFilled && !isBarcodeNumberFilled) || (!isBarcodeSymbolFilled && isBarcodeNumberFilled)

    val isPinChanged = if (isNewUser) pin.isNotBlank() else shouldResetPin
    val hasDialogChanges = name != user.name ||
            isPinChanged ||
            selectedLang != user.language ||
            selectedSecondaryCurrency != user.secondaryCurrency ||
            selectedAvatar != user.avatar ||
            barcode != (user.userBarcode ?: "") ||
            barcodeNumber != (user.userBarcodeNumber ?: "")

    val isModified = if (isNewUser) {
        name.isNotBlank() || pin.isNotBlank() || barcode.isNotBlank() || barcodeNumber.isNotBlank() ||
                selectedLang != user.language || selectedSecondaryCurrency != user.secondaryCurrency || selectedAvatar != user.avatar
    } else {
        hasDialogChanges
    }

    var showDiscardConfirm by remember { mutableStateOf(false) }

    val handleDismissRequest = {
        if (!isSaving) {
            if (isModified) {
                showDiscardConfirm = true
            } else {
                onCancel()
            }
        }
    }

    val canSave = !isSaving && name.isNotBlank() && duplicateConflict == null && !isUserBarcodeIncomplete && (isNewUser || hasDialogChanges)

    val handleSave = {
        if (canSave) {
            val bCode = barcode.trim().ifBlank { null }
            val bNum = barcodeNumber.trim().ifBlank { null }
            val userBalance = if (isNewUser) 0L else user.balance
            val rawNewPin = pin.trim().ifBlank { null }
            val finalPin = if (isNewUser) {
                rawNewPin?.let { de.joelneumann.lojinha.security.PasswordHasher.hash(it) }
            } else if (shouldResetPin) {
                rawNewPin?.let { de.joelneumann.lojinha.security.PasswordHasher.hash(it) }
            } else {
                user.pin
            }

            val updated = user.copy(
                name = name.trim(),
                pin = finalPin,
                userBarcode = if (bCode != null && bNum != null) bCode else null,
                userBarcodeNumber = if (bCode != null && bNum != null) bNum else null,
                language = selectedLang,
                secondaryCurrency = selectedSecondaryCurrency,
                avatar = selectedAvatar,
                balance = userBalance
            )
            onSave(updated)
        }
    }

    Dialog(
        onDismissRequest = handleDismissRequest,
        properties = DialogProperties(
            dismissOnClickOutside = true,
            dismissOnBackPress = true
        )
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceWhite,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 480.dp)
                .wrapContentHeight()
                .formModalKeys(
                    onCancel = handleDismissRequest,
                    onConfirm = handleSave,
                    confirmEnabled = canSave
                )
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isNewUser) strings.addUser else strings.editUser,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNavy
                    )

                    if (!isNewUser && hasDialogChanges) {
                        IconButton(
                            onClick = {
                                name = user.name
                                selectedLang = user.language
                                selectedSecondaryCurrency = user.secondaryCurrency
                                selectedAvatar = user.avatar
                                shouldResetPin = false
                                pin = ""
                                barcode = user.userBarcode ?: ""
                                barcodeNumber = user.userBarcodeNumber ?: ""
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = strings.revertChanges,
                                tint = AccentNavy,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = DividerBorder)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AdminLabeledField(
                        label = strings.userNameLabel,
                        value = name,
                        onValueChange = { name = it },
                        placeholder = strings.userNamePlaceholder,
                        modifier = Modifier.weight(1f)
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        if (isNewUser) {
                            Text(strings.initialPinOptional, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = pin,
                                onValueChange = { pin = it },
                                placeholder = { Text(strings.noPinPlaceholder, fontSize = 13.sp, color = TextSecondaryMuted) },
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                                visualTransformation = if (isPinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { isPinVisible = !isPinVisible }) {
                                        Icon(
                                            imageVector = if (isPinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = strings.togglePinVisibility,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(8.dp)
                            )
                        } else {
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
                                    text = strings.resetPin,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PrimaryNavy
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))

                            if (shouldResetPin) {
                                OutlinedTextField(
                                    value = pin,
                                    onValueChange = { pin = it },
                                    placeholder = { Text(strings.newPinOptionalPlaceholder, fontSize = 13.sp, color = TextSecondaryMuted) },
                                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                                    visualTransformation = if (isPinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { isPinVisible = !isPinVisible }) {
                                            Icon(
                                                imageVector = if (isPinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = strings.togglePinVisibility,
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
                                    value = if (user.pin != null) "••••••••" else strings.noPinSet,
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
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AdminLabeledField(
                        label = strings.barcodeSymbolLabel,
                        value = barcode,
                        onValueChange = {
                            val prev = barcode
                            barcode = it
                            if (barcodeNumber.isBlank() || barcodeNumber == prev) {
                                barcodeNumber = it
                            }
                        },
                        placeholder = strings.barcodeSymbolPlaceholder,
                        modifier = Modifier
                            .weight(1f)
                            .onPreviewKeyEvent {
                                it.key == Key.Enter || it.key == Key.NumPadEnter
                            }
                    )

                    AdminLabeledField(
                        label = strings.barcodeNumberIdLabel,
                        value = barcodeNumber,
                        onValueChange = { barcodeNumber = it },
                        placeholder = strings.barcodeNumberIdPlaceholder,
                        modifier = Modifier
                            .weight(1f)
                            .onPreviewKeyEvent {
                                it.key == Key.Enter || it.key == Key.NumPadEnter
                            }
                    )
                }

                // Profile Avatar & Color Customization
                Text(strings.profileAvatarColorTitle, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceContainerHighLight,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        UserAvatar(
                            user = user.copy(name = name),
                            customAvatar = selectedAvatar,
                            modifier = Modifier.size(52.dp),
                            fontSize = 22.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = { selectedAvatar = selectedAvatar.copy(type = AvatarType.INITIALS) },
                                modifier = Modifier.weight(1f).height(36.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (selectedAvatar.type == AvatarType.INITIALS) AccentNavy else SurfaceWhite,
                                    contentColor = if (selectedAvatar.type == AvatarType.INITIALS) SurfaceWhite else PrimaryNavy
                                ),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(strings.initialsLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { selectedAvatar = selectedAvatar.copy(type = AvatarType.EMOJI) },
                                modifier = Modifier.weight(1f).height(36.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (selectedAvatar.type == AvatarType.EMOJI) AccentNavy else SurfaceWhite,
                                    contentColor = if (selectedAvatar.type == AvatarType.EMOJI) SurfaceWhite else PrimaryNavy
                                ),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(strings.emojiLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (selectedAvatar.type == AvatarType.EMOJI) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PRESET_AVATAR_EMOJIS.forEach { emoji ->
                                    val isSelected = selectedAvatar.emoji == emoji
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) AccentNavy.copy(alpha = 0.2f) else SurfaceWhite)
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) AccentNavy else DividerBorder,
                                                shape = CircleShape
                                            )
                                            .clickable { selectedAvatar = selectedAvatar.copy(emoji = emoji) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        PlatformEmoji(emoji = emoji, fontSize = 16.sp)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PRESET_AVATAR_COLORS.forEach { colorHex ->
                                val isSelected = selectedAvatar.colorHex.equals(colorHex, ignoreCase = true)
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
                                        .clickable { selectedAvatar = selectedAvatar.copy(colorHex = colorHex) },
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

                AdminSegmentedOptionsRow(
                    label = strings.preferredLanguage,
                    options = Language.entries,
                    selected = selectedLang,
                    onSelect = { selectedLang = it },
                    optionContent = { lang ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            de.joelneumann.lojinha.ui.components.general.LanguageFlagIcon(language = lang, width = 16.dp, height = 11.dp)
                            Text(lang.code.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                )

                AdminSegmentedOptionsRow(
                    label = strings.secondaryCurrency,
                    options = SecondaryCurrency.entries,
                    selected = selectedSecondaryCurrency,
                    onSelect = { selectedSecondaryCurrency = it },
                    optionLabel = { curr ->
                        when (curr) {
                            SecondaryCurrency.NONE -> strings.secondaryCurrencyNone
                            SecondaryCurrency.USD -> strings.secondaryCurrencyUsd
                            SecondaryCurrency.EUR -> strings.secondaryCurrencyEur
                        }
                    }
                )

                if (isUserBarcodeIncomplete) {
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
                            text = strings.userBarcodeIncompleteWarning(isBarcodeSymbolFilled),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorWarningAmber
                        )
                    }
                }

                if (duplicateConflict != null) {
                    val (conflictingCode, conflictingUser) = duplicateConflict
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
                            text = strings.barcodeConflictAlreadyAssignedToUser(conflictingCode, conflictingUser.name),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorDangerCrimson
                        )
                    }
                }

                HorizontalDivider(color = DividerBorder)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = handleDismissRequest,
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(strings.cancel, fontSize = 14.sp)
                    }

                    Button(
                        onClick = handleSave,
                        enabled = canSave,
                        modifier = Modifier.weight(1f).height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = SurfaceWhite,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(if (isNewUser) strings.addUser else strings.save, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (showDiscardConfirm) {
            ConfirmationDialog(
                title = strings.discardChangesTitle,
                message = strings.discardChangesMsg,
                confirmText = strings.discard,
                cancelText = strings.cancel,
                confirmButtonColor = ColorDangerCrimson,
                onConfirm = {
                    showDiscardConfirm = false
                    onCancel()
                },
                onDismiss = {
                    showDiscardConfirm = false
                }
            )
        }
    }
}
