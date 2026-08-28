package de.joelneumann.lojinha.server

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
import kotlinx.serialization.json.Json
import java.io.File

class LojinhaAdminServer(
    private val productRepository: ProductRepository,
    private val userRepository: UserRepository,
    private val transactionRepository: TransactionRepository,
    private val settingsRepository: SettingsRepository,
    private val port: Int = 8080
) {
    private var server: EmbeddedServer<*, *>? = null

    fun start() {
        server = embeddedServer(Netty, port = port, host = "0.0.0.0") {
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
                // 1. Public Login Validation Endpoint
                post("/api/admin/login") {
                    try {
                        val req = call.receive<LoginRequest>()
                        val settings = settingsRepository.getSettings()
                        val expectedPassword = if (settings.adminPasswordHash.isNotBlank()) settings.adminPasswordHash else "admin"
                        val matches = (req.password == expectedPassword)
                        println("[AUTH] Admin Login Attempt: received='${req.password}', expected='$expectedPassword', match=$matches")
                        if (matches) {
                            call.respond(HttpStatusCode.OK, LoginResponse(true, "Authenticated"))
                        } else {
                            call.respond(HttpStatusCode.OK, LoginResponse(false, "Invalid admin password"))
                        }
                    } catch (e: Exception) {
                        println("[ERROR] Error processing login request: ${e.message}")
                        call.respond(HttpStatusCode.OK, LoginResponse(false, "Error processing login request"))
                    }
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

                    // Settings
                    get("/settings") {
                        if (!call.checkAdminAuth(settingsRepository)) return@get
                        call.respond(settingsRepository.getSettings())
                    }
                    post("/settings") {
                        if (!call.checkAdminAuth(settingsRepository)) return@post
                        val settings = call.receive<SystemSettings>()
                        settingsRepository.updateSettings(settings)
                        call.respond(HttpStatusCode.OK)
                    }
                }

                // 3. Serve static Wasm assets
                get("/{filename...}") {
                    val filename = call.parameters.getAll("filename")?.joinToString("/") ?: "index.html"
                    val file = findWasmAsset(filename)
                    if (file != null && file.exists() && !file.isDirectory) {
                        val contentType = when {
                            file.name.endsWith(".html") -> ContentType.Text.Html
                            file.name.endsWith(".js") -> ContentType.Text.JavaScript
                            file.name.endsWith(".wasm") -> ContentType.Application.Wasm
                            file.name.endsWith(".css") -> ContentType.Text.CSS
                            file.name.endsWith(".png") -> ContentType.Image.PNG
                            file.name.endsWith(".jpg") || file.name.endsWith(".jpeg") -> ContentType.Image.JPEG
                            file.name.endsWith(".svg") -> ContentType.Image.SVG
                            else -> ContentType.Application.OctetStream
                        }
                        call.respondFile(file)
                    } else {
                        val indexFile = findWasmAsset("index.html")
                        if (indexFile != null && indexFile.exists()) {
                            call.respondFile(indexFile)
                        } else {
                            call.respondText(
                                "Wasm distribution assets not found. Run './gradlew :shared:wasmJsBrowserDistribution' to build the Web Wasm bundle.",
                                status = HttpStatusCode.NotFound
                            )
                        }
                    }
                }
            }
        }.start(wait = false)
        println("[INFO] Lojinha Embedded Admin Server active on http://0.0.0.0:$port")
    }

    private suspend fun ApplicationCall.checkAdminAuth(settingsRepository: SettingsRepository): Boolean {
        if (request.httpMethod == HttpMethod.Options) return true
        val authPassword = request.headers["X-Admin-Password"]
        val settings = settingsRepository.getSettings()
        val expectedPassword = if (settings.adminPasswordHash.isNotBlank()) settings.adminPasswordHash else "admin"
        if (authPassword == null || authPassword != expectedPassword) {
            respond(HttpStatusCode.Unauthorized, "Unauthorized access")
            return false
        }
        return true
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
            val file = File(dir, name)
            if (file.exists()) return file
        }
        return null
    }

    fun stop() {
        server?.stop(1000, 2000)
    }
}
