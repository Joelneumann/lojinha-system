package de.joelneumann.lojinha.domain.repository

import de.joelneumann.lojinha.domain.model.User
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    fun getUsersFlow(): Flow<List<User>>
    suspend fun getAllUsers(): List<User>
    suspend fun getUserById(id: String): User?
    suspend fun getUserByBarcode(barcode: String): User?
    suspend fun saveUser(user: User)
    suspend fun deactivateUser(id: String)
    suspend fun softDeleteUser(id: String)
    suspend fun restoreUser(id: String)
    suspend fun canHardDeleteUser(id: String): Boolean
    suspend fun hardDeleteUser(id: String): Boolean
    suspend fun updateBalance(userId: String, amountDelta: Long)
}
