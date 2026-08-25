package de.joelneumann.lojinha.domain.model

import de.joelneumann.lojinha.ui.utils.PlatformFile
import kotlinx.serialization.Serializable

@Serializable
data class CsvImportResult(
    val totalProcessed: Int,
    val addedCount: Int,
    val updatedCount: Int,
    val strippedBarcodesCount: Int,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)

data class BackupFileInfo(
    val file: PlatformFile,
    val name: String,
    val timestamp: Long,
    val formattedDate: String,
    val formattedSize: String
)
