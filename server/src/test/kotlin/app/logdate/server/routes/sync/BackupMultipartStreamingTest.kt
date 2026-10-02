package app.logdate.server.routes.sync

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals

class BackupMultipartStreamingTest {
    @Test
    fun `missing multipart boundary is a client error`() =
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing { post("/backup") { call.receiveBackupMultipartFile() } }
            }
            val response =
                client.post("/backup") {
                    contentType(ContentType.MultiPart.FormData)
                    setBody("invalid")
                }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `cancellation while returning an acquired file removes it`() =
        kotlinx.coroutines.runBlocking {
            val path = createPrivateBackupTempFile("logdate-backup-upload-", ".bin")
            try {
                kotlinx.coroutines.supervisorScope {
                    val job =
                        launch {
                            withBackupFileHandoff {
                                kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]!!.cancel()
                                ParsedBackupMultipartFile("fixture", "{}", path, 1)
                            }
                        }
                    job.join()
                    kotlin.test.assertTrue(job.isCancelled)
                }
                kotlin.test.assertFalse(
                    java.nio.file.Files
                        .exists(path),
                )
            } finally {
                deletePrivateBackupTempFile(path)
            }
        }

    @Test
    fun `a hundred megabyte file streams without the generic multipart size ceiling`() =
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing {
                    post("/backup") {
                        val parsed = call.receiveBackupMultipartFile() ?: return@post
                        try {
                            call.respondText(parsed.sizeBytes.toString())
                        } finally {
                            deletePrivateBackupTempFile(parsed.path)
                        }
                    }
                }
            }
            val size = 100L * 1024 * 1024
            val response = client.post("/backup") { setBody(backupBody(size)) }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(size.toString(), response.bodyAsText())
        }

    @Test
    fun `oversized text fields are rejected independently of file capacity`() =
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing {
                    post("/backup") {
                        val parsed = call.receiveBackupMultipartFile() ?: return@post
                        try {
                            call.respondText("unexpected acceptance")
                        } finally {
                            deletePrivateBackupTempFile(parsed.path)
                        }
                    }
                }
            }
            val response = client.post("/backup") { setBody(backupBody(1, manifestBytes = 300 * 1024)) }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    private fun backupBody(
        size: Long,
        manifestBytes: Int = 2,
    ) = object : OutgoingContent.WriteChannelContent() {
        private val boundary = "test-backup-stream-boundary"
        override val contentType = ContentType.parse("multipart/form-data; boundary=$boundary")

        override suspend fun writeTo(channel: ByteWriteChannel) {
            channel.writeStringUtf8("--$boundary\r\nContent-Disposition: form-data; name=\"deviceId\"\r\n\r\nfixture\r\n")
            channel.writeStringUtf8("--$boundary\r\nContent-Disposition: form-data; name=\"manifest\"\r\n\r\n")
            channel.writeFully(ByteArray(manifestBytes) { 120 })
            channel.writeStringUtf8(
                "\r\n--$boundary\r\nContent-Disposition: form-data; name=\"data\"; filename=\"backup.bin\"\r\nContent-Type: application/octet-stream\r\n\r\n",
            )
            val chunk = ByteArray(64 * 1024) { 42 }
            var remaining = size
            while (remaining > 0) {
                val count = minOf(chunk.size.toLong(), remaining).toInt()
                channel.writeFully(chunk, 0, count)
                remaining -= count
            }
            channel.writeStringUtf8("\r\n--$boundary--\r\n")
        }
    }
}
