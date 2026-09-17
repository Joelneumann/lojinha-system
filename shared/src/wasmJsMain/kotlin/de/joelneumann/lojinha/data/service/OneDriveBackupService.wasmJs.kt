package de.joelneumann.lojinha.data.service

import de.joelneumann.lojinha.domain.model.TokenResponse
import de.joelneumann.lojinha.domain.model.UserProfileResponse

actual class OneDriveBackupService {
    actual suspend fun startPkceAuth(
        clientId: String,
        tenant: String,
        onStatusUpdate: ((String) -> Unit)?
    ): Result<TokenResponse> {
        return Result.failure(Exception("OneDrive authentication is managed on Desktop App."))
    }

    actual suspend fun refreshAccessToken(
        clientId: String,
        refreshToken: String,
        tenant: String
    ): Result<TokenResponse> {
        return Result.failure(Exception("OneDrive refresh token is managed on Desktop App."))
    }

    actual suspend fun fetchUserProfile(accessToken: String): Result<UserProfileResponse> {
        return Result.failure(Exception("OneDrive user profile is managed on Desktop App."))
    }
}
