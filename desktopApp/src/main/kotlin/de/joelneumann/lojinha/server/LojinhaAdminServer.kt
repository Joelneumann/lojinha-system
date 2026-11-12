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
import de.joelneumann.lojinha.AppVersion
import de.joelneumann.lojinha.data.database.AppDatabase
import de.joelneumann.lojinha.data.repository.RoomVeggieOrderRepositoryImpl
import de.joelneumann.lojinha.data.service.VeggieImageService
import de.joelneumann.lojinha.data.service.VeggieOrderPdfGenerator
import de.joelneumann.lojinha.ui.utils.IsoWeekUtils
import de.joelneumann.lojinha.ui.utils.PdfFilenames
import de.joelneumann.lojinha.util.AppLogger
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPOutputStream

class LojinhaAdminServer(
    private val productRepository: ProductRepository,
    private val userRepository: UserRepository,
    private val transactionRepository: TransactionRepository,
    private val billingListRepository: BillingListRepository,
    private val settingsRepository: SettingsRepository,
    private val adminNoteRepository: AdminNoteRepository? = null,
    private val veggieOrderRepository: VeggieOrderRepository? = null,
    private val veggieImageService: VeggieImageService = VeggieImageService(),
    private val database: AppDatabase? = null,
    private val port: Int = 8080
) {
    private var server: EmbeddedServer<*, *>? = null

    private data class CustomerSession(
        val userId: String,
        val expiryTimestamp: Long
    )

    // Token Session Management
    private val activeTokens = ConcurrentHashMap<String, Long>() // token -> expiryTimestamp
    private val activeCustomerTokens = ConcurrentHashMap<String, CustomerSession>() // token -> CustomerSession
    private val failedLoginAttempts = ConcurrentHashMap<String, Pair<Int, Long>>() // ip -> (count, firstAttemptTime)
    private val SESSION_DURATION_MS = 8 * 60 * 60 * 1000L // 8 hours
    private val MAX_LOGIN_ATTEMPTS = 5
    private val RATE_LIMIT_WINDOW_MS = 60 * 1000L // 1 minute

    private fun resolveVeggieRepo(): VeggieOrderRepository? {
        return veggieOrderRepository ?: database?.let {
            RoomVeggieOrderRepositoryImpl(
                it.veggieOrderDao(),
                it.userDao(),
                it.transactionDao()
            ) { DataChangeNotifier.notifyDataChanged() }
        }
    }

    private fun safeEquals(a: String, b: String): Boolean {
        return MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
    }

    private data class CachedAsset(
        val lastModified: Long,
        val etag: String,
        val rawBytes: ByteArray,
        val gzippedBytes: ByteArray?
    )

    private val staticAssetCache = ConcurrentHashMap<String, CachedAsset>()

    private fun isCompressible(name: String): Boolean =
        name.endsWith(".wasm") || name.endsWith(".js") || name.endsWith(".html") ||
        name.endsWith(".css") || name.endsWith(".map") || name.endsWith(".json") ||
        name.endsWith(".svg")

    // Only content-hashed wasm chunks are safe for 1-year immutable caching.
    // Unhashed files (lojinha.js, composeResources/*, index.html, favicons) must use no-cache with ETag validation so updates take effect immediately.
    private fun isImmutableAsset(name: String): Boolean =
        name.endsWith(".wasm") && !name.startsWith("lojinha")

    private fun gzipBytes(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream(data.size / 2)
        GZIPOutputStream(bos).use { gz ->
            gz.write(data)
        }
        return bos.toByteArray()
    }

    private fun getOrLoadAsset(assetName: String): CachedAsset? {
        val cached = staticAssetCache[assetName]
        // Fast-path: Classpath assets (JAR) are immutable in memory; avoid filesystem stat calls
        if (cached != null && cached.lastModified == 0L) {
            return cached
        }

        val file = findWasmAsset(assetName)
        if (file != null && file.exists() && !file.isDirectory) {
            val fileModified = file.lastModified()
            if (cached != null && cached.lastModified == fileModified) {
                return cached
            }
            val rawData = try { file.readBytes() } catch (e: Exception) { return null }
            val etag = "W/\"${assetName.hashCode()}-${rawData.size}-${AppVersion.CURRENT}-$fileModified\""
            val gzFile = findWasmAsset("$assetName.gz") ?: File(file.parentFile, "${file.name}.gz")
            val gzippedData = if (isCompressible(assetName)) {
                if (gzFile.exists() && !gzFile.isDirectory) {
                    try { gzFile.readBytes() } catch (e: Exception) { gzipBytes(rawData) }
                } else {
                    gzipBytes(rawData)
                }
            } else null
            val newAsset = CachedAsset(fileModified, etag, rawData, gzippedData)
            staticAssetCache[assetName] = newAsset
            return newAsset
        }

        // Classpath resource (JAR)
        if (cached != null) {
            return cached
        }
        val rawData = loadWasmResourceBytes(assetName) ?: return null
        val etag = "W/\"${assetName.hashCode()}-${rawData.size}-${AppVersion.CURRENT}\""
        val gzData = if (isCompressible(assetName)) {
            loadWasmResourceBytes("$assetName.gz") ?: gzipBytes(rawData)
        } else null
        val newAsset = CachedAsset(0L, etag, rawData, gzData)
        staticAssetCache[assetName] = newAsset
        return newAsset
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
                    get("/products/{id}/history") {
                        if (!call.checkAdminAuth(settingsRepository)) return@get
                        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                        val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
                        val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 25
                        val result = productRepository.getProductHistory(id, page, pageSize)
                        call.respond(result)
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
                    get("/users/{id}/transactions-pdf") {
                        if (!call.checkAdminAuth(settingsRepository)) return@get
                        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                        val fromTimestamp = call.request.queryParameters["from"]?.toLongOrNull()
                        val toTimestamp = call.request.queryParameters["to"]?.toLongOrNull()
                        val langStr = call.request.queryParameters["lang"] ?: "BR"
                        val language = try {
                            Language.valueOf(langStr.uppercase())
                        } catch (_: Exception) {
                            Language.BR
                        }

                        val user = userRepository.getUserById(id)
                        if (user == null) {
                            call.respond(HttpStatusCode.NotFound, "User not found")
                            return@get
                        }

                        val txs = transactionRepository.getTransactionsByUserId(id)
                        try {
                            val pdfBytes = de.joelneumann.lojinha.data.service.TransactionPdfGenerator.generateStatementBytes(
                                user = user,
                                transactions = txs,
                                fromTimestamp = fromTimestamp,
                                toTimestamp = toTimestamp,
                                language = language
                            )
                            val filename = PdfFilenames.transactionStatementFilename(user.name, language)
                            call.response.header(
                                HttpHeaders.ContentDisposition,
                                ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, filename).toString()
                            )
                            call.respondBytes(pdfBytes, ContentType.Application.Pdf)
                        } catch (e: Exception) {
                            AppLogger.error("LojinhaAdminServer", "Failed to generate transactions PDF: ${e.message}", e)
                            call.respond(HttpStatusCode.InternalServerError, "Failed to generate transactions PDF")
                        }
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

                    // Admin Notes & Suggestions
                    get("/notes") {
                        if (!call.checkAdminAuth(settingsRepository)) return@get
                        val repo = adminNoteRepository ?: database?.let {
                            de.joelneumann.lojinha.data.repository.RoomAdminNoteRepositoryImpl(it.adminNoteDao()) { DataChangeNotifier.notifyDataChanged() }
                        }
                        if (repo == null) {
                            call.respond(emptyList<AdminNote>())
                            return@get
                        }
                        call.respond(repo.getAllNotes())
                    }
                    post("/notes") {
                        if (!call.checkAdminAuth(settingsRepository)) return@post
                        val note = call.receive<AdminNote>()
                        val cleanMessage = note.message.trim().take(1000)
                        if (cleanMessage.isBlank()) {
                            return@post call.respond(HttpStatusCode.BadRequest, "Note message cannot be blank")
                        }
                        val noteToSave = note.copy(
                            id = if (note.id.isBlank()) UUID.randomUUID().toString() else note.id,
                            message = cleanMessage,
                            timestamp = if (note.timestamp <= 0L) System.currentTimeMillis() else note.timestamp
                        )
                        val repo = adminNoteRepository ?: database?.let {
                            de.joelneumann.lojinha.data.repository.RoomAdminNoteRepositoryImpl(it.adminNoteDao()) { DataChangeNotifier.notifyDataChanged() }
                        }
                        if (repo != null) {
                            repo.addNote(noteToSave)
                        }
                        call.respond(HttpStatusCode.Created, noteToSave)
                    }
                    delete("/notes/{id}") {
                        if (!call.checkAdminAuth(settingsRepository)) return@delete
                        val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                        val repo = adminNoteRepository ?: database?.let {
                            de.joelneumann.lojinha.data.repository.RoomAdminNoteRepositoryImpl(it.adminNoteDao()) { DataChangeNotifier.notifyDataChanged() }
                        }
                        if (repo != null) {
                            repo.deleteNote(id)
                        }
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
                        call.streamDataChangeEvents()
                    }

                    // Veggie Order System Admin Endpoints
                    route("/veggie") {
                        get("/unit-types") {
                            if (!call.checkAdminAuth(settingsRepository)) return@get
                            val repo = resolveVeggieRepo() ?: return@get call.respond(emptyList<VeggieUnitType>())
                            call.respond(repo.getAllUnitTypes())
                        }
                        post("/unit-types") {
                            if (!call.checkAdminAuth(settingsRepository)) return@post
                            val repo = resolveVeggieRepo() ?: return@post call.respond(HttpStatusCode.ServiceUnavailable)
                            val unitType = call.receive<VeggieUnitType>()
                            if (unitType.name.isBlank()) {
                                call.respond(HttpStatusCode.BadRequest, "Unit type name cannot be blank")
                                return@post
                            }
                            repo.saveUnitType(unitType)
                            call.respond(HttpStatusCode.OK)
                        }

                        get("/products") {
                            if (!call.checkAdminAuth(settingsRepository)) return@get
                            val repo = resolveVeggieRepo() ?: return@get call.respond(emptyList<VeggieProduct>())
                            call.respond(repo.getAllVeggieProducts())
                        }
                        get("/order-form-pdf") {
                            if (!call.checkAdminAuth(settingsRepository)) return@get
                            val repo = resolveVeggieRepo() ?: return@get call.respond(HttpStatusCode.ServiceUnavailable)
                            val langStr = call.request.queryParameters["lang"] ?: "BR"
                            val language = try { Language.valueOf(langStr.uppercase()) } catch (_: Exception) { Language.BR }
                            try {
                                val products = repo.getAllVeggieProducts()
                                val unitTypes = repo.getAllUnitTypes().associateBy { it.id }
                                val pdfBytes = VeggieOrderPdfGenerator.generateCustomerOrderFormBytes(products, unitTypes, language)
                                val dateStamp = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                                val filename = PdfFilenames.customerOrderFormFilename(dateStamp, language)
                                call.response.header(
                                    HttpHeaders.ContentDisposition,
                                    ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, filename).toString()
                                )
                                call.respondBytes(pdfBytes, ContentType.Application.Pdf)
                            } catch (e: Exception) {
                                AppLogger.error("LojinhaAdminServer", "Failed to generate customer order form PDF: ${e.message}", e)
                                call.respond(HttpStatusCode.InternalServerError, "Failed to generate customer order form PDF")
                            }
                        }
                        post("/products") {
                            if (!call.checkAdminAuth(settingsRepository)) return@post
                            val repo = resolveVeggieRepo() ?: return@post call.respond(HttpStatusCode.ServiceUnavailable)
                            val product = call.receive<VeggieProduct>()
                            if (product.name.isBlank()) {
                                call.respond(HttpStatusCode.BadRequest, "Veggie product name cannot be blank")
                                return@post
                            }
                            val toSave = if (product.id.isBlank()) product.copy(id = UUID.randomUUID().toString()) else product
                            repo.saveVeggieProduct(toSave)
                            call.respond(HttpStatusCode.OK, toSave)
                        }
                        post("/products/deactivate/{id}") {
                            if (!call.checkAdminAuth(settingsRepository)) return@post
                            val repo = resolveVeggieRepo() ?: return@post call.respond(HttpStatusCode.ServiceUnavailable)
                            val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                            repo.deactivateVeggieProduct(id)
                            call.respond(HttpStatusCode.OK)
                        }
                        delete("/products/{id}") {
                            if (!call.checkAdminAuth(settingsRepository)) return@delete
                            val repo = resolveVeggieRepo() ?: return@delete call.respond(HttpStatusCode.ServiceUnavailable)
                            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                            val existing = repo.getVeggieProductById(id)
                            repo.hardDeleteVeggieProduct(id)
                            veggieImageService.deleteProductImages(existing?.imageFileName ?: id)
                            call.respond(HttpStatusCode.OK)
                        }
                        post("/products/{id}/image") {
                            if (!call.checkAdminAuth(settingsRepository)) return@post
                            val repo = resolveVeggieRepo() ?: return@post call.respond(HttpStatusCode.ServiceUnavailable)
                            val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                            val bytes = call.receive<ByteArray>()
                            if (bytes.isEmpty()) {
                                call.respond(HttpStatusCode.BadRequest, "Image payload is empty")
                                return@post
                            }
                            val existing = repo.getVeggieProductById(id)
                            val productName = call.request.queryParameters["name"] ?: existing?.name
                            val otherProductKeys = repo.getAllVeggieProducts()
                                .filter { it.id != id }
                                .mapNotNull { it.imageFileName }
                            val fileName = try {
                                veggieImageService.saveProductImage(
                                    productId = id,
                                    productName = productName,
                                    rawImageBytes = bytes,
                                    previousImageFileName = existing?.imageFileName,
                                    existingImageKeys = otherProductKeys
                                )
                            } catch (e: Exception) {
                                AppLogger.error("LojinhaAdminServer", "Failed to save product image for $id: ${e.message}", e)
                                call.respond(HttpStatusCode.BadRequest, "Invalid or unsupported image data")
                                return@post
                            }
                            if (existing != null) {
                                repo.saveVeggieProduct(
                                    existing.copy(
                                        imageFileName = fileName,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                )
                            }
                            DataChangeNotifier.notifyDataChanged()
                            call.respond(HttpStatusCode.OK, mapOf("imageFileName" to fileName))
                        }
                        delete("/products/{id}/image") {
                            if (!call.checkAdminAuth(settingsRepository)) return@delete
                            val repo = resolveVeggieRepo() ?: return@delete call.respond(HttpStatusCode.ServiceUnavailable)
                            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                            val existing = repo.getVeggieProductById(id)
                            veggieImageService.deleteProductImages(existing?.imageFileName ?: id)
                            if (existing != null && existing.imageFileName != null) {
                                repo.saveVeggieProduct(
                                    existing.copy(
                                        imageFileName = null,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                )
                            }
                            DataChangeNotifier.notifyDataChanged()
                            call.respond(HttpStatusCode.OK)
                        }

                        get("/rounds") {
                            if (!call.checkAdminAuth(settingsRepository)) return@get
                            val repo = resolveVeggieRepo() ?: return@get call.respond(emptyList<VeggieOrderRound>())
                            call.respond(repo.getAllRounds())
                        }
                        get("/rounds/active") {
                            if (!call.checkAdminAuth(settingsRepository)) return@get
                            val repo = resolveVeggieRepo() ?: return@get call.respond(HttpStatusCode.NoContent)
                            val round = repo.getActiveOrLatestRound()
                            if (round != null) {
                                call.respond(round)
                            } else {
                                call.respond(HttpStatusCode.NoContent)
                            }
                        }
                        get("/rounds/{id}") {
                            if (!call.checkAdminAuth(settingsRepository)) return@get
                            val repo = resolveVeggieRepo() ?: return@get call.respond(HttpStatusCode.NotFound)
                            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                            val round = repo.getRoundById(id)
                            if (round != null) {
                                call.respond(round)
                            } else {
                                call.respond(HttpStatusCode.NotFound)
                            }
                        }
                        post("/rounds/open") {
                            if (!call.checkAdminAuth(settingsRepository)) return@post
                            val repo = resolveVeggieRepo() ?: return@post call.respond(HttpStatusCode.ServiceUnavailable)
                            val req = call.receive<OpenVeggieRoundRequest>()
                            val openedAt = if (req.openedAt > 0L) req.openedAt else System.currentTimeMillis()
                            val round = repo.openNewRound(
                                buyerOperationFeeCents = req.buyerOperationFeeCents,
                                transportFeeCents = req.transportFeeCents,
                                openedAt = openedAt
                            )
                            call.respond(HttpStatusCode.OK, round)
                        }
                        post("/rounds/{id}/close") {
                            if (!call.checkAdminAuth(settingsRepository)) return@post
                            val repo = resolveVeggieRepo() ?: return@post call.respond(HttpStatusCode.ServiceUnavailable)
                            val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                            val req = try { call.receive<CloseVeggieRoundRequest>() } catch (_: Exception) { CloseVeggieRoundRequest() }
                            val closedAt = if (req.closedAt > 0L) req.closedAt else System.currentTimeMillis()
                            val ok = repo.closeRound(id, closedAt)
                            if (ok) {
                                call.respond(HttpStatusCode.OK)
                            } else {
                                call.respond(HttpStatusCode.BadRequest, "Failed to close round")
                            }
                        }
                        post("/rounds/{id}/reopen") {
                            if (!call.checkAdminAuth(settingsRepository)) return@post
                            val repo = resolveVeggieRepo() ?: return@post call.respond(HttpStatusCode.ServiceUnavailable)
                            val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                            val ok = repo.reopenRound(id)
                            if (ok) {
                                call.respond(HttpStatusCode.OK)
                            } else {
                                call.respond(HttpStatusCode.BadRequest, "Failed to reopen round")
                            }
                        }
                        get("/rounds/{id}/entries") {
                            if (!call.checkAdminAuth(settingsRepository)) return@get
                            val repo = resolveVeggieRepo() ?: return@get call.respond(emptyList<VeggieOrderEntry>())
                            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                            call.respond(repo.getEntriesForRound(id))
                        }
                        post("/rounds/{id}/entries") {
                            if (!call.checkAdminAuth(settingsRepository)) return@post
                            val repo = resolveVeggieRepo() ?: return@post call.respond(HttpStatusCode.ServiceUnavailable)
                            val entry = call.receive<VeggieOrderEntry>()
                            val ok = repo.upsertAdminOrderEntry(entry)
                            if (ok) {
                                call.respond(HttpStatusCode.OK)
                            } else {
                                call.respond(HttpStatusCode.BadRequest, "Failed to upsert order entry")
                            }
                        }
                        get("/rounds/{id}/settlements") {
                            if (!call.checkAdminAuth(settingsRepository)) return@get
                            val repo = resolveVeggieRepo() ?: return@get call.respond(emptyList<VeggieRoundProductSettlement>())
                            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                            call.respond(repo.getProductSettlementsForRound(id))
                        }
                        post("/rounds/{id}/draft-settlement") {
                            if (!call.checkAdminAuth(settingsRepository)) return@post
                            val repo = resolveVeggieRepo() ?: return@post call.respond(HttpStatusCode.ServiceUnavailable)
                            val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                            val req = call.receive<VeggieSettlementRequest>()
                            val ok = repo.saveDraftSettlement(
                                roundId = id,
                                buyerOperationFeeCents = req.buyerOperationFeeCents,
                                transportFeeCents = req.transportFeeCents,
                                transporterUserId = req.transporterUserId,
                                productSettlements = req.productSettlements,
                                actualQuantitiesByEntryId = req.actualQuantitiesByEntryId
                            )
                            if (ok) {
                                call.respond(HttpStatusCode.OK)
                            } else {
                                call.respond(HttpStatusCode.BadRequest, "Failed to save draft settlement")
                            }
                        }
                        post("/rounds/{id}/settle") {
                            if (!call.checkAdminAuth(settingsRepository)) return@post
                            val repo = resolveVeggieRepo() ?: return@post call.respond(HttpStatusCode.ServiceUnavailable)
                            val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                            val req = call.receive<VeggieSettlementRequest>()
                            val settledAt = if (req.settledAt > 0L) req.settledAt else System.currentTimeMillis()
                            val result = repo.executeRoundSettlement(
                                roundId = id,
                                buyerOperationFeeCents = req.buyerOperationFeeCents,
                                transportFeeCents = req.transportFeeCents,
                                transporterUserId = req.transporterUserId,
                                productSettlements = req.productSettlements,
                                actualQuantitiesByEntryId = req.actualQuantitiesByEntryId,
                                settledAt = settledAt
                            )
                            if (result != null) {
                                call.respond(HttpStatusCode.OK, result)
                            } else {
                                call.respond(HttpStatusCode.BadRequest, "Failed to execute round settlement")
                            }
                        }
                        get("/rounds/{id}/buyer-pdf") {
                            if (!call.checkAdminAuth(settingsRepository)) return@get
                            val repo = resolveVeggieRepo() ?: return@get call.respond(HttpStatusCode.ServiceUnavailable)
                            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                            val round = repo.getRoundById(id) ?: return@get call.respond(HttpStatusCode.NotFound)
                            val entries = repo.getEntriesForRound(id)
                            val langStr = call.request.queryParameters["lang"] ?: "BR"
                            val language = try { Language.valueOf(langStr.uppercase()) } catch (_: Exception) { Language.BR }
                            try {
                                val pdfBytes = VeggieOrderPdfGenerator.generateBuyerShoppingListBytes(round, entries, language)
                                repo.markBuyerPdfExported(id, System.currentTimeMillis())
                                val isoWeek = IsoWeekUtils.fromEpochMillis(round.openedAt)
                                val filename = PdfFilenames.buyerShoppingListFilename(isoWeek.weekOfYear, isoWeek.isoYear, language)
                                call.response.header(
                                    HttpHeaders.ContentDisposition,
                                    ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, filename).toString()
                                )
                                call.respondBytes(pdfBytes, ContentType.Application.Pdf)
                            } catch (e: Exception) {
                                AppLogger.error("LojinhaAdminServer", "Failed to generate buyer PDF: ${e.message}", e)
                                call.respond(HttpStatusCode.InternalServerError, "Failed to generate buyer PDF")
                            }
                        }
                        post("/rounds/{id}/buyer-pdf/exported") {
                            if (!call.checkAdminAuth(settingsRepository)) return@post
                            val repo = resolveVeggieRepo() ?: return@post call.respond(HttpStatusCode.ServiceUnavailable)
                            val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                            repo.markBuyerPdfExported(id, System.currentTimeMillis())
                            call.respond(HttpStatusCode.OK)
                        }
                        get("/rounds/{id}/worker-pdf") {
                            if (!call.checkAdminAuth(settingsRepository)) return@get
                            val repo = resolveVeggieRepo() ?: return@get call.respond(HttpStatusCode.ServiceUnavailable)
                            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                            val round = repo.getRoundById(id) ?: return@get call.respond(HttpStatusCode.NotFound)
                            val entries = repo.getEntriesForRound(id)
                            val langStr = call.request.queryParameters["lang"] ?: "BR"
                            val language = try { Language.valueOf(langStr.uppercase()) } catch (_: Exception) { Language.BR }
                            try {
                                val pdfBytes = VeggieOrderPdfGenerator.generateWorkerSortingMatrixBytes(round, entries, language)
                                repo.markWorkerPdfExported(id, System.currentTimeMillis())
                                val isoWeek = IsoWeekUtils.fromEpochMillis(round.openedAt)
                                val filename = PdfFilenames.workerSortingMatrixFilename(isoWeek.weekOfYear, isoWeek.isoYear, language)
                                call.response.header(
                                    HttpHeaders.ContentDisposition,
                                    ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, filename).toString()
                                )
                                call.respondBytes(pdfBytes, ContentType.Application.Pdf)
                            } catch (e: Exception) {
                                AppLogger.error("LojinhaAdminServer", "Failed to generate worker PDF: ${e.message}", e)
                                call.respond(HttpStatusCode.InternalServerError, "Failed to generate worker PDF")
                            }
                        }
                        post("/rounds/{id}/worker-pdf/exported") {
                            if (!call.checkAdminAuth(settingsRepository)) return@post
                            val repo = resolveVeggieRepo() ?: return@post call.respond(HttpStatusCode.ServiceUnavailable)
                            val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                            repo.markWorkerPdfExported(id, System.currentTimeMillis())
                            call.respond(HttpStatusCode.OK)
                        }
                        delete("/rounds/{id}") {
                            if (!call.checkAdminAuth(settingsRepository)) return@delete
                            val repo = resolveVeggieRepo() ?: return@delete call.respond(HttpStatusCode.ServiceUnavailable)
                            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                            val success = repo.deleteRound(id)
                            if (success) {
                                call.respond(HttpStatusCode.OK)
                            } else {
                                call.respond(HttpStatusCode.BadRequest, "Could not delete round (may have existing transactions or not found)")
                            }
                        }
                    }
                }

                // 2b. Public Veggie Product Image Endpoint
                get("/api/veggie/images/{id}") {
                    val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val isThumb = call.request.queryParameters["thumb"]?.lowercase() != "false"
                    val directFile = if (isThumb) veggieImageService.getThumbnailFile(id) else veggieImageService.getFullImageFile(id)
                    val imgFile: File? = directFile ?: run {
                        val product = resolveVeggieRepo()?.getVeggieProductById(id)
                        val key = product?.imageFileName
                        if (isThumb) veggieImageService.getThumbnailFile(key) else veggieImageService.getFullImageFile(key)
                    }
                    if (imgFile == null || !imgFile.exists()) {
                        call.respond(HttpStatusCode.NotFound)
                        return@get
                    }
                    val fileModified = imgFile.lastModified()
                    val fileSize = imgFile.length()
                    val etag = "W/\"veggie-img-$id-$isThumb-$fileModified-$fileSize\""
                    call.response.header(HttpHeaders.ETag, etag)
                    call.response.header(HttpHeaders.CacheControl, "public, max-age=3600")
                    val clientIfNoneMatch = call.request.headers[HttpHeaders.IfNoneMatch]
                    if (clientIfNoneMatch != null) {
                        val opaqueEtag = etag.removePrefix("W/").trim('"')
                        val clientTags = clientIfNoneMatch.split(',').map { it.trim().removePrefix("W/").trim('"') }
                        if (clientIfNoneMatch.trim() == "*" || clientTags.any { it == opaqueEtag }) {
                            call.respond(HttpStatusCode.NotModified)
                            return@get
                        }
                    }
                    call.respondBytes(imgFile.readBytes(), ContentType.Image.JPEG)
                }

                // 2c. Customer Network Portal Endpoints (/api/customer)
                route("/api/customer") {
                    get("/users") {
                        val activeUsers = userRepository.getAllUsers()
                            .filter { it.isActive && !it.isDeleted }
                            .map { u ->
                                u.copy(
                                    balance = 0L,
                                    pin = if (u.pin.isNullOrBlank()) null else "PROTECTED"
                                )
                            }
                        call.respond(activeUsers)
                    }

                    post("/login") {
                        val clientIp = call.request.local.remoteHost
                        val now = System.currentTimeMillis()

                        val currentAttempt = failedLoginAttempts[clientIp]
                        if (currentAttempt != null) {
                            val (count, firstAttempt) = currentAttempt
                            if (now - firstAttempt < RATE_LIMIT_WINDOW_MS && count >= MAX_LOGIN_ATTEMPTS) {
                                call.respond(
                                    HttpStatusCode.TooManyRequests,
                                    CustomerLoginResponse(false, "Too many failed login attempts. Please wait a minute before retrying.")
                                )
                                return@post
                            } else if (now - firstAttempt >= RATE_LIMIT_WINDOW_MS) {
                                failedLoginAttempts.remove(clientIp)
                            }
                        }

                        val req = call.receive<CustomerLoginRequest>()
                        val user = userRepository.getUserById(req.userId)
                        if (user == null || !user.isActive || user.isDeleted) {
                            call.respond(HttpStatusCode.OK, CustomerLoginResponse(false, "User not found"))
                            return@post
                        }
                        if (user.pin.isNullOrBlank()) {
                            call.respond(HttpStatusCode.OK, CustomerLoginResponse(false, "PIN required on network portal"))
                            return@post
                        }

                        val settings = settingsRepository.getSettings()
                        val expectedAdminPassword = if (settings.adminPasswordHash.isNotBlank()) settings.adminPasswordHash else "admin"
                        val cleanPin = req.pin.trim()
                        val matches = de.joelneumann.lojinha.security.PasswordHasher.verify(cleanPin, user.pin) ||
                            de.joelneumann.lojinha.security.PasswordHasher.verifyAdminBypass(cleanPin, expectedAdminPassword)

                        if (matches) {
                            failedLoginAttempts.remove(clientIp)
                            val token = UUID.randomUUID().toString()
                            activeCustomerTokens[token] = CustomerSession(user.id, now + SESSION_DURATION_MS)
                            call.respond(
                                HttpStatusCode.OK,
                                CustomerLoginResponse(
                                    success = true,
                                    message = "Authenticated",
                                    token = token,
                                    user = user.copy(pin = "PROTECTED")
                                )
                            )
                        } else {
                            failedLoginAttempts.compute(clientIp) { _, current ->
                                val count = current?.first ?: 0
                                val firstTime = current?.second ?: now
                                Pair(count + 1, firstTime)
                            }
                            call.respond(HttpStatusCode.OK, CustomerLoginResponse(false, "Invalid PIN"))
                        }
                    }

                    post("/logout") {
                        val token = call.extractBearerToken() ?: call.request.queryParameters["token"]
                        if (token != null) {
                            activeCustomerTokens.remove(token)
                        }
                        call.respond(HttpStatusCode.OK)
                    }

                    get("/me") {
                        val userId = call.requireCustomerUserId() ?: return@get
                        val user = userRepository.getUserById(userId)
                        if (user == null || !user.isActive || user.isDeleted) {
                            call.respond(HttpStatusCode.NotFound, "User not found")
                            return@get
                        }
                        call.respond(user.copy(pin = if (user.pin.isNullOrBlank()) null else "PROTECTED"))
                    }

                    post("/me/settings") {
                        val userId = call.requireCustomerUserId() ?: return@post
                        val user = userRepository.getUserById(userId)
                        if (user == null || !user.isActive || user.isDeleted) {
                            call.respond(HttpStatusCode.NotFound, "User not found")
                            return@post
                        }
                        val req = call.receive<CustomerSettingsUpdateRequest>()
                        val cleanNewPin = req.newPin?.trim()
                        val updatedPinHash = if (!cleanNewPin.isNullOrBlank()) {
                            if (de.joelneumann.lojinha.security.PasswordHasher.isHash(cleanNewPin)) {
                                cleanNewPin
                            } else {
                                de.joelneumann.lojinha.security.PasswordHasher.hash(cleanNewPin)
                            }
                        } else {
                            user.pin
                        }
                        val updatedUser = user.copy(
                            language = req.language,
                            secondaryCurrency = req.secondaryCurrency ?: user.secondaryCurrency,
                            avatar = req.avatar ?: user.avatar,
                            pin = updatedPinHash,
                            requirePinOnKiosk = req.requirePinOnKiosk
                        )
                        userRepository.saveUser(updatedUser)
                        call.respond(updatedUser.copy(pin = if (updatedUser.pin.isNullOrBlank()) null else "PROTECTED"))
                    }

                    get("/transactions") {
                        val userId = call.requireCustomerUserId() ?: return@get
                        val pageParam = call.request.queryParameters["page"]?.toIntOrNull()
                        val pageSizeParam = call.request.queryParameters["pageSize"]?.toIntOrNull()
                        val searchParam = call.request.queryParameters["search"]
                        val typeParam = call.request.queryParameters["type"]?.let {
                            try { TransactionType.valueOf(it) } catch (_: Exception) { null }
                        }
                        if (pageParam != null && pageSizeParam != null) {
                            val result = transactionRepository.getTransactionsByUserIdPaged(
                                userId = userId,
                                page = pageParam,
                                pageSize = pageSizeParam,
                                searchQuery = searchParam,
                                typeFilter = typeParam
                            )
                            call.respond(result)
                        } else {
                            call.respond(transactionRepository.getTransactionsByUserId(userId))
                        }
                    }

                    get("/transactions-pdf") {
                        val userId = call.requireCustomerUserId() ?: return@get
                        val fromTimestamp = call.request.queryParameters["from"]?.toLongOrNull()
                        val toTimestamp = call.request.queryParameters["to"]?.toLongOrNull()
                        val langStr = call.request.queryParameters["lang"] ?: "BR"
                        val language = try { Language.valueOf(langStr.uppercase()) } catch (_: Exception) { Language.BR }
                        val user = userRepository.getUserById(userId)
                        if (user == null) {
                            call.respond(HttpStatusCode.NotFound, "User not found")
                            return@get
                        }
                        val txs = transactionRepository.getTransactionsByUserId(userId)
                        try {
                            val pdfBytes = de.joelneumann.lojinha.data.service.TransactionPdfGenerator.generateStatementBytes(
                                user = user,
                                transactions = txs,
                                fromTimestamp = fromTimestamp,
                                toTimestamp = toTimestamp,
                                language = language
                            )
                            val filename = PdfFilenames.transactionStatementFilename(user.name, language)
                            call.response.header(
                                HttpHeaders.ContentDisposition,
                                ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, filename).toString()
                            )
                            call.respondBytes(pdfBytes, ContentType.Application.Pdf)
                        } catch (e: Exception) {
                            AppLogger.error("LojinhaAdminServer", "Failed to generate customer statement PDF: ${e.message}", e)
                            call.respond(HttpStatusCode.InternalServerError, "Failed to generate transactions PDF")
                        }
                    }

                    post("/notes") {
                        val userId = call.requireCustomerUserId() ?: return@post
                        val user = userRepository.getUserById(userId)
                        if (user == null) {
                            call.respond(HttpStatusCode.NotFound, "User not found")
                            return@post
                        }
                        val note = call.receive<AdminNote>()
                        val cleanMessage = note.message.trim().take(1000)
                        if (cleanMessage.isBlank()) {
                            return@post call.respond(HttpStatusCode.BadRequest, "Note message cannot be blank")
                        }
                        val noteToSave = AdminNote(
                            id = UUID.randomUUID().toString(),
                            message = cleanMessage,
                            userId = user.id,
                            userNameSnapshot = user.name,
                            timestamp = System.currentTimeMillis()
                        )
                        val repo = adminNoteRepository ?: database?.let {
                            de.joelneumann.lojinha.data.repository.RoomAdminNoteRepositoryImpl(it.adminNoteDao()) { DataChangeNotifier.notifyDataChanged() }
                        }
                        repo?.addNote(noteToSave)
                        call.respond(HttpStatusCode.Created, noteToSave)
                    }

                    get("/veggie/state") {
                        val userId = call.requireCustomerUserId() ?: return@get
                        val repo = resolveVeggieRepo()
                        if (repo == null) {
                            call.respond(CustomerVeggieStateResponse())
                            return@get
                        }
                        val unitTypes = repo.getAllUnitTypes().filter { it.isActive }
                        val activeRound = repo.getActiveOrLatestRound()
                        val currentEntries = if (activeRound != null) {
                            repo.getEntriesForUserInRound(activeRound.id, userId)
                        } else {
                            emptyList()
                        }
                        val orderedProductIds = currentEntries
                            .filter { it.orderedQuantityScaled > 0L }
                            .map { it.veggieProductId }
                            .toSet()
                        val products = repo.getAllVeggieProducts().filter {
                            !it.isDeleted && (it.isActive || it.id in orderedProductIds)
                        }
                        val previousQuantities = if (activeRound != null) {
                            repo.getPreviousRoundQuantitiesForUser(activeRound.id, userId)
                        } else {
                            emptyMap()
                        }
                        call.respond(
                            CustomerVeggieStateResponse(
                                unitTypes = unitTypes,
                                products = products,
                                activeRound = activeRound,
                                currentEntries = currentEntries,
                                previousRoundQuantities = previousQuantities
                            )
                        )
                    }

                    post("/veggie/order") {
                        val userId = call.requireCustomerUserId() ?: return@post
                        val user = userRepository.getUserById(userId)
                        if (user == null || !user.isActive || user.isDeleted) {
                            call.respond(HttpStatusCode.NotFound, "User not found")
                            return@post
                        }
                        val repo = resolveVeggieRepo() ?: return@post call.respond(HttpStatusCode.ServiceUnavailable)
                        val req = call.receive<SaveCustomerVeggieOrderRequest>()
                        val saved = repo.saveCustomerOrder(
                            roundId = req.roundId,
                            user = user,
                            productQuantitiesScaled = req.productQuantitiesScaled
                        )
                        if (saved) {
                            call.respond(HttpStatusCode.OK)
                        } else {
                            call.respond(HttpStatusCode.Conflict, "Order round is not open")
                        }
                    }

                    get("/veggie/history") {
                        val userId = call.requireCustomerUserId() ?: return@get
                        val repo = resolveVeggieRepo()
                        if (repo == null) {
                            call.respond(emptyList<CustomerVeggieRoundHistoryItem>())
                            return@get
                        }
                        call.respond(repo.getCustomerOrderHistory(userId))
                    }

                    get("/events") {
                        val userId = call.requireCustomerUserId() ?: return@get
                        if (userId.isNotBlank()) {
                            call.streamDataChangeEvents()
                        }
                    }
                }

                // 3. Serve static Wasm assets with HTTP Caching and Gzip Compression
                suspend fun io.ktor.server.application.ApplicationCall.respondWasmAsset(
                    assetName: String,
                    preloadedAsset: CachedAsset? = null
                ) {
                    val contentType = getContentTypeForName(assetName)
                    val compressible = isCompressible(assetName)
                    val acceptEncoding = request.headers[HttpHeaders.AcceptEncoding] ?: ""
                    val clientAcceptsGzip = acceptEncoding.contains("gzip", ignoreCase = true)

                    // RFC 9110: Vary header MUST be sent on all responses for content-negotiated resources
                    if (compressible) {
                        response.header(HttpHeaders.Vary, HttpHeaders.AcceptEncoding)
                    }

                    // Cache-Control headers: Immutable 1-year cache for hashed assets; no-cache for entry HTML/JS
                    if (isImmutableAsset(assetName)) {
                        response.header(HttpHeaders.CacheControl, "public, max-age=31536000, immutable")
                    } else {
                        response.header(HttpHeaders.CacheControl, "no-cache, must-revalidate")
                    }

                    val asset = preloadedAsset ?: getOrLoadAsset(assetName)
                    if (asset == null) {
                        if (assetName == "index.html") {
                            respond(HttpStatusCode.NotFound, "Admin web interface assets not found.")
                        } else {
                            respond(HttpStatusCode.NotFound)
                        }
                        return
                    }

                    response.header(HttpHeaders.ETag, asset.etag)

                    // RFC 9110 conditional request validation (weak comparison allows stripped W/ and quotes, comma-separated, wildcard)
                    val clientIfNoneMatch = request.headers[HttpHeaders.IfNoneMatch]
                    if (clientIfNoneMatch != null) {
                        val opaqueEtag = asset.etag.removePrefix("W/").trim('"')
                        val clientTags = clientIfNoneMatch.split(',').map { it.trim().removePrefix("W/").trim('"') }
                        if (clientIfNoneMatch.trim() == "*" || clientTags.any { it == opaqueEtag }) {
                            respond(HttpStatusCode.NotModified)
                            return
                        }
                    }

                    if (clientAcceptsGzip && asset.gzippedBytes != null) {
                        response.header(HttpHeaders.ContentEncoding, "gzip")
                        respondBytes(asset.gzippedBytes, contentType)
                    } else {
                        respondBytes(asset.rawBytes, contentType)
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

                    val asset = getOrLoadAsset(filename)
                    if (asset != null) {
                        call.respondWasmAsset(filename, asset)
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

    private suspend fun ApplicationCall.requireCustomerUserId(): String? {
        if (request.httpMethod == HttpMethod.Options) return ""
        val token = extractBearerToken() ?: request.queryParameters["token"]
        if (token != null) {
            val session = activeCustomerTokens[token]
            if (session != null) {
                if (System.currentTimeMillis() < session.expiryTimestamp) {
                    return session.userId
                } else {
                    activeCustomerTokens.remove(token)
                }
            }
        }
        respond(HttpStatusCode.Unauthorized, "Unauthorized customer access")
        return null
    }

    private suspend fun ApplicationCall.streamDataChangeEvents() {
        response.cacheControl(CacheControl.NoCache(null))
        respondTextWriter(contentType = ContentType.Text.EventStream) {
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
        return try {
            val stream = LojinhaAdminServer::class.java.getResourceAsStream("/wasm/$clean")
                ?: Thread.currentThread().contextClassLoader.getResourceAsStream("wasm/$clean")
            stream?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        }
    }

    private fun findWasmAsset(name: String): File? {
        val clean = name.trim('/')
        if (clean.isBlank()) return null
        val candidatePaths = listOf(
            File("desktopApp/build/generated/wasmResources/wasm"),
            File("build/generated/wasmResources/wasm"),
            File("shared/build/dist/wasmJs/productionExecutable"),
            File("build/dist/wasmJs/productionExecutable"),
            File("../shared/build/dist/wasmJs/productionExecutable"),
            File("shared/build/dist/wasmJs/developmentExecutable"),
            File("build/dist/wasmJs/developmentExecutable"),
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
