package de.joelneumann.lojinha.domain.scale

import de.joelneumann.lojinha.domain.model.VeggieQuantityScaling
import kotlin.math.roundToLong

/**
 * Stateless parser for Toledo Prix 3 Plus serial frames (Prt3 / Prt1 polling).
 *
 * Frame format: [STX (0x02)] <payload> [ETX (0x03)]
 * Standard payload is 5 ASCII characters:
 * - "01250" -> 1250 grams (1.250 kg)
 * - "00000" -> 0 grams (ZERO)
 * - "NNNNN" -> NEGATIVE
 * - "SSSSS" -> OVERLOAD
 * - "IIIII" -> UNSTABLE
 */
object Prix3ProtocolParser {
    const val ENQ: Byte = 0x05
    const val STX: Byte = 0x02
    const val ETX: Byte = 0x03

    data class ParsedFrame(
        val grams: Long,
        val status: ScaleReadingStatus
    )

    /**
     * Extracts the latest complete [STX]...[ETX] frame from [buffer] and removes consumed bytes.
     * Returns null if no complete frame is available yet.
     */
    fun extractLatestFrame(buffer: MutableList<Byte>): ParsedFrame? {
        var latest: ParsedFrame? = null
        while (true) {
            val stxIndex = buffer.indexOf(STX)
            if (stxIndex < 0) {
                buffer.clear()
                break
            }
            if (stxIndex > 0) {
                repeat(stxIndex) { buffer.removeAt(0) }
            }
            val etxIndex = buffer.indexOf(ETX)
            if (etxIndex < 0) {
                // Keep partial frame from STX onward, capped against runaway noise
                if (buffer.size > 64) {
                    buffer.clear()
                }
                break
            }
            val nestedStx = buffer.subList(1, etxIndex).lastIndexOf(STX)
            if (nestedStx >= 0) {
                repeat(nestedStx + 1) { buffer.removeAt(0) }
                continue
            }
            val payloadBytes = ByteArray(etxIndex - 1) { i -> buffer[i + 1] }
            repeat(etxIndex + 1) { buffer.removeAt(0) }
            val payload = payloadBytes.decodeToString()
            parsePayload(payload)?.let { latest = it }
        }
        return latest
    }

    fun parsePayload(rawPayload: String): ParsedFrame? {
        val trimmed = rawPayload.trim()
        if (trimmed.isEmpty()) return null

        return when {
            trimmed.all { it == 'N' || it == 'n' } -> ParsedFrame(0L, ScaleReadingStatus.NEGATIVE)
            trimmed.all { it == 'S' || it == 's' } -> ParsedFrame(0L, ScaleReadingStatus.OVERLOAD)
            trimmed.all { it == 'I' || it == 'i' } -> ParsedFrame(0L, ScaleReadingStatus.UNSTABLE)
            trimmed.contains('.') || trimmed.contains(',') -> {
                if (trimmed.any { it == 'e' || it == 'E' }) return null
                val kg = trimmed.replace(',', '.').toDoubleOrNull() ?: return null
                if (!kg.isFinite()) return null
                if (kg < 0.0) return ParsedFrame(0L, ScaleReadingStatus.NEGATIVE)
                val grams = (kg * 1000.0).roundToLong()
                val status = if (grams == 0L) ScaleReadingStatus.ZERO else ScaleReadingStatus.OK
                ParsedFrame(grams, status)
            }
            else -> {
                val grams = trimmed.toLongOrNull() ?: return null
                if (grams < 0L) return ParsedFrame(0L, ScaleReadingStatus.NEGATIVE)
                val status = if (grams == 0L) ScaleReadingStatus.ZERO else ScaleReadingStatus.OK
                ParsedFrame(grams, status)
            }
        }
    }

    /**
     * Formats [grams] for an Admin Veggie Matrix cell based on its unit type.
     * - Gram units ("g", "gr", "gram", "grams", "gramas") receive whole grams (e.g. "1250").
     * - Other units (e.g. "kg") receive kg formatted with [VeggieQuantityScaling.formatScaledQuantity].
     */
    fun formatGramsForVeggieUnit(
        grams: Long,
        unitName: String,
        allowDecimals: Boolean,
        decimalSeparator: Char
    ): String {
        val normalizedUnit = unitName.trim().lowercase()
        val isGramUnit = normalizedUnit in setOf("g", "gr", "gram", "grams", "gramas", "grama")
        if (isGramUnit) {
            return grams.coerceAtLeast(0L).toString()
        }
        return VeggieQuantityScaling.formatScaledQuantity(
            scaled = grams.coerceAtLeast(0L),
            allowDecimals = allowDecimals,
            decimalSeparator = decimalSeparator
        )
    }

    /**
     * Picks the best matching port from [ports] for [config], supporting USB hot-plugging
     * even when the OS assigns a new port path (e.g. macOS /dev/cu.usbserial-*).
     */
    fun findMatchingPort(config: ScaleConfig, ports: List<ScalePortInfo>): ScalePortInfo? {
        if (ports.isEmpty()) return null
        if (config.portName.isNotBlank()) {
            ports.firstOrNull { it.systemPortName.equals(config.portName, ignoreCase = true) }?.let {
                return it
            }
        }
        val savedSerial = config.serialNumber?.trim()?.takeIf { it.isNotEmpty() }
        if (savedSerial != null) {
            ports.firstOrNull { it.serialNumber?.trim().equals(savedSerial, ignoreCase = true) }?.let {
                return it
            }
        }
        if (config.vendorId > 0 && config.productId > 0) {
            val vidPidMatches = ports.filter {
                it.vendorId == config.vendorId && it.productId == config.productId
            }
            if (vidPidMatches.size == 1) {
                return vidPidMatches.first()
            }
        }
        return null
    }
}
