package de.joelneumann.lojinha

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.application
import de.joelneumann.lojinha.data.database.DatabaseFactory
import de.joelneumann.lojinha.data.repository.RoomProductRepositoryImpl
import de.joelneumann.lojinha.data.repository.RoomSettingsRepositoryImpl
import de.joelneumann.lojinha.data.repository.RoomTransactionRepositoryImpl
import de.joelneumann.lojinha.data.repository.RoomUserRepositoryImpl
import de.joelneumann.lojinha.server.LojinhaAdminServer
import de.joelneumann.lojinha.ui.App

fun main() = application {
    val database = DatabaseFactory.createDatabase()
    val productRepository = RoomProductRepositoryImpl(database.productDao())
    val userRepository = RoomUserRepositoryImpl(database.userDao(), database.transactionDao())
    val transactionRepository = RoomTransactionRepositoryImpl(database.transactionDao())
    val settingsRepository = RoomSettingsRepositoryImpl(database.settingsDao())

    val adminServer = LojinhaAdminServer(
        productRepository = productRepository,
        userRepository = userRepository,
        transactionRepository = transactionRepository,
        settingsRepository = settingsRepository,
        port = 8080
    )

    DisposableEffect(Unit) {
        adminServer.start()
        onDispose {
            adminServer.stop()
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
        App()
    }
}