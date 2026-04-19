package app.logdate.server

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.writeText

private const val OPENAPI_JSON_PATH = "/openapi.json"
private const val OPENAPI_YAML_PATH = "/openapi.yaml"
private const val REQUEST_TIMEOUT_SECONDS = 20L

fun main() {
    val outputDirectory = Path.of(System.getProperty("logdate.openapi.outputDir") ?: "build/openapi").toAbsolutePath()
    Files.createDirectories(outputDirectory)
    val port = ServerSocket(0).use { it.localPort }

    val server =
        embeddedServer(Netty, host = "localhost", port = port) {
            module(isDatabaseAvailable = false)
        }

    server.start(wait = false)
    try {
        val httpClient =
            HttpClient
                .newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .build()

        val openApiJson = fetchOpenApi(httpClient, port, OPENAPI_JSON_PATH)
        val openApiYaml = fetchOpenApi(httpClient, port, OPENAPI_YAML_PATH)

        outputDirectory.resolve("openapi.json").writeText(openApiJson)
        outputDirectory.resolve("openapi.yaml").writeText(openApiYaml)
    } finally {
        server.stop(gracePeriodMillis = 500, timeoutMillis = 5_000)
    }
}

private fun fetchOpenApi(
    client: HttpClient,
    port: Int,
    path: String,
): String {
    val acceptHeader =
        when {
            path.endsWith(".json") -> "application/json"
            path.endsWith(".yaml") || path.endsWith(".yml") -> "application/x-yaml"
            else -> "application/json"
        }
    val request =
        HttpRequest
            .newBuilder()
            .uri(URI("http://localhost:$port$path"))
            .header("Accept", acceptHeader)
            .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
            .GET()
            .build()

    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
    require(response.statusCode() == 200) {
        "Failed to fetch $path (status=${response.statusCode()})"
    }
    return response.body()
}
