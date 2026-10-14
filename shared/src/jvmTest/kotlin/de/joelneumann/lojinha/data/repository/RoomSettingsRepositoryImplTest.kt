package de.joelneumann.lojinha.data.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.joelneumann.lojinha.data.database.AppDatabase
import de.joelneumann.lojinha.data.database.AppDatabase_Impl
import de.joelneumann.lojinha.domain.model.SystemSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class RoomSettingsRepositoryImplTest {

    private lateinit var tempDir: File
    private lateinit var dbFile: File
    private lateinit var db: AppDatabase
    private var dataChangedCallCount = 0
    private lateinit var repository: RoomSettingsRepositoryImpl

    @BeforeTest
    fun setup() {
        tempDir = Files.createTempDirectory("settings_repo_test").toFile()
        dbFile = File(tempDir, "test_settings.db")

        val builder = Room.databaseBuilder<AppDatabase>(
            name = dbFile.absolutePath,
            factory = { AppDatabase_Impl() }
        )
        builder.setDriver(BundledSQLiteDriver())
        builder.setQueryCoroutineContext(Dispatchers.IO)
        db = builder.build()

        dataChangedCallCount = 0
        repository = RoomSettingsRepositoryImpl(
            settingsDao = db.settingsDao(),
            onDataChanged = { dataChangedCallCount++ }
        )
    }

    @AfterTest
    fun tearDown() {
        db.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun testDefaultSettingsContainExpectedThresholds() = runTest {
        val settings = repository.getSettings()
        assertEquals(300_00L, settings.yellowBalanceWarningThresholdCents)
        assertEquals(0L, settings.redBalanceWarningThresholdCents)
    }

    @Test
    fun testUpdateSettingsPersistsThresholdsAndTriggersCallback() = runTest {
        val initial = repository.getSettings()
        val custom = initial.copy(
            yellowBalanceWarningThresholdCents = 150_00L,
            redBalanceWarningThresholdCents = -50_00L
        )

        repository.updateSettings(custom, notifyDataChanged = true)

        val retrieved = repository.getSettings()
        assertEquals(150_00L, retrieved.yellowBalanceWarningThresholdCents)
        assertEquals(-50_00L, retrieved.redBalanceWarningThresholdCents)
        assertEquals(1, dataChangedCallCount)

        val flowValue = repository.getSettingsFlow().first()
        assertEquals(150_00L, flowValue.yellowBalanceWarningThresholdCents)
        assertEquals(-50_00L, flowValue.redBalanceWarningThresholdCents)
    }
}
