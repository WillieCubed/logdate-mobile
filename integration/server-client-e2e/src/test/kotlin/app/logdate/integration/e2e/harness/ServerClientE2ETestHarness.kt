package app.logdate.integration.e2e.harness

import app.logdate.client.sync.cloud.LogDateCloudApiClient
import app.logdate.server.auth.AuthMetricsRegistry
import app.logdate.server.auth.FakeGoogleIdTokenVerifier
import app.logdate.server.auth.InMemoryAccountIdentityRepository
import app.logdate.server.auth.InMemoryAccountRepository
import app.logdate.server.auth.InMemorySessionManager
import app.logdate.server.auth.JwtTokenService
import app.logdate.server.passkeys.WebAuthnPasskeyService
import app.logdate.server.routes.authV1Routes
import app.logdate.server.routes.syncRoutes
import app.logdate.server.sync.InMemorySyncRepository
import app.logdate.server.sync.SyncMetricsRegistry
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import io.ktor.serialization.kotlinx.json.json as ktorJson
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import java.net.http.HttpClient as JHttpClient

data class ServerClientE2EHarness(
    val baseUrl: String,
    val apiClient: LogDateCloudApiClient,
    val tokenService: JwtTokenService,
    val accountRepository: InMemoryAccountRepository,
    val identityRepository: InMemoryAccountIdentityRepository,
    val syncRepository: InMemorySyncRepository,
    private val httpClient: HttpClient,
    private val engine: EmbeddedServer<*, *>,
) : AutoCloseable {
    override fun close() {
        httpClient.close()
        engine.stop(gracePeriodMillis = 500, timeoutMillis = 5_000)
    }
}

suspend fun <T> withServerClientHarness(block: suspend ServerClientE2EHarness.() -> T): T {
    val tokenService = JwtTokenService("server-client-e2e-secret")
    val accountRepository = InMemoryAccountRepository()
    val identityRepository = InMemoryAccountIdentityRepository()
    val syncRepository = InMemorySyncRepository()
    val sessionManager = InMemorySessionManager()
    val webAuthnService = WebAuthnPasskeyService()
    val authMetrics = AuthMetricsRegistry()
    val syncMetrics = SyncMetricsRegistry()
    val googleVerifier = FakeGoogleIdTokenVerifier(emptyMap())

    val json =
        Json {
            prettyPrint = false
            isLenient = true
            ignoreUnknownKeys = true
        }

    val port = ServerSocket(0).use { it.localPort }
    val host = "127.0.0.1"
    val engine =
        embeddedServer(Netty, host = host, port = port) {
            install(ServerContentNegotiation) {
                ktorJson(json)
            }
            routing {
                route("/api/v1") {
                    authV1Routes(
                        accountRepository = accountRepository,
                        identityRepository = identityRepository,
                        sessionManager = sessionManager,
                        webAuthnService = webAuthnService,
                        tokenService = tokenService,
                        googleIdTokenVerifier = googleVerifier,
                        metrics = authMetrics,
                    )
                    syncRoutes(
                        repository = syncRepository,
                        tokenService = tokenService,
                        mediaStorage = null,
                        metrics = syncMetrics,
                    )
                }
            }
        }

    engine.start(wait = false)
    waitForServerStartup(host, port)

    val baseUrl = "http://$host:$port/api/v1"
    val httpClient =
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                ktorJson(json)
            }
        }
    val apiClient = LogDateCloudApiClient(baseUrl = baseUrl, httpClient = httpClient)
    val harness =
        ServerClientE2EHarness(
            baseUrl = baseUrl,
            apiClient = apiClient,
            tokenService = tokenService,
            accountRepository = accountRepository,
            identityRepository = identityRepository,
            syncRepository = syncRepository,
            httpClient = httpClient,
            engine = engine,
        )

    return try {
        harness.block()
    } finally {
        harness.close()
    }
}

private fun waitForServerStartup(
    host: String,
    port: Int,
    timeoutMillis: Long = 5_000,
) {
    val deadline = System.currentTimeMillis() + timeoutMillis
    val client =
        JHttpClient
            .newBuilder()
            .connectTimeout(Duration.ofMillis(250))
            .build()
    val request =
        HttpRequest
            .newBuilder()
            .uri(URI("http://$host:$port/api/v1/ops/sync/status"))
            .timeout(Duration.ofMillis(500))
            .GET()
            .build()

    while (System.currentTimeMillis() < deadline) {
        runCatching { client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() }
            .onSuccess { status ->
                if (status in 100..599) {
                    return
                }
            }
        Thread.sleep(50)
    }
    error("Timed out waiting for test server startup on $host:$port")
}
