package de.joelneumann.lojinha.server

import de.joelneumann.lojinha.data.database.DatabaseFactory
import de.joelneumann.lojinha.data.service.DataChangeNotifier
import de.joelneumann.lojinha.domain.model.*
import de.joelneumann.lojinha.domain.repository.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import de.joelneumann.lojinha.AppVersion
import de.joelneumann.lojinha.data.database.AppDatabase
import de.joelneumann.lojinha.data.repository.RoomShoppingListRepositoryImpl
import de.joelneumann.lojinha.data.repository.RoomVeggieOrderRepositoryImpl
import de.joelneumann.lojinha.data.service.ShoppingListPdfGenerator
import de.joelneumann.lojinha.data.service.VeggieImageService
import de.joelneumann.lojinha.data.service.VeggieOrderPdfGenerator
import de.joelneumann.lojinha.ui.utils.IsoWeekUtils
import de.joelneumann.lojinha.ui.utils.PdfFilenames
import de.joelneumann.lojinha.ui.utils.isReservedAdminName
import de.joelneumann.lojinha.ui.utils.normalizeUserName
import de.joelneumann.lojinha.util.AppLogger
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPOutputStream

class LojinhaAdminServer(
    private val productRepository: ProductRepository,
    private val userRepository: UserRepository,
    private val transactionRepository: TransactionRepository,
    private val billingListRepository: BillingListRepository,
    private val settingsRepository: SettingsRepository,
    private val adminNoteRepository: AdminNoteRepository? = null,
    private val shoppingListRepository: ShoppingListRepository? = null,
    private val veggieOrderRepository: VeggieOrderRepository? = null,
    private val veggieImageService: VeggieImageService = VeggieImageService(),
    private val database: AppDatabase? = null,
    private val port: Int = 8080,
    private val sessionStoreFile: File? = File(System.getProperty("user.home"), ".lojinha/customer_sessions.json")
) {
    private var server: EmbeddedServer<*, *>? = null

    private data class CustomerSession(
        val userId: String,
        val expiryTimestamp: Long,
        val keepLoggedIn: Boolean = false
    )

    @kotlinx.serialization.Serializable
    private data class PersistedCustomerSession(
        val tokenHash: String,
        val userId: String,
        val expiryTimestamp: Long,
        val keepLoggedIn: Boolean = true
    )

    private data class LoginAttemptState(
        val failures: Int,
        val lastFailureAt: Long,
        val lockedUntil: Long = 0L
    )

    // Token Session Management (keyed by SHA-256(token) so raw tokens are never stored in memory or on disk)
    private val activeTokens = ConcurrentHashMap<String, Long>() // tokenHash -> expiryTimestamp
    private val activeCustomerTokens = ConcurrentHashMap<String, CustomerSession>() // tokenHash -> CustomerSession
    private val sessionStoreLock = Any()
    private val sessionJson = Json { ignoreUnknownKeys = true }
    private val failedLoginAttempts = ConcurrentHashMap<String, LoginAttemptState>() // "ip:..." or "account:..." -> LoginAttemptState
    private val ADMIN_COOKIE_NAME = "lojinha_admin_session"
    private val CUSTOMER_COOKIE_NAME = "lojinha_customer_session"
    private val SESSION_DURATION_MS = 8 * 60 * 60 * 1000L // 8 hours
    private val PERSISTENT_CUSTOMER_SESSION_DURATION_MS = 90L * 24 * 60 * 60 * 1000L // 90 days
    private val SLIDING_RENEWAL_PERSIST_INTERVAL_MS = 60 * 60 * 1000L // 1 hour
    private val MAX_LOGIN_ATTEMPTS_PER_TIER = 5
    private val RATE_LIMIT_RESET_MS = 30 * 60 * 1000L // 30 minutes idle resets failure counter
    private val MAX_RATE_LIMIT_ENTRIES = 10_000
    private val MAX_SSE_CONNECTIONS = 100
    private val MAX_IMAGE_UPLOAD_BYTES = 15L * 1024 * 1024
    private val activeSseConnections = AtomicInteger(0)

    init {
        loadPersistedCustomerSessions()
    }

    private fun hashToken(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun loadPersistedCustomerSessions() {
        val file = sessionStoreFile ?: return
        synchronized(sessionStoreLock) {
            if (!file.exists()) return
            try {
                val now = System.currentTimeMillis()
                val loaded = sessionJson.decodeFromString<List<PersistedCustomerSession>>(file.readText())
                var pruned = false
                loaded.forEach { entry ->
                    if (entry.keepLoggedIn && now < entry.expiryTimestamp && entry.tokenHash.isNotBlank() && entry.userId.isNotBlank()) {
                        activeCustomerTokens[entry.tokenHash] = CustomerSession(
                            userId = entry.userId,
                            expiryTimestamp = entry.expiryTimestamp,
                            keepLoggedIn = true
                        )
                    } else {
                        pruned = true
                    }
                }
                if (pruned) {
                    savePersistedCustomerSessionsLocked(file)
                }
            } catch (e: Exception) {
                AppLogger.warn("LojinhaAdminServer", "Failed to load persisted customer sessions: ${e.message}")
            }
        }
    }

    private fun savePersistedCustomerSessions() {
        val file = sessionStoreFile ?: return
        synchronized(sessionStoreLock) {
            savePersistedCustomerSessionsLocked(file)
        }
    }

    private fun savePersistedCustomerSessionsLocked(file: File) {
        try {
            val now = System.currentTimeMillis()
            val validEntries = activeCustomerTokens.entries
                .filter { it.value.keepLoggedIn && now < it.value.expiryTimestamp }
                .map {
                    PersistedCustomerSession(
                        tokenHash = it.key,
                        userId = it.value.userId,
                        expiryTimestamp = it.value.expiryTimestamp,
                        keepLoggedIn = true
                    )
                }
            file.parentFile?.mkdirs()
            val tmpFile = File(file.parentFile, "${file.name}.tmp")
            tmpFile.writeText(sessionJson.encodeToString(validEntries))
            java.nio.file.Files.move(
                tmpFile.toPath(),
                file.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            )
        } catch (e: Exception) {
            AppLogger.warn("LojinhaAdminServer", "Failed to save customer sessions: ${e.message}")
        }
    }

    private fun revokeCustomerSessions(userId: String) {
        val removed = activeCustomerTokens.entries.removeIf { it.value.userId == userId }
        if (removed) {
            savePersistedCustomerSessions()
        }
    }

    private fun checkLockoutRemainingMs(key: String, now: Long): Long? {
        val state = failedLoginAttempts[key] ?: return null
        if (now < state.lockedUntil) {
            return state.lockedUntil - now
        }
        if (now - state.lastFailureAt >= RATE_LIMIT_RESET_MS) {
            failedLoginAttempts.remove(key, state)
        }
        return null
    }

    private fun recordFailedLogin(key: String, now: Long) {
        if (failedLoginAttempts.size >= MAX_RATE_LIMIT_ENTRIES) {
            failedLoginAttempts.entries.removeIf { (_, state) ->
                now >= state.lockedUntil && now - state.lastFailureAt >= RATE_LIMIT_RESET_MS
            }
            if (failedLoginAttempts.size >= MAX_RATE_LIMIT_ENTRIES) {
                val overflow = failedLoginAttempts.size - MAX_RATE_LIMIT_ENTRIES + 1
                failedLoginAttempts.entries
                    .sortedBy { it.value.lastFailureAt }
                    .take(overflow)
                    .forEach { failedLoginAttempts.remove(it.key, it.value) }
            }
        }
        failedLoginAttempts.compute(key) { _, current ->
            val baseFailures = if (current == null || (now >= current.lockedUntil && now - current.lastFailureAt >= RATE_LIMIT_RESET_MS)) {
                0
            } else {
                current.failures
            }
            val nextFailures = baseFailures + 1
            val lockoutMs = when {
                nextFailures >= MAX_LOGIN_ATTEMPTS_PER_TIER * 3 -> 15 * 60 * 1000L
                nextFailures >= MAX_LOGIN_ATTEMPTS_PER_TIER * 2 && nextFailures % MAX_LOGIN_ATTEMPTS_PER_TIER == 0 -> 5 * 60 * 1000L
                nextFailures >= MAX_LOGIN_ATTEMPTS_PER_TIER && nextFailures % MAX_LOGIN_ATTEMPTS_PER_TIER == 0 -> 1 * 60 * 1000L
                else -> 0L
            }
            LoginAttemptState(
                failures = nextFailures,
                lastFailureAt = now,
                lockedUntil = if (lockoutMs > 0L) now + lockoutMs else 0L
            )
        }
    }

    private val fallbackVeggieRepo: VeggieOrderRepository? by lazy {
        database?.let { db ->
            RoomVeggieOrderRepositoryImpl(
                veggieOrderDao = db.veggieOrderDao(),
                userDao = db.userDao(),
                transactionDao = db.transactionDao(),
                onDataChanged = { DataChangeNotifier.notifyDataChanged() },
                onUserScopedDataChanged = { userId ->
                    DataChangeNotifier.notifyScopedDataChanged(DataChangeNotifier.ChangeScope(targetUserId = userId))
                },
                onAdminOnlyDataChanged = {
                    DataChangeNotifier.notifyScopedDataChanged(DataChangeNotifier.ChangeScope(adminOnly = true))
                },
                runInTransaction = { block ->
                    DatabaseFactory.runImmediateTransaction(db, block)
                }
            )
        }
    }

    private fun resolveVeggieRepo(): VeggieOrderRepository? {
        return veggieOrderRepository ?: fallbackVeggieRepo
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
            install(createApplicationPlugin("SecurityHeaders") {
                onCallRespond { call, _ ->
                    if (!call.response.headers.contains("X-Content-Type-Options")) {
                        call.response.header("X-Content-Type-Options", "nosniff")
                    }
                    if (!call.response.headers.contains("X-Frame-Options")) {
                        call.response.header("X-Frame-Options", "DENY")
                    }
                    if (!call.response.headers.contains("Referrer-Policy")) {
                        call.response.header("Referrer-Policy", "no-referrer")
                    }
                    if (!call.response.headers.contains("Content-Security-Policy")) {
                        call.response.header(
                            "Content-Security-Policy",
                            "default-src 'self'; script-src 'self' 'unsafe-inline' 'wasm-unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; connect-src 'self'; worker-src 'self'; manifest-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'self'"
                        )
                    }
                    if (call.isHttpsRequest() && !call.response.headers.contains("Strict-Transport-Security")) {
                        call.response.header("Strict-Transport-Security", "max-age=31536000")
                    }
                }
            })

            routing {
                // Lightweight unauthenticated health check for Service Worker & live reconnect polling
                get("/api/health") {
                    call.response.header(HttpHeaders.CacheControl, "no-store, no-cache, must-revalidate")
                    call.respondText("""{"status":"ok"}""", ContentType.Application.Json, HttpStatusCode.OK)
                }

                // Lightweight session role discovery so the Wasm app avoids probing protected routes on startup
                get("/api/session") {
                    call.response.header(HttpHeaders.CacheControl, "no-store, no-cache, must-revalidate")
                    call.respond(SessionStatusResponse(role = call.resolveSessionRole()))
                }

                // 1. Unified Public Login & Admin Logout Endpoints
                post("/api/login") {
                    val clientIp = call.resolveClientIp()
                    val now = System.currentTimeMillis()
                    val req = try {
                        call.receive<UnifiedLoginRequest>()
                    } catch (_: Exception) {
                        call.respond(HttpStatusCode.BadRequest, UnifiedLoginResponse(false, message = "Invalid request body"))
                        return@post
                    }
                    val normalizedIdentifier = req.identifier.normalizeUserName()
                    val ipKey = "ip:$clientIp"
                    val accountKey = if (normalizedIdentifier.isNotBlank()) "account:$normalizedIdentifier" else null

                    val ipLockMs = checkLockoutRemainingMs(ipKey, now)
                    val accountLockMs = accountKey?.let { checkLockoutRemainingMs(it, now) }
                    val remainingLockMs = listOfNotNull(ipLockMs, accountLockMs).maxOrNull()
                    if (remainingLockMs != null && remainingLockMs > 0L) {
                        val retryAfterSeconds = kotlin.math.max(1L, kotlin.math.ceil(remainingLockMs / 1000.0).toLong())
                        AppLogger.warn("LojinhaAdminServer", "Login rate limit exceeded for IP=$clientIp, identifier='$normalizedIdentifier'")
                        call.response.header(HttpHeaders.RetryAfter, retryAfterSeconds.toString())
                        call.respond(
                            HttpStatusCode.TooManyRequests,
                            UnifiedLoginResponse(false, message = "Too many failed login attempts. Please wait before retrying.")
                        )
                        return@post
                    }

                    val cleanPassword = req.password.trim()
                    val settings = settingsRepository.getSettings()
                    val defaultAdminHash = de.joelneumann.lojinha.security.PasswordHasher.hash("admin")
                    val hasCustomAdminHash = settings.adminPasswordHash.isNotBlank() &&
                        !safeEquals(settings.adminPasswordHash.lowercase(), defaultAdminHash)

                    if (normalizedIdentifier == "admin") {
                        val matches = hasCustomAdminHash &&
                            cleanPassword.isNotBlank() &&
                            de.joelneumann.lojinha.security.PasswordHasher.verify(cleanPassword, settings.adminPasswordHash)

                        if (!hasCustomAdminHash) {
                            AppLogger.warn("LojinhaAdminServer", "Admin web login rejected: custom admin password is not configured.")
                        } else {
                            AppLogger.info("LojinhaAdminServer", "Admin Login Attempt from $clientIp: success=$matches")
                        }

                        if (matches) {
                            failedLoginAttempts.remove(ipKey)
                            accountKey?.let { failedLoginAttempts.remove(it) }
                            activeTokens.entries.removeIf { now >= it.value }
                            val token = UUID.randomUUID().toString()
                            activeTokens[hashToken(token)] = now + SESSION_DURATION_MS
                            val cookieMaxAgeSec = if (req.keepLoggedIn) (SESSION_DURATION_MS / 1000L).toInt() else null
                            call.setSessionCookie(ADMIN_COOKIE_NAME, token, cookieMaxAgeSec)
                            call.respond(HttpStatusCode.OK, UnifiedLoginResponse(success = true, role = "ADMIN", message = "Authenticated"))
                        } else {
                            recordFailedLogin(ipKey, now)
                            accountKey?.let { recordFailedLogin(it, now) }
                            call.respond(HttpStatusCode.Unauthorized, UnifiedLoginResponse(success = false, message = "Invalid credentials"))
                        }
                        return@post
                    }

                    val matchingUsers = if (normalizedIdentifier.isBlank()) {
                        emptyList()
                    } else {
                        userRepository.getAllUsers().filter {
                            it.isActive && !it.isDeleted && (
                                it.name.normalizeUserName() == normalizedIdentifier ||
                                    it.nickname?.normalizeUserName() == normalizedIdentifier
                            )
                        }
                    }
                    val targetUser = if (matchingUsers.size == 1) matchingUsers.first() else null
                    val userMatches = targetUser != null &&
                        !targetUser.pin.isNullOrBlank() &&
                        cleanPassword.isNotBlank() &&
                        (
                            de.joelneumann.lojinha.security.PasswordHasher.verify(cleanPassword, targetUser.pin) ||
                                (hasCustomAdminHash &&
                                    de.joelneumann.lojinha.security.PasswordHasher.verify(cleanPassword, settings.adminPasswordHash))
                        )

                    if (userMatches) {
                        failedLoginAttempts.remove(ipKey)
                        accountKey?.let { failedLoginAttempts.remove(it) }
                        val token = UUID.randomUUID().toString()
                        val sessionTtl = if (req.keepLoggedIn) PERSISTENT_CUSTOMER_SESSION_DURATION_MS else SESSION_DURATION_MS
                        activeCustomerTokens[hashToken(token)] = CustomerSession(
                            userId = targetUser.id,
                            expiryTimestamp = now + sessionTtl,
                            keepLoggedIn = req.keepLoggedIn
                        )
                        if (req.keepLoggedIn) {
                            savePersistedCustomerSessions()
                        }
                        val cookieMaxAgeSec = if (req.keepLoggedIn) (PERSISTENT_CUSTOMER_SESSION_DURATION_MS / 1000L).toInt() else null
                        call.setSessionCookie(CUSTOMER_COOKIE_NAME, token, cookieMaxAgeSec)
                        AppLogger.info("LojinhaAdminServer", "Customer Login Attempt from $clientIp for '${targetUser.name}': success=true")
                        call.respond(HttpStatusCode.OK, UnifiedLoginResponse(success = true, role = "CUSTOMER", message = "Authenticated"))
                    } else {
                        recordFailedLogin(ipKey, now)
                        accountKey?.let { recordFailedLogin(it, now) }
                        AppLogger.info("LojinhaAdminServer", "Customer Login Attempt from $clientIp: success=false")
                        call.respond(HttpStatusCode.Unauthorized, UnifiedLoginResponse(success = false, message = "Invalid credentials"))
                    }
                }

                post("/api/admin/logout") {
                    val token = call.request.cookies[ADMIN_COOKIE_NAME]
                    if (!token.isNullOrBlank()) {
                        activeTokens.remove(hashToken(token))
                    }
                    call.clearSessionCookie(ADMIN_COOKIE_NAME)
                    call.respond(HttpStatusCode.OK)
                }

                // 2. Protected Admin API Endpoints
                route("/api/admin") {
                    // Read-only non-sensitive display/business settings for Web Admin parity
                    get("/ui-settings") {
                        if (!call.checkAdminAuth(settingsRepository)) return@get
                        call.respond(
                            settingsRepository.getSettings().copy(
                                adminPasswordHash = "",
                                oneDriveRefreshToken = null,
                                backupLocationPath = ""
                            )
                        )
                    }

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
                        val incoming = call.receive<User>()
                        val trimmedNickname = incoming.nickname?.trim()?.ifBlank { null }
                        val trimmedUser = incoming.copy(name = incoming.name.trim(), nickname = trimmedNickname)
                        if (trimmedUser.name.isBlank()) {
                            call.respond(HttpStatusCode.BadRequest, "User name cannot be blank")
                            return@post
                        }
                        if (trimmedUser.name.isReservedAdminName()) {
                            call.respond(HttpStatusCode.BadRequest, "The name \"Admin\" is reserved for administrator login")
                            return@post
                        }
                        if (trimmedNickname != null) {
                            if (trimmedNickname.length > MAX_USER_NICKNAME_LENGTH) {
                                call.respond(HttpStatusCode.BadRequest, "Nickname cannot exceed $MAX_USER_NICKNAME_LENGTH characters")
                                return@post
                            }
                            if (trimmedNickname.isReservedAdminName()) {
                                call.respond(HttpStatusCode.BadRequest, "The nickname \"Admin\" is reserved for administrator login")
                                return@post
                            }
                        }
                        val allUsers = userRepository.getAllUsers()
                        val normalized = trimmedUser.name.normalizeUserName()
                        val duplicate = allUsers.firstOrNull {
                            it.id != trimmedUser.id && !it.isDeleted && (
                                it.name.normalizeUserName() == normalized ||
                                    it.nickname?.normalizeUserName() == normalized
                            )
                        }
                        if (duplicate != null) {
                            call.respond(HttpStatusCode.BadRequest, "A user or nickname matching \"${trimmedUser.name}\" already exists")
                            return@post
                        }
                        val normalizedNick = trimmedNickname?.normalizeUserName()
                        if (normalizedNick != null) {
                            val duplicateNick = allUsers.firstOrNull {
                                it.id != trimmedUser.id && !it.isDeleted && (
                                    it.name.normalizeUserName() == normalizedNick ||
                                        it.nickname?.normalizeUserName() == normalizedNick
                                )
                            }
                            if (duplicateNick != null) {
                                call.respond(HttpStatusCode.BadRequest, "A user or nickname matching \"$trimmedNickname\" already exists")
                                return@post
                            }
                        }
                        val previousUser = allUsers.find { it.id == trimmedUser.id }
                        userRepository.saveUser(trimmedUser)
                        if (previousUser != null && (previousUser.pin != trimmedUser.pin || !trimmedUser.isActive || trimmedUser.isDeleted)) {
                            revokeCustomerSessions(trimmedUser.id)
                        }
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
                        revokeCustomerSessions(id)
                        call.respond(HttpStatusCode.OK)
                    }
                    post("/users/soft-delete/{id}") {
                        if (!call.checkAdminAuth(settingsRepository)) return@post
                        val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                        userRepository.softDeleteUser(id)
                        revokeCustomerSessions(id)
                        call.respond(HttpStatusCode.OK)
                    }
                    post("/users/restore/{id}") {
                        if (!call.checkAdminAuth(settingsRepository)) return@post
                        val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                        val allUsers = userRepository.getAllUsers()
                        val target = allUsers.find { it.id == id }
                        if (target != null) {
                            if (target.name.isReservedAdminName()) {
                                call.respond(HttpStatusCode.BadRequest, "The name \"Admin\" is reserved for administrator login")
                                return@post
                            }
                            val normalized = target.name.normalizeUserName()
                            val duplicate = allUsers.firstOrNull {
                                it.id != id && !it.isDeleted && (
                                    it.name.normalizeUserName() == normalized ||
                                        it.nickname?.normalizeUserName() == normalized
                                )
                            }
                            if (duplicate != null) {
                                call.respond(HttpStatusCode.BadRequest, "A user or nickname matching \"${target.name}\" already exists")
                                return@post
                            }
                            val normalizedNick = target.nickname?.normalizeUserName()
                            if (normalizedNick != null) {
                                if (target.nickname?.isReservedAdminName() == true) {
                                    call.respond(HttpStatusCode.BadRequest, "The nickname \"Admin\" is reserved for administrator login")
                                    return@post
                                }
                                val duplicateNick = allUsers.firstOrNull {
                                    it.id != id && !it.isDeleted && (
                                        it.name.normalizeUserName() == normalizedNick ||
                                            it.nickname?.normalizeUserName() == normalizedNick
                                    )
                                }
                                if (duplicateNick != null) {
                                    call.respond(HttpStatusCode.BadRequest, "A user or nickname matching \"${target.nickname}\" already exists")
                                    return@post
                                }
                            }
                        }
                        userRepository.restoreUser(id)
                        call.respond(HttpStatusCode.OK)
                    }
                    delete("/users/{id}") {
                        if (!call.checkAdminAuth(settingsRepository)) return@delete
                        val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                        userRepository.hardDeleteUser(id)
                        revokeCustomerSessions(id)
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
                        val language = call.parseLanguageParam()

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
                            call.respondPdf(pdfBytes, filename)
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
                        } else if (!userIdParam.isNullOrBlank()) {
                            call.respond(transactionRepository.getTransactionsByUserId(userIdParam))
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

                    // Admin Shopping List
                    route("/shopping-list") {
                        fun resolveShoppingListRepo(): ShoppingListRepository? =
                            shoppingListRepository ?: database?.let {
                                RoomShoppingListRepositoryImpl(it.shoppingListItemDao()) {
                                    DataChangeNotifier.notifyScopedDataChanged(
                                        DataChangeNotifier.ChangeScope(adminOnly = true)
                                    )
                                }
                            }

                        suspend fun requireRepo(call: ApplicationCall): ShoppingListRepository? {
                            val repo = resolveShoppingListRepo()
                            if (repo == null) {
                                AppLogger.warn("LojinhaAdminServer", "Shopping list repository unavailable for ${call.request.httpMethod.value} ${call.request.uri}")
                                call.respond(HttpStatusCode.ServiceUnavailable, "Shopping list service unavailable")
                            }
                            return repo
                        }

                        suspend fun executeRoute(call: ApplicationCall, action: String, block: suspend () -> Unit) {
                            try {
                                block()
                            } catch (e: io.ktor.server.plugins.CannotTransformContentToTypeException) {
                                AppLogger.warn("LojinhaAdminServer", "Invalid request body for shopping list $action: ${e.message}")
                                call.respond(HttpStatusCode.BadRequest, "Invalid shopping list payload")
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                AppLogger.error("LojinhaAdminServer", "Shopping list $action failed: ${e.message}", e)
                                call.respond(HttpStatusCode.InternalServerError, "Failed to $action")
                            }
                        }

                        get {
                            if (!call.checkAdminAuth(settingsRepository)) return@get
                            executeRoute(call, "list items") {
                                val repo = resolveShoppingListRepo()
                                if (repo == null) {
                                    AppLogger.warn("LojinhaAdminServer", "Shopping list repository unavailable for GET /api/admin/shopping-list; returning empty list")
                                    call.respond(emptyList<ShoppingListItem>())
                                    return@executeRoute
                                }
                                call.respond(repo.getAllItems())
                            }
                        }

                        post {
                            if (!call.checkAdminAuth(settingsRepository)) return@post
                            executeRoute(call, "add or update item") {
                                val repo = requireRepo(call) ?: return@executeRoute
                                val item = call.receive<ShoppingListItem>()
                                if (item.name.isBlank() || item.quantity.isBlank()) {
                                    AppLogger.warn("LojinhaAdminServer", "Rejected shopping list POST: blank name or quantity")
                                    return@executeRoute call.respond(HttpStatusCode.BadRequest, "Name and quantity cannot be blank")
                                }
                                repo.addOrUpdateItem(item)
                                call.respond(HttpStatusCode.OK)
                            }
                        }

                        put("/item") {
                            if (!call.checkAdminAuth(settingsRepository)) return@put
                            executeRoute(call, "update item") {
                                val repo = requireRepo(call) ?: return@executeRoute
                                val item = call.receive<ShoppingListItem>()
                                if (item.id.isBlank() || item.name.isBlank() || item.quantity.isBlank()) {
                                    AppLogger.warn("LojinhaAdminServer", "Rejected shopping list PUT /item: blank id, name, or quantity")
                                    return@executeRoute call.respond(HttpStatusCode.BadRequest, "ID, name, and quantity cannot be blank")
                                }
                                repo.updateItem(item)
                                call.respond(HttpStatusCode.OK)
                            }
                        }

                        post("/{id}/confirm") {
                            if (!call.checkAdminAuth(settingsRepository)) return@post
                            executeRoute(call, "confirm item") {
                                val repo = requireRepo(call) ?: return@executeRoute
                                val id = call.parameters["id"] ?: return@executeRoute call.respond(HttpStatusCode.BadRequest)
                                repo.confirmItem(id)
                                call.respond(HttpStatusCode.OK)
                            }
                        }

                        post("/{id}/unconfirm") {
                            if (!call.checkAdminAuth(settingsRepository)) return@post
                            executeRoute(call, "unconfirm item") {
                                val repo = requireRepo(call) ?: return@executeRoute
                                val id = call.parameters["id"] ?: return@executeRoute call.respond(HttpStatusCode.BadRequest)
                                repo.unconfirmItem(id)
                                call.respond(HttpStatusCode.OK)
                            }
                        }

                        put("/reorder") {
                            if (!call.checkAdminAuth(settingsRepository)) return@put
                            executeRoute(call, "reorder items") {
                                val repo = requireRepo(call) ?: return@executeRoute
                                val items = call.receive<List<ShoppingListItem>>()
                                repo.updateItemsOrder(items)
                                call.respond(HttpStatusCode.OK)
                            }
                        }

                        delete("/{id}") {
                            if (!call.checkAdminAuth(settingsRepository)) return@delete
                            executeRoute(call, "delete item") {
                                val repo = requireRepo(call) ?: return@executeRoute
                                val id = call.parameters["id"] ?: return@executeRoute call.respond(HttpStatusCode.BadRequest)
                                repo.deleteItem(id)
                                call.respond(HttpStatusCode.OK)
                            }
                        }

                        delete {
                            if (!call.checkAdminAuth(settingsRepository)) return@delete
                            executeRoute(call, "clear items") {
                                val repo = requireRepo(call) ?: return@executeRoute
                                repo.clearAllItems()
                                call.respond(HttpStatusCode.OK)
                            }
                        }

                        post("/commit-export") {
                            if (!call.checkAdminAuth(settingsRepository)) return@post
                            executeRoute(call, "commit export baseline") {
                                val repo = requireRepo(call) ?: return@executeRoute
                                repo.commitExportBaseline()
                                call.respond(HttpStatusCode.OK)
                            }
                        }

                        get("/pdf") {
                            if (!call.checkAdminAuth(settingsRepository)) return@get
                            val repo = requireRepo(call) ?: return@get
                            val language = call.parseLanguageParam()
                            try {
                                val items = repo.getAllItems()
                                val products = productRepository.getAllProducts()
                                val pdfBytes = ShoppingListPdfGenerator.generateBytes(items, language, products)
                                repo.commitExportBaseline()
                                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                                val filename = PdfFilenames.shoppingListFilename(dateStr, language)
                                call.respondPdf(pdfBytes, filename)
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                AppLogger.error("LojinhaAdminServer", "Failed to generate shopping list PDF: ${e.message}", e)
                                call.respond(HttpStatusCode.InternalServerError, "Failed to generate shopping list PDF")
                            }
                        }
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
                            val language = call.parseLanguageParam()
                            try {
                                val products = repo.getAllVeggieProducts()
                                val unitTypes = repo.getAllUnitTypes().associateBy { it.id }
                                val pdfBytes = VeggieOrderPdfGenerator.generateCustomerOrderFormBytes(products, unitTypes, language)
                                val dateStamp = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                                val filename = PdfFilenames.customerOrderFormFilename(dateStamp, language)
                                call.respondPdf(pdfBytes, filename)
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
                        post("/rounds/{roundId}/cancel-product/{productId}") {
                            if (!call.checkAdminAuth(settingsRepository)) return@post
                            val repo = resolveVeggieRepo() ?: return@post call.respond(HttpStatusCode.ServiceUnavailable)
                            val roundId = call.parameters["roundId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                            val productId = call.parameters["productId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                            val ok = repo.cancelProductEntriesInOpenRound(roundId, productId)
                            if (ok) {
                                call.respond(HttpStatusCode.OK)
                            } else {
                                call.respond(HttpStatusCode.Conflict, "Order round is not open")
                            }
                        }
                        delete("/products/{id}") {
                            if (!call.checkAdminAuth(settingsRepository)) return@delete
                            val repo = resolveVeggieRepo() ?: return@delete call.respond(HttpStatusCode.ServiceUnavailable)
                            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                            val existing = repo.getVeggieProductById(id)
                            val otherProductKeys = repo.getAllVeggieProducts()
                                .filter { it.id != id }
                                .mapNotNull { it.imageFileName }
                            repo.hardDeleteVeggieProduct(id)
                            veggieImageService.deleteProductImages(existing?.imageFileName ?: id, otherProductKeys)
                            call.respond(HttpStatusCode.OK)
                        }
                        post("/products/{id}/image") {
                            if (!call.checkAdminAuth(settingsRepository)) return@post
                            val repo = resolveVeggieRepo() ?: return@post call.respond(HttpStatusCode.ServiceUnavailable)
                            val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                            val contentLength = call.request.contentLength()
                            if (contentLength != null && contentLength > MAX_IMAGE_UPLOAD_BYTES) {
                                call.respond(HttpStatusCode.PayloadTooLarge, "Image exceeds 15 MB limit")
                                return@post
                            }
                            val bytes = call.receive<ByteArray>()
                            if (bytes.size > MAX_IMAGE_UPLOAD_BYTES) {
                                call.respond(HttpStatusCode.PayloadTooLarge, "Image exceeds 15 MB limit")
                                return@post
                            }
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
                                repo.updateVeggieProductImage(id, fileName, System.currentTimeMillis())
                            } else {
                                DataChangeNotifier.notifyDataChanged()
                            }
                            call.respond(HttpStatusCode.OK, mapOf("imageFileName" to fileName))
                        }
                        delete("/products/{id}/image") {
                            if (!call.checkAdminAuth(settingsRepository)) return@delete
                            val repo = resolveVeggieRepo() ?: return@delete call.respond(HttpStatusCode.ServiceUnavailable)
                            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                            val existing = repo.getVeggieProductById(id)
                            val otherProductKeys = repo.getAllVeggieProducts()
                                .filter { it.id != id }
                                .mapNotNull { it.imageFileName }
                            veggieImageService.deleteProductImages(existing?.imageFileName ?: id, otherProductKeys)
                            if (existing != null && existing.imageFileName != null) {
                                repo.updateVeggieProductImage(id, null, System.currentTimeMillis())
                            } else {
                                DataChangeNotifier.notifyDataChanged()
                            }
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
                            val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                            val entry = call.receive<VeggieOrderEntry>()
                            val ok = repo.upsertAdminOrderEntry(entry.copy(roundId = id))
                            if (ok) {
                                call.respond(HttpStatusCode.OK)
                            } else {
                                call.respond(HttpStatusCode.BadRequest, "Failed to upsert order entry")
                            }
                        }
                        delete("/rounds/{id}/entries/{entryId}") {
                            if (!call.checkAdminAuth(settingsRepository)) return@delete
                            val repo = resolveVeggieRepo() ?: return@delete call.respond(HttpStatusCode.ServiceUnavailable)
                            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                            val entryId = call.parameters["entryId"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                            val ok = repo.deleteAdminOrderEntry(id, entryId)
                            if (ok) {
                                call.respond(HttpStatusCode.OK)
                            } else {
                                call.respond(HttpStatusCode.BadRequest, "Failed to delete order entry")
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
                            val products = repo.getAllVeggieProducts()
                            val language = call.parseLanguageParam()
                            try {
                                val pdfBytes = VeggieOrderPdfGenerator.generateBuyerShoppingListBytes(round, entries, language, products)
                                repo.markBuyerPdfExported(id, System.currentTimeMillis())
                                val isoWeek = IsoWeekUtils.fromEpochMillis(round.openedAt)
                                val filename = PdfFilenames.buyerShoppingListFilename(isoWeek.weekOfYear, isoWeek.isoYear, language)
                                call.respondPdf(pdfBytes, filename)
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
                            val users = userRepository.getAllUsers()
                            val language = call.parseLanguageParam()
                            try {
                                val pdfBytes = VeggieOrderPdfGenerator.generateWorkerSortingMatrixBytes(round, entries, language, users)
                                repo.markWorkerPdfExported(id, System.currentTimeMillis())
                                val isoWeek = IsoWeekUtils.fromEpochMillis(round.openedAt)
                                val filename = PdfFilenames.workerSortingMatrixFilename(isoWeek.weekOfYear, isoWeek.isoYear, language)
                                call.respondPdf(pdfBytes, filename)
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
                    if (id.isBlank() || id.contains("..") || id.contains("/") || id.contains("\\")) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@get
                    }
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
                    post("/logout") {
                        val token = call.request.cookies[CUSTOMER_COOKIE_NAME]
                        if (!token.isNullOrBlank()) {
                            if (activeCustomerTokens.remove(hashToken(token)) != null) {
                                savePersistedCustomerSessions()
                            }
                        }
                        call.clearSessionCookie(CUSTOMER_COOKIE_NAME)
                        call.respond(HttpStatusCode.OK)
                    }

                    get("/me") {
                        val user = call.requireActiveCustomer() ?: return@get
                        call.respond(user.copy(pin = if (user.pin.isNullOrBlank()) null else "PROTECTED"))
                    }

                    post("/me/settings") {
                        val user = call.requireActiveCustomer() ?: return@post
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
                        val updatedUser = userRepository.updateCustomerSettings(
                            userId = user.id,
                            language = req.language,
                            secondaryCurrency = req.secondaryCurrency ?: user.secondaryCurrency,
                            avatar = req.avatar ?: user.avatar,
                            pinHash = updatedPinHash,
                            requirePinOnKiosk = req.requirePinOnKiosk
                        ) ?: user.copy(
                            language = req.language,
                            secondaryCurrency = req.secondaryCurrency ?: user.secondaryCurrency,
                            avatar = req.avatar ?: user.avatar,
                            pin = updatedPinHash,
                            requirePinOnKiosk = req.requirePinOnKiosk
                        )
                        if (!cleanNewPin.isNullOrBlank()) {
                            val currentTokenHash = call.request.cookies[CUSTOMER_COOKIE_NAME]?.let { hashToken(it) }
                            val revokedOther = activeCustomerTokens.entries.removeIf {
                                it.value.userId == user.id && it.key != currentTokenHash
                            }
                            if (revokedOther) {
                                savePersistedCustomerSessions()
                            }
                        }
                        call.respond(updatedUser.copy(pin = if (updatedUser.pin.isNullOrBlank()) null else "PROTECTED"))
                    }

                    get("/transactions") {
                        val user = call.requireActiveCustomer() ?: return@get
                        val userId = user.id
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
                        val user = call.requireActiveCustomer() ?: return@get
                        val fromTimestamp = call.request.queryParameters["from"]?.toLongOrNull()
                        val toTimestamp = call.request.queryParameters["to"]?.toLongOrNull()
                        val language = call.parseLanguageParam()
                        val txs = transactionRepository.getTransactionsByUserId(user.id)
                        try {
                            val pdfBytes = de.joelneumann.lojinha.data.service.TransactionPdfGenerator.generateStatementBytes(
                                user = user,
                                transactions = txs,
                                fromTimestamp = fromTimestamp,
                                toTimestamp = toTimestamp,
                                language = language
                            )
                            val filename = PdfFilenames.transactionStatementFilename(user.name, language)
                            call.respondPdf(pdfBytes, filename)
                        } catch (e: Exception) {
                            AppLogger.error("LojinhaAdminServer", "Failed to generate customer statement PDF: ${e.message}", e)
                            call.respond(HttpStatusCode.InternalServerError, "Failed to generate transactions PDF")
                        }
                    }

                    post("/notes") {
                        val user = call.requireActiveCustomer() ?: return@post
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
                        val user = call.requireActiveCustomer() ?: return@get
                        val userId = user.id
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
                        val favoriteProductIds = repo.getFavoriteProductIdsForUser(userId)
                        val settings = settingsRepository.getSettings()
                        call.respond(
                            CustomerVeggieStateResponse(
                                unitTypes = unitTypes,
                                products = products,
                                activeRound = activeRound,
                                currentEntries = currentEntries,
                                previousRoundQuantities = previousQuantities,
                                favoriteProductIds = favoriteProductIds,
                                displaySettings = CustomerDisplaySettings(
                                    usdExchangeRate = settings.usdExchangeRate,
                                    eurExchangeRate = settings.eurExchangeRate,
                                    yellowBalanceWarningThresholdCents = settings.yellowBalanceWarningThresholdCents,
                                    redBalanceWarningThresholdCents = settings.redBalanceWarningThresholdCents
                                )
                            )
                        )
                    }

                    post("/veggie/favorites") {
                        val user = call.requireActiveCustomer() ?: return@post
                        val repo = resolveVeggieRepo() ?: return@post call.respond(HttpStatusCode.ServiceUnavailable)
                        val req = call.receive<UpdateCustomerVeggieFavoritesRequest>()
                        repo.setFavoriteProductIdsForUser(user.id, req.productIds)
                        call.respond(HttpStatusCode.OK)
                    }

                    post("/veggie/order") {
                        val user = call.requireActiveCustomer() ?: return@post
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
                        val user = call.requireActiveCustomer() ?: return@get
                        val repo = resolveVeggieRepo()
                        if (repo == null) {
                            call.respond(emptyList<CustomerVeggieRoundHistoryItem>())
                            return@get
                        }
                        call.respond(repo.getCustomerOrderHistory(user.id))
                    }

                    get("/events") {
                        val user = call.requireActiveCustomer() ?: return@get
                        call.streamDataChangeEvents(targetUserId = user.id)
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

                suspend fun io.ktor.server.application.ApplicationCall.respondEntryHtml() {
                    val assetName = if (hasValidSessionCookie()) "index.html" else "login.html"
                    val asset = getOrLoadAsset(assetName)
                    if (asset == null) {
                        respond(HttpStatusCode.NotFound, "Web interface assets not found.")
                        return
                    }
                    response.header(HttpHeaders.CacheControl, "no-store, no-cache, must-revalidate")
                    response.header(HttpHeaders.Vary, "${HttpHeaders.AcceptEncoding}, ${HttpHeaders.Cookie}")
                    val acceptEncoding = request.headers[HttpHeaders.AcceptEncoding] ?: ""
                    if (acceptEncoding.contains("gzip", ignoreCase = true) && asset.gzippedBytes != null) {
                        response.header(HttpHeaders.ContentEncoding, "gzip")
                        respondBytes(asset.gzippedBytes, ContentType.Text.Html)
                    } else {
                        respondBytes(asset.rawBytes, ContentType.Text.Html)
                    }
                }

                get("/") {
                    call.respondEntryHtml()
                }

                get("/{filename...}") {
                    val rawFilename = call.parameters.getAll("filename")?.joinToString("/")?.trim('/')
                    val filename = if (rawFilename.isNullOrBlank()) {
                        call.respondEntryHtml()
                        return@get
                    } else {
                        rawFilename
                    }

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

                    if (filename == "login.html") {
                        call.respondEntryHtml()
                        return@get
                    }

                    val asset = getOrLoadAsset(filename)
                    if (asset != null) {
                        call.respondWasmAsset(filename, asset)
                    } else {
                        // Only fallback to entry HTML for clean single-segment SPA routes
                        val isSpaRoute = !filename.contains(".")
                        if (isSpaRoute) {
                            call.respondEntryHtml()
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

    private fun ApplicationCall.parseLanguageParam(): Language {
        val langStr = request.queryParameters["lang"] ?: return Language.DEFAULT
        return Language.fromCode(langStr, fallback = Language.DEFAULT)
    }

    private suspend fun ApplicationCall.respondPdf(pdfBytes: ByteArray, filename: String) {
        val asciiFallback = filename.replace(Regex("[^\\x20-\\x7E]"), "_").replace("\"", "_")
        val encodedUtf8 = java.net.URLEncoder.encode(filename, "UTF-8").replace("+", "%20")
        response.header(
            HttpHeaders.ContentDisposition,
            "attachment; filename=\"$asciiFallback\"; filename*=UTF-8''$encodedUtf8"
        )
        respondBytes(pdfBytes, ContentType.Application.Pdf)
    }

    private fun ApplicationCall.resolveClientIp(): String {
        val remote = request.local.remoteHost
        val isLoopback = remote == "127.0.0.1" || remote == "::1" || remote == "0:0:0:0:0:0:0:1" || remote == "localhost"
        if (isLoopback) {
            val cfIp = request.header("CF-Connecting-IP")?.trim()?.takeIf { it.isNotBlank() }
            if (cfIp != null) return cfIp
        }
        return remote
    }

    private fun ApplicationCall.isHttpsRequest(): Boolean {
        val forwardedProto = request.header("X-Forwarded-Proto")
        return forwardedProto.equals("https", ignoreCase = true) || request.local.scheme.equals("https", ignoreCase = true)
    }

    private fun ApplicationCall.setSessionCookie(name: String, rawToken: String, maxAgeSeconds: Int?) {
        response.cookies.append(
            Cookie(
                name = name,
                value = rawToken,
                maxAge = maxAgeSeconds,
                path = "/",
                httpOnly = true,
                secure = isHttpsRequest(),
                extensions = mapOf("SameSite" to "Lax")
            )
        )
    }

    private fun ApplicationCall.clearSessionCookie(name: String) {
        response.cookies.append(
            Cookie(
                name = name,
                value = "",
                maxAge = 0,
                path = "/",
                httpOnly = true,
                secure = isHttpsRequest(),
                extensions = mapOf("SameSite" to "Lax")
            )
        )
    }

    private fun ApplicationCall.hasValidSessionCookie(): Boolean {
        val now = System.currentTimeMillis()
        val adminToken = request.cookies[ADMIN_COOKIE_NAME]
        if (!adminToken.isNullOrBlank()) {
            val adminHash = hashToken(adminToken)
            val expiry = activeTokens[adminHash]
            if (expiry != null) {
                if (now < expiry) return true
                activeTokens.remove(adminHash)
            }
        }
        val customerToken = request.cookies[CUSTOMER_COOKIE_NAME]
        if (!customerToken.isNullOrBlank()) {
            val customerHash = hashToken(customerToken)
            val session = activeCustomerTokens[customerHash]
            if (session != null) {
                if (now < session.expiryTimestamp) return true
                activeCustomerTokens.remove(customerHash)
                savePersistedCustomerSessions()
            }
        }
        return false
    }

    private suspend fun ApplicationCall.resolveSessionRole(): String? {
        val now = System.currentTimeMillis()
        val adminToken = request.cookies[ADMIN_COOKIE_NAME]
        if (!adminToken.isNullOrBlank()) {
            val adminHash = hashToken(adminToken)
            val expiry = activeTokens[adminHash]
            if (expiry != null) {
                if (now < expiry) return "ADMIN"
                activeTokens.remove(adminHash)
            }
        }
        val customerToken = request.cookies[CUSTOMER_COOKIE_NAME]
        if (!customerToken.isNullOrBlank()) {
            val customerHash = hashToken(customerToken)
            val session = activeCustomerTokens[customerHash]
            if (session != null) {
                if (now < session.expiryTimestamp) {
                    val user = userRepository.getUserById(session.userId)
                    if (user != null && user.isActive && !user.isDeleted) {
                        return "CUSTOMER"
                    }
                }
                activeCustomerTokens.remove(customerHash)
                savePersistedCustomerSessions()
            }
        }
        return null
    }

    private suspend fun ApplicationCall.checkAdminAuth(settingsRepository: SettingsRepository): Boolean {
        val token = request.cookies[ADMIN_COOKIE_NAME]
        if (!token.isNullOrBlank()) {
            val tokenHash = hashToken(token)
            val expiry = activeTokens[tokenHash]
            if (expiry != null) {
                if (System.currentTimeMillis() < expiry) {
                    return true
                } else {
                    activeTokens.remove(tokenHash)
                }
            }
        }

        respond(HttpStatusCode.Unauthorized, "Unauthorized access")
        return false
    }

    private suspend fun ApplicationCall.requireCustomerUserId(): String? {
        val token = request.cookies[CUSTOMER_COOKIE_NAME]
        if (!token.isNullOrBlank()) {
            val tokenHash = hashToken(token)
            val session = activeCustomerTokens[tokenHash]
            if (session != null) {
                val now = System.currentTimeMillis()
                if (now < session.expiryTimestamp) {
                    if (session.keepLoggedIn) {
                        val newExpiry = now + PERSISTENT_CUSTOMER_SESSION_DURATION_MS
                        if (newExpiry - session.expiryTimestamp >= SLIDING_RENEWAL_PERSIST_INTERVAL_MS) {
                            activeCustomerTokens[tokenHash] = session.copy(expiryTimestamp = newExpiry)
                            setSessionCookie(
                                CUSTOMER_COOKIE_NAME,
                                token,
                                (PERSISTENT_CUSTOMER_SESSION_DURATION_MS / 1000L).toInt()
                            )
                            savePersistedCustomerSessions()
                        }
                    }
                    return session.userId
                } else {
                    activeCustomerTokens.remove(tokenHash)
                    savePersistedCustomerSessions()
                }
            }
        }
        respond(HttpStatusCode.Unauthorized, "Unauthorized customer access")
        return null
    }

    private suspend fun ApplicationCall.requireActiveCustomer(): User? {
        val userId = requireCustomerUserId() ?: return null
        if (userId.isEmpty()) return null
        val user = userRepository.getUserById(userId)
        if (user == null || !user.isActive || user.isDeleted) {
            val token = request.cookies[CUSTOMER_COOKIE_NAME]
            if (!token.isNullOrBlank() && activeCustomerTokens.remove(hashToken(token)) != null) {
                savePersistedCustomerSessions()
            }
            clearSessionCookie(CUSTOMER_COOKIE_NAME)
            respond(HttpStatusCode.Unauthorized, "Customer account is inactive or not found")
            return null
        }
        return user
    }

    private suspend fun ApplicationCall.streamDataChangeEvents(targetUserId: String? = null) {
        if (activeSseConnections.incrementAndGet() > MAX_SSE_CONNECTIONS) {
            activeSseConnections.decrementAndGet()
            respond(HttpStatusCode.ServiceUnavailable, "Too many active event streams")
            return
        }
        try {
            response.cacheControl(CacheControl.NoCache(null))
            respondTextWriter(contentType = ContentType.Text.EventStream) {
                val channel = Channel<Unit>(Channel.CONFLATED)
                val listener: (DataChangeNotifier.ChangeScope) -> Unit = { scope ->
                    val shouldNotify = if (targetUserId == null) {
                        true
                    } else {
                        !scope.adminOnly && (scope.targetUserId == null || scope.targetUserId == targetUserId)
                    }
                    if (shouldNotify) {
                        channel.trySend(Unit)
                    }
                }
                DataChangeNotifier.addScopedListener(listener)
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
                    DataChangeNotifier.removeScopedListener(listener)
                    channel.close()
                }
            }
        } finally {
            activeSseConnections.decrementAndGet()
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
        name.endsWith(".webmanifest") -> ContentType("application", "manifest+json")
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
