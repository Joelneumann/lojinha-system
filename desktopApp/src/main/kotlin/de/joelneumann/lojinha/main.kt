package de.joelneumann.lojinha

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.application
import de.joelneumann.lojinha.ui.App

fun main() = application {
    val windowState = rememberWindowState(width = 1280.dp, height = 850.dp)
    Window(
        onCloseRequest = ::exitApplication,
        title = "🛒 Lojinha POS & Self-Service Kiosk",
        state = windowState
    ) {
        App()
    }
}