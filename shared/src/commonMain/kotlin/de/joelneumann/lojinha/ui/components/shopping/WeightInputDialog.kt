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
import de.joelneumann.lojinha.domain.model.Language
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*

@Composable
fun WeightInputDialog(
    productName: String,
    weightInput: String,
    weightError: String?,
    language: Language,
    onWeightInputChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = I18n.get(language)
    val weightFocusRequester = remember { FocusRequester() }
    var showWeightTooltip by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        weightFocusRequester.requestFocus()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceWhite,
            modifier = modifier.width(420.dp).wrapContentHeight()
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

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = weightInput,
                    onValueChange = onWeightInputChange,
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
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
                        .fillMaxWidth()
                        .height(56.dp)
                        .focusRequester(weightFocusRequester)
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyUp && keyEvent.key == Key.Enter) {
                                onSubmit()
                                true
                            } else false
                        },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSubmit() })
                )

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
