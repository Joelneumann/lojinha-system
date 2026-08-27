package de.joelneumann.lojinha.data.service

import de.joelneumann.lojinha.domain.model.BackupFileType
import de.joelneumann.lojinha.domain.model.BackupRoutine
import de.joelneumann.lojinha.domain.model.BackupScheduleConfig
import de.joelneumann.lojinha.domain.model.BackupType
import de.joelneumann.lojinha.domain.model.BackupWriteMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackupRoutineTest {

    @Test
    fun testIntervalScheduleNextDueCalculation() {
        val anchorTime = 1700000000000L // Fixed reference timestamp
        val routine = BackupRoutine(
            id = "rt-test-1",
            name = "Test Interval Routine",
            isEnabled = true,
            type = BackupType.LOCAL,
            fileType = BackupFileType.DB,
            scheduleConfig = BackupScheduleConfig.Interval(
                intervalHours = 1,
                intervalMinutes = 30,
                anchorStartTimestamp = anchorTime
            ),
            backupLocationPath = "/tmp/backups",
            lastBackupTimestamp = anchorTime
        )

        // 1 hour 30 mins = 90 mins = 5,400,000 ms
        val expectedNextDue = anchorTime + 5_400_000L
        val actualNextDue = routine.calculateNextDueTimestamp()

        assertEquals(expectedNextDue, actualNextDue)
    }

    @Test
    fun testRestartSafetyDeterministicCalculation() {
        val anchorTime = de.joelneumann.lojinha.ui.utils.currentTimeMillis() - 10_000_000L
        val lastRunTime = anchorTime + 3_600_000L

        val routine = BackupRoutine(
            id = "rt-test-2",
            name = "Restart Safety Routine",
            isEnabled = true,
            type = BackupType.LOCAL,
            fileType = BackupFileType.CSV,
            scheduleConfig = BackupScheduleConfig.Interval(
                intervalHours = 2,
                intervalMinutes = 0,
                anchorStartTimestamp = anchorTime
            ),
            backupLocationPath = "/tmp/backups",
            lastBackupTimestamp = lastRunTime
        )

        // Calculation should always equal lastRunTime + 2 hours (7,200,000 ms) regardless of system restarts
        val expectedNextDue = lastRunTime + 7_200_000L
        val actualNextDue = routine.calculateNextDueTimestamp()

        assertEquals(expectedNextDue, actualNextDue)
    }

    @Test
    fun testTimedScheduleCalculation() {
        val routine = BackupRoutine(
            id = "rt-test-3",
            name = "Daily Timed Routine",
            isEnabled = true,
            type = BackupType.LOCAL,
            fileType = BackupFileType.BOTH,
            scheduleConfig = BackupScheduleConfig.Timed("03:00"),
            backupLocationPath = "/tmp/backups",
            lastBackupTimestamp = null
        )

        val nextDue = routine.calculateNextDueTimestamp()
        assertTrue(nextDue > 0L)
    }

    @Test
    fun testOnDataChangeScheduleNextDueCalculation() {
        val routine = BackupRoutine(
            id = "rt-test-4",
            name = "Realtime Data Change Routine",
            isEnabled = true,
            type = BackupType.LOCAL,
            fileType = BackupFileType.DB,
            scheduleConfig = BackupScheduleConfig.OnDataChange(debounceMs = 1000L),
            backupLocationPath = "/tmp/backups",
            lastBackupTimestamp = null
        )

        val nextDue = routine.calculateNextDueTimestamp()
        assertEquals(Long.MAX_VALUE, nextDue)
    }

    @Test
    fun testBackupWriteModeDefaults() {
        val routineDefault = BackupRoutine(
            id = "rt-test-5",
            name = "Default Write Mode Routine"
        )
        assertEquals(BackupWriteMode.CREATE_NEW_FILE, routineDefault.writeMode)

        val routineOverwrite = BackupRoutine(
            id = "rt-test-6",
            name = "Overwrite Mode Routine",
            writeMode = BackupWriteMode.OVERWRITE_LATEST
        )
        assertEquals(BackupWriteMode.OVERWRITE_LATEST, routineOverwrite.writeMode)
    }
}
