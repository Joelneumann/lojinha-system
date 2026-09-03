package de.joelneumann.lojinha.data.repository

import de.joelneumann.lojinha.domain.model.*
import de.joelneumann.lojinha.domain.repository.*
import de.joelneumann.lojinha.ui.utils.sortedByAccentInsensitive
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

class AdminNetworkClient(private val baseUrl: String = "") {
    var adminPassword: String = ""

    val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }

    suspend fun login(password: String): Boolean {
        return try {
            val response = httpClient.post("$baseUrl/api/admin/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(password))
            }
            if (response.status.isSuccess()) {
                val loginResponse: LoginResponse = response.body()
                if (loginResponse.success) {
                    adminPassword = password
                    true
                } else {
                    false
                }
            } else {
                println("Client login failed with HTTP status: ${response.status}")
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun HttpRequestBuilder.appendAdminAuth() {
        if (adminPassword.isNotBlank()) {
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
            val list: List<Product> = client.httpClient.get("/api/admin/products") {
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
        client.httpClient.post("/api/admin/products") {
            contentType(ContentType.Application.Json)
            client.run { appendAdminAuth() }
            setBody(product)
        }
    }

    override suspend fun deactivateProduct(id: String) {
        client.httpClient.post("/api/admin/products/deactivate/$id") {
            client.run { appendAdminAuth() }
        }
    }

    override suspend fun hardDeleteProduct(id: String) {
        client.httpClient.delete("/api/admin/products/$id") {
            client.run { appendAdminAuth() }
        }
    }

    override suspend fun updateStock(productId: String, delta: Long) {
        client.httpClient.post("/api/admin/products/stock") {
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
            val list: List<User> = client.httpClient.get("/api/admin/users") {
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
        client.httpClient.post("/api/admin/users") {
            contentType(ContentType.Application.Json)
            client.run { appendAdminAuth() }
            setBody(user)
        }
    }

    override suspend fun deactivateUser(id: String) {
        client.httpClient.post("/api/admin/users/deactivate/$id") {
            client.run { appendAdminAuth() }
        }
    }

    override suspend fun softDeleteUser(id: String) {
        client.httpClient.post("/api/admin/users/soft-delete/$id") {
            client.run { appendAdminAuth() }
        }
    }

    override suspend fun restoreUser(id: String) {
        client.httpClient.post("/api/admin/users/restore/$id") {
            client.run { appendAdminAuth() }
        }
    }

    override suspend fun canHardDeleteUser(id: String): Boolean = true

    override suspend fun hardDeleteUser(id: String): Boolean {
        val res = client.httpClient.delete("/api/admin/users/$id") {
            client.run { appendAdminAuth() }
        }
        return res.status.isSuccess()
    }

    override suspend fun updateBalance(userId: String, amountDelta: Long) {
        client.httpClient.post("/api/admin/users/balance") {
            contentType(ContentType.Application.Json)
            client.run { appendAdminAuth() }
            setBody(DeltaRequest(userId, amountDelta))
        }
    }
}

class HttpTransactionRepository(private val client: AdminNetworkClient) : TransactionRepository {
    override fun getTransactionsFlow(): Flow<List<Transaction>> = flow {
        emit(getAllTransactions())
    }

    override suspend fun getAllTransactions(): List<Transaction> {
        return try {
            client.httpClient.get("/api/admin/transactions") {
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
        client.httpClient.post("/api/admin/transactions") {
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
            client.httpClient.get("/api/admin/transactions") {
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
            client.httpClient.get("/api/admin/transactions") {
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
}

class HttpSettingsRepository(private val client: AdminNetworkClient) : SettingsRepository {
    override fun getSettingsFlow(): Flow<SystemSettings> = flow {
        emit(getSettings())
    }

    override suspend fun getSettings(): SystemSettings {
        return try {
            client.httpClient.get("/api/admin/settings") {
                client.run { appendAdminAuth() }
            }.body()
        } catch (e: Exception) {
            SystemSettings()
        }
    }

    override suspend fun updateSettings(settings: SystemSettings) {
        client.httpClient.post("/api/admin/settings") {
            contentType(ContentType.Application.Json)
            client.run { appendAdminAuth() }
            setBody(settings)
        }
    }
}
