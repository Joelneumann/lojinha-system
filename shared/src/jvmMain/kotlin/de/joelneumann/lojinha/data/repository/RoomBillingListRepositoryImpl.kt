package de.joelneumann.lojinha.data.repository

import de.joelneumann.lojinha.data.dao.BillingListDao
import de.joelneumann.lojinha.data.entity.BillingListEntity
import de.joelneumann.lojinha.data.entity.BillingListUserEntity
import de.joelneumann.lojinha.domain.model.BillingList
import de.joelneumann.lojinha.domain.model.BillingListType
import de.joelneumann.lojinha.domain.model.BillingListUser
import de.joelneumann.lojinha.domain.repository.BillingListRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class RoomBillingListRepositoryImpl(
    private val dao: BillingListDao,
    private val onDataChanged: () -> Unit
) : BillingListRepository {

    override fun getActiveBillingListsFlow(): Flow<List<BillingList>> {
        return combine(
            dao.getActiveBillingListsFlow(),
            dao.getAllBillingListUsersFlow()
        ) { lists, allUsers ->
            val usersByListId = allUsers.groupBy { it.listId }
            lists.map { listEntity ->
                val userEntities = usersByListId[listEntity.id] ?: emptyList()
                BillingList(
                    id = listEntity.id,
                    name = listEntity.name,
                    type = BillingListType.valueOf(listEntity.type),
                    basePrice = listEntity.basePrice,
                    comment = listEntity.comment,
                    isDeleted = listEntity.isDeleted,
                    lastExecutionTime = listEntity.lastExecutionTime,
                    users = userEntities.map {
                        BillingListUser(
                            id = it.id,
                            listId = it.listId,
                            userId = it.userId,
                            quantity = it.quantity
                        )
                    }
                )
            }
        }
    }

    override suspend fun saveBillingList(list: BillingList) {
        val listEntity = BillingListEntity(
            id = list.id,
            name = list.name,
            type = list.type.name,
            basePrice = list.basePrice,
            comment = list.comment,
            isDeleted = list.isDeleted,
            lastExecutionTime = list.lastExecutionTime
        )
        val userEntities = list.users.map { u ->
            BillingListUserEntity(
                id = if (u.id.isBlank()) de.joelneumann.lojinha.ui.utils.generateUuid() else u.id,
                listId = list.id,
                userId = u.userId,
                quantity = u.quantity
            )
        }
        dao.saveBillingListWithUsers(listEntity, userEntities)
        onDataChanged()
    }

    override suspend fun updateLastExecutionTime(id: String, lastExecutionTime: Long?) {
        dao.updateLastExecutionTime(id, lastExecutionTime)
        onDataChanged()
    }

    override suspend fun deleteBillingList(id: String) {
        dao.softDeleteBillingList(id)
        onDataChanged()
    }

    override suspend fun addUserToList(user: BillingListUser) {
        dao.insertBillingListUser(
            BillingListUserEntity(
                id = user.id,
                listId = user.listId,
                userId = user.userId,
                quantity = user.quantity
            )
        )
        onDataChanged()
    }

    override suspend fun removeUserFromList(listId: String, userId: String) {
        dao.removeUserFromList(listId, userId)
        onDataChanged()
    }
    
    override suspend fun removeAllUsersFromList(listId: String) {
        dao.removeAllUsersFromList(listId)
        onDataChanged()
    }
    
    override suspend fun updateUserQuantity(listId: String, userId: String, quantity: Int) {
        dao.updateUserQuantity(listId, userId, quantity)
        onDataChanged()
    }
}
