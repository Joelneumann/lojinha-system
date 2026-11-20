package de.joelneumann.lojinha.data.repository

import de.joelneumann.lojinha.domain.model.BuyerSaveDraftSettlementRequest
import de.joelneumann.lojinha.domain.model.BuyerVeggieRoundStateResponse
import de.joelneumann.lojinha.domain.model.SessionStatusResponse
import de.joelneumann.lojinha.domain.repository.BuyerVeggieRepository
import de.joelneumann.lojinha.util.AppLogger
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class BuyerNetworkClient(val baseUrl: String = "") {
    private val _onDataChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val onDataChanged: SharedFlow<Unit> = _onDataChanged.asSharedFlow()

    private val _onUnauthorized = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val onUnauthorized: SharedFlow<Unit> = _onUnauthorized.asSharedFlow()

    private var syncJob: Job? = null

    fun notifyUnauthorized() {
        stopRealtimeSync()
        _onUnauthorized.tryEmit(Unit)
    }

    fun resolveUrl(path: String): String {
        val cleanBase = baseUrl.trimEnd('/')
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        return if (cleanBase.isEmpty()) cleanPath else "$cleanBase$cleanPath"
    }

    val httpClient = HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000L
            connectTimeoutMillis = 10_000L
        }
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
        HttpResponseValidator {
            validateResponse { response ->
                if (response.status == HttpStatusCode.Unauthorized) {
                    notifyUnauthorized()
                }
            }
        }
    }

    fun startRealtimeSync(scope: CoroutineScope) {
        try {
            jsStartSse("buyer", "/api/buyer/events", baseUrl)
        } catch (e: Exception) {
            AppLogger.error("BuyerNetworkClient", "Failed to initialize Buyer EventSource: ${e.message}", e)
        }

        syncJob?.cancel()
        syncJob = scope.launch {
            while (isActive) {
                delay(1200)
                try {
                    if (jsCheckAndClearSseDataChanged("buyer")) {
                        _onDataChanged.tryEmit(Unit)
                    }
                    if (jsCheckAndClearSseSyncError("buyer")) {
                        val healthy = KioskConnectionMonitor.checkHealthNow(baseUrl)
                        if (healthy) {
                            val stillValid = try {
                                val resp = httpClient.get(resolveUrl("/api/session"))
                                resp.status.isSuccess() && resp.body<SessionStatusResponse>().role == "BUYER"
                            } catch (e: Throwable) {
                                if (e is CancellationException) throw e
                                true
                            }
                            if (!stillValid) {
                                notifyUnauthorized()
                                break
                            } else {
                                try {
                                    jsStartSse("buyer", "/api/buyer/events", baseUrl)
                                } catch (_: Exception) {}
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
            }
        }
    }

    fun stopRealtimeSync() {
        syncJob?.cancel()
        syncJob = null
        try {
            jsStopSse("buyer")
        } catch (_: Exception) {}
    }
}

class HttpBuyerVeggieRepository(
    private val client: BuyerNetworkClient
) : BuyerVeggieRepository {

    override suspend fun getRoundState(): BuyerVeggieRoundStateResponse {
        return try {
            val response = client.httpClient.get(client.resolveUrl("/api/buyer/veggie/state"))
            if (!response.status.isSuccess()) {
                error("Failed to fetch buyer veggie state: ${response.status}")
            }
            KioskConnectionMonitor.reportServerReachable()
            response.body()
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            if (e !is IllegalStateException) {
                KioskConnectionMonitor.reportNetworkFailure(client.baseUrl)
            }
            throw e
        }
    }

    override suspend fun saveDraftSettlement(request: BuyerSaveDraftSettlementRequest): Boolean {
        return try {
            val response = client.httpClient.post(client.resolveUrl("/api/buyer/veggie/round/draft-settlement")) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            KioskConnectionMonitor.reportServerReachable()
            response.status.isSuccess()
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            KioskConnectionMonitor.reportNetworkFailure(client.baseUrl)
            false
        }
    }

    override suspend fun logout() {
        client.stopRealtimeSync()
        try {
            client.httpClient.post(client.resolveUrl("/api/buyer/logout"))
        } catch (_: Exception) {}
    }

    override fun observeDataChanges(): Flow<Unit> = client.onDataChanged
}
