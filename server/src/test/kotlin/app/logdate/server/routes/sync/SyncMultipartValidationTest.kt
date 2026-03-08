package app.logdate.server.routes.sync

import app.logdate.server.routes.support.authHeader
import app.logdate.server.routes.support.backupUploadMultipartContent
import app.logdate.server.routes.support.configureInMemorySyncApp
import app.logdate.server.routes.support.mediaUploadMultipartContent
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncMultipartValidationTest {
    @Test
    fun `multipart upload endpoints reject blank required fields`() =
        testApplication {
            val tokenService = configureInMemorySyncApp()
            val auth = authHeader(tokenService)

            val blankContentId =
                client.post("/api/v1/media") {
                    header(HttpHeaders.Authorization, auth)
                    setBody(
                        mediaUploadMultipartContent(
                            contentId = "   ",
                            fileName = "photo.jpg",
                            mimeType = "image/jpeg",
                            data = byteArrayOf(1, 2, 3, 4),
                            deviceId = "device-1",
                        ),
                    )
                }
            assertEquals(HttpStatusCode.BadRequest, blankContentId.status)

            val blankDeviceId =
                client.post("/api/v1/backups") {
                    header(HttpHeaders.Authorization, auth)
                    setBody(
                        backupUploadMultipartContent(
                            deviceId = "   ",
                            manifest = "{}",
                            data = byteArrayOf(1, 2, 3, 4),
                        ),
                    )
                }
            assertEquals(HttpStatusCode.BadRequest, blankDeviceId.status)
        }
}
