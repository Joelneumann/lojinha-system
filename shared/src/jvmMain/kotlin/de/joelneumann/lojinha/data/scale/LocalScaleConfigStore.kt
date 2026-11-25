package de.joelneumann.lojinha.data.scale

import de.joelneumann.lojinha.domain.scale.ScaleConfig
import de.joelneumann.lojinha.util.AppLogger
import java.util.prefs.Preferences

/**
 * Stores per-machine serial scale settings outside the Room database so
 * database backups remain portable between macOS and Windows.
 */
object LocalScaleConfigStore {
    private const val NODE_NAME = "de/joelneumann/lojinha/scale"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_PORT_NAME = "portName"
    private const val KEY_BAUD_RATE = "baudRate"
    private const val KEY_VENDOR_ID = "vendorId"
    private const val KEY_PRODUCT_ID = "productId"
    private const val KEY_SERIAL_NUMBER = "serialNumber"

    private val prefs: Preferences by lazy {
        Preferences.userRoot().node(NODE_NAME)
    }

    fun load(): ScaleConfig {
        return try {
            ScaleConfig(
                enabled = prefs.getBoolean(KEY_ENABLED, false),
                portName = prefs.get(KEY_PORT_NAME, ""),
                baudRate = prefs.getInt(KEY_BAUD_RATE, 9600),
                vendorId = prefs.getInt(KEY_VENDOR_ID, -1),
                productId = prefs.getInt(KEY_PRODUCT_ID, -1),
                serialNumber = prefs.get(KEY_SERIAL_NUMBER, "").takeIf { it.isNotBlank() }
            )
        } catch (e: Exception) {
            AppLogger.warn("Scale", "Failed to load local scale config: ${e.message}")
            ScaleConfig()
        }
    }

    fun save(config: ScaleConfig) {
        try {
            prefs.putBoolean(KEY_ENABLED, config.enabled)
            prefs.put(KEY_PORT_NAME, config.portName)
            prefs.putInt(KEY_BAUD_RATE, config.baudRate)
            prefs.putInt(KEY_VENDOR_ID, config.vendorId)
            prefs.putInt(KEY_PRODUCT_ID, config.productId)
            prefs.put(KEY_SERIAL_NUMBER, config.serialNumber.orEmpty())
            prefs.flush()
        } catch (e: Exception) {
            AppLogger.warn("Scale", "Failed to save local scale config: ${e.message}")
        }
    }
}
