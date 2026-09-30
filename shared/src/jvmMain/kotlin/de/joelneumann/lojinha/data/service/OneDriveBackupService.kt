package de.joelneumann.lojinha.data.service

import de.joelneumann.lojinha.domain.model.TokenResponse
import de.joelneumann.lojinha.domain.model.UserProfileResponse
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

actual class OneDriveBackupService {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
    }

    actual suspend fun startPkceAuth(
        clientId: String,
        tenant: String,
        onStatusUpdate: ((String) -> Unit)?
    ): Result<TokenResponse> = withContext(Dispatchers.IO) {
        var serverSocket: java.net.ServerSocket? = null
        var listenerJob: Job? = null
        try {
            onStatusUpdate?.invoke("Initializing 1-click browser login...")

            val codeVerifier = generateCodeVerifier()
            val codeChallenge = generateCodeChallenge(codeVerifier)
            val port = 8989
            val redirectUri = "http://localhost:$port/callback"
            val authCodeDeferred = CompletableDeferred<String>()

            // Create local temporary ServerSocket on port 8989 (binds to loopback interfaces)
            serverSocket = try {
                java.net.ServerSocket(port, 10)
            } catch (e: java.net.BindException) {
                return@withContext Result.failure(Exception("Port $port is currently in use. Please close any applications using port $port and try again."))
            }

            val currentServer = serverSocket
            listenerJob = launch {
                while (isActive && !currentServer.isClosed) {
                    val clientSocket = try {
                        currentServer.accept()
                    } catch (_: java.net.SocketException) {
                        break // ServerSocket was closed
                    } catch (_: Exception) {
                        break
                    }

                    launch {
                        handleAuthClientSocket(clientSocket, authCodeDeferred)
                    }
                }
            }

            val safeTenant = tenant.ifBlank { "common" }

            // Construct 1-click authorization URL
            val authUrl = "https://login.microsoftonline.com/$safeTenant/oauth2/v2.0/authorize?" +
                    "client_id=$clientId" +
                    "&response_type=code" +
                    "&redirect_uri=${java.net.URLEncoder.encode(redirectUri, "UTF-8")}" +
                    "&response_mode=query" +
                    "&scope=${java.net.URLEncoder.encode("Files.ReadWrite User.Read offline_access", "UTF-8")}" +
                    "&code_challenge=$codeChallenge" +
                    "&code_challenge_method=S256"

            onStatusUpdate?.invoke("Opening browser for login...")
            try {
                java.awt.Desktop.getDesktop().browse(java.net.URI(authUrl))
            } catch (e: Exception) {
                try { currentServer.close() } catch (_: Exception) {}
                listenerJob.cancel()
                return@withContext Result.failure(Exception("Could not open system browser: ${e.message}"))
            }

            onStatusUpdate?.invoke("Waiting for authorization in browser...")
            val authCode = withTimeoutOrNull(180_000L) {
                try {
                    authCodeDeferred.await()
                } catch (e: Exception) {
                    null
                }
            }

            delay(1000L) // Allow browser to receive HTML response page cleanly before closing socket
            try { currentServer.close() } catch (_: Exception) {}
            listenerJob.cancel()
            serverSocket = null

            if (authCode.isNullOrBlank()) {
                return@withContext Result.failure(Exception("Authorization timed out or was cancelled."))
            }

            onStatusUpdate?.invoke("Exchanging authorization code for tokens...")
            val tokenResp = client.submitForm(
                url = "https://login.microsoftonline.com/$safeTenant/oauth2/v2.0/token",
                formParameters = parameters {
                    append("grant_type", "authorization_code")
                    append("client_id", clientId)
                    append("code", authCode)
                    append("redirect_uri", redirectUri)
                    append("code_verifier", codeVerifier)
                }
            )

            val bodyText = tokenResp.bodyAsText()
            val parsed = json.decodeFromString<TokenResponse>(bodyText)

            if (parsed.accessToken != null && parsed.refreshToken != null) {
                Result.success(parsed)
            } else {
                Result.failure(Exception(parsed.errorDescription ?: parsed.error ?: "Token exchange failed"))
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Result.failure(Exception(t.message ?: "Authentication failed", t))
        } finally {
            try {
                serverSocket?.close()
            } catch (_: Exception) {}
            listenerJob?.cancel()
        }
    }

    private fun handleAuthClientSocket(
        socket: java.net.Socket,
        authCodeDeferred: CompletableDeferred<String>
    ) {
        try {
            socket.use { s ->
                val reader = s.getInputStream().bufferedReader(Charsets.UTF_8)
                val requestLine = reader.readLine() ?: return
                // Drain headers until empty line
                while (true) {
                    val header = reader.readLine()
                    if (header.isNullOrEmpty()) break
                }

                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val fullUri = parts[1]

                val pathAndQuery = fullUri.split("?", limit = 2)
                val path = pathAndQuery[0]
                val query = pathAndQuery.getOrNull(1).orEmpty()

                if (path == "/callback") {
                    val queryParams = query.split("&").filter { it.isNotEmpty() }.associate {
                        val p = it.split("=", limit = 2)
                        val key = p[0]
                        val value = p.getOrNull(1)?.let { v ->
                            try { java.net.URLDecoder.decode(v, "UTF-8") } catch (_: Exception) { v }
                        }.orEmpty()
                        key to value
                    }

                    val code = queryParams["code"]
                    val errorDesc = queryParams["error_description"] ?: queryParams["error"]

                    val htmlResponse = if (!code.isNullOrBlank()) {
                        authCodeDeferred.complete(code)
                        """
                        <!DOCTYPE html>
                        <html>
                        <head><meta charset="UTF-8"><title>Lojinha - Connected</title></head>
                        <body style="font-family: system-ui, -apple-system, sans-serif; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; background-color: #F8FAFC; color: #0F172A;">
                            <div style="text-align: center; background: white; padding: 40px; border-radius: 16px; box-shadow: 0 10px 25px rgba(0,0,0,0.05); max-width: 400px; width: 90%;">
                                <div style="font-size: 48px; margin-bottom: 16px;">🟢</div>
                                <h1 style="color: #059669; font-size: 22px; margin: 0 0 8px 0; font-weight: 700;">Connected to OneDrive!</h1>
                                <p style="color: #64748B; font-size: 14px; margin: 0; line-height: 1.5;">You can now close this browser tab and return to Lojinha.</p>
                            </div>
                        </body>
                        </html>
                        """.trimIndent()
                    } else {
                        val errText = errorDesc ?: "Authorization was cancelled."
                        authCodeDeferred.completeExceptionally(Exception(errText))
                        """
                        <!DOCTYPE html>
                        <html>
                        <head><meta charset="UTF-8"><title>Lojinha - Connection Error</title></head>
                        <body style="font-family: system-ui, -apple-system, sans-serif; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; background-color: #F8FAFC; color: #0F172A;">
                            <div style="text-align: center; background: white; padding: 40px; border-radius: 16px; box-shadow: 0 10px 25px rgba(0,0,0,0.05); max-width: 400px; width: 90%;">
                                <div style="font-size: 48px; margin-bottom: 16px;">🔴</div>
                                <h1 style="color: #DC2626; font-size: 22px; margin: 0 0 8px 0; font-weight: 700;">Connection Failed</h1>
                                <p style="color: #64748B; font-size: 14px; margin: 0; line-height: 1.5;">$errText</p>
                            </div>
                        </body>
                        </html>
                        """.trimIndent()
                    }

                    val responseBytes = htmlResponse.toByteArray(Charsets.UTF_8)
                    val out = s.getOutputStream()
                    val header = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/html; charset=UTF-8\r\n" +
                            "Content-Length: ${responseBytes.size}\r\n" +
                            "Connection: close\r\n\r\n"
                    out.write(header.toByteArray(Charsets.UTF_8))
                    out.write(responseBytes)
                    out.flush()
                } else {
                    val notFound = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                    val out = s.getOutputStream()
                    out.write(notFound.toByteArray(Charsets.UTF_8))
                    out.flush()
                }
            }
        } catch (_: Exception) {}
    }

    actual suspend fun refreshAccessToken(
        clientId: String,
        refreshToken: String,
        tenant: String
    ): Result<TokenResponse> = withContext(Dispatchers.IO) {
        try {
            val safeTenant = tenant.ifBlank { "common" }
            val response = client.submitForm(
                url = "https://login.microsoftonline.com/$safeTenant/oauth2/v2.0/token",
                formParameters = parameters {
                    append("grant_type", "refresh_token")
                    append("client_id", clientId)
                    append("refresh_token", refreshToken)
                }
            )
            val bodyText = response.bodyAsText()
            val parsed = json.decodeFromString<TokenResponse>(bodyText)
            if (parsed.accessToken != null) {
                Result.success(parsed)
            } else {
                Result.failure(Exception(parsed.errorDescription ?: parsed.error ?: "Failed to refresh token"))
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Result.failure(Exception(t.message ?: "Failed to refresh token", t))
        }
    }

    actual suspend fun fetchUserProfile(accessToken: String): Result<UserProfileResponse> = withContext(Dispatchers.IO) {
        try {
            val response = client.get("https://graph.microsoft.com/v1.0/me") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
            }
            if (response.status.isSuccess()) {
                val bodyText = response.bodyAsText()
                val parsed = json.decodeFromString<UserProfileResponse>(bodyText)
                Result.success(parsed)
            } else {
                Result.failure(Exception("HTTP ${response.status.value}: ${response.bodyAsText()}"))
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Result.failure(Exception(t.message ?: "Failed to fetch user profile", t))
        }
    }

    suspend fun uploadFile(
        accessToken: String,
        file: File,
        remoteFolderPath: String = "/LojinhaBackups",
        remoteFileName: String = file.name
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val normalizedFolder = remoteFolderPath.trim().trim('/')
            val pathSegment = if (normalizedFolder.isBlank()) remoteFileName else "$normalizedFolder/$remoteFileName"
            val encodedPath = pathSegment.split("/").joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

            val fileLength = file.length()
            if (fileLength <= 4 * 1024 * 1024) {
                // Direct PUT upload for files <= 4MB
                val url = "https://graph.microsoft.com/v1.0/me/drive/root:/$encodedPath:/content"
                val response = client.put(url) {
                    header(HttpHeaders.Authorization, "Bearer $accessToken")
                    setBody(file.readBytes())
                }
                if (response.status.isSuccess()) {
                    Result.success(response.bodyAsText())
                } else {
                    Result.failure(Exception("Upload HTTP ${response.status.value}: ${response.bodyAsText()}"))
                }
            } else {
                // Chunked Upload Session for files > 4MB
                val createSessionUrl = "https://graph.microsoft.com/v1.0/me/drive/root:/$encodedPath:/createUploadSession"
                val sessionResp = client.post(createSessionUrl) {
                    header(HttpHeaders.Authorization, "Bearer $accessToken")
                }
                if (!sessionResp.status.isSuccess()) {
                    return@withContext Result.failure(Exception("Failed to create upload session: ${sessionResp.bodyAsText()}"))
                }
                val sessionJson = json.parseToJsonElement(sessionResp.bodyAsText()).jsonObject
                val uploadUrl = sessionJson["uploadUrl"]?.jsonPrimitive?.content
                    ?: return@withContext Result.failure(Exception("No uploadUrl returned in upload session"))

                // Stream chunks (3.2 MB per chunk, multiple of 320 KB required by Microsoft Graph)
                val chunkSize = 10 * 320 * 1024
                val buffer = ByteArray(chunkSize)
                var offset = 0L
                var lastRespText = ""

                file.inputStream().use { input ->
                    while (offset < fileLength) {
                        var bytesRead = 0
                        while (bytesRead < chunkSize) {
                            val r = input.read(buffer, bytesRead, chunkSize - bytesRead)
                            if (r <= 0) break
                            bytesRead += r
                        }
                        if (bytesRead <= 0) break

                        val end = offset + bytesRead - 1
                        val chunkData = if (bytesRead == chunkSize) buffer else buffer.copyOf(bytesRead)

                        val chunkResp = client.put(uploadUrl) {
                            header(HttpHeaders.ContentRange, "bytes $offset-$end/$fileLength")
                            header(HttpHeaders.ContentLength, bytesRead.toString())
                            setBody(chunkData)
                        }

                        if (!chunkResp.status.isSuccess() &&
                            chunkResp.status != HttpStatusCode.Accepted &&
                            chunkResp.status != HttpStatusCode.Created
                        ) {
                            return@withContext Result.failure(
                                Exception("Chunk upload HTTP ${chunkResp.status.value}: ${chunkResp.bodyAsText()}")
                            )
                        }
                        lastRespText = chunkResp.bodyAsText()
                        offset += bytesRead
                    }
                }
                Result.success(lastRespText)
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Result.failure(Exception(t.message ?: "Failed to upload file", t))
        }
    }

    private fun generateCodeVerifier(): String {
        val randomBytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(randomBytes)
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val bytes = verifier.toByteArray(Charsets.US_ASCII)
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    actual fun close() {
        client.close()
    }
}
