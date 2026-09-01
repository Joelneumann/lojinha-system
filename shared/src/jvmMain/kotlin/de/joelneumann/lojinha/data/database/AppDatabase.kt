package de.joelneumann.lojinha.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import de.joelneumann.lojinha.data.dao.*
import de.joelneumann.lojinha.data.entity.*

@Database(
    entities = [UserEntity::class, ProductEntity::class, TransactionEntity::class, SettingsEntity::class, BackupEntity::class],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun productDao(): ProductDao
    abstract fun transactionDao(): TransactionDao
    abstract fun settingsDao(): SettingsDao
    abstract fun backupDao(): BackupDao
}
