package de.joelneumann.lojinha.data.database

import androidx.room.TypeConverter
import de.joelneumann.lojinha.domain.model.BackupScheduleConfig
import de.joelneumann.lojinha.domain.model.Barcode
import de.joelneumann.lojinha.domain.model.TransactionItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromBarcodeList(value: List<Barcode>): String {
        return json.encodeToString(value)
    }

    @TypeConverter
    fun toBarcodeList(value: String): List<Barcode> {
        return if (value.isBlank()) emptyList() else runCatching { json.decodeFromString<List<Barcode>>(value) }.getOrElse { emptyList() }
    }

    @TypeConverter
    fun fromTransactionItemList(value: List<TransactionItem>): String {
        return json.encodeToString(value)
    }

    @TypeConverter
    fun toTransactionItemList(value: String): List<TransactionItem> {
        return if (value.isBlank()) emptyList() else runCatching { json.decodeFromString<List<TransactionItem>>(value) }.getOrElse { emptyList() }
    }

    @TypeConverter
    fun fromBackupScheduleConfig(value: BackupScheduleConfig): String {
        return json.encodeToString(value)
    }

    @TypeConverter
    fun toBackupScheduleConfig(value: String): BackupScheduleConfig {
        return if (value.isBlank()) {
            BackupScheduleConfig.Timed("02:00")
        } else {
            runCatching {
                json.decodeFromString<BackupScheduleConfig>(value)
            }.getOrElse {
                BackupScheduleConfig.Timed("02:00")
            }
        }
    }
}
