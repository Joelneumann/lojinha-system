package de.joelneumann.lojinha.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class SystemSettings(
    val adminPasswordHash: String = "admin",
    val globalMarkupPercent: Double = 0.0,
    val usdExchangeRate: Double = 0.18,
    val eurExchangeRate: Double = 0.16,
    val inactivityTimeoutMinutes: Int = 3
)
