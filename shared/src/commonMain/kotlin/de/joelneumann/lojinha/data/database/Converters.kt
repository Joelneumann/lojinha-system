package de.joelneumann.lojinha.data.database

import androidx.room.TypeConverter
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
        return if (value.isBlank()) emptyList() else json.decodeFromString(value)
    }

    @TypeConverter
    fun fromTransactionItemList(value: List<TransactionItem>): String {
        return json.encodeToString(value)
    }

    @TypeConverter
    fun toTransactionItemList(value: String): List<TransactionItem> {
        return if (value.isBlank()) emptyList() else json.decodeFromString(value)
    }

    @TypeConverter
    fun fromBackupScheduleConfig(value: de.joelneumann.lojinha.domain.model.BackupScheduleConfig): String {
        return json.encodeToString(value)
    }

    @TypeConverter
    fun toBackupScheduleConfig(value: String): de.joelneumann.lojinha.domain.model.BackupScheduleConfig {
        return if (value.isBlank()) de.joelneumann.lojinha.domain.model.BackupScheduleConfig.Timed("02:00") else json.decodeFromString(value)
    }
}
