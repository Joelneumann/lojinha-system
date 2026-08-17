package de.joelneumann.lojinha

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Lojinha",
    ) {
        App()
    }
}