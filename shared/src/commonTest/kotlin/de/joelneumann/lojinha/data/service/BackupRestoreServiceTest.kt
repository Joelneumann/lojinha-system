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
    fun testUserBarcodeSelectiveCollisionStrippingLogic() {
        val existingUserBarcodes = mapOf(
            "USER_BC_001" to "u-existing-001"
        )

        val newImportUserId = "u-new-002"
        val sameUserImportId = "u-existing-001"

        // Candidate 1: Collides with another user (u-existing-001) -> Stripped (null)
        val candidateBarcode1 = "USER_BC_001"
        val assigned1 = existingUserBarcodes[candidateBarcode1]
        val importedBarcode1: String? = if (assigned1 != null && assigned1 != newImportUserId) null else candidateBarcode1

        assertEquals(null, importedBarcode1)

        // Candidate 2: Same user updating their record -> Preserved
        val assigned2 = existingUserBarcodes[candidateBarcode1]
        val importedBarcode2: String? = if (assigned2 != null && assigned2 != sameUserImportId) null else candidateBarcode1

        assertEquals("USER_BC_001", importedBarcode2)

        // Candidate 3: Unique barcode for new user -> Preserved
        val candidateBarcode3 = "USER_BC_003"
        val assigned3 = existingUserBarcodes[candidateBarcode3]
        val importedBarcode3: String? = if (assigned3 != null && assigned3 != newImportUserId) null else candidateBarcode3

        assertEquals("USER_BC_003", importedBarcode3)
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
