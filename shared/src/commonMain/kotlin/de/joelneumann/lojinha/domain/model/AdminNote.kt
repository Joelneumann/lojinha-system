package de.joelneumann.lojinha.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AdminNote(
    val id: String = "",
    val message: String,
    val userId: String,
    val userNameSnapshot: String,
    val timestamp: Long = 0L
)
