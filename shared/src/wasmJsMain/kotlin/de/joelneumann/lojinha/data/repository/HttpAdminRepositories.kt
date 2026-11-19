package de.joelneumann.lojinha.data.repository

import de.joelneumann.lojinha.domain.model.*
import de.joelneumann.lojinha.domain.repository.*
import de.joelneumann.lojinha.domain.service.VeggieRoundSettlementResult
import de.joelneumann.lojinha.ui.utils.sortedByAccentInsensitive
import de.joelneumann.lojinha.util.AppLogger
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@JsFun("""
(key, path, baseUrl) => {
    window._lojinhaSse = window._lojinhaSse || {};
    const prev = window._lojinhaSse[key];
    if (prev) {
        if (prev.es) prev.es.close();
        if (prev.visHandler) document.removeEventListener('visibilitychange', prev.visHandler);
    }
    const cleanBase = baseUrl ? baseUrl.replace(/\/+$/, '') : '';
    const url = cleanBase + path;
    const state = { es: null, syncError: false, hasNewData: false, visHandler: null };
    window._lojinhaSse[key] = state;

    const connect = () => {
        if (document.hidden || state.es) return;
        try {
            state.syncError = false;
            const es = new EventSource(url, { withCredentials: true });
            es.onopen = () => { state.syncError = false; };
            es.addEventListener('data_changed', () => {
                state.syncError = false;
                state.hasNewData = true;
            });
            es.onerror = () => { state.syncError = true; };
            state.es = es;
        } catch (err) {
            console.warn('Failed to start EventSource:', err);
        }
    };

    state.visHandler = () => {
        if (document.hidden) {
            if (state.es) {
                state.es.close();
                state.es = null;
            }
        } else {
            state.hasNewData = true;
            connect();
        }
    };
    document.addEventListener('visibilitychange', state.visHandler);
    connect();
}
""")
internal external fun jsStartSse(key: String, path: String, baseUrl: String)

@JsFun("""
(key) => {
    const state = window._lojinhaSse && window._lojinhaSse[key];
    if (state) {
        if (state.es) { state.es.close(); state.es = null; }
        if (state.visHandler) { document.removeEventListener('visibilitychange', state.visHandler); state.visHandler = null; }
        state.syncError = false;
    }
}
""")
internal external fun jsStopSse(key: String)

@JsFun("(key) => { const s = window._lojinhaSse && window._lojinhaSse[key]; if (!s) return false; const f = s.hasNewData === true; s.hasNewData = false; return f; }")
internal external fun jsCheckAndClearSseDataChanged(key: String): Boolean

@JsFun("(key) => { const s = window._lojinhaSse && window._lojinhaSse[key]; if (!s) return false; const e = s.syncError === true; s.syncError = false; return e; }")
internal external fun jsCheckAndClearSseSyncError(key: String): Boolean

object KioskConnectionMonitor {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _isServerReachable = MutableStateFlow(true)
    val isServerReachable: StateFlow<Boolean> = _isServerReachable.asStateFlow()

    private val _onReconnected = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val onReconnected: SharedFlow<Unit> = _onReconnected.asSharedFlow()

    private var pollJob: Job? = null
    private val healthClient = HttpClient()

    fun reportNetworkFailure(baseUrl: String = "") {
        if (_isServerReachable.value) {
            _isServerReachable.value = false
        }
        startPolling(baseUrl)
    }

    fun reportServerReachable() {
        pollJob?.cancel()
        pollJob = null
        if (!_isServerReachable.value) {
            _isServerReachable.value = true
            _onReconnected.tryEmit(Unit)
        }
    }

    suspend fun checkHealthNow(baseUrl: String = ""): Boolean {
        val cleanBase = baseUrl.trimEnd('/')
        val url = if (cleanBase.isEmpty()) "/api/health" else "$cleanBase/api/health"
        return try {
            val response = healthClient.get(url)
            if (response.status.isSuccess()) {
                reportServerReachable()
                true
            } else {
                reportNetworkFailure(baseUrl)
                false
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            reportNetworkFailure(baseUrl)
            false
        }
    }

    private fun startPolling(baseUrl: String) {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive && !_isServerReachable.value) {
                delay(2500)
                val cleanBase = baseUrl.trimEnd('/')
                val url = if (cleanBase.isEmpty()) "/api/health" else "$cleanBase/api/health"
                try {
                    val response = healthClient.get(url)
                    if (response.status.isSuccess()) {
                        _isServerReachable.value = true
                        _onReconnected.tryEmit(Unit)
                        break
                    }
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                }
            }
            pollJob = null
        }
    }
}

class AdminNetworkClient(val baseUrl: String = "") {
    var isAuthenticated: Boolean = false
        private set

    fun resolveUrl(path: String): String {
        val cleanBase = baseUrl.trimEnd('/')
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        return if (cleanBase.isEmpty()) cleanPath else "$cleanBase$cleanPath"
    }

    private val _onDataChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val onDataChanged: SharedFlow<Unit> = _onDataChanged.asSharedFlow()

    private val _onUnauthorized = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val onUnauthorized: SharedFlow<Unit> = _onUnauthorized.asSharedFlow()

    fun notifyUnauthorized() {
        val wasAuthenticated = isAuthenticated
        stopRealtimeSync()
        isAuthenticated = false
        if (wasAuthenticated) {
            _onUnauthorized.tryEmit(Unit)
        }
    }

    private var syncJob: Job? = null

