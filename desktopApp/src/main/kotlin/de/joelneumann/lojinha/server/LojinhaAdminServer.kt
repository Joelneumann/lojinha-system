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
import de.joelneumann.lojinha.data.database.AppDatabase
import de.joelneumann.lojinha.util.AppLogger
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
    private val database: AppDatabase? = null,
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
                            AppLogger.warn("LojinhaAdminServer", "Rate limit exceeded for IP: $clientIp")
                            call.respond(
                                HttpStatusCode.TooManyRequests,
                                LoginResponse(false, "Too many failed login attempts. Please wait a minute before retrying.")
                            )
                            return@post
                        } else if (now - firstAttempt >= RATE_LIMIT_WINDOW_MS) {
                            failedLoginAttempts.remove(clientIp)
                        }
                    }

                    val req = call.receive<LoginRequest>()
                    val settings = settingsRepository.getSettings()
                    val expectedPassword = if (settings.adminPasswordHash.isNotBlank()) settings.adminPasswordHash else "admin"
                    val matches = de.joelneumann.lojinha.security.PasswordHasher.verifyAdminBypass(req.password, expectedPassword)

                    AppLogger.info("LojinhaAdminServer", "Admin Login Attempt from $clientIp: success=$matches")
                    if (matches) {
                        failedLoginAttempts.remove(clientIp)
                        val token = UUID.randomUUID().toString()
                        activeTokens[token] = now + SESSION_DURATION_MS
                        call.respond(HttpStatusCode.OK, LoginResponse(true, "Authenticated", token))
                    } else {
                        failedLoginAttempts.compute(clientIp) { _, current ->
                            val count = current?.first ?: 0
                            val firstTime = current?.second ?: now
                            Pair(count + 1, firstTime)
                        }
                        call.respond(HttpStatusCode.OK, LoginResponse(false, "Invalid admin password"))
                    }
                }

                post("/api/admin/logout") {
                    val token = call.extractBearerToken() ?: call.request.queryParameters["token"]
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
                        if (product.name.isBlank()) {
                            call.respond(HttpStatusCode.BadRequest, "Product name cannot be blank")
                            return@post
                        }
                        if (product.basePrice < 0) {
                            call.respond(HttpStatusCode.BadRequest, "Product base price cannot be negative")
                            return@post
                        }
                        if (product.stockQuantity < 0) {
                            call.respond(HttpStatusCode.BadRequest, "Product stock cannot be negative")
                            return@post
                        }
                        val productToSave = if (product.id.isBlank()) product.copy(id = UUID.randomUUID().toString()) else product
                        productRepository.saveProduct(productToSave)
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
                        if (user.name.isBlank()) {
                            call.respond(HttpStatusCode.BadRequest, "User name cannot be blank")
                            return@post
                        }
                        userRepository.saveUser(user)
                        call.respond(HttpStatusCode.OK)
                    }
                    post("/users/balance") {
                        if (!call.checkAdminAuth(settingsRepository)) return@post
                        val req = call.receive<DeltaRequest>()
                        val user = userRepository.getUserById(req.id)
                        if (user == null) {
                            call.respond(HttpStatusCode.NotFound, "User not found")
                            return@post
                        }
                        if (req.delta != 0L) {
                            val isDeposit = req.delta > 0
                            val tx = Transaction(
                                id = UUID.randomUUID().toString(),
                                userId = user.id,
                                userNameSnapshot = user.name,
                                timestamp = System.currentTimeMillis(),
                                type = if (isDeposit) TransactionType.ADMIN_DEPOSIT else TransactionType.ADMIN_WITHDRAWAL,
                                note = if (isDeposit) "SYSNOTE|ADMIN_DEPOSIT" else "SYSNOTE|ADMIN_DEBIT",
                                totalAmount = req.delta,
                                items = emptyList()
                            )
                            transactionRepository.executeAtomicTransaction(
                                transaction = tx,
                                balanceDelta = req.delta,
                                stockDeltas = emptyMap()
                            )
                        }
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
                        val listToSave = list.copy(
                            id = idToSave,
                            users = list.users.map { u ->
                                u.copy(
                                    id = if (u.id.isBlank()) UUID.randomUUID().toString() else u.id,
                                    listId = idToSave
                                )
                            }
                        )
                        billingListRepository.saveBillingList(listToSave)
                        call.respond(HttpStatusCode.OK, mapOf("id" to idToSave))
                    }
                    delete("/billing-lists/{id}") {
                        if (!call.checkAdminAuth(settingsRepository)) return@delete
                        val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                        billingListRepository.deleteBillingList(id)
                        call.respond(HttpStatusCode.OK)
                    }
                    patch("/billing-lists/{id}/last-execution") {
                        if (!call.checkAdminAuth(settingsRepository)) return@patch
                        val id = call.parameters["id"] ?: return@patch call.respond(HttpStatusCode.BadRequest)
                        val body = call.receive<Map<String, Long?>>()
                        val timestamp = body["lastExecutionTime"]
                        billingListRepository.updateLastExecutionTime(id, timestamp)
                        call.respond(HttpStatusCode.OK)
                    }

                    // Diagnostics Support Bundle
                    get("/diagnostics/export") {
                        if (!call.checkAdminAuth(settingsRepository)) return@get
                        try {
                            val diagnosticsService = de.joelneumann.lojinha.data.service.DiagnosticsService(db = database)
                            val bundleZip = diagnosticsService.createSupportBundle()
                            call.response.header(
                                HttpHeaders.ContentDisposition,
                                ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, bundleZip.name).toString()
                            )
                            call.respondFile(bundleZip)
                        } catch (e: Exception) {
                            AppLogger.error("LojinhaAdminServer", "Failed to export diagnostic bundle: ${e.message}", e)
                            call.respond(HttpStatusCode.InternalServerError, "Failed to generate diagnostic bundle")
                        }
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
                                if (e is kotlinx.coroutines.CancellationException) throw e
                            } finally {
                                DataChangeNotifier.removeListener(listener)
                                channel.close()
                            }
                        }
                    }
                }

                // 3. Serve static Wasm assets
                suspend fun io.ktor.server.application.ApplicationCall.respondWasmAsset(assetName: String) {
                    val file = findWasmAsset(assetName)
                    if (file != null && file.exists() && !file.isDirectory) {
                        respondFile(file)
                        return
                    }
                    val resourceBytes = loadWasmResourceBytes(assetName)
                    if (resourceBytes != null) {
                        respondBytes(resourceBytes, getContentTypeForName(assetName))
                        return
                    }
                    if (assetName == "index.html") {
                        respond(HttpStatusCode.NotFound, "Admin web interface assets not found.")
                    } else {
                        respond(HttpStatusCode.NotFound)
                    }
                }

                get("/") {
                    call.respondWasmAsset("index.html")
                }

                get("/{filename...}") {
                    val rawFilename = call.parameters.getAll("filename")?.joinToString("/")?.trim('/')
                    val filename = if (rawFilename.isNullOrBlank()) "index.html" else rawFilename

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
                    val resourceBytes = if (filename.isNotBlank()) loadWasmResourceBytes(filename) else null

                    if (file != null || resourceBytes != null) {
                        call.respondWasmAsset(filename)
                    } else {
                        // Only fallback to index.html for root or clean single-segment SPA routes
                        val isSpaRoute = !filename.contains(".")
                        if (isSpaRoute) {
                            call.respondWasmAsset("index.html")
                        } else {
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
                AppLogger.info("LojinhaAdminServer", "Lojinha Embedded Admin Server active on http://0.0.0.0:$candidatePort")
                return true
            } catch (e: Exception) {
                AppLogger.warn("LojinhaAdminServer", "Failed to start admin server on port $candidatePort: ${e.message}", e)
            }
        }
        AppLogger.error("LojinhaAdminServer", "Could not bind admin server to port $port or ${port + 1}. Desktop kiosk will continue.")
        return false
    }

    private suspend fun ApplicationCall.checkAdminAuth(settingsRepository: SettingsRepository): Boolean {
        if (request.httpMethod == HttpMethod.Options) return true

        // 1. Check Bearer Token or Query Param
        val token = extractBearerToken() ?: request.queryParameters["token"]
        if (token != null) {
            val expiry = activeTokens[token]
            if (expiry != null) {
                if (System.currentTimeMillis() < expiry) {
                    return true
                } else {
                    activeTokens.remove(token)
                }
            }
        }

        // 2. Fallback check for legacy X-Admin-Password header
        val authPassword = request.headers["X-Admin-Password"]
        if (authPassword != null) {
            val settings = settingsRepository.getSettings()
            val expectedPassword = if (settings.adminPasswordHash.isNotBlank()) settings.adminPasswordHash else "admin"
            if (de.joelneumann.lojinha.security.PasswordHasher.verifyAdminBypass(authPassword, expectedPassword)) {
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
        name.endsWith(".json") || name.endsWith(".map") -> ContentType.Application.Json
        name.endsWith(".ico") -> ContentType("image", "x-icon")
        else -> ContentType.Application.OctetStream
    }

    private fun loadWasmResourceBytes(name: String): ByteArray? {
        val clean = name.trim('/')
        if (clean.isBlank()) return null
        val stream = LojinhaAdminServer::class.java.getResourceAsStream("/wasm/$clean")
            ?: Thread.currentThread().contextClassLoader.getResourceAsStream("wasm/$clean")
        return stream?.use { it.readBytes() }
    }

    private fun findWasmAsset(name: String): File? {
        val clean = name.trim('/')
        if (clean.isBlank()) return null
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
            val file = File(dir, clean)
            try {
                // Canonical path check strictly prevents directory traversal attacks
                val canonicalDirPath = dir.canonicalFile.toPath()
                val canonicalFilePath = file.canonicalFile.toPath()
                if (file.exists() && !file.isDirectory && canonicalFilePath.startsWith(canonicalDirPath)) {
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
