package de.joelneumann.lojinha.ui.components.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.joelneumann.lojinha.domain.model.AvatarType
import de.joelneumann.lojinha.domain.model.Language
import de.joelneumann.lojinha.domain.model.SecondaryCurrency
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.domain.model.UserAvatarConfig
import de.joelneumann.lojinha.ui.components.general.ConfirmationDialog
import de.joelneumann.lojinha.ui.components.userselection.PRESET_AVATAR_COLORS
import de.joelneumann.lojinha.ui.components.userselection.PRESET_AVATAR_EMOJIS
import de.joelneumann.lojinha.ui.components.userselection.PlatformEmoji
import de.joelneumann.lojinha.ui.components.userselection.UserAvatar
import de.joelneumann.lojinha.ui.components.userselection.parseHexColor
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.formModalKeys
import de.joelneumann.lojinha.ui.utils.safeRequestFocus
import de.joelneumann.lojinha.ui.utils.trackUserInteractions
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll

@Composable
fun UserSettingsModalDialog(
    user: User,
    pinInput: String,
    selectedLanguage: Language,
    selectedSecondaryCurrency: SecondaryCurrency,
    selectedAvatar: UserAvatarConfig,
    onPinInputChange: (String) -> Unit,
    onLanguageSelect: (Language) -> Unit,
    onSecondaryCurrencySelect: (SecondaryCurrency) -> Unit,
    onAvatarSelect: (UserAvatarConfig) -> Unit,
    onDismiss: () -> Unit,
    onSave: (removePin: Boolean) -> Unit,
    onUserInteracted: (force: Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val strings = I18n.current
    var isPinVisible by remember { mutableStateOf(false) }
    var isAvatarExpanded by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var shouldRemovePin by remember { mutableStateOf(false) }
    var confirmPinInput by remember { mutableStateOf("") }
    val dialogFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        dialogFocusRequester.safeRequestFocus()
    }

    val isPinEntering = pinInput.isNotEmpty()
    val isPinMatching = !isPinEntering || pinInput == confirmPinInput
    val canSave = (!shouldRemovePin && (!isPinEntering || (confirmPinInput.isNotEmpty() && isPinMatching))) || (shouldRemovePin && user.pin != null)

    val isPinActionChanged = if (user.pin != null) {
        shouldRemovePin || (pinInput.isNotBlank() && pinInput == confirmPinInput)
    } else {
        pinInput.isNotBlank() && pinInput == confirmPinInput
    }

    val hasPendingInputs = shouldRemovePin ||
            pinInput.isNotEmpty() ||
            confirmPinInput.isNotEmpty() ||
            (selectedLanguage != user.language) ||
            (selectedSecondaryCurrency != user.secondaryCurrency) ||
            (selectedAvatar != user.avatar)

    val hasChanges = isPinActionChanged ||
            (selectedLanguage != user.language) ||
            (selectedSecondaryCurrency != user.secondaryCurrency) ||
            (selectedAvatar != user.avatar)

    val handleDismissRequest = {
        onUserInteracted(true)
        if (hasPendingInputs) {
            showDiscardConfirm = true
        } else {
            onDismiss()
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
            modifier = modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 480.dp)
                .wrapContentHeight()
                .trackUserInteractions(onUserInteracted)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        onUserInteracted(true)
                    }
                    false
                }
                .focusRequester(dialogFocusRequester)
                .focusable()
                .formModalKeys(
                    onCancel = handleDismissRequest,
                    onConfirm = { if (hasChanges && canSave && !showDiscardConfirm) onSave(shouldRemovePin) },
                    confirmEnabled = hasChanges && canSave && !showDiscardConfirm
                )
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = strings.userSettingsTitle,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavy
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Profile Avatar & Color Customization Section (Collapsible - Standard Collapsed)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceContainerHighLight,
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Collapsible Header Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onUserInteracted(true)
                                    isAvatarExpanded = !isAvatarExpanded
                                }
                                .padding(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                UserAvatar(
                                    user = user,
                                    customAvatar = selectedAvatar,
                                    modifier = Modifier.size(38.dp),
                                    fontSize = 18.sp
                                )

                                Column {
                                    Text(
                                        text = strings.profileAvatarColorTitle,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = PrimaryNavy
                                    )
                                    Text(
                                        text = if (selectedAvatar.type == AvatarType.EMOJI) strings.emojiWithVal(selectedAvatar.emoji) else strings.initialsWithVal(user.initials),
                                        fontSize = 12.sp,
                                        color = TextSecondaryMuted
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (isAvatarExpanded) strings.hide else strings.edit,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AccentNavy
                                )
                                Icon(
                                    imageVector = if (isAvatarExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = AccentNavy,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Expanded Customization Panel
                        AnimatedVisibility(visible = isAvatarExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                HorizontalDivider(color = DividerBorder)
                                Spacer(modifier = Modifier.height(12.dp))

                                // Avatar Type Toggle (Initials vs Emoji)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            onUserInteracted(true)
                                            onAvatarSelect(selectedAvatar.copy(type = AvatarType.INITIALS))
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (selectedAvatar.type == AvatarType.INITIALS) AccentNavy else SurfaceWhite,
                                            contentColor = if (selectedAvatar.type == AvatarType.INITIALS) SurfaceWhite else PrimaryNavy
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(strings.initialsWithVal(user.initials), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            onUserInteracted(true)
                                            onAvatarSelect(selectedAvatar.copy(type = AvatarType.EMOJI))
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (selectedAvatar.type == AvatarType.EMOJI) AccentNavy else SurfaceWhite,
                                            contentColor = if (selectedAvatar.type == AvatarType.EMOJI) SurfaceWhite else PrimaryNavy
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(strings.emojiWithVal(selectedAvatar.emoji), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // Horizontally Scrollable Preset Emoji Selection Bar
                                if (selectedAvatar.type == AvatarType.EMOJI) {
                                    Spacer(modifier = Modifier.height(10.dp))
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
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isSelected) AccentNavy.copy(alpha = 0.2f) else SurfaceWhite)
                                                    .border(
                                                        width = if (isSelected) 2.dp else 1.dp,
                                                        color = if (isSelected) AccentNavy else DividerBorder,
                                                        shape = CircleShape
                                                    )
                                                    .clickable {
                                                        onUserInteracted(true)
                                                        onAvatarSelect(selectedAvatar.copy(emoji = emoji))
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                PlatformEmoji(emoji = emoji, fontSize = 18.sp)
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Background Color Chips Picker
                                Text(strings.avatarBackgroundColorTitle, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryMuted)
                                Spacer(modifier = Modifier.height(6.dp))
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
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(chipColor)
                                                .border(
                                                    width = if (isSelected) 3.dp else 1.dp,
                                                    color = if (isSelected) PrimaryNavy else SurfaceWhite.copy(alpha = 0.5f),
                                                    shape = CircleShape
                                                )
                                                .clickable {
                                                    onUserInteracted(true)
                                                    onAvatarSelect(selectedAvatar.copy(colorHex = colorHex))
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = SurfaceWhite,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // PIN Setting Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = strings.setPin, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)

                    if (user.pin != null) {
                        TextButton(
                            onClick = {
                                onUserInteracted(true)
                                shouldRemovePin = !shouldRemovePin
                                if (shouldRemovePin) {
                                    onPinInputChange("")
                                    confirmPinInput = ""
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Icon(
                                imageVector = if (shouldRemovePin) Icons.AutoMirrored.Filled.Undo else Icons.Default.LockOpen,
                                contentDescription = null,
                                tint = if (shouldRemovePin) AccentNavy else ColorDangerCrimson,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (shouldRemovePin) strings.cancel else strings.removePin,
                                color = if (shouldRemovePin) AccentNavy else ColorDangerCrimson,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (shouldRemovePin) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SurfaceContainerHighLight,
                        border = BorderStroke(1.dp, ColorDangerCrimson.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = ColorDangerCrimson,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = strings.pinWillBeRemovedNotice,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ColorDangerCrimson
                                )
                                Text(
                                    text = strings.pinRemovedWarningDesc,
                                    fontSize = 12.sp,
                                    color = TextSecondaryMuted
                                )
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = onPinInputChange,
                        placeholder = {
                            Text(
                                if (user.pin != null) strings.enterNewPinPlaceholder else strings.pinPlaceholder,
                                fontSize = 13.sp,
                                color = TextSecondaryMuted
                            )
                        },
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.5.sp),
                        visualTransformation = if (isPinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = {
                                onUserInteracted(true)
                                isPinVisible = !isPinVisible
                            }) {
                                Icon(
                                    imageVector = if (isPinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = strings.togglePinVisibility,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = if (pinInput.isNotEmpty()) ImeAction.Next else ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (hasChanges && canSave) onSave(false) }),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    )

                    AnimatedVisibility(visible = pinInput.isNotEmpty()) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            OutlinedTextField(
                                value = confirmPinInput,
                                onValueChange = {
                                    confirmPinInput = it
                                    onUserInteracted(true)
                                },
                                placeholder = {
                                    Text(
                                        text = strings.confirmPinPlaceholder,
                                        fontSize = 13.sp,
                                        color = TextSecondaryMuted
                                    )
                                },
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.5.sp),
                                visualTransformation = if (isPinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                isError = confirmPinInput.isNotEmpty() && !isPinMatching,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { if (hasChanges && canSave) onSave(false) }),
                                modifier = Modifier.fillMaxWidth().height(56.dp)
                            )

                            if (confirmPinInput.isNotEmpty() && !isPinMatching) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = strings.pinMismatchError,
                                    color = ColorDangerCrimson,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Language Selection
                Text(text = strings.preferredLanguage, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Language.entries.forEach { lang ->
                        val isSel = selectedLanguage == lang
                        OutlinedButton(
                            onClick = {
                                onUserInteracted(true)
                                onLanguageSelect(lang)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSel) AccentNavy else SurfaceWhite,
                                contentColor = if (isSel) SurfaceWhite else PrimaryNavy
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                de.joelneumann.lojinha.ui.components.general.LanguageFlagIcon(language = lang, width = 18.dp, height = 12.dp)
                                Text(lang.code.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Secondary Display Currency Selection
                Text(text = strings.secondaryCurrency, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        SecondaryCurrency.NONE to strings.secondaryCurrencyNone,
                        SecondaryCurrency.USD to strings.secondaryCurrencyUsd,
                        SecondaryCurrency.EUR to strings.secondaryCurrencyEur
                    ).forEach { (curr, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onUserInteracted(true)
                                    onSecondaryCurrencySelect(curr)
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = selectedSecondaryCurrency == curr,
                                onClick = {
                                    onUserInteracted(true)
                                    onSecondaryCurrencySelect(curr)
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, fontSize = 14.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Assigned Barcode ID (Read-Only)
                Text(text = strings.assignedBarcodeId, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceContainerHighLight, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = user.userBarcodeNumber.takeUnless { it.isNullOrBlank() } ?: strings.noBarcodeAssigned,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondarySubtle
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = handleDismissRequest,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(strings.cancel)
                    }

                    Button(
                        onClick = {
                            onUserInteracted(true)
                            onSave(shouldRemovePin)
                        },
                        enabled = hasChanges && canSave,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentNavy)
                    ) {
                        Text(strings.save, fontWeight = FontWeight.Bold)
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
                    onDismiss()
                },
                onDismiss = {
                    showDiscardConfirm = false
                }
            )
        }
    }
}
