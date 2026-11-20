package de.joelneumann.lojinha.ui.utils

/**
 * Returns true on iOS / iPadOS Safari (including standalone Safari PWA), where opening the
 * virtual keyboard from a Canvas-based Compose Web text field requires synchronous priming
 * via a hidden proxy input inside the tap gesture.
 *
 * Returns false on Android Chrome, iOS Chrome (`CriOS`), desktop browsers, and JVM desktop,
 * where Compose's native `BasicTextField` focus flow works directly without a proxy input.
 */
expect fun usesIosSafariKeyboardProxy(): Boolean

/**
 * Synchronously primes iOS Safari's software keyboard during a touch/tap gesture so it opens
 * reliably on the first tap and after OS keyboard dismissal.
 *
 * No-op on non-iOS-Safari browsers and JVM desktop.
 */
expect fun notifyTextFieldTapGesture(allowDecimals: Boolean = false)

/**
 * Installs a one-time `visualViewport` listener on non-Safari mobile touch browsers (Android
 * Chrome and iOS Chrome) that blurs Compose's hidden backing DOM `<textarea>` when the user
 * dismisses the software keyboard. That prevents stale DOM focus from making the next tap on
 * the same focused field a no-op.
 *
 * No-op on iOS Safari, desktop browsers, and JVM desktop.
 */
expect fun ensureMobileKeyboardDismissHandlerInstalled()
