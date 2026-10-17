package app.logdate.client.sync.cloud

import app.logdate.shared.config.DefaultLogDateConfigRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class MediaDownloadIntegrityTest {
    @Test
    fun `truncated oversized and invalid media responses never become available`() =
        runTest {
            for (declared in listOf(-1L, 0L, 1L, 100L)) {
                val result = download(declared, byteArrayOf(1, 2, 3))
                assertTrue(result.isFailure, "Declared $declared bytes must not accept three bytes")
            }
        }

    @Test
    fun `complete legacy originals remain supported`() =
        runTest {
            assertContentEquals(byteArrayOf(1, 2, 3), download(3, byteArrayOf(1, 2, 3)).getOrThrow().data)
        }

    private suspend fun download(
        size: Long,
        bytes: ByteArray,
    ): Result<MediaFile> {
        val id = Uuid.random()
        val engine =
            MockEngine { request ->
                if (request.url.encodedPath.endsWith("/binary")) {
                    respond(bytes, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString()))
                } else {
                    respond(
                        """{
                                "contentId":"$id","mediaId":"original","fileName":"original.jpg",
                                "mimeType":"image/jpeg","sizeBytes":$size,
                                "downloadUrl":"https://example.test/media/original","uploadedAt":1
                            }""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            }
        return HttpClient(engine) { install(ContentNegotiation) { json() } }.use { client ->
            DefaultCloudMediaDataSource(
                LogDateCloudApiClient(DefaultLogDateConfigRepository(initialBackendUrl = "https://example.test"), client),
            ).downloadMedia("token", "original")
        }
    }
}
