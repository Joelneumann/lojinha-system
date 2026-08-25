package de.joelneumann.lojinha.data.repository

import de.joelneumann.lojinha.data.dao.TransactionDao
import de.joelneumann.lojinha.data.dao.UserDao
import de.joelneumann.lojinha.data.entity.UserEntity
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomUserRepositoryImpl(
    private val userDao: UserDao,
    private val transactionDao: TransactionDao
) : UserRepository {

    override fun getUsersFlow(): Flow<List<User>> {
        return userDao.getUsersFlow().map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun getAllUsers(): List<User> {
        return userDao.getAllUsers().map { it.toDomain() }
    }

    override suspend fun getUserById(id: String): User? {
        return userDao.getUserById(id)?.toDomain()
    }

    override suspend fun getUserByBarcode(barcode: String): User? {
        return userDao.getUserByBarcode(barcode)?.toDomain()
    }

    override suspend fun saveUser(user: User) {
        userDao.insertOrUpdateUser(UserEntity.fromDomain(user))
    }

    override suspend fun deactivateUser(id: String) {
        userDao.deactivateUser(id)
    }

    override suspend fun softDeleteUser(id: String) {
        userDao.softDeleteUser(id)
    }

    override suspend fun restoreUser(id: String) {
        userDao.restoreUser(id)
    }

    override suspend fun canHardDeleteUser(id: String): Boolean {
        val txCount = transactionDao.getTransactionCountForUser(id)
        return txCount == 0
    }

    override suspend fun hardDeleteUser(id: String): Boolean {
        return if (canHardDeleteUser(id)) {
            userDao.deleteUser(id)
            true
        } else {
            userDao.softDeleteUser(id)
            false
        }
    }

    override suspend fun updateBalance(userId: String, amountDelta: Long) {
        userDao.updateBalance(userId, amountDelta)
    }
}
