package de.joelneumann.lojinha.domain.scale

import kotlinx.coroutines.flow.StateFlow

interface ScaleService {
    val state: StateFlow<ScaleUiState>

    /**
     * Reference-counted polling token. Serial polling only runs while enabled and
     * at least one UI consumer holds an active token.
     */
    fun acquirePollingToken(owner: String)

    fun releasePollingToken(owner: String)

    fun refreshPorts()

    fun updateConfig(config: ScaleConfig)

    fun close()
}
