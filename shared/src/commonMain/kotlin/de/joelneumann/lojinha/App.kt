package de.joelneumann.lojinha.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import de.joelneumann.lojinha.data.database.DatabaseFactory
import de.joelneumann.lojinha.data.repository.RoomProductRepositoryImpl
import de.joelneumann.lojinha.data.repository.RoomSettingsRepositoryImpl
import de.joelneumann.lojinha.data.repository.RoomTransactionRepositoryImpl
import de.joelneumann.lojinha.data.repository.RoomUserRepositoryImpl
import de.joelneumann.lojinha.ui.components.HeaderBar
import de.joelneumann.lojinha.ui.components.InactivityWarningDialog
import de.joelneumann.lojinha.ui.screens.AdminScreen
import de.joelneumann.lojinha.ui.screens.ShoppingScreen
import de.joelneumann.lojinha.ui.screens.TransactionHistoryScreen
import de.joelneumann.lojinha.ui.screens.UserSelectionScreen
import de.joelneumann.lojinha.ui.theme.LojinhaTheme
import de.joelneumann.lojinha.ui.viewmodel.*

@Composable
fun App() {
    val database = remember { DatabaseFactory.createDatabase() }

    val userRepository = remember { RoomUserRepositoryImpl(database.userDao(), database.transactionDao()) }
    val productRepository = remember { RoomProductRepositoryImpl(database.productDao()) }
    val transactionRepository = remember { RoomTransactionRepositoryImpl(database.transactionDao()) }
    val settingsRepository = remember { RoomSettingsRepositoryImpl(database.settingsDao()) }

    val appViewModel = remember { AppViewModel(userRepository, settingsRepository) }
    val userSelectionViewModel = remember { UserSelectionViewModel(userRepository) }

    val currentScreen by appViewModel.currentScreen.collectAsState()
    val currentUser by appViewModel.currentUser.collectAsState()
    val currentLanguage by appViewModel.currentLanguage.collectAsState()
    val settings by appViewModel.settings.collectAsState()
    val showInactivityWarning by appViewModel.showInactivityWarning.collectAsState()
    val inactivitySeconds by appViewModel.inactivitySecondsRemaining.collectAsState()

    val shoppingViewModel = remember(currentUser?.id) { ShoppingViewModel(productRepository, userRepository, transactionRepository) }
    val historyViewModel = remember(currentUser?.id) { TransactionHistoryViewModel(transactionRepository, userRepository) }
    val adminViewModel = remember { AdminViewModel(productRepository, userRepository, transactionRepository, settingsRepository) }

    val interactionSource = remember { MutableInteractionSource() }

    LojinhaTheme {
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
            Scaffold(
                topBar = {
                    HeaderBar(
                        currentScreen = currentScreen,
                        currentLanguage = currentLanguage,
                        onLanguageSelected = { appViewModel.setLanguage(it) },
                        onLogoutClicked = { appViewModel.logout() },
                        onAdminLoginClicked = { userSelectionViewModel.openAdminAuthDialog() }
                    )
                }
            ) { innerPadding ->
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    when (currentScreen) {
                        AppScreen.MAIN_USER_SELECT -> {
                            UserSelectionScreen(
                                viewModel = userSelectionViewModel,
                                language = currentLanguage,
                                settings = settings,
                                onUserLoggedIn = { user ->
                                    appViewModel.loginUser(user)
                                },
                                onNavigateToAdmin = {
                                    adminViewModel.loadData()
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
                                    onContinueShopping = {
                                        appViewModel.navigateTo(AppScreen.SHOPPING)
                                    },
                                    onLogout = {
                                        appViewModel.logout()
                                    },
                                    onUserUpdated = { updated ->
                                        appViewModel.updateCurrentUser(updated)
                                    }
                                )
                            }
                        }

                        AppScreen.ADMIN_PANEL -> {
                            AdminScreen(
                                viewModel = adminViewModel,
                                language = currentLanguage,
                                onCloseAdmin = {
                                    userSelectionViewModel.loadUsers()
                                    appViewModel.navigateTo(AppScreen.MAIN_USER_SELECT)
                                }
                            )
                        }
                    }
                }
            }

            // Inactivity 1-Minute Warning Modal
            if (showInactivityWarning && currentUser != null) {
                InactivityWarningDialog(
                    secondsRemaining = inactivitySeconds,
                    language = currentLanguage,
                    onStayLoggedIn = { appViewModel.resetInactivityTimer() },
                    onLogoutNow = { appViewModel.logout() }
                )
            }
        }
    }
}