package de.joelneumann.lojinha.ui.components.veggie

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.joelneumann.lojinha.domain.model.VEGGIE_QTY_SCALE
import de.joelneumann.lojinha.domain.model.VeggieProduct
import de.joelneumann.lojinha.domain.model.VeggieQuantityScaling
import de.joelneumann.lojinha.domain.model.VeggieUnitType
import de.joelneumann.lojinha.ui.components.admin.AdminBadgeType
import de.joelneumann.lojinha.ui.components.admin.AdminStatusBadge
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting
import de.joelneumann.lojinha.ui.utils.formModalKeys
import de.joelneumann.lojinha.ui.utils.safeRequestFocus

@Composable
fun VeggieAmountInputDialog(
    product: VeggieProduct,
    unitType: VeggieUnitType?,
    initialScaledQuantity: Long,
    decimalSeparator: Char,
    previousScaledQuantity: Long = 0L,
    maxAllowedScaledQuantity: Long? = null,
    onDismiss: () -> Unit,
    onSubmit: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = I18n.current
    val allowDecimals = unitType?.allowDecimals ?: false
    val unitName = unitType?.name ?: "Unit"

    val initialText = remember(product.id, initialScaledQuantity) {
        if (initialScaledQuantity > 0L) {
            VeggieQuantityScaling.formatScaledQuantity(initialScaledQuantity, allowDecimals, decimalSeparator)
        } else {
            "1"
        }
    }

    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = initialText,
                selection = TextRange(0, initialText.length)
            )
        )
    }

    val focusRequester = remember { FocusRequester() }
    var hadFocus by remember { mutableStateOf(false) }
    var justFocused by remember { mutableStateOf(false) }

    val parsedQtyScaled = remember(textFieldValue.text, allowDecimals) {
        VeggieQuantityScaling.parseQuantityScaled(textFieldValue.text.trim(), allowDecimals)
    }
    val exceedsMax = maxAllowedScaledQuantity != null && parsedQtyScaled != null && parsedQtyScaled > maxAllowedScaledQuantity
    val canSubmit = parsedQtyScaled != null && parsedQtyScaled > 0L && !exceedsMax

    LaunchedEffect(Unit) {
        focusRequester.safeRequestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceWhite,
            modifier = modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .wrapContentHeight()
                .formModalKeys(
                    onCancel = onDismiss,
                    onConfirm = { if (canSubmit) onSubmit(parsedQtyScaled!!) },
                    confirmEnabled = canSubmit
                )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = strings.veggieAmountDialogTitle,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavy
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Product Card: Thumbnail + Info (Name, Unit badge, Price, Description)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceContainerLight,
                    border = BorderStroke(1.dp, DividerBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VeggieProductImage(
                            productId = product.id,
                            imageFileName = product.imageFileName,
                            fullSize = false,
                            contentDescription = product.name,
                            shape = RoundedCornerShape(10.dp),
                            placeholderIconSize = 28.dp,
                            modifier = Modifier.size(68.dp)
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = product.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryNavy,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                AdminStatusBadge(
                                    text = unitName,
                                    type = AdminBadgeType.NAVY
                                )
                            }

                            if (product.estimatedPriceCents != null) {
                                Text(
                                    text = "~ ${Formatting.formatBrl(product.estimatedPriceCents)} / $unitName",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = PrimaryNavy
                                )
                            }

                            if (!product.description.isNullOrBlank()) {
                                Text(
                                    text = product.description,
                                    fontSize = 12.sp,
                                    color = TextSecondaryMuted,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = textFieldValue,
                        onValueChange = { newValue ->
                            if (justFocused && newValue.text == textFieldValue.text) {
                                textFieldValue = newValue.copy(
                                    selection = TextRange(0, newValue.text.length)
                                )
                                justFocused = false
                            } else {
                                justFocused = false
                                val filteredText = filterVeggieAmountInput(newValue.text, allowDecimals)
                                if (filteredText == newValue.text) {
                                    textFieldValue = newValue
                                } else {
                                    textFieldValue = TextFieldValue(
                                        text = filteredText,
                                        selection = TextRange(filteredText.length)
                                    )
                                }
                            }
                        },
                        textStyle = LocalTextStyle.current.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                        singleLine = true,
                        placeholder = {
                            Text(
                                text = strings.veggieAmountDialogPlaceholder,
                                color = TextSecondaryMuted,
                                fontSize = 15.sp
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .focusRequester(focusRequester)
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
                                        onSubmit(parsedQtyScaled!!)
                                    }
                                    true
                                } else false
                            },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (allowDecimals) KeyboardType.Decimal else KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (canSubmit) {
                                    onSubmit(parsedQtyScaled!!)
                                }
                            }
                        )
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AccentNavy.copy(alpha = 0.10f),
                        border = BorderStroke(1.5.dp, AccentNavy.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .widthIn(min = 72.dp)
                            .height(56.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        ) {
                            Text(
                                text = unitName,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryNavy,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Under the input field: value of last week
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = TextSecondaryMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    val lastWeekText = if (previousScaledQuantity > 0L) {
                        val prevFormatted = VeggieQuantityScaling.formatScaledQuantity(
                            previousScaledQuantity,
                            allowDecimals,
                            decimalSeparator
                        )
                        strings.lastOrderBadge(prevFormatted, unitName)
                    } else {
                        strings.lastOrderNone
                    }
                    Text(
                        text = lastWeekText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondaryMuted
                    )
                }

                if (exceedsMax) {
                    Spacer(modifier = Modifier.height(10.dp))
                    val maxFormatted = VeggieQuantityScaling.formatScaledQuantity(
                        maxAllowedScaledQuantity!!,
                        allowDecimals,
                        decimalSeparator
                    )
                    Text(
                        text = strings.veggieAmountMaxExceededError(maxFormatted, unitName),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = ColorDangerCrimson,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

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
                        onClick = {
                            if (canSubmit) {
                                onSubmit(parsedQtyScaled!!)
                            }
                        },
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

/**
 * Strictly filters input:
 * - If integers only: allows digits 0-9 only.
 * - If decimals: allows digits 0-9 and at most one separator ('.' or ','), up to 3 decimal digits.
 */
internal fun filterVeggieAmountInput(input: String, allowDecimals: Boolean): String {
    if (!allowDecimals) {
        return input.filter { it.isDigit() }
    }

    val result = StringBuilder()
    var hasSeparator = false
    var decimalDigits = 0

    for (char in input) {
        if (char.isDigit()) {
            if (hasSeparator) {
                if (decimalDigits < 3) {
                    result.append(char)
                    decimalDigits++
                }
            } else {
                result.append(char)
            }
        } else if (char == '.' || char == ',') {
            if (!hasSeparator) {
                hasSeparator = true
                result.append(char)
            } else {
                break
            }
        }
    }
    return result.toString()
}
