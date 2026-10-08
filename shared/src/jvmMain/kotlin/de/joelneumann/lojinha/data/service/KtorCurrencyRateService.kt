package de.joelneumann.lojinha.data.service

import de.joelneumann.lojinha.util.AppLogger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.round

class KtorCurrencyRateService(
    private val client: HttpClient = createDefaultClient()
) : CurrencyRateService, AutoCloseable {

    companion object {
        private const val TAG = "CurrencyRateService"
        private const val PRIMARY_URL = "https://api.frankfurter.dev/v1/latest?base=BRL&symbols=USD,EUR"
        private const val SECONDARY_URL = "https://api.frankfurter.app/latest?base=BRL&symbols=USD,EUR"
        private const val FALLBACK_URL = "https://open.er-api.com/v6/latest/BRL"

        fun createDefaultClient(): HttpClient {
            val jsonConfig = Json {
                ignoreUnknownKeys = true
                isLenient = true
            }
            return HttpClient(CIO) {
                install(ContentNegotiation) {
                    json(jsonConfig)
                }
            }
        }
    }

    @Serializable
    private data class FrankfurterResponse(
        val amount: Double = 1.0,
        val base: String = "BRL",
        val rates: Map<String, Double> = emptyMap()
    )

    @Serializable
    private data class ErApiResponse(
        val result: String = "",
        val rates: Map<String, Double> = emptyMap()
    )

    override suspend fun fetchRates(): Result<CurrencyRates> = withContext(Dispatchers.IO) {
        // 1. Try Frankfurter Primary (.dev)
        try {
            return@withContext fetchFromFrankfurter(PRIMARY_URL, "Frankfurter API")
        } catch (e: Exception) {
            AppLogger.warn(TAG, "Primary endpoint ($PRIMARY_URL) failed: ${e.message}. Trying backup host...")
        }

        // 2. Try Frankfurter Backup host (.app)
        try {
            return@withContext fetchFromFrankfurter(SECONDARY_URL, "Frankfurter API (.app)")
        } catch (e: Exception) {
            AppLogger.warn(TAG, "Secondary endpoint ($SECONDARY_URL) failed: ${e.message}. Trying ExchangeRate-API fallback...")
        }

        // 3. Try ExchangeRate-API Fallback
        try {
            return@withContext fetchFromErApi(FALLBACK_URL, "ExchangeRate-API")
        } catch (e: Exception) {
            AppLogger.error(TAG, "All exchange rate API providers failed: ${e.message}", e)
            Result.failure(Exception("Could not fetch exchange rates from any provider: ${e.message}", e))
        }
    }

    private suspend fun fetchFromFrankfurter(url: String, providerName: String): Result<CurrencyRates> {
        val response = withTimeout(8_000L) {
            client.get(url).body<FrankfurterResponse>()
        }
        val usd = response.rates["USD"] ?: throw IllegalStateException("USD rate missing in $providerName response")
        val eur = response.rates["EUR"] ?: throw IllegalStateException("EUR rate missing in $providerName response")

        validateRates(usd, eur)
        val roundedUsd = roundTo4Decimals(usd)
        val roundedEur = roundTo4Decimals(eur)

        AppLogger.info(TAG, "Successfully fetched exchange rates from $providerName: 1 BRL = $roundedUsd USD, $roundedEur EUR")
        return Result.success(
            CurrencyRates(
                usdRate = roundedUsd,
                eurRate = roundedEur,
                provider = providerName
            )
        )
    }

    private suspend fun fetchFromErApi(url: String, providerName: String): Result<CurrencyRates> {
        val response = withTimeout(8_000L) {
            client.get(url).body<ErApiResponse>()
        }
        val usd = response.rates["USD"] ?: throw IllegalStateException("USD rate missing in $providerName response")
        val eur = response.rates["EUR"] ?: throw IllegalStateException("EUR rate missing in $providerName response")

        validateRates(usd, eur)
        val roundedUsd = roundTo4Decimals(usd)
        val roundedEur = roundTo4Decimals(eur)

        AppLogger.info(TAG, "Successfully fetched exchange rates from $providerName: 1 BRL = $roundedUsd USD, $roundedEur EUR")
        return Result.success(
            CurrencyRates(
                usdRate = roundedUsd,
                eurRate = roundedEur,
                provider = providerName
            )
        )
    }

    private fun validateRates(usd: Double, eur: Double) {
        if (!usd.isFinite() || usd < 0.0001 || usd > 1000.0) {
            throw IllegalArgumentException("Invalid USD rate fetched: $usd")
        }
        if (!eur.isFinite() || eur < 0.0001 || eur > 1000.0) {
            throw IllegalArgumentException("Invalid EUR rate fetched: $eur")
        }
    }

    private fun roundTo4Decimals(value: Double): Double {
        return round(value * 10000.0) / 10000.0
    }

    override fun close() {
        try {
            client.close()
        } catch (_: Exception) {}
    }
}