    val httpClient = HttpClient {
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

    suspend fun fetchSessionRole(): String? {
        return try {
            val response = httpClient.get(resolveUrl("/api/session"))
            if (response.status.isSuccess()) {
                val status: SessionStatusResponse = response.body()
                status.role
            } else {
                null
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            null
        }
    }

    suspend fun tryRestoreSession(): Boolean {
        return try {
            val response = httpClient.get(resolveUrl("/api/admin/ui-settings"))
            if (response.status.isSuccess()) {
                isAuthenticated = true
                true
            } else {
                isAuthenticated = false
                false
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            isAuthenticated = false
            false
        }
    }

    suspend fun logout() {
        stopRealtimeSync()
        isAuthenticated = false
        try {
            httpClient.post(resolveUrl("/api/admin/logout"))
        } catch (_: Exception) {}
    }

    fun startRealtimeSync(scope: CoroutineScope) {
        try {
            jsStartSse("admin", "/api/admin/events", baseUrl)
        } catch (e: Exception) {
            AppLogger.error("AdminNetworkClient", "Failed to initialize EventSource: ${e.message}", e)
        }

        syncJob?.cancel()
        syncJob = scope.launch {
            while (isActive) {
                delay(1200)
                if (!isAuthenticated) break
                try {
                    if (jsCheckAndClearSseDataChanged("admin")) {
                        _onDataChanged.tryEmit(Unit)
                    }
                    if (jsCheckAndClearSseSyncError("admin")) {
                        KioskConnectionMonitor.checkHealthNow(baseUrl)
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
            }
        }
    }

    suspend fun verifySessionStillValid() {
        if (!isAuthenticated) return
        try {
            val response = httpClient.get(resolveUrl("/api/admin/products"))
            if (response.status.isSuccess()) {
                KioskConnectionMonitor.reportServerReachable()
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            KioskConnectionMonitor.reportNetworkFailure(baseUrl)
        }
    }

    fun stopRealtimeSync() {
        syncJob?.cancel()
        syncJob = null
        try {
            jsStopSse("admin")
        } catch (e: Exception) {}
    }

    fun HttpRequestBuilder.appendAdminAuth() {}
}

class HttpSettingsRepository(private val client: AdminNetworkClient) : SettingsRepository {
    override fun getSettingsFlow(): Flow<SystemSettings> = flow {
        emit(getSettings())
    }

    override suspend fun getSettings(): SystemSettings {
        return try {
            client.httpClient.get(client.resolveUrl("/api/admin/ui-settings")) {
                client.run { appendAdminAuth() }
            }.body()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.error("HttpSettingsRepository", "getSettings failed: ${e.message}", e)
            KioskConnectionMonitor.reportNetworkFailure(client.baseUrl)
            SystemSettings()
        }
    }

    override suspend fun updateSettings(settings: SystemSettings, notifyDataChanged: Boolean) {
        // Settings are managed on Desktop only
    }
}

class HttpProductRepository(private val client: AdminNetworkClient) : ProductRepository {
    override fun getProductsFlow(): Flow<List<Product>> = flow {
        emit(getAllProducts())
    }

    override suspend fun getAllProducts(): List<Product> {
        return try {
            val list: List<Product> = client.httpClient.get(client.resolveUrl("/api/admin/products")) {
                client.run { appendAdminAuth() }
            }.body()
            list.sortedByAccentInsensitive { it.name }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.error("HttpProductRepository", "getAllProducts failed: ${e.message}", e)
            KioskConnectionMonitor.reportNetworkFailure(client.baseUrl)
            emptyList()
        }
    }

    override suspend fun getProductById(id: String): Product? {
        return getAllProducts().find { it.id == id }
    }

    override suspend fun getProductByBarcode(barcode: String): Product? {
        return getAllProducts().find { p -> p.barcodes.any { it.code == barcode } }
    }

    override suspend fun saveProduct(product: Product) {
        try {
            val response = client.httpClient.post(client.resolveUrl("/api/admin/products")) {
                contentType(ContentType.Application.Json)
                client.run { appendAdminAuth() }
                setBody(product)
            }
            if (!response.status.isSuccess()) {
                throw IllegalStateException("saveProduct failed with status ${response.status}")
            }
        } catch (e: Exception) {
            AppLogger.error("HttpProductRepository", "saveProduct failed: ${e.message}", e)
            throw e
        }
    }

    override suspend fun deactivateProduct(id: String) {
        try {
            val response = client.httpClient.post(client.resolveUrl("/api/admin/products/deactivate/$id")) {
                client.run { appendAdminAuth() }
            }
            if (!response.status.isSuccess()) {
                throw IllegalStateException("deactivateProduct failed with status ${response.status}")
            }
        } catch (e: Exception) {
            AppLogger.error("HttpProductRepository", "deactivateProduct failed: ${e.message}", e)
            throw e
        }
    }

    override suspend fun hardDeleteProduct(id: String) {
        try {
            val response = client.httpClient.delete(client.resolveUrl("/api/admin/products/$id")) {
                client.run { appendAdminAuth() }
            }
            if (!response.status.isSuccess()) {
                throw IllegalStateException("hardDeleteProduct failed with status ${response.status}")
            }
        } catch (e: Exception) {
            AppLogger.error("HttpProductRepository", "hardDeleteProduct failed: ${e.message}", e)
            throw e
        }
    }

    override suspend fun updateStock(productId: String, delta: Long) {
        try {
            val response = client.httpClient.post(client.resolveUrl("/api/admin/products/stock")) {
                contentType(ContentType.Application.Json)
                client.run { appendAdminAuth() }
                setBody(DeltaRequest(productId, delta))
            }
            if (!response.status.isSuccess()) {
                throw IllegalStateException("updateStock failed with status ${response.status}")
            }
        } catch (e: Exception) {
            AppLogger.error("HttpProductRepository", "updateStock failed: ${e.message}", e)
            throw e
        }
    }

    override suspend fun getProductHistory(productId: String, page: Int, pageSize: Int): PagedResult<ProductHistoryEntry> {
        return try {
            val result: PagedResult<ProductHistoryEntry> = client.httpClient.get(client.resolveUrl("/api/admin/products/$productId/history")) {
                client.run { appendAdminAuth() }
                parameter("page", page)
                parameter("pageSize", pageSize)
            }.body()
            result
        } catch (e: Exception) {
            AppLogger.error("HttpProductRepository", "getProductHistory failed: ${e.message}", e)
            throw e
        }
    }

    override suspend fun recordStockAdjustment(adjustment: ProductStockAdjustment) {
        // Stock adjustments on HTTP are recorded by the backend server
    }
}

class HttpUserRepository(private val client: AdminNetworkClient) : UserRepository {
    override fun getUsersFlow(): Flow<List<User>> = flow {
        emit(getAllUsers())
    }

    override suspend fun getAllUsers(): List<User> {
        return try {
            val list: List<User> = client.httpClient.get(client.resolveUrl("/api/admin/users")) {
                client.run { appendAdminAuth() }
            }.body()
            list.sortedByAccentInsensitive { it.name }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.error("HttpProductRepository", "getAllProducts failed: ${e.message}", e)
            KioskConnectionMonitor.reportNetworkFailure(client.baseUrl)
            emptyList()
        }
    }

    override suspend fun getUserById(id: String): User? {
        return getAllUsers().find { it.id == id }
    }

    override suspend fun getUserByBarcode(barcode: String): User? {
        return getAllUsers().find { it.userBarcode == barcode || it.userBarcodeNumber == barcode }
    }

    override suspend fun saveUser(user: User) {
        try {
            val response = client.httpClient.post(client.resolveUrl("/api/admin/users")) {
                contentType(ContentType.Application.Json)
                client.run { appendAdminAuth() }
                setBody(user)
            }
            if (!response.status.isSuccess()) {
                throw IllegalStateException("saveUser failed with status ${response.status}")
            }
        } catch (e: Exception) {
            AppLogger.error("HttpUserRepository", "saveUser failed: ${e.message}", e)
            throw e
        }
    }

    override suspend fun deactivateUser(id: String) {
        try {
            val response = client.httpClient.post(client.resolveUrl("/api/admin/users/deactivate/$id")) {
                client.run { appendAdminAuth() }
            }
            if (!response.status.isSuccess()) {
                throw IllegalStateException("deactivateUser failed with status ${response.status}")
            }
        } catch (e: Exception) {
            AppLogger.error("HttpUserRepository", "deactivateUser failed: ${e.message}", e)
            throw e
        }
    }

    override suspend fun softDeleteUser(id: String) {
        try {
            val response = client.httpClient.post(client.resolveUrl("/api/admin/users/soft-delete/$id")) {
                client.run { appendAdminAuth() }
            }
            if (!response.status.isSuccess()) {
                throw IllegalStateException("softDeleteUser failed with status ${response.status}")
            }
        } catch (e: Exception) {
            AppLogger.error("HttpUserRepository", "softDeleteUser failed: ${e.message}", e)
            throw e
        }
    }

    override suspend fun restoreUser(id: String) {
        try {
            val response = client.httpClient.post(client.resolveUrl("/api/admin/users/restore/$id")) {
                client.run { appendAdminAuth() }
            }
            if (!response.status.isSuccess()) {
                throw IllegalStateException("restoreUser failed with status ${response.status}")
            }
        } catch (e: Exception) {
            AppLogger.error("HttpUserRepository", "restoreUser failed: ${e.message}", e)
            throw e
        }
    }

    override suspend fun canHardDeleteUser(id: String): Boolean {
        return try {
            val res: Map<String, Boolean> = client.httpClient.get(client.resolveUrl("/api/admin/users/can-delete/$id")) {
                client.run { appendAdminAuth() }
            }.body()
            res["canDelete"] ?: false
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun hardDeleteUser(id: String): Boolean {
        return try {
            val res = client.httpClient.delete(client.resolveUrl("/api/admin/users/$id")) {
                client.run { appendAdminAuth() }
            }
            res.status.isSuccess()
        } catch (e: Exception) {
            AppLogger.error("HttpUserRepository", "hardDeleteUser failed: ${e.message}", e)
            false
        }
    }

    override suspend fun updateBalance(userId: String, amountDelta: Long) {
        try {
            val response = client.httpClient.post(client.resolveUrl("/api/admin/users/balance")) {
                contentType(ContentType.Application.Json)
                client.run { appendAdminAuth() }
                setBody(DeltaRequest(userId, amountDelta))
            }
            if (!response.status.isSuccess()) {
                throw IllegalStateException("updateBalance failed with status ${response.status}")
            }
        } catch (e: Exception) {
            AppLogger.error("HttpUserRepository", "updateBalance failed: ${e.message}", e)
            throw e
        }
    }
}

class HttpTransactionRepository(private val client: AdminNetworkClient) : TransactionRepository {
    override fun getTransactionsFlow(): Flow<List<Transaction>> = emptyFlow()

    override suspend fun getAllTransactions(): List<Transaction> {
        return try {
            client.httpClient.get(client.resolveUrl("/api/admin/transactions")) {
                client.run { appendAdminAuth() }
            }.body()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.error("HttpProductRepository", "getAllProducts failed: ${e.message}", e)
            KioskConnectionMonitor.reportNetworkFailure(client.baseUrl)
            emptyList()
        }
    }

    override suspend fun getTransactionsByUserId(userId: String): List<Transaction> {
        return try {
            client.httpClient.get(client.resolveUrl("/api/admin/transactions")) {
                client.run { appendAdminAuth() }
                parameter("userId", userId)
            }.body()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.error("HttpTransactionRepository", "getTransactionsByUserId failed: ${e.message}", e)
            KioskConnectionMonitor.reportNetworkFailure(client.baseUrl)
            emptyList()
        }
    }

    override suspend fun getTransactionById(id: String): Transaction? {
        return getAllTransactions().find { it.id == id }
    }

    override suspend fun recordTransaction(transaction: Transaction) {
        try {
            val response = client.httpClient.post(client.resolveUrl("/api/admin/transactions")) {
                contentType(ContentType.Application.Json)
                client.run { appendAdminAuth() }
                setBody(transaction)
            }
            if (!response.status.isSuccess()) {
                throw IllegalStateException("recordTransaction failed with status ${response.status}")
            }
        } catch (e: Exception) {
            AppLogger.error("HttpTransactionRepository", "recordTransaction failed: ${e.message}", e)
            throw e
        }
    }

    override suspend fun getTransactionCountForUser(userId: String): Int {
        return getTransactionsByUserId(userId).size
    }

    override suspend fun getTransactionsPaged(
        page: Int,
        pageSize: Int,
        searchQuery: String?,
        typeFilter: TransactionType?
    ): PagedResult<Transaction> {
        return try {
            client.httpClient.get(client.resolveUrl("/api/admin/transactions")) {
                client.run { appendAdminAuth() }
                parameter("page", page)
                parameter("pageSize", pageSize)
                if (!searchQuery.isNullOrBlank()) parameter("search", searchQuery)
                if (typeFilter != null) parameter("type", typeFilter.name)
            }.body()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.error("HttpTransactionRepository", "getTransactionsPaged failed: ${e.message}", e)
            KioskConnectionMonitor.reportNetworkFailure(client.baseUrl)
            PagedResult(emptyList(), 0, page, pageSize, 1)
        }
    }

    override suspend fun getTransactionsByUserIdPaged(
        userId: String,
        page: Int,
        pageSize: Int,
        searchQuery: String?,
        typeFilter: TransactionType?
    ): PagedResult<Transaction> {
        return try {
            client.httpClient.get(client.resolveUrl("/api/admin/transactions")) {
                client.run { appendAdminAuth() }
                parameter("userId", userId)
                parameter("page", page)
                parameter("pageSize", pageSize)
                if (!searchQuery.isNullOrBlank()) parameter("search", searchQuery)
                if (typeFilter != null) parameter("type", typeFilter.name)
            }.body()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.error("HttpTransactionRepository", "getTransactionsByUserIdPaged failed: ${e.message}", e)
            KioskConnectionMonitor.reportNetworkFailure(client.baseUrl)
            PagedResult(emptyList(), 0, page, pageSize, 1)
        }
    }

    override suspend fun getTransactionsByReferenceIds(referenceIds: List<String>): List<Transaction> {
        if (referenceIds.isEmpty()) return emptyList()
        return try {
            client.httpClient.post(client.resolveUrl("/api/admin/transactions/by-reference-ids")) {
                contentType(ContentType.Application.Json)
                client.run { appendAdminAuth() }
                setBody(referenceIds)
            }.body()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.error("HttpProductRepository", "getAllProducts failed: ${e.message}", e)
            KioskConnectionMonitor.reportNetworkFailure(client.baseUrl)
            emptyList()
        }
    }

    override suspend fun getTransactionsByIds(ids: List<String>): List<Transaction> {
        if (ids.isEmpty()) return emptyList()
        return try {
            client.httpClient.post(client.resolveUrl("/api/admin/transactions/by-ids")) {
                contentType(ContentType.Application.Json)
                client.run { appendAdminAuth() }
                setBody(ids)
            }.body()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.error("HttpProductRepository", "getAllProducts failed: ${e.message}", e)
            KioskConnectionMonitor.reportNetworkFailure(client.baseUrl)
            emptyList()
        }
    }

    override suspend fun getCancellationCountForReference(refId: String): Int {
        return try {
            val res = client.httpClient.get(client.resolveUrl("/api/admin/transactions/cancellation-count/$refId")) {
                client.run { appendAdminAuth() }
            }.body<Map<String, Int>>()
            res["count"] ?: 0
        } catch (e: Exception) {
            0
        }
    }

    override suspend fun executeAtomicTransaction(
        transaction: Transaction,
        balanceDelta: Long,
        stockDeltas: Map<String, Long>
    ) {
        try {
            val response = client.httpClient.post(client.resolveUrl("/api/admin/transactions/atomic")) {
                contentType(ContentType.Application.Json)
                client.run { appendAdminAuth() }
                setBody(AtomicTransactionRequest(transaction, balanceDelta, stockDeltas))
            }
            if (!response.status.isSuccess()) {
                throw IllegalStateException("executeAtomicTransaction failed with status ${response.status}")
            }
        } catch (e: Exception) {
            AppLogger.error("HttpTransactionRepository", "executeAtomicTransaction failed: ${e.message}", e)
            throw e
        }
    }

    override suspend fun applyPurchaseCorrection(
        originalTransactionId: String,
        newItems: List<TransactionItem>
    ): Boolean {
        return try {
            val response = client.httpClient.post(client.resolveUrl("/api/admin/transactions/purchase-correction")) {
                contentType(ContentType.Application.Json)
                client.run { appendAdminAuth() }
                setBody(PurchaseCorrectionRequest(originalTransactionId, newItems))
            }
            response.status == HttpStatusCode.OK
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun stornoNonPurchase(transactionId: String): Boolean {
        return try {
            val response = client.httpClient.post(client.resolveUrl("/api/admin/transactions/storno-non-purchase")) {
                contentType(ContentType.Application.Json)
                client.run { appendAdminAuth() }
                setBody(StornoNonPurchaseRequest(transactionId))
            }
            response.status == HttpStatusCode.OK
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun executeBatchTransactions(
        requests: List<AtomicTransactionRequest>
    ): Boolean {
        if (requests.isEmpty()) return true
        return try {
            val response = client.httpClient.post(client.resolveUrl("/api/admin/transactions/batch")) {
                contentType(ContentType.Application.Json)
                client.run { appendAdminAuth() }
                setBody(requests)
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            AppLogger.error("HttpTransactionRepository", "executeBatchTransactions failed: ${e.message}", e)
            false
        }
    }
}

class HttpBillingListRepository(private val client: AdminNetworkClient) : BillingListRepository {
    override fun getActiveBillingListsFlow(): Flow<List<BillingList>> = flow {
        emit(getAllBillingLists())
    }

    suspend fun getAllBillingLists(): List<BillingList> {
        return try {
            client.httpClient.get(client.resolveUrl("/api/admin/billing-lists")) {
                client.run { appendAdminAuth() }
            }.body()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.error("HttpProductRepository", "getAllProducts failed: ${e.message}", e)
            KioskConnectionMonitor.reportNetworkFailure(client.baseUrl)
            emptyList()
        }
    }

    override suspend fun saveBillingList(list: BillingList) {
        try {
            val response = client.httpClient.post(client.resolveUrl("/api/admin/billing-lists")) {
                contentType(ContentType.Application.Json)
                client.run { appendAdminAuth() }
                setBody(list)
            }
            if (!response.status.isSuccess()) {
                throw IllegalStateException("saveBillingList failed with status ${response.status}")
            }
        } catch (e: Exception) {
            AppLogger.error("HttpBillingListRepository", "saveBillingList failed: ${e.message}", e)
            throw e
        }
    }

    override suspend fun deleteBillingList(id: String) {
        try {
            val response = client.httpClient.delete(client.resolveUrl("/api/admin/billing-lists/$id")) {
                client.run { appendAdminAuth() }
            }
            if (!response.status.isSuccess()) {
                throw IllegalStateException("deleteBillingList failed with status ${response.status}")
            }
        } catch (e: Exception) {
            AppLogger.error("HttpBillingListRepository", "deleteBillingList failed: ${e.message}", e)
            throw e
        }
    }

    override suspend fun updateLastExecutionTime(id: String, lastExecutionTime: Long?) {
        try {
            val response = client.httpClient.patch(client.resolveUrl("/api/admin/billing-lists/$id/last-execution")) {
                contentType(ContentType.Application.Json)
                client.run { appendAdminAuth() }
                setBody(mapOf("lastExecutionTime" to lastExecutionTime))
            }
            if (!response.status.isSuccess()) {
                AppLogger.warn("HttpBillingListRepository", "updateLastExecutionTime status ${response.status}")
            }
        } catch (e: Exception) {
            AppLogger.error("HttpBillingListRepository", "updateLastExecutionTime failed: ${e.message}", e)
        }
    }

    override suspend fun addUserToList(user: BillingListUser) {
        // Managed through saveBillingList
    }

    override suspend fun removeUserFromList(listId: String, userId: String) {
        // Managed through saveBillingList
    }

    override suspend fun removeAllUsersFromList(listId: String) {
        // Managed through saveBillingList
    }

    override suspend fun updateUserQuantity(listId: String, userId: String, quantity: Int) {
        // Managed through saveBillingList
    }
}

class HttpAdminNoteRepository(private val client: AdminNetworkClient) : AdminNoteRepository {
    override fun getNotesFlow(): Flow<List<AdminNote>> = flow {
        emit(getAllNotes())
    }

    override suspend fun getAllNotes(): List<AdminNote> {
        return try {
            client.httpClient.get(client.resolveUrl("/api/admin/notes")) {
                client.run { appendAdminAuth() }
            }.body()
        } catch (e: Exception) {
            AppLogger.error("HttpAdminNoteRepository", "getAllNotes failed: ${e.message}", e)
            emptyList()
        }
    }

    override suspend fun addNote(note: AdminNote) {
        try {
            val response = client.httpClient.post(client.resolveUrl("/api/admin/notes")) {
                contentType(ContentType.Application.Json)
                client.run { appendAdminAuth() }
                setBody(note)
            }
            if (!response.status.isSuccess()) {
                throw IllegalStateException("addNote failed with status ${response.status}")
            }
        } catch (e: Exception) {
            AppLogger.error("HttpAdminNoteRepository", "addNote failed: ${e.message}", e)
            throw e
        }
    }

    override suspend fun deleteNote(id: String) {
        try {
            val response = client.httpClient.delete(client.resolveUrl("/api/admin/notes/$id")) {
                client.run { appendAdminAuth() }
            }
            if (!response.status.isSuccess()) {
                throw IllegalStateException("deleteNote failed with status ${response.status}")
            }
        } catch (e: Exception) {
            AppLogger.error("HttpAdminNoteRepository", "deleteNote failed: ${e.message}", e)
            throw e
        }
    }

    override suspend fun deleteAllNotes() {
        // Not used by admin client directly
    }
}

class HttpShoppingListRepository(private val client: AdminNetworkClient) : ShoppingListRepository {
    override fun getItemsFlow(): Flow<List<ShoppingListItem>> = flow {
        emit(getAllItems())
    }

    private suspend inline fun <T> performCall(crossinline call: suspend () -> T): T {
        return try {
            call()
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            KioskConnectionMonitor.reportNetworkFailure(client.baseUrl)
            throw e
        }
    }

    override suspend fun getAllItems(): List<ShoppingListItem> {
        val response = performCall {
            client.httpClient.get(client.resolveUrl("/api/admin/shopping-list")) {
                client.run { appendAdminAuth() }
            }
        }
        ensureSuccess("getAllItems", response)
        return response.body()
    }

    override suspend fun addOrUpdateItem(item: ShoppingListItem) {
        val response = performCall {
            client.httpClient.post(client.resolveUrl("/api/admin/shopping-list")) {
                contentType(ContentType.Application.Json)
                client.run { appendAdminAuth() }
                setBody(item)
            }
        }
        ensureSuccess("addOrUpdateItem", response)
    }

    override suspend fun updateItem(item: ShoppingListItem) {
        val response = performCall {
            client.httpClient.put(client.resolveUrl("/api/admin/shopping-list/item")) {
                contentType(ContentType.Application.Json)
                client.run { appendAdminAuth() }
                setBody(item)
            }
        }
        ensureSuccess("updateItem", response)
    }

    override suspend fun confirmItem(id: String) {
        val response = performCall {
            client.httpClient.post(client.resolveUrl("/api/admin/shopping-list/$id/confirm")) {
                client.run { appendAdminAuth() }
            }
        }
        ensureSuccess("confirmItem", response)
    }

    override suspend fun unconfirmItem(id: String) {
        val response = performCall {
            client.httpClient.post(client.resolveUrl("/api/admin/shopping-list/$id/unconfirm")) {
                client.run { appendAdminAuth() }
            }
        }
        ensureSuccess("unconfirmItem", response)
    }

    override suspend fun updateItemsOrder(items: List<ShoppingListItem>) {
        val response = performCall {
            client.httpClient.put(client.resolveUrl("/api/admin/shopping-list/reorder")) {
                contentType(ContentType.Application.Json)
                client.run { appendAdminAuth() }
                setBody(items)
            }
        }
        ensureSuccess("updateItemsOrder", response)
    }

    override suspend fun deleteItem(id: String) {
        val response = performCall {
            client.httpClient.delete(client.resolveUrl("/api/admin/shopping-list/$id")) {
                client.run { appendAdminAuth() }
            }
        }
        ensureSuccess("deleteItem", response)
    }

    override suspend fun clearAllItems() {
        val response = performCall {
            client.httpClient.delete(client.resolveUrl("/api/admin/shopping-list")) {
                client.run { appendAdminAuth() }
            }
        }
        ensureSuccess("clearAllItems", response)
    }

    override suspend fun commitExportBaseline() {
        val response = performCall {
            client.httpClient.post(client.resolveUrl("/api/admin/shopping-list/commit-export")) {
                client.run { appendAdminAuth() }
            }
        }
        ensureSuccess("commitExportBaseline", response)
    }

    private suspend fun ensureSuccess(operation: String, response: HttpResponse) {
        if (!response.status.isSuccess()) {
            val detail = try {
                response.bodyAsText().take(200).trim()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                ""
            }
            val suffix = if (detail.isNotEmpty()) ": $detail" else ""
            throw IllegalStateException("$operation failed with status ${response.status}$suffix")
        }
    }
}

class HttpVeggieOrderRepository(private val client: AdminNetworkClient) : VeggieOrderRepository {
    override fun getUnitTypesFlow(): Flow<List<VeggieUnitType>> = flow {
        emit(getAllUnitTypes())
    }

    override suspend fun getAllUnitTypes(): List<VeggieUnitType> {
        return try {
            val list: List<VeggieUnitType> = client.httpClient.get(client.resolveUrl("/api/admin/veggie/unit-types")) {
                client.run { appendAdminAuth() }
            }.body()
            list.sortedByAccentInsensitive { it.name }
        } catch (e: Exception) {
            AppLogger.error("HttpVeggieOrderRepository", "getAllUnitTypes failed: ${e.message}", e)
            emptyList()
        }
    }

    override suspend fun saveUnitType(unitType: VeggieUnitType) {
        try {
            val response = client.httpClient.post(client.resolveUrl("/api/admin/veggie/unit-types")) {
                contentType(ContentType.Application.Json)
                client.run { appendAdminAuth() }
                setBody(unitType)
            }
            if (!response.status.isSuccess()) {
                throw IllegalStateException("saveUnitType failed with status ${response.status}")
            }
        } catch (e: Exception) {
            AppLogger.error("HttpVeggieOrderRepository", "saveUnitType failed: ${e.message}", e)
            throw e
        }
    }

    override fun getVeggieProductsFlow(): Flow<List<VeggieProduct>> = flow {
        emit(getAllVeggieProducts())
    }

    override suspend fun getAllVeggieProducts(): List<VeggieProduct> {
        return try {
            val list: List<VeggieProduct> = client.httpClient.get(client.resolveUrl("/api/admin/veggie/products")) {
                client.run { appendAdminAuth() }
            }.body()
            list.sortedByAccentInsensitive { it.name }
        } catch (e: Exception) {
            AppLogger.error("HttpVeggieOrderRepository", "getAllVeggieProducts failed: ${e.message}", e)
            emptyList()
        }
    }

    override suspend fun getVeggieProductById(id: String): VeggieProduct? {
        return getAllVeggieProducts().find { it.id == id }
    }

    override suspend fun saveVeggieProduct(product: VeggieProduct) {
        try {
            val response = client.httpClient.post(client.resolveUrl("/api/admin/veggie/products")) {
                contentType(ContentType.Application.Json)
                client.run { appendAdminAuth() }
                setBody(product)
            }
            if (!response.status.isSuccess()) {
                throw IllegalStateException("saveVeggieProduct failed with status ${response.status}")
            }
        } catch (e: Exception) {
            AppLogger.error("HttpVeggieOrderRepository", "saveVeggieProduct failed: ${e.message}", e)
            throw e
        }
    }

    override suspend fun deactivateVeggieProduct(id: String) {
        try {
            val response = client.httpClient.post(client.resolveUrl("/api/admin/veggie/products/deactivate/$id")) {
                client.run { appendAdminAuth() }
            }
            if (!response.status.isSuccess()) {
                throw IllegalStateException("deactivateVeggieProduct failed with status ${response.status}")
            }
        } catch (e: Exception) {
            AppLogger.error("HttpVeggieOrderRepository", "deactivateVeggieProduct failed: ${e.message}", e)
            throw e
        }
    }

    override suspend fun cancelProductEntriesInOpenRound(roundId: String, productId: String): Boolean {
        return try {
            val response = client.httpClient.post(client.resolveUrl("/api/admin/veggie/rounds/$roundId/cancel-product/$productId")) {
                client.run { appendAdminAuth() }
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            AppLogger.error("HttpVeggieOrderRepository", "cancelProductEntriesInOpenRound failed: ${e.message}", e)
            false
        }
    }

    override suspend fun hardDeleteVeggieProduct(id: String) {
        try {
            val response = client.httpClient.delete(client.resolveUrl("/api/admin/veggie/products/$id")) {
                client.run { appendAdminAuth() }
            }
            if (!response.status.isSuccess()) {
                throw IllegalStateException("hardDeleteVeggieProduct failed with status ${response.status}")
            }
        } catch (e: Exception) {
            AppLogger.error("HttpVeggieOrderRepository", "hardDeleteVeggieProduct failed: ${e.message}", e)
            throw e
        }
    }

    suspend fun uploadProductImage(productId: String, productName: String?, imageBytes: ByteArray): String? {
        return try {
            val response = client.httpClient.post(client.resolveUrl("/api/admin/veggie/products/$productId/image")) {
                contentType(ContentType.Application.OctetStream)
                client.run { appendAdminAuth() }
                if (!productName.isNullOrBlank()) {
                    parameter("name", productName)
                }
                setBody(imageBytes)
            }
            if (response.status.isSuccess()) {
                val res: Map<String, String> = response.body()
                res["imageFileName"]
            } else {
                throw IllegalStateException("uploadProductImage failed with status ${response.status}")
            }
        } catch (e: Exception) {
            AppLogger.error("HttpVeggieOrderRepository", "uploadProductImage failed: ${e.message}", e)
            throw e
        }
    }

    suspend fun deleteProductImage(productId: String) {
        try {
            val response = client.httpClient.delete(client.resolveUrl("/api/admin/veggie/products/$productId/image")) {
                client.run { appendAdminAuth() }
            }
            if (!response.status.isSuccess()) {
                throw IllegalStateException("deleteProductImage failed with status ${response.status}")
            }
        } catch (e: Exception) {
            AppLogger.error("HttpVeggieOrderRepository", "deleteProductImage failed: ${e.message}", e)
            throw e
        }
    }

    override fun getRoundsFlow(): Flow<List<VeggieOrderRound>> = flow {
        emit(getAllRounds())
    }

    override suspend fun getAllRounds(): List<VeggieOrderRound> {
        return try {
            client.httpClient.get(client.resolveUrl("/api/admin/veggie/rounds")) {
                client.run { appendAdminAuth() }
            }.body()
        } catch (e: Exception) {
            AppLogger.error("HttpVeggieOrderRepository", "getAllRounds failed: ${e.message}", e)
            emptyList()
        }
    }

    override suspend fun getActiveOrLatestRound(): VeggieOrderRound? {
        return try {
            val response = client.httpClient.get(client.resolveUrl("/api/admin/veggie/rounds/active")) {
                client.run { appendAdminAuth() }
            }
            if (response.status == HttpStatusCode.OK) {
                response.body()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getRoundById(roundId: String): VeggieOrderRound? {
        return try {
            val response = client.httpClient.get(client.resolveUrl("/api/admin/veggie/rounds/$roundId")) {
                client.run { appendAdminAuth() }
            }
            if (response.status == HttpStatusCode.OK) {
                response.body()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun openNewRound(
        buyerOperationFeeCents: Long,
        transportFeeCents: Long,
        openedAt: Long
    ): VeggieOrderRound {
        val response = client.httpClient.post(client.resolveUrl("/api/admin/veggie/rounds/open")) {
            contentType(ContentType.Application.Json)
            client.run { appendAdminAuth() }
            setBody(OpenVeggieRoundRequest(buyerOperationFeeCents, transportFeeCents, openedAt))
        }
        if (!response.status.isSuccess()) {
            throw IllegalStateException("openNewRound failed with status ${response.status}")
        }
        return response.body()
    }

    override suspend fun closeRound(roundId: String, closedAt: Long): Boolean {
        return try {
            val response = client.httpClient.post(client.resolveUrl("/api/admin/veggie/rounds/$roundId/close")) {
                contentType(ContentType.Application.Json)
                client.run { appendAdminAuth() }
                setBody(CloseVeggieRoundRequest(closedAt))
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            AppLogger.error("HttpVeggieOrderRepository", "closeRound failed: ${e.message}", e)
            false
        }
    }

    override suspend fun reopenRound(roundId: String): Boolean {
        return try {
            val response = client.httpClient.post(client.resolveUrl("/api/admin/veggie/rounds/$roundId/reopen")) {
                client.run { appendAdminAuth() }
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            AppLogger.error("HttpVeggieOrderRepository", "reopenRound failed: ${e.message}", e)
            false
        }
    }

    override suspend fun deleteRound(roundId: String): Boolean {
        return try {
            val response = client.httpClient.delete(client.resolveUrl("/api/admin/veggie/rounds/$roundId")) {
                client.run { appendAdminAuth() }
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            AppLogger.error("HttpVeggieOrderRepository", "deleteRound failed: ${e.message}", e)
            false
        }
    }

    override suspend fun markBuyerPdfExported(roundId: String, timestamp: Long): Boolean {
        return try {
            val response = client.httpClient.post(client.resolveUrl("/api/admin/veggie/rounds/$roundId/buyer-pdf/exported")) {
                client.run { appendAdminAuth() }
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            AppLogger.error("HttpVeggieOrderRepository", "markBuyerPdfExported failed: ${e.message}", e)
            false
        }
    }

    override suspend fun markWorkerPdfExported(roundId: String, timestamp: Long): Boolean {
        return try {
            val response = client.httpClient.post(client.resolveUrl("/api/admin/veggie/rounds/$roundId/worker-pdf/exported")) {
                client.run { appendAdminAuth() }
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            AppLogger.error("HttpVeggieOrderRepository", "markWorkerPdfExported failed: ${e.message}", e)
            false
        }
    }

    override suspend fun getEntriesForRound(roundId: String): List<VeggieOrderEntry> {
        return try {
            client.httpClient.get(client.resolveUrl("/api/admin/veggie/rounds/$roundId/entries")) {
                client.run { appendAdminAuth() }
            }.body()
        } catch (e: Exception) {
            AppLogger.error("HttpVeggieOrderRepository", "getEntriesForRound failed: ${e.message}", e)
            emptyList()
        }
    }

    override suspend fun getEntriesForUserInRound(roundId: String, userId: String): List<VeggieOrderEntry> {
        return getEntriesForRound(roundId).filter { it.userId == userId }
    }

    override suspend fun getPreviousRoundQuantitiesForUser(currentRoundId: String, userId: String): Map<String, Long> {
        return emptyMap()
    }

    override suspend fun saveCustomerOrder(
        roundId: String,
        user: User,
        productQuantitiesScaled: Map<String, Long>
    ): Boolean {
        return false
    }

    override suspend fun getCustomerOrderHistory(userId: String): List<CustomerVeggieRoundHistoryItem> {
        return emptyList()
    }

    override suspend fun upsertAdminOrderEntry(entry: VeggieOrderEntry): Boolean {
        return try {
            val response = client.httpClient.post(client.resolveUrl("/api/admin/veggie/rounds/${entry.roundId}/entries")) {
                contentType(ContentType.Application.Json)
                client.run { appendAdminAuth() }
                setBody(entry)
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            AppLogger.error("HttpVeggieOrderRepository", "upsertAdminOrderEntry failed: ${e.message}", e)
            false
        }
    }

    override suspend fun deleteAdminOrderEntry(roundId: String, entryId: String): Boolean {
        return try {
            val response = client.httpClient.delete(client.resolveUrl("/api/admin/veggie/rounds/$roundId/entries/$entryId")) {
                client.run { appendAdminAuth() }
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            AppLogger.error("HttpVeggieOrderRepository", "deleteAdminOrderEntry failed: ${e.message}", e)
            false
        }
    }

    override suspend fun getProductSettlementsForRound(roundId: String): List<VeggieRoundProductSettlement> {
        return try {
            client.httpClient.get(client.resolveUrl("/api/admin/veggie/rounds/$roundId/settlements")) {
                client.run { appendAdminAuth() }
            }.body()
        } catch (e: Exception) {
            AppLogger.error("HttpVeggieOrderRepository", "getProductSettlementsForRound failed: ${e.message}", e)
            emptyList()
        }
    }

    override suspend fun saveDraftSettlement(
        roundId: String,
        buyerOperationFeeCents: Long,
        transportFeeCents: Long,
        transporterUserId: String?,
        productSettlements: List<VeggieRoundProductSettlement>,
        actualQuantitiesByEntryId: Map<String, Long>
    ): Boolean {
        return try {
            val response = client.httpClient.post(client.resolveUrl("/api/admin/veggie/rounds/$roundId/draft-settlement")) {
                contentType(ContentType.Application.Json)
                client.run { appendAdminAuth() }
                setBody(
                    VeggieSettlementRequest(
                        buyerOperationFeeCents = buyerOperationFeeCents,
                        transportFeeCents = transportFeeCents,
                        transporterUserId = transporterUserId,
                        productSettlements = productSettlements,
                        actualQuantitiesByEntryId = actualQuantitiesByEntryId
                    )
                )
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            AppLogger.error("HttpVeggieOrderRepository", "saveDraftSettlement failed: ${e.message}", e)
            false
        }
    }

    override suspend fun executeRoundSettlement(
        roundId: String,
        buyerOperationFeeCents: Long,
        transportFeeCents: Long,
        transporterUserId: String?,
        productSettlements: List<VeggieRoundProductSettlement>,
        actualQuantitiesByEntryId: Map<String, Long>,
        settledAt: Long
    ): VeggieRoundSettlementResult? {
        return try {
            val response = client.httpClient.post(client.resolveUrl("/api/admin/veggie/rounds/$roundId/settle")) {
                contentType(ContentType.Application.Json)
                client.run { appendAdminAuth() }
                setBody(
                    VeggieSettlementRequest(
                        buyerOperationFeeCents = buyerOperationFeeCents,
                        transportFeeCents = transportFeeCents,
                        transporterUserId = transporterUserId,
                        productSettlements = productSettlements,
                        actualQuantitiesByEntryId = actualQuantitiesByEntryId,
                        settledAt = settledAt
                    )
                )
            }
            if (response.status.isSuccess()) {
                response.body()
            } else {
                null
            }
        } catch (e: Exception) {
            AppLogger.error("HttpVeggieOrderRepository", "executeRoundSettlement failed: ${e.message}", e)
            null
        }
    }
}

class CustomerNetworkClient(val baseUrl: String = "") {
    var isAuthenticated: Boolean = false
        private set
    var currentUser: User? = null

    fun resolveUrl(path: String): String {
        val cleanBase = baseUrl.trimEnd('/')
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        return if (cleanBase.isEmpty()) cleanPath else "$cleanBase$cleanPath"
    }

    fun resolveProductImageUrl(productId: String, isThumb: Boolean = true, updatedAt: Long = 0L): String {
        val versionParam = if (updatedAt > 0L) "&v=$updatedAt" else ""
        return resolveUrl("/api/veggie/images/$productId?thumb=$isThumb$versionParam")
    }

    private val _onDataChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val onDataChanged: SharedFlow<Unit> = _onDataChanged.asSharedFlow()

    private val _onUnauthorized = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val onUnauthorized: SharedFlow<Unit> = _onUnauthorized.asSharedFlow()

    fun notifyUnauthorized() {
        val wasAuthenticated = isAuthenticated
        stopRealtimeSync()
        isAuthenticated = false
        currentUser = null
        if (wasAuthenticated) {
            _onUnauthorized.tryEmit(Unit)
        }
    }

    private var syncJob: Job? = null

    val httpClient = HttpClient {
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

    fun HttpRequestBuilder.appendCustomerAuth() {}

    suspend fun tryRestoreCustomerSession(): User? {
        return try {
            val response = httpClient.get(resolveUrl("/api/customer/me"))
            if (response.status.isSuccess()) {
                val user: User = response.body()
                currentUser = user
                isAuthenticated = true
                user
            } else {
                isAuthenticated = false
                currentUser = null
                null
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            isAuthenticated = false
            currentUser = null
            null
        }
    }

    suspend fun fetchCurrentUser(): User? {
        return try {
            val response = httpClient.get(resolveUrl("/api/customer/me")) {
                appendCustomerAuth()
            }
            if (response.status.isSuccess()) {
                val user: User = response.body()
                currentUser = user
                user
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun updateMySettings(
        language: Language,
        secondaryCurrency: SecondaryCurrency? = null,
        avatar: UserAvatarConfig? = null,
        newPin: String? = null,
        requirePinOnKiosk: Boolean = true
    ): User? {
        return try {
            val response = httpClient.post(resolveUrl("/api/customer/me/settings")) {
                contentType(ContentType.Application.Json)
                appendCustomerAuth()
                setBody(
                    CustomerSettingsUpdateRequest(
                        language = language,
                        secondaryCurrency = secondaryCurrency,
                        avatar = avatar,
                        newPin = newPin,
                        requirePinOnKiosk = requirePinOnKiosk
                    )
                )
            }
            if (response.status.isSuccess()) {
                val updated: User = response.body()
                currentUser = updated
                updated
            } else {
                null
            }
        } catch (e: Exception) {
            AppLogger.error("CustomerNetworkClient", "updateMySettings failed: ${e.message}", e)
            null
        }
    }

    suspend fun getVeggieState(): CustomerVeggieStateResponse {
        return try {
            val response = httpClient.get(resolveUrl("/api/customer/veggie/state")) {
                appendCustomerAuth()
            }
            if (!response.status.isSuccess()) {
                throw IllegalStateException("HTTP ${response.status.value}")
            }
            response.body()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.error("CustomerNetworkClient", "getVeggieState failed: ${e.message}", e)
            KioskConnectionMonitor.reportNetworkFailure(baseUrl)
            throw e
        }
    }

    suspend fun saveCustomerOrder(roundId: String, productQuantitiesScaled: Map<String, Long>): Boolean {
        return try {
            val response = httpClient.post(resolveUrl("/api/customer/veggie/order")) {
                contentType(ContentType.Application.Json)
                appendCustomerAuth()
                setBody(SaveCustomerVeggieOrderRequest(roundId, productQuantitiesScaled))
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            AppLogger.error("CustomerNetworkClient", "saveCustomerOrder failed: ${e.message}", e)
            KioskConnectionMonitor.reportNetworkFailure(baseUrl)
            false
        }
    }

    suspend fun saveVeggieFavorites(productIds: Set<String>): Boolean {
        return try {
            val response = httpClient.post(resolveUrl("/api/customer/veggie/favorites")) {
                contentType(ContentType.Application.Json)
                appendCustomerAuth()
                setBody(UpdateCustomerVeggieFavoritesRequest(productIds))
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            AppLogger.error("CustomerNetworkClient", "saveVeggieFavorites failed: ${e.message}", e)
            false
        }
    }

    suspend fun getVeggieHistory(): List<CustomerVeggieRoundHistoryItem> {
        return try {
            val response = httpClient.get(resolveUrl("/api/customer/veggie/history")) {
                appendCustomerAuth()
            }
            if (!response.status.isSuccess()) {
                throw IllegalStateException("HTTP ${response.status.value}")
            }
            response.body()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.error("CustomerNetworkClient", "getVeggieHistory failed: ${e.message}", e)
            KioskConnectionMonitor.reportNetworkFailure(baseUrl)
            throw e
        }
    }

    suspend fun submitCustomerNote(message: String): Boolean {
        return try {
            val response = httpClient.post(resolveUrl("/api/customer/notes")) {
                contentType(ContentType.Application.Json)
                appendCustomerAuth()
                setBody(AdminNote(id = "", message = message, userId = currentUser?.id ?: "", userNameSnapshot = currentUser?.name ?: ""))
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            AppLogger.error("CustomerNetworkClient", "submitCustomerNote failed: ${e.message}", e)
            false
        }
    }

    suspend fun logout() {
        stopRealtimeSync()
        isAuthenticated = false
        currentUser = null
        try {
            httpClient.post(resolveUrl("/api/customer/logout"))
        } catch (_: Exception) {}
    }

    fun startRealtimeSync(scope: CoroutineScope) {
        try {
            jsStartSse("customer", "/api/customer/events", baseUrl)
        } catch (e: Exception) {
            AppLogger.error("CustomerNetworkClient", "Failed to initialize Customer EventSource: ${e.message}", e)
        }

        syncJob?.cancel()
        syncJob = scope.launch {
            while (isActive) {
                delay(1200)
                if (!isAuthenticated) break
                try {
                    if (jsCheckAndClearSseDataChanged("customer")) {
                        _onDataChanged.tryEmit(Unit)
                    }
                    if (jsCheckAndClearSseSyncError("customer")) {
                        KioskConnectionMonitor.checkHealthNow(baseUrl)
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
            jsStopSse("customer")
        } catch (_: Exception) {}
    }
}

class HttpCustomerUserRepository(private val client: CustomerNetworkClient) : UserRepository {
    override fun getUsersFlow(): Flow<List<User>> = flow {
        emit(getAllUsers())
    }

    override suspend fun getAllUsers(): List<User> = listOfNotNull(client.currentUser ?: client.fetchCurrentUser())

    override suspend fun getUserById(id: String): User? =
        (client.currentUser ?: client.fetchCurrentUser())?.takeIf { it.id == id }

    override suspend fun getUserByBarcode(barcode: String): User? = null

    override suspend fun saveUser(user: User) {
        val newPin = if (user.pin != null && user.pin != "PROTECTED") user.pin else null
        client.updateMySettings(
            language = user.language,
            secondaryCurrency = user.secondaryCurrency,
            avatar = user.avatar,
            newPin = newPin,
            requirePinOnKiosk = user.requirePinOnKiosk
        )
    }
    override suspend fun deactivateUser(id: String) {}
    override suspend fun softDeleteUser(id: String) {}
    override suspend fun restoreUser(id: String) {}
    override suspend fun canHardDeleteUser(id: String): Boolean = false
    override suspend fun hardDeleteUser(id: String): Boolean = false
    override suspend fun updateBalance(userId: String, amountDelta: Long) {}
}

class HttpCustomerTransactionRepository(private val client: CustomerNetworkClient) : TransactionRepository {
    override fun getTransactionsFlow(): Flow<List<Transaction>> = emptyFlow()

    override suspend fun getAllTransactions(): List<Transaction> {
        return try {
            client.httpClient.get(client.resolveUrl("/api/customer/transactions")) {
                client.run { appendCustomerAuth() }
            }.body()
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun getTransactionsByUserId(userId: String): List<Transaction> {
        return getAllTransactions()
    }

    override suspend fun getTransactionById(id: String): Transaction? {
        return getAllTransactions().find { it.id == id }
    }

    override suspend fun recordTransaction(transaction: Transaction) {}

    override suspend fun getTransactionCountForUser(userId: String): Int {
        return getAllTransactions().size
    }

    override suspend fun getTransactionsPaged(
        page: Int,
        pageSize: Int,
        searchQuery: String?,
        typeFilter: TransactionType?
    ): PagedResult<Transaction> {
        return getTransactionsByUserIdPaged("", page, pageSize, searchQuery, typeFilter)
    }

    override suspend fun getTransactionsByUserIdPaged(
        userId: String,
        page: Int,
        pageSize: Int,
        searchQuery: String?,
        typeFilter: TransactionType?
    ): PagedResult<Transaction> {
        return try {
            client.httpClient.get(client.resolveUrl("/api/customer/transactions")) {
                client.run { appendCustomerAuth() }
                parameter("page", page)
                parameter("pageSize", pageSize)
                if (!searchQuery.isNullOrBlank()) parameter("search", searchQuery)
                if (typeFilter != null) parameter("type", typeFilter.name)
            }.body()
        } catch (_: Exception) {
            PagedResult(emptyList(), 0, page, pageSize, 1)
        }
    }

    override suspend fun getTransactionsByReferenceIds(referenceIds: List<String>): List<Transaction> = emptyList()
    override suspend fun getTransactionsByIds(ids: List<String>): List<Transaction> = emptyList()
    override suspend fun getCancellationCountForReference(refId: String): Int = 0
    override suspend fun executeAtomicTransaction(transaction: Transaction, balanceDelta: Long, stockDeltas: Map<String, Long>) {}
    override suspend fun applyPurchaseCorrection(originalTransactionId: String, newItems: List<TransactionItem>): Boolean = false
    override suspend fun stornoNonPurchase(transactionId: String): Boolean = false
    override suspend fun executeBatchTransactions(requests: List<AtomicTransactionRequest>): Boolean = false
}

