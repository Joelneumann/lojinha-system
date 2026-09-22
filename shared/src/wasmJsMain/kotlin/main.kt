import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.CanvasBasedWindow
import de.joelneumann.lojinha.data.repository.*
import de.joelneumann.lojinha.ui.AdminWebLoginScreen
import de.joelneumann.lojinha.ui.screens.admin.AdminScreen
import de.joelneumann.lojinha.ui.theme.LojinhaTheme
import de.joelneumann.lojinha.ui.theme.WEB_UI_SCALE_FACTOR
import de.joelneumann.lojinha.ui.viewmodel.admin.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    CanvasBasedWindow("Lojinha Admin Console") {
        var isAuthenticated by remember { mutableStateOf(false) }
        var isCheckingSession by remember { mutableStateOf(true) }
        val scope = rememberCoroutineScope()

        val networkClient = remember { AdminNetworkClient() }
        val productRepository = remember { HttpProductRepository(networkClient) }
        val userRepository = remember { HttpUserRepository(networkClient) }
        val transactionRepository = remember { HttpTransactionRepository(networkClient) }
        val billingListRepository = remember { HttpBillingListRepository(networkClient) }

        // Session restoration on startup
        LaunchedEffect(Unit) {
            val restored = networkClient.tryRestoreSession()
            if (restored) {
                isAuthenticated = true
            }
            isCheckingSession = false
        }

        LojinhaTheme(scaleFactor = WEB_UI_SCALE_FACTOR) {
            if (isCheckingSession) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Crossfade(
                    targetState = isAuthenticated,
                    animationSpec = tween(durationMillis = 250),
                    modifier = Modifier.fillMaxSize()
                ) { authed ->
                    if (!authed) {
                        AdminWebLoginScreen(
                            onLoginSubmit = { password ->
                                networkClient.login(password)
                            },
                            onLoginSuccess = {
                                isAuthenticated = true
                            }
                        )
                    } else {
                        val productsViewModel = remember { AdminProductsViewModel(productRepository) }
                        val usersViewModel = remember { AdminUsersViewModel(userRepository, transactionRepository) }
                        val transactionsViewModel = remember { AdminTransactionsViewModel(transactionRepository, userRepository, productRepository) }
                        val bulkBillingViewModel = remember { AdminBulkBillingViewModel(billingListRepository, userRepository, transactionRepository) }

                        LaunchedEffect(Unit) {
                            networkClient.startRealtimeSync(this)
                            launch {
                                networkClient.onDataChanged.collect {
                                    productsViewModel.loadData()
                                    usersViewModel.loadData()
                                    transactionsViewModel.loadData()
                                    bulkBillingViewModel.loadData()
                                }
                            }
                            launch {
                                networkClient.onUnauthorized.collect {
                                    isAuthenticated = false
                                }
                            }
                        }

                        DisposableEffect(Unit) {
                            onDispose {
                                networkClient.stopRealtimeSync()
                            }
                        }

                        AdminScreen(
                            productsViewModel = productsViewModel,
                            usersViewModel = usersViewModel,
                            transactionsViewModel = transactionsViewModel,
                            bulkBillingViewModel = bulkBillingViewModel,
                            settingsViewModel = null,
                            onExitAdmin = {
                                scope.launch {
                                    networkClient.logout()
                                    isAuthenticated = false
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
