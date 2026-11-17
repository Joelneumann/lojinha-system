package de.joelneumann.lojinha.ui.components.admin.shoppinglist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import de.joelneumann.lojinha.domain.model.Product
import de.joelneumann.lojinha.domain.model.UnitType
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.formModalKeys
import de.joelneumann.lojinha.ui.utils.safeRequestFocus
import de.joelneumann.lojinha.ui.viewmodel.admin.AdminShoppingListViewModel
import de.joelneumann.lojinha.ui.viewmodel.admin.ShoppingListModalRequest

@Composable
fun ShoppingListQuantityDialog(
    request: ShoppingListModalRequest,
    onDismiss: () -> Unit,
    onConfirm: (name: String, rawQuantity: String, removeNote: Boolean) -> Boolean
) {
    val strings = I18n.current
    val initialQty = remember(request) {
        extractInitialQuantityInput(request.existingItem?.quantity, request.product)
    }
    var qtyValue by remember(request) {
        mutableStateOf(
            TextFieldValue(
                text = initialQty,
                selection = TextRange(0, initialQty.length)
            )
        )
    }
    var removeNote by remember(request) { mutableStateOf(request.sourceNoteId != null) }
    var validationError by remember(request) { mutableStateOf<String?>(null) }

    val qtyFocusRequester = remember { FocusRequester() }
    LaunchedEffect(request) {
        qtyFocusRequester.safeRequestFocus()
    }

    val submit = {
        val valid = AdminShoppingListViewModel.formatQuantityInput(qtyValue.text, request.product) != null
        if (!valid) {
            validationError = strings.shoppingListInvalidQuantityError
        } else {
            val ok = onConfirm(request.customName, qtyValue.text, removeNote)
            if (!ok) {
                validationError = strings.shoppingListInvalidQuantityError
            }
        }
    }

    val placeholderText = when (request.product?.unitType) {
        UnitType.PIECE -> strings.shoppingListQuantityPlaceholderPiece
        UnitType.WEIGHT -> strings.shoppingListQuantityPlaceholderWeight
        null -> strings.shoppingListQuantityPlaceholderCustom
    }

    val unitSuffix = when (request.product?.unitType) {
        UnitType.PIECE -> strings.unitPieceBadge
        UnitType.WEIGHT -> "kg / g"
        null -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        shape = RoundedCornerShape(16.dp),
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
        modifier = Modifier.formModalKeys(
            onCancel = onDismiss,
            onConfirm = { submit() }
        ),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AddShoppingCart,
                    contentDescription = null,
                    tint = AccentNavy,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = strings.shoppingListModalTitle,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavy
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Item Title & Unit Type Badge
                Surface(
                    color = SurfaceContainerLight,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, DividerBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = request.customName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryNavy,
                            modifier = Modifier.weight(1f)
                        )
                        if (unitSuffix != null) {
                            Surface(
                                color = AccentNavy.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = unitSuffix,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AccentNavy,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                // Informational note if product is already on the list
                request.existingItem?.let { existing ->
                    Surface(
                        color = ColorWarningAmber.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ColorWarningAmber.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = ColorWarningAmber,
                                modifier = Modifier.size(18.dp).padding(top = 1.dp)
                            )
                            Text(
                                text = strings.shoppingListAlreadyExistsWarning(existing.quantity),
                                fontSize = 12.sp,
                                color = PrimaryNavy,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // Quantity Input
                OutlinedTextField(
                    value = qtyValue,
                    onValueChange = {
                        qtyValue = it
                        validationError = null
                    },
                    label = { Text(strings.shoppingListQuantityLabel) },
                    placeholder = { Text(placeholderText) },
                    singleLine = true,
                    isError = validationError != null,
                    supportingText = validationError?.let { err ->
                        { Text(err, color = ColorDangerCrimson) }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = when (request.product?.unitType) {
                            UnitType.PIECE -> KeyboardType.Number
                            UnitType.WEIGHT -> KeyboardType.Decimal
                            null -> KeyboardType.Text
                        },
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(qtyFocusRequester)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused && qtyValue.text.isNotEmpty()) {
                                qtyValue = qtyValue.copy(selection = TextRange(0, qtyValue.text.length))
                            }
                        }
                )

                // Optional Remove System Note checkbox
                if (request.sourceNoteId != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = removeNote,
                            onCheckedChange = { removeNote = it },
                            colors = CheckboxDefaults.colors(checkedColor = AccentNavy)
                        )
                        Text(
                            text = strings.shoppingListRemoveNoteCheckbox,
                            fontSize = 13.sp,
                            color = PrimaryNavy
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { submit() },
                colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (request.existingItem != null) strings.shoppingListOverwriteBtn else strings.shoppingListAddBtn,
                    color = SurfaceWhite,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(strings.cancel)
            }
        }
    )
}

private fun extractInitialQuantityInput(existingQuantity: String?, product: Product?): String {
    if (existingQuantity.isNullOrBlank()) return ""
    if (product == null) return existingQuantity
    return when (product.unitType) {
        UnitType.PIECE -> {
            val digits = existingQuantity.takeWhile { it.isDigit() }
            digits.ifBlank { existingQuantity }
        }
        UnitType.WEIGHT -> {
            existingQuantity.removeSuffix(" kg").trim()
        }
    }
}
