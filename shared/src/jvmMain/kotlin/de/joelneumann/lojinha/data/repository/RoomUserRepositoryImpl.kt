package de.joelneumann.lojinha.data.repository

import de.joelneumann.lojinha.data.dao.TransactionDao
import de.joelneumann.lojinha.data.dao.UserDao
import de.joelneumann.lojinha.data.entity.UserEntity
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.domain.repository.UserRepository
import de.joelneumann.lojinha.ui.utils.sortedByAccentInsensitive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomUserRepositoryImpl(
    private val userDao: UserDao,
    private val transactionDao: TransactionDao,
    private val onDataChanged: (() -> Unit)? = null
) : UserRepository {

    override fun getUsersFlow(): Flow<List<User>> {
        return userDao.getUsersFlow().map { entities ->
            entities.map { it.toDomain() }.sortedByAccentInsensitive { it.name }
        }
    }

    override suspend fun getAllUsers(): List<User> {
        return userDao.getAllUsers().map { it.toDomain() }.sortedByAccentInsensitive { it.name }
    }

    override suspend fun getUserById(id: String): User? {
        return userDao.getUserById(id)?.toDomain()
    }

    override suspend fun getUserByBarcode(barcode: String): User? {
        return userDao.getUserByBarcode(barcode)?.toDomain()
    }

    override suspend fun saveUser(user: User) {
        val existing = userDao.getUserById(user.id)
        if (existing != null) {
            val entity = UserEntity.fromDomain(user)
            userDao.updateUserProfile(
                id = entity.id,
                name = entity.name,
                language = entity.language,
                secondaryCurrency = entity.secondaryCurrency,
                pin = entity.pin,
                userBarcode = entity.userBarcode,
                userBarcodeNumber = entity.userBarcodeNumber,
                isActive = entity.isActive,
                isDeleted = entity.isDeleted,
                avatarType = entity.avatarType,
                avatarEmoji = entity.avatarEmoji,
                avatarColor = entity.avatarColor
            )
        } else {
            userDao.insertOrUpdateUser(UserEntity.fromDomain(user))
        }
        onDataChanged?.invoke()
    }

    override suspend fun deactivateUser(id: String) {
        userDao.deactivateUser(id)
        onDataChanged?.invoke()
    }

    override suspend fun softDeleteUser(id: String) {
        userDao.softDeleteUser(id)
        onDataChanged?.invoke()
    }

    override suspend fun restoreUser(id: String) {
        userDao.restoreUser(id)
        onDataChanged?.invoke()
    }

    override suspend fun canHardDeleteUser(id: String): Boolean {
        val txCount = transactionDao.getTransactionCountForUser(id)
        return txCount == 0
    }

    override suspend fun hardDeleteUser(id: String): Boolean {
        val res = if (canHardDeleteUser(id)) {
            userDao.deleteUser(id)
            true
        } else {
            userDao.softDeleteUser(id)
            false
        }
        onDataChanged?.invoke()
        return res
    }

    override suspend fun updateBalance(userId: String, amountDelta: Long) {
        userDao.updateBalance(userId, amountDelta)
        onDataChanged?.invoke()
    }
}
