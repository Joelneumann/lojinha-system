package de.joelneumann.lojinha.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import de.joelneumann.lojinha.domain.model.Language
import de.joelneumann.lojinha.domain.model.SystemSettings
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.viewmodel.UserSelectionViewModel

@Composable
fun UserSelectionScreen(
    viewModel: UserSelectionViewModel,
    language: Language,
    settings: SystemSettings,
    onUserLoggedIn: (User) -> Unit,
    onNavigateToAdmin: () -> Unit
) {
    val strings = I18n.get(language)
    val users by viewModel.users.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedUserForPin by viewModel.selectedUserForPin.collectAsState()
    val pinInput by viewModel.pinInput.collectAsState()
    val pinError by viewModel.pinError.collectAsState()
    val showAdminAuthDialog by viewModel.showAdminAuthDialog.collectAsState()
    val adminPasswordInput by viewModel.adminPasswordInput.collectAsState()
    val adminPasswordError by viewModel.adminPasswordError.collectAsState()

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val filteredUsers = remember(users, searchQuery) {
        if (searchQuery.isBlank()) users
        else users.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceContainerLight)
            .padding(24.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Search Input (Auto-Focused)
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { query ->
                    viewModel.updateSearchQuery(query)
                    // Also check if barcode buffer matches user
                    viewModel.onUserBarcodeScanned(query, onUserLoggedIn)
                },
                placeholder = {
                    Text(
                        text = strings.searchUserPlaceholder,
                        color = TextSecondaryMuted
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyUp && keyEvent.key == Key.Enter) {
                            viewModel.onUserBarcodeScanned(searchQuery, onUserLoggedIn)
                            true
                        } else false
                    },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceWhite,
                    unfocusedContainerColor = SurfaceWhite,
                    focusedBorderColor = AccentNavy,
                    unfocusedBorderColor = DividerBorder
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    viewModel.onUserBarcodeScanned(searchQuery, onUserLoggedIn)
                })
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = strings.selectUserTitle,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryNavy
            )

            Spacer(modifier = Modifier.height(16.dp))

            // User Cards Grid (Privacy First: NO Balances rendered!)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 180.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredUsers, key = { it.id }) { user ->
                    UserCard(
                        user = user,
                        onClick = { viewModel.onUserCardClicked(user, onUserLoggedIn) }
                    )
                }
            }
        }

        // User PIN Dialog Modal
        if (selectedUserForPin != null) {
            val pinFocusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { pinFocusRequester.requestFocus() }

            Dialog(onDismissRequest = { viewModel.cancelPinDialog() }) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceWhite,
                    modifier = Modifier.width(360.dp).wrapContentHeight()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = strings.enterPinTitle,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryNavy
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "${strings.enterPinPrompt} ${selectedUserForPin?.name}:",
                            fontSize = 14.sp,
                            color = TextSecondarySubtle,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = { viewModel.updatePinInput(it) },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            isError = pinError != null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(pinFocusRequester)
                                .onKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyUp && keyEvent.key == Key.Enter) {
                                        viewModel.submitPin(onUserLoggedIn)
                                        true
                                    } else false
                                },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                viewModel.submitPin(onUserLoggedIn)
                            })
                        )

                        if (pinError != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = strings.pinIncorrect,
                                fontSize = 12.sp,
                                color = ColorDangerCrimson
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.cancelPinDialog() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(strings.cancel)
                            }

                            Button(
                                onClick = { viewModel.submitPin(onUserLoggedIn) },
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

        // Admin Password Modal
        if (showAdminAuthDialog) {
            val adminFocusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { adminFocusRequester.requestFocus() }

            Dialog(onDismissRequest = { viewModel.closeAdminAuthDialog() }) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceWhite,
                    modifier = Modifier.width(360.dp).wrapContentHeight()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = strings.adminLoginBtn,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryNavy
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = strings.adminPasswordPrompt,
                            fontSize = 14.sp,
                            color = TextSecondarySubtle
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = adminPasswordInput,
                            onValueChange = { viewModel.updateAdminPassword(it) },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            isError = adminPasswordError != null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(adminFocusRequester)
                                .onKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyUp && keyEvent.key == Key.Enter) {
                                        viewModel.submitAdminPassword(settings.adminPasswordHash, onNavigateToAdmin)
                                        true
                                    } else false
                                },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                viewModel.submitAdminPassword(settings.adminPasswordHash, onNavigateToAdmin)
                            })
                        )

                        if (adminPasswordError != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = strings.adminPasswordIncorrect,
                                fontSize = 12.sp,
                                color = ColorDangerCrimson
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.closeAdminAuthDialog() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(strings.cancel)
                            }

                            Button(
                                onClick = { viewModel.submitAdminPassword(settings.adminPasswordHash, onNavigateToAdmin) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryNavy)
                            ) {
                                Text(strings.confirm, color = SurfaceWhite)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserCard(
    user: User,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(AccentNavy),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = user.initials,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = SurfaceWhite
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = user.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = PrimaryNavy,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}
