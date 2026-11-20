package de.joelneumann.lojinha.domain.repository

import de.joelneumann.lojinha.domain.model.BuyerSaveDraftSettlementRequest
import de.joelneumann.lojinha.domain.model.BuyerVeggieRoundStateResponse
import kotlinx.coroutines.flow.Flow

interface BuyerVeggieRepository {
    suspend fun getRoundState(): BuyerVeggieRoundStateResponse
    suspend fun saveDraftSettlement(request: BuyerSaveDraftSettlementRequest): Boolean
    suspend fun logout()
    fun observeDataChanges(): Flow<Unit>
}
