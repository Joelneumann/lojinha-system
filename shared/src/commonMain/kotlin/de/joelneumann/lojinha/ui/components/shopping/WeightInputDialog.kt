package de.joelneumann.lojinha.ui.components.shopping

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting
import de.joelneumann.lojinha.ui.utils.formModalKeys
import de.joelneumann.lojinha.ui.utils.safeRequestFocus

@Composable
fun WeightInputDialog(
    productName: String,
    weightInput: String,
    weightError: String?,
    onWeightInputChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    maxAvailableQuantityText: String? = null
) {
    val strings = I18n.current
    val weightFocusRequester = remember { FocusRequester() }
    var showWeightTooltip by remember { mutableStateOf(false) }
    val canSubmit = weightError == null && weightInput.isNotBlank()

    LaunchedEffect(Unit) {
        weightFocusRequester.safeRequestFocus()
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
                    text = strings.weightDialogTitle,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavy
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = strings.weightDialogMsg(productName),
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

                val detectedUnit = Formatting.detectWeightUnit(weightInput)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = weightInput,
                        onValueChange = onWeightInputChange,
                        textStyle = LocalTextStyle.current.copy(fontSize = 15.sp),
                        singleLine = true,
                        isError = weightError != null,
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            IconButton(
                                onClick = { showWeightTooltip = !showWeightTooltip },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (showWeightTooltip) PrimaryNavy else Color.Transparent,
                                    border = BorderStroke(
                                        width = 1.5.dp,
                                        color = if (showWeightTooltip) PrimaryNavy else TextSecondaryMuted
                                    ),
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "i",
                                            color = if (showWeightTooltip) SurfaceWhite else TextSecondaryMuted,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            style = TextStyle(fontFamily = FontFamily.Serif)
                                        )
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .focusRequester(weightFocusRequester)
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown && (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)) {
                                    if (canSubmit) {
                                        onSubmit()
                                    }
                                    true
                                } else false
                            },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (canSubmit) onSubmit() })
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (detectedUnit != null) AccentNavy.copy(alpha = 0.12f) else SurfaceContainerLight,
                        border = BorderStroke(
                            width = if (detectedUnit != null) 1.5.dp else 1.dp,
                            color = if (detectedUnit != null) AccentNavy else DividerBorder
                        ),
                        modifier = Modifier
                            .width(68.dp)
                            .height(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = detectedUnit?.symbol ?: "—",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (detectedUnit != null) PrimaryNavy else TextSecondaryMuted
                            )
                        }
                    }
                }

                if (showWeightTooltip) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SurfaceContainerHighLight,
                        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(DividerBorder)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = strings.weightTooltip,
                            fontSize = 12.sp,
                            color = TextSecondarySubtle,
                            modifier = Modifier.padding(12.dp),
                            lineHeight = 16.sp
                        )
                    }
                }

                if (weightError != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(weightError, color = ColorDangerCrimson, fontSize = 12.sp)
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
