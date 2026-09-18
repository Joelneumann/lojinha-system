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

    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("CREATE TABLE IF NOT EXISTS `billing_lists` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `basePrice` INTEGER, `isDeleted` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            connection.execSQL("CREATE TABLE IF NOT EXISTS `billing_list_users` (`id` TEXT NOT NULL, `listId` TEXT NOT NULL, `userId` TEXT NOT NULL, `quantity` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`listId`) REFERENCES `billing_lists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_billing_list_users_listId` ON `billing_list_users` (`listId`)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_billing_list_users_userId` ON `billing_list_users` (`userId`)")
        }
    }

    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE `billing_lists` ADD COLUMN `comment` TEXT")
        }
    }

    private val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE settings ADD COLUMN supportEmail TEXT DEFAULT NULL")
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
        builder.addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
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
        val currentSettings = settingsDao.getSettings()
        if (currentSettings == null) {
            settingsDao.insertOrUpdateSettings(
                SettingsEntity(
                    id = 1,
                    adminPasswordHash = de.joelneumann.lojinha.security.PasswordHasher.hash("admin"),
                    globalMarkupPercent = 0.0,
                    usdExchangeRate = 0.18,
                    eurExchangeRate = 0.16,
                    inactivityTimeoutMinutes = 3
                )
            )
        } else {
            // Migrate legacy plain-text admin password if not already a 64-char hash
            if (!de.joelneumann.lojinha.security.PasswordHasher.isHash(currentSettings.adminPasswordHash)) {
                val migratedSettings = currentSettings.copy(
                    adminPasswordHash = de.joelneumann.lojinha.security.PasswordHasher.hash(currentSettings.adminPasswordHash)
                )
                settingsDao.insertOrUpdateSettings(migratedSettings)
            }
        }

        // Migrate any existing users with legacy plain-text PINs
        val userDao = db.userDao()
        val users = userDao.getAllUsers()
        users.forEach { user ->
            val rawPin = user.pin
            if (!rawPin.isNullOrBlank() && !de.joelneumann.lojinha.security.PasswordHasher.isHash(rawPin)) {
                userDao.updateUserProfile(
                    id = user.id,
                    name = user.name,
                    language = user.language,
                    secondaryCurrency = user.secondaryCurrency,
                    pin = de.joelneumann.lojinha.security.PasswordHasher.hash(rawPin.trim()),
                    userBarcode = user.userBarcode,
                    userBarcodeNumber = user.userBarcodeNumber,
                    isActive = user.isActive,
                    isDeleted = user.isDeleted,
                    avatarType = user.avatarType,
                    avatarEmoji = user.avatarEmoji,
                    avatarColor = user.avatarColor
                )
            }
        }
    }
}
