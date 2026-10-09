package de.joelneumann.lojinha.data.service

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.round
import kotlin.test.*

class CurrencyRateServiceTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
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

    @Test
    fun testFrankfurterResponseParsingAndRounding() {
        val payload = """
            {
                "amount": 1.0,
                "base": "BRL",
                "date": "2026-10-08",
                "rates": {
                    "EUR": 0.1782,
                    "USD": 0.19933
                }
            }
        """.trimIndent()

        val parsed = json.decodeFromString<FrankfurterResponse>(payload)
        assertEquals("BRL", parsed.base)
        assertEquals(0.1782, parsed.rates["EUR"])
        assertEquals(0.19933, parsed.rates["USD"])

        val roundedUsd = round((parsed.rates["USD"] ?: 0.0) * 10000.0) / 10000.0
        val roundedEur = round((parsed.rates["EUR"] ?: 0.0) * 10000.0) / 10000.0

        assertEquals(0.1993, roundedUsd)
        assertEquals(0.1782, roundedEur)

        val rates = CurrencyRates(
            usdRate = roundedUsd,
            eurRate = roundedEur,
            provider = "Frankfurter API"
        )
        assertEquals(0.1993, rates.usdRate)
        assertEquals(0.1782, rates.eurRate)
        assertEquals("Frankfurter API", rates.provider)
        assertTrue(rates.timestamp > 0)
    }

    @Test
    fun testErApiResponseParsingAndRounding() {
        val payload = """
            {
                "result": "success",
                "provider": "https://www.exchangerate-api.com",
                "base_code": "BRL",
                "rates": {
                    "BRL": 1.0,
                    "EUR": 0.178585,
                    "USD": 0.199892
                }
            }
        """.trimIndent()

        val parsed = json.decodeFromString<ErApiResponse>(payload)
        assertEquals("success", parsed.result)
        assertEquals(0.178585, parsed.rates["EUR"])
        assertEquals(0.199892, parsed.rates["USD"])

        val roundedUsd = round((parsed.rates["USD"] ?: 0.0) * 10000.0) / 10000.0
        val roundedEur = round((parsed.rates["EUR"] ?: 0.0) * 10000.0) / 10000.0

        assertEquals(0.1999, roundedUsd)
        assertEquals(0.1786, roundedEur)
    }

    @Test
    fun testRateSanityValidation() {
        fun validate(usd: Double, eur: Double) {
            if (!usd.isFinite() || usd < 0.0001 || usd > 1000.0) {
                throw IllegalArgumentException("Invalid USD rate fetched: $usd")
            }
            if (!eur.isFinite() || eur < 0.0001 || eur > 1000.0) {
                throw IllegalArgumentException("Invalid EUR rate fetched: $eur")
            }
        }

        // Valid rates
        validate(0.18, 0.16)
        validate(0.1993, 0.1782)

        // Invalid rates
        assertFailsWith<IllegalArgumentException> { validate(-0.18, 0.16) }
        assertFailsWith<IllegalArgumentException> { validate(0.0, 0.16) }
        assertFailsWith<IllegalArgumentException> { validate(0.18, -0.05) }
        assertFailsWith<IllegalArgumentException> { validate(Double.NaN, 0.16) }
        assertFailsWith<IllegalArgumentException> { validate(0.18, Double.POSITIVE_INFINITY) }
        assertFailsWith<IllegalArgumentException> { validate(1500.0, 0.16) }
    }
}
