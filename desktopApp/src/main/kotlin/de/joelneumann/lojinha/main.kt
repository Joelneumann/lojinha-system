package de.joelneumann.lojinha

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.application
import de.joelneumann.lojinha.data.database.DatabaseFactory
import de.joelneumann.lojinha.data.repository.RoomBillingListRepositoryImpl
import de.joelneumann.lojinha.data.repository.RoomProductRepositoryImpl
import de.joelneumann.lojinha.data.repository.RoomSettingsRepositoryImpl
import de.joelneumann.lojinha.data.repository.RoomTransactionRepositoryImpl
import de.joelneumann.lojinha.data.repository.RoomUserRepositoryImpl
import de.joelneumann.lojinha.data.service.DataChangeNotifier
import de.joelneumann.lojinha.server.LojinhaAdminServer
import de.joelneumann.lojinha.ui.App
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.util.AppLogger
import de.joelneumann.lojinha.util.CrashHandler
import de.joelneumann.lojinha.util.AppVersionTracker

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
fun main() {
    System.setProperty("io.netty.noUnsafe", "true")
    CrashHandler.install()
    AppVersionTracker.checkAndTrackVersion()
    AppLogger.info("Main", "Starting Lojinha System v${AppVersion.CURRENT} on ${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})")
    AppLogger.info("Main", "Java Runtime: ${System.getProperty("java.version")} by ${System.getProperty("java.vendor")}")

    application {
        val database = DatabaseFactory.createDatabase()
        val onDataChanged = { DataChangeNotifier.notifyDataChanged() }
        val productRepository = RoomProductRepositoryImpl(database.productDao(), database.transactionDao(), onDataChanged)
        val userRepository = RoomUserRepositoryImpl(database.userDao(), database.transactionDao(), onDataChanged)
        val transactionRepository = RoomTransactionRepositoryImpl(database.transactionDao(), onDataChanged)
        val billingListRepository = RoomBillingListRepositoryImpl(database.billingListDao(), onDataChanged)
        val settingsRepository = RoomSettingsRepositoryImpl(database.settingsDao(), onDataChanged)

        val adminServer = LojinhaAdminServer(
            productRepository = productRepository,
            userRepository = userRepository,
            transactionRepository = transactionRepository,
            billingListRepository = billingListRepository,
            settingsRepository = settingsRepository,
            database = database,
            port = 8080
        )

        DisposableEffect(Unit) {
            try {
                adminServer.start()
            } catch (e: Exception) {
                AppLogger.warn("Main", "Failed to start admin server: ${e.message}", e)
            }
            onDispose {
                try {
                    adminServer.stop()
                } catch (e: Exception) {
                    AppLogger.warn("Main", "Error stopping admin server: ${e.message}", e)
                }
                try {
                    database.close()
                    AppLogger.info("Main", "AppDatabase closed and WAL checkpointed successfully.")
                } catch (e: Exception) {
                    AppLogger.warn("Main", "Error closing database: ${e.message}", e)
                }
            }
        }

        val isMac = System.getProperty("os.name").lowercase().contains("mac")
        
        val windowState = rememberWindowState(
            placement = WindowPlacement.Fullscreen
        )
        @Suppress("DEPRECATION")
        val appIcon = painterResource("icon.png")
        var showExitAuthDialog by remember { mutableStateOf(false) }

        Window(
            onCloseRequest = {
                showExitAuthDialog = true
            },
            title = I18n.get().appWindowTitle,
            state = windowState,
            icon = appIcon,
            undecorated = !isMac,
            resizable = isMac,
        ) {
            App(
                database = database,
                serverPort = adminServer.getActualPort(),
                onExitApplication = ::exitApplication,
                productRepository = productRepository,
                userRepository = userRepository,
                transactionRepository = transactionRepository,
                billingListRepository = billingListRepository,
                settingsRepository = settingsRepository,
                showExitAuthDialog = showExitAuthDialog,
                onDismissExitAuthDialog = { showExitAuthDialog = false }
            )
        }
}
}