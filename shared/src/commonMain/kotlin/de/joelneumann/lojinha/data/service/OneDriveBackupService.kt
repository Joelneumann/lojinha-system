package de.joelneumann.lojinha.data.service

import de.joelneumann.lojinha.domain.model.TokenResponse
import de.joelneumann.lojinha.domain.model.UserProfileResponse

expect class OneDriveBackupService() {
    suspend fun startPkceAuth(
        clientId: String,
        onStatusUpdate: ((String) -> Unit)? = null
    ): Result<TokenResponse>

    suspend fun refreshAccessToken(clientId: String, refreshToken: String): Result<TokenResponse>
    suspend fun fetchUserProfile(accessToken: String): Result<UserProfileResponse>
}
