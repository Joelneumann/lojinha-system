package de.joelneumann.lojinha.data.service

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Thread-safe event broadcaster that coordinates data change notifications
 * across the desktop Kiosk UI and the embedded Ktor Web Admin server.
 */
object DataChangeNotifier {
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun notifyDataChanged() {
        for (listener in listeners) {
            listener()
        }
    }

    fun clearListeners() {
        listeners.clear()
    }
}
