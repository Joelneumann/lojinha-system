package de.joelneumann.lojinha.domain.repository

import de.joelneumann.lojinha.domain.model.Language
import de.joelneumann.lojinha.domain.model.SecondaryCurrency
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.domain.model.UserAvatarConfig
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    fun getUsersFlow(): Flow<List<User>>
    suspend fun getAllUsers(): List<User>
    suspend fun getUserById(id: String): User?
    suspend fun getUserByBarcode(barcode: String): User?
    suspend fun saveUser(user: User)
    suspend fun updateCustomerSettings(
        userId: String,
        language: Language,
        secondaryCurrency: SecondaryCurrency,
        avatar: UserAvatarConfig,
        pinHash: String?,
        requirePinOnKiosk: Boolean
    ): User? {
        val existing = getUserById(userId) ?: return null
        val updated = existing.copy(
            language = language,
            secondaryCurrency = secondaryCurrency,
            avatar = avatar,
            pin = pinHash,
            requirePinOnKiosk = requirePinOnKiosk
        )
        saveUser(updated)
        return updated
    }
    suspend fun deactivateUser(id: String)
    suspend fun softDeleteUser(id: String)
    suspend fun restoreUser(id: String)
    suspend fun canHardDeleteUser(id: String): Boolean
    suspend fun hardDeleteUser(id: String): Boolean
    suspend fun updateBalance(userId: String, amountDelta: Long)
}
