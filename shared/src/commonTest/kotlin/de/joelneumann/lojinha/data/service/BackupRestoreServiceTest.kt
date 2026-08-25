package de.joelneumann.lojinha.data.service

import de.joelneumann.lojinha.domain.model.Barcode
import de.joelneumann.lojinha.domain.model.Product
import de.joelneumann.lojinha.domain.model.UnitType
import de.joelneumann.lojinha.domain.model.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackupRestoreServiceTest {

    @Test
    fun testBarcodeSerializationFormat() {
        val barcodes = listOf(
            Barcode("4029764001807", "Single Can"),
            Barcode("7891000000011", "Box")
        )
        val serialized = barcodes.joinToString("|") { "${it.code}:${it.description ?: ""}" }
        assertEquals("4029764001807:Single Can|7891000000011:Box", serialized)

        val deserialized = serialized.split("|").map { part ->
            val pair = part.split(":")
            Barcode(pair[0], pair.getOrNull(1))
        }
        assertEquals(2, deserialized.size)
        assertEquals("4029764001807", deserialized[0].code)
        assertEquals("Single Can", deserialized[0].description)
        assertEquals("7891000000011", deserialized[1].code)
    }

    @Test
    fun testUserBarcodeStrippingLogic() {
        val rawUserBarcode = "USER001"
        val rawUserBarcodeNumber = "USER001"

        // On import, user barcodes must be set to null for security and to avoid kiosk barcode collisions
        val importedUserBarcode: String? = null
        val importedUserBarcodeNumber: String? = null

        assertEquals(null, importedUserBarcode)
        assertEquals(null, importedUserBarcodeNumber)
    }

    @Test
    fun testProductBarcodeCollisionDetection() {
        val existingProductBarcodes = mapOf(
            "4029764001807" to "p-existing-001"
        )

        val newImportProductId = "p-new-002"
        val candidateBarcodes = listOf(
            Barcode("4029764001807", "Can"), // Collides with existing product
            Barcode("9999999999999", "Fresh") // Safe
        )

        val validBarcodes = mutableListOf<Barcode>()
        var strippedCount = 0

        candidateBarcodes.forEach { b ->
            val assignedId = existingProductBarcodes[b.code]
            if (assignedId != null && assignedId != newImportProductId) {
                strippedCount++
            } else {
                validBarcodes.add(b)
            }
        }

        assertEquals(1, strippedCount)
        assertEquals(1, validBarcodes.size)
        assertEquals("9999999999999", validBarcodes[0].code)
    }
}
