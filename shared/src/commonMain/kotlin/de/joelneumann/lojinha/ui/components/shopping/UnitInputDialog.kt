package de.joelneumann.lojinha.ui.components.shopping

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.formModalKeys
import de.joelneumann.lojinha.ui.utils.safeRequestFocus

@Composable
fun UnitInputDialog(
    productName: String,
    unitInput: String,
    unitError: String?,
    onUnitInputChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    maxAvailableQuantityText: String? = null
) {
    val strings = I18n.current
    val unitFocusRequester = remember { FocusRequester() }
    val parsedQty = unitInput.toLongOrNull()
    val canSubmit = unitError == null && unitInput.isNotBlank() && parsedQty != null && parsedQty > 0L

    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = unitInput,
                selection = TextRange(0, unitInput.length)
            )
        )
    }
    var hadFocus by remember { mutableStateOf(false) }
    var justFocused by remember { mutableStateOf(false) }

    LaunchedEffect(unitInput) {
        if (unitInput != textFieldValue.text) {
            textFieldValue = textFieldValue.copy(
                text = unitInput,
                selection = TextRange(0, unitInput.length)
            )
        }
    }

    LaunchedEffect(Unit) {
        unitFocusRequester.safeRequestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceWhite,
            modifier = modifier
                .width(420.dp)
                .wrapContentHeight()
                .formModalKeys(
                    onCancel = onDismiss,
                    onConfirm = { if (canSubmit) onSubmit() },
                    confirmEnabled = canSubmit
                )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = strings.unitDialogTitle,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavy
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = strings.unitDialogMsg(productName),
                    fontSize = 14.sp,
                    color = TextSecondarySubtle,
                    textAlign = TextAlign.Center
                )

                if (maxAvailableQuantityText != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceContainerHighLight,
                        border = BorderStroke(1.dp, DividerBorder)
                    ) {
                        Text(
                            text = strings.availableStockLabel(maxAvailableQuantityText),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondarySubtle,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        val digitsOnly = newValue.text.filter { it.isDigit() }
                        if (justFocused && newValue.text == textFieldValue.text) {
                            textFieldValue = newValue.copy(
                                selection = TextRange(0, newValue.text.length)
                            )
                            justFocused = false
                        } else {
                            justFocused = false
                            textFieldValue = newValue.copy(text = digitsOnly)
                            if (digitsOnly != unitInput) {
                                onUnitInputChange(digitsOnly)
                            }
                        }
                    },
                    textStyle = LocalTextStyle.current.copy(fontSize = 15.sp),
                    singleLine = true,
                    isError = unitError != null,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .focusRequester(unitFocusRequester)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                if (!hadFocus) {
                                    hadFocus = true
                                    justFocused = true
                                    textFieldValue = textFieldValue.copy(
                                        selection = TextRange(0, textFieldValue.text.length)
                                    )
                                }
                            } else {
                                hadFocus = false
                                justFocused = false
                            }
                        }
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown && (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)) {
                                if (canSubmit) {
                                    onSubmit()
                                }
                                true
                            } else false
                        },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { if (canSubmit) onSubmit() })
                )

                if (unitError != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(unitError, color = ColorDangerCrimson, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(strings.cancel)
                    }

                    Button(
                        onClick = onSubmit,
                        enabled = canSubmit,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentNavy)
                    ) {
                        Text(strings.confirm, color = SurfaceWhite)
                    }
                }
            }
        }
    }
}
