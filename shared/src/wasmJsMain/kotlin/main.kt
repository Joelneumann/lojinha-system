import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.CanvasBasedWindow
import de.joelneumann.lojinha.data.repository.*
import de.joelneumann.lojinha.ui.AdminWebLoginScreen
import de.joelneumann.lojinha.ui.screens.admin.AdminScreen
import de.joelneumann.lojinha.ui.theme.LojinhaTheme
import de.joelneumann.lojinha.ui.theme.WEB_UI_SCALE_FACTOR
import de.joelneumann.lojinha.ui.viewmodel.admin.*

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    CanvasBasedWindow("Lojinha Admin Console") {
        var isAuthenticated by remember { mutableStateOf(false) }

        val networkClient = remember { AdminNetworkClient() }
        val productRepository = remember { HttpProductRepository(networkClient) }
        val userRepository = remember { HttpUserRepository(networkClient) }
        val transactionRepository = remember { HttpTransactionRepository(networkClient) }
        val settingsRepository = remember { HttpSettingsRepository(networkClient) }

        val productsViewModel = remember(isAuthenticated) {
            AdminProductsViewModel(productRepository, settingsRepository)
        }
        val usersViewModel = remember(isAuthenticated) {
            AdminUsersViewModel(userRepository, transactionRepository)
        }
        val transactionsViewModel = remember(isAuthenticated) {
            AdminTransactionsViewModel(transactionRepository, userRepository, productRepository)
        }
        val settingsViewModel = remember(isAuthenticated) {
            AdminSettingsViewModel(settingsRepository)
        }

        LaunchedEffect(isAuthenticated) {
            if (isAuthenticated) {
                productsViewModel.loadData()
                usersViewModel.loadData()
                transactionsViewModel.loadData()
                settingsViewModel.loadSettings()
            }
        }

        LojinhaTheme(scaleFactor = WEB_UI_SCALE_FACTOR) {
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
                    AdminScreen(
                        productsViewModel = productsViewModel,
                        usersViewModel = usersViewModel,
                        transactionsViewModel = transactionsViewModel,
                        settingsViewModel = settingsViewModel,
                        onExitAdmin = {
                            isAuthenticated = false
                        }
                    )
                }
            }
        }
    }
}
