package de.joelneumann.lojinha.domain.repository

import de.joelneumann.lojinha.domain.model.BillingList
import de.joelneumann.lojinha.domain.model.BillingListUser
import kotlinx.coroutines.flow.Flow

interface BillingListRepository {
    fun getActiveBillingListsFlow(): Flow<List<BillingList>>
    suspend fun saveBillingList(list: BillingList)
    suspend fun deleteBillingList(id: String)
    suspend fun addUserToList(user: BillingListUser)
    suspend fun removeUserFromList(listId: String, userId: String)
    suspend fun updateUserQuantity(listId: String, userId: String, quantity: Int)
}
