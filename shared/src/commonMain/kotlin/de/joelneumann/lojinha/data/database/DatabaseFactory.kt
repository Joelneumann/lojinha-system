package de.joelneumann.lojinha.data.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.joelneumann.lojinha.data.entity.ProductEntity
import de.joelneumann.lojinha.data.entity.SettingsEntity
import de.joelneumann.lojinha.data.entity.UserEntity
import de.joelneumann.lojinha.domain.model.Barcode
import de.joelneumann.lojinha.domain.model.Language
import de.joelneumann.lojinha.domain.model.SecondaryCurrency
import de.joelneumann.lojinha.domain.model.UnitType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import java.io.File

object DatabaseFactory {
    fun createDatabase(): AppDatabase {
        val dbFile = File(System.getProperty("user.home"), ".lojinha/lojinha_room.db")
        dbFile.parentFile?.mkdirs()

        val builder = Room.databaseBuilder<AppDatabase>(
            name = dbFile.absolutePath,
            factory = { AppDatabaseConstructor.initialize() }
        )
        builder.setDriver(BundledSQLiteDriver())
        builder.setQueryCoroutineContext(Dispatchers.IO)
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

        val userDao = db.userDao()
        if (userDao.getAllUsers().isEmpty()) {
            val seedUsers = listOf(
                UserEntity(
                    id = "u-maria-silva-001",
                    name = "Maria Silva",
                    balance = 4550L, // R$ 45,50
                    language = Language.DE.code,
                    secondaryCurrency = SecondaryCurrency.USD.name,
                    pin = null,
                    userBarcode = "USER001",
                    userBarcodeNumber = "USER001",
                    isActive = true
                ),
                UserEntity(
                    id = "u-joao-santos-002",
                    name = "João Santos",
                    balance = 12000L, // R$ 120,00
                    language = Language.BR.code,
                    secondaryCurrency = SecondaryCurrency.NONE.name,
                    pin = "1234",
                    userBarcode = "USER002",
                    userBarcodeNumber = "USER002",
                    isActive = true
                ),
                UserEntity(
                    id = "u-ana-costa-003",
                    name = "Ana Costa",
                    balance = 1500L, // R$ 15,00
                    language = Language.EN.code,
                    secondaryCurrency = SecondaryCurrency.EUR.name,
                    pin = null,
                    userBarcode = "USER003",
                    userBarcodeNumber = "USER003",
                    isActive = true
                )
            )
            seedUsers.forEach { userDao.insertOrUpdateUser(it) }
        }

        val productDao = db.productDao()
        if (productDao.getAllProducts().isEmpty()) {
            val seedProducts = listOf(
                ProductEntity(
                    id = "p-club-mate-001",
                    name = "Club Mate (330ml)",
                    barcodes = listOf(Barcode("4029764001807", "Single Can")),
                    basePrice = 800L, // R$ 8,00
                    unitType = UnitType.PIECE.name,
                    stockQuantity = 50L,
                    customMarkupPercent = null,
                    isActive = true
                ),
                ProductEntity(
                    id = "p-snickers-002",
                    name = "Snickers Bar",
                    barcodes = listOf(Barcode("5000159461122", "Single Bar")),
                    basePrice = 450L, // R$ 4,50
                    unitType = UnitType.PIECE.name,
                    stockQuantity = 30L,
                    customMarkupPercent = null,
                    isActive = true
                ),
                ProductEntity(
                    id = "p-apples-003",
                    name = "Apples (Fresh)",
                    barcodes = listOf(Barcode("1000000000001", "Per Kg")),
                    basePrice = 500L, // R$ 5,00 per kg
                    unitType = UnitType.WEIGHT.name,
                    stockQuantity = 15000L, // 15 kg
                    customMarkupPercent = null,
                    isActive = true
                ),
                ProductEntity(
                    id = "p-coffee-004",
                    name = "Espresso Coffee",
                    barcodes = listOf(Barcode("1000000000002", "Single Espresso")),
                    basePrice = 300L, // R$ 3,00
                    unitType = UnitType.PIECE.name,
                    stockQuantity = 100L,
                    customMarkupPercent = null,
                    isActive = true
                ),
                ProductEntity(
                    id = "p-water-005",
                    name = "Mineral Water (500ml)",
                    barcodes = listOf(Barcode("7891000000011", "500ml Bottle")),
                    basePrice = 250L, // R$ 2,50
                    unitType = UnitType.PIECE.name,
                    stockQuantity = 40L,
                    customMarkupPercent = null,
                    isActive = true
                )
            )
            seedProducts.forEach { productDao.insertOrUpdateProduct(it) }
        }
    }
}
