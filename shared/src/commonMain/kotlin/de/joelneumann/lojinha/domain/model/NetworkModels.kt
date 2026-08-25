package de.joelneumann.lojinha.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val password: String = "")

@Serializable
data class LoginResponse(val success: Boolean, val message: String = "")

@Serializable
data class DeltaRequest(val id: String, val delta: Long)
