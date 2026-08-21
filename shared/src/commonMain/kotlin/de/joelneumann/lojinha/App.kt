package de.joelneumann.lojinha.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import de.joelneumann.lojinha.data.database.DatabaseFactory
import de.joelneumann.lojinha.data.repository.RoomProductRepositoryImpl
import de.joelneumann.lojinha.data.repository.RoomSettingsRepositoryImpl
import de.joelneumann.lojinha.data.repository.RoomTransactionRepositoryImpl
import de.joelneumann.lojinha.data.repository.RoomUserRepositoryImpl
import de.joelneumann.lojinha.ui.components.general.InactivityWarningDialog
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.screens.AdminScreen
import de.joelneumann.lojinha.ui.screens.ShoppingScreen
import de.joelneumann.lojinha.ui.screens.TransactionHistoryScreen
import de.joelneumann.lojinha.ui.screens.UserSelectionScreen
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.viewmodel.*

@Composable
fun App() {
    val database = remember { DatabaseFactory.createDatabase() }
    val productRepository = remember { RoomProductRepositoryImpl(database.productDao()) }
    val userRepository = remember { RoomUserRepositoryImpl(database.userDao(), database.transactionDao()) }
    val settingsRepository = remember { RoomSettingsRepositoryImpl(database.settingsDao()) }
    val transactionRepository = remember { RoomTransactionRepositoryImpl(database.transactionDao()) }

    val appViewModel = remember { AppViewModel(userRepository, settingsRepository) }
    val userSelectionViewModel = remember { UserSelectionViewModel(userRepository) }
    val shoppingViewModel = remember { ShoppingViewModel(productRepository, userRepository, transactionRepository) }
    val historyViewModel = remember { TransactionHistoryViewModel(transactionRepository, userRepository) }

    val currentScreen by appViewModel.currentScreen.collectAsState()
    val currentLanguage by appViewModel.currentLanguage.collectAsState()
    val settings by appViewModel.settings.collectAsState()
    val currentUser by appViewModel.currentUser.collectAsState()
    val showInactivityWarning by appViewModel.showInactivityWarning.collectAsState()
    val inactivitySecondsRemaining by appViewModel.inactivitySecondsRemaining.collectAsState()

    val cartItems by shoppingViewModel.cartItems.collectAsState()
    var showAbandonCartGuardDialog by remember { mutableStateOf(false) }

    val handleLogoutRequest = {
        val hasCartItems = (currentScreen == AppScreen.SHOPPING || currentScreen == AppScreen.TRANSACTION_HISTORY) && cartItems.isNotEmpty()
        if (hasCartItems) {
            showAbandonCartGuardDialog = true
        } else {
            appViewModel.logout()
        }
    }

    val interactionSource = remember { MutableInteractionSource() }

    LojinhaTheme {
        val strings = I18n.get(currentLanguage)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) {
                    appViewModel.onUserInteracted()
                }
        ) {
            when (currentScreen) {
                AppScreen.MAIN_USER_SELECT -> {
                    UserSelectionScreen(
                        viewModel = userSelectionViewModel,
                        language = currentLanguage,
                        settings = settings,
                        onLanguageSelected = { appViewModel.setLanguage(it) },
                        onUserLoggedIn = { user ->
                            appViewModel.loginUser(user)
                        },
                        onNavigateToAdmin = {
                            appViewModel.navigateTo(AppScreen.ADMIN_PANEL)
                        }
                    )
                }

                AppScreen.SHOPPING -> {
                    if (currentUser != null) {
                        ShoppingScreen(
                            viewModel = shoppingViewModel,
                            user = currentUser!!,
                            language = currentLanguage,
                            settings = settings,
                            onLanguageSelected = { appViewModel.setLanguage(it) },
                            onLogout = handleLogoutRequest,
                            onNavigateToHistory = {
                                appViewModel.refreshCurrentUser()
                                appViewModel.navigateTo(AppScreen.TRANSACTION_HISTORY)
                            }
                        )
                    }
                }

                AppScreen.TRANSACTION_HISTORY -> {
                    if (currentUser != null) {
                        TransactionHistoryScreen(
                            viewModel = historyViewModel,
                            user = currentUser!!,
                            language = currentLanguage,
                            settings = settings,
                            onLanguageSelected = { appViewModel.setLanguage(it) },
                            onContinueShopping = {
                                appViewModel.navigateTo(AppScreen.SHOPPING)
                            },
                            onLogout = handleLogoutRequest,
                            onUserUpdated = { updated ->
                                appViewModel.updateCurrentUser(updated)
                            }
                        )
                    }
                }

                AppScreen.ADMIN_PANEL -> {
                    val adminViewModel = remember {
                        AdminViewModel(productRepository, userRepository, transactionRepository, settingsRepository)
                    }
                    AdminScreen(
                        viewModel = adminViewModel,
                        language = currentLanguage,
                        settings = settings,
                        onLanguageSelected = { appViewModel.setLanguage(it) },
                        onExitAdmin = {
                            userSelectionViewModel.loadUsers()
                            appViewModel.navigateTo(AppScreen.MAIN_USER_SELECT)
                        }
                    )
                }
            }

            // Inactivity Warning Modal Dialog
            if (showInactivityWarning) {
                InactivityWarningDialog(
                    secondsRemaining = inactivitySecondsRemaining,
                    language = currentLanguage,
                    onStayLoggedIn = { appViewModel.onUserInteracted() },
                    onLogoutNow = { appViewModel.logout() }
                )
            }

            // Abandon Cart Guard Dialog
            if (showAbandonCartGuardDialog) {
                AlertDialog(
                    onDismissRequest = { showAbandonCartGuardDialog = false },
                    title = { Text(strings.abandonCartTitle, fontWeight = FontWeight.Bold, color = PrimaryNavy) },
                    text = { Text(strings.abandonCartMsg, color = TextSecondarySubtle) },
                    confirmButton = {
                        Button(
                            onClick = {
                                showAbandonCartGuardDialog = false
                                shoppingViewModel.clearCart()
                                appViewModel.logout()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ColorDangerCrimson)
                        ) {
                            Text(strings.confirm, color = SurfaceWhite, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { showAbandonCartGuardDialog = false }) {
                            Text(strings.cancel)
                        }
                    }
                )
            }
        }
    }
}