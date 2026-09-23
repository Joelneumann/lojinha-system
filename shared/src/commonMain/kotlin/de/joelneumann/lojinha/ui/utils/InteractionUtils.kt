package de.joelneumann.lojinha.ui.utils

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Attaches pointer event monitoring to detect real user activity (mouse movement, clicks, scrolling).
 * Filters out minor jitter (<15f movement) and window enter/exit events.
 * Immediately invokes [onUserInteracted] with force = true on press or scroll,
 * and with force = false (debounced to 500ms) on real pointer movement.
 */
fun Modifier.trackUserInteractions(onUserInteracted: (force: Boolean) -> Unit): Modifier = this.pointerInput(onUserInteracted) {
    var lastInteractionTime = 0L
    var lastPosition: Offset? = null
    var accumulatedDistance = 0f
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            // Ignore exit and enter events (e.g. dialog popups appearing/disappearing or window focus shifts)
            if (event.type == PointerEventType.Exit || event.type == PointerEventType.Enter) {
                lastPosition = null
                accumulatedDistance = 0f
                continue
            }

            val currentPosition = event.changes.firstOrNull()?.position
            val prevPosition = lastPosition
            val isClickOrScroll = event.type == PointerEventType.Press || event.type == PointerEventType.Scroll

            var isRealMovement = false
            if (currentPosition != null && prevPosition != null && event.type == PointerEventType.Move) {
                val delta = (currentPosition - prevPosition).getDistance()
                accumulatedDistance += delta
                if (accumulatedDistance >= 15f) {
                    isRealMovement = true
                    accumulatedDistance = 0f
                }
            }

            if (currentPosition != null) {
                lastPosition = currentPosition
            }

            if (isClickOrScroll) {
                lastInteractionTime = currentTimeMillis()
                onUserInteracted(true)
            } else if (isRealMovement) {
                val now = currentTimeMillis()
                if (now - lastInteractionTime >= 500L) {
                    lastInteractionTime = now
                    onUserInteracted(false)
                }
            }
        }
    }
}
