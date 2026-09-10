package de.joelneumann.lojinha.data.database

import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import de.joelneumann.lojinha.data.entity.SettingsEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import java.io.File

object DatabaseFactory {
    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE transactions ADD COLUMN userBalanceBefore INTEGER DEFAULT NULL")
            connection.execSQL("ALTER TABLE transactions ADD COLUMN userBalanceAfter INTEGER DEFAULT NULL")
            connection.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_referenceTransactionId ON transactions(referenceTransactionId)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_userId ON transactions(userId)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_timestamp ON transactions(timestamp)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_type ON transactions(type)")
        }
    }

    @Volatile
    private var instance: AppDatabase? = null

    fun createDatabase(): AppDatabase {
        return instance ?: synchronized(this) {
            instance ?: buildDatabase().also { instance = it }
        }
    }

    private fun buildDatabase(): AppDatabase {
        val dbFile = File(System.getProperty("user.home"), ".lojinha/lojinha_room.db")
        dbFile.parentFile?.mkdirs()

        val builder = Room.databaseBuilder<AppDatabase>(
            name = dbFile.absolutePath,
            factory = { AppDatabase_Impl() }
        )
        builder.setDriver(BundledSQLiteDriver())
        builder.setQueryCoroutineContext(Dispatchers.IO)
        builder.addMigrations(MIGRATION_7_8)
        builder.fallbackToDestructiveMigration(true)
        val db = builder.build()

        // Seed initial data if database is new/empty
        runBlocking(Dispatchers.IO) {
            seedInitialData(db)
        }

        return db
    }

    private suspend fun seedInitialData(db: AppDatabase) {
        val settingsDao = db.settingsDao()
        if (settingsDao.getSettings() == null) {
            settingsDao.insertOrUpdateSettings(
                SettingsEntity(
                    id = 1,
                    adminPasswordHash = "admin",
                    globalMarkupPercent = 0.0,
                    usdExchangeRate = 0.18,
                    eurExchangeRate = 0.16,
                    inactivityTimeoutMinutes = 3
                )
            )
        }
    }
}
