package de.joelneumann.lojinha.data.scale

import com.fazecast.jSerialComm.SerialPort
import de.joelneumann.lojinha.domain.scale.Prix3ProtocolParser
import de.joelneumann.lojinha.domain.scale.ScaleConfig
import de.joelneumann.lojinha.domain.scale.ScaleConnectionStatus
import de.joelneumann.lojinha.domain.scale.ScalePortInfo
import de.joelneumann.lojinha.domain.scale.ScaleReading
import de.joelneumann.lojinha.domain.scale.ScaleReadingStatus
import de.joelneumann.lojinha.domain.scale.ScaleService
import de.joelneumann.lojinha.domain.scale.ScaleUiState
import de.joelneumann.lojinha.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@OptIn(ExperimentalCoroutinesApi::class)
class JSerialCommScaleService : ScaleService {
    // Single-threaded IO dispatcher serializes port open/poll/close so UI threads never block and jobs never overlap
    private val serialDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + serialDispatcher)
    private val lifecycleMutex = Mutex()
    private val activeTokens = mutableSetOf<String>()
    private val tokenLock = Any()

    private val _state = MutableStateFlow(
        LocalScaleConfigStore.load().let { initialConfig ->
            ScaleUiState(
                config = initialConfig,
                connectionStatus = if (initialConfig.enabled) ScaleConnectionStatus.DISCONNECTED else ScaleConnectionStatus.DISABLED
            )
        }
    )
    override val state: StateFlow<ScaleUiState> = _state.asStateFlow()

    private var pollingJob: Job? = null

    init {
        refreshPorts()
    }

    override fun acquirePollingToken(owner: String) {
        val shouldStart = synchronized(tokenLock) {
            activeTokens.add(owner)
            activeTokens.isNotEmpty()
        }
        if (shouldStart) {
            scope.launch {
                lifecycleMutex.withLock {
                    if (_state.value.config.enabled && synchronized(tokenLock) { activeTokens.isNotEmpty() }) {
                        ensurePollingStartedLocked()
                    }
                }
            }
        }
    }

    override fun releasePollingToken(owner: String) {
        val shouldStop = synchronized(tokenLock) {
            activeTokens.remove(owner)
            activeTokens.isEmpty()
        }
        if (shouldStop) {
            scope.launch {
                lifecycleMutex.withLock {
                    if (synchronized(tokenLock) { activeTokens.isEmpty() }) {
                        stopPollingLocked()
                    }
                }
            }
        }
    }

    override fun refreshPorts() {
        scope.launch {
            val ports = scanPorts()
            _state.update { it.copy(availablePorts = ports) }
        }
    }

    override fun updateConfig(config: ScaleConfig) {
        LocalScaleConfigStore.save(config)
        scope.launch {
            lifecycleMutex.withLock {
                stopPollingLocked()
                val ports = scanPorts()
                _state.update {
                    it.copy(
                        config = config,
                        availablePorts = ports,
                        connectionStatus = if (config.enabled) ScaleConnectionStatus.DISCONNECTED else ScaleConnectionStatus.DISABLED,
                        reading = null,
                        activePortName = null,
                        lastError = null
                    )
                }
                val hasTokens = synchronized(tokenLock) { activeTokens.isNotEmpty() }
                if (config.enabled && hasTokens) {
                    ensurePollingStartedLocked()
                }
            }
        }
    }

    override fun close() {
        synchronized(tokenLock) { activeTokens.clear() }
        scope.cancel()
    }

    private fun ensurePollingStartedLocked() {
        if (pollingJob?.isActive == true) return
        pollingJob = scope.launch {
            var openPort: SerialPort? = null
            val buffer = mutableListOf<Byte>()
            val readScratch = ByteArray(128)
            val enqPayload = byteArrayOf(Prix3ProtocolParser.ENQ)
            var lastGrams: Long? = null
            var consecutiveSameCount = 0
            var consecutiveNoFramePolls = 0

            fun resetOpenPort(
                status: ScaleConnectionStatus = ScaleConnectionStatus.DISCONNECTED,
                error: String? = null,
                keepActivePortName: Boolean = false
            ) {
                if (!keepActivePortName) {
                    closePort(openPort)
                    openPort = null
                }
                buffer.clear()
                lastGrams = null
                consecutiveSameCount = 0
                consecutiveNoFramePolls = 0
                _state.update {
                    it.copy(
                        connectionStatus = status,
                        reading = null,
                        activePortName = if (keepActivePortName) it.activePortName else null,
                        lastError = error
                    )
                }
            }

            try {
                while (isActive) {
                    val config = _state.value.config
                    if (!config.enabled) {
                        resetOpenPort(status = ScaleConnectionStatus.DISABLED)
                        break
                    }

                    val currentPort = openPort
                    if (currentPort == null || !currentPort.isOpen) {
                        closePort(openPort)
                        openPort = null
                        buffer.clear()
                        lastGrams = null
                        consecutiveSameCount = 0
                        consecutiveNoFramePolls = 0

                        val ports = scanPorts()
                        _state.update { it.copy(availablePorts = ports) }

                        val target = Prix3ProtocolParser.findMatchingPort(config, ports)
                        if (target == null) {
                            resetOpenPort()
                            delay(RECONNECT_INTERVAL_MS)
                            continue
                        }

                        _state.update {
                            it.copy(
                                connectionStatus = ScaleConnectionStatus.CONNECTING,
                                activePortName = target.systemPortName,
                                lastError = null
                            )
                        }

                        val opened = tryOpenPort(target.systemPortName, config.baudRate)
                        if (opened == null) {
                            resetOpenPort(
                                status = ScaleConnectionStatus.ERROR,
                                error = "Failed to open ${target.systemPortName}"
                            )
                            delay(RECONNECT_INTERVAL_MS)
                            continue
                        }

                        openPort = opened
                        // Stay CONNECTING until the first valid frame arrives from the scale
                    }

                    val port = openPort ?: continue
                    try {
                        val written = port.writeBytes(enqPayload, enqPayload.size)
                        if (written < 1 || !port.isOpen) {
                            AppLogger.warn("Scale", "Serial write failed on ${port.systemPortName}; reconnecting")
                            resetOpenPort()
                            delay(RECONNECT_INTERVAL_MS)
                            continue
                        }

                        delay(FRAME_WAIT_MS)
                        val bytesRead = port.readBytes(readScratch, readScratch.size)
                        if (bytesRead < 0) {
                            AppLogger.warn("Scale", "Serial read error on ${port.systemPortName}; reconnecting")
                            resetOpenPort()
                            delay(RECONNECT_INTERVAL_MS)
                            continue
                        }

                        if (bytesRead > 0) {
                            for (i in 0 until bytesRead) {
                                buffer.add(readScratch[i])
                            }
                        }

                        val frame = if (bytesRead > 0) Prix3ProtocolParser.extractLatestFrame(buffer) else null
                        if (frame != null) {
                            consecutiveNoFramePolls = 0
                            val isStable = when (frame.status) {
                                ScaleReadingStatus.OK, ScaleReadingStatus.ZERO -> {
                                    if (lastGrams == frame.grams) {
                                        consecutiveSameCount++
                                    } else {
                                        lastGrams = frame.grams
                                        consecutiveSameCount = 1
                                    }
                                    consecutiveSameCount >= STABLE_CONSECUTIVE_READINGS
                                }
                                else -> {
                                    lastGrams = null
                                    consecutiveSameCount = 0
                                    false
                                }
                            }
                            _state.update {
                                it.copy(
                                    connectionStatus = ScaleConnectionStatus.CONNECTED,
                                    reading = ScaleReading(
                                        grams = frame.grams,
                                        isStable = isStable,
                                        status = frame.status
                                    ),
                                    lastError = null
                                )
                            }
                        } else {
                            consecutiveNoFramePolls++
                            if (consecutiveNoFramePolls >= MAX_NO_FRAME_POLLS_BEFORE_DISCONNECT) {
                                // USB adapter may be plugged in while the scale itself is off, unplugged, or at wrong baud rate
                                val stillPresent = SerialPort.getCommPorts().any {
                                    it.systemPortName.equals(port.systemPortName, ignoreCase = true)
                                }
                                resetOpenPort(keepActivePortName = stillPresent)
                            }
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        AppLogger.warn("Scale", "Serial polling exception: ${e.message}")
                        resetOpenPort(status = ScaleConnectionStatus.ERROR, error = e.message)
                        delay(RECONNECT_INTERVAL_MS)
                        continue
                    }

                    delay(POLL_INTERVAL_MS)
                }
            } finally {
                closePort(openPort)
            }
        }
    }

    private suspend fun stopPollingLocked() {
        pollingJob?.cancelAndJoin()
        pollingJob = null
        _state.update {
            it.copy(
                connectionStatus = if (it.config.enabled) ScaleConnectionStatus.DISCONNECTED else ScaleConnectionStatus.DISABLED,
                reading = null,
                activePortName = null
            )
        }
    }

    private fun tryOpenPort(portName: String, baudRate: Int): SerialPort? {
        return try {
            val port = SerialPort.getCommPort(portName)
            port.setComPortParameters(baudRate, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY)
            port.setComPortTimeouts(
                SerialPort.TIMEOUT_READ_SEMI_BLOCKING or SerialPort.TIMEOUT_WRITE_BLOCKING,
                READ_TIMEOUT_MS,
                WRITE_TIMEOUT_MS
            )
            if (port.openPort()) {
                port
            } else {
                null
            }
        } catch (e: Exception) {
            AppLogger.warn("Scale", "Failed to open $portName: ${e.message}")
            null
        }
    }

    private fun closePort(port: SerialPort?) {
        try {
            if (port != null && port.isOpen) {
                port.closePort()
            }
        } catch (_: Exception) {
        }
    }

    private fun scanPorts(): List<ScalePortInfo> {
        return try {
            val rawPorts = SerialPort.getCommPorts().toList()
            val cuNames = rawPorts
                .map { it.systemPortName }
                .filter { it.startsWith("cu.", ignoreCase = true) }
                .map { it.removePrefix("cu.").lowercase() }
                .toSet()

            rawPorts
                .filterNot { port ->
                    // On macOS, prefer /dev/cu.* over /dev/tty.* to avoid carrier-detect blocking
                    port.systemPortName.startsWith("tty.", ignoreCase = true) &&
                        cuNames.contains(port.systemPortName.removePrefix("tty.").lowercase())
                }
                .map { port ->
                    ScalePortInfo(
                        systemPortName = port.systemPortName,
                        descriptiveName = port.descriptivePortName.ifBlank { port.systemPortName },
                        vendorId = port.vendorID,
                        productId = port.productID,
                        serialNumber = runCatching { port.serialNumber }.getOrNull()?.takeIf { it.isNotBlank() }
                    )
                }
                .sortedBy { it.systemPortName.lowercase() }
        } catch (e: Exception) {
            AppLogger.warn("Scale", "Failed to enumerate serial ports: ${e.message}")
            emptyList()
        }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 180L
        private const val FRAME_WAIT_MS = 60L
        private const val READ_TIMEOUT_MS = 120
        private const val WRITE_TIMEOUT_MS = 120
        private const val RECONNECT_INTERVAL_MS = 1500L
        private const val STABLE_CONSECUTIVE_READINGS = 2
        private const val MAX_NO_FRAME_POLLS_BEFORE_DISCONNECT = 10
    }
}
