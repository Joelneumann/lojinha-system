package de.joelneumann.lojinha.ui.components.veggie

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.domain.model.VEGGIE_QTY_SCALE
import de.joelneumann.lojinha.domain.model.VeggieQuantityScaling
import de.joelneumann.lojinha.ui.theme.*

@Composable
fun InlineVeggieQuantityField(
    quantityScaled: Long,
    allowDecimals: Boolean,
    unitName: String,
    decimalSeparator: Char,
    enabled: Boolean,
    onQuantityChange: (Long) -> Unit,
    onCommit: (() -> Unit)? = null,
    onCommitPrevious: (() -> Unit)? = null,
    onFocused: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    fun formatCurrent(qty: Long): String {
        return if (qty > 0L) {
            VeggieQuantityScaling.formatScaledQuantity(qty, allowDecimals, decimalSeparator)
        } else {
            "0"
        }
    }

    var textFieldValue by remember {
        val formatted = formatCurrent(quantityScaled)
        mutableStateOf(
            TextFieldValue(
                text = formatted,
                selection = TextRange(0, formatted.length)
            )
        )
    }

    LaunchedEffect(quantityScaled) {
        if (!isFocused) {
            val formatted = formatCurrent(quantityScaled)
            if (textFieldValue.text != formatted) {
                textFieldValue = TextFieldValue(
                    text = formatted,
                    selection = TextRange(formatted.length)
                )
            }
        }
    }

    val isOrdered = quantityScaled > 0L

    val borderColor = when {
        isFocused -> AccentNavy
        isOrdered -> ColorSuccessEmerald
        else -> DividerBorder
    }

    val borderWidth = when {
        isFocused -> 1.5.dp
        isOrdered -> 1.5.dp
        else -> 1.dp
    }

    val backgroundColor = when {
        isFocused -> if (isOrdered) ColorSuccessEmerald.copy(alpha = 0.16f) else SurfaceWhite
        isOrdered -> ColorSuccessEmerald.copy(alpha = 0.12f)
        else -> SurfaceContainerLight
    }

    val textColor = when {
        isOrdered -> ColorSuccessEmerald
        isFocused -> PrimaryNavy
        else -> TextSecondaryMuted
    }

    val unitColor = when {
        isOrdered -> ColorSuccessEmerald
        isFocused -> PrimaryNavy
        else -> TextSecondaryMuted
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = backgroundColor,
        border = BorderStroke(borderWidth, borderColor),
        modifier = modifier
            .height(28.dp)
            .widthIn(min = 72.dp, max = 92.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            BasicTextField(
                value = textFieldValue,
                onValueChange = { newValue ->
                    val filtered = filterVeggieAmountInput(newValue.text, allowDecimals)
                    textFieldValue = newValue.copy(text = filtered)
                    val parsed = VeggieQuantityScaling.parseQuantityScaled(filtered, allowDecimals) ?: 0L
                    if (parsed != quantityScaled) {
                        onQuantityChange(parsed)
                    }
                },
                enabled = enabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (allowDecimals) KeyboardType.Decimal else KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = {
                        val parsed = VeggieQuantityScaling.parseQuantityScaled(textFieldValue.text, allowDecimals) ?: 0L
                        onQuantityChange(parsed)
                        onCommit?.invoke()
                    },
                    onDone = {
                        val parsed = VeggieQuantityScaling.parseQuantityScaled(textFieldValue.text, allowDecimals) ?: 0L
                        onQuantityChange(parsed)
                        onCommit?.invoke()
                    }
                ),
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    textAlign = TextAlign.End
                ),
                cursorBrush = SolidColor(if (isOrdered) ColorSuccessEmerald else AccentNavy),
                modifier = Modifier
                    .weight(1f)
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .onFocusChanged { focusState ->
                        val wasFocused = isFocused
                        isFocused = focusState.isFocused
                        if (focusState.isFocused) {
                            onFocused?.invoke()
                            if (!wasFocused) {
                                val formatted = formatCurrent(quantityScaled)
                                textFieldValue = TextFieldValue(
                                    text = formatted,
                                    selection = TextRange(0, formatted.length)
                                )
                            }
                        } else if (wasFocused) {
                            val parsed = VeggieQuantityScaling.parseQuantityScaled(textFieldValue.text, allowDecimals) ?: 0L
                            onQuantityChange(parsed)
                            val cleaned = formatCurrent(parsed)
                            textFieldValue = TextFieldValue(text = cleaned, selection = TextRange(cleaned.length))
                        }
                    }
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            val step = if (allowDecimals) (VEGGIE_QTY_SCALE / 2) else VEGGIE_QTY_SCALE
                            when (event.key) {
                                Key.Tab -> {
                                    val parsed = VeggieQuantityScaling.parseQuantityScaled(textFieldValue.text, allowDecimals) ?: 0L
                                    onQuantityChange(parsed)
                                    if (event.isShiftPressed) {
                                        onCommitPrevious?.invoke()
                                    } else {
                                        onCommit?.invoke()
                                    }
                                    true
                                }
                                Key.DirectionUp -> {
                                    val current = VeggieQuantityScaling.parseQuantityScaled(textFieldValue.text, allowDecimals) ?: 0L
                                    val next = current + step
                                    onQuantityChange(next)
                                    val formatted = formatCurrent(next)
                                    textFieldValue = TextFieldValue(text = formatted, selection = TextRange(0, formatted.length))
                                    true
                                }
                                Key.DirectionDown -> {
                                    val current = VeggieQuantityScaling.parseQuantityScaled(textFieldValue.text, allowDecimals) ?: 0L
                                    val next = (current - step).coerceAtLeast(0L)
                                    onQuantityChange(next)
                                    val formatted = formatCurrent(next)
                                    textFieldValue = TextFieldValue(text = formatted, selection = TextRange(0, formatted.length))
                                    true
                                }
                                Key.Enter, Key.NumPadEnter -> {
                                    val parsed = VeggieQuantityScaling.parseQuantityScaled(textFieldValue.text, allowDecimals) ?: 0L
                                    onQuantityChange(parsed)
                                    onCommit?.invoke()
                                    true
                                }
                                else -> false
                            }
                        } else false
                    }
            )

            Text(
                text = unitName,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = unitColor,
                maxLines = 1
            )
        }
    }
}
