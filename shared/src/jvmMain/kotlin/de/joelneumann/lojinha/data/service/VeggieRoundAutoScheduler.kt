package de.joelneumann.lojinha.data.service

import de.joelneumann.lojinha.domain.model.VeggieRoundStatus
import de.joelneumann.lojinha.domain.repository.SettingsRepository
import de.joelneumann.lojinha.domain.repository.VeggieOrderRepository
import de.joelneumann.lojinha.util.AppLogger
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

    fun startScheduler() {
        schedulerJob?.cancel()
        schedulerJob = externalScope.launch(Dispatchers.IO) {
            evaluateAndAutoOpenIfDue()
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

            val targetDayIso = settings.veggieAutoOpenDayOfWeek.coerceIn(1, 7)
            val (targetHour, targetMinute) = parseTimeOfDay(settings.veggieAutoOpenTime)

            val nowInstant = Instant.fromEpochMilliseconds(nowMillis)
            val nowLocal = nowInstant.toLocalDateTime(timeZone)
            val currentDayIso = nowLocal.date.dayOfWeek.isoDayNumber // 1 (Monday) .. 7 (Sunday)

            // Compute the date of targetDayIso within the current ISO week (Monday..Sunday)
            val mondayOfCurrentWeek = nowLocal.date.minus(DatePeriod(days = currentDayIso - 1))
            val scheduledDateThisWeek = mondayOfCurrentWeek.plus(DatePeriod(days = targetDayIso - 1))
            val scheduledDateTimeThisWeek = LocalDateTime(
                year = scheduledDateThisWeek.year,
                monthNumber = scheduledDateThisWeek.monthNumber,
                dayOfMonth = scheduledDateThisWeek.dayOfMonth,
                hour = targetHour,
                minute = targetMinute,
                second = 0,
                nanosecond = 0
            )
            val scheduledSlotMillis = scheduledDateTimeThisWeek.toInstant(timeZone).toEpochMilliseconds()

            if (nowMillis < scheduledSlotMillis) {
                return@withLock false
            }

            val allRounds = veggieOrderRepository.getAllRounds()
            // Do not auto-open if there is already an OPEN or CLOSED round awaiting settlement
            if (allRounds.any { it.status != VeggieRoundStatus.SETTLED }) {
                return@withLock false
            }

            // Do not auto-open if a round was already opened at or after this week's scheduled slot
            if (allRounds.any { it.openedAt >= scheduledSlotMillis }) {
                return@withLock false
            }

            val opened = veggieOrderRepository.openNewRound(
                buyerOperationFeeCents = settings.defaultVeggieBuyerFeeCents,
                transportFeeCents = settings.defaultVeggieTransportFeeCents,
                openedAt = nowMillis
            )
            AppLogger.info(TAG, "Automatically opened weekly Veggie Order Round '${opened.id}' at $nowLocal")
            true
        } catch (e: Exception) {
            AppLogger.error(TAG, "Failed to evaluate weekly Veggie auto-open schedule: ${e.message}", e)
            false
        }
    }

    private fun parseTimeOfDay(rawTime: String): Pair<Int, Int> =
        de.joelneumann.lojinha.domain.service.VeggieAutoScheduleCalculator.parseTimeOfDay(rawTime)
}
