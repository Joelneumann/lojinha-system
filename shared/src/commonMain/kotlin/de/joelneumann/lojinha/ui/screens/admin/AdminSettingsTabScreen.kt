package de.joelneumann.lojinha.ui.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.domain.model.SystemSettings
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.viewmodel.admin.AdminSettingsViewModel

@Composable
fun AdminSettingsTabScreen(
    viewModel: AdminSettingsViewModel,
    onUnsavedStateChanged: (Boolean) -> Unit
) {
    val settings by viewModel.settings.collectAsState()

    var newPassword by remember(settings) { mutableStateOf("") }
    var confirmPassword by remember(settings) { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    var globalMarkup by remember(settings) { mutableStateOf(settings.globalMarkupPercent.toString()) }
    var usdRate by remember(settings) { mutableStateOf(settings.usdExchangeRate.toString()) }
    var eurRate by remember(settings) { mutableStateOf(settings.eurExchangeRate.toString()) }
    var inactivityTimeout by remember(settings) { mutableStateOf(settings.inactivityTimeoutMinutes.toString()) }

    LaunchedEffect(settings) {
        newPassword = ""
        confirmPassword = ""
        globalMarkup = settings.globalMarkupPercent.toString()
        usdRate = settings.usdExchangeRate.toString()
        eurRate = settings.eurExchangeRate.toString()
        inactivityTimeout = settings.inactivityTimeoutMinutes.toString()
    }

    val isPasswordEntered = newPassword.isNotEmpty() || confirmPassword.isNotEmpty()
    val doPasswordsMatch = newPassword == confirmPassword
    val isPasswordValid = !isPasswordEntered || (newPassword.isNotBlank() && doPasswordsMatch)

    val hasFieldChanges = remember(settings, newPassword, confirmPassword, globalMarkup, usdRate, eurRate, inactivityTimeout) {
        newPassword.isNotEmpty() ||
                globalMarkup != settings.globalMarkupPercent.toString() ||
                usdRate != settings.usdExchangeRate.toString() ||
                eurRate != settings.eurExchangeRate.toString() ||
                inactivityTimeout != settings.inactivityTimeoutMinutes.toString()
    }

    LaunchedEffect(hasFieldChanges) {
        onUnsavedStateChanged(hasFieldChanges)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⚙️ System & Admin Settings",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavy
                )

                if (hasFieldChanges) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ColorWarningAmber.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "● Unsaved Edits",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorWarningAmber,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxHeight()
            ) {
                if (hasFieldChanges) {
                    OutlinedButton(
                        onClick = {
                            newPassword = ""
                            confirmPassword = ""
                            globalMarkup = settings.globalMarkupPercent.toString()
                            usdRate = settings.usdExchangeRate.toString()
                            eurRate = settings.eurExchangeRate.toString()
                            inactivityTimeout = settings.inactivityTimeoutMinutes.toString()
                            onUnsavedStateChanged(false)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Text("↩️ Revert Changes", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                    }
                }

                Button(
                    onClick = {
                        if (hasFieldChanges && isPasswordValid) {
                            val updatedSettings = settings.copy(
                                adminPasswordHash = if (newPassword.isNotBlank()) newPassword else settings.adminPasswordHash,
                                globalMarkupPercent = globalMarkup.toDoubleOrNull() ?: settings.globalMarkupPercent,
                                usdExchangeRate = usdRate.toDoubleOrNull() ?: settings.usdExchangeRate,
                                eurExchangeRate = eurRate.toDoubleOrNull() ?: settings.eurExchangeRate,
                                inactivityTimeoutMinutes = inactivityTimeout.toIntOrNull() ?: settings.inactivityTimeoutMinutes
                            )
                            viewModel.updateSystemSettings(updatedSettings)
                            newPassword = ""
                            confirmPassword = ""
                            onUnsavedStateChanged(false)
                        }
                    },
                    enabled = hasFieldChanges && isPasswordValid,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentNavy,
                        disabledContainerColor = SurfaceContainerHighLight
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Text(
                        text = if (hasFieldChanges) "💾 Save Settings" else "✓ Saved",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = SurfaceWhite
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "admin-security-card") {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceWhite,
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder)),
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("🔐 Admin Master Password", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("New Password:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = newPassword,
                                    onValueChange = { newPassword = it },
                                    placeholder = { Text("Enter new password", color = TextSecondaryMuted, fontSize = 14.sp) },
                                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { showPassword = !showPassword }) {
                                            Text(if (showPassword) "🙈" else "👁️", fontSize = 14.sp)
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = SurfaceWhite,
                                        unfocusedContainerColor = SurfaceWhite,
                                        focusedBorderColor = AccentNavy,
                                        unfocusedBorderColor = DividerBorder
                                    ),
                                    modifier = Modifier.fillMaxWidth().height(56.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text("Confirm New Password:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = confirmPassword,
                                    onValueChange = { confirmPassword = it },
                                    placeholder = { Text("Confirm new password", color = TextSecondaryMuted, fontSize = 14.sp) },
                                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { showPassword = !showPassword }) {
                                            Text(if (showPassword) "🙈" else "👁️", fontSize = 14.sp)
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = SurfaceWhite,
                                        unfocusedContainerColor = SurfaceWhite,
                                        focusedBorderColor = AccentNavy,
                                        unfocusedBorderColor = DividerBorder
                                    ),
                                    modifier = Modifier.fillMaxWidth().height(56.dp)
                                )
                            }
                        }

                        if (isPasswordEntered) {
                            if (!doPasswordsMatch) {
                                Text("❌ Passwords do not match", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ColorDangerCrimson)
                            } else if (newPassword.isNotBlank()) {
                                Text("✓ Passwords match", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ColorSuccessEmerald)
                            }
                        }
                    }
                }
            }

            item(key = "product-pricing-card") {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceWhite,
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder)),
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("🏷️ Product Pricing Rules", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Global Product Markup (%):", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = globalMarkup,
                                onValueChange = { globalMarkup = it },
                                placeholder = { Text("e.g. 10.0", color = TextSecondaryMuted, fontSize = 14.sp) },
                                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = SurfaceWhite,
                                    unfocusedContainerColor = SurfaceWhite,
                                    focusedBorderColor = AccentNavy,
                                    unfocusedBorderColor = DividerBorder
                                ),
                                modifier = Modifier.fillMaxWidth().height(56.dp)
                            )
                        }
                    }
                }
            }

            item(key = "currency-exchange-card") {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceWhite,
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder)),
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("🔱 Currency Exchange Rates", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("USD Rate (1 BRL = X USD):", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = usdRate,
                                    onValueChange = { usdRate = it },
                                    placeholder = { Text("e.g. 0.18", color = TextSecondaryMuted, fontSize = 14.sp) },
                                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = SurfaceWhite,
                                        unfocusedContainerColor = SurfaceWhite,
                                        focusedBorderColor = AccentNavy,
                                        unfocusedBorderColor = DividerBorder
                                    ),
                                    modifier = Modifier.fillMaxWidth().height(56.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text("EUR Rate (1 BRL = X EUR):", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = eurRate,
                                    onValueChange = { eurRate = it },
                                    placeholder = { Text("e.g. 0.16", color = TextSecondaryMuted, fontSize = 14.sp) },
                                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = SurfaceWhite,
                                        unfocusedContainerColor = SurfaceWhite,
                                        focusedBorderColor = AccentNavy,
                                        unfocusedBorderColor = DividerBorder
                                    ),
                                    modifier = Modifier.fillMaxWidth().height(56.dp)
                                )
                            }
                        }
                    }
                }
            }

            item(key = "kiosk-timers-card") {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceWhite,
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder)),
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("⏱️ Kiosk System Timers", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Inactivity Timeout (Minutes):", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = inactivityTimeout,
                                onValueChange = { inactivityTimeout = it },
                                placeholder = { Text("e.g. 3", color = TextSecondaryMuted, fontSize = 14.sp) },
                                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = SurfaceWhite,
                                    unfocusedContainerColor = SurfaceWhite,
                                    focusedBorderColor = AccentNavy,
                                    unfocusedBorderColor = DividerBorder
                                ),
                                modifier = Modifier.fillMaxWidth().height(56.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
