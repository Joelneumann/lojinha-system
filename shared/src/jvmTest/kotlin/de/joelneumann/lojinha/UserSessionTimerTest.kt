package de.joelneumann.lojinha

import de.joelneumann.lojinha.domain.model.SystemSettings
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.domain.repository.SettingsRepository
import de.joelneumann.lojinha.ui.viewmodel.UserSessionViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FakeSettingsRepository(
    initialSettings: SystemSettings = SystemSettings(inactivityTimeoutMinutes = 2)
) : SettingsRepository {
    val flow = MutableStateFlow(initialSettings)
    override fun getSettingsFlow(): Flow<SystemSettings> = flow
    override suspend fun getSettings(): SystemSettings = flow.value
    override suspend fun updateSettings(settings: SystemSettings) {
        flow.value = settings
    }
}

class UserSessionTimerTest {

    private val testUser = User(id = "user1", name = "Test User")

    @Test
    fun testInitialState_defaultTimeoutIsAtLeastTwoMinutes() = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var currentTime = 100_000L
        val repo = FakeSettingsRepository(SystemSettings(inactivityTimeoutMinutes = 2))
        var logoutRequested = false

        val vm = UserSessionViewModel(
            user = testUser,
            settingsRepository = repo,
            onLogoutRequest = { logoutRequested = true },
            clock = { currentTime },
            coroutineScope = testScope
        )

