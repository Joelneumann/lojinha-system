package de.joelneumann.lojinha.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AdminNote(
    val id: String = "",
    val message: String,
    val userId: String,
    val userNameSnapshot: String,
    val timestamp: Long = 0L
) {
    val isSystem: Boolean get() = userId == SYSTEM_USER_ID

    companion object {
        const val SYSTEM_USER_ID = "SYSTEM"
        const val SYSTEM_USER_NAME = "System"
    }
}
