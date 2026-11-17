package de.joelneumann.lojinha.data.service

import de.joelneumann.lojinha.domain.model.VeggieRoundStatus
import de.joelneumann.lojinha.domain.repository.SettingsRepository
import de.joelneumann.lojinha.domain.repository.VeggieOrderRepository
import de.joelneumann.lojinha.domain.service.VeggieAutoScheduleCalculator
import de.joelneumann.lojinha.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.*

class VeggieRoundAutoScheduler(
    private val veggieOrderRepository: VeggieOrderRepository,
    private val settingsRepository: SettingsRepository,
    private val externalScope: CoroutineScope
) {
    companion object {
        private const val TAG = "VeggieRoundAutoScheduler"
    }

    private var schedulerJob: Job? = null
    private val evaluationMutex = Mutex()
    private var lastHandledSlotMillis: Long? = null

    fun startScheduler() {
        schedulerJob?.cancel()
        schedulerJob = externalScope.launch(Dispatchers.IO) {
            launch {
                settingsRepository.getSettingsFlow().collect {
                    evaluateAndAutoOpenIfDue()
                }
            }
            while (isActive) {
                delay(60_000L)
                evaluateAndAutoOpenIfDue()
            }
        }
    }

    fun stopScheduler() {
        schedulerJob?.cancel()
        schedulerJob = null
    }

    /**
     * Checks if `veggieAutoOpenEnabled` is active and the current time ([nowMillis]) is at or past
     * this week's scheduled opening slot (`veggieAutoOpenDayOfWeek` + `veggieAutoOpenTime`).
     *
     * Opens a new `VeggieOrderRound` pre-filled with `defaultVeggieBuyerFeeCents` and
     * `defaultVeggieTransportFeeCents` if no active round exists and no round has already been opened
     * at or after this week's scheduled slot.
     */
    suspend fun evaluateAndAutoOpenIfDue(
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): Boolean = evaluationMutex.withLock {
        try {
            val settings = settingsRepository.getSettings()
            if (!settings.veggieAutoOpenEnabled) return@withLock false

            val scheduledSlotMillis = VeggieAutoScheduleCalculator
                .computeThisWeekSlotMillis(settings, nowMillis, timeZone)

            if (nowMillis < scheduledSlotMillis) {
                return@withLock false
            }

            if (lastHandledSlotMillis == scheduledSlotMillis) {
                return@withLock false
            }

            val allRounds = veggieOrderRepository.getAllRounds()
            // Do not auto-open if there is already an OPEN or CLOSED round awaiting settlement
            if (allRounds.any { it.status != VeggieRoundStatus.SETTLED }) {
                return@withLock false
            }

            // Do not auto-open if a round was already opened at or after this week's scheduled slot
            if (allRounds.any { it.openedAt >= scheduledSlotMillis }) {
                lastHandledSlotMillis = scheduledSlotMillis
                return@withLock false
            }

            val opened = veggieOrderRepository.openNewRound(
                buyerOperationFeeCents = settings.defaultVeggieBuyerFeeCents,
                transportFeeCents = settings.defaultVeggieTransportFeeCents,
                openedAt = nowMillis
            )
            lastHandledSlotMillis = scheduledSlotMillis
            val nowLocal = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(timeZone)
            AppLogger.info(TAG, "Automatically opened weekly Veggie Order Round '${opened.id}' at $nowLocal")
            true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.error(TAG, "Failed to evaluate weekly Veggie auto-open schedule: ${e.message}", e)
            false
        }
    }
}
