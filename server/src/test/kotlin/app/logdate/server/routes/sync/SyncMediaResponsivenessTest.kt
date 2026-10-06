package app.logdate.server.routes.sync

import app.logdate.server.auth.JwtTokenService
import app.logdate.server.logdate.asLogDateBackupRepository
import app.logdate.server.logdate.asLogDateCollectionsRepository
import app.logdate.server.logdate.asLogDateMediaBlobRepository
import app.logdate.server.logdate.asLogDateMediaRepository
import app.logdate.server.routes.syncRoutes
import app.logdate.server.sync.GcsMediaStorage
import app.logdate.server.sync.InMemorySyncRepository
import app.logdate.server.sync.MediaRecord
import app.logdate.server.sync.SyncMetricsRegistry
import app.logdate.shared.model.sync.DeviceId
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class SyncMediaResponsivenessTest {
    @Test
    fun `slow media storage does not block health or other requests on one request worker`() =
        runBlocking {
            val repository = InMemorySyncRepository()
            val owner = UUID.fromString(Uuid.random().toString())
            val tokens = JwtTokenService("responsiveness-fixture")
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val bytes = "LDCE1fixture".encodeToByteArray()
            val storage = mockk<GcsMediaStorage>()
            every { storage.getBlob("fixture") } answers {
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS))
                bytes
            }
            repository.upsertMedia(
                owner,
                MediaRecord(
                    mediaId = "media-fixture",
                    contentId = "entry-fixture",
                    userId = owner,
                    fileName = "fixture.m4a",
                    mimeType = "audio/mp4",
                    sizeBytes = bytes.size.toLong(),
                    data = byteArrayOf(),
                    storagePath = "fixture",
                    createdAt = 1,
                    serverVersion = 1,
                    deviceId = DeviceId("fixture"),
                ),
            )
            val server =
                embeddedServer(Netty, configure = {
                    connector {
                        host = "127.0.0.1"
                        port = 0
                    }
                    callGroupSize = 1
                    workerGroupSize = 1
                }) {
                    install(ContentNegotiation) { json() }
                    routing {
                        get("/health") { call.respondText("ready") }
                        route("/api/v1") {
                            syncRoutes(
                                tokenService = tokens,
                                mediaStorage = storage,
                                metrics = SyncMetricsRegistry(),
                                collectionsRepository = repository.asLogDateCollectionsRepository(),
                                mediaBlobRepository = repository.asLogDateMediaRepository().asLogDateMediaBlobRepository(),
                                backupRepository = repository.asLogDateBackupRepository(),
                            )
                        }
                    }
                }.start(wait = false)
            try {
                val origin = "http://127.0.0.1:${server.engine.resolvedConnectors().single().port}"
                val client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()
                val download =
                    client.sendAsync(
                        HttpRequest
                            .newBuilder(URI("$origin/api/v1/media/media-fixture/binary"))
                            .header("Authorization", "Bearer ${tokens.generateAccessToken(owner.toString())}")
                            .GET()
                            .build(),
                        HttpResponse.BodyHandlers.ofByteArray(),
                    )
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                val health =
                    client.send(
                        HttpRequest
                            .newBuilder(URI("$origin/health"))
                            .timeout(Duration.ofSeconds(3))
                            .GET()
                            .build(),
                        HttpResponse.BodyHandlers.ofString(),
                    )
                assertEquals(200, health.statusCode())
                release.countDown()
                val response = download.get(5, TimeUnit.SECONDS)
                assertEquals(200, response.statusCode())
                assertContentEquals(bytes, response.body())
            } finally {
                release.countDown()
                server.stop(gracePeriodMillis = 0, timeoutMillis = 1000)
            }
        }
}
