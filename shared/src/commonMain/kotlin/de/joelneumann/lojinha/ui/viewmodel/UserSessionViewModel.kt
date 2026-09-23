package de.joelneumann.lojinha.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.joelneumann.lojinha.domain.model.SystemSettings
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.domain.repository.SettingsRepository
import de.joelneumann.lojinha.ui.utils.currentTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.math.ceil

class UserSessionViewModel(
    val user: User,
    private val settingsRepository: SettingsRepository,
    private val onLogoutRequest: () -> Unit,
    private val clock: () -> Long = { currentTimeMillis() },
    coroutineScope: CoroutineScope? = null,
    initialSettings: SystemSettings? = null
) : ViewModel() {

    private val activeScope = coroutineScope ?: viewModelScope

    private val initialTimeout = maxOf(2, initialSettings?.inactivityTimeoutMinutes ?: 3)
    private val _settings = MutableStateFlow(initialSettings ?: SystemSettings())
    val settings: StateFlow<SystemSettings> = _settings.asStateFlow()

    private val _inactivitySecondsRemaining = MutableStateFlow(initialTimeout * 60)
    val inactivitySecondsRemaining: StateFlow<Int> = _inactivitySecondsRemaining.asStateFlow()

    private val _showInactivityWarning = MutableStateFlow(false)
    val showInactivityWarning: StateFlow<Boolean> = _showInactivityWarning.asStateFlow()

    @Volatile
    private var lastActivityTimestampMs: Long = clock()

    @Volatile
    private var isPaused: Boolean = false

    @Volatile
    private var isLoggingOut: Boolean = false

    private var tickerJob: Job? = null
    private var settingsJob: Job? = null

    init {
        startSettingsObserver()
        startTicker()
    }

    private fun startSettingsObserver() {
        settingsJob?.cancel()
        settingsJob = activeScope.launch {
            settingsRepository.getSettingsFlow().collect { updatedSettings ->
                val oldTimeout = _settings.value.inactivityTimeoutMinutes
                _settings.value = updatedSettings
                // Only adjust timer if inactivityTimeoutMinutes actually changed
                if (updatedSettings.inactivityTimeoutMinutes != oldTimeout) {
                    recalculateTimerOnSettingsChange()
                }
            }
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = activeScope.launch {
            while (isActive) {
                delay(250L)
                if (!isPaused) {
                    tick()
                }
            }
        }
    }

    private fun tick() {
        if (isLoggingOut) return
        val now = clock()
        if (now < lastActivityTimestampMs) {
            // Clock stepped backward (NTP, daylight saving, manual change); align to prevent freeze
            lastActivityTimestampMs = now
        }

        val timeoutSecs = maxOf(2, _settings.value.inactivityTimeoutMinutes) * 60
        val timeoutMs = timeoutSecs * 1000L
        val elapsedMs = maxOf(0L, now - lastActivityTimestampMs)
        val remainingMs = maxOf(0L, timeoutMs - elapsedMs)
        val remainingSecs = minOf(timeoutSecs, maxOf(0, ceil(remainingMs / 1000.0).toInt()))

        _inactivitySecondsRemaining.value = remainingSecs

        if (remainingSecs <= 0) {
            _showInactivityWarning.value = false
            requestLogout()
        } else if (remainingSecs <= 60) {
            _showInactivityWarning.value = true
        } else {
            _showInactivityWarning.value = false
        }
    }

    private fun recalculateTimerOnSettingsChange() {
        if (isLoggingOut) return
        val timeoutSecs = maxOf(2, _settings.value.inactivityTimeoutMinutes) * 60
        if (isPaused) {
            // While paused, do not compute elapsed time to prevent spurious premature auto-logouts
            _inactivitySecondsRemaining.value = timeoutSecs
        } else {
            tick()
        }
    }

    fun onUserInteracted(force: Boolean = false) {
        if (isLoggingOut) return
        if (_showInactivityWarning.value && !force) {
            // While the inactivity warning is visible, passive interactions (such as
            // background pointer moves or window exit events) must not dismiss the warning.
            return
        }
        resetInactivityTimer()
    }

    fun stayLoggedIn() {
        if (isLoggingOut) return
        resetInactivityTimer()
    }

    fun resetInactivityTimer() {
        if (isLoggingOut) return
        lastActivityTimestampMs = clock()
        _showInactivityWarning.value = false
        val timeoutSecs = maxOf(2, _settings.value.inactivityTimeoutMinutes) * 60
        _inactivitySecondsRemaining.value = timeoutSecs
        if (tickerJob == null || tickerJob?.isActive == false) {
            startTicker()
        }
        if (settingsJob == null || settingsJob?.isActive == false) {
            startSettingsObserver()
        }
    }

    fun pauseInactivityTimer() {
        if (isLoggingOut) return
        isPaused = true
        _showInactivityWarning.value = false
    }

    fun resumeInactivityTimer() {
        if (isLoggingOut) return
        if (isPaused) {
            lastActivityTimestampMs = clock()
            isPaused = false
            val timeoutSecs = maxOf(2, _settings.value.inactivityTimeoutMinutes) * 60
            _inactivitySecondsRemaining.value = timeoutSecs
            _showInactivityWarning.value = false
            if (tickerJob == null || tickerJob?.isActive == false) {
                startTicker()
            }
        }
    }

    fun requestLogout() {
        if (isLoggingOut) {
            return
        }
        isLoggingOut = true
        stopInactivityTimer()
        onLogoutRequest()
    }

    fun stopInactivityTimer() {
        tickerJob?.cancel()
        tickerJob = null
        settingsJob?.cancel()
        settingsJob = null
        _showInactivityWarning.value = false
    }

    override fun onCleared() {
        super.onCleared()
        stopInactivityTimer()
    }
}
