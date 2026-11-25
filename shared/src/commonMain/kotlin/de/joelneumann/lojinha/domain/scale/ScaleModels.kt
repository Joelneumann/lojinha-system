package de.joelneumann.lojinha.domain.scale

enum class ScaleConnectionStatus {
    DISABLED,
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

enum class ScaleReadingStatus {
    OK,
    UNSTABLE,
    NEGATIVE,
    OVERLOAD,
    ZERO
}

data class ScaleReading(
    val grams: Long,
    val isStable: Boolean,
    val status: ScaleReadingStatus
)

data class ScalePortInfo(
    val systemPortName: String,
    val descriptiveName: String,
    val vendorId: Int = -1,
    val productId: Int = -1,
    val serialNumber: String? = null
)

data class ScaleConfig(
    val enabled: Boolean = false,
    val portName: String = "",
    val baudRate: Int = 9600,
    val vendorId: Int = -1,
    val productId: Int = -1,
    val serialNumber: String? = null
)

data class ScaleUiState(
    val config: ScaleConfig = ScaleConfig(),
    val connectionStatus: ScaleConnectionStatus = ScaleConnectionStatus.DISABLED,
    val reading: ScaleReading? = null,
    val availablePorts: List<ScalePortInfo> = emptyList(),
    val activePortName: String? = null,
    val lastError: String? = null
)
