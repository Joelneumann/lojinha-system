package de.joelneumann.lojinha.data.service

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Thread-safe event broadcaster that coordinates data change notifications
 * across the desktop Kiosk UI and the embedded Ktor Web Admin server.
 */
object DataChangeNotifier {
    data class ChangeScope(
        val adminOnly: Boolean = false,
        val targetUserId: String? = null
    )

    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val scopedListeners = CopyOnWriteArrayList<(ChangeScope) -> Unit>()

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun addScopedListener(listener: (ChangeScope) -> Unit) {
        scopedListeners.add(listener)
    }

    fun removeScopedListener(listener: (ChangeScope) -> Unit) {
        scopedListeners.remove(listener)
    }

    fun notifyDataChanged() {
        notifyScopedDataChanged(ChangeScope())
    }

    fun notifyScopedDataChanged(scope: ChangeScope) {
        for (listener in listeners) {
            listener()
        }
        for (listener in scopedListeners) {
            listener(scope)
        }
    }

    fun clearListeners() {
        listeners.clear()
        scopedListeners.clear()
    }
}
