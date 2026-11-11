package de.joelneumann.lojinha.ui.utils

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.delay

/**
 * Requests focus safely after the layout pass has attached the node to the window hierarchy.
 */
suspend fun FocusRequester.safeRequestFocus(maxAttempts: Int = 10, delayMillis: Long = 25L) {
    for (attempt in 0 until maxAttempts) {
        try {
            withFrameNanos { }
            requestFocus()
            break
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            delay(delayMillis)
        }
    }
}
