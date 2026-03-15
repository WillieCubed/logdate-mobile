package app.logdate.integration.e2e.harness

import app.logdate.client.sync.cloud.LogDateCloudApiClient
import app.logdate.server.module
import app.logdate.shared.config.DefaultLogDateConfigRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.serialization.json.Json
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import io.ktor.serialization.kotlinx.json.json as ktorJson
import java.net.http.HttpClient as JHttpClient

data class ServerClientE2EHarness(
    val baseUrl: String,
    val apiClient: LogDateCloudApiClient,
    private val httpClient: HttpClient,
    private val engine: EmbeddedServer<*, *>,
) : AutoCloseable {
    override fun close() {
        httpClient.close()
        engine.stop(gracePeriodMillis = 500, timeoutMillis = 5_000)
    }
}

suspend fun <T> withServerClientHarness(block: suspend ServerClientE2EHarness.() -> T): T {
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
            module(isDatabaseAvailable = false)
        }

    engine.start(wait = false)
    waitForServerStartup(host, port)

    val configRepository = DefaultLogDateConfigRepository(initialBackendUrl = "http://$host:$port")
    val baseUrl = configRepository.getCurrentApiBaseUrl()
    val httpClient =
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                ktorJson(json)
            }
        }
    val apiClient = LogDateCloudApiClient(configRepository = configRepository, httpClient = httpClient)
    val harness =
        ServerClientE2EHarness(
            baseUrl = baseUrl,
            apiClient = apiClient,
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
