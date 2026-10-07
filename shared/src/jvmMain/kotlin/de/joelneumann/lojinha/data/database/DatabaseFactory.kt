package de.joelneumann.lojinha.data.database

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import de.joelneumann.lojinha.data.entity.SettingsEntity
import de.joelneumann.lojinha.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import java.io.File

object DatabaseFactory {
    private const val TAG = "DatabaseFactory"
    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(connection: SQLiteConnection) {
            AppLogger.info(TAG, "Starting database migration: v7 -> v8 (adding balance tracking to transactions)...")
            try {
                connection.execSQL("ALTER TABLE transactions ADD COLUMN userBalanceBefore INTEGER DEFAULT NULL")
                connection.execSQL("ALTER TABLE transactions ADD COLUMN userBalanceAfter INTEGER DEFAULT NULL")
                connection.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_referenceTransactionId ON transactions(referenceTransactionId)")
                connection.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_userId ON transactions(userId)")
                connection.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_timestamp ON transactions(timestamp)")
                connection.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_type ON transactions(type)")
                AppLogger.info(TAG, "Database migration v7 -> v8 completed successfully.")
            } catch (e: Exception) {
                AppLogger.error(TAG, "Database migration v7 -> v8 failed: ${e.message}", e)
                throw e
            }
        }
    }

    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(connection: SQLiteConnection) {
            AppLogger.info(TAG, "Starting database migration: v8 -> v9 (creating billing_lists tables)...")
            try {
                connection.execSQL("CREATE TABLE IF NOT EXISTS `billing_lists` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `basePrice` INTEGER, `isDeleted` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                connection.execSQL("CREATE TABLE IF NOT EXISTS `billing_list_users` (`id` TEXT NOT NULL, `listId` TEXT NOT NULL, `userId` TEXT NOT NULL, `quantity` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`listId`) REFERENCES `billing_lists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                connection.execSQL("CREATE INDEX IF NOT EXISTS `index_billing_list_users_listId` ON `billing_list_users` (`listId`)")
                connection.execSQL("CREATE INDEX IF NOT EXISTS `index_billing_list_users_userId` ON `billing_list_users` (`userId`)")
                AppLogger.info(TAG, "Database migration v8 -> v9 completed successfully.")
            } catch (e: Exception) {
                AppLogger.error(TAG, "Database migration v8 -> v9 failed: ${e.message}", e)
                throw e
            }
        }
    }

    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(connection: SQLiteConnection) {
            AppLogger.info(TAG, "Starting database migration: v9 -> v10 (adding comment column to billing_lists)...")
            try {
                connection.execSQL("ALTER TABLE `billing_lists` ADD COLUMN `comment` TEXT")
                AppLogger.info(TAG, "Database migration v9 -> v10 completed successfully.")
            } catch (e: Exception) {
                AppLogger.error(TAG, "Database migration v9 -> v10 failed: ${e.message}", e)
                throw e
            }
        }
    }

    private val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(connection: SQLiteConnection) {
            AppLogger.info(TAG, "Starting database migration: v10 -> v11 (adding supportEmail to settings)...")
            try {
                connection.execSQL("ALTER TABLE settings ADD COLUMN supportEmail TEXT DEFAULT NULL")
                AppLogger.info(TAG, "Database migration v10 -> v11 completed successfully.")
            } catch (e: Exception) {
                AppLogger.error(TAG, "Database migration v10 -> v11 failed: ${e.message}", e)
                throw e
            }
        }
    }

    private val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(connection: SQLiteConnection) {
            AppLogger.info(TAG, "Starting database migration: v11 -> v12 (adding lastExecutionTime to billing_lists)...")
            try {
                connection.execSQL("ALTER TABLE billing_lists ADD COLUMN lastExecutionTime INTEGER DEFAULT NULL")
                AppLogger.info(TAG, "Database migration v11 -> v12 completed successfully.")
            } catch (e: Exception) {
                AppLogger.error(TAG, "Database migration v11 -> v12 failed: ${e.message}", e)
                throw e
            }
        }
    }

    private val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(connection: SQLiteConnection) {
            AppLogger.info(TAG, "Starting database migration: v12 -> v13 (deduplicating members and adding indices)...")
            try {
                // Deduplicate any historical duplicate members before enforcing uniqueness
                connection.execSQL("DELETE FROM billing_list_users WHERE id NOT IN (SELECT MIN(id) FROM billing_list_users GROUP BY listId, userId);")
                // Ensure no rogue partial cancellation index exists that would fail Room's TableInfo validation
                connection.execSQL("DROP INDEX IF EXISTS index_transactions_unique_cancellation")
                // Drop old listId index superseded by composite (listId, userId) index
                connection.execSQL("DROP INDEX IF EXISTS index_billing_list_users_listId")
                // 1. Composite unique index on billing_list_users to prevent duplicate member entries
                connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_billing_list_users_listId_userId ON billing_list_users(listId, userId)")
                // 2. User table indices for fast name sorting and barcode scanning
                connection.execSQL("CREATE INDEX IF NOT EXISTS index_users_name ON users(name)")
                connection.execSQL("CREATE INDEX IF NOT EXISTS index_users_userBarcode ON users(userBarcode)")
                connection.execSQL("CREATE INDEX IF NOT EXISTS index_users_userBarcodeNumber ON users(userBarcodeNumber)")
                AppLogger.info(TAG, "Database migration v12 -> v13 completed successfully.")
            } catch (e: Exception) {
                AppLogger.error(TAG, "Database migration v12 -> v13 failed: ${e.message}", e)
                throw e
            }
        }
    }

    @Volatile
    private var instance: AppDatabase? = null

    fun createDatabase(): AppDatabase {
        return instance ?: synchronized(this) {
            instance ?: buildDatabase().also {
                instance = it
                AppLogger.info(TAG, "Database initialized and ready.")
            }
        }
    }

    private fun buildDatabase(): AppDatabase {
        val dbFile = File(System.getProperty("user.home"), ".lojinha/lojinha_room.db")
        dbFile.parentFile?.mkdirs()

        // 1. Safety snapshot before Room initializes to guard against unexpected migration issues
        if (dbFile.exists()) {
            try {
                val startupBackup = File(dbFile.parentFile, "lojinha_room.db.startup_backup")
                dbFile.copyTo(startupBackup, overwrite = true)

                // Copy companion WAL and SHM files if active to ensure a complete, consistent snapshot
                val walFile = File("${dbFile.absolutePath}-wal")
                val shmFile = File("${dbFile.absolutePath}-shm")
                val backupWal = File("${startupBackup.absolutePath}-wal")
                val backupShm = File("${startupBackup.absolutePath}-shm")

                if (walFile.exists()) {
                    walFile.copyTo(backupWal, overwrite = true)
                } else if (backupWal.exists()) {
                    backupWal.delete()
                }

                if (shmFile.exists()) {
                    shmFile.copyTo(backupShm, overwrite = true)
                } else if (backupShm.exists()) {
                    backupShm.delete()
                }

                AppLogger.info(TAG, "Created pre-startup safety backup at ${startupBackup.absolutePath}")
            } catch (e: Exception) {
                AppLogger.warn(TAG, "Could not create pre-startup DB backup: ${e.message}", e)
            }
        }

        val builder = Room.databaseBuilder<AppDatabase>(
            name = dbFile.absolutePath,
            factory = { AppDatabase_Impl() }
        )
        builder.setDriver(BundledSQLiteDriver())
        builder.setQueryCoroutineContext(Dispatchers.IO)
        builder.addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
        builder.addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(connection: SQLiteConnection) {
                AppLogger.info(TAG, "Room onCreate: Fresh database created from scratch (initial install).")
            }

            override fun onOpen(connection: SQLiteConnection) {
                connection.execSQL("PRAGMA foreign_keys = ON;")
                connection.execSQL("PRAGMA busy_timeout = 5000;")
                connection.execSQL("PRAGMA synchronous = NORMAL;")
                try {
                    connection.execSQL("UPDATE products SET stockQuantity = 0 WHERE stockQuantity < 0;")
                } catch (_: Exception) {}
                val version = try {
                    val stmt = connection.prepare("PRAGMA user_version;")
                    try {
                        if (stmt.step()) stmt.getLong(0).toInt() else -1
                    } finally {
                        stmt.close()
                    }
                } catch (_: Exception) {
                    -1
                }
                AppLogger.info(TAG, "Room onOpen: Database opened successfully (schema version: $version).")
            }

            override fun onDestructiveMigration(connection: SQLiteConnection) {
                AppLogger.warn(TAG, "Room onDestructiveMigration: Database destructive migration was executed.")
            }
        })
        // Strictly disable destructive migration in production to prevent data loss
        builder.fallbackToDestructiveMigration(false)
        val db = builder.build()

        // 2. Ensure SQLite WAL Mode is set in database header
        runBlocking(Dispatchers.IO) {
            try {
                val connection = BundledSQLiteDriver().open(dbFile.absolutePath)
                try {
                    connection.execSQL("PRAGMA journal_mode = WAL;")
                    try {
                        connection.execSQL("DROP INDEX IF EXISTS index_transactions_unique_cancellation;")
                        connection.execSQL("DROP INDEX IF EXISTS index_billing_list_users_listId;")
                        connection.execSQL("DELETE FROM billing_list_users WHERE userId NOT IN (SELECT id FROM users WHERE isDeleted = 0);")
                    } catch (_: Exception) {}
                } finally {
                    connection.close()
                }
            } catch (e: Exception) {
                AppLogger.warn(TAG, "Failed to configure WAL header: ${e.message}", e)
            }

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

        // Seed a default automated local backup routine if no routines currently exist
        try {
            val backupDao = db.backupDao()
            val existingRoutines = backupDao.getAllBackups()
            if (existingRoutines.isEmpty()) {
                val defaultBackupDir = File(System.getProperty("user.home"), ".lojinha/backups")
                if (!defaultBackupDir.exists()) {
                    defaultBackupDir.mkdirs()
                }
                val defaultRoutine = de.joelneumann.lojinha.data.entity.BackupEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    name = "Default Daily Local Backup",
                    isEnabled = true,
                    type = de.joelneumann.lojinha.domain.model.BackupType.LOCAL.name,
                    fileType = de.joelneumann.lojinha.domain.model.BackupFileType.DB.name,
                    writeMode = de.joelneumann.lojinha.domain.model.BackupWriteMode.CREATE_NEW_FILE.name,
                    scheduleConfig = de.joelneumann.lojinha.domain.model.BackupScheduleConfig.Timed(timeOfDay = "02:00"),
                    backupLocationPath = defaultBackupDir.absolutePath,
                    lastBackupTimestamp = null
                )
                backupDao.insertOrUpdateBackup(defaultRoutine)
                AppLogger.info(TAG, "Seeded default automated local backup routine to ${defaultBackupDir.absolutePath}")
            }
        } catch (e: Exception) {
            AppLogger.warn(TAG, "Failed to seed default backup routine: ${e.message}", e)
        }
    }
}
