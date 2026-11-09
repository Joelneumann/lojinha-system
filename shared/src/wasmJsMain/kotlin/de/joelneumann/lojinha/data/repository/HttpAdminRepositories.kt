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
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@JsFun("(key, value) => window.sessionStorage.setItem(key, value)")
private external fun jsSetSessionItem(key: String, value: String)

@JsFun("(key) => window.sessionStorage.getItem(key)")
private external fun jsGetSessionItem(key: String): String?

@JsFun("(key) => window.sessionStorage.removeItem(key)")
private external fun jsRemoveSessionItem(key: String)

@JsFun("""
(baseUrl, token) => {
    if (window._lojinhaEventSource) {
        window._lojinhaEventSource.close();
    }
    const cleanBase = baseUrl ? baseUrl.replace(/\/+$/, '') : '';
    const url = cleanBase + '/api/admin/events' + (token ? '?token=' + encodeURIComponent(token) : '');
    try {
        const es = new EventSource(url);
        es.addEventListener('data_changed', () => {
            window._lojinhaHasNewData = true;
        });
        es.onerror = (e) => {
            window._lojinhaSyncError = true;
        };
        window._lojinhaEventSource = es;
    } catch (err) {
        console.warn('Failed to start EventSource:', err);
    }
}
""")
private external fun jsStartEventSource(baseUrl: String, token: String)

@JsFun("""
() => {
    if (window._lojinhaEventSource) {
        window._lojinhaEventSource.close();
        window._lojinhaEventSource = null;
    }
}
""")
private external fun jsStopEventSource()

@JsFun("() => { const flag = window._lojinhaHasNewData === true; window._lojinhaHasNewData = false; return flag; }")
private external fun jsCheckAndClearDataChanged(): Boolean

sealed class LoginResult {
    object Success : LoginResult()
    data class RateLimited(val message: String) : LoginResult()
    object InvalidCredentials : LoginResult()
    data class NetworkError(val message: String) : LoginResult()
}

class AdminNetworkClient(val baseUrl: String = "") {
    var authToken: String? = null
    var adminPassword: String = ""

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
        val hadToken = authToken != null
        stopRealtimeSync()
        authToken = null
        adminPassword = ""
        try { jsRemoveSessionItem("lojinha_admin_token") } catch (e: Exception) {}
        if (hadToken) {
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

    suspend fun login(password: String): LoginResult {
        return try {
            val response = httpClient.post(resolveUrl("/api/admin/login")) {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(password))
            }
            if (response.status == HttpStatusCode.TooManyRequests) {
                val resp: LoginResponse? = try { response.body() } catch (e: Exception) { null }
                val msg = resp?.message?.ifBlank { null } ?: "Too many failed attempts. Please wait a minute."
                LoginResult.RateLimited(msg)
            } else if (response.status.isSuccess()) {
                val loginResponse: LoginResponse = response.body()
                if (loginResponse.success) {
                    authToken = loginResponse.token
                    adminPassword = password
                    authToken?.let { token ->
                        try { jsSetSessionItem("lojinha_admin_token", token) } catch (e: Exception) {}
                    }
                    LoginResult.Success
                } else {
                    LoginResult.InvalidCredentials
                }
            } else {
                AppLogger.warn("AdminNetworkClient", "Client login failed with HTTP status: ${response.status}")
                LoginResult.InvalidCredentials
            }
        } catch (e: Exception) {
            e.printStackTrace()
            LoginResult.NetworkError(e.message ?: "Network error")
        }
    }

    suspend fun tryRestoreSession(): Boolean {
        val storedToken = try { jsGetSessionItem("lojinha_admin_token") } catch (e: Exception) { null }
        if (storedToken.isNullOrBlank()) return false
        authToken = storedToken
        return try {
            val response = httpClient.get(resolveUrl("/api/admin/products")) {
                appendAdminAuth()
            }
            if (response.status.isSuccess()) {
                true
            } else if (response.status == HttpStatusCode.Unauthorized) {
                logout()
                false
            } else {
                // Server error / unavailable - do not clear stored token so user can retry
                authToken = null
                false
            }
        } catch (e: Exception) {
            // Transient network failure - keep stored token
            authToken = null
            false
        }
    }

    suspend fun logout() {
        stopRealtimeSync()
        try {
            httpClient.post(resolveUrl("/api/admin/logout")) {
                appendAdminAuth()
            }
        } catch (e: Exception) {}
        authToken = null
        adminPassword = ""
        try { jsRemoveSessionItem("lojinha_admin_token") } catch (e: Exception) {}
    }

