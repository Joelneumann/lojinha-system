package de.joelneumann.lojinha.data.service

import kotlinx.serialization.Serializable

@Serializable
data class CurrencyRates(
    val usdRate: Double,
    val eurRate: Double,
    val timestamp: Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
    val provider: String = "Frankfurter API"
)

interface CurrencyRateService {
    suspend fun fetchRates(): Result<CurrencyRates>
}
