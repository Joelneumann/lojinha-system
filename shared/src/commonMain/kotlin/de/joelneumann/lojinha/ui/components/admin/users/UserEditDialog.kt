package de.joelneumann.lojinha.ui.components.admin.users

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import de.joelneumann.lojinha.domain.model.Language
import de.joelneumann.lojinha.domain.model.SecondaryCurrency
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.ui.components.admin.AdminLabeledField
import de.joelneumann.lojinha.ui.components.admin.AdminSegmentedOptionsRow
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*

import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

@Composable
fun UserEditDialog(
    user: User,
    allUsers: List<User>,
    onSave: (User) -> Unit,
    onCancel: () -> Unit
) {
    val strings = I18n.current
    val isNewUser = remember(user.id) { user.id.isBlank() || user.name.isBlank() }

    var name by remember { mutableStateOf(user.name) }
    var shouldResetPin by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var isPinVisible by remember { mutableStateOf(false) }

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
            modifier = Modifier.fillMaxWidth(0.95f).widthIn(max = 480.dp).wrapContentHeight()
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
                    AdminLabeledField(
                        label = "User Name",
                        value = name,
                        onValueChange = { name = it },
                        placeholder = "Full Name",
                        modifier = Modifier.weight(1f)
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        if (isNewUser) {
                            Text("Initial PIN (Optional)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = pin,
                                onValueChange = { pin = it },
                                placeholder = { Text("No PIN", fontSize = 13.sp, color = TextSecondaryMuted) },
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                                visualTransformation = if (isPinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { isPinVisible = !isPinVisible }) {
                                        Text(if (isPinVisible) "👁️" else "🙈", fontSize = 14.sp)
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
                                    text = "Reset PIN",
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
                                    placeholder = { Text("New PIN (or blank for none)", fontSize = 13.sp, color = TextSecondaryMuted) },
                                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                                    visualTransformation = if (isPinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { isPinVisible = !isPinVisible }) {
                                            Text(if (isPinVisible) "👁️" else "🙈", fontSize = 14.sp)
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
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AdminLabeledField(
                        label = "Barcode Symbol",
                        value = barcode,
                        onValueChange = {
                            barcode = it
                            if (barcodeNumber.isBlank()) barcodeNumber = it
                        },
                        placeholder = "e.g. USR-001",
                        modifier = Modifier.weight(1f)
                    )

                    AdminLabeledField(
                        label = "Barcode Number (ID)",
                        value = barcodeNumber,
                        onValueChange = { barcodeNumber = it },
                        placeholder = "e.g. 100000000001",
                        modifier = Modifier.weight(1f)
                    )
                }

                AdminSegmentedOptionsRow(
                    label = strings.preferredLanguage,
                    options = Language.entries,
                    selected = selectedLang,
                    onSelect = { selectedLang = it },
                    optionLabel = { "${it.flagEmoji} ${it.code.uppercase()}" }
                )

                AdminSegmentedOptionsRow(
                    label = strings.secondaryCurrency,
                    options = SecondaryCurrency.entries,
                    selected = selectedSecondaryCurrency,
                    onSelect = { selectedSecondaryCurrency = it },
                    optionLabel = { curr ->
                        when (curr) {
                            SecondaryCurrency.NONE -> "None"
                            SecondaryCurrency.USD -> "USD ($)"
                            SecondaryCurrency.EUR -> "EUR (€)"
                        }
                    }
                )

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
                        shouldResetPin ||
                        (isNewUser && pin.isNotBlank()) ||
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
                                shouldResetPin = false
                                pin = ""
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
                            val finalPin = if (isNewUser) pin.trim().ifBlank { null } else if (shouldResetPin) pin.trim().ifBlank { null } else user.pin

                            val updated = user.copy(
                                name = name.trim(),
                                pin = finalPin,
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
