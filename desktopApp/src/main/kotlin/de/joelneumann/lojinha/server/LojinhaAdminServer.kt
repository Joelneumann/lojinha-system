package de.joelneumann.lojinha.server

import de.joelneumann.lojinha.data.service.DataChangeNotifier
import de.joelneumann.lojinha.domain.model.*
import de.joelneumann.lojinha.domain.repository.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class LojinhaAdminServer(
    private val productRepository: ProductRepository,
    private val userRepository: UserRepository,
    private val transactionRepository: TransactionRepository,
    private val billingListRepository: BillingListRepository,
    private val settingsRepository: SettingsRepository,
    private val port: Int = 8080
) {
    private var server: EmbeddedServer<*, *>? = null

    // Token Session Management
    private val activeTokens = ConcurrentHashMap<String, Long>() // token -> expiryTimestamp
    private val failedLoginAttempts = ConcurrentHashMap<String, Pair<Int, Long>>() // ip -> (count, firstAttemptTime)
    private val SESSION_DURATION_MS = 8 * 60 * 60 * 1000L // 8 hours
    private val MAX_LOGIN_ATTEMPTS = 5
    private val RATE_LIMIT_WINDOW_MS = 60 * 1000L // 1 minute

    private fun safeEquals(a: String, b: String): Boolean {
        return MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
    }

    private fun buildServer(bindPort: Int): EmbeddedServer<*, *> = embeddedServer(Netty, port = bindPort, host = "0.0.0.0") {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
            install(CORS) {
                anyHost()
                allowHeader(HttpHeaders.ContentType)
                allowHeader(HttpHeaders.Authorization)
                allowHeader("X-Admin-Password")
                allowMethod(HttpMethod.Options)
                allowMethod(HttpMethod.Put)
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Delete)
                allowMethod(HttpMethod.Get)
            }

            routing {
                // 1. Public Login & Logout Endpoints
                post("/api/admin/login") {
                    val clientIp = call.request.local.remoteHost
                    val now = System.currentTimeMillis()

                    // Rate Limiting Check
                    val currentAttempt = failedLoginAttempts[clientIp]
                    if (currentAttempt != null) {
                        val (count, firstAttempt) = currentAttempt
                        if (now - firstAttempt < RATE_LIMIT_WINDOW_MS && count >= MAX_LOGIN_ATTEMPTS) {
                            println("[AUTH] Rate limit exceeded for IP: $clientIp")
                            call.respond(
                                HttpStatusCode.TooManyRequests,
                                LoginResponse(false, "Too many failed login attempts. Please wait a minute before retrying.")
                            )
                            return@post
                        } else if (now - firstAttempt >= RATE_LIMIT_WINDOW_MS) {
                            failedLoginAttempts.remove(clientIp)
                        }
                    }

                    try {
                        val req = call.receive<LoginRequest>()
                        val settings = settingsRepository.getSettings()
                        val expectedPassword = if (settings.adminPasswordHash.isNotBlank()) settings.adminPasswordHash else "admin"
                        val matches = safeEquals(req.password, expectedPassword)

                        println("[AUTH] Admin Login Attempt: success=$matches")
                        if (matches) {
                            failedLoginAttempts.remove(clientIp)
                            val token = UUID.randomUUID().toString()
                            activeTokens[token] = now + SESSION_DURATION_MS
                            call.respond(HttpStatusCode.OK, LoginResponse(true, "Authenticated", token))
                        } else {
                            val prevCount = failedLoginAttempts[clientIp]?.first ?: 0
                            val firstTime = failedLoginAttempts[clientIp]?.second ?: now
                            failedLoginAttempts[clientIp] = Pair(prevCount + 1, firstTime)
                            call.respond(HttpStatusCode.OK, LoginResponse(false, "Invalid admin password"))
                        }
                    } catch (e: Exception) {
                        println("[ERROR] Error processing login request: ${e.message}")
                        call.respond(HttpStatusCode.OK, LoginResponse(false, "Error processing login request"))
                    }
                }

                post("/api/admin/logout") {
                    val token = call.extractBearerToken()
                    if (token != null) {
                        activeTokens.remove(token)
                    }
                    call.respond(HttpStatusCode.OK)
                }

                // 2. Protected Admin API Endpoints
                route("/api/admin") {
                    // Products
                    get("/products") {
                        if (!call.checkAdminAuth(settingsRepository)) return@get
                        call.respond(productRepository.getAllProducts())
                    }
                    post("/products") {
                        if (!call.checkAdminAuth(settingsRepository)) return@post
                        val product = call.receive<Product>()
                        productRepository.saveProduct(product)
                        call.respond(HttpStatusCode.OK)
                    }
                    post("/products/stock") {
                        if (!call.checkAdminAuth(settingsRepository)) return@post
                        val req = call.receive<DeltaRequest>()
                        productRepository.updateStock(req.id, req.delta)
                        call.respond(HttpStatusCode.OK)
                    }
                    post("/products/deactivate/{id}") {
                        if (!call.checkAdminAuth(settingsRepository)) return@post
                        val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                        productRepository.deactivateProduct(id)
                        call.respond(HttpStatusCode.OK)
                    }
                    delete("/products/{id}") {
                        if (!call.checkAdminAuth(settingsRepository)) return@delete
                        val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                        productRepository.hardDeleteProduct(id)
                        call.respond(HttpStatusCode.OK)
                    }

                    // Users
                    get("/users") {
                        if (!call.checkAdminAuth(settingsRepository)) return@get
                        call.respond(userRepository.getAllUsers())
                    }
                    post("/users") {
                        if (!call.checkAdminAuth(settingsRepository)) return@post
                        val user = call.receive<User>()
                        userRepository.saveUser(user)
                        call.respond(HttpStatusCode.OK)
                    }
                    post("/users/balance") {
                        if (!call.checkAdminAuth(settingsRepository)) return@post
                        val req = call.receive<DeltaRequest>()
                        userRepository.updateBalance(req.id, req.delta)
                        call.respond(HttpStatusCode.OK)
                    }
                    post("/users/deactivate/{id}") {
                        if (!call.checkAdminAuth(settingsRepository)) return@post
                        val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                        userRepository.deactivateUser(id)
                        call.respond(HttpStatusCode.OK)
                    }
                    post("/users/soft-delete/{id}") {
                        if (!call.checkAdminAuth(settingsRepository)) return@post
                        val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                        userRepository.softDeleteUser(id)
                        call.respond(HttpStatusCode.OK)
                    }
                    post("/users/restore/{id}") {
                        if (!call.checkAdminAuth(settingsRepository)) return@post
                        val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                        userRepository.restoreUser(id)
                        call.respond(HttpStatusCode.OK)
                    }
                    delete("/users/{id}") {
                        if (!call.checkAdminAuth(settingsRepository)) return@delete
                        val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                        userRepository.hardDeleteUser(id)
                        call.respond(HttpStatusCode.OK)
                    }
                    get("/users/can-delete/{id}") {
                        if (!call.checkAdminAuth(settingsRepository)) return@get
                        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                        val canDelete = userRepository.canHardDeleteUser(id)
                        call.respond(mapOf("canDelete" to canDelete))
                    }

                    // Transactions
                    get("/transactions") {
                        if (!call.checkAdminAuth(settingsRepository)) return@get
                        val pageParam = call.request.queryParameters["page"]?.toIntOrNull()
                        val pageSizeParam = call.request.queryParameters["pageSize"]?.toIntOrNull()
                        val searchParam = call.request.queryParameters["search"]
                        val typeParam = call.request.queryParameters["type"]?.let {
                            try { TransactionType.valueOf(it) } catch (e: Exception) { null }
                        }
                        val userIdParam = call.request.queryParameters["userId"]

                        if (pageParam != null && pageSizeParam != null) {
                            val result = if (userIdParam != null) {
                                transactionRepository.getTransactionsByUserIdPaged(userIdParam, pageParam, pageSizeParam, searchParam, typeParam)
                            } else {
                                transactionRepository.getTransactionsPaged(pageParam, pageSizeParam, searchParam, typeParam)
                            }
                            call.respond(result)
                        } else {
                            call.respond(transactionRepository.getAllTransactions())
                        }
                    }
                    post("/transactions") {
                        if (!call.checkAdminAuth(settingsRepository)) return@post
                        val transaction = call.receive<Transaction>()
                        transactionRepository.recordTransaction(transaction)
                        call.respond(HttpStatusCode.OK)
                    }
                    post("/transactions/atomic") {
                        if (!call.checkAdminAuth(settingsRepository)) return@post
                        val req = call.receive<AtomicTransactionRequest>()
                        transactionRepository.executeAtomicTransaction(
                            transaction = req.transaction,
                            balanceDelta = req.balanceDelta,
                            stockDeltas = req.stockDeltas
                        )
                        call.respond(HttpStatusCode.OK)
                    }
                    post("/transactions/batch") {
                        if (!call.checkAdminAuth(settingsRepository)) return@post
                        val reqs = call.receive<List<AtomicTransactionRequest>>()
                        val success = transactionRepository.executeBatchTransactions(reqs)
                        if (success) {
                            call.respond(HttpStatusCode.OK)
                        } else {
                            call.respond(HttpStatusCode.InternalServerError, "Batch execution failed")
                        }
                    }
                    post("/transactions/by-reference-ids") {
                        if (!call.checkAdminAuth(settingsRepository)) return@post
                        val refIds = call.receive<List<String>>()
                        call.respond(transactionRepository.getTransactionsByReferenceIds(refIds))
                    }
                    post("/transactions/by-ids") {
                        if (!call.checkAdminAuth(settingsRepository)) return@post
                        val ids = call.receive<List<String>>()
                        call.respond(transactionRepository.getTransactionsByIds(ids))
                    }
                    get("/transactions/cancellation-count/{refId}") {
                        if (!call.checkAdminAuth(settingsRepository)) return@get
                        val refId = call.parameters["refId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                        val count = transactionRepository.getCancellationCountForReference(refId)
                        call.respond(mapOf("count" to count))
                    }
                    post("/transactions/purchase-correction") {
                        if (!call.checkAdminAuth(settingsRepository)) return@post
                        val request = call.receive<PurchaseCorrectionRequest>()
                        val success = transactionRepository.applyPurchaseCorrection(request.originalTransactionId, request.newItems)
                        if (success) {
                            call.respond(HttpStatusCode.OK)
                        } else {
                            call.respond(HttpStatusCode.BadRequest, "Failed to apply purchase correction")
                        }
                    }
                    post("/transactions/storno-non-purchase") {
                        if (!call.checkAdminAuth(settingsRepository)) return@post
                        val request = call.receive<StornoNonPurchaseRequest>()
                        val success = transactionRepository.stornoNonPurchase(request.transactionId)
                        if (success) {
                            call.respond(HttpStatusCode.OK)
                        } else {
                            call.respond(HttpStatusCode.BadRequest, "Failed to storno transaction")
                        }
                    }

                    // Bulk Billing
                    get("/billing-lists") {
                        if (!call.checkAdminAuth(settingsRepository)) return@get
                        val lists = billingListRepository.getActiveBillingListsFlow().first()
                        call.respond(lists)
                    }
                    post("/billing-lists") {
                        if (!call.checkAdminAuth(settingsRepository)) return@post
                        val list = call.receive<BillingList>()
                        val isNew = list.id.isBlank()
                        val idToSave = if (isNew) UUID.randomUUID().toString() else list.id
                        val listToSave = list.copy(id = idToSave)
                        billingListRepository.saveBillingList(listToSave)
                        billingListRepository.removeAllUsersFromList(idToSave)
                        for (u in listToSave.users) {
                            billingListRepository.addUserToList(
                                u.copy(id = UUID.randomUUID().toString(), listId = idToSave)
                            )
                        }
                        call.respond(HttpStatusCode.OK, mapOf("id" to idToSave))
                    }
                    delete("/billing-lists/{id}") {
                        if (!call.checkAdminAuth(settingsRepository)) return@delete
                        val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                        billingListRepository.deleteBillingList(id)
                        call.respond(HttpStatusCode.OK)
                    }

                    // Real-Time Server-Sent Events (SSE) Stream
                    get("/events") {
                        if (!call.checkAdminAuth(settingsRepository)) return@get
                        call.response.cacheControl(CacheControl.NoCache(null))
                        call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                            val channel = Channel<Unit>(Channel.CONFLATED)
                            val listener = {
                                channel.trySend(Unit)
                                Unit
                            }
                            DataChangeNotifier.addListener(listener)
                            try {
                                write("event: connected\ndata: {}\n\n")
                                flush()
                                while (true) {
                                    val received = withTimeoutOrNull(25_000L) {
                                        channel.receive()
                                    }
                                    if (received != null) {
                                        write("event: data_changed\ndata: {}\n\n")
                                        flush()
                                    } else {
                                        // Keep-alive heartbeat comment ping every 25s
                                        write(": ping\n\n")
                                        flush()
                                    }
                                }
                            } catch (e: Exception) {
                                // Client disconnected
                            } finally {
                                DataChangeNotifier.removeListener(listener)
                                channel.close()
                            }
                        }
                    }
                }

                // 3. Serve static Wasm assets
                get("/{filename...}") {
                    val filename = call.parameters.getAll("filename")?.joinToString("/") ?: "index.html"

                    // Do not handle unknown API requests as static assets
                    if (filename.startsWith("api/") || filename == "api") {
                        call.respond(HttpStatusCode.NotFound)
                        return@get
                    }

                    // Strict protection against path traversal attempts
                    if (filename.contains("..")) {
                        call.respond(HttpStatusCode.NotFound)
                        return@get
                    }

                    val file = findWasmAsset(filename)
                    if (file != null && file.exists() && !file.isDirectory) {
                        call.respondFile(file)
                    } else {
                        val resourceBytes = loadWasmResourceBytes(filename)
                        if (resourceBytes != null) {
                            val contentType = getContentTypeForName(filename)
                            call.respondBytes(resourceBytes, contentType)
                        } else {
                            // Only fallback to index.html for root or clean single-segment SPA routes
                            val isSpaRoute = !filename.contains(".") && !filename.contains("/")
                            if (isSpaRoute) {
                                val indexFile = findWasmAsset("index.html")
                                if (indexFile != null && indexFile.exists()) {
                                    call.respondFile(indexFile)
                                    return@get
                                }
                                val indexBytes = loadWasmResourceBytes("index.html")
                                if (indexBytes != null) {
                                    call.respondBytes(indexBytes, ContentType.Text.Html)
                                    return@get
                                }
                            }
                            call.respond(HttpStatusCode.NotFound)
                        }
                    }
                }
            }
        }

    private var actualPort: Int = port
    fun getActualPort(): Int = actualPort

    fun start(): Boolean {
        for (candidatePort in listOf(port, port + 1)) {
            try {
                actualPort = candidatePort
                val s = buildServer(candidatePort)
                s.start(wait = false)
                server = s
                println("[INFO] Lojinha Embedded Admin Server active on http://0.0.0.0:$candidatePort")
                return true
            } catch (e: Exception) {
                println("[WARN] Failed to start admin server on port $candidatePort: ${e.message}")
            }
        }
        println("[ERROR] Could not bind admin server to port $port or ${port + 1}. Desktop kiosk will continue.")
        return false
    }

    private suspend fun ApplicationCall.checkAdminAuth(settingsRepository: SettingsRepository): Boolean {
        if (request.httpMethod == HttpMethod.Options) return true

        // 1. Check Bearer Token or Query Param
        val token = extractBearerToken() ?: request.queryParameters["token"]
        if (token != null) {
            val expiry = activeTokens[token]
            if (expiry != null && System.currentTimeMillis() < expiry) {
                return true
            }
        }

        // 2. Fallback check for legacy X-Admin-Password header
        val authPassword = request.headers["X-Admin-Password"]
        if (authPassword != null) {
            val settings = settingsRepository.getSettings()
            val expectedPassword = if (settings.adminPasswordHash.isNotBlank()) settings.adminPasswordHash else "admin"
            if (safeEquals(authPassword, expectedPassword)) {
                return true
            }
        }

        respond(HttpStatusCode.Unauthorized, "Unauthorized access")
        return false
    }

    private fun ApplicationCall.extractBearerToken(): String? {
        val authHeader = request.headers[HttpHeaders.Authorization] ?: return null
        return if (authHeader.startsWith("Bearer ", ignoreCase = true)) {
            authHeader.substring(7).trim()
        } else {
            null
        }
    }

    private fun getContentTypeForName(name: String): ContentType = when {
        name.endsWith(".html") -> ContentType.Text.Html
        name.endsWith(".js") -> ContentType.Text.JavaScript
        name.endsWith(".wasm") -> ContentType.Application.Wasm
        name.endsWith(".css") -> ContentType.Text.CSS
        name.endsWith(".png") -> ContentType.Image.PNG
        name.endsWith(".jpg") || name.endsWith(".jpeg") -> ContentType.Image.JPEG
        name.endsWith(".svg") -> ContentType.Image.SVG
        name.endsWith(".json") -> ContentType.Application.Json
        else -> ContentType.Application.OctetStream
    }

    private fun loadWasmResourceBytes(name: String): ByteArray? {
        val clean = name.trimStart('/')
        val stream = LojinhaAdminServer::class.java.getResourceAsStream("/wasm/$clean")
            ?: Thread.currentThread().contextClassLoader.getResourceAsStream("wasm/$clean")
            ?: LojinhaAdminServer::class.java.getResourceAsStream("/$clean")
        return stream?.use { it.readBytes() }
    }

    private fun findWasmAsset(name: String): File? {
        val candidatePaths = listOf(
            File("shared/build/dist/wasmJs/productionExecutable"),
            File("shared/build/dist/wasmJs/developmentExecutable"),
            File("build/dist/wasmJs/productionExecutable"),
            File("build/dist/wasmJs/developmentExecutable"),
            File("../shared/build/dist/wasmJs/productionExecutable"),
            File("../shared/build/dist/wasmJs/developmentExecutable")
        )
        for (dir in candidatePaths) {
            if (!dir.exists() || !dir.isDirectory) continue
            val file = File(dir, name)
            try {
                // Canonical path check strictly prevents directory traversal attacks
                val canonicalDirPath = dir.canonicalFile.toPath()
                val canonicalFilePath = file.canonicalFile.toPath()
                if (file.exists() && canonicalFilePath.startsWith(canonicalDirPath)) {
                    return file
                }
            } catch (e: Exception) {
                // Malformed path or security access violation
            }
        }
        return null
    }

    fun stop() {
        server?.stop(1000, 2000)
    }
}
