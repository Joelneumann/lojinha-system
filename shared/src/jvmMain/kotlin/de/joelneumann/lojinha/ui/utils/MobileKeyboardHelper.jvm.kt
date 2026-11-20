package de.joelneumann.lojinha.ui.utils

actual fun usesIosSafariKeyboardProxy(): Boolean = false

actual fun notifyTextFieldTapGesture(allowDecimals: Boolean) {
    // No-op on JVM desktop
}

actual fun ensureMobileKeyboardDismissHandlerInstalled() {
    // No-op on JVM desktop
}
