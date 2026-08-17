package de.joelneumann.lojinha.data.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import de.joelneumann.lojinha.data.dao.ProductDao
import de.joelneumann.lojinha.data.dao.SettingsDao
import de.joelneumann.lojinha.data.dao.TransactionDao
import de.joelneumann.lojinha.data.dao.UserDao
import de.joelneumann.lojinha.data.entity.ProductEntity
import de.joelneumann.lojinha.data.entity.SettingsEntity
import de.joelneumann.lojinha.data.entity.TransactionEntity
import de.joelneumann.lojinha.data.entity.UserEntity

@Database(
    entities = [UserEntity::class, ProductEntity::class, TransactionEntity::class, SettingsEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun productDao(): ProductDao
    abstract fun transactionDao(): TransactionDao
    abstract fun settingsDao(): SettingsDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase>