    fun startRealtimeSync(scope: CoroutineScope) {
        val token = authToken ?: ""
        try {
            jsStartEventSource(baseUrl, token)
        } catch (e: Exception) {
            AppLogger.error("AdminNetworkClient", "Failed to initialize EventSource: ${e.message}", e)
        }

        syncJob?.cancel()
        syncJob = scope.launch {
            while (isActive) {
                delay(1200)
                if (authToken.isNullOrBlank()) break
                try {
                    if (jsCheckAndClearDataChanged()) {
                        _onDataChanged.tryEmit(Unit)
                    }
                } catch (e: Exception) {}
            }
        }
    }

    fun stopRealtimeSync() {
        syncJob?.cancel()
        syncJob = null
        try {
            jsStopEventSource()
        } catch (e: Exception) {}
    }

    fun HttpRequestBuilder.appendAdminAuth() {
        if (!authToken.isNullOrBlank()) {
            header(HttpHeaders.Authorization, "Bearer $authToken")
        } else if (adminPassword.isNotBlank()) {
            header("X-Admin-Password", adminPassword)
        }
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
            emptyList()
        }
    }

    override suspend fun getTransactionsByUserId(userId: String): List<Transaction> {
        return getAllTransactions().filter { it.userId == userId }
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

@JsFun("""
(baseUrl, token) => {
    if (window._lojinhaCustomerEventSource) {
        window._lojinhaCustomerEventSource.close();
    }
    const cleanBase = baseUrl ? baseUrl.replace(/\/+$/, '') : '';
    const url = cleanBase + '/api/customer/events' + (token ? '?token=' + encodeURIComponent(token) : '');
    try {
        const es = new EventSource(url);
        es.addEventListener('data_changed', () => {
            window._lojinhaCustomerHasNewData = true;
        });
        es.onerror = (e) => {
            window._lojinhaCustomerSyncError = true;
        };
        window._lojinhaCustomerEventSource = es;
    } catch (err) {
        console.warn('Failed to start Customer EventSource:', err);
    }
}
""")
private external fun jsStartCustomerEventSource(baseUrl: String, token: String)

@JsFun("""
() => {
    if (window._lojinhaCustomerEventSource) {
        window._lojinhaCustomerEventSource.close();
        window._lojinhaCustomerEventSource = null;
    }
}
""")
private external fun jsStopCustomerEventSource()

@JsFun("() => { const flag = window._lojinhaCustomerHasNewData === true; window._lojinhaCustomerHasNewData = false; return flag; }")
private external fun jsCheckAndClearCustomerDataChanged(): Boolean

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

    suspend fun uploadProductImage(productId: String, imageBytes: ByteArray): String? {
        return try {
            val response = client.httpClient.post(client.resolveUrl("/api/admin/veggie/products/$productId/image")) {
                contentType(ContentType.Application.OctetStream)
                client.run { appendAdminAuth() }
                setBody(imageBytes)
            }
            if (response.status.isSuccess()) {
                val res: Map<String, String> = response.body()
                res["imageFileName"]
            } else {
                null
            }
        } catch (e: Exception) {
            AppLogger.error("HttpVeggieOrderRepository", "uploadProductImage failed: ${e.message}", e)
            null
        }
    }

    suspend fun deleteProductImage(productId: String): Boolean {
        return try {
            val response = client.httpClient.delete(client.resolveUrl("/api/admin/veggie/products/$productId/image")) {
                client.run { appendAdminAuth() }
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            AppLogger.error("HttpVeggieOrderRepository", "deleteProductImage failed: ${e.message}", e)
            false
        }
    }

    fun resolveProductImageUrl(productId: String, isThumb: Boolean = true, updatedAt: Long = 0L): String {
        val versionParam = if (updatedAt > 0L) "&v=$updatedAt" else ""
        return client.resolveUrl("/api/veggie/images/$productId?thumb=$isThumb$versionParam")
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

sealed class CustomerLoginResult {
    data class Success(val user: User) : CustomerLoginResult()
    data class RateLimited(val message: String) : CustomerLoginResult()
    object PinRequired : CustomerLoginResult()
    object InvalidPin : CustomerLoginResult()
    data class NetworkError(val message: String) : CustomerLoginResult()
}

class CustomerNetworkClient(val baseUrl: String = "") {
    var authToken: String? = null
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
        val hadToken = authToken != null
        stopRealtimeSync()
        authToken = null
        currentUser = null
        try { jsRemoveSessionItem("lojinha_customer_token") } catch (_: Exception) {}
        if (hadToken) {
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

    fun HttpRequestBuilder.appendCustomerAuth() {
        if (!authToken.isNullOrBlank()) {
            header(HttpHeaders.Authorization, "Bearer $authToken")
        }
    }

    suspend fun loginCustomer(userId: String, pin: String): CustomerLoginResult {
        return try {
            val response = httpClient.post(resolveUrl("/api/customer/login")) {
                contentType(ContentType.Application.Json)
                setBody(CustomerLoginRequest(userId = userId, pin = pin))
            }
            if (response.status == HttpStatusCode.TooManyRequests) {
                val resp: CustomerLoginResponse? = try { response.body() } catch (_: Exception) { null }
                val msg = resp?.message?.ifBlank { null } ?: "Too many failed attempts. Please wait a minute."
                CustomerLoginResult.RateLimited(msg)
            } else if (response.status.isSuccess()) {
                val loginResp: CustomerLoginResponse = response.body()
                if (loginResp.success && loginResp.user != null && !loginResp.token.isNullOrBlank()) {
                    authToken = loginResp.token
                    currentUser = loginResp.user
                    try { jsSetSessionItem("lojinha_customer_token", loginResp.token) } catch (_: Exception) {}
                    CustomerLoginResult.Success(loginResp.user)
                } else if (loginResp.message.contains("PIN required", ignoreCase = true)) {
                    CustomerLoginResult.PinRequired
                } else {
                    CustomerLoginResult.InvalidPin
                }
            } else {
                CustomerLoginResult.InvalidPin
            }
        } catch (e: Exception) {
            CustomerLoginResult.NetworkError(e.message ?: "Network error")
        }
    }

    suspend fun tryRestoreCustomerSession(): User? {
        val storedToken = try { jsGetSessionItem("lojinha_customer_token") } catch (_: Exception) { null }
        if (storedToken.isNullOrBlank()) return null
        authToken = storedToken
        return try {
            val response = httpClient.get(resolveUrl("/api/customer/me")) {
                appendCustomerAuth()
            }
            if (response.status.isSuccess()) {
                val user: User = response.body()
                currentUser = user
                user
            } else if (response.status == HttpStatusCode.Unauthorized) {
                logout()
                null
            } else {
                authToken = null
                null
            }
        } catch (_: Exception) {
            authToken = null
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
            httpClient.get(resolveUrl("/api/customer/veggie/state")) {
                appendCustomerAuth()
            }.body()
        } catch (e: Exception) {
            AppLogger.error("CustomerNetworkClient", "getVeggieState failed: ${e.message}", e)
            CustomerVeggieStateResponse()
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
            false
        }
    }

    suspend fun getVeggieHistory(): List<CustomerVeggieRoundHistoryItem> {
        return try {
            httpClient.get(resolveUrl("/api/customer/veggie/history")) {
                appendCustomerAuth()
            }.body()
        } catch (e: Exception) {
            AppLogger.error("CustomerNetworkClient", "getVeggieHistory failed: ${e.message}", e)
            emptyList()
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
        try {
            httpClient.post(resolveUrl("/api/customer/logout")) {
                appendCustomerAuth()
            }
        } catch (_: Exception) {}
        authToken = null
        currentUser = null
        try { jsRemoveSessionItem("lojinha_customer_token") } catch (_: Exception) {}
    }

    fun startRealtimeSync(scope: CoroutineScope) {
        val token = authToken ?: ""
        try {
            jsStartCustomerEventSource(baseUrl, token)
        } catch (e: Exception) {
            AppLogger.error("CustomerNetworkClient", "Failed to initialize Customer EventSource: ${e.message}", e)
        }

        syncJob?.cancel()
        syncJob = scope.launch {
            while (isActive) {
                delay(1200)
                if (authToken.isNullOrBlank()) break
                try {
                    if (jsCheckAndClearCustomerDataChanged()) {
                        _onDataChanged.tryEmit(Unit)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun stopRealtimeSync() {
        syncJob?.cancel()
        syncJob = null
        try {
            jsStopCustomerEventSource()
        } catch (_: Exception) {}
    }
}

class HttpCustomerUserRepository(private val client: CustomerNetworkClient) : UserRepository {
    override fun getUsersFlow(): Flow<List<User>> = flow {
        emit(getAllUsers())
    }

    override suspend fun getAllUsers(): List<User> {
        return try {
            val list: List<User> = client.httpClient.get(client.resolveUrl("/api/customer/users")).body()
            list.sortedByAccentInsensitive { it.name }
        } catch (_: Exception) {
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


