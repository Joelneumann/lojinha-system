package de.joelneumann.lojinha.domain.scale

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class Prix3ProtocolParserTest {

    @Test
    fun extractLatestFrame_parsesStandardWeightAndStatuses() {
        val stx = Prix3ProtocolParser.STX
        val etx = Prix3ProtocolParser.ETX

        val buf = mutableListOf(stx, *("01250".encodeToByteArray().toTypedArray()), etx)
        val ok = Prix3ProtocolParser.extractLatestFrame(buf)
        assertEquals(Prix3ProtocolParser.ParsedFrame(1250L, ScaleReadingStatus.OK), ok)
        assertEquals(0, buf.size)

        val zeroBuf = mutableListOf(stx, *("00000".encodeToByteArray().toTypedArray()), etx)
        assertEquals(
            Prix3ProtocolParser.ParsedFrame(0L, ScaleReadingStatus.ZERO),
            Prix3ProtocolParser.extractLatestFrame(zeroBuf)
        )

        val negBuf = mutableListOf(stx, *("NNNNN".encodeToByteArray().toTypedArray()), etx)
        assertEquals(
            Prix3ProtocolParser.ParsedFrame(0L, ScaleReadingStatus.NEGATIVE),
            Prix3ProtocolParser.extractLatestFrame(negBuf)
        )

        val overBuf = mutableListOf(stx, *("SSSSS".encodeToByteArray().toTypedArray()), etx)
        assertEquals(
            Prix3ProtocolParser.ParsedFrame(0L, ScaleReadingStatus.OVERLOAD),
            Prix3ProtocolParser.extractLatestFrame(overBuf)
        )

        val unstableBuf = mutableListOf(stx, *("IIIII".encodeToByteArray().toTypedArray()), etx)
        assertEquals(
            Prix3ProtocolParser.ParsedFrame(0L, ScaleReadingStatus.UNSTABLE),
            Prix3ProtocolParser.extractLatestFrame(unstableBuf)
        )

        val negNumBuf = mutableListOf(stx, *("-0.050".encodeToByteArray().toTypedArray()), etx)
        assertEquals(
            Prix3ProtocolParser.ParsedFrame(0L, ScaleReadingStatus.NEGATIVE),
            Prix3ProtocolParser.extractLatestFrame(negNumBuf)
        )

        assertNull(Prix3ProtocolParser.parsePayload("1.5e2"))
    }

    @Test
    fun extractLatestFrame_handlesSplitFramesAndNoise() {
        val stx = Prix3ProtocolParser.STX
        val etx = Prix3ProtocolParser.ETX

        // Leading noise + partial frame
        val buf = mutableListOf<Byte>(0x00, 0x15, stx, '0'.code.toByte(), '2'.code.toByte())
        assertNull(Prix3ProtocolParser.extractLatestFrame(buf))

        // Remainder arrives + a newer complete frame in the same chunk
        buf.addAll(listOf('5'.code.toByte(), '0'.code.toByte(), '0'.code.toByte(), etx))
        buf.add(stx)
        buf.addAll("03125".encodeToByteArray().toList())
        buf.add(etx)

        val latest = Prix3ProtocolParser.extractLatestFrame(buf)
        assertEquals(Prix3ProtocolParser.ParsedFrame(3125L, ScaleReadingStatus.OK), latest)
        assertEquals(0, buf.size)
    }

    @Test
    fun formatGramsForVeggieUnit_formatsKgAndGramsCorrectly() {
        assertEquals(
            "1,25",
            Prix3ProtocolParser.formatGramsForVeggieUnit(1250L, "kg", allowDecimals = true, decimalSeparator = ',')
        )
        assertEquals(
            "1.255",
            Prix3ProtocolParser.formatGramsForVeggieUnit(1255L, "kg", allowDecimals = true, decimalSeparator = '.')
        )
        assertEquals(
            "1250",
            Prix3ProtocolParser.formatGramsForVeggieUnit(1250L, "g", allowDecimals = false, decimalSeparator = ',')
        )
    }

    @Test
    fun findMatchingPort_matchesExactNameOrUsbMetadata() {
        val port1 = ScalePortInfo("cu.usbserial-1410", "USB-Serial", vendorId = 0x1A86, productId = 0x7523, serialNumber = "ABC123")
        val port2 = ScalePortInfo("COM4", "Prolific", vendorId = 0x067B, productId = 0x2303, serialNumber = "XYZ999")

        val exactConfig = ScaleConfig(enabled = true, portName = "COM4", vendorId = 0x067B, productId = 0x2303, serialNumber = "XYZ999")
        assertEquals(port2, Prix3ProtocolParser.findMatchingPort(exactConfig, listOf(port1, port2)))

        // Simulate macOS assigning a new port path (cu.usbserial-1420 instead of 1410)
        val movedPort1 = port1.copy(systemPortName = "cu.usbserial-1420")
        val movedConfig = ScaleConfig(enabled = true, portName = "cu.usbserial-1410", vendorId = 0x1A86, productId = 0x7523, serialNumber = "ABC123")
        assertEquals(movedPort1, Prix3ProtocolParser.findMatchingPort(movedConfig, listOf(movedPort1, port2)))

        // Simulate adapter without serial number, matched uniquely by VID/PID
        val vidPidPort = ScalePortInfo("COM7", "USB-Serial", vendorId = 0x1A86, productId = 0x7523, serialNumber = null)
        val vidPidConfig = ScaleConfig(enabled = true, portName = "COM3", vendorId = 0x1A86, productId = 0x7523, serialNumber = null)
        assertEquals(vidPidPort, Prix3ProtocolParser.findMatchingPort(vidPidConfig, listOf(vidPidPort, port2)))
    }
}
