package de.joelneumann.lojinha.data.repository

import de.joelneumann.lojinha.data.dao.SettingsDao
import de.joelneumann.lojinha.data.entity.SettingsEntity
import de.joelneumann.lojinha.domain.model.SystemSettings
import de.joelneumann.lojinha.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomSettingsRepositoryImpl(
    private val settingsDao: SettingsDao
) : SettingsRepository {

    override fun getSettingsFlow(): Flow<SystemSettings> {
        return settingsDao.getSettingsFlow().map { entity ->
            entity?.toDomain() ?: SystemSettings()
        }
    }

    override suspend fun getSettings(): SystemSettings {
        return settingsDao.getSettings()?.toDomain() ?: SystemSettings()
    }

    override suspend fun updateSettings(settings: SystemSettings) {
        settingsDao.insertOrUpdateSettings(SettingsEntity.fromDomain(settings))
    }
}
