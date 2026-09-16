package de.joelneumann.lojinha.data.repository

import de.joelneumann.lojinha.domain.model.*
import de.joelneumann.lojinha.domain.repository.*
import de.joelneumann.lojinha.ui.utils.sortedByAccentInsensitive
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
    const es = new EventSource(url);
    es.addEventListener('data_changed', () => {
        window._lojinhaHasNewData = true;
    });
    window._lojinhaEventSource = es;
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
                println("Client login failed with HTTP status: ${response.status}")
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
            println("Failed to initialize EventSource: ${e.message}")
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
        client.httpClient.post(client.resolveUrl("/api/admin/products")) {
            contentType(ContentType.Application.Json)
            client.run { appendAdminAuth() }
            setBody(product)
        }
    }

    override suspend fun deactivateProduct(id: String) {
        client.httpClient.post(client.resolveUrl("/api/admin/products/deactivate/$id")) {
            client.run { appendAdminAuth() }
        }
    }

    override suspend fun hardDeleteProduct(id: String) {
        client.httpClient.delete(client.resolveUrl("/api/admin/products/$id")) {
            client.run { appendAdminAuth() }
        }
    }

    override suspend fun updateStock(productId: String, delta: Long) {
        client.httpClient.post(client.resolveUrl("/api/admin/products/stock")) {
            contentType(ContentType.Application.Json)
            client.run { appendAdminAuth() }
            setBody(DeltaRequest(productId, delta))
        }
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
        client.httpClient.post(client.resolveUrl("/api/admin/users")) {
            contentType(ContentType.Application.Json)
            client.run { appendAdminAuth() }
            setBody(user)
        }
    }

    override suspend fun deactivateUser(id: String) {
        client.httpClient.post(client.resolveUrl("/api/admin/users/deactivate/$id")) {
            client.run { appendAdminAuth() }
        }
    }

    override suspend fun softDeleteUser(id: String) {
        client.httpClient.post(client.resolveUrl("/api/admin/users/soft-delete/$id")) {
            client.run { appendAdminAuth() }
        }
    }

    override suspend fun restoreUser(id: String) {
        client.httpClient.post(client.resolveUrl("/api/admin/users/restore/$id")) {
            client.run { appendAdminAuth() }
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
        val res = client.httpClient.delete(client.resolveUrl("/api/admin/users/$id")) {
            client.run { appendAdminAuth() }
        }
        return res.status.isSuccess()
    }

    override suspend fun updateBalance(userId: String, amountDelta: Long) {
        client.httpClient.post(client.resolveUrl("/api/admin/users/balance")) {
            contentType(ContentType.Application.Json)
            client.run { appendAdminAuth() }
            setBody(DeltaRequest(userId, amountDelta))
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
        client.httpClient.post(client.resolveUrl("/api/admin/transactions")) {
            contentType(ContentType.Application.Json)
            client.run { appendAdminAuth() }
            setBody(transaction)
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
            client.httpClient.post(client.resolveUrl("/api/admin/transactions/atomic")) {
                contentType(ContentType.Application.Json)
                client.run { appendAdminAuth() }
                setBody(AtomicTransactionRequest(transaction, balanceDelta, stockDeltas))
            }
        } catch (e: Exception) {
            println("[HttpTransactionRepository] executeAtomicTransaction failed: ${e.message}")
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
            println("[HttpTransactionRepository] executeBatchTransactions failed: ${e.message}")
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
        client.httpClient.post(client.resolveUrl("/api/admin/billing-lists")) {
            contentType(ContentType.Application.Json)
            client.run { appendAdminAuth() }
            setBody(list)
        }
    }

    override suspend fun deleteBillingList(id: String) {
        client.httpClient.delete(client.resolveUrl("/api/admin/billing-lists/$id")) {
            client.run { appendAdminAuth() }
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
