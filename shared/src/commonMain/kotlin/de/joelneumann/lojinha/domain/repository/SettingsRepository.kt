package de.joelneumann.lojinha.domain.repository

import de.joelneumann.lojinha.domain.model.SystemSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getSettingsFlow(): Flow<SystemSettings>
    suspend fun getSettings(): SystemSettings
    suspend fun updateSettings(settings: SystemSettings)
}
