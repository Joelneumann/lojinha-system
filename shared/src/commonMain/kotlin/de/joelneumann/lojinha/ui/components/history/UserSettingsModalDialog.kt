package de.joelneumann.lojinha.ui.components.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import de.joelneumann.lojinha.domain.model.Language
import de.joelneumann.lojinha.domain.model.SecondaryCurrency
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*

@Composable
fun UserSettingsModalDialog(
    user: User,
    pinInput: String,
    selectedLanguage: Language,
    selectedSecondaryCurrency: SecondaryCurrency,
    onPinInputChange: (String) -> Unit,
    onLanguageSelect: (Language) -> Unit,
    onSecondaryCurrencySelect: (SecondaryCurrency) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = I18n.current
    var isPinVisible by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceWhite,
            modifier = modifier.width(460.dp).wrapContentHeight()
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = strings.userSettingsTitle,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavy
                )

                Spacer(modifier = Modifier.height(16.dp))

                // PIN Setting
                Text(text = strings.setPin, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = onPinInputChange,
                    placeholder = { Text("e.g. 1234 (leave blank for none)", fontSize = 13.sp, color = TextSecondaryMuted) },
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.5.sp),
                    visualTransformation = if (isPinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPinVisible = !isPinVisible }) {
                            Icon(
                                imageVector = if (isPinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle PIN Visibility",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Language Selection
                Text(text = strings.preferredLanguage, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Language.entries.forEach { lang ->
                        val isSel = selectedLanguage == lang
                        OutlinedButton(
                            onClick = { onLanguageSelect(lang) },
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
                                .clickable { onSecondaryCurrencySelect(curr) }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = selectedSecondaryCurrency == curr,
                                onClick = { onSecondaryCurrencySelect(curr) }
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
                        text = user.userBarcodeNumber ?: "No barcode assigned",
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
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(strings.cancel)
                    }

                    Button(
                        onClick = onSave,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentNavy)
                    ) {
                        Text(strings.save, color = SurfaceWhite, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
