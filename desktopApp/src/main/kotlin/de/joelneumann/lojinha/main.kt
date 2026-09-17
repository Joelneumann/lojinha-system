package de.joelneumann.lojinha

import androidx.compose.runtime.DisposableEffect
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

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
fun main() = application {
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
        port = 8080
    )

    DisposableEffect(Unit) {
        try {
            adminServer.start()
        } catch (e: Exception) {
            println("[WARN] Failed to start admin server: ${e.message}")
        }
        onDispose {
            try {
                adminServer.stop()
            } catch (e: Exception) {}
        }
    }

    val windowState = rememberWindowState(placement = WindowPlacement.Fullscreen)
    @Suppress("DEPRECATION")
    val appIcon = painterResource("icon.png")
    Window(
        onCloseRequest = ::exitApplication,
        title = "Lojinha POS & Self-Service Kiosk",
        state = windowState,
        icon = appIcon
    ) {
        App(database = database)
    }
}