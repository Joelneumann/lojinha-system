package de.joelneumann.lojinha.ui.components.admin.users

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.ui.components.userselection.UserAvatar
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting

@Composable
fun UserCustomExpenseDialog(
    user: User,
    onSubmit: (deltaCents: Long, description: String) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = I18n.current

    var description by remember { mutableStateOf("") }
    var amountBrl by remember { mutableStateOf("") }

    val parsedDouble = remember(amountBrl) {
        amountBrl.replace(',', '.').trim().toDoubleOrNull()
    }
    val cents = remember(parsedDouble) {
        if (parsedDouble != null && parsedDouble != 0.0) {
            kotlin.math.round(parsedDouble * 100.0).toLong()
        } else {
            0L
        }
    }
    val canSubmit = remember(description, cents) {
        description.isNotBlank() && cents != 0L
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            dismissOnBackPress = false
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
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = strings.customExpenseDialogTitle,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavy
                )

                // User Snapshot Pill
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceContainerHighLight,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        UserAvatar(
                            user = user,
                            modifier = Modifier.size(38.dp),
                            fontSize = 16.sp
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = user.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryNavy
                            )
                            Text(
                                text = "${strings.balance}: ${Formatting.formatBrl(user.balance)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (user.balance >= 0) ColorSuccessEmerald else ColorDangerCrimson
                            )
                        }
                    }
                }

                // Description / Reason
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = strings.customExpenseDescriptionLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryNavy
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        placeholder = {
                            Text(
                                text = strings.customExpenseDescriptionPlaceholder,
                                fontSize = 13.sp,
                                color = TextSecondaryMuted
                            )
                        },
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceWhite,
                            unfocusedContainerColor = SurfaceWhite,
                            focusedBorderColor = AccentNavy,
                            unfocusedBorderColor = DividerBorder
                        ),
                        singleLine = true
                    )
                }

                // Amount / Balance Change (+/-)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = strings.balanceChangeLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryNavy
                    )
                    OutlinedTextField(
                        value = amountBrl,
                        onValueChange = { amountBrl = it },
                        placeholder = {
                            Text(
                                text = strings.amountPlaceholder,
                                fontSize = 13.sp,
                                color = TextSecondaryMuted
                            )
                        },
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceWhite,
                            unfocusedContainerColor = SurfaceWhite,
                            focusedBorderColor = AccentNavy,
                            unfocusedBorderColor = DividerBorder
                        ),
                        singleLine = true
                    )
                }

                HorizontalDivider(color = DividerBorder)

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(strings.cancel, fontSize = 14.sp)
                    }

                    Button(
                        onClick = {
                            if (canSubmit) {
                                onSubmit(cents, description.trim())
                            }
                        },
                        enabled = canSubmit,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (cents < 0) ColorDangerCrimson else ColorSuccessEmerald
                        )
                    ) {
                        Text(
                            text = when {
                                cents < 0 -> strings.customExpenseConfirmBtn
                                cents > 0 -> strings.customDepositConfirmBtn
                                else -> strings.confirm
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