        try {
            delay(100L)
            assertEquals(120, vm.inactivitySecondsRemaining.value)
            assertFalse(vm.showInactivityWarning.value)
            assertFalse(logoutRequested)
        } finally {
            vm.stopInactivityTimer()
            testScope.cancel()
        }
    }

    @Test
    fun testWarningAppearsWhen60SecondsOrLessRemaining() = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var currentTime = 100_000L
        val repo = FakeSettingsRepository(SystemSettings(inactivityTimeoutMinutes = 2))
        var logoutRequested = false

        val vm = UserSessionViewModel(
            user = testUser,
            settingsRepository = repo,
            onLogoutRequest = { logoutRequested = true },
            clock = { currentTime },
            coroutineScope = testScope
        )

        try {
            // Advance clock by 59 seconds (elapsed = 59s, remaining = 61s)
            currentTime += 59_000L
            delay(350L)
            assertEquals(61, vm.inactivitySecondsRemaining.value)
            assertFalse(vm.showInactivityWarning.value)

            // Advance clock by 2 seconds (elapsed = 61s, remaining = 59s <= 60s)
            currentTime += 2_000L
            delay(350L)
            assertTrue(vm.inactivitySecondsRemaining.value <= 60)
            assertTrue(vm.showInactivityWarning.value)
            assertFalse(logoutRequested)
        } finally {
            vm.stopInactivityTimer()
            testScope.cancel()
        }
    }

    @Test
    fun testUserInteractionResetsTimerAndClearsWarning() = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var currentTime = 100_000L
        val repo = FakeSettingsRepository(SystemSettings(inactivityTimeoutMinutes = 2))

        val vm = UserSessionViewModel(
            user = testUser,
            settingsRepository = repo,
            onLogoutRequest = {},
            clock = { currentTime },
            coroutineScope = testScope
        )

        try {
            // Advance clock until warning is visible
            currentTime += 70_000L
            delay(350L)
            assertTrue(vm.showInactivityWarning.value)

            // Active user interaction (force = true or stayLoggedIn)
            currentTime += 1_000L
            vm.stayLoggedIn()
            assertFalse(vm.showInactivityWarning.value)
            assertEquals(120, vm.inactivitySecondsRemaining.value)
        } finally {
            vm.stopInactivityTimer()
            testScope.cancel()
        }
    }

    @Test
    fun testPassiveInteractionIgnoredWhileWarningVisible() = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var currentTime = 100_000L
        val repo = FakeSettingsRepository(SystemSettings(inactivityTimeoutMinutes = 2))

        val vm = UserSessionViewModel(
            user = testUser,
            settingsRepository = repo,
            onLogoutRequest = {},
            clock = { currentTime },
            coroutineScope = testScope
        )

        try {
            // Advance to warning threshold
            currentTime += 80_000L
            delay(350L)
            assertTrue(vm.showInactivityWarning.value)
            val remainingBefore = vm.inactivitySecondsRemaining.value

            // Passive mouse move (force = false) must NOT dismiss warning
            vm.onUserInteracted(force = false)
            assertTrue(vm.showInactivityWarning.value)
            assertEquals(remainingBefore, vm.inactivitySecondsRemaining.value)

            // Explicit click (force = true) DOES dismiss warning
            vm.onUserInteracted(force = true)
            assertFalse(vm.showInactivityWarning.value)
            assertEquals(120, vm.inactivitySecondsRemaining.value)
        } finally {
            vm.stopInactivityTimer()
            testScope.cancel()
        }
    }

    @Test
    fun testSleepWakeResilience_triggersImmediateLogout() = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var currentTime = 100_000L
        val repo = FakeSettingsRepository(SystemSettings(inactivityTimeoutMinutes = 2))
        var logoutCalled = false

        val vm = UserSessionViewModel(
            user = testUser,
            settingsRepository = repo,
            onLogoutRequest = { logoutCalled = true },
            clock = { currentTime },
            coroutineScope = testScope
        )

        try {
            // Simulate machine sleep for 30 minutes (1,800,000 ms)
            currentTime += 30 * 60 * 1000L
            delay(350L)

            assertEquals(0, vm.inactivitySecondsRemaining.value)
            assertFalse(vm.showInactivityWarning.value)
            assertTrue(logoutCalled)
        } finally {
            vm.stopInactivityTimer()
            testScope.cancel()
        }
    }

    @Test
    fun testPauseAndResumeInactivityTimer() = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var currentTime = 100_000L
        val repo = FakeSettingsRepository(SystemSettings(inactivityTimeoutMinutes = 2))

        val vm = UserSessionViewModel(
            user = testUser,
            settingsRepository = repo,
            onLogoutRequest = {},
            clock = { currentTime },
            coroutineScope = testScope
        )

        try {
            // Pause timer (e.g. post-purchase overview opened)
            vm.pauseInactivityTimer()

            // Advance time significantly while paused
            currentTime += 500_000L
            delay(350L)

            // Remaining seconds should not have counted down to zero or triggered logout
            assertFalse(vm.showInactivityWarning.value)

            // Resume timer (e.g. navigating to History)
            vm.resumeInactivityTimer()
            assertEquals(120, vm.inactivitySecondsRemaining.value)
        } finally {
            vm.stopInactivityTimer()
            testScope.cancel()
        }
    }

    @Test
    fun testUnrelatedSettingsChangeDoesNotResetTimer() = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var currentTime = 100_000L
        val repo = FakeSettingsRepository(SystemSettings(inactivityTimeoutMinutes = 2, globalMarkupPercent = 10.0))

        val vm = UserSessionViewModel(
            user = testUser,
            settingsRepository = repo,
            onLogoutRequest = {},
            clock = { currentTime },
            coroutineScope = testScope
        )

        try {
            // Advance time by 40s (remaining = 80s)
            currentTime += 40_000L
            delay(350L)
            val remainingBefore = vm.inactivitySecondsRemaining.value
            assertTrue(remainingBefore in 79..81)

            // Update unrelated settings (e.g. background OneDrive token refresh or exchange rate)
            repo.updateSettings(repo.getSettings().copy(globalMarkupPercent = 20.0, usdExchangeRate = 0.20))
            delay(350L)

            // Timer should NOT have been reset back to 120s!
            val remainingAfter = vm.inactivitySecondsRemaining.value
            assertTrue(remainingAfter <= remainingBefore)
        } finally {
            vm.stopInactivityTimer()
            testScope.cancel()
        }
    }

    @Test
    fun testMinimumTimeoutClampedToTwoMinutes() = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var currentTime = 100_000L
        // Even if DB has 1 minute from legacy configuration, ViewModel clamps to 2 minutes
        val repo = FakeSettingsRepository(SystemSettings(inactivityTimeoutMinutes = 1))

        val vm = UserSessionViewModel(
            user = testUser,
            settingsRepository = repo,
            onLogoutRequest = {},
            clock = { currentTime },
            coroutineScope = testScope
        )

        try {
            delay(100L)
            assertEquals(120, vm.inactivitySecondsRemaining.value)
        } finally {
            vm.stopInactivityTimer()
            testScope.cancel()
        }
    }

    @Test
    fun testSettingsChangeWhilePausedDoesNotTriggerLogout() = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var currentTime = 100_000L
        val repo = FakeSettingsRepository(SystemSettings(inactivityTimeoutMinutes = 2))
        var logoutCalled = false

        val vm = UserSessionViewModel(
            user = testUser,
            settingsRepository = repo,
            onLogoutRequest = { logoutCalled = true },
            clock = { currentTime },
            coroutineScope = testScope,
            initialSettings = SystemSettings(inactivityTimeoutMinutes = 2)
        )

        try {
            // Pause timer (e.g. PurchaseOverviewDialog opened)
            vm.pauseInactivityTimer()

            // Advance time past the 2-minute threshold
            currentTime += 130_000L
            delay(100L)

            // Settings change arrives while paused
            repo.updateSettings(SystemSettings(inactivityTimeoutMinutes = 3))
            delay(350L)

            // Logout should NOT have been called while paused!
            assertFalse(logoutCalled)
            assertFalse(vm.showInactivityWarning.value)
        } finally {
            vm.stopInactivityTimer()
            testScope.cancel()
        }
    }

    @Test
    fun testSystemClockRollbackDoesNotFreezeTimerOrExceedTimeout() = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var currentTime = 100_000L
        val repo = FakeSettingsRepository(SystemSettings(inactivityTimeoutMinutes = 2))

        val vm = UserSessionViewModel(
            user = testUser,
            settingsRepository = repo,
            onLogoutRequest = {},
            clock = { currentTime },
            coroutineScope = testScope,
            initialSettings = SystemSettings(inactivityTimeoutMinutes = 2)
        )

        try {
            // Clock rolls back backwards by 1 hour (NTP sync or timezone change)
            currentTime -= 3600_000L
            delay(350L)

            // Remaining seconds should be clamped to timeout (120s), not thousands of seconds!
            assertTrue(vm.inactivitySecondsRemaining.value <= 120)
            assertEquals(120, vm.inactivitySecondsRemaining.value)
        } finally {
            vm.stopInactivityTimer()
            testScope.cancel()
        }
    }

    @Test
    fun testConcurrentLogoutCallsTriggerOnLogoutRequestOnce() = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var currentTime = 100_000L
        val repo = FakeSettingsRepository(SystemSettings(inactivityTimeoutMinutes = 2))
        var logoutCount = 0

        val vm = UserSessionViewModel(
            user = testUser,
            settingsRepository = repo,
            onLogoutRequest = { logoutCount++ },
            clock = { currentTime },
            coroutineScope = testScope,
            initialSettings = SystemSettings(inactivityTimeoutMinutes = 2)
        )

        try {
            // Fire multiple logout requests concurrently
            vm.requestLogout()
            vm.requestLogout()
            vm.requestLogout()

            assertEquals(1, logoutCount)
        } finally {
            vm.stopInactivityTimer()
            testScope.cancel()
        }
    }

    @Test
    fun testInitialSettingsRespectedImmediately() {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var currentTime = 100_000L
        val repo = FakeSettingsRepository(SystemSettings(inactivityTimeoutMinutes = 5))

        val vm = UserSessionViewModel(
            user = testUser,
            settingsRepository = repo,
            onLogoutRequest = {},
            clock = { currentTime },
            coroutineScope = testScope,
            initialSettings = SystemSettings(inactivityTimeoutMinutes = 5)
        )

        try {
            // Synchronously initialized to 5 minutes (300s) without waiting for coroutine collection
            assertEquals(300, vm.inactivitySecondsRemaining.value)
        } finally {
            vm.stopInactivityTimer()
            testScope.cancel()
        }
    }

    @Test
    fun testSearchTypingForceInteractionsDismissWarning() = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var currentTime = 100_000L
        val repo = FakeSettingsRepository(SystemSettings(inactivityTimeoutMinutes = 2))

        val vm = UserSessionViewModel(
            user = testUser,
            settingsRepository = repo,
            onLogoutRequest = {},
            clock = { currentTime },
            coroutineScope = testScope,
            initialSettings = SystemSettings(inactivityTimeoutMinutes = 2)
        )

        try {
            // Advance time into the warning zone (last 60s)
            currentTime += 70_000L
            delay(350L)
            assertTrue(vm.showInactivityWarning.value)

            // User typing into search field passes force = true
            vm.onUserInteracted(force = true)
            assertFalse(vm.showInactivityWarning.value)
            assertEquals(120, vm.inactivitySecondsRemaining.value)
        } finally {
            vm.stopInactivityTimer()
            testScope.cancel()
        }
    }

    @Test
    fun testPauseInactivityTimerDismissesWarningDialog() = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var currentTime = 100_000L
        val repo = FakeSettingsRepository(SystemSettings(inactivityTimeoutMinutes = 2))

        val vm = UserSessionViewModel(
            user = testUser,
            settingsRepository = repo,
            onLogoutRequest = {},
            clock = { currentTime },
            coroutineScope = testScope,
            initialSettings = SystemSettings(inactivityTimeoutMinutes = 2)
        )

        try {
            // Advance into warning zone
            currentTime += 70_000L
            delay(350L)
            assertTrue(vm.showInactivityWarning.value)

            // Pause timer (e.g. post-purchase dialog opens)
            vm.pauseInactivityTimer()
            // Warning must be immediately hidden while paused
            assertFalse(vm.showInactivityWarning.value)
        } finally {
            vm.stopInactivityTimer()
            testScope.cancel()
        }
    }

    @Test
    fun testResetInactivityTimerRestartsCancelledTicker() = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var currentTime = 100_000L
        val repo = FakeSettingsRepository(SystemSettings(inactivityTimeoutMinutes = 2))
        var logoutTriggered = false

        val vm = UserSessionViewModel(
            user = testUser,
            settingsRepository = repo,
            onLogoutRequest = { logoutTriggered = true },
            clock = { currentTime },
            coroutineScope = testScope,
            initialSettings = SystemSettings(inactivityTimeoutMinutes = 2)
        )

        try {
            // Explicitly stop timer (simulating previous logout completion or teardown)
            vm.stopInactivityTimer()

            // Resetting timer must resurrect the ticker
            vm.resetInactivityTimer()
            assertFalse(logoutTriggered)

            // Advance clock past the 2-minute threshold
            currentTime += 130_000L
            delay(350L)

            // Resurrected ticker should trigger logout
            assertTrue(logoutTriggered)
        } finally {
            vm.stopInactivityTimer()
            testScope.cancel()
        }
    }

    @Test
    fun testRapidSettingsUpdatesDoNotDestabilizeTimer() = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var currentTime = 100_000L
        val repo = FakeSettingsRepository(SystemSettings(inactivityTimeoutMinutes = 2))

        val vm = UserSessionViewModel(
            user = testUser,
            settingsRepository = repo,
            onLogoutRequest = {},
            clock = { currentTime },
            coroutineScope = testScope,
            initialSettings = SystemSettings(inactivityTimeoutMinutes = 2)
        )

        try {
            // Rapid settings updates with unrelated fields
            repeat(10) { i ->
                repo.updateSettings(
                    SystemSettings(
                        inactivityTimeoutMinutes = 2,
                        globalMarkupPercent = i * 1.5,
                        oneDriveRefreshToken = "token_$i"
                    )
                )
            }
            delay(350L)

            // Timer should remain steady at 120s
            assertEquals(120, vm.inactivitySecondsRemaining.value)
            assertFalse(vm.showInactivityWarning.value)
        } finally {
            vm.stopInactivityTimer()
            testScope.cancel()
        }
    }

    @Test
    fun testRapidMovementsDoNotSwallowSubsequentClick() {
        var lastInteractionTime = 0L
        var interactionsCount = 0
        var forceCount = 0

        fun onPointerEvent(isClickOrScroll: Boolean, isRealMovement: Boolean, now: Long) {
            if (isClickOrScroll) {
                lastInteractionTime = now
                interactionsCount++
                forceCount++
            } else if (isRealMovement) {
                if (now - lastInteractionTime >= 500L) {
                    lastInteractionTime = now
                    interactionsCount++
                }
            }
        }

        // Time = 1000ms: continuous mouse movement
        onPointerEvent(isClickOrScroll = false, isRealMovement = true, now = 1000L)
        assertEquals(1, interactionsCount)
        assertEquals(0, forceCount)

        // Time = 1100ms (100ms later): another mouse movement throttled out (< 500ms)
        onPointerEvent(isClickOrScroll = false, isRealMovement = true, now = 1100L)
        assertEquals(1, interactionsCount)

        // Time = 1200ms (200ms after first move): user clicks
        onPointerEvent(isClickOrScroll = true, isRealMovement = false, now = 1200L)
        // Click MUST be processed immediately despite < 500ms from preceding mouse movement!
        assertEquals(2, interactionsCount)
        assertEquals(1, forceCount)
        assertEquals(1200L, lastInteractionTime)
    }
}
