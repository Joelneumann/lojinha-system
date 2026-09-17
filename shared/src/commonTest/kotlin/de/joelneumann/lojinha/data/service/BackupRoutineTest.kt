package de.joelneumann.lojinha.data.service

import de.joelneumann.lojinha.domain.model.BackupFileType
import de.joelneumann.lojinha.domain.model.BackupRoutine
import de.joelneumann.lojinha.domain.model.BackupScheduleConfig
import de.joelneumann.lojinha.domain.model.BackupType
import de.joelneumann.lojinha.domain.model.BackupWriteMode
import kotlinx.datetime.toLocalDateTime
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
    fun testTimedScheduleDueTodayWhenNotRunYet() {
        val tz = kotlinx.datetime.TimeZone.currentSystemDefault()
        val nowInstant = kotlinx.datetime.Clock.System.now()
        val nowLdt = nowInstant.toLocalDateTime(tz)

        // Target time 1 hour before current time (or 00:00 if hour is 0)
        val targetHour = if (nowLdt.hour > 0) nowLdt.hour - 1 else 0
        val timeStr = "${targetHour.toString().padStart(2, '0')}:00"

        val routine = BackupRoutine(
            id = "rt-test-timed-due",
            name = "Daily Timed Routine Due Today",
            isEnabled = true,
            type = BackupType.LOCAL,
            fileType = BackupFileType.BOTH,
            scheduleConfig = BackupScheduleConfig.Timed(timeStr),
            backupLocationPath = "/tmp/backups",
            lastBackupTimestamp = null // Has not executed today
        )

        val nextDue = routine.calculateNextDueTimestamp()
        val nowMs = nowInstant.toEpochMilliseconds()

        if (nowLdt.hour > 0) {
            // Target was earlier today, so it MUST be due now!
            assertTrue(nowMs >= nextDue, "Expected nowMs ($nowMs) >= nextDue ($nextDue) for routine scheduled earlier today")
        } else {
            assertTrue(nextDue > 0L)
        }
    }

    @Test
    fun testTimedScheduleAdvancesToTomorrowAfterRunningToday() {
        val nowInstant = kotlinx.datetime.Clock.System.now()
        val nowMs = nowInstant.toEpochMilliseconds()

        val routine = BackupRoutine(
            id = "rt-test-timed-tomorrow",
            name = "Daily Timed Routine Already Ran Today",
            isEnabled = true,
            type = BackupType.LOCAL,
            fileType = BackupFileType.BOTH,
            scheduleConfig = BackupScheduleConfig.Timed("02:00"),
            backupLocationPath = "/tmp/backups",
            lastBackupTimestamp = nowMs // Executed just now today
        )

        val nextDue = routine.calculateNextDueTimestamp()
        // Must be in the future (tomorrow)
        assertTrue(nextDue > nowMs, "Expected nextDue ($nextDue) > nowMs ($nowMs) after executing today")
    }

    @Test
    fun testIntervalScheduleZeroOrNegativeReturnsMaxValue() {
        val routineZero = BackupRoutine(
            id = "rt-test-zero",
            name = "Zero Interval",
            scheduleConfig = BackupScheduleConfig.Interval(intervalHours = 0, intervalMinutes = 0)
        )
        assertEquals(Long.MAX_VALUE, routineZero.calculateNextDueTimestamp())

        val routineNegative = BackupRoutine(
            id = "rt-test-neg",
            name = "Negative Interval",
            scheduleConfig = BackupScheduleConfig.Interval(intervalHours = -1, intervalMinutes = 0)
        )
        assertEquals(Long.MAX_VALUE, routineNegative.calculateNextDueTimestamp())
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

    @Test
    fun testOneDriveBackupTypeRoutine() {
        val oneDriveRoutine = BackupRoutine(
            id = "rt-onedrive-1",
            name = "Realtime OneDrive Sync",
            type = BackupType.ONEDRIVE,
            fileType = BackupFileType.DB,
            writeMode = BackupWriteMode.OVERWRITE_LATEST,
            scheduleConfig = BackupScheduleConfig.OnDataChange(debounceMs = 1000L),
            backupLocationPath = "/LojinhaBackups"
        )

        assertEquals(BackupType.ONEDRIVE, oneDriveRoutine.type)
        assertEquals("/LojinhaBackups", oneDriveRoutine.backupLocationPath)
        assertEquals(Long.MAX_VALUE, oneDriveRoutine.calculateNextDueTimestamp())
    }
}
