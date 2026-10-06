package app.logdate.server

import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.runBlocking
import kotlinx.io.readByteArray
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

class ServerTransportTest {
    @Test
    fun `cleartext HTTP2 accepts large transfers and retains HTTP1 health probes`() =
        runBlocking {
            val server =
                embeddedServer(Netty, configure = {
                    connector {
                        host = "127.0.0.1"
                        port = 0
                    }
                    configureLogDateTransport()
                }) {
                    routing {
                        get("/health") { call.respondText("ready") }
                        post("/transfer") {
                            call.respondText(
                                call
                                    .receiveChannel()
                                    .readRemaining()
                                    .readByteArray()
                                    .size
                                    .toString(),
                            )
                        }
                    }
                }.start(wait = false)
            try {
                val origin = "http://127.0.0.1:${server.engine.resolvedConnectors().single().port}"
                val client =
                    HttpClient
                        .newBuilder()
                        .version(HttpClient.Version.HTTP_2)
                        .connectTimeout(Duration.ofSeconds(5))
                        .build()
                val ready = client.send(HttpRequest.newBuilder(URI("$origin/health")).GET().build(), HttpResponse.BodyHandlers.ofString())
                assertEquals(HttpClient.Version.HTTP_2, ready.version())
                val payload = ByteArray(33 * 1024 * 1024) { 7 }
                val transfer =
                    client.send(
                        HttpRequest
                            .newBuilder(URI("$origin/transfer"))
                            .timeout(Duration.ofSeconds(20))
                            .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                            .build(),
                        HttpResponse.BodyHandlers.ofString(),
                    )
                assertEquals(200, transfer.statusCode())
                assertEquals(HttpClient.Version.HTTP_2, transfer.version())
                assertEquals(payload.size.toString(), transfer.body())
                val probe =
                    HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build().send(
                        HttpRequest.newBuilder(URI("$origin/health")).GET().build(),
                        HttpResponse.BodyHandlers.ofString(),
                    )
                assertEquals(200, probe.statusCode())
                assertEquals(HttpClient.Version.HTTP_1_1, probe.version())
            } finally {
                server.stop(gracePeriodMillis = 0, timeoutMillis = 1000)
            }
        }
}
