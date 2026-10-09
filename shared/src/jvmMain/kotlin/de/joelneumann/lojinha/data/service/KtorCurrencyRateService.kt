package de.joelneumann.lojinha.data.service

import de.joelneumann.lojinha.util.AppLogger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.ensureActive
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

        private data class Endpoint(val url: String, val providerName: String)

        private val ENDPOINTS = listOf(
            Endpoint("https://api.frankfurter.dev/v1/latest?base=BRL&symbols=USD,EUR", "Frankfurter API"),
            Endpoint("https://api.frankfurter.app/latest?base=BRL&symbols=USD,EUR", "Frankfurter API (.app)"),
            Endpoint("https://open.er-api.com/v6/latest/BRL", "ExchangeRate-API")
        )

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
    private data class RatesResponse(
        val rates: Map<String, Double> = emptyMap()
    )

    override suspend fun fetchRates(): Result<CurrencyRates> = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        for (endpoint in ENDPOINTS) {
            coroutineContext.ensureActive()
            try {
                return@withContext fetchFromEndpoint(endpoint.url, endpoint.providerName)
            } catch (e: Exception) {
                if (e is CancellationException && e !is TimeoutCancellationException) throw e
                lastError = e
                AppLogger.warn(TAG, "${endpoint.providerName} failed: ${e.message}. Trying next fallback...")
            }
        }
        AppLogger.error(TAG, "All exchange rate API providers failed: ${lastError?.message}", lastError)
        Result.failure(Exception("Could not fetch exchange rates from any provider: ${lastError?.message}", lastError))
    }

    private suspend fun fetchFromEndpoint(url: String, providerName: String): Result<CurrencyRates> {
        val response = withTimeout(8_000L) {
            val httpResponse = client.get(url)
            if (!httpResponse.status.isSuccess()) {
                throw IllegalStateException("HTTP ${httpResponse.status.value} ${httpResponse.status.description}")
            }
            httpResponse.body<RatesResponse>()
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
